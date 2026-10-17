package org.valkyrienskies.clockwork.content.contraptions.sugar_rocket.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.clockwork.content.contraptions.sugar_rocket.SugarRocketBlockEntity;

/**
 * 糖火箭（{@code sugar_rocket}）的方块实体渲染器。
 *
 * <p>对应旧版 {@code content/propulsion/sugar_rocket/SugarRocketRenderer.kt}（35 行）：
 * 把整个方块模型再画一遍，燃烧时按进度由白渐变到黑（{@code Color.mixColors(0xFFFFFF, 0x000000, progress)}），
 * 并切到 {@code RenderType.cutout()} 让贴图的镂空部分透出来。
 *
 * <p><b>与旧版的差异</b>
 * <ul>
 *   <li>进度来源：旧版读服务端每刻发包驱动的 {@code clientBurnProgress}；
 *       这里读 {@link SugarRocketBlockEntity#getBurnProgress(float)}（同名的 LerpedFloat，
 *       但由客户端按点火时同步下来的总刻数自行推进，不再每刻发包）。</li>
 *   <li>旧版直接用 {@code CachedBuffers.block(...)} 的缓存实例（整个旧版只有这一处画它，
 *       所以不需要复位）；这里显式 {@code reset()}，避免同帧内被别的渲染器改过变换。</li>
 * </ul>
 *
 * <p>注意：本渲染器没有任何 partial 模型（画的是方块自身的烘焙模型），
 * 所以不需要登记到 {@code ContraptionPartials} / {@code CWClientSetup}。
 */
public class SugarRocketRenderer extends SmartBlockEntityRenderer<SugarRocketBlockEntity> {

    public SugarRocketRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(final SugarRocketBlockEntity be, final float partialTicks, final PoseStack ms,
                              final MultiBufferSource buffer, final int light, final int overlay) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);

        final BlockState state = be.getBlockState();
        final SuperByteBuffer model = CachedBuffers.block(state).reset();
        if (be.isBurning()) {
            final float progress = be.getBurnProgress(partialTicks);
            final int tint = Color.mixColors(0xFFFFFF, 0x000000, progress);
            model.light(light).color(tint).renderInto(ms, buffer.getBuffer(RenderType.cutout()));
        } else {
            model.light(light).renderInto(ms, buffer.getBuffer(RenderType.solid()));
        }
    }
}
