package org.valkyrienskies.clockwork.content.contraptions.phys.gimbal.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4d;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.valkyrienskies.clockwork.content.contraptions.client.ContraptionPartials;
import org.valkyrienskies.clockwork.content.contraptions.phys.gimbal.GimbalBearingBlock;
import org.valkyrienskies.clockwork.content.contraptions.phys.gimbal.GimbalBearingBlockEntity;
import org.valkyrienskies.clockwork.platform.CWSubLevels;

/**
 * 万向轴承（{@code gimbal_bearing}）的渲染器：托板跟着被举起的子空间走，四条连杆把
 * 轴承底座的四个角与托板四角连起来。
 *
 * <p>对应旧版 {@code content/contraptions/phys/gimbal/GimbalBearingRenderer.kt}（295 行）。
 * 旧版依赖 VS2 的 {@code ClientShip.renderTransform.shipToWorld/worldToShip} 做
 * 世界 ↔ 渲染本地换算；Sable 下没有 {@code ClientShip}，本移植改用
 * {@code SubLevel} 的位姿（{@link Pose3dc}）完成同样的换算：
 * <ul>
 *   <li>被举起的结构 = 轴承装配出来的子空间（{@link GimbalBearingBlockEntity#getSubLevelID()}，
 *       装配信息随方块实体数据同步，客户端拿得到）；
 *       它的位姿用 {@code ClientSubLevel#renderPose(partialTicks)}（插值后的渲染位姿，
 *       旧版的 {@code renderTransform} 同样是插值位姿），非客户端子空间退回
 *       {@code SubLevel#logicalPose()}。</li>
 *   <li>轴承自身可能也在某个子空间里（"船上装云台"），此时它的渲染本地坐标系 =
 *       宿主子空间的 plot 坐标系，换算用宿主位姿的逆变换（对应旧版
 *       {@code worldPointToRenderLocal(be, hostShip, world)}）。</li>
 * </ul>
 *
 * <p><b>与旧版的差异</b>
 * <ul>
 *   <li>子空间本地的轴承轴向（旧版 {@code bearingAxisLocal}，是可存取的"装配瞬间姿态"）
 *       本移植没有保存：装配时子空间与宿主同向、plot 坐标只做平移，因此直接把轴承的
 *       {@code facing} 法向当作子空间本地的轴向（旧版在保存值缺失时也是这个回退）。</li>
 *   <li>托板原点取"锚点方块中心"，与 {@code AbstractBearingBlockEntity} 建立约束时的
 *       {@code attachPos} 完全一致（旧版取 {@code bearingPosInSub} 再沿轴偏
 *       {@code FACE_ATTACHMENT_OFFSET_BLOCKS}）；两者只差半格，看起来就是托板嵌在
 *       子结构第一个方块的中央。</li>
 *   <li>连杆的滑动求解（旧版 {@code slidingTarget}）简化为"沿 base→托板角连线取不超过
 *       臂长的最近点"：连杆是定长模型，子空间被晃到远处时旧解会让连杆沿托板对角线滑动，
 *       本简化让它直达角点，视觉差异只在极限摆幅下出现（代码量少一半，便于维护）。</li>
 *   <li>旧版还有一个"限位锥"（摆角上限）与力控制器；本移植的约束是自由关节（见
 *       {@code GimbalBearingBlockEntity} 类注释），所以托板能摆到任意角度，渲染照实画即可。</li>
 * </ul>
 */
public class GimbalBearingRenderer extends SmartBlockEntityRenderer<GimbalBearingBlockEntity> {

    /** 托板模型的上表面中心（旧版 {@code PLATE_FACE_CENTER_MODEL}）：托板模型的枢轴点。 */
    private static final Vector3d PLATE_FACE_CENTER = new Vector3d(0.5, 1.0, 0.5);
    /** 连杆模型的旋转中心（旧版 {@code ARM_ROTATION_CENTER}）。 */
    private static final Vector3d ARM_ROTATION_CENTER = new Vector3d(0.5, 0.0, 0.5);
    /** 连杆底端铰点在模型里的位置 = (3.5/16, 8/16, 3.5/16)（旧版 {@code ARM_BOTTOM_MODEL}）。 */
    private static final Vector3d ARM_BOTTOM_MODEL = new Vector3d(3.5 / 16.0, 8.0 / 16.0, 3.5 / 16.0);
    /** 连杆在模型里从 y=8/16 伸到 y=14/16，故臂长 6/16（旧版 {@code ARM_LENGTH}）。 */
    private static final double ARM_LENGTH = 6.0 / 16.0;
    /** 四角铰点相对方块边的内缩量（旧版 {@code ARM_INSET} = 5/16）。 */
    private static final double ARM_INSET = 5.0 / 16.0;
    /** 铰点相对方块上表面的抬高量（旧版 {@code ARM_VERTICAL_OFFSET} = 3/16）。 */
    private static final double ARM_VERTICAL_OFFSET = 3.0 / 16.0;
    /** 连杆底端铰点到底面的高度：7/16（轴承上表面）+ 3/16（旧版 {@code ARM_BOTTOM_Y}）。 */
    private static final double ARM_BOTTOM_Y = 7.0 / 16.0 + ARM_VERTICAL_OFFSET;
    /** 连杆顶端铰点到底面的高度：14/16（托板下表面）+ 3/16（旧版 {@code ARM_TOP_Y}）。 */
    private static final double ARM_TOP_Y = 14.0 / 16.0 + ARM_VERTICAL_OFFSET;

    /** 模型默认朝向（+Y，与局部模型"以 facing=UP 导出"一致）。 */
    private static final Vector3d DEFAULT_NORMAL = new Vector3d(0.0, 1.0, 0.0);
    /** 模型默认的 v 轴（+Z），用于确定托板绕法向的滚转角。 */
    private static final Vector3d DEFAULT_V = new Vector3d(0.0, 0.0, 1.0);
    /** 连杆模型在 NW 角时的"径向外侧"方向（旧版 {@code DEFAULT_CORNER_RADIAL}）。 */
    private static final Vector3d DEFAULT_CORNER_RADIAL = new Vector3d(-1.0, 0.0, -1.0).normalize();

    /** 四个角的符号与"从 NW 转到该角"的偏航角（旧版 {@code CORNERS}）。 */
    private static final Corner[] CORNERS = {
            new Corner(-1, -1, 0.0),
            new Corner(-1, 1, Math.PI / 2.0),
            new Corner(1, 1, Math.PI),
            new Corner(1, -1, -Math.PI / 2.0),
    };

    public GimbalBearingRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    /** 托板可能被举到离轴承很远的地方，必须关掉视锥剔除（旧版同样重写这两个方法）。 */
    @Override
    public boolean shouldRenderOffScreen(final GimbalBearingBlockEntity be) {
        return true;
    }

    @Override
    public boolean shouldRender(final GimbalBearingBlockEntity be, final Vec3 cameraPos) {
        return true;
    }

    @Override
    protected void renderSafe(final GimbalBearingBlockEntity be, final float partialTicks, final PoseStack ms,
                              final MultiBufferSource buffer, final int light, final int overlay) {
        final BlockState state = be.getBlockState();
        final Direction facing = state.getValue(GimbalBearingBlock.FACING);
        final VertexConsumer vb = buffer.getBuffer(RenderType.cutout());

        final Frame host = hostFrame(facing);
        // 没有装配 / 子空间还没同步到客户端时退回宿主姿态（旧版 platePose ?: hostPose）
        final Frame plate = plateFrame(be, facing, partialTicks);
        final Frame plateOrHost = plate == null ? host : plate;

        renderPlate(plateOrHost, state, ms, vb, light, overlay);
        renderArms(host, plateOrHost, state, ms, vb, light, overlay);
    }

    // ==================== 姿态 ====================

    /** 轴承自身的姿态：原点 = 朝向那一面的面心，法向 = facing，u/v 是面内两个正交轴。 */
    private static Frame hostFrame(final Direction facing) {
        final Basis basis = basisFor(facing);
        final Vector3d origin = new Vector3d(0.5, 0.5, 0.5).fma(0.5, basis.normal);
        return new Frame(origin, basis.u, basis.normal, basis.v);
    }

    /** 托板姿态：由被举起子空间的位姿算出，换算到"本方块为原点"的渲染本地坐标系。 */
    private static @Nullable Frame plateFrame(final GimbalBearingBlockEntity be, final Direction facing,
                                              final float partialTicks) {
        if (be.getLevel() == null || be.getSubLevelID() == null) {
            return null;
        }
        final BlockPos anchor = be.getAssembledAnchor();
        final SubLevel child = CWSubLevels.byId(be.getLevel(), be.getSubLevelID());
        if (anchor == null || child == null) {
            return null;
        }
        final SubLevel host = CWSubLevels.containing(be.getLevel(), be.getBlockPos());
        final Pose3dc childPose = poseOf(child, partialTicks);
        final Pose3dc hostPose = poseOf(host, partialTicks);
        if (childPose == null) {
            return null;
        }

        // 装配瞬间子空间与宿主同向，所以轴承轴向在子空间本地就是 facing 的法向
        final Basis localBasis = basisFor(facing);
        final Vector3d normalLocal = new Vector3d(facing.getStepX(), facing.getStepY(), facing.getStepZ());

        final Vector3d anchorWorld =
                childPose.transformPosition(new Vector3d(anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5),
                        new Vector3d());
        final Vector3d normalWorld = childPose.transformNormal(normalLocal, new Vector3d()).normalize();
        final Vector3d uWorld = childPose.transformNormal(localBasis.u, new Vector3d()).normalize();
        final Vector3d vWorld = childPose.transformNormal(localBasis.v, new Vector3d()).normalize();

        return new Frame(
                toRenderLocal(hostPose, anchorWorld, be.getBlockPos()),
                toRenderLocalDirection(hostPose, uWorld),
                toRenderLocalDirection(hostPose, normalWorld),
                toRenderLocalDirection(hostPose, vWorld)).orthonormalized();
    }

    /** 子空间的渲染位姿：客户端用插值位姿（旧版 {@code renderTransform} 同义），否则退回逻辑位姿。 */
    private static @Nullable Pose3dc poseOf(final @Nullable SubLevel subLevel, final float partialTicks) {
        if (subLevel instanceof final ClientSubLevel client) {
            // renderPose 会就地更新并复用同一个 Pose3d 实例，所以调用方必须立刻把数值取走
            return client.renderPose(partialTicks);
        }
        return subLevel == null ? null : subLevel.logicalPose();
    }

    /** 世界坐标 → 渲染本地（宿主位姿的逆变换，再减去本方块的原点）。 */
    private static Vector3d toRenderLocal(final @Nullable Pose3dc hostPose, final Vector3d world,
                                          final BlockPos blockPos) {
        final Vector3d local = hostPose == null
                ? new Vector3d(world)
                : hostPose.transformPositionInverse(world, new Vector3d());
        return local.sub(blockPos.getX(), blockPos.getY(), blockPos.getZ());
    }

    /** 世界方向 → 渲染本地方向（只转不平移，与旧版 {@code worldDirectionToRenderLocal} 一致）。 */
    private static Vector3d toRenderLocalDirection(final @Nullable Pose3dc hostPose, final Vector3d world) {
        final Vector3d local = hostPose == null
                ? new Vector3d(world)
                : hostPose.transformNormalInverse(world, new Vector3d());
        return local.normalize();
    }

    /** 面内正交基（旧版 {@code basisForDirection}）：竖直/南北朝向取 +X 为 u，东西朝向取 ∓Z。 */
    private static Basis basisFor(final Direction direction) {
        final Vector3d normal = new Vector3d(direction.getStepX(), direction.getStepY(), direction.getStepZ());
        final Vector3d u = switch (direction) {
            case UP, DOWN, NORTH, SOUTH -> new Vector3d(1.0, 0.0, 0.0);
            case EAST -> new Vector3d(0.0, 0.0, -1.0);
            case WEST -> new Vector3d(0.0, 0.0, 1.0);
        };
        return new Basis(u.normalize(), normal.normalize(), u.cross(normal, new Vector3d()).normalize());
    }

    // ==================== 绘制 ====================

    private static void renderPlate(final Frame plate, final BlockState state, final PoseStack ms,
                                    final VertexConsumer vb, final int light, final int overlay) {
        ms.pushPose();
        ms.translate(plate.origin.x, plate.origin.y, plate.origin.z);
        ms.mulPose(toFloat(quaternionFromModelAxes(plate.normal, plate.v)));
        ms.translate(-PLATE_FACE_CENTER.x, -PLATE_FACE_CENTER.y, -PLATE_FACE_CENTER.z);
        CachedBuffers.partial(ContraptionPartials.GIMBAL_PLATE, state)
                .light(light)
                .overlay(overlay)
                .renderInto(ms, vb);
        ms.popPose();
    }

    /** 四条连杆：各自从轴承上的底端铰点指向托板对应角，朝向差用四元数对齐（旧版 {@code renderArms}）。 */
    private static void renderArms(final Frame host, final Frame plate, final BlockState state, final PoseStack ms,
                                   final VertexConsumer vb, final int light, final int overlay) {
        final Vector3d plateCenter = modelPoint(plate, 0.5, ARM_TOP_Y, 0.5);
        final Vector3d hostCenter = modelPoint(host, 0.5, ARM_BOTTOM_Y, 0.5);

        for (final Corner corner : CORNERS) {
            final double x = corner.xSign < 0 ? ARM_INSET : 1.0 - ARM_INSET;
            final double z = corner.zSign < 0 ? ARM_INSET : 1.0 - ARM_INSET;
            final Vector3d base = modelPoint(host, x, ARM_BOTTOM_Y, z);
            final Vector3d plateCorner = modelPoint(plate, x, ARM_TOP_Y, z);
            final Vector3d target = slidingTarget(base, plateCorner);
            final Vector3d link = target.sub(base, new Vector3d());
            if (link.lengthSquared() < 1.0E-10) {
                continue;
            }

            final Vector3d linkDir = link.normalize(new Vector3d());
            final Vector3d desiredRadial = target.sub(plateCenter, new Vector3d());
            if (desiredRadial.lengthSquared() < 1.0E-10) {
                desiredRadial.set(base).sub(hostCenter);
            }

            final Quaterniond cornerRotation = new Quaterniond(new AxisAngle4d(corner.rotationRad, 0.0, 1.0, 0.0));
            final Vector3d bottomModel = rotateAboutCenter(ARM_BOTTOM_MODEL, cornerRotation);
            final Vector3d modelRadial = new Vector3d(DEFAULT_CORNER_RADIAL).rotate(cornerRotation).normalize();
            final Quaterniond armRotation = directionAndRoll(DEFAULT_NORMAL, linkDir, modelRadial, desiredRadial);

            ms.pushPose();
            ms.translate(base.x, base.y, base.z);
            ms.mulPose(toFloat(armRotation));
            ms.translate(ARM_ROTATION_CENTER.x - bottomModel.x,
                    ARM_ROTATION_CENTER.y - bottomModel.y,
                    ARM_ROTATION_CENTER.z - bottomModel.z);
            ms.mulPose(toFloat(cornerRotation));
            ms.translate(-ARM_ROTATION_CENTER.x, -ARM_ROTATION_CENTER.y, -ARM_ROTATION_CENTER.z);
            CachedBuffers.partial(ContraptionPartials.GIMBAL_ARM, state)
                    .light(light)
                    .overlay(overlay)
                    .renderInto(ms, vb);
            ms.popPose();
        }
    }

    /**
     * 连杆顶端的目标点：从底端铰点沿"到托板角"的连线取不超过臂长的最近点。
     *
     * <p>简化自旧版 {@code slidingTarget}（它解一个二次方程，让点落在托板角到托板中心的
     * 对角线上），见类注释的差异说明。
     */
    private static Vector3d slidingTarget(final Vector3d base, final Vector3d plateCorner) {
        final Vector3d link = plateCorner.sub(base, new Vector3d());
        final double length = link.length();
        if (length <= ARM_LENGTH || length < 1.0E-10) {
            return new Vector3d(plateCorner);
        }
        return new Vector3d(base).fma(ARM_LENGTH / length, link);
    }

    /** 姿态里的一个点：{@code origin + (x-0.5)·u + (y-1)·normal + (z-0.5)·v}（旧版 {@code modelPoint}）。 */
    private static Vector3d modelPoint(final Frame frame, final double x, final double y, final double z) {
        return new Vector3d(frame.origin)
                .fma(x - PLATE_FACE_CENTER.x, frame.u)
                .fma(y - PLATE_FACE_CENTER.y, frame.normal)
                .fma(z - PLATE_FACE_CENTER.z, frame.v);
    }

    /** 绕 {@link #ARM_ROTATION_CENTER} 旋转一个模型点（旧版 {@code rotateModelCorner}）。 */
    private static Vector3d rotateAboutCenter(final Vector3d point, final Quaterniond rotation) {
        return new Vector3d(point).sub(ARM_ROTATION_CENTER).rotate(rotation).add(ARM_ROTATION_CENTER);
    }

    // ==================== 四元数工具（旧版同名函数逐行照抄） ====================

    /** 把模型的默认法向转到 {@code normal}，再绕法向滚转使默认 v 轴对齐 {@code v}。 */
    private static Quaterniond quaternionFromModelAxes(final Vector3d normal, final Vector3d v) {
        final Vector3d normalUnit = new Vector3d(normal).normalize();
        final Vector3d vUnit = new Vector3d(v).normalize();
        final Quaterniond base = new Quaterniond().rotationTo(DEFAULT_NORMAL, normalUnit);
        final Vector3d currentV = base.transform(new Vector3d(DEFAULT_V)).normalize();
        final double roll = signedAngle(currentV, vUnit, normalUnit);
        return new Quaterniond(new AxisAngle4d(roll, normalUnit.x, normalUnit.y, normalUnit.z)).mul(base);
    }

    /** 把 {@code modelRadial} 方向转到 {@code desiredRadial}（都在垂直于 {@code targetDirection} 的面内）。 */
    private static Quaterniond directionAndRoll(final Vector3d defaultDirection, final Vector3d targetDirection,
                                                final Vector3d modelRadial, final Vector3d desiredRadial) {
        final Vector3d targetUnit = new Vector3d(targetDirection).normalize();
        final Quaterniond base = new Quaterniond().rotationTo(defaultDirection, targetUnit);
        final Vector3d currentRadial = perpendicular(base.transform(new Vector3d(modelRadial)), targetUnit);
        final Vector3d desired = perpendicular(new Vector3d(desiredRadial), targetUnit);
        if (currentRadial.lengthSquared() < 1.0E-10 || desired.lengthSquared() < 1.0E-10) {
            return base;
        }
        final double roll = signedAngle(currentRadial.normalize(), desired.normalize(), targetUnit);
        return new Quaterniond(new AxisAngle4d(roll, targetUnit.x, targetUnit.y, targetUnit.z)).mul(base);
    }

    /** {@code from} 绕 {@code axis} 转到 {@code to} 的有符号夹角。 */
    private static double signedAngle(final Vector3d from, final Vector3d to, final Vector3d axis) {
        final Vector3d cross = from.cross(to, new Vector3d());
        return Math.atan2(axis.dot(cross), from.dot(to));
    }

    private static Vector3d perpendicular(final Vector3d vector, final Vector3d normal) {
        return vector.fma(-vector.dot(normal), normal);
    }

    private static Quaternionf toFloat(final Quaterniond quaternion) {
        return new Quaternionf((float) quaternion.x, (float) quaternion.y, (float) quaternion.z, (float) quaternion.w);
    }

    // ==================== 小数据结构 ====================

    /** 面内正交基。 */
    private record Basis(Vector3d u, Vector3d normal, Vector3d v) {
    }

    /** 一个姿态：原点 + 三个正交轴。 */
    private record Frame(Vector3d origin, Vector3d u, Vector3d normal, Vector3d v) {

        /** 物理插值后的三个轴未必严格正交，画之前先正交化（旧版 {@code RenderPose.orthonormalized}）。 */
        Frame orthonormalized() {
            final Vector3d normalUnit = new Vector3d(this.normal).normalize();
            final Vector3d uUnit = new Vector3d(this.u).fma(-this.u.dot(normalUnit), normalUnit);
            if (uUnit.lengthSquared() < 1.0E-10) {
                uUnit.set(1.0, 0.0, 0.0);
            }
            uUnit.normalize();
            return new Frame(this.origin, uUnit, normalUnit,
                    uUnit.cross(normalUnit, new Vector3d()).normalize());
        }
    }

    /** 托板四角之一：x/z 的符号与从默认角转过来的偏航角。 */
    private record Corner(int xSign, int zSign, double rotationRad) {
    }
}
