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
 * 陀螺方块实体。
 *
 * <p>对应旧版 {@code content/physicalities/gyro/GyroBlockEntity} +
 * {@code content/physicalities/gyro/GyroShipControl}。
 *
 * <p><b>施力链路</b>：旧版把控制逻辑挂成 VS2 的 {@code ShipPhysicsListener} 附件，
 * 在 {@code physTick} 里 {@code physShip.applyWorldTorque(idealTorque)}；
 * 移植后改为实现 Sable 的 {@link BlockEntitySubLevelActor}，
 * 在 {@code sable$physicsTick} 里把力矩记进力分组 —— 这是 Sable 唯一被允许在物理子步里施力的入口
 * （{@code ServerSubLevel} 每个物理子步开始时都会 reset 力分组，游戏刻里加的力会被清掉）。
 *
 * <p>控制律与旧版一致：
 * <pre>
 *   up      = 陀螺自身"上"方向（由红石倾斜）经结构姿态变换到世界系
 *   ω⊥      = ω − up·(ω·up)          偏离自转轴的那部分角速度（需要被消掉）
 *   ω理想   = up × (0,1,0) − ω⊥      一边把 up 掰向世界上，一边阻尼摆动
 *   τ_local = I_local · R⁻¹ · ω理想   再乘以由输入轴转速决定的强度
 * </pre>
 *
 * <p>为什么是 local：Sable 的 {@code ForceTotal}/Rapier 接口收的是 local 力矩
 * （{@code sable_rapier} 的 {@code applyForceAndTorque} 里先 {@code rb.rotation().mul_vec3(torque)}
 * 再 {@code apply_torque_impulse}）。旧版 VS2 的 {@code applyWorldTorque} 收世界系，
 * 所以旧版写 {@code R·I·Rᵀ·ω}；两者数值等价（局部表示 = {@code I·R⁻¹·ω}），不要按旧版公式再乘 R。
 */
public class GyroBlockEntity extends KineticBlockEntity implements BlockEntitySubLevelActor {

    /** 旧版 {@code GyroShipControl#speedToForce} 的上限与形状常数。 */
    private static final double SPEED_FORCE_MAX = 100.0;

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
        // 由红石倾斜"上"方向：每 15 级信号倾斜 90°（照抄旧版）
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
        // 即横滚 = west - east。这里原先写成 east - west，与旧版方向相反
        // （2026-10-18 按旧版源码逐行核对后修正；俯仰的 north - south 本来就一致）。
        this.rollSignal = west - east;
    }

    @Override
    public void sable$physicsTick(final ServerSubLevel subLevel, final RigidBodyHandle handle, final double timeStep) {
        final double strength = this.speedToForce(this.getSpeed());
        if (strength <= 0.0) {
            return;
        }

        final Quaterniondc orientation = subLevel.logicalPose().orientation();
        final Vector3d upWorld = orientation.transform(new Vector3d(this.localUp), new Vector3d());
        final Vector3d omega = handle.getAngularVelocity(new Vector3d());
        final Vector3d offAxis = new Vector3d(omega).sub(new Vector3d(upWorld).mul(omega.dot(upWorld)));
        final Vector3d worldUp = new Vector3d(0.0, 1.0, 0.0);
        final Vector3d idealOmega = new Vector3d(upWorld).cross(worldUp, new Vector3d()).sub(offAxis);

        // τ_local = I_local · R⁻¹ · ω理想：Sable 的 ForceTotal/Rapier 接口收的是 local 力矩
        // （sable_rapier lib.rs: rb.rotation().mul_vec3(torque) 之后才 apply_torque_impulse），
        // 所以这里绝不能按“世界系 τ = R·I·Rᵀ·ω理想”再乘一次 R，否则斜着的结构会被往错误方向掰。
        // 等价换算：R⁻¹·(R·I·Rᵀ·ω理想) = I·(R⁻¹·ω理想)，即下面两行。
        final Vector3d localIdeal = orientation.transformInverse(idealOmega, new Vector3d());
        final Vector3d localTorque = subLevel.getMassTracker().getInertiaTensor()
                .transform(localIdeal, new Vector3d());
        localTorque.mul(strength * ClockworkConfig.server().gyroStrength.get());
        CWForces.clampInPlace(localTorque, ClockworkConfig.server().gyroMaxTorque.get());
        CWForces.torque(subLevel, CWForces.magneticForce(), localTorque, timeStep);
    }

    /**
     * 旧版 {@code GyroShipControl#speedToForce}：把输入轴转速变成 0..100 的控制强度。
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
