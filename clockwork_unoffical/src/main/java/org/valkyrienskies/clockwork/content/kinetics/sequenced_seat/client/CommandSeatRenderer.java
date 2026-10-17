package org.valkyrienskies.clockwork.content.kinetics.sequenced_seat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import org.valkyrienskies.clockwork.content.kinetics.sequenced_seat.CommandSeatBlockEntity;

/**
 * 命令座椅（{@code command_seat}）的方块实体渲染器：画 1 根向下的输入轴 + 4 根水平输出轴。
 *
 * <p>对应旧版 {@code content/kinetics/sequenced_seat/SequencedSeatRenderer.kt}：
 * {@code getRotatedModel} 返回朝下的 {@code AllPartialModels.SHAFT_HALF}（输入轴），
 * 然后对 {@code Direction.Plane.HORIZONTAL} 的四个方向各画一根 {@code SHAFT_HALF}（输出轴）。
 * {@code ClockworkBlockEntities.kt} 的 {@code COMMAND_SEAT} 条目登记了这个渲染器。
 *
 * <h2>为什么本移植仍然需要它</h2>
 * <p>全方块渲染审计里曾把 {@code command_seat} 记为"旧版只画轴半块、本移植已把输出轴做进 block 模型"，
 * 但逐个核对 {@code models/block/command_seat/block.json} 的 15 个 element 后确认<b>没有任何轴</b>
 * （元素全是底座/座椅/靠背/扶手，坐标范围与 {@code shaft_half} 的 6..10 × 6..10 × 8..16 都不重合）。
 * 也就是说：不补这个 BER，座椅在世界里会缺 5 根轴，与 {@code hasShaftTowards} 宣告的接口不符。
 *
 * <h2>与旧版的差异</h2>
 * <ul>
 *   <li>输入轴沿用旧版的 {@code renderRotatingBuffer}（Create 的
 *       {@link KineticBlockEntityRenderer#renderRotatingBuffer}），所以它会按座椅的输入转速旋转；
 *       四根水平输出轴与旧版一致，静态绘制（旧版的输出轴旋转代码被注释掉了，
 *       {@code SequencedSeatRenderer.kt:41-51}）。</li>
 *   <li>没有使用 {@code models/block/command_seat/buttonone|buttontwo|joystick} 三个子模型：
 *       它们既不被本工程的 blockstate 引用，也不被旧版 blockstate / 渲染器引用（旧版同样只有轴半块），
 *       属于上游遗留素材，硬塞进来反而会出现悬空的按钮。</li>
 * </ul>
 */
public class CommandSeatRenderer extends SmartBlockEntityRenderer<CommandSeatBlockEntity> {

    public CommandSeatRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(CommandSeatBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);

        final VertexConsumer vb = buffer.getBuffer(RenderType.solid());
        // 输入轴：从座椅底部接入（旧版 getRotatedModel 的 Direction.DOWN + renderRotatingBuffer）。
        final SuperByteBuffer input = CachedBuffers
                .partialFacing(AllPartialModels.SHAFT_HALF, be.getBlockState(), Direction.DOWN)
                .overlay(overlay);
        KineticBlockEntityRenderer.renderRotatingBuffer(be, input, ms, vb, light);
        // 输出轴：四个水平面（旧版 for (hDir in Direction.Plane.HORIZONTAL)），静态。
        for (final Direction direction : Direction.Plane.HORIZONTAL) {
            renderStaticShaft(be, direction, ms, vb, light, overlay);
        }
    }

    private static void renderStaticShaft(CommandSeatBlockEntity be, Direction direction, PoseStack ms,
                                          VertexConsumer vb, int light, int overlay) {
        CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, be.getBlockState(), direction)
                .light(light)
                .overlay(overlay)
                .renderInto(ms, vb);
    }
}
