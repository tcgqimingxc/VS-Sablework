package org.valkyrienskies.clockwork.content.logistics.gas.generator;

import com.simibubi.create.foundation.block.IBE;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import org.valkyrienskies.clockwork.content.logistics.gas.CWGasIndex;
import org.valkyrienskies.clockwork.content.logistics.gas.IGasNodeBlock;

/**
 * 煤炉：烧炼燃料给网络加热（旧版说明"最高加热到 1000K"）。
 *
 * <p>对应旧版 {@code CoalBurnerBlock}（{@code HorizontalDirectionalBlock} + {@code facing}/{@code lit}）。
 * 方块状态与资源 JSON 一致（multipart 用到 facing 与 lit）。
 *
 * <p>与旧版差异：旧版把燃料槽做成 {@code WorldlyContainer} 并接入 {@code ISyncableStorage}（漏斗/机械臂
 * 可自动投料并同步物品栏）。本移植保留容器接口（漏斗可投料/取空桶）但去掉了自定义同步包 ——
 * 物品栏内容走原版 GUI 数据同步即可，机器本身没有界面，因此没有额外同步需求。
 */
public class CoalBurnerBlock extends HorizontalDirectionalBlock implements IBE<CoalBurnerBlockEntity>, IGasNodeBlock {

    public static final MapCodec<CoalBurnerBlock> CODEC = simpleCodec(CoalBurnerBlock::new);

    public CoalBurnerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(BlockStateProperties.LIT, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BlockStateProperties.LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public Class<CoalBurnerBlockEntity> getBlockEntityClass() {
        return CoalBurnerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CoalBurnerBlockEntity> getBlockEntityType() {
        return CWGasIndex.COAL_BURNER_BE.get();
    }

    /** 空手取回燃料/残余，手持燃料则投入（与旧版一致）。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                              BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CoalBurnerBlockEntity burner)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ItemStack extracted = burner.extractOne();
        if (!extracted.isEmpty()) {
            player.getInventory().placeItemBackInInventory(extracted);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown() || stack.isEmpty()) {
            return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(level.getBlockEntity(pos) instanceof CoalBurnerBlockEntity burner)) {
            return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!burner.accepts(stack)) {
            return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return net.minecraft.world.ItemInteractionResult.SUCCESS;
        }
        burner.insertFromHand(player, hand);
        return net.minecraft.world.ItemInteractionResult.SUCCESS;
    }
}
