package org.valkyrienskies.clockwork.content.logistics.gas;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * 一个气体网络节点（由方块实体实现）。
 *
 * <p>对应旧版 Kelvin 的 {@code DuctNode} + {@code INodeBlockEntity} 这一对概念：
 * 旧版把"物理状态"放在求解器的 DuctNode 里，方块实体只是它的把手；
 * 本移植把状态直接放在方块实体里的 {@link GasNodeState}，不再有独立的求解器对象。
 *
 * <p>所有可选行为都给了默认实现，实现类通常只需要提供 {@link #gasState()}。
 */
public interface GasNode {

    /** 节点状态（气体内容 + 容量），实现类各自持有一份。 */
    GasNodeState gasState();

    /**
     * 该方块实体当前是否代表一个有效的节点。
     *
     * <p>多方块（储罐）只有主方块算节点，其余段落返回 false，避免它们去顶替主方块在网络里的位置。
     */
    default boolean gasNodeActive() {
        return true;
    }

    default GasMix gasMix() {
        return gasState().mix();
    }

    /** 该节点的额定气体容量 [kg]，决定压力（见 {@link GasMix#pressure}）。 */
    default double gasCapacityKg() {
        return gasState().capacityKg();
    }

    default BlockPos gasPos() {
        return gasState().hostPos();
    }

    default Level gasLevel() {
        return gasState().hostLevel();
    }

    /**
     * 本节点是否在 {@code dir} 面与邻居连通。分组与流动都要求两侧同时成立。
     *
     * <p>默认行为：查询本方块是否为 {@link IGasNodeBlock}，是则由方块决定（管道看连接状态，
     * 机器默认全连通）。管道方块会覆盖本方法以读取 6 个连接属性。
     */
    default boolean gasConnected(Direction dir) {
        return gasState().blockAllowsConnection(dir);
    }

    /** {@code dir} 面的连接参数，默认普通管道。 */
    default GasLink gasLink(Direction dir) {
        return GasLink.PIPE;
    }

    /**
     * 本节点是否算"管道"。
     *
     * <p>沿用旧版 ponder 的规则（"气体机器之间只能用管道连接"）：两个节点要连通，
     * 至少一侧必须是管道类方块（导管 / 泵 / 阀门 / 红石导管）。罐与机器之间必须夹一段导管。
     */
    default boolean isGasPipe() {
        return false;
    }

    /**
     * 本节点参与的所有连接只允许通过的气体种类，{@code null} = 不过滤。
     *
     * <p>对应旧版 Kelvin 的 {@code ClockworkFilteredDuctEdge}（滤管按边过滤）；
     * 本移植把过滤声明放在节点上，{@link GasNetwork} 在搬运时统一裁决，
     * 这样"滤管两侧"都受约束，不会出现从滤管背面塞进杂气的情况。
     */
    @org.jetbrains.annotations.Nullable
    default GasType gasFilter() {
        return null;
    }

    /**
     * 本节点处于"泄漏"状态的连接面数量（0..6）。
     *
     * <p>对应旧版 {@code DuctPipeNode.getLeakRatio(level)}：泄漏面比例 = 泄漏面数 / 6，
     * 每刻按该比例 × {@code gasPressureDecay} 流失气体。管道被爆炸炸到时会变成泄漏面。
     */
    default int gasLeakFaces() {
        return 0;
    }

    /**
     * 本节点参与"与环境换热"的权重（默认 1.0）。
     *
     * <p>给多方块节点用：储罐的每一段都注册成节点、却共享同一份气体（{@link GasMix}），
     * 若每段都按整份权重换热，5 层罐子就会以 5 倍速度趋近环境温度。让每段返回 1/块数，
     * 整罐合计仍然是 1，等价于旧版"整罐一个节点"。
     */
    default double gasAmbientExchangeScale() {
        return 1.0;
    }

    /** 气体状态被修改后回调，用于标脏与同步。 */
    void gasChanged();

    /** 每刻流动与换热处理完成后调用，供机器做自身逻辑（默认什么都不做）。 */
    default void afterGasTick() {
    }
}
