package org.valkyrienskies.clockwork.content.logistics.gas;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.List;

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
     * 本节点占据的<b>全部物理方块位置</b>。单方块节点只有 {@link #gasPos()} 一个；多方块节点
     * （储罐）返回整罐所有段落。
     *
     * <p><b>为什么网络必须知道这些"端点"</b>：气体网络按方块位置查邻居
     * （{@link GasNetworks#nodeAt(Level, BlockPos)}），而多方块的节点身份只有一个（主方块坐标）。
     * 导管可以贴在任意一段上，如果网络只从主方块坐标向外找邻居，贴在非主方块段上的导管就永远
     * 不会被当成同一条边——实机表现就是"1×1 能进气，搭成 2×2/3×3 后同一根导管却抽不动"。
     * 端点列表让 {@link GasNetworks} 能从<b>每个真实方块位置</b>出发找邻居，同时仍只模拟一个节点。
     *
     * <p>只有服务端注册/建网时会调用（客户端不跑气体模拟），实现可以按需计算。
     */
    default List<BlockPos> gasEndpoints() {
        return List.of(gasPos());
    }

    /**
     * 在物理端点 {@code endpoint} 上，本节点是否愿意在 {@code dir} 面与邻居连通。
     *
     * <p>单方块节点的端点就是自己，直接复用 {@link #gasConnected(Direction)}；多方块节点必须按
     * 传进来的端点判断（同一张"段与段不互连"的规则，但邻居是端点旁边的方块，而不是主方块旁边的）。
     *
     * <p>默认实现忽略端点，因此已有节点无需改动。
     */
    default boolean gasConnectedAt(BlockPos endpoint, Direction dir) {
        return gasConnected(dir);
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
     * 本节点全部连接只允许通过的气体种类，{@code null} = 不过滤。
     *
     * <p>对应旧版 Kelvin 的 {@code ClockworkFilteredDuctEdge}（滤管按边过滤）；
     * 本移植把"节点级、与方向无关"的过滤声明放在这里（{@code filter_duct} 用它做白名单），
     * 而<b>边类型</b>带来的过滤挂在方向上（{@link #gasLink(Direction)} 携带的 {@link GasFilter}）——
     * 因为边类型是"某一面"的属性，不是整个节点的属性。
     */
    @org.jetbrains.annotations.Nullable
    default GasType gasFilter() {
        return null;
    }

    /**
     * 本节点在 {@code dir} 这一面是否允许<b>这一次</b>搬运通过（智能边类型的阈值闸）。
     *
     * <p>为什么闸门放在节点上而不是 {@link GasLink} 上：判定需要"这次搬运的压差 / 气体温度"，
     * 而 link 是在<b>搬运之前</b>按方向查出来的（压差还没算）。放在节点上就只多一次虚调用，
     * 也不用为每根管子每刻新建一个带阈值的 link 对象。
     *
     * <p>为什么要带 {@code dir}：阈值参数是<b>面级</b>配置（旧版每条边各有一套
     * {@code FilterType} / {@code comparisonValue} / {@code moreThan}，见
     * {@code smart/ClockworkSmartEdge.kt:16-19}），同一条导管的不同面可以完全不同。
     * {@code GasNetwork#transfer} 会分别以"本节点朝向对侧的那一面"问 {@code from} 与 {@code to}。
     *
     * <p>对应旧版 Kelvin {@code SmartEdge}。两侧节点都会被问一次，只有智能边会否决；
     * 其余方块用默认实现（永远放行）。
     *
     * @param dir            本节点朝向对侧的连接面
     * @param pressureDelta  有符号压差 [Pa]，<b>正 = 从本节点流向对侧</b>（阈值比较请取绝对值）
     * @param gasTemperature 将要被搬走的那股气体的温度 [K]
     */
    default boolean gasFlowGateOpen(Direction dir, double pressureDelta, double gasTemperature) {
        return true;
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
