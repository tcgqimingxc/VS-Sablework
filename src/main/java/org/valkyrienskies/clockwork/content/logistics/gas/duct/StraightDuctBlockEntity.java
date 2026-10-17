package org.valkyrienskies.clockwork.content.logistics.gas.duct;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.clockwork.content.logistics.gas.GasNodeBlockEntity;

/**
 * 直管方块实体：只朝轴向两面连通的管道节点。
 *
 * <p>见 {@link StraightDuctBlock} 的类头注释（本方块旧版没有实现源码，属自行设计）。
 */
public class StraightDuctBlockEntity extends GasNodeBlockEntity {

    public StraightDuctBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, 1.0);
    }

    @Override
    public boolean gasConnected(Direction dir) {
        return getBlockState().getBlock() instanceof StraightDuctBlock
                && dir.getAxis() == getBlockState().getValue(StraightDuctBlock.AXIS);
    }

    @Override
    public boolean isGasPipe() {
        return true;
    }
}
