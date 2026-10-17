package org.valkyrienskies.clockwork.content.curiosities.solver;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;

/**
 * 解算器方块实体。
 *
 * <p>对应旧版 {@code curiosities/solver/SolverBlockEntity.kt}：每 tick 沿朝向做一次射线检测，
 * 按命中方块的爆炸抗性累积"烧穿进度"，显示原版的破坏裂纹（{@code destroyBlockProgress}）并在进度满时破坏方块。
 *
 * <p>移植差异（VS2 → Sable 与范围裁剪）：
 * <ul>
 *   <li>旧版的世界坐标/朝向从 {@code getLoadedShipManagingPos(...).transform} 取；
 *       这里用 {@link SubLevel#logicalPose()}（等价刚体变换）；不在子空间上时直接使用方块本地坐标。</li>
 *   <li>旧版还会用 {@code sLevel.clip(...)} 去判定命中实体并造成伤害 —— 但 {@code Level#clip} 永远不会返回
 *       ENTITY 类型的结果（旧版逻辑失效），因此这里不做实体伤害；能量束目前只破坏方块。</li>
 *   <li>旧版的闪电束渲染（{@code LightningManager / LightningBolt}，客户端）与 Kelvin 网络节点
 *       （{@code KNodeBlockEntity / IArcConnector}）属于其它切片，本切片不引入；
 *       方块本身仍会正常发光破坏方块。</li>
 * </ul>
 *
 * <p><b>客户端光束所需的状态</b>：旧版 {@code SolverRenderer} 每帧在客户端自己重新做一次
 * {@code level.clip}（{@code SolverRenderer.kt:50}），再把世界坐标换算回方块本地坐标。
 * 本移植改为<b>服务端每 tick 算出一次光束长度</b>（{@link #beamDistance}）随
 * {@code sendData()} 同步，渲染器只读这个标量。理由：
 * <ul>
 *   <li>子空间被 Sable 以"整体刚体变换"渲染，方块本地方向就是光束方向，长度是刚体变换不变量，
 *       不需要在渲染线程重复做 64 格射线检测（每帧一次 {@code clip} 对多个解算器是可观开销）。</li>
 *   <li>命中点是服务端权威：方块被打穿/被放置时，下一 tick 客户端就能看到光束缩短或恢复，
 *       不会出现"客户端射线看到旧方块"的错位。</li>
 * </ul>
 */
public class SolverBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    /** 旧版常数：射程 64 格、光束起点在方块中心前 1.5 格。渲染器复用同一组常量，避免两边漂移。 */
    public static final double BEAM_LENGTH = 64.0;
    public static final double BEAM_ORIGIN_OFFSET = 1.5;

    private float destroyProgress;
    private boolean hasTarget;
    /** 从光束起点到命中方块的距离 [格]；没有命中时等于 {@link #BEAM_LENGTH}。 */
    private double beamDistance = BEAM_LENGTH;

    public SolverBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // 旧版同样为空实现。
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        final SubLevel subLevel = Sable.HELPER.getContaining(level, worldPosition);
        final Vec3 facing = worldFacing(subLevel);
        final Vec3 origin = toWorld(subLevel, Vec3.atCenterOf(worldPosition)).add(facing.scale(BEAM_ORIGIN_OFFSET));
        final Vec3 end = origin.add(facing.scale(BEAM_LENGTH));

        final BlockHitResult hit = serverLevel.clip(new ClipContext(origin, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, (Entity) null));
        final BlockPos target = hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
        final BlockState targetState = target == null ? null : serverLevel.getBlockState(target);

        if (targetState == null || targetState.isAir()) {
            // 光路空了：立刻把客户端光束恢复成整段 64 格，否则它会停在上一帧的命中点。
            final boolean hadBeamState = hasTarget || beamDistance != BEAM_LENGTH;
            resetBeam();
            hasTarget = false;
            if (hadBeamState) {
                sendData();
            }
            return;
        }

        hasTarget = true;
        beamDistance = Math.min(BEAM_LENGTH, origin.distanceTo(hit.getLocation()));
        // 旧版公式：抗性越高越难烧穿；每 tick 的增长被夹在 0~2，总进度夹在 0~1。
        // Block#getExplosionResistance() 在 1.21.1 被标记过时，但 BlockState 上的同名方法只有
        // (BlockGetter, BlockPos, Explosion) 三参数版本（javap 核实），这里语义就是"方块抗性"，
        // 与旧版 SolverBlockEntity.kt:124 的写法一致，故保留并局部压制过时告警。
        @SuppressWarnings("deprecation")
        final float resistance = Math.max(targetState.getBlock().getExplosionResistance(), 0.01f);
        destroyProgress += Mth.clamp(0.5f * Math.abs(4.0f * Mth.clamp(1.0f / resistance, 0.1f, 1.0f)), 0.0f, 2.0f);
        destroyProgress = Mth.clamp(destroyProgress, 0.0f, 1.0f);

        serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH,
                target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, 10,
                serverLevel.random.nextDouble() * 0.5, serverLevel.random.nextDouble() * 0.5,
                serverLevel.random.nextDouble() * 0.5, 0.02);
        serverLevel.destroyBlockProgress(0, target, (int) (destroyProgress * 10.0f));

        if (destroyProgress >= 1.0f) {
            serverLevel.destroyBlock(target, true, null, 512);
            destroyProgress = 0.0f;
        }
        // 进度每 tick 都在变，命中期间照旧每 tick 同步；没有目标时只在状态真的变了才发（见上）。
        sendData();
    }

    private void resetBeam() {
        destroyProgress = 0.0f;
        beamDistance = BEAM_LENGTH;
    }

    private Vec3 worldFacing(@Nullable SubLevel subLevel) {
        Vec3 normal = Vec3.atLowerCornerOf(getBlockState().getValue(SolverBlock.FACING).getNormal());
        if (subLevel == null) {
            return normal;
        }
        Vector3d world = subLevel.logicalPose()
                .transformNormal(new Vector3d(normal.x, normal.y, normal.z), new Vector3d());
        return new Vec3(world.x, world.y, world.z);
    }

    private Vec3 toWorld(@Nullable SubLevel subLevel, Vec3 local) {
        if (subLevel == null) {
            return local;
        }
        Vector3d world = subLevel.logicalPose()
                .transformPosition(new Vector3d(local.x, local.y, local.z), new Vector3d());
        return new Vec3(world.x, world.y, world.z);
    }

    /** 光束长度 [格]（服务端每 tick 更新，随方块实体数据同步给渲染器）。 */
    public double getBeamDistance() {
        return beamDistance;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("DestroyProgress", destroyProgress);
        tag.putBoolean("HasTarget", hasTarget);
        tag.putDouble("BeamDistance", beamDistance);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        destroyProgress = tag.getFloat("DestroyProgress");
        hasTarget = tag.getBoolean("HasTarget");
        // 旧存档没有这个键：默认整段 64 格，下一 tick 服务端会补上真实值。
        beamDistance = tag.contains("BeamDistance") ? tag.getDouble("BeamDistance") : BEAM_LENGTH;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("Solver"));
        tooltip.add(Component.literal("  Beam length: " + (int) BEAM_LENGTH));
        tooltip.add(Component.literal(hasTarget
                ? "  Burn-through: " + Math.round(destroyProgress * 100.0f) + "%"
                : "  No target"));
        return true;
    }
}
