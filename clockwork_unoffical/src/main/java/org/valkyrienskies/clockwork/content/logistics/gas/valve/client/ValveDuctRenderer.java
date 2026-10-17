package org.valkyrienskies.clockwork.content.logistics.gas.valve.client;

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
import org.valkyrienskies.clockwork.content.logistics.gas.valve.ValveDuctBlock;
import org.valkyrienskies.clockwork.content.logistics.gas.valve.ValveDuctBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.valve.ValveDuctPartials;

/**
 * 气体阀门（{@code valve_duct}）的方块实体渲染器：画阀顶的开度指针 + 旋转的输入轴。
 *
 * <p>对应官方 / 旧版 {@code content/logistics/gas/valve/ValveDuctRenderer.kt}
 * （{@code ClockworkBlockEntities.kt:523} 的 {@code .renderer { ValveDuctRenderer(...) }}）。
 *
 * <p>为什么必须有它：{@code models/block/valve_duct/pointer.json} 是独立子模型，
 * blockstate 只引用阀体（horizontal / vertical 两支），指针<b>只能</b>由 BER 画；
 * 缺了它阀门是一个没有读数指针的铜壳子，玩家看不出开度。
 *
 * <p>与旧版的差异：
 * <ul>
 *   <li>指针角度的来源：旧版读 {@code LerpedFloat pointer}；本移植的
 *       {@code ValveDuctBlockEntity#openness()} 是服务端权威的 {@code double}（每次变化 {@code sendData()}
 *       同步），语义相同（0 = 全关）。旧版 {@code Mth.lerp(pointer, 0, -90)} 的插值本移植省掉：
 *       {@code openness} 本身就是按 {@code chaseSpeed} 逐刻逼近的，已经平滑。</li>
 *   <li>旧版开头有 {@code if (VisualizationManager.supportsVisualization(level)) return;}；
 *       本工程没有注册 Flywheel visual，那行会让渲染器<b>永不工作</b>，所以不保留。</li>
 *   <li>父类的 {@code super.renderSafe} 同样在 Flywheel 实例化开启时直接 return（javap 核实），
 *       因此输入轴改为显式 {@code renderRotatingBuffer} 绘制。</li>
 * </ul>
 */
public class ValveDuctRenderer extends KineticBlockEntityRenderer<ValveDuctBlockEntity> {

    /** 指针从全关到全开扫过的角度（旧版 {@code Mth.lerp(pointer, 0, -90)}）。 */
    private static final float POINTER_TRAVEL_DEGREES = -90f;
    /** 垂直朝向时指针的基准角（旧版 {@code pointerRotationOffset = 90}）。 */
    private static final float VERTICAL_OFFSET_DEGREES = 90f;

    public ValveDuctRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(ValveDuctBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        renderShaft(be, ms, buffer, light);

        BlockState state = be.getBlockState();
        Direction facing = state.getValue(ValveDuctBlock.FACING);
        Direction.Axis ductAxis = ValveDuctBlock.getDuctAxis(state);
        Direction.Axis shaftAxis = getRotationAxisOf(be);

        // 旧版 :41-43：只有"水平管 + 轴向为 X"或"垂直管"时指针才需要额外 90° 基准
        float offset = 0;
        if ((ductAxis.isHorizontal() && shaftAxis == Direction.Axis.X) || ductAxis.isVertical()) {
            offset = VERTICAL_OFFSET_DEGREES;
        }
        float needle = POINTER_TRAVEL_DEGREES * (float) be.openness();

        SuperByteBuffer pointer = CachedBuffers.partial(ValveDuctPartials.POINTER, state);
        pointer.center()
                .rotateYDegrees(AngleHelper.horizontalAngle(facing))
                .rotateXDegrees(facing == Direction.UP ? 0f : facing == Direction.DOWN ? 180f : 90f)
                .rotateYDegrees(offset + needle)
                .uncenter()
                .light(light);

        VertexConsumer vb = buffer.getBuffer(RenderType.solid());
        pointer.renderInto(ms, vb);
    }

    /** 输入轴：父类在无 Flywheel visual 时画的那一份（旧版用 {@code getRenderedBlockState = shaft(axis)}）。 */
    private void renderShaft(ValveDuctBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light) {
        BlockState rendered = getRenderedBlockState(be);
        renderRotatingBuffer(be, getRotatedModel(be, rendered), ms,
                buffer.getBuffer(getRenderType(be, rendered)), light);
    }

    /** 与旧版 {@code getRenderedBlockState} 一致：渲染用方块状态换成沿转动轴的轴。 */
    @Override
    protected BlockState getRenderedBlockState(ValveDuctBlockEntity be) {
        return shaft(getRotationAxisOf(be));
    }
}
