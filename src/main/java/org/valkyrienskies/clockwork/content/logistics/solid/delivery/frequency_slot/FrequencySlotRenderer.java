package org.valkyrienskies.clockwork.content.logistics.solid.delivery.frequency_slot;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.clockwork.content.logistics.solid.delivery.chute.DeliveryChuteBlockEntity;

/**
 * 交付滑道的频率槽渲染器：把 {@link FrequencySlotBehaviour} 里保存的频率物品画进槽位。
 *
 * <p>对应旧版 {@code content/logistics/solid/delivery/frequency_slot/FrequencySlotRenderer.kt}
 * （{@code ClockworkBlockEntities.kt} 的 {@code .renderer { FrequencySlotRenderer<DeliveryChuteBlockEntity>(...) }}）。
 * 旧版是一个泛型 {@code SmartBlockEntityRenderer}，用 {@code be.getBehaviour(TYPE)} 找到槽位，
 * 再 {@code ValueBoxRenderer.renderItemIntoValueBox} 把物品画进去；本类逐段照做，差异只有：
 * <ul>
 *   <li>旧版用 {@code Minecraft.getInstance().player.getItemInHand(MAIN_HAND).descriptionId ==
 *       "item.create.wrench"} 判断扳手，这里用 {@link AllItems#WRENCH} 的注册条目判断（Create 6 的
 *       item 改名/迁移后 descriptionId 不可靠）。</li>
 *   <li>旧版的 {@code toShipRenderCoordinates} 距离换算按 VS2 的渲染坐标；Sable 的方块实体渲染
 *       已经在子空间 plot 坐标里，直接对主相机位置做距离平方比较即可。</li>
 *   <li>旧版用 {@code behaviour.slot.shouldRender(...)} 判断；本移植的槽位是固定顶面槽，
 *       额外用 {@link FrequencySlotBehaviour#shouldRender()} 包一层（做空 level 保护）。</li>
 * </ul>
 *
 * <p>渲染距离沿用 Create 的 {@code filterItemRenderDistance}（旧版同样用它），超过距离不画，
 * 避免几十个滑道同时绘制物品实体。槽位为空时直接返回。
 */
public class FrequencySlotRenderer extends SmartBlockEntityRenderer<DeliveryChuteBlockEntity> {

    public FrequencySlotRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(DeliveryChuteBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        // 旧版 FrequencySlotRenderer 也是先 super（Create 的 Filtering/Link behaviour 渲染）；
        // 本方块没有那两类 behaviour，调用是无害的，保留是为了以后加过滤槽时不用改渲染链。
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);

        final FrequencySlotBehaviour behaviour = be.getBehaviour(FrequencySlotBehaviour.TYPE);
        if (behaviour == null || be.getLevel() == null) {
            return;
        }

        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || AllItems.WRENCH.isIn(mc.player.getMainHandItem())) {
            // 旧版同规则：手持扳手时不显示槽位物品，避免挡住滚轮/拆装操作。
            return;
        }
        final ItemStack frequency = behaviour.getFrequency();
        if (frequency.isEmpty() || !behaviour.shouldRender()) {
            return;
        }

        final double maxDistance = AllConfigs.client().filterItemRenderDistance.getF();
        final Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        final double dx = camera.x - (be.getBlockPos().getX() + 0.5);
        final double dy = camera.y - (be.getBlockPos().getY() + 0.5);
        final double dz = camera.z - (be.getBlockPos().getZ() + 0.5);
        if (dx * dx + dy * dy + dz * dz > maxDistance * maxDistance) {
            return;
        }

        ms.pushPose();
        behaviour.getSlot().transform(be.getLevel(), be.getBlockPos(), be.getBlockState(), ms);
        ValueBoxRenderer.renderItemIntoValueBox(frequency, ms, buffer, light, overlay);
        ms.popPose();
    }
}
