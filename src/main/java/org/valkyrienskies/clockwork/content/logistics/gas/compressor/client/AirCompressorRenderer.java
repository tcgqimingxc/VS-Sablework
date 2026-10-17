package org.valkyrienskies.clockwork.content.logistics.gas.compressor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.particle.AirParticleData;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.clockwork.content.logistics.gas.compressor.AirCompressorBlockEntity;

/**
 * 空气压缩机（{@code air_compressor}）的方块实体渲染器：输入轴自转 + 风箱充气/放气 + 顶板升降。
 *
 * <p>对应官方（1.20.1）{@code content/logistics/gas/generation/compressor/AirCompressorRenderer.kt}（146 行），
 * 常量、缓动、粒子与音效逻辑逐项照搬。
 *
 * <h2>为什么必须有它（实机"只有灰色底板"的根因）</h2>
 * <p>{@code blockstates/air_compressor.json} → {@code compressor/block}，那只是底架（11 个 element）；
 * 官方把所有会动/超高的部件拆成了三个独立模型：
 * {@code compressor/axis}（输入轴扇叶）、{@code compressor/fabric}（风箱布）、{@code compressor/top}（顶板，
 * 位于 y=25..28，高出方块）。{@code models/block/compressor/item.json} 的 19 个 element
 * = 底架 11 + fabric 2 + top 1 + axis 5，正好说明这三块是"世界里也该看得见"的部件，
 * 只是它们不走 blockstate、必须由 BER 画。本类之前不存在 → 世界里只剩底架。
 *
 * <h2>与官方的差异</h2>
 * <ul>
 *   <li>不调 {@code super.renderSafe}：父类会把"渲染用方块状态"（默认就是本方块模型，即底架）
 *       再按动力学角度画一遍，导致底架被重复绘制且随转速乱转。官方同样没有调 super
 *       ——底架由普通方块渲染负责。</li>
 *   <li>旧版读自己维护的 {@code LerpedFloat visualSpeed + angle}（Create 0.5 的字段），
 *       1.21.1 的 {@code KineticBlockEntity} 已无这两个字段，轴改用父类的
 *       {@link #standardKineticRotationTransform}（内部 {@code getAngleForBe}，同一套换算）。</li>
 *   <li>风箱动画状态放在 {@link AirCompressorBlockEntity} 上（旧版也是），
 *       因为一种 BE 只有一个渲染器实例，状态放渲染器会被别的压缩机覆盖。</li>
 * </ul>
 */
public class AirCompressorRenderer extends KineticBlockEntityRenderer<AirCompressorBlockEntity> {

    /** 转速 → 每秒充气量；官方 {@code SPEED_TO_SIZE_FACTOR}。 */
    private static final float SPEED_TO_INFLATE = 1f / 1500f;
    /** 风箱完全瘪时的 Y 缩放；官方 {@code BASE_FABRIC_SCALE_Y}。 */
    private static final float FABRIC_BASE_SCALE_Y = 0.5f;
    /** 顶板行程倍率；官方 {@code TOP_TRANSLATION_FACTOR}。 */
    private static final float TOP_TRAVEL_FACTOR = 1.5f;

    /** 充饱时喷出的空气粒子数；官方 {@code PARTICLE_BURST_COUNT}。 */
    private static final int BURST_COUNT = 36;
    private static final float BURST_Y_OFFSET = 1.6f;
    private static final double BURST_VERTICAL_SPEED = 0.05;
    private static final float BURST_HORIZONTAL_SPREAD = 0.5f;
    /** Create 空气粒子的参数（官方 {@code PARTICLE_AIR_DATA_SCALE/DRAG}）。 */
    private static final float AIR_PARTICLE_SCALE = 0.0f;
    private static final float AIR_PARTICLE_DRAG = 0.005f;

    public AirCompressorRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(AirCompressorBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }

        BlockPos pos = be.getBlockPos();
        // 官方按"脚下/头顶"的亮度分别打光：轴在底板下方，风箱与顶板在方块上方
        int lightBelow = LevelRenderer.getLightColor(level, pos.below());
        int lightAbove = LevelRenderer.getLightColor(level, pos.above());
        BlockState state = be.getBlockState();
        VertexConsumer vb = buffer.getBuffer(RenderType.cutoutMipped());

        // ---- 输入轴：绕方块 Y 轴自转（AirCompressorBlock#getRotationAxis = Y，轴在底面）----
        SuperByteBuffer axis = CachedBuffers.partial(CompressorPartials.AXIS, state).reset();
        standardKineticRotationTransform(axis, be, lightBelow).renderInto(ms, vb);

        // ---- 风箱动画 ----
        advanceInflate(be, partialTicks);
        maybeBurst(be, level);

        // easedHalf ∈ [0, 0.5]：0 = 完全瘪，0.5 = 完全鼓
        float easedHalf = easeInOutQuad(be.getClientInflate()) / 2f;

        SuperByteBuffer fabric = CachedBuffers.partial(CompressorPartials.FABRIC, state).reset();
        fabric.scale(1f, FABRIC_BASE_SCALE_Y + easedHalf, 1f);
        // 缩放以方块底面为原点（y=0），所以要把风箱底边补回原来的高度
        fabric.translate(0, (FABRIC_BASE_SCALE_Y - easedHalf) / 2.0, 0);
        fabric.light(lightAbove).renderInto(ms, vb);

        SuperByteBuffer top = CachedBuffers.partial(CompressorPartials.TOP, state).reset();
        top.translate(0, (easedHalf - FABRIC_BASE_SCALE_Y) * TOP_TRAVEL_FACTOR, 0);
        top.light(lightAbove).renderInto(ms, vb);
    }

    /**
     * 推进风箱充气进度（官方 {@code updateClientSize}）。
     *
     * <p>有转速且状态为 ACTIVE 时鼓起来；一旦"充饱过"或停机就瘪下去。
     * 暂停游戏时不推进，避免暂停期间动画继续跑。
     */
    private static void advanceInflate(AirCompressorBlockEntity be, float partialTicks) {
        if (Minecraft.getInstance().isPaused()) {
            return;
        }
        boolean inflating = !be.hasClientBurst()
                && be.status() == AirCompressorBlockEntity.CompressorStatus.ACTIVE;
        // 官方：clientSize += partialTicks * |speed| * sizeMultiplier * 1/1500
        float step = Math.abs(be.getSpeed()) * SPEED_TO_INFLATE;
        be.setClientInflate(be.getClientInflate() + partialTicks * step * (inflating ? 1f : -1f));
    }

    /**
     * 充饱时喷粒子 + 放气音效，瘪到底时复位（官方 {@code handleParticleSpawning}）。
     *
     * <p>粒子与音效都只在客户端做：官方也是纯客户端行为，服务端不参与。
     */
    private static void maybeBurst(AirCompressorBlockEntity be, Level level) {
        float inflate = be.getClientInflate();
        if (inflate >= 1f && !be.hasClientBurst()) {
            be.setClientBurst(true);
            spawnBurst(be.getBlockPos(), level);
            float pitch = Math.abs(be.getSpeed()) / 256f + level.random.nextFloat() + 0.25f;
            AllSoundEvents.STEAM.playAt(level, be.getBlockPos().getCenter(), 0.4f, pitch, true);
        }
        if (inflate <= 0f) {
            be.setClientBurst(false);
        }
    }

    /** 在方块上方喷一圈空气粒子（官方 {@code spawnAirBurstParticles}）。 */
    private static void spawnBurst(BlockPos pos, Level level) {
        double xBase = pos.getX() + 0.5;
        double yBase = pos.getY() + BURST_Y_OFFSET;
        double zBase = pos.getZ() + 0.5;
        for (int i = 0; i < BURST_COUNT; i++) {
            double rX = (level.random.nextFloat() - 0.5f) * 2 * BURST_HORIZONTAL_SPREAD;
            double rY = level.random.nextFloat();
            double rZ = (level.random.nextFloat() - 0.5f) * 2 * BURST_HORIZONTAL_SPREAD;
            level.addParticle(new AirParticleData(AIR_PARTICLE_SCALE, AIR_PARTICLE_DRAG),
                    xBase + rX, yBase + rY, zBase + rZ,
                    0, BURST_VERTICAL_SPEED, 0);
        }
    }

    /** 官方 {@code EaseHelper.easeInOutQuad}：两端慢、中间快。 */
    private static float easeInOutQuad(float x) {
        return x < 0.5f ? 2f * x * x : 1f - (float) Math.pow(-2f * x + 2f, 2) / 2f;
    }
}
