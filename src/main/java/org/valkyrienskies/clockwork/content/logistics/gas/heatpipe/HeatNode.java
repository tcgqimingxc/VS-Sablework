package org.valkyrienskies.clockwork.content.logistics.gas.heatpipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import org.valkyrienskies.clockwork.content.logistics.gas.GasMix;
import org.valkyrienskies.clockwork.content.logistics.gas.GasNode;

/**
 * "能被热管导热的方块实体"：温度 + 热容 + 位置。
 *
 * <p>对应官方 0.6.0（1.21.1）的 {@code HeatNode}（反编译见
 * {@code _refs/baseline_vs_clockwork_0.6.0-1.21.1.jar}，本仓库没有它的 1.20.1 版本：
 * 1.20.1 树里热管只有 blockstate/模型，没有 Java 实现）。
 *
 * <p><b>谁实现它</b>：{@link GasNode} 继承本接口，而所有气体方块实体（导管 / 储罐 / 加热器 /
 * 煤炉 / 斯特林 / 燃气机 / 压缩机 / …）都实现 {@code GasNode}，因此<b>热管接触到的每个气体节点
 * 都是热节点</b>。这不是扩大语义，而是官方本来的设计意图：官方语言文件
 * {@code block.vs_clockwork.heat_pipe.function} 写的是
 * "Conducts heat between neighboring nodes without moving gas."（在相邻<b>节点</b>之间导热，
 * 不搬运气体），但官方代码里只有热管自己实现 HeatNode，于是机器永远不参与换热——这条断链正是
 * 本次要修的"臂在、温度不动"。
 *
 * <p><b>口径</b>（实现见 {@link GasNode} 的默认方法）：
 * <ul>
 *   <li>温度 = 节点内气体温度；节点被抽空后保留最后一次的温度，视作管壁温度；</li>
 *   <li>热容 = 气体热容 + 管壁热容（{@link GasMix#nodeHeatCapacity()}，与
 *       {@code GasNetwork#conduct} 同一口径），所以换热前后 {@code Σ C·T} 精确守恒；</li>
 *   <li>位置 = <b>方块实体自己的坐标</b>，不是储罐的 {@code gasPos()}（主方块坐标）：
 *       热管可以贴在多方块储罐的任意一段上。</li>
 * </ul>
 *
 * <p>热管只与"自己的连接位为 true"（= 画了臂）的那几面换热，见
 * {@code HeatPipeBlock#getConnection}；连接判定与传热共用同一套谓词，不再各判各的。
 */
public interface HeatNode {

    /** 该节点的温度 [K]。 */
    double heatNodeTemperature();

    /** 该节点的总热容 [J/K]（气体 + 管壁）。 */
    double heatNodeCapacity();

    /** 该节点物理接触点的方块位置（用于 {@link #at} 防伪，避免读到失效/错位的引用）。 */
    BlockPos heatNodePos();

    /** 把温度直接设为给定值 [K]。 */
    void setHeatNodeTemperature(double kelvin);

    /**
     * 从世界里取某位置的 HeatNode；不是热节点（或方块实体的位置对不上）则返回 null。
     *
     * <p>位置必须严格相等：多方块储罐的成员段和主方块共享同一份 {@link GasMix}，
     * 但"接触点"是成员段自己，不能用主方块的坐标顶替。
     *
     * <p>参数用 {@link BlockGetter} 而不是 {@link Level}：热管的 {@code updateShape} 拿到的是
     * {@code LevelAccessor}（不是 {@code Level}），而判定"邻居是不是热节点"只需要
     * {@code getBlockEntity}，没必要为了类型转换把连接判定挪到别处。
     */
    static HeatNode at(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof final HeatNode node
                && node.heatNodePos().equals(pos) ? node : null;
    }

    /** 从某方向取邻居热节点。 */
    static HeatNode neighbor(Level level, BlockPos pos, Direction dir) {
        return at(level, pos.relative(dir));
    }
}
