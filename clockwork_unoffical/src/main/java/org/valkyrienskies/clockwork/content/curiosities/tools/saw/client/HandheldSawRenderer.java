package org.valkyrienskies.clockwork.content.curiosities.tools.saw.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueHandler;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 手持切割锯的物品渲染器（对应旧版 {@code HandheldSawRenderer.kt}）。
 *
 * <p>物品模型只包含机身：锯片（{@link SawPartials#BUZZSAW}）与齿轮（{@link SawPartials#COG}）
 * 是独立模型，旧版在这里画上去。
 *
 * <p>与旧版的差异：无（旧版齿轮那段被注释掉的 {@code xOffset} 平移没有搬过来，它在旧版里也是注释状态）。
 */
public class HandheldSawRenderer extends CustomRenderedItemModelRenderer {

    @Override
    protected void render(final ItemStack stack, final CustomRenderedItemModel model,
                          final PartialItemModelRenderer renderer, final ItemDisplayContext transformType,
                          final PoseStack ms, final MultiBufferSource buffer, final int light, final int overlay) {
        renderer.renderSolid(model.getOriginalModel(), light);

        ms.pushPose();
        ms.mulPose(Axis.ZP.rotationDegrees(
                ScrollValueHandler.getScroll(AnimationTickHolder.getPartialTicks())));
        renderer.render(SawPartials.COG.get(), light);
        ms.popPose();

        ms.pushPose();
        // 1.21.1 的 KeyMapping.isDown 是包私有字段，公开访问器是 isDown()
        if (Minecraft.getInstance().options.keyAttack.isDown()) {
            ms.mulPose(Axis.XP.rotationDegrees(AnimationTickHolder.getTicks() * 40f % 360f));
        }
        renderer.render(SawPartials.BUZZSAW.get(), light);
        ms.popPose();
    }
}
