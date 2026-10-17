package org.valkyrienskies.clockwork.content.curiosities.sensor.rotation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.valkyrienskies.clockwork.content.curiosities.sensor.rotation.LodefocusBlockEntity;

/**
 * 透镜（{@code lodefocus}）的方块实体渲染器：把装进去的指南针悬浮显示在方块上方。
 *
 * <p>对应官方 / 旧版 {@code content/curiosities/sensor/rotation/LodefocusRenderer.kt}
 * （{@code ClockworkBlockEntities.kt:333} 的 {@code .renderer { LodefocusRenderer(...) }}）。
 * 旧版会按 {@code itemStack.count} 叠若干层、并按摄像机朝向把指南针摆正。
 *
 * <p>为什么必须有它：{@code blockstates/lodefocus.json} 只有方块本体模型，
 * 指南针是"装进去的物品"，只能由 BER 画；缺了它，玩家装进指南针后<b>什么都看不见</b>。
 *
 * <p><b>与旧版的差异（刻意简化，行为不变）</b>：
 * <ul>
 *   <li>只画一层（旧版按 {@code log2(count)} 叠层；本方块每次只装 1 个指南针，见
 *       {@code LodefocusBlockEntity#insertCompass} 的 {@code copyWithCount(1)}）。</li>
 *   <li>不按摄像机摆正：旧版那份逻辑是给"漏斗里堆了一摞物品"的传送带场景写的（{@code BetHelper}），
 *       对单个悬浮指南针只是让它乱转；这里固定绕 Y 转 90°（旧版 {@code angle = 90}），
 *       指南针的指针方向靠物品自身的 {@code LodestoneTracker} 决定，与旧版一致。</li>
 * </ul>
 */
public class LodefocusRenderer extends SmartBlockEntityRenderer<LodefocusBlockEntity> {

    /** 物品悬浮高度（方块上方 1 格）。 */
    private static final float ITEM_HEIGHT = 1.0f;
    /** 物品缩放（旧版 {@code ms.scale(.5f,.5f,.5f)} × 台座留白，这里取 0.75 让它在格子里更清楚）。 */
    private static final float ITEM_SCALE = 0.75f;
    /** 绕 Y 的固定朝向（旧版 {@code renderItem(..., angle = 90, ...)}）。 */
    private static final float Y_ROTATION_DEGREES = 90f;

    public LodefocusRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(LodefocusBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        ItemStack compass = be.getCompass();
        if (compass.isEmpty() || be.getLevel() == null) {
            return;
        }

        ms.pushPose();
        ms.translate(0.5f, ITEM_HEIGHT, 0.5f);
        ms.mulPose(new Quaternionf().rotateY((float) Math.toRadians(Y_ROTATION_DEGREES)));
        ms.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
        Minecraft.getInstance().getItemRenderer().renderStatic(compass, ItemDisplayContext.GROUND,
                light, overlay, ms, buffer, be.getLevel(), 0);
        ms.popPose();
    }
}
