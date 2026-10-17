package org.valkyrienskies.clockwork.content.logistics.gas;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 气体方块需要实现的接口，用于决定"是否愿意与某方向的邻居连通"。
 *
 * <p>对应旧版 Kelvin 的 {@code INodeBlock#canConnectTo}。区别：旧版还负责建边（{@code nodePlace} /
 * {@code nodeRemove} 里往求解器塞节点），本移植只需要回答"愿不愿意连"，建边由
 * {@link GasNetworks} 的洪泛与 {@link GasNetwork} 每刻的流动隐式完成。
 */
public interface IGasNodeBlock {

    /**
     * 该方块在 {@code dir} 面是否允许连接。
     *
     * @param state 本方块状态
     * @param dir   从本方块指向邻居的方向
     */
    default boolean gasConnectable(BlockState state, Direction dir) {
        return true;
    }
}
