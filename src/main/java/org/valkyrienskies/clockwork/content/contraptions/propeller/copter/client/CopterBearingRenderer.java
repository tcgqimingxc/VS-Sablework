package org.valkyrienskies.clockwork.content.contraptions.propeller.copter.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
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
import org.valkyrienskies.clockwork.content.contraptions.propeller.copter.CopterBearingBlockEntity;

/**
 * 直升机旋翼轴承（{@code copter_bearing}）的渲染器：补上静态方块模型缺的
 * 顶盖（{@code top}）、桨盘（{@code wafer}）与四根操纵活塞（{@code piston_*}）。
 *
 * <p>对应旧版 {@code CopterBearingRenderer.kt}（198 行）。旧版把这些局部模型
 * （{@code ClockworkPartials.SMART_PROP_*}）画在方块上方，并用一个
 * {@code clientTiltQuat}（客户端从服务端同步的倾角四元数插值而来）让顶盖与桨盘整体倾斜，
 * 顶盖再绕输入轴自转。
 *
 * <p><b>与旧版的取舍（倾角没有画）</b>
 * <ul>
 *   <li>倾角是"旋翼倾斜盘"玩法的一部分：旧版 {@code CopterBearingBlockEntity} 里有一整套
 *       PID 稳定器（{@code computeStabilizedTarget}、{@code tiltVector} / {@code targetTiltQuaternion}、
 *       限位锥 ±24°），服务端算出倾角、客户端用 {@code clientTargetTiltQuat} 插值后渲染。
 *       这套倾角在<b>本移植里根本不存在</b>：{@link CopterBearingBlockEntity} 只是把螺旋桨的
 *       叶素模型换成 {@code PropellerKind.COPTER} 规格，推力沿 {@code facing} 施加，
 *       没有倾斜盘、也没有倾角状态可同步 —— 所以这里不是"渲染器偷懒没接同步"，
 *       而是没有数据源可接。真要做需要先移植整个倾角控制器（属于玩法移植，不属于 BER 补齐），
 *       故本轮不画倾角，并在实机确认点里写清"顶盖应水平"。</li>
 *   <li>旧版渲染器<b>不画</b>输入轴（它把 {@code super.renderSafe} 注释掉了），这里保持一致，
 *       因此本类继承 {@link SmartBlockEntityRenderer} 而不是 {@code KineticBlockEntityRenderer}
 *       （后者会在 Flywheel 关闭时顺手画一遍方块模型，与本渲染器重复）。</li>
 *   <li>自转角度：旧版用 {@code getInterpolatedAngle}（contraption 角度）；这里用输入轴角速度换算的
 *       {@code KineticBlockEntityRenderer#getAngleForBe}（理由同 {@code PropellerBearingRenderer}）。</li>
 * </ul>
 */
public class CopterBearingRenderer extends SmartBlockEntityRenderer<CopterBearingBlockEntity> {

    public CopterBearingRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(final CopterBearingBlockEntity be, final float partialTicks, final PoseStack ms,
                              final MultiBufferSource buffer, final int light, final int overlay) {
        final BlockState state = be.getBlockState();
        final Direction facing = state.getValue(BlockStateProperties.FACING);
        final VertexConsumer vb = buffer.getBuffer(RenderType.solid());

        // 四根操纵活塞与桨盘：只跟 facing 走，不随输入轴转（旧版 renderPistons / renderWafer 同样不转）
        renderStatic(ContraptionPartials.COPTER_PISTON_NW, state, facing, ms, vb, light);
        renderStatic(ContraptionPartials.COPTER_PISTON_NE, state, facing, ms, vb, light);
        renderStatic(ContraptionPartials.COPTER_PISTON_SW, state, facing, ms, vb, light);
        renderStatic(ContraptionPartials.COPTER_PISTON_SE, state, facing, ms, vb, light);
        renderStatic(ContraptionPartials.COPTER_WAFER, state, facing, ms, vb, light);

        // 顶盖：绕 facing 轴自转。getAngleForBe 返回弧度，必须用 rotateCentered（不是 ...Degrees），
        // 否则弧度被当角度再除 57.3，旋翼顶盖看起来几乎不转（同 GyroRenderer / PropellerBearingRenderer）。
        final float angle = KineticBlockEntityRenderer.getAngleForBe(be, be.getBlockPos(), facing.getAxis());
        final SuperByteBuffer top = ContraptionRenderUtil.orientToFacing(
                CachedBuffers.partial(ContraptionPartials.COPTER_TOP, state).reset(), facing);
        top.rotateCentered(angle, Direction.UP);
        top.light(light).renderInto(ms, vb);
    }

    /** 摆到 facing 方向后直接画（四个活塞与桨盘都是静态件）。 */
    private static void renderStatic(final PartialModel model, final BlockState state, final Direction facing,
                                     final PoseStack ms, final VertexConsumer vb, final int light) {
        ContraptionRenderUtil.orientToFacing(CachedBuffers.partial(model, state).reset(), facing)
                .light(light)
                .renderInto(ms, vb);
    }
}
