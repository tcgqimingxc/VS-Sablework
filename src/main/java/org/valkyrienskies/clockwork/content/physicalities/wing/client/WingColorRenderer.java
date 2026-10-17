package org.valkyrienskies.clockwork.content.physicalities.wing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.valkyrienskies.clockwork.content.physicalities.wing.ConnectedWingBlock;
import org.valkyrienskies.clockwork.content.physicalities.wing.WingColorBlockEntity;
import org.valkyrienskies.clockwork.content.physicalities.wing.WingPartials;

/**
 * 机翼 / 襟翼的方块实体渲染器：把"翼面"按玩家染的颜色画出来。
 *
 * <p>对应旧版 {@code client.render.WingBlockEntityRenderer}（旧版挂在通用 ColorBlockEntity 上）。
 *
 * <p>渲染组成（与旧版一一对应）：
 * <ul>
 *   <li>主翼面 {@code wing_middle}：按朝向立起来；</li>
 *   <li>相邻翼面 {@code wing_side} / {@code wing_side_vertical}：只在对应连接标记为 true 时画，
 *       让连成一片的机翼看起来是整张膜（旋转角度表直接沿用旧版的分支）。</li>
 * </ul>
 *
 * <p>与旧版的差异：颜色以 24 位 RGB 存储，写顶点色前显式补上 {@code 0xFF} 不透明度
 * （旧版直接把颜色交给 {@code SuperByteBuffer#color(int)}，alpha 为 0 时会整片透明）。
 */
public class WingColorRenderer extends SmartBlockEntityRenderer<WingColorBlockEntity> {

    private static final float HALF_PI = (float) (Math.PI / 2);
    private static final float PI = (float) Math.PI;
    private static final float THREE_HALF_PI = (float) (Math.PI * 1.5);

    public WingColorRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(WingColorBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof ConnectedWingBlock)) {
            return;
        }
        VertexConsumer vb = buffer.getBuffer(RenderType.cutout());
        Direction.Axis axis = state.getValue(BlockStateProperties.FACING).getAxis();
        int color = be.getColor();

        SuperByteBuffer middle = colored(CachedBuffers.partial(WingPartials.WING_MIDDLE, state), color);
        switch (axis) {
            case Z -> middle.rotateCentered(HALF_PI, Direction.EAST);
            case X -> middle.rotateCentered(HALF_PI, Direction.NORTH);
            default -> {
            }
        }
        middle.light(light).renderInto(ms, vb);

        for (Direction side : Direction.values()) {
            if (side.getAxis() == axis || !state.getValue(propertyFor(side))) {
                continue;
            }
            // 竖向的轴（东西向）用竖直版侧翼，其余用水平版（与旧版的判断一致）
            boolean verticalSide = axis != Direction.Axis.Y && side.getAxis() != Direction.Axis.Y;
            SuperByteBuffer buf = colored(CachedBuffers.partial(
                    verticalSide ? WingPartials.WING_SIDE_VERTICAL : WingPartials.WING_SIDE, state), color);
            applySideRotation(buf, axis, side);
            buf.light(light).renderInto(ms, vb);
        }
    }

    /** 侧翼的摆放角度：逐条对应旧版渲染器里的分支。 */
    private static void applySideRotation(SuperByteBuffer buf, Direction.Axis axis, Direction side) {
        switch (side.getAxis()) {
            case Z -> {
                if (side == Direction.SOUTH) {
                    buf.rotateCentered(PI, Direction.UP);
                }
            }
            case X -> buf.rotateCentered(side == Direction.EAST ? THREE_HALF_PI : HALF_PI, Direction.UP);
            case Y -> {
                if (axis == Direction.Axis.X) {
                    // 东西向机翼的上下翼面要转两次：先立起来，再绕水平轴摆正
                    if (side == Direction.UP) {
                        buf.rotateCentered(HALF_PI, Direction.EAST).rotateCentered(THREE_HALF_PI, Direction.NORTH);
                    } else {
                        buf.rotateCentered(HALF_PI, Direction.NORTH).rotateCentered(THREE_HALF_PI, Direction.UP);
                    }
                } else {
                    buf.rotateCentered(side == Direction.UP ? HALF_PI : THREE_HALF_PI, Direction.EAST);
                }
            }
            default -> {
            }
        }
    }

    private static SuperByteBuffer colored(SuperByteBuffer buffer, int color) {
        return color == WingColorBlockEntity.NO_COLOR ? buffer : buffer.color(0xFF000000 | color);
    }

    private static BooleanProperty propertyFor(Direction side) {
        return switch (side) {
            case NORTH -> BlockStateProperties.NORTH;
            case SOUTH -> BlockStateProperties.SOUTH;
            case EAST -> BlockStateProperties.EAST;
            case WEST -> BlockStateProperties.WEST;
            case UP -> BlockStateProperties.UP;
            case DOWN -> BlockStateProperties.DOWN;
        };
    }
}
