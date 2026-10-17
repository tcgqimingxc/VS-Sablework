package org.valkyrienskies.clockwork.content.logistics.gas.redstone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.valkyrienskies.clockwork.content.logistics.gas.GasNodeBlockEntity;

/**
 * 红石导管方块实体：每刻把节点压力写进方块状态的 {@code power}。
 *
 * <p>对应旧版 {@code RedstoneDuctBlockEntity}（旧版按 GUI 配置的条件给 15 或 0）。
 * 差异见 {@link RedstoneDuctBlock}：这里输出与压力成正比的模拟信号。
 *
 * <p>连接规则：只沿自身的轴向（与管道一致），并且它算"管道"，所以机器之间可以借它连通。
 */
public class RedstoneDuctBlockEntity extends GasNodeBlockEntity {

    public RedstoneDuctBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, 1.0);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof RedstoneDuctBlock)) {
            return;
        }
        int power = signalForPressure();
        if (state.getValue(BlockStateProperties.POWER) != power) {
            level.setBlockAndUpdate(getBlockPos(), state.setValue(BlockStateProperties.POWER, power));
            level.updateNeighborsAt(getBlockPos(), state.getBlock());
        }
    }

    private int signalForPressure() {
        double atmospheres = gasMix().pressure(gasCapacityKg()) / 101325.0;
        return Mth.clamp((int) atmospheres, 0, 15);
    }

    @Override
    public boolean gasConnected(Direction dir) {
        return dir.getAxis() == getBlockState().getValue(BlockStateProperties.AXIS);
    }

    @Override
    public boolean isGasPipe() {
        return true;
    }
}
