package org.valkyrienskies.clockwork.content.curiosities.solver;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.valkyrienskies.clockwork.content.curiosities.CWInstrumentsIndex;

/**
 * 解算器（Solver）。
 *
 * <p>对应旧版 {@code curiosities/solver/SolverBlock.kt}：朝向前方射出一道 64 格的能量束，
 * 持续"烧穿"命中的方块。
 *
 * <p>移植差异：旧版还实现了 Kelvin 的 {@code INodeBlock}（气体网络节点），
 * 那部分依赖气体物流切片，本切片不引入；方块状态与外观完全一致。
 */
public class SolverBlock extends DirectionalBlock implements IBE<SolverBlockEntity> {

    public static final MapCodec<SolverBlock> CODEC = simpleCodec(SolverBlock::new);

    public SolverBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    public Class<SolverBlockEntity> getBlockEntityClass() {
        return SolverBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SolverBlockEntity> getBlockEntityType() {
        return CWInstrumentsIndex.SOLVER_BE.get();
    }
}
