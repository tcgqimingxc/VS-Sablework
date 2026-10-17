package org.valkyrienskies.clockwork.content.contraptions.gyro.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.clockwork.content.contraptions.client.ContraptionPartials;
import org.valkyrienskies.clockwork.content.contraptions.gyro.GyroBlockEntity;

/**
 * 陀螺（{@code gyro}）的方块实体渲染器。
 *
 * <p>对应旧版 {@code content/physicalities/gyro/GyroBlockEntityRenderer.kt}（99 行）：
 * 底板 {@code GYRO_BASE} + 绕 Y 轴自转的陀螺本体 + 底部输入轴 {@code PHYS_SHAFT}。
 *
 * <p><b>与旧版逐项对照</b>
 * <ul>
 *   <li>陀螺本体：{@code CachedBuffers.block(state)}（即 {@code block/gyro/block} → {@code gyro.obj}）
 *       绕 Y 轴自转，两个 {@code translate(±0.45,0,±0.45)} 与末尾的
 *       {@code translate(0,1,0)} 逐行照抄旧版 :82-89。</li>
 *   <li>输入轴：旧版自己手写 {@code coolKineticRotationTransform}（:53-65：先绕 X 轴 −90°、
 *       再下移 1 格、最后绕 Z 轴按角度转），这里原样照抄。</li>
 *   <li>输入轴模型：{@code partialFacing(PHYS_SHAFT, state, DOWN)}，同旧版 :94-98。</li>
 *   <li>角度：旧版用自己维护的 {@code LerpedFloat visualSpeed + angle}；1.21.1 的
 *       {@code KineticBlockEntity} 已经没有这两个字段（javap 核实），因此改用父类
 *       {@link #getAngleForBe}（{@code AnimationTickHolder.getRenderTime() * speed * 3/10 + offset}，
 *       与旧式 {@code speed * 3/10} 同一换算，且按渲染时间推进更平滑）。</li>
 * </ul>
 *
 * <p><b>未移植的部件</b>：旧版 {@code renderCore}（:67-76）额外画三层水晶核心
 * （{@code RenderUtil.renderCubeMatrix}：内层 {@code RenderType.endPortal()}、中层自研
 * {@code ClockworkRenderTypes.CRYSTAL}（带 shader）、外层 {@code entityTranslucent(PURPLE_HUE)}，
 * 缩放 1.5）。自研 render type / shader 本移植没有（{@code ClockworkRenderTypes} 未移植），
 * 照抄会崩，所以本轮只补"结构 + 自转"；缺的只是模型中心那个发光方块，不影响其余部件。
 *
 * <p>旧版此渲染器重写了 {@code renderSafe} 且<b>没有</b>调用 {@code super.renderSafe}
 * （:28 被注释掉），所以这里也不调 —— 否则会多画一根标准轴（旧版没有）。
 */
public class GyroRenderer extends KineticBlockEntityRenderer<GyroBlockEntity> {

    /** 自转轴：写死 Y，与 {@code GyroBlock#getRotationAxis} 一致（旧版 {@code getRotationAxisOf(be)} 同样恒为 Y）。 */
    private static final Direction.Axis SPIN_AXIS = Direction.Axis.Y;

    public GyroRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(final GyroBlockEntity be, final float partialTicks, final PoseStack ms,
                              final MultiBufferSource buffer, final int light, final int overlay) {
        final BlockState state = be.getBlockState();
        final VertexConsumer vb = buffer.getBuffer(RenderType.solid());
        final float angle = getAngleForBe(be, be.getBlockPos(), SPIN_AXIS);

        // 1) 底板（旧版 :40-44）：唯一不随轮子转的部件
        CachedBuffers.partial(ContraptionPartials.GYRO_BASE, state)
                .reset()
                .light(light)
                .color(255, 255, 255, 255)
                .renderInto(ms, vb);

        // 2) 陀螺本体：整个方块模型绕 Y 自转
        renderWheel(be, ms, light, state, angle, vb);

        // 3) 底部输入轴（旧版 :46-49）
        coolKineticRotationTransform(CachedBuffers.partialFacing(ContraptionPartials.PHYS_SHAFT, state,
                Direction.DOWN).reset(), angle, light).renderInto(ms, vb);
    }

    /**
     * 陀螺本体。
     *
     * <p>两次 {@code translate(±0.45,0,±0.45)} 把旋转枢轴从方块中心挪到 {@code (0.95,0.5,0.95)}，
     * 这是旧版刻意为之（:82-86，注释里作者也在怀疑这里），<b>不要"顺手修正"</b>成绕中心转：
     * 挪动枢轴后陀螺的摆动幅度才是旧版的观感。
     */
    private static void renderWheel(final GyroBlockEntity be, final PoseStack ms, final int light,
                                    final BlockState state, final float angle, final VertexConsumer vb) {
        final SuperByteBuffer wheel = CachedBuffers.block(state).reset();
        wheel.translate(0.45, 0.0, 0.45);
        wheel.translate(-0.45, 0.0, -0.45);
        kineticRotationTransform(wheel, be, SPIN_AXIS, AngleHelper.rad(angle), light);
        wheel.translate(0.0, 1.0, 0.0);
        wheel.renderInto(ms, vb);
    }

    /**
     * 旧版 {@code coolKineticRotationTransform}：把"躺着的"轴模型摆到方块下方，再绕自身轴旋转。
     *
     * @param angle 弧度（旧版传的也是 {@code getAngleForBe} 的返回值）
     */
    private static SuperByteBuffer coolKineticRotationTransform(final SuperByteBuffer buffer, final float angle,
                                                                final int light) {
        buffer.light(light);
        buffer.rotateDegrees(-90.0f, Direction.Axis.X);
        buffer.translate(0.0, -1.0, 0.0);
        buffer.rotateCentered(angle, ContraptionPartials.positiveDirection(Direction.Axis.Z));
        return buffer;
    }
}
