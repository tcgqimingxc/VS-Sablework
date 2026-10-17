package org.valkyrienskies.clockwork.content.logistics.gas.tank.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.valkyrienskies.clockwork.content.logistics.gas.GasMix;
import org.valkyrienskies.clockwork.content.logistics.gas.GasPhysics;
import org.valkyrienskies.clockwork.content.logistics.gas.GasType;
import org.valkyrienskies.clockwork.content.logistics.gas.GasTypes;
import org.valkyrienskies.clockwork.content.logistics.gas.tank.DuctTankBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.tank.DuctTankPartials;

/**
 * 气体储罐渲染器：在罐体空腔里画一个"气面高度随内容物变化"的染色方块。
 *
 * <h2>参考的是 Create 的哪个文件</h2>
 * <p>模型与贴图全部来自 Create 6.0.10（见 {@code tools/gen_duct_tank_assets.py}）；
 * 罐体外观（4 个结构模型 + 连接纹理取格）见
 * {@link org.valkyrienskies.clockwork.content.logistics.gas.tank.client.DuctTankModel} 与
 * {@link org.valkyrienskies.clockwork.content.logistics.gas.tank.client.DuctTankCTBehaviour}。
 * 渲染这一层的对照物是 {@code com.simibubi.create.content.fluids.tank.FluidTankRenderer}
 * （已用 {@code javap -p -c} 核实，不是凭印象）：
 * <ul>
 *   <li>它 <b>是</b> 一个真正的 BER（{@code extends SafeBlockEntityRenderer<FluidTankBlockEntity>}），
 *       不是 Flywheel 实例化渲染；</li>
 *   <li>{@code renderSafe} 里先算液面比例，再用
 *       {@code NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(fluidStack, x1,y1,z1, x2,y2,z2, …)}
 *       画一个盒子，纵向按液面裁切；</li>
 *   <li>比例取自 {@code fluidInTank.getAmount() / tankCapability.getTankCapacity(0)}（内容物 / 容量）；</li>
 *   <li>浮力方向：液体的 {@code isLighterThanAir()} 为真时把液面自上而下量（倒挂）。</li>
 * </ul>
 *
 * <p>它用的几个几何常数（字节码里的 {@code 0.25f} / {@code 0.0625f} / {@code 0.0703125f}），
 * 本渲染器一一对上：
 * <pre>
 *   Create 的盒子 x1 = z1 = 0.0703 = 罐壁 1 像素 + 1/8 像素余量   → 本类 WALL + EDGE
 *   Create 的盒子 y1（平移后）= 0.25 = 底盖厚度 4 像素           → 本类 FLOOR
 *   Create 的盒子最高点 = height - 0.3125 = 顶盖内面再让 1 像素   → 本类 LID_GAP
 *   Create 传 renderBottom=false（不画底面，避免与底盖内面共面） → 本类抬高 EDGE 达到同一目的
 * </pre>
 * 也就是说：<b>罐壁 1 像素、底/顶盖 4 像素</b>，与 Create 的 {@code block_bottom/block_top}
 * 模型（Lid 元素 0..4 与 12..16）是同一套数字。
 *
 * <h2>本模组的差异（必读）</h2>
 * <ul>
 *   <li><b>画的是"气体"而不是流体</b>：本模组的气体没有 {@code Fluid}，因此无法调用
 *       {@code renderFluidBox}；改为缩放一个纯白局部模型（{@link DuctTankPartials#GAS_VOLUME}）
 *       并用 {@code SuperByteBuffer.color()} 染色。染色 = 各组分 {@link GasType#color()} 按质量加权，
 *       所以混合气会得到混合色、换气体不用换贴图。</li>
 *   <li><b>液面比例沿用 Create 的口径</b>{@code 质量 / 容量}（{@code gasCapacityKg()} 的含义就是
 *       "环境温度下 1 atm 的气量"）。代价：压力超过 1 atm 后高度就满了，多的气只体现在
 *       <b>不透明度</b>上（{@code OPAQUE_ATMOSPHERES} 个大气压时完全不透明）。
 *       这是刻意的：Create 的液面也只表达"装了多少"，本模组另用护目镜/比较器表达压力。</li>
 *   <li><b>不做沸腾/翻腾动画</b>：Create 的流体用流体贴图自带动画帧；气体是纯色块，
 *       靠不透明度与颜色表达，没有流动动画。</li>
 *   <li><b>不在罐壁上画"液面线"</b>：Create 的液面只在罐内可见（透过观察窗），这里一致。</li>
 * </ul>
 *
 * <h2>只在主方块画一次</h2>
 * <p>整罐的气体只有主方块那一份（见 {@link DuctTankBlockEntity} 的类注释），而且气体体积要跨越
 * {@code width × height} 整座罐子，所以这里只让 {@code isController()} 的那一块出图，
 * 与 {@code FluidTankRenderer.shouldRenderOffScreen() == true} 的理由相同：
 * 主方块本身可能不在视锥里，但罐子还在屏幕上。
 */
public class DuctTankRenderer extends SmartBlockEntityRenderer<DuctTankBlockEntity> {

    /** 罐壁厚度 = 1 像素（与 Create 的模型一致：四面墙都是 0..1 / 15..16）。 */
    private static final float WALL = 1.0f / 16.0f;
    /** 底盖 / 顶盖厚度 = 4 像素（Create 的 block_bottom/block_top 里 Lid 元素是 0..4 与 12..16）。 */
    private static final float LID = 0.25f;
    /** 0.125 像素的避让：不画与罐壁内面共面的那一层，免得 z-fighting（Create 用 renderBottom=false 达到同一目的）。 */
    private static final float EDGE = 1.0f / 128.0f;
    /** 灌满时气面离顶盖内面留 1 像素（Create 的最高液面同样停在 height-0.3125）。 */
    private static final float LID_GAP = 1.0f / 16.0f;
    /** 再小的气量也画 1 像素高，否则"罐里到底有没有气"在观察窗里看不出来。 */
    private static final float MIN_LEVEL = 1.0f / 16.0f;
    /** 不透明度区间：空罐最透、满罐最实。 */
    private static final float MIN_ALPHA = 70.0f;
    private static final float MAX_ALPHA = 215.0f;
    /** 达到该表压时气体完全不透明（1 atm ≈ 101325 Pa）。 */
    private static final double OPAQUE_ATMOSPHERES = 8.0;

    public DuctTankRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(DuctTankBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        if (be.getLevel() == null || !be.isController()) {
            return;
        }
        final GasMix mix = be.gasMix();
        if (mix.isEmpty()) {
            return;
        }
        final double capacity = be.gasCapacityKg();
        if (!(capacity > 0)) {
            return;
        }

        final double mass = mix.totalMass();
        final double atmospheres = mix.pressure(capacity) / GasPhysics.ambientPressure();

        // 各组分按质量加权：颜色 + 标准状态密度（密度用来判断"比空气轻"）
        double r = 0;
        double g = 0;
        double b = 0;
        double density = 0;
        for (GasType type : GasTypes.all()) {
            final double componentMass = mix.massOf(type);
            if (componentMass <= 0) {
                continue;
            }
            final int color = type.color();
            r += ((color >> 16) & 0xFF) * componentMass;
            g += ((color >> 8) & 0xFF) * componentMass;
            b += (color & 0xFF) * componentMass;
            density += type.density() * componentMass;
        }
        r /= mass;
        g /= mass;
        b /= mass;
        density /= mass;

        final float fill = (float) Mth.clamp(mass / capacity, 0.0, 1.0);
        final float opacity = (float) Mth.clamp(atmospheres / OPAQUE_ATMOSPHERES, 0.0, 1.0);
        final int alpha = (int) (MIN_ALPHA + (MAX_ALPHA - MIN_ALPHA) * opacity);

        // 罐体空腔（局部坐标，原点 = 主方块的最小角）：四面墙各让开 1 像素 + 0.125 像素避让，
        // 竖向从"底盖内面"到"顶盖内面再留 1 像素"，与 Create 的 renderFluidBox 完全同口径
        final float width = Math.max(1, be.getWidth());
        final float height = Math.max(1, be.getHeight());
        final float inset = WALL + EDGE;
        final float x1 = inset;
        final float z1 = inset;
        final float sx = width - 2.0f * inset;
        final float sz = sx;
        final float yMin = LID + EDGE;
        final float yMax = height - LID - LID_GAP;
        final float span = yMax - yMin;
        if (span <= 0) {
            return;
        }
        final float gasHeight = Math.max(MIN_LEVEL, fill * span);
        // 比空气轻的气体"浮"在罐顶（对应 Create 的 isLighterThanAir 倒挂液面）
        final float y0 = density < GasTypes.AIR.density() ? yMax - gasHeight : yMin;

        final BlockState state = be.getBlockState();
        // CachedBuffers.partial 返回缓存实例，变换是就地累加的 → 每次使用前 reset()
        // （与 GasEngineRenderer 同款写法）
        final SuperByteBuffer gas = CachedBuffers.partial(DuctTankPartials.GAS_VOLUME, state).reset();
        gas.color((int) r, (int) g, (int) b, alpha);
        // 罐内是密闭空腔，用世界光照会几乎全黑；这里固定全亮 + 关掉方向光，
        // 让"有多少气"透过观察窗一眼可辨（与 GasEngineRenderer 的内腔发光同款处理）
        gas.disableDiffuse();
        gas.light(LightTexture.FULL_BRIGHT);

        ms.pushPose();
        ms.translate(x1, y0, z1);
        ms.scale(sx, gasHeight, sz);
        gas.renderInto(ms, buffer.getBuffer(RenderType.translucent()));
        ms.popPose();
    }

    /**
     * 主方块要替整座罐子出图，所以渲染包围盒必须是整座罐子，否则罐子还在屏幕上、
     * 主方块那格刚出视锥就会被剔除，气体整块消失。
     */
    @Override
    public AABB getRenderBoundingBox(DuctTankBlockEntity be) {
        final BlockPos pos = be.getBlockPos();
        final int width = Math.max(1, be.getWidth());
        final int height = Math.max(1, be.getHeight());
        return new AABB(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + width, pos.getY() + height, pos.getZ() + width);
    }

    /** 与 Create 的 {@code FluidTankRenderer#shouldRenderOffScreen} 同理：主方块不在视锥里也要画。 */
    @Override
    public boolean shouldRenderOffScreen(DuctTankBlockEntity be) {
        return true;
    }
}
