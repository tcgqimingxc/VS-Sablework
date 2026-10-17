package org.valkyrienskies.clockwork.content.contraptions.phys.bearing;

import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 物理轴承家族的公共方块行为。
 *
 * <p>对标 SwivelBearingBlock：空手右键切换"装配 / 解体"，扳手旋转前先解体，
 * 并实现 {@link BlockSubLevelAssemblyListener} —— 轴承本身被别的装置搬进子空间时，
 * 要先把自己的子结构拆掉，否则会出现"子空间套子空间"的递归。
 *
 * <p>方块状态沿用旧版 JSON（只有 {@code facing} 一个属性），所以这里<b>不</b>新增
 * {@code assembled} 之类的属性，装配状态只存在于方块实体与客户端同步里。
 */
public abstract class AbstractBearingBlock extends BearingBlock implements BlockSubLevelAssemblyListener {

    protected AbstractBearingBlock(final Properties properties) {
        super(properties);
    }

    @Override
    protected ItemInteractionResult useItemOn(final ItemStack stack, final BlockState state, final Level level,
                                              final BlockPos pos, final Player player, final InteractionHand hand,
                                              final BlockHitResult hit) {
        if (!player.mayBuild()) {
            return ItemInteractionResult.FAIL;
        }
        if (player.isShiftKeyDown()) {
            return ItemInteractionResult.FAIL;
        }
        if (!stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof final AbstractBearingBlockEntity bearing) {
            bearing.toggleAssembled();
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onWrenched(final BlockState state, final UseOnContext context) {
        final Level level = context.getLevel();
        if (!level.isClientSide
                && level.getBlockEntity(context.getClickedPos()) instanceof final AbstractBearingBlockEntity bearing
                && bearing.isAssembled()) {
            // 有子结构时先放回世界，再旋转方块，避免约束轴与方块朝向不一致
            bearing.disassemble();
        }
        return super.onWrenched(state, context);
    }

    @Override
    public void beforeMove(final ServerLevel originLevel, final ServerLevel resultingLevel, final BlockState newState,
                           final BlockPos oldPos, final BlockPos newPos) {
        if (originLevel.getBlockEntity(oldPos) instanceof final AbstractBearingBlockEntity bearing) {
            // 轴承本身要被搬走了：先把自己的子结构放回世界，否则 plot 坐标一变约束就全错了
            bearing.onBeforeMovedByAssembly();
        }
    }

    @Override
    public void afterMove(final ServerLevel originLevel, final ServerLevel resultingLevel, final BlockState newState,
                          final BlockPos oldPos, final BlockPos newPos) {
        final BlockEntity moved = resultingLevel.getBlockEntity(newPos);
        if (moved instanceof final AbstractBearingBlockEntity bearing) {
            // 搬完之后位置/坐标系都变了，旧的装配信息一律作废（约束由 checkPersistence 重建）
            bearing.onAfterMovedByAssembly();
        }
    }
}
