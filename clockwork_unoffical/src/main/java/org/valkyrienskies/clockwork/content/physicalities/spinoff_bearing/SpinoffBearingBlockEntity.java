package org.valkyrienskies.clockwork.content.physicalities.spinoff_bearing;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintHandle;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.valkyrienskies.clockwork.config.ClockworkConfig;
import org.valkyrienskies.clockwork.platform.CWSubLevels;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 对接口（惰性轴承）方块实体：把两个结构的转动连起来，但不传递任何力。
 *
 * <p>对应旧版 {@code SpinoffBearingBlockEntity.kt}（414 行，VS2 版）。
 *
 * <p><b>玩法（与旧版一致）</b>：两个对接口面对面放在两个结构上，它们自动对接，
 * 形成一个自由铰链（{@code driveFreeSpin}）：一边转，另一边跟着转，但铰链本身不产生力矩。
 * 红石信号会让它断开（断开后 10 刻内不再重新对接，避免红石抖动导致反复连断）。
 *
 * <p><b>与旧版的差异</b>
 * <ul>
 *   <li>伙伴检测：旧版用 {@code clipIncludeShips} 做"穿船射线"；Sable 直接改写了
 *       {@code BlockGetter#clip(ClipContext)} 让它考虑子空间，所以这里用原版
 *       {@code level.clip(...)} 就等价（射线打到的 {@code BlockPos} 就是对方方块在自己的
 *       plot 坐标系里的坐标，可以直接 {@code getBlockEntity}）。</li>
 *   <li>约束：旧版手工构造 {@code VSRevoluteJoint}（还带 jointId 找回、模糊匹配去重那一套）；
 *       这里用 Sable 的 {@link RotaryConstraintConfiguration}，两端锚点统一取"两个方块中心的中点"
 *       在各自刚体坐标系下的位置，保证几何上严格重合（旧版两端各取自己的面心，差半格会互相拉扯）。</li>
 *   <li>持久化：只存伙伴方块坐标 + 谁是主导端，约束对象本身不存（旧版存 jointId 再"找回来"，
 *       区块重载后经常需要容错）；重载后第一次 tick 重新对接。</li>
 *   <li>旧版的 {@code ICopyableBlock#onPaste}（蓝图粘贴时按船 id 迁移 partner 坐标）没有对应 API，
 *       改为"两端刚体变了就自动断开重连"。</li>
 * </ul>
 */
public class SpinoffBearingBlockEntity extends SmartBlockEntity {

    /** 向前探测伙伴的距离 [格]（旧版 {@code relative(facing, 2)}）。 */
    private static final double CONNECT_RANGE = 2.0;
    /** "面对面"判据：两边世界朝向点积必须小于该值（旧版 {@code dot > -0.75} 即拒绝）。 */
    private static final double OPPOSITE_FACING_DOT = -0.75;
    /** 断开后的重连冷却 [刻]（旧版 {@code reconnectDelay = 10}）。 */
    private static final int RECONNECT_DELAY = 10;

    /** 伙伴方块坐标（对方方块自己的坐标系）。 */
    @Nullable
    private BlockPos partnerPos;
    /** 只有主导端负责建约束，避免两端各建一个。 */
    private boolean leader;
    /** 建约束时两端所在的子空间 UUID（null = 世界）；用来发现"某一端被搬走了"。 */
    @Nullable
    private UUID selfBodyId;
    @Nullable
    private UUID partnerBodyId;
    @Nullable
    private PhysicsConstraintHandle handle;
    private int reconnectDelay;
    private boolean connected;

    public SpinoffBearingBlockEntity(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(final List<BlockEntityBehaviour> behaviours) {
        // 本方块没有滚轮/过滤器之类的行为
    }

    public Direction getFacing() {
        return this.getBlockState().getValue(SpinoffBearingBlock.FACING);
    }

    public boolean isConnected() {
        return this.connected;
    }

    // ==================== tick：对接 / 校验 / 红石断开 ====================

    @Override
    public void tick() {
        super.tick();
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        if (this.reconnectDelay > 0) {
            this.reconnectDelay--;
        }

        // 红石信号 = 脱开（旧版 hasNeighborSignal → disconnect）
        if (this.level.hasNeighborSignal(this.worldPosition)) {
            if (this.connected || this.partnerPos != null) {
                this.disconnect();
            }
            return;
        }

        if (this.partnerPos != null) {
            this.validateConnection();
            return;
        }
        if (this.reconnectDelay == 0) {
            this.tryConnect();
        }
    }

    /** 伙伴还在不在、两端刚体有没有变；不满足就断开并进入冷却。 */
    private void validateConnection() {
        final SpinoffBearingBlockEntity partner = this.getPartner();
        if (partner == null || !partner.isPartnerOf(this)) {
            this.disconnect();
            return;
        }
        if (this.leader && (this.handle == null || !this.handle.isValid()
                || !Objects.equals(this.selfBodyId, this.bodyId(this.worldPosition))
                || !Objects.equals(this.partnerBodyId, this.bodyId(partner.worldPosition)))) {
            this.createConstraint(partner);
        }
    }

    /** 向前打一条射线，命中另一个对接口就对接（旧版 tick 里的 partner 搜索）。 */
    private void tryConnect() {
        final Direction facing = this.getFacing();
        final Vec3 normal = Vec3.atLowerCornerOf(facing.getNormal());
        // 起点从"自己的面外侧 0.01 格"开始：旧版从方块中心打，会先命中自己的碰撞箱（半砖）而找不到对方
        final Vec3 from = Vec3.atCenterOf(this.worldPosition).add(normal.scale(0.51));
        final Vec3 to = from.add(normal.scale(CONNECT_RANGE));
        final BlockHitResult hit = this.level.clip(
                new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, (Entity) null));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        if (!(this.level.getBlockEntity(hit.getBlockPos()) instanceof final SpinoffBearingBlockEntity partner)
                || partner == this || partner.partnerPos != null) {
            return;
        }

        final ServerSubLevel selfBody = this.body();
        final ServerSubLevel partnerBody = partner.body();
        if (selfBody == partnerBody) {
            return;   // 同一刚体（含"都在世界里"）之间没有可连的铰链
        }

        // 两端必须大致对着（世界坐标系里比较，旧版 toWorldFacing + dot）
        final Vec3 selfWorld = worldNormal(selfBody, facing);
        final Vec3 partnerWorld = worldNormal(partnerBody, partner.getFacing());
        if (selfWorld.dot(partnerWorld) > OPPOSITE_FACING_DOT) {
            return;
        }

        this.partnerPos = partner.worldPosition;
        this.partnerBodyId = partner.bodyId(partner.worldPosition);
        this.leader = true;
        partner.partnerPos = this.worldPosition;
        partner.partnerBodyId = this.bodyId(this.worldPosition);
        partner.leader = false;
        this.createConstraint(partner);
    }

    // ==================== 约束 ====================

    private void createConstraint(final SpinoffBearingBlockEntity partner) {
        this.removeHandle();
        final PhysicsPipeline pipeline = CWSubLevels.pipeline(this.level);
        if (pipeline == null) {
            return;
        }
        final ServerSubLevel selfBody = this.body();
        final ServerSubLevel partnerBody = partner.body();
        if (selfBody == partnerBody) {
            return;
        }

        // 公共铰链点 = 两个方块中心的中点，分别换算到各自刚体的坐标系
        final Vec3 hinge = Vec3.atCenterOf(this.worldPosition).add(Vec3.atCenterOf(partner.worldPosition)).scale(0.5);
        final Vector3d selfAnchor = selfBody == null
                ? JOMLConversion.toJOML(hinge)
                : selfBody.logicalPose().transformPositionInverse(JOMLConversion.toJOML(hinge), new Vector3d());
        final Vector3d partnerAnchor = partnerBody == null
                ? JOMLConversion.toJOML(hinge)
                : partnerBody.logicalPose().transformPositionInverse(JOMLConversion.toJOML(hinge), new Vector3d());

        // 铰链轴：取本端朝向为世界轴，再换算到两端各自的坐标系（避免两端法向反号）
        final Vector3d axisWorld = JOMLConversion.toJOML(
                worldNormal(selfBody, this.getFacing()));
        final Vector3d selfAxis = selfBody == null
                ? new Vector3d(axisWorld)
                : selfBody.logicalPose().transformNormalInverse(axisWorld, new Vector3d());
        final Vector3d partnerAxis = partnerBody == null
                ? new Vector3d(axisWorld)
                : partnerBody.logicalPose().transformNormalInverse(axisWorld, new Vector3d());

        this.handle = pipeline.addConstraint(selfBody, partnerBody,
                new RotaryConstraintConfiguration(selfAnchor, partnerAnchor, selfAxis, partnerAxis));
        if (this.handle != null) {
            // 惰性：不打刚度/目标角，只留一点粘性阻尼（旧版 driveFreeSpin 的自由铰链手感）
            this.handle.setContactsEnabled(false);
            this.handle.setMotor(RotaryConstraintHandle.DEFAULT_AXIS, 0.0, 0.0,
                    ClockworkConfig.server().spinoffBearingFriction.get(), false, 0.0);
        }
        this.selfBodyId = this.bodyId(this.worldPosition);
        this.connected = this.handle != null;
        CWSubLevels.wakeUp(this.level, selfBody);
        CWSubLevels.wakeUp(this.level, partnerBody);
    }

    /** 断开连接（红石 / 拆除 / 伙伴丢失时调用；两端都会清空状态）。 */
    public void disconnect() {
        final SpinoffBearingBlockEntity partner = this.getPartner();
        this.removeHandle();
        this.partnerPos = null;
        this.selfBodyId = null;
        this.partnerBodyId = null;
        this.leader = false;
        this.reconnectDelay = RECONNECT_DELAY;
        if (partner != null && partner != this) {
            partner.partnerPos = null;
            partner.selfBodyId = null;
            partner.partnerBodyId = null;
            partner.leader = false;
            partner.reconnectDelay = RECONNECT_DELAY;
            partner.removeHandle();
        }
    }

    private void removeHandle() {
        if (this.handle != null) {
            this.handle.remove();
            this.handle = null;
        }
        this.connected = false;
    }

    // ==================== 辅助 ====================

    private @Nullable SpinoffBearingBlockEntity getPartner() {
        if (this.level == null || this.partnerPos == null) {
            return null;
        }
        return this.level.getBlockEntity(this.partnerPos) instanceof final SpinoffBearingBlockEntity partner
                ? partner
                : null;
    }

    private boolean isPartnerOf(final SpinoffBearingBlockEntity other) {
        return this.worldPosition.equals(other.partnerPos);
    }

    /** 本方块所在刚体（子空间），不在任何子空间里则返回 null（世界）。 */
    private @Nullable ServerSubLevel body() {
        return this.level == null ? null : CWSubLevels.containingServer(this.level, this.worldPosition);
    }

    private @Nullable UUID bodyId(final BlockPos pos) {
        if (this.level == null) {
            return null;
        }
        final ServerSubLevel body = CWSubLevels.containingServer(this.level, pos);
        return body == null ? null : body.getUniqueId();
    }

    /** 把本地朝向换算到世界坐标；不在子空间里时就是本地朝向。 */
    private static Vec3 worldNormal(final @Nullable ServerSubLevel body, final Direction facing) {
        final Vec3 local = Vec3.atLowerCornerOf(facing.getNormal());
        return body == null ? local : body.logicalPose().transformNormal(local);
    }

    // ==================== 生命周期 / 持久化 ====================

    @Override
    public void invalidate() {
        super.invalidate();
        this.removeHandle();
    }

    @Override
    public void remove() {
        this.removeHandle();
        super.remove();
    }

    @Override
    protected void write(final CompoundTag tag, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (this.partnerPos != null) {
            tag.put("Partner", NbtUtils.writeBlockPos(this.partnerPos));
        }
        tag.putBoolean("Leader", this.leader);
    }

    @Override
    protected void read(final CompoundTag tag, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.partnerPos = tag.contains("Partner")
                ? NbtUtils.readBlockPos(tag, "Partner").orElse(null)
                : null;
        this.leader = tag.getBoolean("Leader");
        // 约束句柄不持久化：重载后由 tick 重新对接（旧版也是这样"找回" joint 的）
        this.connected = false;
        this.handle = null;
    }
}
