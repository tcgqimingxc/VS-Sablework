package org.valkyrienskies.clockwork.content.kinetics.universal_shaft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.valkyrienskies.clockwork.ClockworkMod;
import org.valkyrienskies.clockwork.content.kinetics.universal_shaft.UniversalShaftBlockEntity;

/**
 * 通用轴的方块实体渲染器：只负责画两个接头之间的"软管"。
 *
 * <p>对应旧版 {@code content.kinetics.universal_shaft.UniversalShaftRenderer}。
 *
 * <p>与旧版的差异（重要）：
 * <ul>
 *   <li>旧版让 BER 连方块本体一起画（{@code ENTITYBLOCK_ANIMATED}）+ VS2 的船坐标换算；
 *       1.21.1 的方块本体交给普通模型渲染（见 {@code UniversalShaftBlock} 的说明），
 *       这里只画管子，因此不需要处理船的渲染变换。</li>
 *   <li>旧版用欧拉角（pitch/yaw/roll）拼姿态，这里直接用 JOML 的
 *       {@link Quaternionf#rotateTo(float, float, float, float, float, float)} 把 +Y 转到连接方向，
 *       少一堆三角函数，也不会有万向锁问题。</li>
 * </ul>
 */
public class UniversalShaftRenderer extends SafeBlockEntityRenderer<UniversalShaftBlockEntity> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, "textures/block/compressible_axis.png");
    /** 管子半宽（格）：旧版 6/16 像素宽再除以 sqrt(2)，保持一致的观感。 */
    private static final float RADIUS = 6f / 16f / 2f / (float) Math.sqrt(2);
    /** 纹理在轴向上每 15/16 格重复一次，与旧版一致。 */
    private static final float TEXTURE_PERIOD = 15f / 16f;

    public UniversalShaftRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(UniversalShaftBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        BlockPos other = be.getConnectedPos();
        Level level = be.getLevel();
        if (other == null || level == null || !be.isMain()) {
            return;
        }

        Vec3 from = Vec3.atCenterOf(be.getBlockPos());
        Vec3 to = Vec3.atCenterOf(other);
        Vec3 delta = to.subtract(from);
        double fullLength = delta.length();
        // 两端方块各占 0.5 格，管子只画中间露出来的部分（旧版从 +0.5 起画满整段，会插进对面方块里）
        float length = (float) (fullLength - 1.0);
        if (length <= 0.01f) {
            return;
        }

        VertexConsumer vb = buffer.getBuffer(RenderType.entitySolid(TEXTURE));
        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5);
        Vector3f dir = new Vector3f((float) delta.x, (float) delta.y, (float) delta.z).normalize();
        ms.mulPose(new Quaternionf().rotateTo(0f, 1f, 0f, dir.x, dir.y, dir.z));
        ms.translate(-0.5, -0.5, -0.5);
        // 变换后局部 +Y 指向另一端，从方块表面 (0.5) 开始铺管子
        ms.translate(0.5, 0.5, 0.5);

        int lightStart = lightAt(level, be.getBlockPos());
        int lightEnd = lightAt(level, other);
        float maxV = length / TEXTURE_PERIOD;
        // 四个侧面：分别朝 +X / -X / +Z / -Z
        quad(vb, ms, RADIUS, RADIUS, -RADIUS, -RADIUS, 1f, 0f, length, maxV, lightStart, lightEnd);
        quad(vb, ms, -RADIUS, -RADIUS, RADIUS, RADIUS, -1f, 0f, length, maxV, lightStart, lightEnd);
        quad(vb, ms, RADIUS, -RADIUS, RADIUS, -RADIUS, 0f, 1f, length, maxV, lightStart, lightEnd);
        quad(vb, ms, -RADIUS, RADIUS, -RADIUS, RADIUS, 0f, -1f, length, maxV, lightStart, lightEnd);

        ms.popPose();
    }

    /**
     * 画一根沿 +Y 的侧面四边形。
     *
     * @param x0 z0 起点侧的横向坐标；x1 z1 该侧面另一侧的横向坐标（决定法线朝向）
     */
    private static void quad(VertexConsumer vb, PoseStack ms, float x0, float z0, float x1, float z1,
                             float normalX, float normalZ, float length, float maxV, int lightStart, int lightEnd) {
        PoseStack.Pose pose = ms.last();
        vertex(vb, pose, x0, 0f, z0, normalX, 0f, normalZ, 1f, 0f, lightStart);
        vertex(vb, pose, x0, length, z0, normalX, 0f, normalZ, 1f, maxV, lightEnd);
        vertex(vb, pose, x1, length, z1, normalX, 0f, normalZ, 0f, maxV, lightEnd);
        vertex(vb, pose, x1, 0f, z1, normalX, 0f, normalZ, 0f, 0f, lightStart);
    }

    private static void vertex(VertexConsumer vb, PoseStack.Pose pose, float x, float y, float z,
                               float nx, float ny, float nz, float u, float v, int light) {
        vb.addVertex(pose, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    private static int lightAt(Level level, BlockPos pos) {
        return LightTexture.pack(level.getBrightness(LightLayer.BLOCK, pos), level.getBrightness(LightLayer.SKY, pos));
    }

    /** 让连接的整段管子都不被摄像机剔除。 */
    @Override
    public AABB getRenderBoundingBox(UniversalShaftBlockEntity be) {
        AABB box = new AABB(be.getBlockPos());
        BlockPos other = be.getConnectedPos();
        return other == null ? box : box.minmax(new AABB(other));
    }

    /** 旧版重写了 shouldRenderOffScreen / shouldRender 来强制渲染，1.21 用渲染包围盒 + 关闭视锥剔除即可。 */
    @Override
    public boolean shouldRenderOffScreen(UniversalShaftBlockEntity be) {
        return be.isLinked();
    }
}
