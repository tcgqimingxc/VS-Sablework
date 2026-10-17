package org.valkyrienskies.clockwork.content.contraptions.reactionwheel;

import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.valkyrienskies.clockwork.content.contraptions.CWContraptionsIndex;

/**
 * 反作用轮（旧版 {@code content/physicalities/reactionwheel/ReactionWheelBlock}）。
 *
 * <p>转起来之后，靠角动量守恒给结构一个反向力矩 —— 不需要喷气就能转向。
 *
 * <p>移植位置说明：旧版属于 {@code content/physicalities}，本次只允许写
 * {@code content/contraptions/**}，因此落在 {@code content/contraptions/reactionwheel}；
 * 注册名保持 {@code reactionwheel} 不变。
 */
public class ReactionWheelBlock extends RotatedPillarKineticBlock implements IBE<ReactionWheelBlockEntity> {

    public ReactionWheelBlock(final Properties properties) {
        super(properties);
    }

    @Override
    public Class<ReactionWheelBlockEntity> getBlockEntityClass() {
        return ReactionWheelBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ReactionWheelBlockEntity> getBlockEntityType() {
        return CWContraptionsIndex.REACTION_WHEEL_BE.get();
    }

    @Override
    public Direction.Axis getRotationAxis(final BlockState state) {
        return state.getValue(AXIS);
    }

    /**
     * 方块本体完全交给 {@code ReactionWheelRenderer} 绘制（旧版 {@code ReactionWheelBlock.getRenderShape}
     * 同款，理由与 {@code GyroBlock} 完全一致：模型里既有轮子也有轴，静态那份必须让位给会转的那份）。
     *
     * <p>物品栏图标走物品模型 {@code block/reactionwheel/item}，不受影响。
     */
    @Override
    protected RenderShape getRenderShape(final BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public boolean hasShaftTowards(final LevelReader level, final BlockPos pos, final BlockState state,
                                   final Direction face) {
        return face.getAxis() == this.getRotationAxis(state);
    }

    @Override
    protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos,
                                  final CollisionContext context) {
        return Shapes.block();
    }
}
