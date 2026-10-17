package org.valkyrienskies.clockwork.content.physicalities.slicker.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.valkyrienskies.clockwork.content.physicalities.slicker.SlickerBlockEntity;
import org.valkyrienskies.clockwork.content.physicalities.slicker.SlickerPartials;

/**
 * 减摩方块的渲染器：把"黏液团"沿 FACING 方向按伸出进度推出去。
 *
 * <p>对应旧版 {@code content.contraptions.phys.slicker.SlickerBlockEntityRenderer}。
 *
 * <p>与旧版的差异：旧版为了摆正模型写了一长串 {@code when(facing)} 分支；这里改成
 * "先转到朝向，再沿局部 +Y 平移"的统一写法，结果等价但只有几行
 * （旧版最后也是把 {@code goo} 沿局部 Y 平移 {@code piston - 2/16}）。
 */
public class SlickerRenderer extends SmartBlockEntityRenderer<SlickerBlockEntity> {

    /** 收回时黏液团缩在方块内 2/16 格处，与旧版一致。 */
    private static final double RETRACTED_OFFSET = -2.0 / 16.0;
    /** 伸出时的额外位移：10/16 格。 */
    private static final double EXTENDED_TRAVEL = 10.0 / 16.0;

    public SlickerRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(SlickerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        BlockState state = be.getBlockState();
        Direction facing = state.getValue(BlockStateProperties.FACING);

        ms.pushPose();
        // 把局部坐标系的 +Y 转到 FACING：先移到方块中心，转完再移回来
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(rotationFor(facing));
        ms.translate(-0.5, -0.5, -0.5);

        double offset = RETRACTED_OFFSET + be.getPistonOffset(partialTicks) * EXTENDED_TRAVEL;
        CachedBuffers.partial(SlickerPartials.GOO, state)
                .light(light)
                .translate(0, offset, 0)
                .renderInto(ms, buffer.getBuffer(RenderType.translucent()));
        ms.popPose();

        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);
    }

    /** 局部 +Y 转到目标朝向所需的最小旋转（旧版那一串分支的等价写法）。 */
    private static Quaternionf rotationFor(Direction facing) {
        return switch (facing) {
            case UP -> new Quaternionf();
            case DOWN -> new Quaternionf(new AxisAngle4f(AngleHelper.rad(180), 1, 0, 0));
            case NORTH -> new Quaternionf(new AxisAngle4f(AngleHelper.rad(90), 1, 0, 0));
            case SOUTH -> new Quaternionf(new AxisAngle4f(AngleHelper.rad(270), 1, 0, 0));
            case EAST -> new Quaternionf(new AxisAngle4f(AngleHelper.rad(90), 0, 0, 1))
                    .rotateY(AngleHelper.rad(90));
            case WEST -> new Quaternionf(new AxisAngle4f(AngleHelper.rad(270), 0, 0, 1))
                    .rotateY(AngleHelper.rad(90));
        };
    }
}
