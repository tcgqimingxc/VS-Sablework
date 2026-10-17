package org.valkyrienskies.clockwork.content.logistics.gas.crafter.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.clockwork.content.logistics.gas.crafter.GasCrafterBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.crafter.GasCrafterPartials;

/**
 * 气体合成器渲染器：外壳 + 会滑动的滤网 + 指示灯发光。
 *
 * <p>对应旧版 {@code crafter/GasCrafterBlockEntityRenderer.kt}。
 *
 * <p><b>与旧版的差异</b>
 * <ul>
 *   <li>旧版的发光值来自 {@code GlowBehaviour}（{@code LerpedFloat}，随合成进度推进）；
 *       本移植没有 behaviour，改为"合成中 → 按时间做余弦脉动"，静止时不亮（视觉语义相同）。</li>
 *   <li>旧版合成中会给 frame/glow/tube 叠一个随机抖动（{@code Random.nextFloat()}）；
 *       这里去掉——每帧随机抖动在 1.21 的渲染线程上是明显的不稳定抖动，收益低于噪声。</li>
 *   <li>朝向对齐不再手写 {@code translate/rotateToFace/translateBack} 三连，
 *       而是与旧版<b>逐字相同</b>的同一组调用（{@link SuperByteBuffer#rotateToFace}），
 *       只是抽成了一个方法；{@code SuperByteBuffer} 与旧版同一个库（Flywheel 1.0）。</li>
 *   <li>{@code isRunning()} 需要服务端同步（见 {@code GasCrafterBlockEntity} 里的 {@code sendData()}），
 *       否则客户端永远渲染成静止状态。</li>
 * </ul>
 */
public class GasCrafterRenderer extends SmartBlockEntityRenderer<GasCrafterBlockEntity> {

    /** 滤网沿朝向滑出的最大距离 [格]（旧版常量 0.3）。 */
    private static final float MESH_TRAVEL = 0.3f;
    /** 动画角速度：旧版 {@code cos(clientProcessingTicks * 0.1)}。 */
    private static final float ANIM_SPEED = 0.1f;
    /** 指示灯最大亮度（旧版 {@code color = 200 * glow}）。 */
    private static final int GLOW_MAX = 200;

    public GasCrafterRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(GasCrafterBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        final BlockState state = be.getBlockState();
        final Direction facing = state.getValue(BlockStateProperties.FACING);
        final VertexConsumer cutout = buffer.getBuffer(RenderType.cutout());
        final VertexConsumer translucent = buffer.getBuffer(RenderType.translucent());

        final boolean running = be.isRunning();
        // 用世界时间驱动动画：静止时相位固定为 0，与旧版 clientProcessingTicks 归零后的姿态一致
        final float phase = running && be.getLevel() != null
                ? (be.getLevel().getGameTime() + partialTicks) * ANIM_SPEED
                : 0.0f;

        // 指示灯：0..GLOW_MAX 的余弦脉动（旧版由 GlowBehaviour 的插值驱动，同样在 0..200 之间）
        final int glow = (int) (GLOW_MAX * (0.5f - 0.5f * Mth.cos(phase)));
        final SuperByteBuffer lamp = pristine(GasCrafterPartials.GLOW, state);
        lamp.color(glow, glow, glow, 255);
        lamp.disableDiffuse();

        // 滤网：easeInQuad(cos(phase)) * 0.3 - 0.3 —— 旧版同一个公式（cos=1 时贴住，cos=0 时推到最外）
        final float slide = Mth.square(Mth.cos(phase)) * MESH_TRAVEL - MESH_TRAVEL;
        final SuperByteBuffer mesh = pristine(GasCrafterPartials.MESH, state);
        mesh.translate(Vec3.atLowerCornerOf(facing.getNormal()).scale(slide));

        renderOriented(pristine(GasCrafterPartials.FRAME, state), facing, light, cutout, ms);
        renderOriented(mesh, facing, light, translucent, ms);
        renderOriented(pristine(GasCrafterPartials.TUBE, state), facing, LightTexture.FULL_BRIGHT, cutout, ms);
        renderOriented(lamp, facing, LightTexture.FULL_BRIGHT, translucent, ms);
    }

    /**
     * 取一份"干净"的局部模型缓冲区。
     *
     * <p>{@code CachedBuffers.partial} 返回的是 {@code SuperByteBufferCache} 里的<b>缓存实例</b>
     * （同 key 直接返回同一个对象），而 {@code SuperByteBuffer} 的变换是就地累加的，
     * 因此每次使用前必须 {@code reset()}，否则上一帧/上一段的旋转会叠加进来。
     */
    private static SuperByteBuffer pristine(PartialModel model, BlockState state) {
        return CachedBuffers.partial(model, state).reset();
    }

    /** 绕方块中心转到朝向前，再写入顶点（旧版 {@code rotateAndRender} 逐字等价）。 */
    private static void renderOriented(SuperByteBuffer buf, Direction facing, int light, VertexConsumer vb,
                                       PoseStack ms) {
        buf.translate(0.5, 0.5, 0.5).rotateToFace(facing).translateBack(0.5, 0.5, 0.5);
        buf.light(light).renderInto(ms, vb);
    }
}
