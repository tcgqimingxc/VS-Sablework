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

    /**
     * 空手右键 = 取回最后装入的那片（旧版 {@code use} 的第二个分支）。
     *
     * <p><b>为什么必须先判主手为空</b>：1.21 的 {@code useWithoutItem} 并不保证主手是空的。
     * 只要 {@link #useItemOn} 返回 {@code PASS_TO_DEFAULT_BLOCK_INTERACTION}，服务端就会继续调用这里
     * （{@code ServerPlayerGameMode#useItemOn} 只判断 {@code hand == MAIN_HAND}，不判断物品是否为空）。
     * 旧实现无条件 {@code setItemInHand(MAIN_HAND, blade)}，于是"主手拿着任意物品右键控制器"
     * 会把那件物品整个覆盖掉 —— 玩家反馈的"取下桨叶吞物品"。
     * 主手非空时返回 PASS：把交互让给手上的物品（例如扳手打开桨距面板），绝不覆盖。
     */
    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos,
                                               final Player player, final BlockHitResult hitResult) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
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

    /**
     * 玩家亲手拆掉控制器时归还里面的叶片（旧版 {@code destroy() -> dropBlades()} 的意图）。
     *
     * <p><b>为什么不能放在 {@code onRemove} / 方块实体 {@code destroy()} 里</b>：装配 / 解体时
     * Sable 用 {@code LevelChunk#setBlockState(pos, AIR, true)} 搬方块，而它对旧状态同样会回调
     * {@code onRemove}（1.21.1 反编译 {@code LevelChunk.java:274}）→ {@code IBE.onRemove}
     * → 方块实体 {@code destroy()}。此时方块实体数据已经被复制进子空间 / 放回世界
     * （{@code SubLevelAssemblyHelper#moveBlocks} 先 {@code saveWithFullMetadata} 再放块），
     * 再按"被破坏"掉落就会每开关一次动态结构凭空多出一份（8 片 / 控制器），
     * 来回几次就是满地桨叶 —— 玩家反馈的"大量桨叶掉落物"。
     *
     * <p>因此归还只挂在"玩家挖掘"这条明确路径上（此时方块实体还在，能安全取出叶片）；
     * 搬运路径一律不掉，结构是整体的。
     */
    @Override
    public BlockState playerWillDestroy(final Level level, final BlockPos pos, final BlockState state,
                                        final Player player) {
        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof final BladeControllerBlockEntity controller) {
            controller.dropBlades();
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos, final BlockState newState,
                            final boolean movedByPiston) {
        // 只做"方块实体清理"，不归还叶片（理由见 dropBlades 的注释）。
        // 注意：装配 / 解体搬方块也会走到这里（LevelChunk#setBlockState 同样回调 onRemove），
        // 所以这条路径上绝对不能产生任何掉落物。
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
