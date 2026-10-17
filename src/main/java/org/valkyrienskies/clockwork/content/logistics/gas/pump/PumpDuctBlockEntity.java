package org.valkyrienskies.clockwork.content.logistics.gas.pump;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.clockwork.content.logistics.gas.GasKineticNodeBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.GasLink;

/**
 * 气体泵方块实体。
 *
 * <p>对应旧版 {@code PumpDuctBlockEntity}。旧版 {@code pumpPressure = (|speed| / 256) * maxPumpPressure}，
 * 并把结果写到两侧的 {@code PumpDuctEdge} 上；这里把同一个公式放进 {@link #gasLink(Direction)}，
 * 由网络每刻查询，效果等价且不用维护边的生命周期。
 */
public class PumpDuctBlockEntity extends GasKineticNodeBlockEntity {

    /** 旧版 {@code PumpDuctBlockEntity.maxPumpPressure}：满转速时的泵压 [Pa]。 */
    public static final double MAX_PUMP_PRESSURE = 1023440.0;

    /** 达到最大泵压所需的转速 [RPM]（旧版硬编码 256）。 */
    private static final double MAX_PUMP_RPM = 256.0;

    public PumpDuctBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, 1.0);
    }

    /** 当前泵压 [Pa]。 */
    public double pumpPressure() {
        return Math.min(Math.abs(getSpeed()) / MAX_PUMP_RPM, 1.0) * MAX_PUMP_PRESSURE;
    }

    @Override
    public boolean gasConnected(Direction dir) {
        if (!(getBlockState().getBlock() instanceof PumpDuctBlock pump)) {
            return false;
        }
        return pump.gasConnectable(getBlockState(), dir);
    }

    @Override
    public GasLink gasLink(Direction dir) {
        if (!(getBlockState().getBlock() instanceof PumpDuctBlock pump)) {
            return GasLink.CLOSED;
        }
        if (!pump.gasConnectable(getBlockState(), dir)) {
            return GasLink.CLOSED;
        }
        double pressure = pumpPressure();
        Direction facing = getBlockState().getValue(PumpDuctBlock.FACING);
        // 出气侧给正压差（泵 → 前方），进气侧给负压差（后方 → 泵）：两侧合起来把气体往前推。
        // 两侧都是单向的：气体只能沿泵送方向走，不能倒流。
        return dir == facing
                ? new GasLink(1.0, pressure, true)
                : new GasLink(1.0, -pressure, true);
    }

    @Override
    public boolean isGasPipe() {
        return true;
    }
}
