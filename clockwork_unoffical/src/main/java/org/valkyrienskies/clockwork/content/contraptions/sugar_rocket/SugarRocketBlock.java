package org.valkyrienskies.clockwork.content.contraptions.sugar_rocket;

import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.valkyrienskies.clockwork.content.contraptions.CWContraptionsIndex;

/**
 * 糖火箭（旧版 {@code content/propulsion/sugar_rocket/SugarRocketBlock}）。
 *
 * <p>玩法：空手右键加糖（每颗糖 +1 秒燃烧、+1 推力档），收到红石信号点火，
 * 燃烧期间沿"朝向的反方向"喷射（旧版 {@code FACING.opposite}）推动结构。
 *
 * <p>为什么不用 {@code DirectionalBlock}：1.21.1 的 {@code DirectionalBlock} 要求实现
 * {@code codec()}（数据驱动方块），这里只需要一个 {@code facing} 属性，
 * 于是自己声明 {@link BlockStateProperties#FACING}，与旧版 JSON 完全对齐。
 *
 * <p><b>渲染形状必须是 {@code ENTITYBLOCK_ANIMATED}</b>（与旧版
 * {@code SugarRocketBlock.getRenderShape()} 一致）：{@code SugarRocketRenderer} 会把整个方块模型
 * 再画一遍（燃烧时按进度由白渐黑）。若用默认的 {@code MODEL}，区块渲染器已经画了一份静态模型，
 * BER 又画一份 —— 两份叠在一起，燃烧时的染色也只会染到后面那一份，实机表现是闪烁 / 忽明忽暗。
 */
public class SugarRocketBlock extends Block implements IBE<SugarRocketBlockEntity> {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public SugarRocketBlock(final Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.UP));
    }

    /** 方块本体完全交给 {@code SugarRocketRenderer} 绘制（旧版同款）。 */
    @Override
    protected RenderShape getRenderShape(final BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
    }

    @Override
    protected BlockState rotate(final BlockState state, final Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(final BlockState state, final Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected ItemInteractionResult useItemOn(final ItemStack stack, final BlockState state, final Level level,
                                              final BlockPos pos, final Player player, final InteractionHand hand,
                                              final BlockHitResult hit) {
        if (stack.is(Items.SUGAR)) {
            if (level.isClientSide) {
                return ItemInteractionResult.SUCCESS;
            }
            this.withBlockEntityDo(level, pos, be -> be.addSugar(1));
            if (!player.isCreative()) {
                stack.shrink(1);
            }
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected void neighborChanged(final BlockState state, final Level level, final BlockPos pos,
                                   final Block sourceBlock, final BlockPos sourcePos, final boolean isMoving) {
        super.neighborChanged(state, level, pos, sourceBlock, sourcePos, isMoving);
        if (level.isClientSide) {
            return;
        }
        if (level.hasNeighborSignal(pos)) {
            if (level.getBlockEntity(pos) instanceof final SugarRocketBlockEntity rocket) {
                rocket.induceIgnition();
            }
        }
    }

    @Override
    public Class<SugarRocketBlockEntity> getBlockEntityClass() {
        return SugarRocketBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SugarRocketBlockEntity> getBlockEntityType() {
        return CWContraptionsIndex.SUGAR_ROCKET_BE.get();
    }
}
