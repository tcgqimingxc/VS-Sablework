package org.valkyrienskies.clockwork.content.curiosities.tools.drill.client;

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
 * 手持动力钻的物品渲染器（对应旧版 {@code HandheldDrillRenderer.kt}）。
 *
 * <p>物品模型只包含机身：钻头（{@link DrillPartials#BIT}）与齿轮（{@link DrillPartials#COG}）
 * 是独立模型，旧版就是在这里画上去的。不还原这一段，游戏里只能看到没有钻头的半截钻。
 *
 * <p>与旧版的差异：无。旧版那三行被注释掉的 {@code xOffset} 死代码没有搬过来。
 */
public class HandheldDrillRenderer extends CustomRenderedItemModelRenderer {

    @Override
    protected void render(final ItemStack stack, final CustomRenderedItemModel model,
                          final PartialItemModelRenderer renderer, final ItemDisplayContext transformType,
                          final PoseStack ms, final MultiBufferSource buffer, final int light, final int overlay) {
        // 机身
        renderer.renderSolid(model.getOriginalModel(), light);

        // 齿轮：跟随 Create 的滚轮值自转（旧版同名调用）
        ms.pushPose();
        ms.mulPose(Axis.ZP.rotationDegrees(
                ScrollValueHandler.getScroll(AnimationTickHolder.getPartialTicks())));
        renderer.render(DrillPartials.COG.get(), light);
        ms.popPose();

        // 钻头：按住左键（挖掘）时高速自转
        // 1.21.1 的 KeyMapping.isDown 是包私有字段，公开访问器是 isDown()
        if (Minecraft.getInstance().options.keyAttack.isDown()) {
            ms.pushPose();
            ms.mulPose(Axis.ZP.rotationDegrees(AnimationTickHolder.getTicks() * 40f % 360f));
            renderer.render(DrillPartials.BIT.get(), light);
            ms.popPose();
        } else {
            renderer.render(DrillPartials.BIT.get(), light);
        }
    }
}
