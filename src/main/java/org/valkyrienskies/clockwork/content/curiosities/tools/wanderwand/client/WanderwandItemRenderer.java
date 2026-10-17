package org.valkyrienskies.clockwork.content.curiosities.tools.wanderwand.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.valkyrienskies.clockwork.content.curiosities.tools.CWToolsIndex;

/**
 * 游荡法杖的物品渲染器（对应旧版 {@code WanderwandItemRenderer.kt}）。
 *
 * <p>物品模型只包含杖身；杖头水晶（三层）与法阵环是独立模型，旧版在这里画上去。
 * 不还原这一段，游戏里看到的就是一根没有任何水晶的光杆。
 *
 * <p><b>与旧版的差异（必须知道）</b>
 * <ul>
 *   <li>旧版水晶用自定 shader（{@code ClockworkRenderTypes.CRYSTAL} + 一张空矩阵贴图）与
 *       {@code RenderType.endPortal()} / {@code entityTranslucent(purple_hue)} 三层叠加；
 *       本移植没有引入该 shader，三层都改用普通渲染层（{@code PartialItemModelRenderer#render}），
 *       观感是"三层都是水晶贴图"，不会发光。</li>
 *   <li>旧版的位置变换（{@code RenderUtil.renderCubeMatrix} 里 translate(0.25)/scale(1.5)/
 *       translate(-1/(scale*4)) 那一串）是为它那套 shader 空间调出来的；这里<b>直接使用模型自身
 *       的坐标</b>（水晶三层本来就在 y=10.75~14.25、环在 y=14，都落在杖头顶部），
 *       只叠一个上下浮动与自转，位置与原版一致而代码可读。</li>
 *   <li>旧版 {@code incomplete_wanderwand} 只画机身，这里保持一致。</li>
 * </ul>
 */
public class WanderwandItemRenderer extends CustomRenderedItemModelRenderer {

    /** 水晶三层共用的旋转支点：模型自身的中心 (8, 12.5, 8) / 16。 */
    private static final float PIVOT_X = 0.5f;
    private static final float PIVOT_Y = 12.5f / 16f;
    private static final float PIVOT_Z = 0.5f;

    /** 水晶自转角 [度]（旧版同名实例字段，每帧 +0.1）。 */
    private float crystalAngle;
    /** 上下浮动进度，0..2 循环（旧版 {@code idleProgress}，每帧 +0.01）。 */
    private float idleProgress;

    @Override
    protected void render(final ItemStack stack, final CustomRenderedItemModel model,
                          final PartialItemModelRenderer renderer, final ItemDisplayContext transformType,
                          final PoseStack ms, final MultiBufferSource buffer, final int light, final int overlay) {
        renderer.renderSolid(model.getOriginalModel(), light);
        // 半成品法杖在旧版就只有一个杖身，没有水晶
        if (!stack.is(CWToolsIndex.WANDERWAND.get())) {
            return;
        }

        final float nextAngle = (crystalAngle + 0.1f) % 360f;
        final float angle = Mth.rotLerp(
                AnimationTickHolder.getPartialTicks() - 1f, crystalAngle, nextAngle);
        crystalAngle = nextAngle;

        // 上下浮动：旧版 heightAlt = -6/16 + sin(progress)/16，progress 在 0..2 之间来回
        final float bob = (float) Math.sin(idleProgress) / 16f;

        renderPart(WanderwandPartials.CRYSTAL_OUTER, angle, angle, bob, ms, renderer, light);
        renderPart(WanderwandPartials.CRYSTAL, angle, 0f, bob, ms, renderer, light);
        renderPart(WanderwandPartials.CRYSTAL_INNER, angle, angle, bob, ms, renderer, light);

        // 法阵环：不跟着水晶转，只跟着浮动
        ms.pushPose();
        ms.translate(0.0, bob, 0.0);
        renderer.render(WanderwandPartials.RING.get(), light);
        ms.popPose();

        idleProgress += 0.01f;
        if (idleProgress > 2.0f) {
            idleProgress = 0.0f;
        }
    }

    /** 绕水晶自身中心先绕 Y 再绕 X 旋转（对应旧版 {@code data} / {@code innerData} 的 rotation 组合）。 */
    private static void renderPart(final PartialModel part, final float yRotation, final float xRotation,
                                   final float bob, final PoseStack ms,
                                   final PartialItemModelRenderer renderer, final int light) {
        ms.pushPose();
        ms.translate(0.0, bob, 0.0);
        ms.translate(PIVOT_X, PIVOT_Y, PIVOT_Z);
        ms.mulPose(Axis.YP.rotationDegrees(yRotation));
        ms.mulPose(Axis.XP.rotationDegrees(xRotation));
        ms.translate(-PIVOT_X, -PIVOT_Y, -PIVOT_Z);
        renderer.render(part.get(), light);
        ms.popPose();
    }
}
