package org.valkyrienskies.clockwork.content.physicalities.slicker;

import com.simibubi.create.content.contraptions.glue.SuperGlueItem;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.List;

import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.valkyrienskies.clockwork.content.kinetics.CWKineticsSounds;

/**
 * 减摩方块的方块实体：伸出时把正前方结构"吸"住，收回时放开。
 *
 * <p>对应旧版 {@code content.contraptions.phys.slicker.SlickerBlockEntity}。
 *
 * <p><b>与旧版最大的差异</b>：旧版手搓 VS2 的 {@code VSFixedJoint}（连同关节 id 校验 / 恢复，
 * 单文件近 300 行）；1.21.1 的结构是 Sable 子空间，这里改用 Sable 的固定约束
 * （{@link FixedConstraintConfiguration} + {@code PhysicsPipeline#addConstraint}），
 * 判定与姿态对齐算法照抄 Sable 给 Create 粘性方块写的 mixin，因此约束的语义是一致的。
 *
 * <p>其它差异：
 * <ul>
 *   <li>约束是运行时对象，<b>不写 NBT</b>：区块重载后 tick 会按方块状态自动重建，
 *       比旧版"存关节 id 再逐条校验恢复"简单可靠。</li>
 *   <li>旧版用"关节距离"表达"贴着但能滑"，Sable 的接触摩擦由物理管线决定，本切片不介入，
 *       所以这里只有"吸住 / 放开"两种状态。</li>
 *   <li>旧版的 {@code SlickerMovementBehaviour}（装在 Create  contraption 上的版本）没有移植，
 *       它的作用同样是建立 VS2 关节，属于物理切片的地盘。</li>
 * </ul>
 */
public class SlickerBlockEntity extends SmartBlockEntity {

    /** 吸附射线长度 [格]。 */
    private static final double RAY_LENGTH = 0.75;
    /** 允许的最大夹角 [度]：超过就不吸，避免斜着贴上去把结构扭起来。 */
    private static final double ANGLE_TOLERANCE = 20.0;

    /** 客户端伸出动画：0 = 收回，1 = 伸出。 */
    private final LerpedFloat piston = LerpedFloat.linear();
    private boolean wasExtended;

    /** 已吸附的目标方块；服务端专用。 */
    @Nullable
    private BlockPos attachedPos;
    @Nullable
    private FixedConstraintHandle constraint;

    public SlickerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void initialize() {
        super.initialize();
        wasExtended = isExtended();
        if (level != null && level.isClientSide) {
            piston.startWithValue(wasExtended ? 1 : 0);
        }
    }

    /** 渲染用：伸出进度 0..1（旧版渲染器同样读这个值）。 */
    public float getPistonOffset(float partialTicks) {
        return piston.getValue(partialTicks);
    }

    public boolean isExtended() {
        return getBlockState().getValue(SlickerBlock.EXTENDED);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) {
            return;
        }
        if (level.isClientSide) {
            tickClient();
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        Direction facing = getBlockState().getValue(SlickerBlock.FACING);
        if (!isExtended()) {
            release();
            return;
        }

        // 吸附的方块被拆掉 / 那一面不再是实心面，就断开重新找
        if (attachedPos != null && !isValidTarget(serverLevel, attachedPos, facing.getOpposite())) {
            release();
        }
        if (constraint != null && !constraint.isValid()) {
            constraint = null;
            attachedPos = null;
        }
        if (constraint == null) {
            tryAttach(serverLevel, facing);
        }
    }

    private void tickClient() {
        piston.tickChaser();
        boolean extended = isExtended();
        if (extended == wasExtended) {
            return;
        }
        wasExtended = extended;
        piston.chase(extended ? 1 : 0, 0.4, LerpedFloat.Chaser.LINEAR);
        SuperGlueItem.spawnParticles(level, worldPosition, getBlockState().getValue(SlickerBlock.FACING), extended);
        level.playSound(null, worldPosition, extended ? CWKineticsSounds.DOINK : CWKineticsSounds.BOING,
                SoundSource.BLOCKS, 0.35f, extended ? 0.75f : 0.2f);
    }

    // ------------------------------------------------------------------ 约束

    private void tryAttach(ServerLevel level, Direction facing) {
        Vec3 from = new Vec3(
                worldPosition.getX() + 0.5 + facing.getStepX() * 0.5,
                worldPosition.getY() + 0.5 + facing.getStepY() * 0.5,
                worldPosition.getZ() + 0.5 + facing.getStepZ() * 0.5);
        Vec3 to = from.add(facing.getStepX() * RAY_LENGTH, facing.getStepY() * RAY_LENGTH,
                facing.getStepZ() * RAY_LENGTH);
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (hit.getType() == HitResult.Type.MISS) {
            return;
        }
        BlockPos otherPos = hit.getBlockPos();
        if (!isValidTarget(level, otherPos, hit.getDirection())) {
            return;
        }

        ServerSubLevel thisSubLevel = containing(level, worldPosition);
        ServerSubLevel otherSubLevel = containing(level, otherPos);
        // 两端在同一个结构里（或都在世界里）就没有物理约束可加
        if (thisSubLevel == otherSubLevel) {
            return;
        }

        // 姿态对齐：把两个结构的接触法线在世界坐标里对齐
        Quaterniondc first = thisSubLevel != null ? thisSubLevel.logicalPose().orientation() : new Quaterniond();
        Quaterniondc second = otherSubLevel != null ? otherSubLevel.logicalPose().orientation() : new Quaterniond();
        Vector3d globalA = first.transform(new Vector3d(facing.getStepX(), facing.getStepY(), facing.getStepZ()));
        Vector3d globalB = second.transform(new Vector3d(hit.getDirection().getStepX(),
                hit.getDirection().getStepY(), hit.getDirection().getStepZ()));
        double dot = globalA.dot(globalB);
        Vector3d axis = new Vector3d(globalA).cross(globalB);
        if (dot > 1.0 - 1e-6 || dot < 1e-6 || axis.lengthSquared() < 1e-12) {
            return;
        }
        axis.normalize();
        double angle = Math.acos(Math.min(1.0, dot));
        if (angle > Math.toRadians(ANGLE_TOLERANCE)) {
            return;
        }
        Quaterniond orientation = new Quaterniond().rotateAxis(-angle, axis)
                .mul(second)
                .premul(first.conjugate(new Quaterniond()));

        FixedConstraintConfiguration config = new FixedConstraintConfiguration(
                new Vector3d(from.x, from.y, from.z),
                new Vector3d(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z),
                orientation);
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        constraint = container.physicsSystem().getPipeline()
                .addConstraint(thisSubLevel, otherSubLevel, config);
        attachedPos = constraint != null ? otherPos : null;
    }

    private void release() {
        if (constraint != null) {
            constraint.remove();
            constraint = null;
        }
        attachedPos = null;
    }

    /** 目标方块还在，且朝向我们的那一面仍是实心面（对应旧版"能不能贴"的判定）。 */
    private static boolean isValidTarget(ServerLevel level, BlockPos pos, Direction faceTowardsUs) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && state.isFaceSturdy(level, pos, faceTowardsUs, SupportType.RIGID);
    }

    @Nullable
    private static ServerSubLevel containing(ServerLevel level, BlockPos pos) {
        SubLevel subLevel = Sable.HELPER.getContaining(level, pos);
        return subLevel instanceof ServerSubLevel server ? server : null;
    }

    @Override
    public void destroy() {
        release();
        super.destroy();
    }

    @Override
    public void remove() {
        release();
        super.remove();
    }
}
