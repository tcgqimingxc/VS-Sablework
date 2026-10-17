package org.valkyrienskies.clockwork.content.curiosities.altmeter.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import org.valkyrienskies.clockwork.content.curiosities.altmeter.AltMeterBlockEntity;
import org.valkyrienskies.clockwork.content.curiosities.altmeter.AltMeterPartials;

/**
 * 高度计（{@code alt_meter}）的方块实体渲染器：把表盘上的红石指示条按信号强度染色。
 *
 * <p>对应官方 / 旧版 {@code content/curiosities/altmeter/AltMeterRenderer.kt}
 * （{@code ClockworkBlockEntities.kt:314} 的 {@code .renderer { AltMeterRenderer(...) }}）。
 *
 * <p>为什么要 BER：{@code models/block/alt_meter/redstone.json} 是独立子模型，
 * blockstate 只引用 {@code block/alt_meter/block}（表壳），指示条<b>只能</b>由这里画；
 * 缺了它高度计就是一个没有读数条的灰盒子。
 *
 * <p>与旧版的差异：旧版 {@code Color.mixColors(0x555555, 0xFFFFFF, signal / 15f)} 逐字照抄，
 * 唯一改动是把 {@code getSignalStrength()} 从旧版的字段读法换成 1.21 版的公开访问器
 * （{@code AltMeterBlockEntity.java:getSignalStrength}）。
 */
public class AltMeterRenderer extends SmartBlockEntityRenderer<AltMeterBlockEntity> {

    /** 指示条两端颜色（旧版 {@code Color.mixColors(0x555555, 0xFFFFFF, ...)}）。 */
    private static final int COLOR_OFF = 0x555555;
    private static final int COLOR_ON = 0xFFFFFF;
    /** 红石强度满值：Create/Minecraft 的红石信号范围是 0..15。 */
    private static final float MAX_SIGNAL = 15f;

    public AltMeterRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(AltMeterBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        // 父类只画过滤/频率的悬浮提示（本方块没有），照调保持与 Create 方块一致的行为
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);

        CachedBuffers.partial(AltMeterPartials.REDSTONE, be.getBlockState())
                .light(light)
                .overlay(overlay)
                .color(Color.mixColors(COLOR_OFF, COLOR_ON, be.getSignalStrength() / MAX_SIGNAL))
                .renderInto(ms, buffer.getBuffer(RenderType.cutout()));
    }
}
