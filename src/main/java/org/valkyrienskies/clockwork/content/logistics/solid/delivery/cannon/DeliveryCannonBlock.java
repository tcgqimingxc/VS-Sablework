package org.valkyrienskies.clockwork.content.logistics.solid.delivery.cannon;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.valkyrienskies.clockwork.content.logistics.gas.CWGasIndex;
import org.valkyrienskies.clockwork.content.logistics.solid.delivery.chute.DeliveryChuteBlock;

/**
 * 交付加农炮：朝 {@code FACING} 把下方容器里的物品抛射到交付滑道上。
 *
 * <p>对应旧版 {@code content/logistics/solid/delivery/cannon/DeliveryCannonBlock}（Kotlin，
 * {@code HorizontalDirectionalBlock}）。保持一致的语义：只能坐在 Create 的置物台 / 传送带 / 滑槽上，
 * 支撑方块消失就掉落；用非方块物品右键设置频率（旧版是频率槽界面），潜行 + 空手右键清空频率。
 *
 * <p>与旧版的差异：旧版的朝向是 {@code horizontalDirection.clockWise}（作者注释说"模型做歪了所以这样转"），
 * 本移植<b>沿用同一个规则</b>以保证模型炮口与发射方向一致；旧版有火药加速、圆盘分布模式与抛物线渲染，
 * 这里按任务要求"频率槽可简化"，只保留"同频才能投递"这一条核心规则。
 */
public class DeliveryCannonBlock extends HorizontalDirectionalBlock implements IBE<DeliveryCannonBlockEntity> {

    public DeliveryCannonBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(DeliveryCannonBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** 与旧版一致：{@code FACING} 取玩家水平朝向的顺时针一位（旧版模型就是这么做的）。 */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getClockWise());
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return DeliveryChuteBlock.isDeliverySupport(level.getBlockState(pos.below()));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    /** 红石上升沿：让加农炮立刻补射一次（跳过冷却；旧版没有红石接口，这是本移植新增的便利）。 */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide && level.hasNeighborSignal(pos)
                && level.getBlockEntity(pos) instanceof DeliveryCannonBlockEntity cannon) {
            cannon.requestImmediateShot();
        }
    }

    /** 手持非方块物品右键 = 设置频率（不消耗）；空手右键 = 查看状态 / 潜行清空频率。 */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() || stack.getItem() instanceof BlockItem) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(level.getBlockEntity(pos) instanceof DeliveryCannonBlockEntity cannon)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        cannon.setFrequency(stack, player);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof DeliveryCannonBlockEntity cannon)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            cannon.clearFrequency(player);
        } else {
            player.displayClientMessage(cannon.statusMessage(), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public Class<DeliveryCannonBlockEntity> getBlockEntityClass() {
        return DeliveryCannonBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends DeliveryCannonBlockEntity> getBlockEntityType() {
        return CWGasIndex.DELIVERY_CANNON_BE.get();
    }
}
