package org.valkyrienskies.clockwork.content.logistics.gas.heatpipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * "参与热传导的方块实体"。
 *
 * <p>热管（{@link HeatPipeBlock}）需要与相邻的<b>非气体连通</b>节点交换热量
 * （气体连通的邻居已经由气流本身带走热量了）。旧版这件事由 Kelvin 求解器统一处理，
 * 本移植用一个最小接口暴露"温度 + 热容"，避免热管去依赖具体的方块实体类型。
 */
public interface HeatNode {

    /** 该节点的温度 [K]。 */
    double heatNodeTemperature();

    /** 该节点的总热容 [J/K]（气体 + 管壁）。 */
    double heatNodeCapacity();

    /** 该节点当前的方块位置（用于自检，避免读到失效引用）。 */
    BlockPos heatNodePos();

    /** 把温度直接设为给定值 [K]。 */
    void setHeatNodeTemperature(double kelvin);

    /** 从世界里取某位置的 HeatNode；不是热节点则返回 null。 */
    static HeatNode at(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof final HeatNode node
                && node.heatNodePos().equals(pos) ? node : null;
    }

    /** 从某方向取邻居热节点。 */
    static HeatNode neighbor(Level level, BlockPos pos, Direction dir) {
        return at(level, pos.relative(dir));
    }
}
