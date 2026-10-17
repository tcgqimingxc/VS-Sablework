package org.valkyrienskies.clockwork.content.logistics.gas.backtank;

import com.simibubi.create.AllShapes;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.valkyrienskies.clockwork.content.logistics.gas.CWGasIndex;
import org.valkyrienskies.clockwork.content.logistics.gas.GasNodeBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.IGasNodeBlock;

/**
 * 气体背罐（方块形态）：小型气体储罐，也是随身携带气体的"容器"。
 *
 * <p>对应旧版 {@code GasBacktankBlock}（{@code HorizontalDirectionalBlock} +
 * Kelvin 的 {@code TankDuctNode}，容量 0.75）。移植差异：
 * <ul>
 *   <li>容量沿用旧版的 {@code volume = 0.75}，但本移植的容量单位是 kg（见
 *       {@link org.valkyrienskies.clockwork.content.logistics.gas.GasPhysics#nodeCapacityKg()}），
 *       所以常量写在 {@link #CAPACITY_KG} 里并在注册时传入。</li>
 *   <li>旧版只能从"上/下"两面连管道（{@code canConnectTo} 判 {@code direction.axis != Y} 返回 false）；
 *       本移植一致。</li>
 *   <li>空手右键 = 把背罐穿到胸甲槽（旧版行为）；破坏时掉落物携带内部气体
 *       （{@link #getCloneItemStack}）。</li>
 * </ul>
 */
public class GasBacktankBlock extends HorizontalDirectionalBlock
        implements IBE<GasBacktankBlockEntity>, IGasNodeBlock {

    /** 旧版 {@code TankDuctNode(volume = 0.75)}：背罐与"0.75 个导管节点"等容。 */
    public static final double CAPACITY_KG = 0.75;

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public GasBacktankBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.SOUTH));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(GasBacktankBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 旧版：潜行放置时朝向玩家（背对玩家），否则朝玩家看的方向
        final Direction horizontal = context.getHorizontalDirection();
        return defaultBlockState().setValue(FACING, context.getPlayer() != null
                && context.getPlayer().isShiftKeyDown() ? horizontal.getOpposite() : horizontal);
    }

    @Override
    public boolean gasConnectable(BlockState state, Direction dir) {
        // 旧版：只允许上下两面接管道
        return dir.getAxis() == Direction.Axis.Y;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return AllShapes.BACKTANK;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        // 空手右键：直接穿到胸甲槽（旧版行为）；手里拿着方块或已有胸甲时不处理
        if (!stack.isEmpty() || !player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof GasNodeBlockEntity node)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.75F, 1.0F);
        player.setItemSlot(EquipmentSlot.CHEST, GasBacktankItem.withGas(this, node.gasMix()));
        level.destroyBlock(pos, false);
        return ItemInteractionResult.SUCCESS;
    }

    /** 破坏时把内部气体一起带走（旧版 {@code getCloneItemStack} + {@code getDrops}）。 */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        if (!(level.getBlockEntity(pos) instanceof GasNodeBlockEntity node)) {
            return new ItemStack(this);
        }
        return GasBacktankItem.withGas(this, node.gasMix());
    }

    @Override
    public Class<GasBacktankBlockEntity> getBlockEntityClass() {
        return GasBacktankBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends GasBacktankBlockEntity> getBlockEntityType() {
        return CWGasIndex.GAS_BACKTANK_BE.get();
    }
}
