package org.valkyrienskies.clockwork.platform;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 子空间的"锁定 / 解锁"（把结构焊在世界坐标系上）。
 *
 * <p>用户需求来自 Aeronautics 的<b>物理手杖</b>（{@code physics_staff}）：那里"锁"这一动作
 * （{@code PhysicsStaffAction.LOCK}）的实现就是给目标子空间插一个固定约束，解锁就是把它移除。
 * 本类<b>逐行照抄它的语义</b>（证据：{@code Simulated-Project-main/.../physics_staff/
 * PhysicsStaffServerHandler.java:83-93 addConstraint}、{@code :151-169 toggleLock}、
 * {@code :208-233 applyLockIfNeeded/removeLock}）：
 * <ul>
 *   <li><b>锁定</b>：{@code pipeline.addConstraint(null, subLevel,
 *       new FixedConstraintConfiguration(pose.position(), pose.rotationPoint(), pose.orientation()))} ——
 *       bodyA 传 {@code null} 表示"世界"，锚点取该子空间<b>当前</b>的 {@code logicalPose()}，
 *       于是结构被钉在当前位姿上；</li>
 *   <li><b>解锁</b>：约束句柄 {@code remove()}。</li>
 * </ul>
 * 为什么参数必须是这三个（javap + 原生层核实）：{@code pos1} 是世界系锚点、{@code pos2} 是
 * <b>plot 系</b>锚点（原生层每个物理刻都算 {@code local_anchor_2 = pos_b - center_of_mass}，
 * 而质心是 plot 局部坐标，见 {@code sable_rapier/src/main/rust/rapier/src/joints.rs:76-95}），
 * 第 3 个参数是 frame1 的旋转（同一文件 {@code :428-441}），而固定关节在每个物理刻只重算两个锚点、
 * <b>不</b>重算关节轴（{@code :64-66} 对 {@code fixed} 关节跳过 {@code set_local_axis1}）。
 *
 * <p>持久化与重建（同样照抄物理手杖）：锁的 UUID 集合按维度存进 {@link SavedData}；
 * 约束句柄<b>不</b>落盘。子空间被（重新）加入容器时<b>只登记</b>，真正的
 * {@code addConstraint} 推迟到本维度的容器 tick（见
 * {@link LockObserver#tick(SubLevelContainer)} 与下文"时序"）。
 *
 * <h2>时序：为什么不能在 {@code onSubLevelAdded} 里加约束</h2>
 *
 * 2026-10-16 18:35 实机崩溃（{@code crash-2026-10-16_18.35.47-server.txt}，物理轴承装配时）：
 * <pre>
 * IllegalArgumentException: Constraint validation failed
 *   at RapierPhysicsPipeline.addConstraint(:533 → configuration.validate)
 *   at FixedConstraintConfiguration.validate(:19 → validateAnchors)
 *   at PhysicsConstraintConfiguration.validateAnchors(:27)
 *       "pos2 does not fall within the plot of the second sub-level in block-coordinates!"
 *   at SubLevelContainer.allocateSubLevel(:266 observers.forEach → onSubLevelAdded)
 * </pre>
 * 根因有两条，缺一不可：
 * <ol>
 *   <li><b>分配中的子空间还没有合法锚点</b>：{@code SubLevelAssemblyHelper.assembleBlocks(:75-77)}
 *       用 {@code new Pose3d()} 起手、<b>只</b>设了 position，{@code rotationPoint} 还是默认的
 *       {@code (0,0,0)}；它要到 {@code :111-123}（方块搬完、质心算出来之后）才被写成 plot 系质心。
 *       而 {@code SubLevelContainer.allocateSubLevel(:262-266)} 的观察者回调发生在这一切<b>之前</b>，
 *       {@code RapierPhysicsPipeline.add(:201-204)} 也只在"质量表已经有质心"时才回写 rotationPoint
 *       （刚分配的空 plot 没有）。于是 {@code validateAnchors} 拿 {@code (0,0,0)} 去问
 *       {@code LevelPlot.contains}(plot 在 plotgrid 里的 2000 万格开外) → 必然 false → 抛异常。
 *       注意 pos1 = {@code position()}（世界系）走的是 {@code container.inBounds} 分支、天然为 false，
 *       所以报错只指向 pos2 —— 崩溃信息与源码逐行对得上。</li>
 *   <li><b>本类当时把"重建"写成了"见到子空间就锁"</b>：原 {@code applyLockIfNeeded} 在表里
 *       <b>没有</b>该 UUID 记录时也会 {@code attachLock}，于是任何新装配出来的结构都会被顺手锁住。
 *       物理手杖的判据是 {@code lock != null && (handle == null || !handle.isValid())}
 *       （{@code PhysicsStaffServerHandler.java:216}），也就是"只有存档里确实记着这把锁才重建"。</li>
 * </ol>
 * 现在的做法：观察者回调只把"存档里记着锁"的子空间放进待重建表；容器 tick（跑在
 * {@code SubLevelPhysicsSystem#tick} 之后，刚体一定已在管线里、位姿已从管线读回）里逐个
 * {@code addConstraint}，并且每次都有前置校验 + try/catch，失败只 WARN 不抛；重试上限
 * {@link #MAX_ATTACH_TICKS} 刻，超时把记录一起删掉（不留"存档说有锁、实际没约束"的半状态）。
 */
public final class CWSubLevelLocks extends SavedData {

    /** 存档里的数据 id（同一维度一份）。 */
    public static final String DATA_ID = "vs_clockwork_sub_level_locks";

    /**
     * 一把"待重建的锁"最多等多少游戏刻（1 秒 = 20 刻）。
     *
     * <p>正常只需要 1 刻：装配链路在同一个 tick 里就把位姿定稿了，下一次容器 tick 即可建约束。
     * 给到 2 秒是为了容忍"载入后位姿/质量尚未就绪"这类慢路径；超时说明这次锁定建不起来，
     * 必须放弃并在日志里留证（而不是每刻刷屏）。
     */
    private static final int MAX_ATTACH_TICKS = 40;

    /**
     * 已锁定的子空间 → 约束句柄。
     *
     * <p>键存在即"已锁定"；值为 {@code null} 表示这一份锁还没建成（约束待重建，见
     * {@link #pendingAttach}）。不用 {@code Set} 是因为解锁需要句柄。
     */
    private final Map<UUID, PhysicsConstraintHandle> constraints = new HashMap<>();

    /** 待重建约束的子空间 → 已经等了多少刻（只在这里记账，避免每刻刷屏 WARN）。 */
    private final Map<UUID, Integer> pendingAttach = new HashMap<>();

    /** 观察者是否已注册（注册入口可能被调用多次）。 */
    private static boolean observerRegistered;

    /**
     * 锁定 / 解锁切换（重力枪 LOCK 模式的右键行为）。
     *
     * @return 切换后的状态：{@code true} = 现在被锁着
     */
    public static boolean toggle(final ServerLevel level, final ServerSubLevel subLevel) {
        final CWSubLevelLocks data = get(level);
        final UUID id = subLevel.getUniqueId();
        if (data.constraints.containsKey(id)) {
            data.detach(id);
            data.setLocksDirty();
            return false;
        }
        // 先记账（值为 null = 锁还没建成），再尝试；失败会由容器 tick 重试
        data.constraints.put(id, null);
        data.attachOrQueue(subLevel);
        data.setLocksDirty();
        return true;
    }

    /** 解锁（未锁定时什么都不做）；供"结构被解体 / 被抓取"等路径调用。 */
    public static void unlock(final @Nullable ServerLevel level, final SubLevel subLevel) {
        if (level == null) {
            return;
        }
        final CWSubLevelLocks data = get(level);
        final UUID id = subLevel.getUniqueId();
        if (!data.constraints.containsKey(id)) {
            return;
        }
        data.detach(id);
        data.setLocksDirty();
    }

    /**
     * 注册"子空间锁会被重建"的观察者；由重力枪的静态初始化调用一次。
     *
     * <p>注册时机与写法对标 Aeronautics：{@code SimulatedCommonEvents.java:135-148}
     * （{@code onSubLevelContainerReady} → {@code serverContainer.addObserver(...)}）。
     */
    public static synchronized void registerObserver() {
        if (observerRegistered) {
            return;
        }
        observerRegistered = true;
        SableEventPlatform.INSTANCE.onSubLevelContainerReady((level, container) -> {
            if (container instanceof final ServerSubLevelContainer server) {
                server.addObserver(new LockObserver(server.getLevel()));
            }
        });
    }

    // ==================== 内部：存档 / 约束 ====================

    /** 锁表变了：标脏让存档落盘（锁是"玩家造出来的状态"，必须持久化）。 */
    private void setLocksDirty() {
        this.setDirty(true);
    }

    private static CWSubLevelLocks get(final ServerLevel level) {
        return level.getChunkSource().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CWSubLevelLocks::new,
                        (CompoundTag nbt, HolderLookup.Provider registries) -> load(nbt), null),
                DATA_ID);
    }

    private static CWSubLevelLocks load(final CompoundTag nbt) {
        final CWSubLevelLocks data = new CWSubLevelLocks();
        for (final Tag tag : nbt.getList(DATA_ID, Tag.TAG_INT_ARRAY)) {
            data.constraints.put(NbtUtils.loadUUID(tag), null);
        }
        return data;
    }

    /** 拆掉一把锁（含尚未建成的）：删记录、移除约束、取消重试。 */
    private void detach(final UUID id) {
        this.pendingAttach.remove(id);
        final PhysicsConstraintHandle handle = this.constraints.remove(id);
        if (handle != null && handle.isValid()) {
            handle.remove();
        }
    }

    /** 立即尝试建约束；失败则登记到待重建表（下一游戏刻再试）。 */
    private void attachOrQueue(final ServerSubLevel subLevel) {
        final UUID id = subLevel.getUniqueId();
        final AttachAttempt attempt = attachLock(subLevel);
        if (attempt.handle() != null) {
            this.constraints.put(id, attempt.handle());
            return;
        }
        this.recordFailure(id, 0, attempt.failure());
    }

    /**
     * 子空间刚被加入容器（{@link SubLevelObserver#onSubLevelAdded}）：<b>只登记，不加约束</b>。
     *
     * <p>两个前提缺一不可：①存档里确实记着这把锁（否则新装配出来的结构会被顺手锁住 —— 这正是
     * 2026-10-16 崩溃的第二条根因）；②当前没有活的句柄（有就不必重建）。
     * 子空间是<b>新实例</b>，旧句柄一律作废：Sable 卸载子空间时会连它的关节一起删掉，
     * 旧句柄的 native 槽位可能已被别人的关节复用，拿它去 {@code remove()} 会拆错东西。
     */
    private void enqueueIfLocked(final SubLevel subLevel) {
        final UUID id = subLevel.getUniqueId();
        if (!this.constraints.containsKey(id) || subLevel.isRemoved()) {
            return;
        }
        final PhysicsConstraintHandle stale = this.constraints.put(id, null);
        if (stale != null && stale.isValid()) {
            stale.remove();
        }
        this.pendingAttach.putIfAbsent(id, 0);
    }

    /**
     * 容器 tick：处理待重建的锁。
     *
     * <p>调用时机见 {@link LockObserver#tick(SubLevelContainer)}。遍历的是键的快照，
     * 循环里可以安全地增删待重建表。
     */
    private void processPending(final SubLevelContainer container) {
        if (this.pendingAttach.isEmpty()) {
            return;
        }
        for (final UUID id : new ArrayList<>(this.pendingAttach.keySet())) {
            final int waited = this.pendingAttach.getOrDefault(id, 0);
            if (!(container.getSubLevel(id) instanceof final ServerSubLevel server) || server.isRemoved()) {
                // 子空间已经不在容器里（卸载 / 已解体）：撤掉重试，等它下次被加入时重新登记
                this.pendingAttach.remove(id);
                continue;
            }
            final AttachAttempt attempt = attachLock(server);
            if (attempt.handle() != null) {
                this.pendingAttach.remove(id);
                this.constraints.put(id, attempt.handle());
            } else {
                this.recordFailure(id, waited, attempt.failure());
            }
        }
    }

    /**
     * 记一次失败：第一刻与"放弃"各打一条 WARN（含子空间 id 与原因），中间不重复刷屏。
     *
     * <p>放弃时把锁记录一起删掉 —— 不留"存档记着锁、实际没有约束"的半状态，玩家再右键即可重锁。
     */
    private void recordFailure(final UUID id, final int waited, final @Nullable String reason) {
        if (waited + 1 >= MAX_ATTACH_TICKS) {
            this.pendingAttach.remove(id);
            this.constraints.remove(id);
            this.setLocksDirty();
            Sable.LOGGER.warn("[CWSubLevelLocks] 放弃锁定子空间 {}（已等 {} 刻）：{}", id, waited + 1, reason);
            return;
        }
        if (waited == 0) {
            Sable.LOGGER.warn("[CWSubLevelLocks] 暂时无法锁定子空间 {}：{}（下一游戏刻重试，最多 {} 刻）",
                    id, reason, MAX_ATTACH_TICKS);
        }
        this.pendingAttach.put(id, waited + 1);
    }

    /**
     * 插一个"世界 ↔ 子空间"的固定约束，锚点取该子空间当前的位姿（照抄物理手杖）。
     *
     * <p>前置校验（顺序即"最可能失败的原因"）：子空间还在容器里 → 管线可用 → 质量已就绪 →
     * 位姿有限。任一条不满足都<b>不</b>调 Sable —— 那些情况 {@code addConstraint} 不是抛异常
     * （崩服务端 tick）就是插出一个锚点离质心两千万格的关节（结构当场被撕碎）。
     * 最后再用 try/catch 兜住 Sable 自己的校验（{@code :533} 那层），失败只回原因，绝不外抛。
     *
     * <p>不改 contacts：物理手杖也不改（{@code PhysicsStaffServerHandler.java:83-93}）。
     * 这里 bodyA 是世界，Sable 的世界碰撞体都挂在同一个 ground body 上。
     *
     * @return 成功给句柄，失败给原因字符串（调用方决定 WARN / 重试）
     */
    private static AttachAttempt attachLock(final ServerSubLevel subLevel) {
        final ServerLevel level = subLevel.getLevel();
        if (subLevel.isRemoved()) {
            return AttachAttempt.failure("子空间已被标记移除");
        }
        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || container.getSubLevel(subLevel.getUniqueId()) != subLevel) {
            return AttachAttempt.failure("子空间已不在容器里（区块卸载 / 已被替换）");
        }
        final PhysicsPipeline pipeline = CWSubLevels.pipeline(level);
        if (pipeline == null) {
            return AttachAttempt.failure("取不到物理管线（非服务端维度或容器未初始化）");
        }
        // 判据用"自身质量表"（{@code getSelfMassTracker}），不是合并表（{@code getMassTracker}）：
        // 自身表在装配搬方块、读档逐块回填时就被增量维护
        // （{@code ServerLevelPlot.java:611-614 → SubLevelPhysicsSystem#updateMassDataFromBlockChange:517-531
        // → MassTracker#addBlockMass:197-215}），而合并表只在物理子步里刷新
        // （{@code SubLevelPhysicsSystem.java:263}）—— 物理暂停时它会一直停在 mass=0，
        // 拿它当判据会让"读档恢复锁"永远建不起来。空 plot（分配中/读档搬方块之前）两张表都是 invalid，
        // 所以这条校验照样挡得住崩溃那一幕。
        final MassData mass = subLevel.getSelfMassTracker();
        if (mass.isInvalid()) {
            return AttachAttempt.failure("质量未就绪：mass=" + mass.getMass()
                    + " centerOfMass=" + mass.getCenterOfMass());
        }
        final Pose3dc pose = subLevel.logicalPose();
        // bodyA 恒为 null（世界）、bodyB 恒为该子空间，两端天然不同一，不需要额外判"同一刚体"
        if (!pose.position().isFinite() || !pose.rotationPoint().isFinite() || !pose.orientation().isFinite()) {
            return AttachAttempt.failure("位姿非有限：position=" + pose.position()
                    + " rotationPoint=" + pose.rotationPoint());
        }
        try {
            final PhysicsConstraintHandle handle = pipeline.addConstraint(null, subLevel,
                    new FixedConstraintConfiguration(pose.position(), pose.rotationPoint(), pose.orientation()));
            if (handle == null || !handle.isValid()) {
                return AttachAttempt.failure("管线没有返回有效的约束句柄");
            }
            return new AttachAttempt(handle, null);
        } catch (final RuntimeException e) {
            return AttachAttempt.failure(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @Override
    public @NotNull CompoundTag save(final @NotNull CompoundTag tag, final @NotNull HolderLookup.Provider registries) {
        final ListTag locks = new ListTag();
        for (final UUID id : this.constraints.keySet()) {
            locks.add(NbtUtils.createUUID(id));
        }
        tag.put(DATA_ID, locks);
        return tag;
    }

    /** 一次加约束尝试的结果：成功给句柄，失败给原因（用于日志）。 */
    private record AttachAttempt(@Nullable PhysicsConstraintHandle handle, @Nullable String failure) {

        private static AttachAttempt failure(final String reason) {
            return new AttachAttempt(null, reason);
        }
    }

    /**
     * 按维度挂到容器上的观察者：负责"载入后重建"与"结构没了就清锁"。
     *
     * <p>对应 {@code PhysicsStaffSubLevelObserver}（同一个接口）。
     */
    private static final class LockObserver implements SubLevelObserver {

        private final ServerLevel level;

        private LockObserver(final ServerLevel level) {
            this.level = level;
        }

        @Override
        public void onSubLevelAdded(final SubLevel subLevel) {
            get(this.level).enqueueIfLocked(subLevel);
        }

        @Override
        public void onSubLevelRemoved(final SubLevel subLevel, final SubLevelRemovalReason reason) {
            if (reason == SubLevelRemovalReason.REMOVED) {
                unlock(this.level, subLevel);
            }
        }

        /**
         * 每游戏刻一次，时机由 Sable 保证：{@code ServerLevel.tick} 的 HEAD 调
         * {@code SubLevelContainer.tick()}，观察者按注册顺序跑，本观察者排在
         * {@code SubLevelPhysicsSystem} 之后 —— 也就是说此刻该维度所有物理子步都跑完了，
         * 刚体一定已经在管线里（{@code SubLevelPhysicsSystem.onSubLevelAdded:192 pipeline.add}），
         * 位姿也已经从管线读回（{@code updateAllPoses:286-306}）。这是加约束最安全的时机：
         * 既不在分配过程中，也不在 {@code IN_PHYSICS_STEP} 的物理步里。
         */
        @Override
        public void tick(final SubLevelContainer subLevels) {
            get(this.level).processPending(subLevels);
        }
    }
}
