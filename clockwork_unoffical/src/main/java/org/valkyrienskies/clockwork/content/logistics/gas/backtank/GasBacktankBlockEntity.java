package org.valkyrienskies.clockwork.content.logistics.gas.backtank;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.clockwork.content.logistics.gas.GasNodeBlockEntity;

/**
 * 气体背罐方块实体。
 *
 * <p>对应旧版 {@code GasBacktankBlockEntity}（一个空的 {@code KNodeBlockEntity} 子类）：
 * 它本身没有逻辑，气体状态全在节点里。本移植同样如此 —— 只是把"容量 0.75"这个旧版数值
 * 显式传给基类（见 {@link GasBacktankBlock#CAPACITY_KG}）。
 */
public class GasBacktankBlockEntity extends GasNodeBlockEntity {

    public GasBacktankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, GasBacktankBlock.CAPACITY_KG);
    }
}
