package org.valkyrienskies.clockwork.content.logistics.gas.duct;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.clockwork.content.logistics.gas.GasNodeBlockEntity;

/**
 * 导管方块实体：气体网络里最基础的"管道节点"。
 *
 * <p>对应旧版 {@code DuctBlockEntity}。旧版还要维护 {@code DIR_TO_CONNECTION_TYPE}（边类型）、
 * {@code edgeData} 与 {@code shouldUpdateEdges}（往 Kelvin 求解器增删边）；本移植的连接关系完全
 * 存在方块状态里，方块实体只负责把状态翻译成 {@link org.valkyrienskies.clockwork.content.logistics.gas.GasLink}。
 *
 * <p>容量：1.0 倍额定值（旧版 {@code ductVolme = 0.4 m³}）。
 */
public class GasDuctBlockEntity extends GasNodeBlockEntity {

    public GasDuctBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, 1.0);
    }

    private DuctConnectionType connection(Direction dir) {
        if (!(getBlockState().getBlock() instanceof GasDuctBlock duct)) {
            return DuctConnectionType.NONE;
        }
        return getBlockState().getValue(GasDuctBlock.DIR_TO_CONNECTION.get(dir));
    }

    @Override
    public boolean gasConnected(Direction dir) {
        return connection(dir) == DuctConnectionType.SIDE;
    }

    @Override
    public int gasLeakFaces() {
        int leaks = 0;
        for (Direction dir : Direction.values()) {
            if (connection(dir) == DuctConnectionType.LEAK) {
                leaks++;
            }
        }
        return leaks;
    }

    @Override
    public boolean isGasPipe() {
        return true;
    }
}
