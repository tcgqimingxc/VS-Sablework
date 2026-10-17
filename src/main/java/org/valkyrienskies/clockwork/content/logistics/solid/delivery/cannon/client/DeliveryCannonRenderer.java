package org.valkyrienskies.clockwork.content.logistics.solid.delivery.cannon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.clockwork.content.logistics.solid.delivery.cannon.DeliveryCannonBlock;
import org.valkyrienskies.clockwork.content.logistics.solid.delivery.cannon.DeliveryCannonBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.solid.delivery.cannon.DeliveryCannonPartials;

/**
 * 交付加农炮（{@code delivery_cannon}）的方块实体渲染器：画炮座 / 转台 / 炮管 / 天线四段。
 *
 * <p>对应官方 / 旧版 {@code content/logistics/solid/delivery/cannon/DeliveryCannonRenderer.kt}
 * （{@code ClockworkBlockEntities.kt:784} 的 {@code .renderer { DeliveryCannonRenderer(...) }}）。
 *
 * <p>为什么必须有它：{@code blockstates/delivery_cannon.json} 只引用 {@code delivery_cannon/block}，
 * 而那是个 3 元素的<b>底座</b>（y -4..4）。真正的炮身全在四个子模型里：
 * mount（y 4..17，3 元素）、cannon_base（y 9..22，4 元素）、cannon_barrel（z -12..2，2 元素）、
 * antenna（y 21..27，2 元素）。缺了渲染器，实机看到的是一座齐腰高的空底座。
 *
 * <p><b>与旧版的差异（只有一处，且是本轮刻意的）</b>：旧版按 {@code xRot/yRot} 的
 * {@code LerpedFloat} 做转头动画，并驱动开炮时的后坐/天线抖动。本移植的
 * {@link DeliveryCannonBlockEntity} 明确不做转头动画（类注释"与旧版的简化：没有转头动画（不做 BER）"），
 * 服务端也没有 {@code xRot/yRot} 状态 —— 因此这里画<b>静止姿态</b>：
 * 各部件按作者摆放的默认位置绘制（旧版在 {@code xRot=yRot=0} 时画的正是这一帧）。
 * 也就是说：外观恢复完整，但没有瞄准动画。等把瞄准状态搬回 BE 后，只需在
 * {@link #render} 里按角度插入旋转即可（旧版 {@code rotateCentered}/{@code rotateToAngle} 的枢轴常量
 * {@code pivot=(0,1,0.5)}、{@code antennaPivot=(0.75,1.3125,0.75)} 已记在本文档以便后续对齐）。
 *
 * <p>朝向：炮身局部坐标与底座模型共用同一套约定，所以这里必须复刻 blockstate 施加的 y 旋转，
 * 否则底座与炮身会错开。映射直接照抄 {@code blockstates/delivery_cannon.json}
 * （含它 north/south 都是 90° 的原样行为 —— 资源如此，不在渲染层"顺手修正"）。
 */
public class DeliveryCannonRenderer extends SmartBlockEntityRenderer<DeliveryCannonBlockEntity> {

    /** 与 blockstate JSON 完全一致的 y 旋转（单位：度）。 */
    private static final int ROTATION_WEST = 0;
    private static final int ROTATION_NORTH_SOUTH = 90;
    private static final int ROTATION_EAST = 180;

    public DeliveryCannonRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(DeliveryCannonBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        // 父类只画 Create 的过滤/频率悬浮显示（本方块用自定义频率物品，没有 value box）
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);

        BlockState state = be.getBlockState();
        Direction facing = state.getValue(DeliveryCannonBlock.FACING);
        VertexConsumer vb = buffer.getBuffer(RenderType.cutout());

        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(com.mojang.math.Axis.YP.rotationDegrees(bodyRotation(facing)));
        ms.translate(-0.5, -0.5, -0.5);

        render(DeliveryCannonPartials.MOUNT, state, ms, vb, light, overlay);
        render(DeliveryCannonPartials.BASE, state, ms, vb, light, overlay);
        render(DeliveryCannonPartials.BARREL, state, ms, vb, light, overlay);
        render(DeliveryCannonPartials.ANTENNA, state, ms, vb, light, overlay);

        ms.popPose();
    }

    private static void render(PartialModel partial, BlockState state, PoseStack ms, VertexConsumer vb,
                               int light, int overlay) {
        CachedBuffers.partial(partial, state).light(light).overlay(overlay).renderInto(ms, vb);
    }

    /** 复刻 {@code blockstates/delivery_cannon.json} 的 y 旋转。 */
    private static float bodyRotation(Direction facing) {
        return switch (facing) {
            case NORTH, SOUTH -> ROTATION_NORTH_SOUTH;
            case EAST -> ROTATION_EAST;
            default -> ROTATION_WEST;
        };
    }
}
