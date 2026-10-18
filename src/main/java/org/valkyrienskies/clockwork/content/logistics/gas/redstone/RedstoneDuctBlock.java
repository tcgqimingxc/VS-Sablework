package org.valkyrienskies.clockwork.content.logistics.gas.redstone;

import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.valkyrienskies.clockwork.content.logistics.gas.CWGasIndex;
import org.valkyrienskies.clockwork.content.logistics.gas.IGasNodeBlock;

/**
 * 红石导管：把网络压力变成红石信号。
 *
 * <p>对应旧版 {@code RedstoneDuctBlock}（{@code RotatedPillarBlock} + {@code POWER}）。
 * 资源 JSON 只有 {@code axis}，额外的 {@code power} 属性属于部分匹配，不会缺模型。
 *
 * <p><b>与旧版的差异（重要）</b>：旧版用 GUI 配置"条件"（过滤类型 + 比较值 + 大于/小于），
 * 凑齐条件时输出满信号 15。本切片没有移植该界面，改为<b>信号强度 = 压力对应的 atm 数</b>
 * （1 atm = 1 信号，15 atm 以上满信号），语义等价且不需要界面。
 */
public class RedstoneDuctBlock extends RotatedPillarBlock implements IBE<RedstoneDuctBlockEntity>, IGasNodeBlock {

    public RedstoneDuctBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.POWER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(BlockStateProperties.POWER);
        super.createBlockStateDefinition(builder);
    }

    /**
     * Keep in sync with RedstoneDuctBlockEntity#gasConnected: only the two axial faces connect.
     *
     * <p>Without this override IGasNodeBlock defaults to "connect on all faces", so a neighboring
     * duct would store SIDE on a face the redstone duct itself never accepts: the model and the
     * connection ring look connected, but the BFS can never cross that face. Both sides of an
     * edge must use the same face rule, otherwise this becomes a fake connection.
     */
    @Override
    public boolean gasConnectable(BlockState state, Direction dir) {
        return dir.getAxis() == state.getValue(BlockStateProperties.AXIS);
    }

    @Override
    public Class<RedstoneDuctBlockEntity> getBlockEntityClass() {
        return RedstoneDuctBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RedstoneDuctBlockEntity> getBlockEntityType() {
        return CWGasIndex.REDSTONE_DUCT_BE.get();
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(BlockStateProperties.POWER);
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(BlockStateProperties.POWER);
    }
}
