package org.valkyrienskies.clockwork.content.contraptions.phys.bearing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.clockwork.content.contraptions.client.ContraptionPartials;
import org.valkyrienskies.clockwork.content.contraptions.phys.bearing.PhysBearingBlockEntity;

/**
 * 物理轴承（{@code phys_bearing}）的方块实体渲染器。
 *
 * <p>对应旧版 {@code content/contraptions/phys/bearing/PhysBearingRenderer.kt}（206 行）：
 * 四片可张开翼面 {@code PHYS_*_WING}、输入轴 {@code PHYS_SHAFT}、抓取爪 {@code PHYS_ATTACHER}，
 * 以及围绕轴心旋转的核心（旧版 {@code renderCore}，见下方"未移植"说明）。
 *
 * <p><b>与旧版的关键差异：不再自己算 {@code facing} 的姿态</b>
 * <p>旧版 :43-62 手动把 {@code PoseStack} 摆到 "facing 方向"（先绕 Y 转 180°，再按 facing
 * 叠一个四元数），因为它画的是"以 +Z 为基准姿态"导出的局部模型。
 * 本移植改用 Catnip 的 {@link CachedBuffers#partialFacing} —— 它应用的是
 * <b>与 blockstate JSON 完全相同的 {@code x/y} 旋转</b>，因此局部模型会落在和静态方块模型
 * 一致的位置上，翼面/轴/爪的局部坐标也就与 {@code phys_bearing/shaft.json} 等模型文件直读一致。
 * 旧版那段姿态代码是"另一条等价路径"，照抄只会引入两套朝向约定，故不保留（差异已实机可核对：
 * 六个 facing 下翼面都应贴着方块本体、不歪）。
 *
 * <p><b>逐项照抄的部分</b>
 * <ul>
 *   <li>翼面旋转支点与轴向：{@code rotateAndRiseNorth/South}（:157-165）用
 *       {@code (0,-7/16,∓5.5/16)} 绕 WEST/EAST，{@code rotateWest/EastWing}（:177-185）用
 *       {@code (∓7/16,1/16,0)} 绕 SOUTH/NORTH。</li>
 *   <li>{@code coolKineticRotationTransform}（:114-155）：按 facing 先转再平移，
 *       然后按 {@code AxisDirection} 取旋转轴向 —— 原样照抄。</li>
 *   <li>轴与爪的模型选择：{@code partialFacing(PHYS_SHAFT/PHYS_ATTACHER, state, facing)}，同旧版 :195-205。</li>
 * </ul>
 *
 * <p><b>未移植的部件</b>：旧版 {@code renderCore}（:64-80）画三层水晶核心
 * （{@code RenderUtil.renderCubeMatrix}，依赖未移植的自研 render type / shader，详见
 * {@code GyroRenderer} 的同类说明）。缺的只是轴心的发光方块。
 *
 * <p><b>为什么不调 {@code super.renderSafe}</b>：Create 6 的
 * {@code KineticBlockEntityRenderer#renderSafe} 第一句是
 * {@code if (VisualizationManager.supportsVisualization(level)) return;}（javap -c 核实），
 * 本工程不注册 Flywheel visual，靠父类只会把"整个方块模型"再画一遍（默认
 * {@code getRenderedBlockState} 就是方块状态本身），Flywheel 开启时则什么都不画。
 * 本渲染器自己已经画全了四片翼面 + 轴 + 爪，因此不调父类。
 */
public class PhysBearingRenderer extends KineticBlockEntityRenderer<PhysBearingBlockEntity> {

    /** 四片翼面的旋转枢轴（旧版 {@code rotateAndRise*}/{@code rotateWest/EastWing} 里的常量，单位：格）。 */
    private static final Vec3 NORTH_PIVOT = new Vec3(0.0, -7.0 / 16, -5.5 / 16);
    private static final Vec3 SOUTH_PIVOT = new Vec3(0.0, -7.0 / 16, 5.5 / 16);
    private static final Vec3 WEST_PIVOT = new Vec3(-7.0 / 16, 1.0 / 16, 0.0);
    private static final Vec3 EAST_PIVOT = new Vec3(7.0 / 16, 1.0 / 16, 0.0);

    public PhysBearingRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(final PhysBearingBlockEntity be, final float partialTicks, final PoseStack ms,
                              final MultiBufferSource buffer, final int light, final int overlay) {
        final BlockState state = be.getBlockState();
        final Direction facing = state.getValue(BlockStateProperties.FACING);
        final VertexConsumer vb = buffer.getBuffer(RenderType.translucent());

        renderWings(be, state, ms, vb, light, partialTicks);

        // 轴与爪：与旧版一样在"facing 后的局部空间"里旋转（旧版 :100-111）
        final Direction.Axis axis = ((IRotate) state.getBlock()).getRotationAxis(state);
        final float angle = getAngleForBe(be, be.getBlockPos(), axis);
        coolKineticRotationTransform(CachedBuffers.partialFacing(ContraptionPartials.PHYS_ATTACHER, state, facing)
                .reset(), facing, angle, light).renderInto(ms, vb);
        coolKineticRotationTransform(CachedBuffers.partialFacing(ContraptionPartials.PHYS_SHAFT, state, facing)
                .reset(), facing, angle, light).renderInto(ms, vb);
    }

    /** 四片翼面按 {@link PhysBearingBlockEntity#getWingAngleDegrees(float)} 绕各自枢轴张开。 */
    private static void renderWings(final PhysBearingBlockEntity be, final BlockState state, final PoseStack ms,
                                    final VertexConsumer vb, final int light, final float partialTicks) {
        final float degrees = be.getWingAngleDegrees(partialTicks);
        wing(ContraptionPartials.PHYS_NORTH_WING, state, NORTH_PIVOT, Direction.WEST, degrees).light(light).renderInto(ms, vb);
        wing(ContraptionPartials.PHYS_SOUTH_WING, state, SOUTH_PIVOT, Direction.EAST, degrees).light(light).renderInto(ms, vb);
        wing(ContraptionPartials.PHYS_EAST_WING, state, EAST_PIVOT, Direction.NORTH, degrees).light(light).renderInto(ms, vb);
        wing(ContraptionPartials.PHYS_WEST_WING, state, WEST_PIVOT, Direction.SOUTH, degrees).light(light).renderInto(ms, vb);
    }

    /**
     * 绕枢轴转一片翼面。
     *
     * <p>{@code translate(pivot) → rotateCentered(a) → translateBack(pivot)} ≡ 绕 {@code pivot} 旋转
     * （{@code translateBack} 就是取负的 {@code translate}，已 javap 核实 Flywheel 的
     * {@code Translate#translateBack} 字节码），与旧版 {@code rotateAndRise}/{@code rotateWing} 等价。
     */
    private static SuperByteBuffer wing(final PartialModel model, final BlockState state, final Vec3 pivot,
                                        final Direction axis, final float degrees) {
        final SuperByteBuffer buffer = CachedBuffers.partial(model, state).reset();
        buffer.translate(pivot);
        buffer.rotateCentered(AngleHelper.rad(degrees), axis);
        buffer.translateBack(pivot);
        return buffer;
    }

    /**
     * 旧版 {@code coolKineticRotationTransform}（:114-155）：按 facing 摆正输入轴/抓取爪，再绕关节轴旋转。
     *
     * @param angle 弧度
     */
    private static SuperByteBuffer coolKineticRotationTransform(final SuperByteBuffer buffer, final Direction facing,
                                                                final float angle, final int light) {
        buffer.light(light);
        switch (facing) {
            case DOWN, UP, SOUTH -> {
                buffer.rotateDegrees(90.0f, Direction.Axis.X);
                buffer.translate(0.0, 0.0, -1.0);
            }
            case NORTH -> {
                buffer.rotateDegrees(-90.0f, Direction.Axis.X);
                buffer.translate(0.0, -1.0, 0.0);
            }
            case WEST -> {
                buffer.rotateDegrees(90.0f, Direction.Axis.Z);
                buffer.translate(0.0, -1.0, 0.0);
            }
            case EAST -> {
                buffer.rotateDegrees(-90.0f, Direction.Axis.Z);
                buffer.translate(-1.0, 0.0, 0.0);
            }
        }

        Direction.Axis axis = Direction.Axis.Y;
        Direction.AxisDirection axisDirection = Direction.AxisDirection.NEGATIVE;
        if (facing == Direction.UP || facing == Direction.DOWN) {
            axis = Direction.Axis.Z;
        }
        if (facing == Direction.EAST || facing == Direction.SOUTH) {
            axisDirection = Direction.AxisDirection.POSITIVE;
        }
        buffer.rotateCentered(angle, axisDirection == Direction.AxisDirection.POSITIVE
                ? ContraptionPartials.positiveDirection(axis)
                : ContraptionPartials.positiveDirection(axis).getOpposite());
        return buffer;
    }

    /** 翼面张开后伸到方块外，放大包围盒避免被视锥剔除（旧版没写，属移植新增）。 */
    @Override
    public AABB getRenderBoundingBox(final PhysBearingBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(0.5);
    }
}
