package org.valkyrienskies.clockwork.content.logistics.gas.engine.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.clockwork.content.logistics.gas.engine.GasEngineBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.engine.GasEngineLogic;
import org.valkyrienskies.clockwork.content.logistics.gas.engine.GasEnginePartials;

/**
 * 燃气机渲染器：气体越热，内腔发光越亮。
 *
 * <p><b>旧版没有对应类</b>（旧版燃气机的表现完全交给它驱动的 Create 蒸汽机）。本移植补一个最小可用的
 * 发光层，理由是本方块的"在不在工作"光靠护目镜看不出来，玩家缺少即时反馈。
 *
 * <p>驱动量取自<b>客户端已同步</b>的气体温度（{@code GasNodeState} 会把温度写给客户端），
 * 并用 {@link GasEngineLogic#temperatureEfficiency(double)} 换算成 0..1 —— 与护目镜上
 * "Engine Output" 里的温度分量同一条公式，因此发光强度和面板读数永远一致。
 * 效率里的流量分量没有同步（服务端瞬时量），这里不参与。
 */
public class GasEngineRenderer extends SmartBlockEntityRenderer<GasEngineBlockEntity> {

    /** 脉动角速度 [/刻]，约 4 秒一个周期。 */
    private static final float PULSE_SPEED = 0.025f;
    /** 脉动亮度区间：避免在 0 附近闪烁。 */
    private static final float PULSE_MIN = 0.6f;

    public GasEngineRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(GasEngineBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        if (be.getLevel() == null) {
            return;
        }
        // 温度分量 0..1；常温气体为 0 → 不发光（"冷机不亮"是刻意的）
        final float heat = GasEngineLogic.temperatureEfficiency(be.gasMix().temperature());
        if (heat < 0.02f) {
            return;
        }
        final float phase = (be.getLevel().getGameTime() + partialTicks) * PULSE_SPEED;
        final int color = (int) (255.0f * heat * (PULSE_MIN + (1.0f - PULSE_MIN) * Mth.sin(phase)));

        final BlockState state = be.getBlockState();
        // CachedBuffers.partial 返回缓存实例，变换是就地累加的 → 每次使用前 reset()（见 GasCrafterRenderer 的说明）
        final SuperByteBuffer glow = CachedBuffers.partial(GasEnginePartials.GLOW, state).reset();
        glow.color(color, color, color, 255);
        glow.disableDiffuse();
        // 内腔：全亮度 + 半透明，隔着框架的缝隙透出来
        glow.light(LightTexture.FULL_BRIGHT)
                .renderInto(ms, buffer.getBuffer(RenderType.translucent()));
    }
}
