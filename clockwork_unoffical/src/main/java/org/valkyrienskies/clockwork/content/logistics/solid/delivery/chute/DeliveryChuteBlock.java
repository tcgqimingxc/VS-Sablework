package org.valkyrienskies.clockwork.content.logistics.solid.delivery.chute;

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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.clockwork.content.logistics.gas.CWGasIndex;

/**
 * 交付滑道：接收从上方落下的物品（含交付加农炮打过来的），并从下方输出。
 *
 * <p>对应旧版 {@code content/logistics/solid/delivery/chute/DeliveryChuteBlock}（Kotlin）。
 * 语义保持一致：只能坐在 Create 的<b>置物台 / 传送带 / 滑槽</b>上，支撑方块消失就掉落。
 *
 * <p>频率交互（旧版是 {@code FrequencySlotGlobals} 的客户端射线 + 自定义包）：
 * <ul>
 *   <li><b>潜行 + 右键</b>（手持非方块物品）= 设置频率；<b>直接右键槽位</b>（物品悬浮在滑道顶部
 *       开口中心）= 同样设置 —— 后者就是旧版 ValueBox 的点击语义，两条路径都保留。</li>
 *   <li>手持非方块物品、但既没潜行也没点中槽位 = 只显示当前状态，不误改频率。</li>
 *   <li>空手右键 = 状态；空手潜行右键 = 清空频率。</li>
 * </ul>
 * 方块物品仍然让位给放置逻辑，搭桥/放方块不会被抢走。
 */
public class DeliveryChuteBlock extends Block implements IBE<DeliveryChuteBlockEntity> {

    public DeliveryChuteBlock(Properties properties) {
        super(properties);
    }

    /** 交付滑道与加农炮共用的支撑判定（旧版用方块描述 id 硬比，这里用 Create 的注册条目判定）。 */
    public static boolean isDeliverySupport(BlockState below) {
        return AllBlocks.DEPOT.has(below) || AllBlocks.BELT.has(below) || AllBlocks.CHUTE.has(below);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return isDeliverySupport(level.getBlockState(pos.below()));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    /**
     * 手持非方块物品：潜行或点中槽位 = 设置频率（不消耗物品），否则只报状态；
     * 空手 / 方块物品 = 交给默认处理（空手看 {@link #useWithoutItem}，方块物品走放置）。
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() || stack.getItem() instanceof BlockItem) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(level.getBlockEntity(pos) instanceof DeliveryChuteBlockEntity chute)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        // 命中点转成方块本地坐标（0..1）。Sable 的子空间交互已经在 plot 坐标里，无需再乘船体变换。
        final double localX = hit.getLocation().x - pos.getX();
        final double localY = hit.getLocation().y - pos.getY();
        final double localZ = hit.getLocation().z - pos.getZ();
        final boolean onSlot = chute.getFrequencySlot() != null
                && chute.getFrequencySlot().testHit(new Vec3(localX, localY, localZ));

        if (onSlot || player.isShiftKeyDown()) {
            chute.setFrequency(stack, player);
        } else {
            player.displayClientMessage(chute.statusMessage(), true);
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof DeliveryChuteBlockEntity chute)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            chute.clearFrequency(player);
        } else {
            player.displayClientMessage(chute.statusMessage(), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public Class<DeliveryChuteBlockEntity> getBlockEntityClass() {
        return DeliveryChuteBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends DeliveryChuteBlockEntity> getBlockEntityType() {
        return CWGasIndex.DELIVERY_CHUTE_BE.get();
    }
}
