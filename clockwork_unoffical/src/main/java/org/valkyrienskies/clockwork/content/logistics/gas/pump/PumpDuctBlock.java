package org.valkyrienskies.clockwork.content.logistics.gas.pump;

import com.simibubi.create.AllShapes;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.valkyrienskies.clockwork.content.logistics.gas.CWGasIndex;
import org.valkyrienskies.clockwork.content.logistics.gas.IGasNodeBlock;

/**
 * 气体泵：靠转速在两侧建立压差，把气体从背面推到正面。
 *
 * <p>对应旧版 {@code PumpDuctBlock}（{@code DirectionalKineticBlock} + {@code ICogWheel}）。
 *
 * <p>与旧版的差异：旧版通过 {@code IConnectable#updateConnection} 主动往 Kelvin 求解器里塞
 * {@code PumpDuctEdge}，并在 {@code onSpeedChanged} 时更新边的 {@code pumpPressure}；
 * 本移植把"泵压"变成 {@code GasLink.bias}，每刻按当前转速现算（见 {@link PumpDuctBlockEntity}），
 * 因此不需要在转速变化时做任何同步。
 *
 * <p>连接规则与旧版一致：只在朝向轴上连通（侧面的导管不导通）。转速上限 256 RPM 时达到最大泵压
 * （旧版 {@code maxPumpPressure = 1023440 Pa}，约 10 atm）。
 */
public class PumpDuctBlock extends DirectionalKineticBlock
        implements IBE<PumpDuctBlockEntity>, IGasNodeBlock, ICogWheel {

    public PumpDuctBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.WATERLOGGED);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return AllShapes.SIX_VOXEL_POLE.get(state.getValue(FACING).getAxis());
    }

    @Override
    public boolean gasConnectable(BlockState state, Direction dir) {
        return dir.getAxis() == state.getValue(FACING).getAxis();
    }

    @Override
    public Class<PumpDuctBlockEntity> getBlockEntityClass() {
        return PumpDuctBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends PumpDuctBlockEntity> getBlockEntityType() {
        return CWGasIndex.PUMP_DUCT_BE.get();
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(BlockStateProperties.WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }
}
