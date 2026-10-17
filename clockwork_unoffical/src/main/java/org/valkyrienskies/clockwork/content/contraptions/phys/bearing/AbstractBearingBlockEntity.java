package org.valkyrienskies.clockwork.content.contraptions.phys.bearing;

import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.IDisplayAssemblyExceptions;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintHandle;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.clockwork.platform.CWAssembly;
import org.valkyrienskies.clockwork.platform.CWForces;
import org.valkyrienskies.clockwork.platform.CWSubLevels;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 物理轴承家族的公共骨架（装配 / 解体 / 约束 / 伺服 / 持久化 / 同步只写一次）。
 *
 * <p>对标 {@code SwivelBearingBlockEntity}；对应旧版 {@code PhysBearingBlockEntity} /
 * {@code PropellerBearingBlockEntity} / {@code FlapBearingBlockEntity} /
 * {@code CopterBearingBlockEntity}（旧版把这套逻辑抄了四遍）。
 *
 * <p>三条链路都走 {@code platform} 薄封装：装配 = {@code SimAssemblyHelper#assembleFromSingleBlock}；
 * 约束 = {@link RotaryConstraintConfiguration} + {@code PhysicsPipeline#addConstraint}；
 * 施力 = {@code PhysicsConstraintHandle#setMotor} 伺服（等价旧版 {@code VSRevoluteJoint + BearingController}）。
 *
 * <p><b>伺服刷新节奏（实机"转着转着抽搐一下"的根因，勿改回游戏刻）</b>：
 * Sable 一个游戏刻跑 {@code PhysicsConfigData.substepsPerTick} 个物理子步
 * （默认 2，见 {@code SableServerConfig.SUB_LEVEL_SUBSTEPS_PER_TICK}），
 * 而 {@code SubLevelPhysicsSystem#getPartialPhysicsTick()} 的返回值是
 * {@code (currentSubstep + 1) / substepsPerTick}（源码 {@code SubLevelPhysicsSystem.java:552-554}），
 * 物理子步之外它停在 {@code substeps/substeps = 1.0}。
 * 因此伺服必须在<b>每个物理子步</b>里刷新：目标角用
 * {@code AngleHelper.angleLerp(partialPhysicsTick, 上一刻目标角, 当前目标角)} 插值，才能把
 * "一游戏刻的目标角增量"平摊到各子步。参考实现 SwivelBearing 正是这么做的 ——
 * {@code SwivelBearingPlateBlockEntity#sable$physicsTick} 每子步调
 * {@code SwivelBearingBlockEntity#updateServoCoefficients}（源码
 * {@code SwivelBearingPlateBlockEntity.java:122-130}），目标角在
 * {@code SwivelBearingBlockEntity.java:395} 用 {@code physicsSystem.getPartialPhysicsTick()} 插值。
 *
 * <p><b>与 SwivelBearing 逐行对齐后的三条硬性规则</b>（每一条都对应一次实机抽搐）：
 * <ol>
 *   <li><b>插值必须用 {@code AngleHelper.angleLerp}</b>（跨 0 点安全），不能用朴素线性插值 ——
 *       否则每转一圈会在折回点被要求倒拧半圈。详见 {@link BearingControl.Context#interpolatedDegrees()}。</li>
 *   <li><b>等效惯量每个子步重算</b>（Swivel: {@code SwivelBearingBlockEntity.java:375-389} 每次调用都算）。
 *       缓存一个游戏刻会让 kP/kD 在质量突变（方块增减 / 装配）后仍按旧值出力，产生一次大过冲。</li>
 *   <li><b>目标角推进时唤醒两侧刚体</b>（Swivel: {@code SwivelBearingBlockEntity.java:242-256}）。
 *       休眠中的刚体不参与求解，伺服力矩会被整段丢弃；等被别的力唤醒时再把攒下的角度差一次补回来，
 *       表现就是"停一下、猛抽一下"。</li>
 * </ol>
 * 另外<b>不做游戏刻兜底刷新</b>：子步循环每刻都会跑（物理暂停时既不推进目标角也不刷新，
 * 电机系数留在关节里，恢复后自动继续），而 {@code SubLevelPhysicsSystem.java:297} 在子步循环结束后
 * 把 {@code currentSubstep} 设成 {@code substepsPerTick}，此时 {@code getPartialPhysicsTick()}
 * 返回 {@code (n+1)/n > 1}，在游戏刻里拿它插值会外推过头 50%。
 *
 * <p>本类与它的差异：Clockwork 的轴承本体不进入子空间（被装配的是"朝向那一侧的方块"），
 * 所以它不会出现在任何 plot 的 actor 列表里，{@code BlockEntitySubLevelActor#sable$physicsTick}
 * 永远不会被调到。改用 Sable 的全局回调
 * {@code SableEventPlatform.INSTANCE.onPhysicsTick(...)}（同样是每物理子步一次，
 * 见 {@code SableEventPlatform.java:17-25} 的 javadoc）。该回调在
 * {@code SubLevelPhysicsSystem.tickPipelinePhysics} 里位于"每个子空间的 {@code prePhysicsTick}（actor 刷新）
 * 之后、{@code applyQueuedForces} / {@code physicsTick} 之前"（{@code SubLevelPhysicsSystem.java:266-279}），
 * 与 Swivel 的 actor 路径处在同一个子步的同一阶段。
 *
 * <p>拆解锚点是"朝向那一格的 plot 坐标"，映射回世界后结构落回原位。
 */
public abstract class AbstractBearingBlockEntity extends KineticBlockEntity
        implements IDisplayAssemblyExceptions, BlockEntitySubLevelActor {

    @Nullable
    private UUID subLevelID;
    /** 装配时 "plot 中心方块 - 搜索起点方块" 的偏移；持久化以便重载后重建约束。 */
    @Nullable
    private BlockPos assembleOffset;
    @Nullable
    private PhysicsConstraintHandle handle;
    @Nullable
    private AssemblyException lastException;
    /** 右键后下一 tick 再执行装配/解体（避免在交互回调里搬方块）。 */
    protected boolean assembleNextTick;
    /** 正在搬方块（装配/解体/被搬走），此期间的 remove() 不应递归拆结构。 */
    private boolean moving;
    /**
     * "加约束失败"这条 WARN 只打一次的闸门。
     *
     * <p>{@code checkPersistence} 每刻都会在 {@code handle == null} 时重试，而 Sable 的锚点校验
     * （{@link dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintConfiguration#validateAnchors}）
     * 一旦不成立就会持续不成立 —— 不设闸门就是每刻一条 WARN。成功建起约束后复位。
     */
    private boolean constraintAttachWarned;
    protected double targetAngleDegrees;
    protected double lastTargetAngleDegrees;

    /**
     * 活动轴承表（身份语义：同一时刻同一个方块实体只会有一条）。
     *
     * <p>物理子步回调是全局的，需要知道"哪些轴承现在有活的约束"。用 {@link java.util.IdentityHashMap}
     * 而不是 {@code WeakHashMap}：{@code BlockEntity} 不重写 {@code equals/hashCode}，
     * 这里要的就是引用身份。条目在拆解 / 子空间消失 / 方块实体被移除时删除（见
     * {@code checkPersistence} 与 {@link #physicsSubstep}）。
     */
    private static final Set<AbstractBearingBlockEntity> ACTIVE =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    /** 遍历用的快照，避免在 refreshServo 里改表导致 {@code ConcurrentModificationException}。 */
    private static final List<AbstractBearingBlockEntity> SCRATCH = new java.util.ArrayList<>();
    /** 回调是否已注册（注册入口可能被调用多次，只允许生效一次）。 */
    private static boolean hookRegistered;

    protected AbstractBearingBlockEntity(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    // ==================== 物理子步伺服回调 ====================

    /**
     * 注册"每物理子步刷新伺服"的全局回调；由 {@code CWContraptionsIndex#register()} 调用一次。
     */
    public static void registerPhysicsHook() {
        if (hookRegistered) {
            return;
        }
        hookRegistered = true;
        SableEventPlatform.INSTANCE.onPhysicsTick(AbstractBearingBlockEntity::physicsSubstep);
    }

    /**
     * 一个物理子步：刷新所有属于该维度的活动轴承的伺服电机。
     *
     * <p>这就是本类与 SwivelBearing 唯一必须对齐的地方 —— 见类注释。
     *
     * @param physicsSystem 正在步进的物理系统（一个维度只有一个）
     * @param timeStep      本子步步长 [s]，即 {@code 1 / 20 / substepsPerTick}
     */
    private static void physicsSubstep(final SubLevelPhysicsSystem physicsSystem, final double timeStep) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        SCRATCH.clear();
        SCRATCH.addAll(ACTIVE);
        for (final AbstractBearingBlockEntity bearing : SCRATCH) {
            if (bearing.isRemoved()) {
                ACTIVE.remove(bearing);
            } else if (bearing.level == physicsSystem.getLevel()) {
                bearing.refreshServo();
            }
        }
        SCRATCH.clear();
    }

    // ==================== 子类需要提供 ====================

    /** 轴承朝向：决定转动轴与装配方向。 */
    protected abstract Direction getFacing();

    /** 旋转控制策略。 */
    protected abstract BearingControl getControl();

    /** 装配搜索起点（朝向的相邻格）。 */
    protected BlockPos getAssemblyOrigin() {
        return this.worldPosition.relative(this.getFacing());
    }

    /** 子类每刻的服务端逻辑。 */
    protected void onServerTick() {
    }

    // ==================== 状态 ====================

    public boolean isAssembled() {
        return this.subLevelID != null;
    }

    public @Nullable UUID getSubLevelID() {
        return this.subLevelID;
    }

    /**
     * 装配锚点在<b>子空间 plot 坐标</b>里的位置（= 朝向邻格 + {@code assembleOffset}）。
     *
     * <p>约束的 attach 端用的就是这个点（见 {@link #attachConstraint}），
     * 渲染托板/连杆时也要用它把子空间的位姿换算回本地，所以这里开放一个只读入口。
     * 未装配时返回 {@code null}。
     */
    public @Nullable BlockPos getAssembledAnchor() {
        return this.assembleOffset == null ? null : this.getAssemblyOrigin().offset(this.assembleOffset);
    }

    public double getTargetAngleDegrees() {
        return this.targetAngleDegrees;
    }

    public void setTargetAngleDegrees(final double degrees) {
        // 统一折回 [0,360)，理由见 wrapDegrees
        this.targetAngleDegrees = wrapDegrees(degrees);
        this.lastTargetAngleDegrees = this.targetAngleDegrees;
    }

    public void toggleAssembled() {
        this.assembleNextTick = true;
    }

    /** 轴承本身被别的装置搬走前：先把自己的子结构放回世界，并保持 moving 以免 remove() 再拆一次。 */
    public void onBeforeMovedByAssembly() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        this.moving = true;
        if (this.isAssembled()) {
            this.disassemble();
            this.moving = true;
        }
    }

    /** 搬完之后的方块实体：坐标系已变，装配信息一律作废（约束交给 checkPersistence 重建）。 */
    public void onAfterMovedByAssembly() {
        this.moving = false;
        this.removeHandle();
        this.subLevelID = null;
        this.assembleOffset = null;
    }

    protected @Nullable ServerSubLevel getAttachedSubLevel() {
        if (this.level == null || this.subLevelID == null) {
            return null;
        }
        if (CWSubLevels.byId(this.level, this.subLevelID) instanceof final ServerSubLevel server
                && !server.isRemoved()) {
            return server;
        }
        return null;
    }

    // ==================== tick ====================

    @Override
    public void tick() {
        super.tick();
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        if (this.assembleNextTick) {
            this.assembleNextTick = false;
            if (this.isAssembled()) {
                this.disassemble();
            } else {
                this.assemble();
            }
        }
        final ServerSubLevel attached = this.getAttachedSubLevel();
        this.checkPersistence(attached);
        if (attached != null) {
            // 只推进目标角；伺服系数一律由物理子步回调刷新（见类注释的"三条硬性规则"）
            this.advanceTargetAngle();
        }
        this.onServerTick();
    }

    /** 目标角随输入轴推进（度/刻）；物理暂停时不动。 */
    private void advanceTargetAngle() {
        this.lastTargetAngleDegrees = this.targetAngleDegrees;
        if (CWSubLevels.paused(this.level)) {
            return;
        }
        final float maxSpeed = this.getControl().maxSpeed();
        final float rpm = maxSpeed > 0 ? Mth.clamp(this.getSpeed(), -maxSpeed, maxSpeed) : this.getSpeed();
        // convertToAngular: RPM -> 度/刻（Create 约定）
        double angularSpeed = convertToAngular(rpm);
        // 朝负轴方向时旋转反向（对标 SwivelBearing 的 AxisDirection 处理 / 旧版 WEST,NORTH,DOWN 判断）
        if (this.getFacing().getAxisDirection() == Direction.AxisDirection.NEGATIVE) {
            angularSpeed = -angularSpeed;
        }
        this.targetAngleDegrees = wrapDegrees(this.getControl().advanceDegrees(this.targetAngleDegrees, angularSpeed));

        // 目标角真的动了就唤醒两侧刚体：休眠的刚体不参与求解，伺服力矩会被整段丢掉，
        // 等被唤醒时再一次性补上攒下的角度差 —— 实机就是"停一下、猛抽一下"。
        // 对标 SwivelBearingBlockEntity.java:242-256（它每刻在目标角推进成功后 wakeUp 两个子空间）。
        if (angularSpeed != 0.0 && this.targetAngleDegrees != this.lastTargetAngleDegrees) {
            CWSubLevels.wakeUp(this.level, this.getAttachedSubLevel());
            CWSubLevels.wakeUp(this.level, CWSubLevels.containingServer(this.level, this.worldPosition));
        }
    }

    /**
     * 把角度折回 [0, 360)。
     *
     * <p>为什么折：{@link BearingControl#advanceDegrees} 用 Java 的 {@code %} 取模，对负数返回负数 ——
     * 反转的轴承或往复摆动的襟翼（{@link BearingControls.Oscillating} 返回 {@code sin(phase) × 幅度}）
     * 会给出 {@code -0.1} 这样的目标角；而 {@code addBehaviours} / CC 外设给的绝对角也可能超出
     * 一圈。折回后目标角始终落在同一圈里，便于比较与显示。
     *
     * <p><b>注意</b>：折回本身<b>不</b>解决"跨 0 点时伺服被倒拧"的问题 —— 359.9° → 0.1° 依然是
     * 一跳一整圈。真正解决它的是插值改用 {@link net.createmod.catnip.math.AngleHelper#angleLerp}
     * （见 {@link BearingControl.Context#interpolatedDegrees()}），它对两个端点之间的差取最短角。
     */
    private static double wrapDegrees(final double degrees) {
        final double wrapped = degrees % 360.0;
        return wrapped < 0.0 ? wrapped + 360.0 : wrapped;
    }

    // ==================== 装配 / 解体 ====================

    /** 装配：把朝向一侧的连通结构搬进一个新的子空间，并建立约束。 */
    public void assemble() {
        if (this.level == null || this.level.isClientSide || this.isAssembled()) {
            return;
        }
        if (!CWAssembly.isAreaLoaded(this.level, this.getAssemblyOrigin(), 1)) {
            return;
        }
        this.moving = true;
        try {
            final SimAssemblyHelper.AssemblyResult result =
                    CWAssembly.assembleSingle(this.level, this.worldPosition, this.getAssemblyOrigin(), false, false);
            this.lastException = null;
            if (result != null) {
                this.subLevelID = result.subLevel().getUniqueId();
                this.assembleOffset = result.offset();
                this.targetAngleDegrees = 0.0;
                this.lastTargetAngleDegrees = 0.0;
                // 加入活动表：物理子步回调只服务表里的轴承
                ACTIVE.add(this);
            }
        } catch (final AssemblyException e) {
            this.lastException = e;
        } finally {
            this.moving = false;
        }
        this.sendData();
    }

    /** 解体：把子空间里的方块放回世界原位。 */
    public void disassemble() {
        if (this.level == null || this.level.isClientSide || !this.isAssembled()) {
            return;
        }
        this.moving = true;
        try {
            this.removeHandle();
            final ServerSubLevel attached = this.getAttachedSubLevel();
            if (attached != null && this.assembleOffset != null) {
                // 子空间内的锚点（朝向邻格的 plot 坐标）映射回世界里的同一格
                final BlockPos localAnchor = this.getAssemblyOrigin().offset(this.assembleOffset);
                CWAssembly.disassemble(this.level, attached, localAnchor, this.getAssemblyOrigin(),
                        Rotation.NONE, true);
            }
            this.subLevelID = null;
            this.assembleOffset = null;
            this.targetAngleDegrees = 0.0;
            this.lastTargetAngleDegrees = 0.0;
            ACTIVE.remove(this);
        } finally {
            this.moving = false;
        }
        this.sendData();
    }

    // ==================== 约束与伺服 ====================

    /** 重载后 / 子空间被移走后重建或清理约束。 */
    private void checkPersistence(final @Nullable ServerSubLevel attached) {
        if (this.handle != null && !this.handle.isValid()) {
            this.handle = null;
        }
        if (this.subLevelID == null) {
            this.removeHandle();
            ACTIVE.remove(this);
            return;
        }
        if (attached == null) {
            // 区块没加载时不要急着判定"子空间没了"
            if (!CWAssembly.isAreaLoaded(this.level, this.worldPosition, 1)) {
                return;
            }
            this.removeHandle();
            this.subLevelID = null;
            this.assembleOffset = null;
            ACTIVE.remove(this);
            this.sendData();
            return;
        }
        if (this.handle == null) {
            this.attachConstraint(attached);
        }
        // 重载后约束是重建的，这里把轴承重新纳入物理子步刷新
        ACTIVE.add(this);
    }

    /**
     * 建立连接约束。默认几何照抄 SwivelBearing：铰链两端取「朝向邻格的世界中心」与
     * 「该格装配后在子空间里的中心」，法向都是朝向单位向量，于是铰链轴就是输入轴的轴。
     * 子类可换成别的约束（万向轴承用只锁平移的 {@code FreeConstraintConfiguration}）。
     */
    protected PhysicsConstraintConfiguration<?> createConstraint(final Vector3d anchorPos, final Vector3d attachPos,
                                                                 final Vector3d axis) {
        return new RotaryConstraintConfiguration(anchorPos, attachPos, axis, new Vector3d(axis));
    }

    /** 伺服电机作用的关节轴；单轴转动铰链用 Sable 的默认轴（ANGULAR_X）。 */
    protected ConstraintJointAxis driveAxis() {
        return RotaryConstraintHandle.DEFAULT_AXIS;
    }

    private void attachConstraint(final ServerSubLevel attached) {
        if (this.level == null || this.assembleOffset == null) {
            return;
        }
        final PhysicsPipeline pipeline = CWSubLevels.pipeline(this.level);
        if (pipeline == null) {
            return;
        }
        final Direction facing = this.getFacing();
        final Vector3d axis = JOMLConversion.toJOML(Vec3.atLowerCornerOf(facing.getNormal()));
        final Vector3d anchorPos = JOMLConversion.toJOML(this.worldPosition.relative(facing).getCenter());
        final Vector3d attachPos = JOMLConversion
                .toJOML(this.getAssemblyOrigin().offset(this.assembleOffset).getCenter())
                .sub(new Vector3d(axis).mul(0.001));

        final ServerSubLevel containing = CWSubLevels.containingServer(this.level, this.worldPosition);
        if (containing == attached) {
            return;
        }
        this.removeHandle();
        // 守卫：Sable 的 addConstraint 会做锚点校验（PhysicsConstraintConfiguration#validateAnchors：
        // 两端的锚点必须各自落在对应子空间的 plot 里），不满足时抛 IllegalArgumentException。
        // 这里是从方块实体 tick（checkPersistence）调的，异常会直接崩服务端 —— 2026-10-16 的
        // CWSubLevelLocks 崩溃就是同一类（都是"约束几何还没就绪就 addConstraint"）。
        // 装配刚结束的一刻最容易不成立，所以吞掉、留一条 WARN，下一游戏刻由 checkPersistence 重试。
        try {
            this.handle = pipeline.addConstraint(containing, attached,
                    this.createConstraint(anchorPos, attachPos, axis));
        } catch (final RuntimeException e) {
            this.handle = null;
            if (!this.constraintAttachWarned) {
                this.constraintAttachWarned = true;
                Sable.LOGGER.warn("[CWPhysicsBearing] 无法在 {} 与子空间 {} 之间建立轴承约束：{}；本次跳过，下一游戏刻重试",
                        this.worldPosition, attached.getUniqueId(), e.toString());
            }
            return;
        }
        this.constraintAttachWarned = false;
        if (this.handle != null) {
            this.handle.setContactsEnabled(false);
        }
        CWSubLevels.wakeUp(this.level, attached);
        CWSubLevels.wakeUp(this.level, containing);
    }

    /**
     * 刷新伺服系数：这是"物理轴承"与"自由铰链"的唯一区别。
     *
     * <p>每个<b>物理子步</b>调用一次（由 {@link #physicsSubstep} 驱动），目标角用当前子步的
     * {@code partialPhysicsTick} 插值，因此伺服拿到的是"子步分辨率"的连续目标，而不是
     * 一游戏刻一跳的阶梯 —— 后者就是实机"转着转着抽搐一下"的来源，见类注释。
     */
    private void refreshServo() {
        // 约束尚未建立 / 子空间没了时会被调到，一律先判空
        if (this.level == null || this.level.isClientSide || this.isRemoved()
                || this.assembleOffset == null || this.subLevelID == null) {
            return;
        }
        if (this.handle == null || !this.handle.isValid()) {
            this.handle = null;
            return;
        }
        final ServerSubLevel containing = CWSubLevels.containingServer(this.level, this.worldPosition);
        final ServerSubLevel attached = this.getAttachedSubLevel();
        if (attached == null || containing == attached) {
            return;
        }

        final BearingControl control = this.getControl();
        final BearingControl.Context ctx = new BearingControl.Context(this.level, this.worldPosition, attached,
                this.lastTargetAngleDegrees, this.targetAngleDegrees,
                CWSubLevels.partialPhysicsTick(this.level),
                this.level.getBestNeighborSignal(this.worldPosition));

        if (!control.driven(ctx)) {
            this.applyFriction(this.handle, control);
            return;
        }
        final Vector3dc axis = JOMLConversion.toJOML(Vec3.atLowerCornerOf(this.getFacing().getNormal()));
        final double inertia = this.effectiveInertia(containing, attached, axis);
        this.applyServo(this.handle, ctx, control.goalRadians(ctx),
                control.stiffness() * inertia, control.damping() * inertia);
    }

    /**
     * 绕转动轴的等效转动惯量 [kg·m²]，作为伺服增益的尺度（与 SwivelBearing 完全一致：
     * {@code kP = stiffness × I}、{@code kD = damping × I}，见
     * {@code SwivelBearingBlockEntity.java:386-395}）。
     *
     * <p>惯量张量用的是 Sable 的"合并质量"（{@code ServerSubLevel#getMassTracker()} 返回
     * {@code MergedMassTracker}，源码 {@code ServerSubLevel.java:488-491}），它的
     * {@code update()} 每个物理子步都会被调用（{@code SubLevelPhysicsSystem.java:263}）。
     * <b>每次调用都重算</b>（不做游戏刻缓存）：SwivelBearing 也是每子步重算
     * （{@code SwivelBearingBlockEntity.java:375-389}）。缓存一个游戏刻会让方块增减 /
     * 结构被装上装下之后 kP/kD 仍按旧惯量出力，恢复正确值的那一子步就是一次过冲。
     *
     * @param axis 转动轴（世界系，与传给 {@code setMotor} 的轴同一个约定）
     */
    private double effectiveInertia(final @Nullable ServerSubLevel containing, final ServerSubLevel attached,
                                    final Vector3dc axis) {
        final double inertiaA = containing != null ? CWForces.inertiaAlong(containing, axis) : Double.MAX_VALUE;
        final double inertiaB = CWForces.inertiaAlong(attached, axis);
        // 两边都在子空间里时取较大的惯量（更保守），只有一边有的时候取有的那个；
        // Sable 的合并惯量对极端结构可能给出非有限值（NaN/Inf），此时退回下限，宁软不炸。
        final double picked = containing != null ? Math.max(inertiaA, inertiaB) : Math.min(inertiaA, inertiaB);
        return Double.isFinite(picked) ? Math.max(10.0, picked) : 10.0;
    }

    /** 施加伺服力矩（子类可覆盖以驱动多个关节轴）。 */
    protected void applyServo(final PhysicsConstraintHandle constraint, final BearingControl.Context ctx,
                              final double goalRadians, final double stiffness, final double damping) {
        constraint.setMotor(this.driveAxis(), goalRadians, stiffness, damping, false, 0.0);
        constraint.setContactsEnabled(false);
    }

    /** 未驱动时的被动摩擦（子类可覆盖以驱动多个关节轴）。 */
    protected void applyFriction(final PhysicsConstraintHandle constraint, final BearingControl control) {
        constraint.setMotor(this.driveAxis(), 0.0, 0.0, control.friction(), false, 0.0);
    }

    private void removeHandle() {
        if (this.handle != null) {
            this.handle.remove();
            this.handle = null;
        }
    }

    // ==================== 生命周期 / 持久化 ====================

    @Override
    public void invalidate() {
        super.invalidate();
        this.removeHandle();
    }

    @Override
    public void remove() {
        // moving == true 说明我们正被搬进/搬出子空间，此时不能反向拆结构
        if (this.level != null && !this.level.isClientSide && !this.moving && this.isAssembled()
                && CWAssembly.isAreaLoaded(this.level, this.worldPosition, 1)) {
            this.disassemble();
        }
        this.removeHandle();
        super.remove();
    }

    @Override
    protected void write(final CompoundTag compound, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        if (this.subLevelID != null) {
            compound.putUUID("SubLevelID", this.subLevelID);
        }
        if (this.assembleOffset != null) {
            compound.put("AssembleOffset", NbtUtils.writeBlockPos(this.assembleOffset));
        }
        compound.putDouble("TargetAngle", this.targetAngleDegrees);
        AssemblyException.write(compound, registries, this.lastException);
    }

    @Override
    protected void read(final CompoundTag compound, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        this.subLevelID = compound.hasUUID("SubLevelID") ? compound.getUUID("SubLevelID") : null;
        this.assembleOffset = compound.contains("AssembleOffset")
                ? NbtUtils.readBlockPos(compound, "AssembleOffset").orElse(null) : null;
        this.targetAngleDegrees = compound.getDouble("TargetAngle");
        this.lastTargetAngleDegrees = this.targetAngleDegrees;
        this.lastException = AssemblyException.read(compound, registries);
    }

    @Override
    public AssemblyException getLastAssemblyException() {
        return this.lastException;
    }

    /** 子类在装配前置检查失败时设置原因（会通过 {@code IDisplayAssemblyExceptions} 显示给玩家）。 */
    protected void setLastAssemblyException(final @Nullable AssemblyException exception) {
        this.lastException = exception;
    }

    @Override
    public @Nullable Iterable<SubLevel> sable$getConnectionDependencies() {
        final ServerSubLevel attached = this.getAttachedSubLevel();
        return attached == null ? null : List.of(attached);
    }
}
