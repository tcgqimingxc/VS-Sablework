package org.valkyrienskies.clockwork.content.contraptions.flap.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.clockwork.content.contraptions.flap.FlapBearingBlock;
import org.valkyrienskies.clockwork.content.contraptions.flap.FlapBearingBlockEntity;
import org.valkyrienskies.clockwork.content.contraptions.flap.FlapBearingPartials;

/**
 * 襟翼轴承（{@code andesite_flap_bearing} / {@code smart_flap_bearing}）的方块实体渲染器。
 *
 * <p>对应官方 / 旧版 {@code content/contraptions/flap/FlapBearingRenderer.kt}
 * （{@code ClockworkBlockEntities.kt:269} 与 {@code :294} 两处 {@code .renderer { FlapBearingRenderer(...) }}，
 * 两个方块共用同一个渲染器）。
 *
 * <p>画两件东西：
 * <ol>
 *   <li>顶盖 {@link FlapBearingPartials#TOP}（{@code block/flap_bearing/top}）—— 旧版 :47-63 的
 *       {@code BEARING_TOP_FLAP}，按 facing 摆正后随输入轴转动；</li>
 *   <li>输入轴半根 —— 旧版 :69-75 的 {@code getRotatedModel} = {@code partialFacing(SHAFT_HALF, facing.opposite)}。</li>
 * </ol>
 *
 * <p><b>为什么不调 {@code super.renderSafe}</b>：Create 6 的
 * {@code KineticBlockEntityRenderer#renderSafe} 第一句就是
 * {@code if (VisualizationManager.supportsVisualization(level)) return;}（javap -c 核实，字节码 0-10）。
 * 本工程没有注册任何 Flywheel visual，而客户端的 Flywheel 实例化默认是开着的 ——
 * 所以走父类等于<b>永远不画</b>。这里改成显式渲染：
 * {@code getRenderedBlockState → getRotatedModel → renderRotatingBuffer}，与父类在
 * "无 visual" 分支里做的事逐句等价。
 *
 * <p><b>角度来源与旧版的差异</b>：旧版读 {@code BearingBlockEntity#getInterpolatedAngle}（装置的伺服角）。
 * 本移植的 {@code AbstractBearingBlockEntity} 把目标角保存在服务端并只写进 NBT / 装配时 {@code sendData()}，
 * 每刻推进时并<b>不</b>同步到客户端，因此客户端拿不到伺服角；这里改用与输入轴一致的
 * {@link #getAngleForBe}（同 {@code GyroRenderer} / {@code PropellerBearingRenderer}）。
 * 观感差异：顶盖随输入转速转，而不是随襟翼摆角摆 —— 需实机确认可接受度（见 docs/RENDER-AUDIT.md §5）。
 *
 * <p><b>角度单位（唯一约定，别再混）</b>
 * <ul>
 *   <li><b>自转角 = 弧度</b>：{@link #getAngleForBe} 返回弧度
 *       （{@code ((renderTime*speed*3/10 + offset) % 360) / 180 * PI}，Create 6.0.10 字节码核实），
 *       {@code kineticRotationTransform} 转手就把这个值交给 {@code SuperByteBuffer#rotateCentered}
 *       （弧度重载）；所以原值直传，<b>不要</b>再套 {@code AngleHelper.rad} ——
 *       那会把弧度当角度再除 57.3，顶盖看起来几乎不转（2026-10-18 修）。</li>
 *   <li><b>朝向角 = 角度</b>：{@code AngleHelper.horizontalAngle/verticalAngle} 与常量 90 都是角度，
 *       只在交给 {@code rotateCentered} 时用 {@code AngleHelper.rad} 转一次。</li>
 * </ul>
 */
public class FlapBearingRenderer extends KineticBlockEntityRenderer<FlapBearingBlockEntity> {

    public FlapBearingRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(FlapBearingBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        BlockState state = be.getBlockState();
        Direction facing = state.getValue(FlapBearingBlock.FACING);
        float angle = getAngleForBe(be, be.getBlockPos(), facing.getAxis());

        renderShaft(be, ms, buffer, light);
        renderTop(be, state, facing, angle, ms, buffer, light);
    }

    /** 输入轴：父类在无 Flywheel visual 时画的那一份（旧版 :69-75）。 */
    private void renderShaft(FlapBearingBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light) {
        BlockState rendered = getRenderedBlockState(be);
        renderRotatingBuffer(be, getRotatedModel(be, rendered), ms,
                buffer.getBuffer(getRenderType(be, rendered)), light);
    }

    /**
     * 顶盖：先按 facing 摆正（与 blockstate JSON 的 x/y 同一约定），再绕 facing 轴自转。
     * 三个 {@code rotateCentered} 的顺序与旧版 :53-63 逐行一致，参数换了 1.21 的重载（弧度/轴向不变）。
     *
     * @param angle {@link #getAngleForBe} 的原始返回值，单位<b>弧度</b>；直接交给
     *              {@code kineticRotationTransform}（内部 {@code rotateCentered}，弧度），勿再换算
     */
    private void renderTop(FlapBearingBlockEntity be, BlockState state, Direction facing, float angle,
                           PoseStack ms, MultiBufferSource buffer, int light) {
        boolean axisAlongFirst = state.getValue(FlapBearingBlock.AXIS_ALONG_FIRST_COORDINATE);
        SuperByteBuffer top = CachedBuffers.partial(FlapBearingPartials.TOP, state).reset();
        // 弧度直传：getAngleForBe 与 kineticRotationTransform/rotateCentered 的单位都是弧度。
        kineticRotationTransform(top, be, facing.getAxis(), angle, light);

        if (facing.getAxis().isHorizontal()) {
            top.rotateCentered(AngleHelper.rad(AngleHelper.horizontalAngle(facing.getOpposite())), Direction.UP);
        } else if (!axisAlongFirst) {
            top.rotateCentered(AngleHelper.rad(90), Direction.UP);
        }
        top.rotateCentered(AngleHelper.rad(-90 - AngleHelper.verticalAngle(facing)), Direction.EAST);

        VertexConsumer vb = buffer.getBuffer(RenderType.solid());
        top.renderInto(ms, vb);
    }

    /** 与 Create 的 {@code BearingRenderer} 一致：渲染用方块状态换成朝向反面的半根轴。 */
    @Override
    protected SuperByteBuffer getRotatedModel(FlapBearingBlockEntity be, BlockState state) {
        return CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state,
                state.getValue(FlapBearingBlock.FACING).getOpposite());
    }
}
