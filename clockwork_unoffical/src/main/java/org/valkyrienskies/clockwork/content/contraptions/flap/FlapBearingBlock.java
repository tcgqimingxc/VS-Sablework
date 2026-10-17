package org.valkyrienskies.clockwork.content.contraptions.flap;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.valkyrienskies.clockwork.content.contraptions.phys.bearing.AbstractBearingBlock;

/**
 * 襟翼轴承的公共方块行为（旧版 {@code content/contraptions/flap/FlapBearingBlock}）。
 *
 * <p>和物理轴承的区别只在"旋转控制"策略：襟翼不让输入轴带着结构整圈转，
 * 而是把输入轴转速转成相位，让结构在 ±振幅之间来回摆动（见 {@link FlapBearingBlockEntity}）。
 *
 * <p>方块状态比其它轴承多一个 {@code axis_along_first}（旧版
 * {@code AbstractDirectionalAxisBlock.AXIS_ALONG_FIRST_COORDINATE}），
 * 用来决定铰链的转轴是与朝向垂直的两轴中的哪一个；模型 JSON 里已经这么写了，
 * 所以属性名必须完全一致。
 *
 * <p>{@code IBE} 的泛型不能协变，所以"方块实体类型"由具体方块
 * （{@link AndesiteFlapBearingBlock} / {@link SmartFlapBearingBlock}）各自声明。
 */
public abstract class FlapBearingBlock extends AbstractBearingBlock {

    public static final BooleanProperty AXIS_ALONG_FIRST_COORDINATE = BooleanProperty.create("axis_along_first");

    protected FlapBearingBlock(final Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(AXIS_ALONG_FIRST_COORDINATE, false));
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(AXIS_ALONG_FIRST_COORDINATE));
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        final BlockState state = super.getStateForPlacement(context);
        if (state == null) {
            return null;
        }
        // 摆法与旧版 AbstractDirectionalAxisBlock 一致：
        //   水平朝向 -> 沿 Z 轴为 true；垂直朝向 -> 玩家面朝 Z 轴为 true；
        //   副手放置（shift）取反。JSON 里水平朝向只写了一条 variant，两种取值共用同一个模型。
        final boolean shift = context.isSecondaryUseActive();
        final Direction.Axis faceAxis = state.getValue(FACING).getAxis();
        final boolean alongFirst;
        if (faceAxis.isHorizontal()) {
            alongFirst = faceAxis == Direction.Axis.Z;
        } else if (faceAxis.isVertical()) {
            alongFirst = context.getHorizontalDirection().getAxis() == Direction.Axis.Z;
        } else {
            alongFirst = false;
        }
        return state.setValue(AXIS_ALONG_FIRST_COORDINATE, shift != alongFirst);
    }
}

