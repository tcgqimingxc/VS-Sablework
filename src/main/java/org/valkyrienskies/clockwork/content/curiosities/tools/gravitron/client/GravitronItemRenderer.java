package org.valkyrienskies.clockwork.content.curiosities.tools.gravitron.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.valkyrienskies.clockwork.content.curiosities.tools.gravitron.CreativeGravitronItem;
import org.valkyrienskies.clockwork.content.curiosities.tools.gravitron.GravitronDialState;

/**
 * 重力枪的物品渲染器（对应旧版 {@code GravitronItemRenderer.kt}）。
 *
 * <p>为什么必须自己画：物品模型只包含壳体，爪子（9 个 {@code prong*} 部件）与表盘指针
 * （{@code dialhand}）是独立模型，旧版就是在这里逐个叠上去的；不还原这一段，
 * 游戏里看到的就是没有爪子的半截枪身。
 *
 * <p>与旧版的差异：
 * <ul>
 *   <li>表盘角度来自 {@link GravitronDialState}（服务端 {@code GravitronDialPacket} 同步的质量占比），
 *       旧版角度存在玩家实体上（mixin 字段），本移植没有 mixin。</li>
 *   <li>旧版渲染器里的 {@code tempAngle} 缓动搬进了 {@code GravitronDialState}（按时间缓动，
 *       与帧率无关）；此处只负责画。</li>
 *   <li>旧版那段被注释掉的爪子手算变换（几十行死代码）没有搬过来。</li>
 * </ul>
 */
public class GravitronItemRenderer extends CustomRenderedItemModelRenderer {

    /** 超充光效的自转角 [度]（旧版同名实例字段）。 */
    private float overloadAngle;

    @Override
    protected void render(final ItemStack stack, final CustomRenderedItemModel model,
                          final PartialItemModelRenderer renderer, final ItemDisplayContext transformType,
                          final PoseStack ms, final MultiBufferSource buffer, final int light, final int overlay) {
        final float partialTicks = AnimationTickHolder.getPartialTicks();
        final float dialAngle = GravitronDialState.current();

        // 旧版行为：指针扫到 270° 以上（结构快超重）时枪身轻微抖动
        if (dialAngle >= 270f) {
            ms.translate(Mth.sin(partialTicks / 2f) / 32f, Mth.cos(partialTicks / 2f + 7f) / 64f, 0f);
        }

        renderer.renderSolid(model.getOriginalModel(), light);

        renderDial(dialAngle, ms, renderer, light);
        renderer.render(GravitronPartials.PRONG_TOP_ONE.get(), light);
        renderer.render(GravitronPartials.PRONG_TOP_TWO.get(), light);
        renderer.render(GravitronPartials.PRONG_TOP_THREE.get(), light);
        renderer.render(GravitronPartials.PRONG_LEFT_ONE.get(), light);
        renderer.render(GravitronPartials.PRONG_LEFT_TWO.get(), light);
        renderer.render(GravitronPartials.PRONG_LEFT_THREE.get(), light);
        renderer.render(GravitronPartials.PRONG_RIGHT_ONE.get(), light);
        renderer.render(GravitronPartials.PRONG_RIGHT_TWO.get(), light);
        renderer.render(GravitronPartials.PRONG_RIGHT_THREE.get(), light);

        if (stack.getItem() instanceof CreativeGravitronItem) {
            overloadAngle = (overloadAngle + partialTicks) % 360f;
            ms.pushPose();
            ms.translate(0.0, -0.1, 0.0);
            ms.mulPose(new Quaternionf(new AxisAngle4f(AngleHelper.rad(overloadAngle), 0f, 0f, 1f)));
            ms.translate(8.0 / 16.0, 8.0 / 16.0, 0.0);
            renderer.render(GravitronPartials.OVERLOAD_FX.get(), light);
            ms.popPose();
        }
    }

    /**
     * 表盘指针：绕模型里的表盘轴心旋转，角度即"抓取质量占比"。
     *
     * <p>下面这串平移/旋转是从旧版 {@code GravitronItemRenderer#renderDial} 原样抄过来的
     * （包括 {@code translate(x, y, y)} 这个把 z 写成 y 的笔误）——它决定指针实际落在表盘哪个位置，
     * 改动会让指针与 1.20.1 的外观不一致。
     */
    private static void renderDial(final float angle, final PoseStack ms,
                                   final PartialItemModelRenderer renderer, final int light) {
        final float x = -7.9f / 16f;
        final float y = -7.3f / 16f;
        final float z = -22.0f / 16f;

        ms.pushPose();
        ms.mulPose(new Quaternionf(new AxisAngle4f(AngleHelper.rad(22.5), -1f, 0f, 0f)));
        ms.translate(0.275, 0.2275, -0.115);
        ms.translate(x, y, y);
        ms.mulPose(new Quaternionf(new AxisAngle4f(AngleHelper.rad(angle - 180.0 + 10.0), 0f, 0f, -1f)));
        ms.translate(-x, -y, -z);
        renderer.render(GravitronPartials.DIAL_HAND.get(), light);
        ms.popPose();
    }
}
