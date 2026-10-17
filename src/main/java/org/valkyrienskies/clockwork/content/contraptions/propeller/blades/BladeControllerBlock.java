package org.valkyrienskies.clockwork.content.contraptions.propeller.blades;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.valkyrienskies.clockwork.content.contraptions.CWContraptionsIndex;
import org.valkyrienskies.clockwork.content.contraptions.propeller.blades.item.BladeItem;

/**
 * 叶片控制器方块：螺旋桨的"桨毂"，插上叶片后由螺旋桨轴承读走桨距与叶片几何。
 *
 * <p>对应旧版 {@code blades/BladeControllerBlock.kt}（{@code DirectionalBlock} + {@code IBE}）。
 * 玩法与旧版一致：手持叶片右键装入（最多 8 片），空手右键取回最后装入的那片，
 * 扳手右键打开桨距滚轮面板（范围 ±90°）。
 *
 * <p>与旧版的差异：
 * <ul>
 *   <li>1.21 把 {@code use(...)} 拆成 {@code useItemOn}（手上有物品）/ {@code useWithoutItem}（空手），
 *       逻辑因此分成两个方法，行为不变。</li>
 *   <li>旧版方块只提供 {@code facing} 属性，且要求"控制器的 facing 必须与螺旋桨轴承一致"；
 *       这条校验现在由 {@link org.valkyrienskies.clockwork.content.contraptions.propeller.PropellerBearingBlockEntity}
 *       在装配时检查（旧版在 {@code PropellerContraption#assembleProp} 里检查）。</li>
 * </ul>
 */
public class BladeControllerBlock extends DirectionalBlock implements IBE<BladeControllerBlockEntity> {

    public static final MapCodec<BladeControllerBlock> CODEC = simpleCodec(BladeControllerBlock::new);

    public BladeControllerBlock(final Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    /** 手持叶片右键 = 装入（旧版 {@code use} 的第一个分支）。 */
    @Override
    protected ItemInteractionResult useItemOn(final ItemStack stack, final BlockState state, final Level level,
                                              final BlockPos pos, final Player player, final InteractionHand hand,
                                              final BlockHitResult hitResult) {
        if (!(stack.getItem() instanceof BladeItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof final BladeControllerBlockEntity controller)) {
            return ItemInteractionResult.FAIL;
        }
        final ItemStack single = stack.copy();
        single.setCount(1);
        if (!controller.insertBlade(single)) {
            return ItemInteractionResult.FAIL;
        }
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        return ItemInteractionResult.SUCCESS;
    }

    /** 空手右键 = 取回最后装入的那片（旧版 {@code use} 的第二个分支）。 */
    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos,
                                               final Player player, final BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof final BladeControllerBlockEntity controller)) {
            return InteractionResult.PASS;
        }
        final ItemStack blade = controller.removeBlade();
        if (blade.isEmpty()) {
            return InteractionResult.FAIL;
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, blade);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos, final BlockState newState,
                            final boolean movedByPiston) {
        // 触发 BladeControllerBlockEntity#destroy()：把槽位里的叶片掉出来
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<BladeControllerBlockEntity> getBlockEntityClass() {
        return BladeControllerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends BladeControllerBlockEntity> getBlockEntityType() {
        return CWContraptionsIndex.BLADE_CONTROLLER_BE.get();
    }
}
