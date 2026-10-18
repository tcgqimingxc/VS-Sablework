package org.valkyrienskies.clockwork.content.contraptions.propeller.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.valkyrienskies.clockwork.content.contraptions.client.ContraptionPartials;
import org.valkyrienskies.clockwork.content.contraptions.client.ContraptionRenderUtil;
import org.valkyrienskies.clockwork.content.contraptions.propeller.PropellerBearingBlockEntity;

/**
 * 螺旋桨轴承（{@code brass_propeller_bearing} / {@code juryrigged_propeller_bearing}）的<b>最小</b>渲染器：
 * 只补上静态方块模型缺的那块顶盖，并让它随输入轴自转。
 *
 * <p>对应旧版 {@code ClockworkBlockEntities.PROPELLER_BEARING} 的
 * {@code .renderer { BearingRenderer<PropellerBearingBlockEntity>(context) }}
 * 与 {@code .clockworkVisual { BearingVisual(...) } }：
 * 旧版直接借用 Create 的 {@code BearingRenderer}（画 {@code AllPartialModels.BEARING_TOP}
 * 与半根输入轴），前提是它的方块实体实现了 Create 的 {@code IBearingBlockEntity}。
 *
 * <p><b>取舍（为什么不直接接 Create 的 BearingRenderer）</b>：本移植的
 * {@link PropellerBearingBlockEntity} 继承的是 {@code AbstractBearingBlockEntity}
 * （装配/约束走 Sable，见该类注释），没有实现 {@code IBearingBlockEntity}，
 * 也<b>不打算</b>为了渲染去改继承关系（装配语义会跟着走样）。
 * 因此这里写一个最小实现，只做旧版真正看得见的那一件事：
 * 把顶盖按输入轴角度转起来。
 *
 * <p>顶盖用的是工程自带的 {@code propeller_bearing/top} 模型
 * （旧版 {@code ClockworkPartials.PROPELLER_TOP} 声明过它但从未引用；贴图 {@code block/propbearingtop}），
 * 而<b>不是</b>旧版实际画的 Create {@code BEARING_TOP}。差异只有顶盖贴图/形状：
 * 本移植的顶盖与工程自带的 {@code propbearingbase} 是同一套美术，视觉上更一致；
 * 想换回 Create 那块只需把 {@link ContraptionPartials#PROPELLER_TOP} 换成
 * {@code AllPartialModels.BEARING_TOP}（两者都已提前烘焙，切换安全）。
 *
 * <p>输入轴由本类显式绘制（{@link #renderShaft}）：{@link #getRotatedModel} 与 Create 的
 * {@code BearingRenderer} 一样返回半根轴（{@code SHAFT_HALF}，朝 {@code facing} 的反面）。
 * <b>不能靠父类</b> {@code KineticBlockEntityRenderer#renderSafe}：它第一句就是
 * {@code if (VisualizationManager.supportsVisualization(level)) return;}（javap -c 核实），
 * 而本方块没有注册 Flywheel visual，走父类等于"轴永远不画"。
 *
 * <p><b>自转角度</b>：旧版用 contraption 的角度 {@code getInterpolatedAngle}；
 * 本移植的轴承本体不进子空间、被装配的是朝向那一侧的结构（由 Sable 物理模拟转动），
 * 所以这里用输入轴角速度换算的 {@code getAngleForBe}（与 {@code GyroRenderer} /
 * {@code ReactionWheelRenderer} 同一套），随转速变化、无需网络同步。
 */
public class PropellerBearingRenderer extends KineticBlockEntityRenderer<PropellerBearingBlockEntity> {

    public PropellerBearingRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    /** 与 Create 的 {@code BearingRenderer} 完全一致：把"渲染用方块状态"换成半根输入轴。 */
    @Override
    protected SuperByteBuffer getRotatedModel(final PropellerBearingBlockEntity be, final BlockState state) {
        return CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state,
                state.getValue(BlockStateProperties.FACING).getOpposite());
    }

    @Override
    protected void renderSafe(final PropellerBearingBlockEntity be, final float partialTicks, final PoseStack ms,
                              final MultiBufferSource buffer, final int light, final int overlay) {
        renderShaft(be, ms, buffer, light);

        final BlockState state = be.getBlockState();
        final Direction facing = state.getValue(BlockStateProperties.FACING);
        // getAngleForBe 返回的是弧度（Create 6.0.10：((renderTime*speed*3/10+offset)%360)/180*PI）。
        // 之前这里用的是 rotateCenteredDegrees(angle)，把弧度当角度再除 57.3，顶盖看起来几乎不转；
        // 改用接收弧度的 rotateCentered（2026-10-18 修，同 GyroRenderer 的二次换算）。
        final float angle = getAngleForBe(be, be.getBlockPos(), facing.getAxis());

        // 顶盖：先摆到 facing 方向（等价 blockstate JSON 的 x/y），再绕它自己的 +Y（= facing 轴）自转
        final SuperByteBuffer top = ContraptionRenderUtil.orientToFacing(
                CachedBuffers.partial(ContraptionPartials.PROPELLER_TOP, state).reset(), facing);
        top.rotateCentered(angle, Direction.UP);

        final VertexConsumer vb = buffer.getBuffer(RenderType.solid());
        top.light(light).renderInto(ms, vb);
    }

    /** 输入轴：父类在"无 Flywheel visual"分支里画的那一份（显式写出来，理由见类注释）。 */
    private void renderShaft(final PropellerBearingBlockEntity be, final PoseStack ms,
                             final MultiBufferSource buffer, final int light) {
        final BlockState rendered = getRenderedBlockState(be);
        renderRotatingBuffer(be, getRotatedModel(be, rendered), ms,
                buffer.getBuffer(getRenderType(be, rendered)), light);
    }
}
