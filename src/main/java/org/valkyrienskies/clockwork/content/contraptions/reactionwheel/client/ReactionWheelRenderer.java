package org.valkyrienskies.clockwork.content.contraptions.reactionwheel.client;

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
import org.valkyrienskies.clockwork.content.contraptions.reactionwheel.ReactionWheelBlock;
import org.valkyrienskies.clockwork.content.contraptions.reactionwheel.ReactionWheelBlockEntity;

/**
 * 反作用轮（{@code reactionwheel}）的方块实体渲染器：让整个轮子绕输入轴自转。
 *
 * <p>对应旧版 {@code content/physicalities/reactionwheel/ReactionWheelRenderer.kt}（49 行）：
 * {@code CachedBuffers.block(blockState)} 绕输入轴自转，另把 {@code getRenderedBlockState}
 * 改成 {@code shaft(axis)}（即"渲染用的方块状态"是那根轴，而不是轮子本身）。
 *
 * <p><b>与旧版的差异</b>
 * <ul>
 *   <li>旧版读自己维护的 {@code LerpedFloat visualSpeed + angle}；1.21.1 的
 *       {@code KineticBlockEntity} 已无这两个字段（javap 核实），改用父类
 *       {@link #getAngleForBe}（换算同为 {@code speed * 3/10}，按渲染时间推进）。</li>
 *   <li>旧版重写 {@code getRenderedBlockState} 只画轴、不调 {@code super.renderSafe}，
 *       于是"轴"由 BER 画、轮子由它自己画的一遍覆盖；本移植改为
 *       <b>显式画轴 + 自己画转动的轮子</b>，比旧版少一层歧义。
 *       不能靠 {@code super.renderSafe}：Create 6 的父类实现第一句是
 *       {@code if (VisualizationManager.supportsVisualization(level)) return;}（javap -c 核实），
 *       本工程不注册 Flywheel visual，走父类等于"轴永远不画"（旧版正是这样丢掉轴的）。</li>
 * </ul>
 */
public class ReactionWheelRenderer extends KineticBlockEntityRenderer<ReactionWheelBlockEntity> {

    public ReactionWheelRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(final ReactionWheelBlockEntity be, final float partialTicks, final PoseStack ms,
                              final MultiBufferSource buffer, final int light, final int overlay) {
        // 轴（旧版靠 getRenderedBlockState = shaft(axis) 达到同样效果；这里显式画，理由见类注释）
        final BlockState rendered = getRenderedBlockState(be);
        renderRotatingBuffer(be, getRotatedModel(be, rendered), ms,
                buffer.getBuffer(getRenderType(be, rendered)), light);

        final BlockState state = be.getBlockState();
        final Direction.Axis axis = ((ReactionWheelBlock) state.getBlock()).getRotationAxis(state);
        final float angle = getAngleForBe(be, be.getBlockPos(), axis);
        final VertexConsumer vb = buffer.getBuffer(RenderType.solid());

        // 轮子：整个方块模型绕输入轴自转
        final SuperByteBuffer wheel = CachedBuffers.block(state).reset();
        wheel.rotateCentered(AngleHelper.rad(angle), ContraptionPartials.positiveDirection(axis));
        wheel.light(light).renderInto(ms, vb);
    }

    /** 与旧版一致：渲染用方块状态换成沿转动轴的轴（否则会把自己的轮子再画一遍）。 */
    @Override
    protected BlockState getRenderedBlockState(final ReactionWheelBlockEntity be) {
        return shaft(((ReactionWheelBlock) be.getBlockState().getBlock())
                .getRotationAxis(be.getBlockState()));
    }
}
