package org.valkyrienskies.clockwork.content.curiosities.solver.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import net.minecraft.Util;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.clockwork.ClockworkMod;
import org.valkyrienskies.clockwork.config.ClockworkConfig;
import org.valkyrienskies.clockwork.content.curiosities.solver.SolverBlock;
import org.valkyrienskies.clockwork.content.curiosities.solver.SolverBlockEntity;

/**
 * 解算器（{@code solver}）的光束渲染器。
 *
 * <p>对应旧版 {@code content/curiosities/solver/SolverRenderer.kt}（{@code ClockworkBlockEntities.kt}
 * 的 {@code .renderer { SolverRenderer(...) }}）与 {@code util/beam/BeamRenderer.kt}：
 * 旧版用自研 {@code ClockworkRenderTypes.BEAM}（{@code POSITION_COLOR_TEX_LIGHTMAP} +
 * 自制 {@code POSITION_COLOR_TEX_LIGHTMAP_SHADER}）画 4 条滚动贴图的带子，并在命中点画 14 条迸射。
 *
 * <h2>为什么不照搬旧 render type</h2>
 * <p>那条 render type 的关键是 {@code ClockworkRenderTypes.kt:...} 里自注册的
 * {@code ClockworkShaders.beam}（{@code POSITION_COLOR_TEX_LIGHTMAP_SHADER} 的自研实例）。
 * 1.21.1 的 shader 管线由 Veil 接管（{@code ShaderStateShard} 要注册到 Veil 的 shader 表），
 * 旧字节码里的构造方式在目标版本没有等价物；一旦 shader 没注册，{@code RenderType.create}
 * 只会在首次绘制时抛 {@code IllegalStateException}（"Could not create shader"），属于进世界才炸的坑。
 * 因此这里改成<b>原版现成的 render type</b>，只保留"可见光束"这个效果本身。
 *
 * <h2>具体选型（javap 核实，不猜）</h2>
 * <ul>
 *   <li>{@code RenderType.entityTranslucentEmissive(tex)}：{@code NEW_ENTITY} 顶点格式、
 *       {@code TRANSLUCENT_TRANSPARENCY}、{@code NO_CULL}、{@code COLOR_WRITE}、emissive shader →
 *       全亮度、双面、半透明，正好是光束需要的组合（1.21.1 {@code RenderType.lambda$static$8}）。</li>
 *   <li><b>没有</b>用 {@code RenderType.lightning()}：javap 显示它绑定了
 *       {@code WEATHER_TARGET}（{@code RenderType.<clinit>} 1041-1089），在方块实体渲染阶段画会写进
 *       天气帧缓冲，是否显示取决于天气通道的合成顺序，风险高于收益。</li>
 *   <li><b>Create 6 也没有可直接复用的光束 render type</b>：全 jar 只有
 *       {@code ZapperRenderHandler$LaserBeam}，实际绘制走
 *       {@code ShootableGadgetRenderHandler} 的立即模式（{@code RenderSystem.setShaderTexture}），
 *       不是 {@code RenderType}，无法在 BER 里复用（javap 核实）。</li>
 * </ul>
 *
 * <h2>与旧版的差异</h2>
 * <ul>
 *   <li>不做相机朝向的 4 条 ribbon 重算（旧版 {@code BeamRenderer.renderBeamTube} 每帧构造 4 个
 *       垂直基）；这里在光束轴的垂直平面里固定取 4 个 45° 方向，几何仍是 4 条互相交叉的带子。
 *       视觉效果等价（任何角度至少有一条带子不侧对相机），且每帧零对象分配。</li>
 *   <li>命中点迸射（旧版 {@code renderImpactBurst}）没有移植：本轮验收只要求"看得见光束"；
 *       光束长度已经随命中点缩短，落点就是 {@code start + facing × distance}，后续要补只需在
 *       {@link #renderSafe} 末尾按这个点画同样的带子。</li>
 *   <li>坐标换算：Sable 的方块实体渲染器在"子空间 plot 坐标"里调用（已 javap 核实
 *       {@code VanillaSubLevelBlockEntityRenderer.renderSingleBE} 只把 PoseStack 平移到方块角），
 *       所以这里直接用方块本地的 0..1 坐标；旧版 VS2 需要的 world→render-local 转换不再需要。</li>
 * </ul>
 *
 * <h2>状态来源与性能</h2>
 * <p>光束长度由服务端 {@link SolverBlockEntity#getBeamDistance()} 同步（见该类的类注释），
 * 渲染器每帧只读一个 double；纹理滚动用 {@link Util#getMillis()} 的 60 秒循环（60 × 4 = 240
 * 个整贴图，回绕处 UV 恰好落在整数上，看不出跳变），不建对象、不每帧申请 buffer。
 * 颜色与宽度来自 {@code CWServer.solverBeamColor / solverBeamWidth}。
 */
public class SolverRenderer extends SmartBlockEntityRenderer<SolverBlockEntity> {

    /** 旧版 {@code SolverRenderer.kt:69} 的 {@code ClockworkRenderTypes.BEAM} 贴图，路径逐字相同。 */
    private static final ResourceLocation BEAM_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, "textures/effects/beam.png");

    /** 静态创建一次：{@code RenderType.entityTranslucentEmissive} 内部是 memoize，这里再省一次查表。 */
    private static final RenderType BEAM_TYPE = RenderType.entityTranslucentEmissive(BEAM_TEXTURE);

    /** 旧版 {@code SolverRenderer.kt:46-48}：起点 = 方块中心 + 朝向 × 1.5，射程 64（常量在 BE 里）。 */
    private static final float BEAM_ORIGIN_OFFSET = (float) SolverBlockEntity.BEAM_ORIGIN_OFFSET;

    /** 一条贴图沿光束方向覆盖的世界长度 [格]（旧版 {@code tileWorldUnits = 1f}）。 */
    private static final float TILE_WORLD_UNITS = 1.0f;
    /** 滚动速度 [贴图/秒]（旧版 {@code scrollSpeed = -4f}，负号表示向发射端回卷）。 */
    private static final float SCROLL_TILES_PER_SECOND = 4.0f;
    /** 贴图滚动的周期 [ms]：60 s × 4 = 240 个整贴图，回绕无缝。 */
    private static final long SCROLL_LOOP_MS = 60_000L;

    /** 外壳透明度：贴图本身不透明，靠顶点 alpha 做出半透明光束。 */
    private static final float SHELL_ALPHA = 0.45f;
    /** 内芯透明度：同贴图缩窄后叠一层，形成"亮芯 + 柔边"。 */
    private static final float CORE_ALPHA = 0.85f;
    /** 内芯相对配置宽度的比例。 */
    private static final float CORE_WIDTH_SCALE = 0.34f;

    /** 4 条带子绕光束轴的角度（0°/45°/90°/135°）的 cos / sin，预计算避免每帧三角函数。 */
    private static final float SQRT_HALF = 0.70710677f;
    private static final float[] RIBBON_COS = {1.0f, SQRT_HALF, 0.0f, -SQRT_HALF};
    private static final float[] RIBBON_SIN = {0.0f, SQRT_HALF, 1.0f, SQRT_HALF};

    public SolverRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    /** 光束可以伸到 64 格外，不能按方块本身的视锥裁剪掉。 */
    @Override
    public boolean shouldRenderOffScreen(SolverBlockEntity be) {
        return true;
    }

    @Override
    public boolean shouldRender(SolverBlockEntity be, Vec3 cameraPos) {
        return true;
    }

    @Override
    protected void renderSafe(SolverBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        if (be.getLevel() == null) {
            return;
        }

        final Direction facing = be.getBlockState().getValue(SolverBlock.FACING);
        final int dx = facing.getStepX();
        final int dy = facing.getStepY();
        final int dz = facing.getStepZ();

        // 光束轴的两个垂直基（axis-aligned），再在它们之间取 45° 得到 4 条带子。
        final float ux;
        final float uy;
        final float uz;
        final float vx;
        final float vy;
        final float vz;
        if (dx != 0) {
            ux = 0.0f; uy = 1.0f; uz = 0.0f;
            vx = 0.0f; vy = 0.0f; vz = 1.0f;
        } else if (dy != 0) {
            ux = 1.0f; uy = 0.0f; uz = 0.0f;
            vx = 0.0f; vy = 0.0f; vz = 1.0f;
        } else {
            ux = 1.0f; uy = 0.0f; uz = 0.0f;
            vx = 0.0f; vy = 1.0f; vz = 0.0f;
        }

        final float startX = 0.5f + dx * BEAM_ORIGIN_OFFSET;
        final float startY = 0.5f + dy * BEAM_ORIGIN_OFFSET;
        final float startZ = 0.5f + dz * BEAM_ORIGIN_OFFSET;
        final double distance = Math.min(be.getBeamDistance(), SolverBlockEntity.BEAM_LENGTH);
        if (distance <= 1.0E-4) {
            return;
        }
        final float endX = startX + dx * (float) distance;
        final float endY = startY + dy * (float) distance;
        final float endZ = startZ + dz * (float) distance;

        final int rgb = ClockworkConfig.server().solverBeamColor.get();
        final float red = ((rgb >> 16) & 0xFF) / 255.0f;
        final float green = ((rgb >> 8) & 0xFF) / 255.0f;
        final float blue = (rgb & 0xFF) / 255.0f;

        final float width = Math.max(0.01f, ClockworkConfig.server().solverBeamWidth.getF());
        final float shellHalf = width * 0.5f;
        final float coreHalf = shellHalf * CORE_WIDTH_SCALE;

        final float scroll = -SCROLL_TILES_PER_SECOND
                * (Util.getMillis() % SCROLL_LOOP_MS) / 1000.0f;
        final float vEnd = scroll + (float) distance / TILE_WORLD_UNITS;

        final VertexConsumer vc = buffer.getBuffer(BEAM_TYPE);
        final PoseStack.Pose pose = ms.last();

        for (int i = 0; i < RIBBON_COS.length; i++) {
            final float rightX = ux * RIBBON_COS[i] + vx * RIBBON_SIN[i];
            final float rightY = uy * RIBBON_COS[i] + vy * RIBBON_SIN[i];
            final float rightZ = uz * RIBBON_COS[i] + vz * RIBBON_SIN[i];
            // normal = facing × right（长度 1，right 与 facing 正交）
            final float nx = dy * rightZ - dz * rightY;
            final float ny = dz * rightX - dx * rightZ;
            final float nz = dx * rightY - dy * rightX;

            drawRibbon(vc, pose, startX, startY, startZ, endX, endY, endZ,
                    rightX, rightY, rightZ, nx, ny, nz,
                    shellHalf, red, green, blue, SHELL_ALPHA, scroll, vEnd);
            drawRibbon(vc, pose, startX, startY, startZ, endX, endY, endZ,
                    rightX, rightY, rightZ, nx, ny, nz,
                    coreHalf, red, green, blue, CORE_ALPHA, scroll, vEnd);
        }
    }

    /**
     * 画一条从 start 到 end、沿 right 方向展开 width 的带子。
     *
     * <p>顶点顺序：起点左 → 终点左 → 终点右 → 起点右。render type 是 {@code NO_CULL}，
     * 所以从哪一侧看都可见，不需要按相机翻面。
     */
    private static void drawRibbon(VertexConsumer vc, PoseStack.Pose pose,
                                   float sx, float sy, float sz, float ex, float ey, float ez,
                                   float rx, float ry, float rz, float nx, float ny, float nz,
                                   float halfWidth, float red, float green, float blue, float alpha,
                                   float vStart, float vEnd) {
        final float ox = rx * halfWidth;
        final float oy = ry * halfWidth;
        final float oz = rz * halfWidth;

        vertex(vc, pose, sx - ox, sy - oy, sz - oz, nx, ny, nz, 0.0f, vStart, red, green, blue, alpha);
        vertex(vc, pose, ex - ox, ey - oy, ez - oz, nx, ny, nz, 0.0f, vEnd, red, green, blue, alpha);
        vertex(vc, pose, ex + ox, ey + oy, ez + oz, nx, ny, nz, 1.0f, vEnd, red, green, blue, alpha);
        vertex(vc, pose, sx + ox, sy + oy, sz + oz, nx, ny, nz, 1.0f, vStart, red, green, blue, alpha);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose,
                               float x, float y, float z, float nx, float ny, float nz,
                               float u, float v, float red, float green, float blue, float alpha) {
        vc.addVertex(pose, x, y, z)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(0)
                .setUv2(0xF000F0, 0xF000F0)
                .setNormal(pose, nx, ny, nz);
    }
}
