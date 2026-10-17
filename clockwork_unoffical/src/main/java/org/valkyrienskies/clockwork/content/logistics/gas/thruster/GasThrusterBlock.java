package org.valkyrienskies.clockwork.content.logistics.gas.thruster;

import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.valkyrienskies.clockwork.content.logistics.gas.CWGasIndex;
import org.valkyrienskies.clockwork.content.logistics.gas.IGasNodeBlock;

/**
 * 气体推进器：气体从喷口流出产生推力（对所在的 Sable 子空间施力）。
 *
 * <p>对应旧版 {@code content/physicalities/gas_thruster/GasThrusterBlock}（{@code DirectionalBlock}，
 * 只有 {@code facing}）。旧版把物理计算放在实现 VS2 {@code BlockEntityPhysicsListener} 的方块实体里；
 * 本移植改为实现 Sable 的 {@code BlockEntitySubLevelActor}（见
 * {@link GasThrusterBlockEntity#sable$physicsTick}）。
 *
 * <p>连接规则：只有朝向轴（进气管在背面）算连通。
 */
public class GasThrusterBlock extends DirectionalBlock implements IBE<GasThrusterBlockEntity>, IGasNodeBlock {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private static final VoxelShape SHAPE = Shapes.box(0.125, 0.125, 0.125, 0.875, 0.875, 0.875);

    public GasThrusterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends DirectionalBlock> codec() {
        return simpleCodec(GasThrusterBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 旧版：喷口朝玩家看的方向
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    public boolean gasConnectable(BlockState state, Direction dir) {
        return dir.getAxis() == state.getValue(FACING).getAxis();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public Class<GasThrusterBlockEntity> getBlockEntityClass() {
        return GasThrusterBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends GasThrusterBlockEntity> getBlockEntityType() {
        return CWGasIndex.GAS_THRUSTER_BE.get();
    }
}
