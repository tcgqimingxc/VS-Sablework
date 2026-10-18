package org.valkyrienskies.clockwork.content.contraptions.gyro;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.valkyrienskies.clockwork.config.ClockworkConfig;
import org.valkyrienskies.clockwork.platform.CWForces;

/**
 * 陀螺方块实体（旧版 {@code content/physicalities/gyro/GyroBlockEntity} +
 * {@code content/physicalities/gyro/GyroShipControl}）。
 *
 * <p><b>施力链路</b>：旧版把控制逻辑挂成 VS2 的 {@code ShipPhysicsListener} 附件，
 * 在 {@code physTick} 里 {@code physShip.applyWorldTorque(idealTorque)}；
 * 移植后改为实现 Sable 的 {@link BlockEntitySubLevelActor}，
 * 在 {@code sable$physicsTick} 里把力矩记进力分组 —— 这是 Sable 唯一被允许在物理子步里施力的入口
 * （{@code ServerSubLevel#prePhysicsTickBegin} 每个子步都会 reset 力分组，
 * 游戏刻里加的力会被清掉，见 {@code ServerSubLevel.java:273-287}）。
 *
 * <h2>控制律（与旧版逐项对应）</h2>
 * <pre>
 *   up      = 陀螺自身"上"方向（由红石倾斜）经结构姿态变换到世界系     旧版 GyroShipControl.kt:51
 *   ω⊥      = ω − up·(ω·up)       偏离自转轴的那部分角速度（要消掉的）   旧版 :52-53
 *   ω理想   = up × (0,1,0) − ω⊥   一边把 up 掰向世界上，一边阻尼摆动     旧版 :54
 *   τ_local = I_local · R⁻¹ · ω理想                                      旧版 :56-58（世界系写法）
 * </pre>
 *
 * <p><b>1) 为什么是 local</b>：Sable 的 {@code ForceTotal}/Rapier 收的是 local 力矩
 * （{@code sable_rapier} 的 {@code applyForceAndTorque} 里先 {@code rb.rotation().mul_vec3(torque)}
 * 再 {@code apply_torque_impulse}，lib.rs:1386-1389）。旧版 VS2 的 {@code applyWorldTorque}
 * 收世界系，所以旧版写 {@code R·I·Rᵀ·ω}；两者数值等价（局部表示 = {@code I·R⁻¹·ω}），
 * 不要按旧版公式再乘 R。
 *
 * <p><b>2) D 项在哪</b>：{@code −ω⊥} 就是 D 项，且系数与 P 项相同（都是 s），
 * 连续域里 ζ = √s/2 → 强过阻尼，**不是**"缺 D 项"。真正的问题是离散化：
 * 旧版这条律是连续力矩，交给 VS2 的求解器；Sable 每个物理子步把
 * {@code τ·dt} 当角冲量直接加到角速度上，于是每子步的等效比例是
 *
 * <pre>
 *   显式（修前）：k = s·dt            ← 从旧版公式直接照搬
 *   ZOH 精确解（本实现）：k = 1 − e^(−s·dt)
 * </pre>
 *
 * <p>{@code s} 最大 100（{@code speedToForce} 在 145 RPM 触顶），实机
 * {@code substepsPerTick = 2} → dt = 1/40 → 修前 k = 2.5。小角度离散映射
 * {@code [θ,ω]} 的谱半径在 {@code k > 4/(2+dt) ≈ 2} 时 > 1：阻尼项变成增益 >1 的负反馈，
 * 每子步放大 1.5 倍并**每个子步反号** → 高转速下力矩高频正反抽打，结构剧烈抖动/翻覆
 * （实机"低转速正常、高转速乱飞"）。k = 1−e^(−s·dt) 永远 ∈ [0,1)，对任意 s、dt 收敛，
 * 且 s·dt→0 时与旧版公式逐项一致（中低转速手感不变）。
 * 离线证据与全工况扫描见 {@code tools/gyro_stability_sim.py}。
 *
 * <p><b>3) 为什么限幅</b>：{@code τ = I·Δω/dt}，失控时 Δω 很大 → 一次修正就是巨大角冲量，
 * 结构撞上地面/方块会被弹飞（"乱飞"的第二个来源）。这里按<b>角加速度</b>封顶
 * （{@code gyroMaxAngularAcceleration}，rad/s²）：它与结构大小无关（τ ∝ I 约掉），
 * 大船和小船手感一致；{@code gyroMaxTorque} 仍作为绝对上限兜底。
 *
 * <p><b>4) timeStep 缩放</b>：{@link CWForces#torque} 内部乘 dt（力矩 → 角冲量），
 * 而 {@code sable$physicsTick} 每子步只调用一次（{@code SubLevelPhysicsSystem.java:266-269}），
 * 力分组每子步 reset —— 不存在"每 tick 重复施加导致增益翻倍"。
 *
 * <p><b>5) 多个陀螺</b>：旧版每条船只有一个 {@code GyroShipControl}（{@code gyros} 只用于计数和
 * 移除附件，力矩并不随块数叠加）；本移植是每个方块各施加一次，而且 Sable 先跑完所有
 * {@code sable$physicsTick} 再统一 {@code applyQueuedForces}（{@code ServerSubLevel.java:279-287}），
 * 所以 N 个陀螺读到的是**同一个**本子步起始角速度 → 总增益是 N·k。
 * k 已经接近 1（高转速），N ≥ 3 时 N·k 会重新越过 2 的稳定界，于是又出现"高频正反抽打"。
 * 因此这里按 {@link #countGyros} 把每块的份额除以 N：N 个陀螺合起来等价于旧版的一个控制器
 * （总增益 k ≤ 1、总角加速度上限不变），既不改变"多放几个更稳"的直觉（冗余），
 * 也不会把增益叠出界。
 */
public class GyroBlockEntity extends KineticBlockEntity implements BlockEntitySubLevelActor {

    /** 旧版 {@code GyroShipControl#speedToForce} 的上限与形状常数。 */
    private static final double SPEED_FORCE_MAX = 100.0;
    /** 世界"上"方向；只在算 {@code up × (0,1,0)} 时用，做成常量避免每子步分配。 */
    private static final Vector3d WORLD_UP = new Vector3d(0.0, 1.0, 0.0);

    /** 陀螺自身"上"方向（local 坐标系），由红石信号倾斜。 */
    private final Vector3d localUp = new Vector3d(0.0, 1.0, 0.0);
    /** 红石：南北差（俯仰）与东西差（横滚），范围 ±15（照抄旧版 updatePower）。 */
    private int pitchSignal;
    private int rollSignal;

    public GyroBlockEntity(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        this.updateRedstone();
        // 由红石倾斜"上"方向：每 15 级信号倾斜 90°（照抄旧版 GyroBlockEntity.kt:53-56）
        final Vector3d up = new Vector3d(0.0, 1.0, 0.0);
        up.rotateX(this.pitchSignal / 15.0 * Math.PI / 2.0);
        up.rotateZ(this.rollSignal / 15.0 * Math.PI / 2.0);
        this.localUp.set(up);
    }

    private void updateRedstone() {
        final BlockPos pos = this.getBlockPos();
        final int south = this.level.getSignal(pos.relative(Direction.SOUTH), Direction.SOUTH);
        final int north = this.level.getSignal(pos.relative(Direction.NORTH), Direction.NORTH);
        final int east = this.level.getSignal(pos.relative(Direction.EAST), Direction.EAST);
        final int west = this.level.getSignal(pos.relative(Direction.WEST), Direction.WEST);
        this.pitchSignal = north - south;
        // 旧版 GyroBlockEntity.updatePower 是 Point(powerZN - powerZP, powerXN - powerXP)，
        // 即横滚 = west - east（2026-10-18 按旧版源码逐行核对后修正）。
        this.rollSignal = west - east;
    }

    @Override
    public void sable$physicsTick(final ServerSubLevel subLevel, final RigidBodyHandle handle, final double timeStep) {
        if (timeStep <= 0.0) {
            return;
        }
        // 旧版 :60 `idealTorque.mul(abs(speedToForce(speed)))`：强度 = 转速曲线 × 倍率。
        // 单位是 1/s（角速度环的闭环速率），不是力矩。
        final double strength = this.speedToForce(this.getSpeed())
                * ClockworkConfig.server().gyroStrength.get();
        if (!(strength > 1.0E-6)) {
            return;
        }

        final Quaterniondc orientation = subLevel.logicalPose().orientation();
        final Vector3d upWorld = orientation.transform(new Vector3d(this.localUp), new Vector3d());
        if (!(upWorld.lengthSquared() > 1.0E-9)) {
            return;
        }
        upWorld.normalize();

        final Vector3d omega = handle.getAngularVelocity(new Vector3d());
        // 旧版 :52-53：只阻尼"偏离自转轴"的那部分角速度（绕自转轴的旋转是陀螺的自由度）
        final Vector3d offAxis = new Vector3d(omega).sub(new Vector3d(upWorld).mul(omega.dot(upWorld)));
        // 旧版 :54：ω理想 = up × 世界上 − ω⊥，大小 = sin(倾角)，方向指向"把 up 掰回世界上"
        final Vector3d idealOmega = new Vector3d(upWorld).cross(WORLD_UP, new Vector3d()).sub(offAxis);

        // 见类注释 §2：一阶 ZOH 精确解，替代显式的 s·dt。
        // 这一行是"高转速乱飞"的修复点：k 永远 <1，任意转速/任意子步长都不会把
        // 阻尼项变成正反馈；s·dt ≪ 1（低转速）时与旧版数值一致。
        // 再乘 1/N：Sable 在同一个子步里把所有陀螺的力矩一起排队下发，N 块陀螺看到的是
        // 同一份起始角速度，所以总增益 = N·k，必须按块数摊薄（见类注释 §5）。
        final double share = 1.0 / countGyros(subLevel);
        final Vector3d deltaOmega = new Vector3d(idealOmega)
                .mul((1.0 - Math.exp(-strength * timeStep)) * share);

        // 见类注释 §3：按角加速度封顶（每子步允许改变多少角速度）。份额同样摊薄，
        // 这样 N 块陀螺的"总角加速度上限"仍是一个 gyroMaxAngularAcceleration。
        final double maxAngularAccel = ClockworkConfig.server().gyroMaxAngularAcceleration.get();
        if (maxAngularAccel > 0.0) {
            CWForces.clampInPlace(deltaOmega, maxAngularAccel * timeStep * share);
        }

        // τ_local = I_local · R⁻¹ · Δω / dt（Sable 的 ForceTotal/Rapier 收 local 系角冲量）
        final Vector3d localDelta = orientation.transformInverse(deltaOmega, new Vector3d());
        final Vector3d localTorque = subLevel.getMassTracker().getInertiaTensor()
                .transform(localDelta, new Vector3d())
                .div(timeStep);
        if (!isFinite(localTorque)) {
            // 惯量张量异常（空结构 / NaN）时宁可这一子步不出力，
            // 也不能把 NaN 塞进 Rapier：NaN 姿态会被 Sable 判成非法并直接删掉子空间。
            return;
        }
        CWForces.clampInPlace(localTorque, ClockworkConfig.server().gyroMaxTorque.get());
        CWForces.torque(subLevel, CWForces.magneticForce(), localTorque, timeStep);
    }

    private static boolean isFinite(final Vector3d v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }

    /**
     * 同一个子空间上陀螺方块的数量（至少 1）。
     *
     * <p>为什么需要它：{@code SubLevelPhysicsSystem} 先遍历所有 actor 调
     * {@code sable$physicsTick}，之后才 {@code applyQueuedForces}，所以 N 个陀螺都基于
     * 本子步<b>起始</b>的角速度各算一份 {@code Δω}，效果是相加。k 在高转速已接近 1，
     * N ≥ 3 时 N·k 会越过 {@code k ≤ 4/(2+dt)} 的稳定界（见类注释 §2/§5），
     * 于是又变成"高频正反抽打"。按块数摊薄后总增益恒为 k。
     */
    private static int countGyros(final ServerSubLevel subLevel) {
        int count = 0;
        for (final BlockEntitySubLevelActor actor : subLevel.getPlot().getBlockEntityActors()) {
            if (actor instanceof GyroBlockEntity) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    /**
     * 旧版 {@code GyroShipControl#speedToForce}（:65-68）：把输入轴转速变成 0..100 的控制强度。
     * 逻辑形状（S 曲线）保持不变，只把上限接进配置。
     */
    private double speedToForce(final float speed) {
        final double value = 128.0 / (1.0 + Math.exp(6.0 - speed * 0.05));
        return Math.clamp(value, 0.0, SPEED_FORCE_MAX);
    }

    @Override
    protected void write(final CompoundTag compound, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putDouble("UpX", this.localUp.x);
        compound.putDouble("UpY", this.localUp.y);
        compound.putDouble("UpZ", this.localUp.z);
        compound.putInt("PitchSignal", this.pitchSignal);
        compound.putInt("RollSignal", this.rollSignal);
    }

    @Override
    protected void read(final CompoundTag compound, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        if (compound.contains("UpX")) {
            this.localUp.set(compound.getDouble("UpX"), compound.getDouble("UpY"), compound.getDouble("UpZ"));
        }
        this.pitchSignal = compound.getInt("PitchSignal");
        this.rollSignal = compound.getInt("RollSignal");
    }
}
