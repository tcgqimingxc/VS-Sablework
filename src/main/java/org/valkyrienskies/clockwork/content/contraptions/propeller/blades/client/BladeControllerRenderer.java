package org.valkyrienskies.clockwork.content.contraptions.propeller.blades.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.clockwork.content.contraptions.propeller.blades.BladeControllerBlock;
import org.valkyrienskies.clockwork.content.contraptions.propeller.blades.BladeControllerBlockEntity;
import org.valkyrienskies.clockwork.content.contraptions.propeller.blades.BladeData;
import org.valkyrienskies.clockwork.content.contraptions.propeller.blades.BladePartials;
import org.valkyrienskies.clockwork.content.contraptions.propeller.blades.item.BladeItems;

import java.util.List;

/**
 * 叶片控制器渲染器：把装在控制器里的叶片按"实物"画出来。
 *
 * <p>对应旧版 {@code blades/BladeControllerRenderer.kt}
 * （部分模型 + 朝向对齐 + 桨距角 + 展长拉伸）。
 *
 * <p><b>与旧版的差异</b>
 * <ul>
 *   <li>旧版每片叶子还有一个"角位置"插值器（{@code clientBladeRotation}，由螺旋桨 contraption 旋转时
 *       逐片 chase）。新架构下整个螺旋桨结构是 Sable 子空间，叶片的公转由子空间自己完成，
 *       控制器只需要把叶片按 {@code 360° / 片数} <b>均布</b>，因此不需要网络同步的角度缓存。</li>
 *   <li>桨距角直接读 {@link BladeControllerBlockEntity#pitchDegrees()}（滚轮选项自带客户端同步），
 *       旧版是单独的角速度插值器 {@code clientBladeAngle}。</li>
 *   <li>展长取 {@link BladeData#length()}（来自叶片物品的数据组件，可被加长 / 切割配方改变），
 *       旧版从物品 NBT 的 {@code BladeLength} 读；拉伸方式与旧版逐项一致
 *       （中段沿轴向 {@code scale(z, length)}、叶尖跟随外移，常量 0.3 / 0.5 / 1.0 全部照抄）。</li>
 *   <li>旧版还要处理 Create contraption 的渲染矩阵（{@code ContraptionMatrices}）；这里叶片永远在
 *       Sable 子空间里，Sable 用原版 {@code BlockEntityRenderDispatcher} 派发 BER
 *       （{@code VanillaSubLevelBlockEntityRenderer}），所以什么都不用做。</li>
 * </ul>
 */
public class BladeControllerRenderer extends SmartBlockEntityRenderer<BladeControllerBlockEntity> {

    /** 桨距旋转的支点 = 方块中心（旧版 {@code pivot = Vec3(0.5, 0.5, 0.5)}）。 */
    private static final Vec3 PIVOT = new Vec3(0.5, 0.5, 0.5);
    /** 整片叶片沿局部 -Z 后移的距离 [格]（旧版常量 0.3）。 */
    private static final float BLADE_OFFSET = -0.3f;

    public BladeControllerRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(BladeControllerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        final List<BladeData> blades = be.bladeData();
        final BlockState state = be.getBlockState();
        if (blades.isEmpty() || !(state.getBlock() instanceof BladeControllerBlock)) {
            return;
        }
        final Direction facing = state.getValue(BladeControllerBlock.FACING);
        final VertexConsumer vb = buffer.getBuffer(RenderType.cutout());
        // 绕转轴均布：第 i 片转 (360°/片数)·i（旧版 tick 里的 angleBetweenBlades * i）
        final float step = 360.0f / blades.size();
        for (int i = 0; i < blades.size(); i++) {
            renderBlade(blades.get(i), step * i, facing, state, ms, vb, light);
        }
    }

    private static void renderBlade(BladeData blade, float slotAngleDegrees, Direction facing, BlockState state,
                                    PoseStack ms, VertexConsumer vb, int light) {
        final boolean wide = blade.wide();
        final float length = (float) blade.length();
        final float pitchDegrees = (float) blade.angleDegrees();

        final SuperByteBuffer base = oriented(pristine(
                wide ? BladePartials.WIDEBLADE_BASE : BladePartials.BLADE_BASE, state), facing, slotAngleDegrees, pitchDegrees);
        final SuperByteBuffer extension = oriented(pristine(
                wide ? BladePartials.WIDEBLADE_EXTENSION : BladePartials.BLADE_EXTENSION, state), facing, slotAngleDegrees, pitchDegrees);
        final SuperByteBuffer tip = oriented(pristine(
                wide ? BladePartials.WIDEBLADE_TIP : BladePartials.BLADE_TIP, state), facing, slotAngleDegrees, pitchDegrees);

        // 展长：中段沿轴向拉伸，叶尖跟着往外挪（常量与旧版 renderBlade 一致）
        extension.translate(0.0, 0.0, (length - 1.0f) * 0.3f);
        extension.scale(1.0f, 1.0f, length);
        tip.translate(0.0, 0.0, (length - 1.0f) * 0.5f);
        tip.translate(0.0, 0.0, 1.0f - length);

        base.translate(0.0, 0.0, BLADE_OFFSET).light(light).renderInto(ms, vb);
        extension.translate(0.0, 0.0, BLADE_OFFSET).light(light).renderInto(ms, vb);
        tip.translate(0.0, 0.0, BLADE_OFFSET).light(light).renderInto(ms, vb);
    }

    /**
     * 取一份"干净"的局部模型缓冲区。
     *
     * <p>{@code CachedBuffers.partial} 返回的是 {@code SuperByteBufferCache} 里的<b>缓存实例</b>
     * （同 key 的调用拿到同一个对象，见 {@code CachedBuffers.partial} 的字节码），
     * 而 {@code SuperByteBuffer} 的变换是就地累加的，所以同一帧内多次取同一模型必须先 {@code reset()}，
     * 否则第二片叶子会带上第一片的旋转。
     */
    private static SuperByteBuffer pristine(PartialModel model, BlockState state) {
        return CachedBuffers.partial(model, state).reset();
    }

    /**
     * 朝向对齐 → 绕转轴均布 → 桨距角（旧版 {@code renderBlade} 的前四步，<b>顺序不能改</b>：
     * SuperByteBuffer 的变换是右乘，后面的变换作用在更局部的坐标系里）。
     */
    private static SuperByteBuffer oriented(SuperByteBuffer buf, Direction facing, float slotAngleDegrees,
                                            float pitchDegrees) {
        if (facing.getAxis().isHorizontal()) {
            buf.rotateCentered(AngleHelper.rad(AngleHelper.horizontalAngle(facing.getOpposite())), Direction.UP);
        }
        buf.rotateCentered(AngleHelper.rad(-90.0 - AngleHelper.verticalAngle(facing)), Direction.EAST);
        buf.rotateCentered(AngleHelper.rad(slotAngleDegrees), Direction.UP);
        // 桨距：绕叶片长轴（局部 Z）转，支点是方块中心
        buf.translate(PIVOT).rotateZDegrees(pitchDegrees).translateBack(PIVOT);
        return buf;
    }

    /**
     * 叶片最远伸到控制器外约 {@code 0.5 × 展长 + 0.8} 格（模型：叶尖 z ∈ [-0.5L-0.8, …]，
     * 再加上 0.3 的整片后移与均布旋转），包围盒要按<b>最大</b>展长放大，
     * 否则长叶片在屏幕边缘会被视锥剔除（旧版固定 inflate(1)，只够 1~2 格）。
     *
     * <p>用配置上限而不是当前叶片的实际长度：包围盒是按方块实体缓存的，
     * 玩家换叶片 / 改配置后不一定立刻重建，按上限给是最稳的。
     */
    @Override
    public AABB getRenderBoundingBox(final BladeControllerBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(0.5 * BladeItems.maxLength() + 1.0);
    }
}
