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
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.valkyrienskies.clockwork.content.logistics.gas.compressor.AirCompressorBlockEntity;

/**
 * 空气压缩机（{@code air_compressor}）的方块实体渲染器：输入轴自转 + 风箱充气/放气 + 顶板升降。
 *
 * <p>对应官方（1.20.1）{@code content/logistics/gas/generation/compressor/AirCompressorRenderer.kt}
 * （146 行）：常量、缓动、粒子与音效逐项照搬。三个 partial 的模型文件
 * （{@code compressor/axis}、{@code fabric}、{@code top}）与旧版逐字节相同（已比对哈希）。
 *
 * <h2>为什么这三块必须由 BER 画</h2>
 * <p>{@code blockstates/air_compressor.json} → {@code compressor/block} 只是底架
 * （11 个 element，最高点是上围栏顶面 y=13px）；风箱布（{@code fabric}，y=7..25px）、
 * 顶板（{@code top}，y=25..28px）、输入轴（{@code axis}，y=0..2px）不在任何 blockstate /
 * 物品模型里，只由本类画。物品模型 {@code compressor/item.json} 的 19 个 element
 * 正好是这四部分之和，是"世界里也应完整显示"的证据。
 *
 * <h2>与旧版的关键差异：压缩支点与调用顺序（"上半部分消失"的根因）</h2>
 * <p>旧版（以及本类的上一版）把风箱写成
 * {@code scale(1, 0.5 + e, 1); translate(0, (0.5 - e) / 2, 0)}。{@code PoseStack} 的变换是
 * <b>右乘</b>、后调用的先作用于顶点，所以这条链的真实映射是 {@code p' = s·(p + t)}
 * （平移量自己也被缩放）。e=0（未通电；存档里 Status=INACTIVE、Speed=0）时风箱顶从 25px
 * 掉到 14.5px，而底座上围栏顶面就在 13px，可见部分只剩 1.5px；顶板又按
 * {@code (e-0.5)*1.5 = -0.75 格（12px）}下移到 13..16px，与底座顶面齐平，
 * 16×16 的板把那条布整个罩住 —— 世界里就只剩底座。
 *
 * <p>现在把支点放在<b>底座上围栏顶面 y=13/16</b>：模型作者给风箱布预留的折叠线
 * （风箱底边 7px 本来就藏在围栏内），并让顶板严格跟随风箱顶边 y=25/16：
 * <pre>
 * s = 0.5 + e                                     // e∈[0,0.5] → s∈[0.5,1]
 * fabric: translate(0, PIVOT*(1-s), 0) → scale(1, s, 1)   // 先 translate 后 scale = 绕 PIVOT 缩放
 * top:    translate(0, (FABRIC_TOP - PIVOT)*(s-1), 0)     // 顶板底面始终贴住风箱顶
 * </pre>
 * 要得到 {@code p' = s·p + PIVOT·(1-s)}，必须<b>先 translate 再 scale</b>（{@code M = T·S}）；
 * 反过来 {@code M = S·T} 会得到 {@code p' = s·(p+t)}，支点变成 {@code s·t/(1-s)}，
 * 这正是旧版写错的方式。{@code tools/selftest_compressor_anim.py} 会断言调用顺序。
 *
 * <p>e=0 时风箱布占 ≈10..19px、顶板 19..22px，底座顶面以上仍有 9px 可见；e=0.5 时回到作者
 * 摆放的 25..28px。两种状态都不与底座实体（最高 13px）重叠。
 *
 * <h2>其它说明</h2>
 * <ul>
 *   <li>不调 {@code super.renderSafe}：父类的实现会走
 *       {@code KineticBlockEntityRenderer#renderSafe}，那里在 Flywheel 可视化开启时直接 return；
 *       底架由普通方块模型渲染，本类只画三个 partial。</li>
 *   <li>旧版读自己维护的 {@code LerpedFloat visualSpeed + angle}，1.21.1 的
 *       {@code KineticBlockEntity} 已无这两个字段；输入轴改用
 *       {@link #standardKineticRotationTransform}（内部 {@code getAngleForBe}，同一套换算）。
 *       该旋转绕"过方块中心的 Y 轴"，而输入轴模型的中心就在 x=z=8px 这条轴上，所以是原地自转。</li>
 *   <li>风箱动画状态放在 {@link AirCompressorBlockEntity} 上（旧版也是），
 *       因为一种 BE 只有一个渲染器实例，状态放渲染器会被别的压缩机覆盖。</li>
 *   <li>BER 裁剪盒必须向上扩到模型顶 y=28px：默认盒只有方块本身（0..1 格），
 *       镜头里只剩顶板时会被当作"方块不可见"提前裁掉。</li>
 * </ul>
 */
public class AirCompressorRenderer extends KineticBlockEntityRenderer<AirCompressorBlockEntity> {

    /** 转速 → 每秒充气量；官方 {@code SPEED_TO_SIZE_FACTOR}。 */
    private static final float SPEED_TO_INFLATE = 1f / 1500f;
    /** 风箱完全瘪时的 Y 缩放；官方 {@code BASE_FABRIC_SCALE_Y}。 */
    private static final float FABRIC_BASE_SCALE_Y = 0.5f;

    /**
     * 风箱压缩支点（方块坐标）：底座上围栏顶面 y=13px = 0.8125。
     *
     * <p>为什么不是 y=0 或 y=0.5：模型里风箱布底边在 7px、顶边在 25px，7..13px 这一段本来就
     * 藏在底座围栏里（围栏 6..13px）。绕围栏顶面缩放时，风箱底边只会从 7px 升到 10px（始终
     * 被围栏挡住，不会露出缝隙），而风箱顶边 25→19px 始终高出围栏 6px —— 未通电时也看得见。
     */
    private static final float FABRIC_PIVOT_Y = 0.8125f;
    /** 风箱布模型顶边 y=25px = 1.5625；顶板底面必须贴住这条线。 */
    private static final float FABRIC_TOP_Y = 1.5625f;
    /** 模型最高点：顶板上表面 y=28px = 1.75，用于 BER 裁剪盒。 */
    private static final float MODEL_TOP_Y = 1.75f;

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

        // ---- 输入轴：绕"过方块中心的 Y 轴"自转（轴模型 x=z=8px，正好在这条轴上）----
        SuperByteBuffer axis = CachedBuffers.partial(CompressorPartials.AXIS, state).reset();
        standardKineticRotationTransform(axis, be, lightBelow).renderInto(ms, vb);

        // ---- 风箱动画 ----
        advanceInflate(be, partialTicks);
        maybeBurst(be, level);

        // 夹紧后再缓动：easeInOutQuad 只在 [0,1] 上有定义，负数会走"对称"分支返回正数、
        // NaN 会把 scale 变成 NaN，后者会让整块风箱/顶板的顶点全部失效（看起来就是"消失"）。
        // Mth.clamp 本身不处理 NaN（比较全为 false），所以先单独挡一次；±Inf 由 clamp 处理。
        float inflate = be.getClientInflate();
        if (Float.isNaN(inflate)) {
            inflate = 0f;
        }
        inflate = Mth.clamp(inflate, 0f, 1f);
        // easedHalf ∈ [0, 0.5]：0 = 完全瘪，0.5 = 完全鼓
        float easedHalf = easeInOutQuad(inflate) / 2f;
        // s ∈ [0.5, 1]：风箱布的 Y 缩放。0.5 = 完全瘪（作者给的 BASE_FABRIC_SCALE_Y）
        float fabricScaleY = FABRIC_BASE_SCALE_Y + easedHalf;

        SuperByteBuffer fabric = CachedBuffers.partial(CompressorPartials.FABRIC, state).reset();
        // 顺序（关键）：PoseStack 的每次 translate/scale 都是**右乘**（M ← M·X），顶点按 M·p
        // 变换，所以最后调用的变换最先作用到顶点。先 translate 再 scale ⇒ M = T·S ⇒
        // p' = s·p + t，正好是"缩放后再平移"；取 t = PIVOT·(1-s) 就是绕 PIVOT 缩放。
        // 反过来（先 scale 再 translate）会得到 p' = s·(p+t)，支点变成 s·t/(1-s)，不是 PIVOT。
        fabric.translate(0f, FABRIC_PIVOT_Y * (1f - fabricScaleY), 0f);
        fabric.scale(1f, fabricScaleY, 1f);
        fabric.light(lightAbove).renderInto(ms, vb);

        SuperByteBuffer top = CachedBuffers.partial(CompressorPartials.TOP, state).reset();
        // 顶板底面 = 风箱顶边：Δi = (FABRIC_TOP_Y - PIVOT) * (s - 1)。
        // 旧版用 (easedHalf - 0.5) * 1.5，比实际下移量多 3.5px，会把顶板压进风箱里。
        top.translate(0f, (FABRIC_TOP_Y - FABRIC_PIVOT_Y) * (fabricScaleY - 1f), 0f);
        top.light(lightAbove).renderInto(ms, vb);
    }

    /**
     * 方块实体裁剪盒：默认只有 0..1 格，而顶板上表面到 y=28px（1.75 格）。
     *
     * <p>不扩这一格的话，当摄像机只把"方块上方那截"放进视锥时，整台压缩机都会被剔除。
     * 水平方向模型正好是 0..16px，不需要扩；留 0.01 的余量避免边界浮点误差。
     */
    @Override
    public AABB getRenderBoundingBox(AirCompressorBlockEntity be) {
        return new AABB(be.getBlockPos())
                .expandTowards(0, MODEL_TOP_Y - 1f, 0)
                .inflate(0.01);
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
        float speed = be.getSpeed();
        float step = Float.isFinite(speed) ? Math.abs(speed) * SPEED_TO_INFLATE : 0f;
        float delta = partialTicks * step * (inflating ? 1f : -1f);
        // 转速若来自异常网络可能为 NaN；NaN 一旦进入 clientInflate 就再也夹不回来
        // （NaN 与任何数比较都是 false），会把风箱/顶板的顶点污染成 NaN（整块消失）。
        if (!Float.isFinite(delta)) {
            return;
        }
        be.setClientInflate(be.getClientInflate() + delta);
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
