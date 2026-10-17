package org.valkyrienskies.clockwork.content.physicalities.extendon;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.clockwork.content.kinetics.CWKineticsIndex;

/**
 * 可伸缩杆（Extendon）：用「软管卷」把两根伸缩杆连起来，杆长随两端内部气压变化。
 *
 * <p>对应旧版 {@code content.physicalities.extendon.ExtendonBlock}。
 *
 * <p>与旧版的差异：
 * <ul>
 *   <li>旧版实现了 {@code INodeBlock}（Kelvin 气体网络的节点）与气体相关的 tooltip / 长度显示；
 *       这部分属于气体物流切片，本移植不实现 —— 因此这个方块目前只完成"朝向 + 软管连接"，
 *       气压驱动的伸缩与两点间的物理约束见 {@link ExtendonBlockEntity} 的类注释。</li>
 *   <li>旧版的 {@code use(...)} 在 1.21 拆成 {@code useWithoutItem} / {@code useItemOn}：
 *       空手右键拆软管，手持方块时让位给放置逻辑。</li>
 * </ul>
 *
 * <p>注意它是<b>非动力</b>方块（不传递旋转），所以继承的是普通的 {@link DirectionalBlock}，
 * 只是把 Create {@code DirectionalKineticBlock} 的"朝向邻居轴"摆放逻辑抄了一份
 * ——旧版也是这么做的（那段代码与 Create 的默认实现逐行相同）。
 */
public class ExtendonBlock extends DirectionalBlock implements IBE<ExtendonBlockEntity> {

    /** 1.21 起方块需要提供 codec（{@code DirectionalBlock} 把它声明成抽象方法）。 */
    public static final MapCodec<ExtendonBlock> CODEC = simpleCodec(ExtendonBlock::new);

    public ExtendonBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction preferred = getPreferredFacing(context);
        Direction nearest = context.getNearestLookingDirection();
        boolean sneaking = context.getPlayer() != null && context.getPlayer().isShiftKeyDown();
        if (preferred == null || sneaking) {
            return defaultBlockState().setValue(FACING, sneaking ? nearest : nearest.getOpposite());
        }
        return defaultBlockState().setValue(FACING, preferred.getOpposite());
    }

    /** 找一个"愿意对接"的邻居轴；有两个不同轴则放弃（与旧版逐行一致）。 */
    private static Direction getPreferredFacing(BlockPlaceContext context) {
        Direction preferred = null;
        for (Direction side : Iterate.directions) {
            BlockPos neighbourPos = context.getClickedPos().relative(side);
            BlockState neighbour = context.getLevel().getBlockState(neighbourPos);
            if (!(neighbour.getBlock() instanceof IRotate rotate)) {
                continue;
            }
            if (!rotate.hasShaftTowards(context.getLevel(), neighbourPos, neighbour, side.getOpposite())) {
                continue;
            }
            if (preferred != null && preferred.getAxis() != side.getAxis()) {
                return null;
            }
            preferred = side.getOpposite();
        }
        return preferred;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (level.getBlockEntity(pos) instanceof ExtendonBlockEntity be) {
            be.disconnect();
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    /** 空手右键 = 拆掉软管并返还。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof ExtendonBlockEntity be) || !be.isLinked()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        be.disconnect();
        if (!player.isCreative()) {
            player.addItem(be.getConnectionItem());
        }
        return InteractionResult.SUCCESS;
    }

    /** 手持方块时优先放置方块（旧版同规则）。 */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, net.minecraft.world.InteractionHand hand,
                                              BlockHitResult hitResult) {
        if (stack.getItem() instanceof BlockItem) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    public Class<ExtendonBlockEntity> getBlockEntityClass() {
        return ExtendonBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ExtendonBlockEntity> getBlockEntityType() {
        return CWKineticsIndex.EXTENDON_BE.get();
    }
}
