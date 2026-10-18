package org.valkyrienskies.clockwork.content.logistics.gas;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 一个管道连通域的本刻模拟。
 *
 * <p>对应旧版 Kelvin 求解器里"每刻对一批 DuctNode/DuctEdge 做流体与传热"的工作，
 * 但算法完全不同：
 * <ul>
 *   <li>旧版：Kelvin 用 Jacobi/Seidel 迭代解压力场，带亚步（默认 10 substeps），
 *       流量由边的几何（半径/长度）+ 粘度算出。</li>
 *   <li>本移植：<b>不做压力场迭代</b>。每刻对每条连接按"压差 × 通径 × 系数"直接搬运质量，
 *       并把被搬运气体的热量一起带走。精度换成了简单与稳定，玩法手感（压力会传递、热量会被气流
 *       带走、泵能建立压差、阀门能节流）保持一致。</li>
 * </ul>
 *
 * <p>每刻顺序：① 连接流动 + 导热 → ② 泄漏 → ③ 与环境换热 → ④ 节点自身逻辑（{@link GasNode#afterGasTick()}）。
 *
 * <p><b>边类型（普通 / 单向 / 过滤 / 智能）的语义全部落在本类的 {@link #handleConnection} 里</b>，
 * 取证与推导见 {@code docs/DUCT-EDGE-VARIANTS.md}。四条规则各自只改一个乘数或一次否决，
 * 互不干扰，所以"现有导管/储罐行为"在任何一条边类型规则取默认值时与改动前一致。
 * 边类型是<b>面级</b>配置（存在 {@code GasDuctBlockEntity}），本类只按方向查询，
 * 不关心配置从哪来 —— 因此把边类型以后挪到别的方块上也只需实现对应的 {@link GasNode} 钩子。
 *
 * <p><b>2026-10-18 传热回归修复</b>：所有金属连接现在都有基础导热
 * （{@code gasThermalConductivity}，默认 0.1/tick），不再只有铜接头导热。原因是压力一均衡
 * 流动就停，死端节点（支管末端的斯特林机）拿不到热，实机读到 300K / 效率 0%。
 * 这条路径与边类型规则正交：它只改 {@link #conduct} 的输入，不碰单向 / 过滤 / 阈值闸的语义。
 */
final class GasNetwork {

    /** 单条连接每刻最多搬运源节点质量的比例（避免一步搬空造成数值振荡）。 */
    private static final double MAX_FLOW_RATIO = 0.25;

    /** 小于该流量视为 0 [kg]。 */
    private static final double MIN_FLOW = 1.0E-9;

    /** 小于该温差视为等温 [K]。 */
    private static final double MIN_DELTA_T = 1.0E-6;

    private GasNetwork() {
    }

    static void simulate(Level level, List<GasNode> nodes) {
        // ⓪ 清零流量计数（燃气机等机器靠它判断"这一刻有多少气穿过我"）
        for (GasNode node : nodes) {
            node.gasState().resetThroughput();
        }

        // ① 连接流动 + 导热。
        //
        // 关键：从节点的每个真实端点找邻居（见 GasNode#gasEndpoints），边的"只处理一次"按
        // **节点身份**（gasPos）去重，而不是按物理坐标。
        //
        // 旧实现按物理坐标比较（`bPos.asLong() <= aPos.asLong()` 就跳过），在单方块节点上没问题；
        // 但储罐整罐只有一个节点、身份是主方块坐标，而导管可以贴在任意一段上：
        //   导管(physical D) -- 段(physical S, gasPos = 主方块 C) -- 主方块 C
        // 从导管侧算的 pair 是 (D, S)，反向从储罐侧算的 pair 却是 (C, 邻居(C))。只要 S 与 C 不同，
        // 当 asLong(S) < asLong(D) 时导管侧跳过、储罐侧又找不到 D，这条边就**永远不会被处理**。
        // 实机表现：同一座 2×2/3×3 罐，导管接在主方块那一侧能进气、接在别的段上不进气；
        // 1×1 因为 gasPos == 物理位置，永远正常。
        // 现在两侧都从端点出发，按身份去重：无论哪边坐标大，总有一侧能枚举到这条边。
        Set<Long> seenNeighbours = new HashSet<>();
        for (GasNode a : nodes) {
            seenNeighbours.clear();
            long aIdentity = a.gasPos().asLong();
            for (BlockPos endpoint : GasNetworks.endpointsOf(level, a)) {
                for (Direction dir : Direction.values()) {
                    if (!a.gasConnectedAt(endpoint, dir)) {
                        continue;
                    }
                    BlockPos bPos = endpoint.relative(dir);
                    GasNode b = GasNetworks.nodeAt(level, bPos);
                    if (b == null || b == a) {
                        continue;
                    }
                    long bIdentity = b.gasPos().asLong();
                    if (bIdentity == aIdentity) {
                        continue;
                    }
                    // 每对节点只处理一次；同一节点有多个端点贴着同一个邻居时也只会走一次。
                    if (bIdentity <= aIdentity || !seenNeighbours.add(bIdentity)) {
                        continue;
                    }
                    if (!b.gasConnectedAt(bPos, dir.getOpposite())) {
                        continue;
                    }
                    if (!a.isGasPipe() && !b.isGasPipe()) {
                        continue;
                    }
                    handleConnection(a, b, dir);
                }
            }
        }

        // ②③④ 泄漏、换热、机器自身逻辑
        for (GasNode node : nodes) {
            leak(node);
            equalizeWithAmbient(node);
            node.afterGasTick();
        }
    }

    /**
     * 处理一条相邻连接：先搬质量（受方向闸 / 过滤 / 阈值闸约束），再导热。
     *
     * <p>通径取两侧最小（最窄的一侧是瓶颈）；通流能力与导热取两侧最大（接口件不是节流件）。
     * 导热放在 {@code aperture > 0} 里面：全关的连接（阀门）既是气路断路，也是热路断路，
     * 否则“关阀”之后下游还会被导热慢慢加热，玩家无法用阀门把一段管彻底隔开。
     *
     * @param dir 从 a 指向 b 的方向（也是 a 这一侧 link 的方向）
     */
    private static void handleConnection(GasNode a, GasNode b, Direction dir) {
        GasLink linkA = a.gasLink(dir);
        GasLink linkB = b.gasLink(dir.getOpposite());

        double aperture = Math.min(linkA.aperture(), linkB.aperture());
        double flowScale = Math.max(linkA.flowScale(), linkB.flowScale());
        if (aperture > 0) {
            transfer(a, b, dir, linkA, linkB, aperture, flowScale);
            conduct(a, b, Math.max(linkA.conductivity(), linkB.conductivity()));
        }
    }

    /**
     * 在相邻两节点之间搬运气体。
     *
     * <p>方向约定（本类的核心，改之前先读）：{@code forward = (a→b)}。
     * 两侧 link 的 bias / 方向闸都以"自己这一侧"为正，所以 b 的 link 要先翻到 a→b 坐标系。
     */
    private static void transfer(GasNode a, GasNode b, Direction dir, GasLink linkA, GasLink linkB,
                                 double aperture, double flowScale) {
        // bias 统一到 a→b 坐标系：b 的 link 以 b→a 为正，故取负
        double biasA = linkA.bias();
        double biasB = -linkB.bias();

        // 泵：oneWay + bias 方向（只允许沿 bias 推动的方向流动）
        boolean forbidAToB = (linkA.oneWay() && biasA < 0) || (linkB.oneWay() && biasB < 0);
        boolean forbidBToA = (linkA.oneWay() && biasA > 0) || (linkB.oneWay() && biasB > 0);
        // 单向边类型：link 声明"只出不进 / 只进不出"。注意两侧都要看 ——
        // a 侧"只出不进"约束的是 b→a，b 侧"只出不进"约束的是 a→b。
        forbidAToB |= linkA.flow() == GasLink.Flow.IN || linkB.flow() == GasLink.Flow.OUT;
        forbidBToA |= linkA.flow() == GasLink.Flow.OUT || linkB.flow() == GasLink.Flow.IN;

        double pressureA = a.gasMix().pressure(a.gasCapacityKg());
        double pressureB = b.gasMix().pressure(b.gasCapacityKg());
        double delta = pressureA - pressureB + biasA + biasB;
        if (delta == 0) {
            return;
        }
        boolean forward = delta > 0;
        if (forward ? forbidAToB : forbidBToA) {
            return;
        }

        GasNode from = forward ? a : b;
        GasNode to = forward ? b : a;

        // 智能边类型的阈值闸：拿"这次真的要流动的那股气"去问两侧节点，
        // 并且要问**正确的那个面**（阈值是面级配置；见 GasNode#gasFlowGateOpen）。
        Direction dirFrom = forward ? dir : dir.getOpposite();
        Direction dirTo = dirFrom.getOpposite();
        double gasTemperature = from.gasMix().temperature();
        if (!from.gasFlowGateOpen(dirFrom, delta, gasTemperature)
                || !to.gasFlowGateOpen(dirTo, -delta, gasTemperature)) {
            return;
        }

        double flow = Math.abs(delta) * GasPhysics.flowPerPascal() * aperture * flowScale;
        double limit = MAX_FLOW_RATIO * Math.min(a.gasCapacityKg(), b.gasCapacityKg());
        flow = Math.min(flow, limit);
        flow = Math.min(flow, from.gasMix().totalMass());
        if (flow <= MIN_FLOW) {
            return;
        }

        // 气体种类过滤：① 连接自带的（过滤边类型，GasLink.filter）；② 节点级 gasFilter()（滤管）。
        // 一条连接只认第一个非空的过滤条件：面级边类型建议只配接缝的一侧（见文档 §3），
        // 两侧都配时以 a 侧为准，避免"两个过滤条件谁优先"变成不可解释的行为。
        GasFilter filter = linkA.filter() != null ? linkA.filter() : linkB.filter();
        if (filter == null) {
            GasType nodeFilter = a.gasFilter() != null ? a.gasFilter() : b.gasFilter();
            if (nodeFilter != null) {
                filter = GasFilter.only(nodeFilter);
            }
        }

        GasMix parcel;
        if (filter == null) {
            parcel = from.gasMix().extract(flow);
        } else if (filter.blacklist()) {
            // 黑名单：把被拦气体留在源节点，只搬其余组分（对应旧版 blacklist = true）
            double available = from.gasMix().totalMass() - from.gasMix().massOf(filter.gas());
            if (available <= MIN_FLOW) {
                return;
            }
            parcel = from.gasMix().extract(Math.min(flow, available), filter.gas());
        } else {
            // 白名单：只搬指定气体（对应旧版 filter 集合里只有一种气的情况）
            double available = from.gasMix().massOf(filter.gas());
            if (available <= MIN_FLOW) {
                return;
            }
            double moved = Math.min(flow, available);
            parcel = new GasMix();
            parcel.setTemperature(from.gasMix().temperature());
            parcel.addMass(filter.gas(), moved);
            from.gasMix().removeMass(filter.gas(), moved);
        }

        double moved = parcel.totalMass();
        if (moved <= MIN_FLOW) {
            return;
        }
        to.gasMix().absorb(parcel);
        from.gasChanged();
        to.gasChanged();
        // 只记在接收侧：一刻内某节点的"进 + 出"之和会重复计同一股气流，
        // 只记流入则等价于旧版 min(总流入, 总流出) 的量级（稳定流动时两者相等）。
        // 记的是**实际搬走的质量**（过滤/黑名单会少于请求量），否则护目镜会虚报流量。
        to.gasState().recordThroughput(moved);
    }

    /**
     * 相邻两节点之间的导热（对应旧版 {@code DuctEdge.thermalConductivityMultiplier}）。
     *
     * <p><b>为什么需要单独的导热项</b>：质量搬运只带走“被搬走的那份气体”的热量。压力一旦均衡流动就停，
     * 不在流路上的节点（支管末端的斯特林机、死端储罐）再也拿不到热 —— 实机表现就是
     * “煤炉 850K、泵 600K，几格外的斯特林只有 300K”。旧版给<b>每条金属边</b>都写了
     * {@code ductThermalConductivityMultiplier}（默认 1000），移植早期只给铜接头加了导热，
     * 普通连接的 {@code conductivity} 恒为 0，于是整张网只剩“随气流带走的热”一条路。
     *
     * <p><b>导热率</b>：{@code max(gasThermalConductivity, 边件 conductivity)}。
     * 基础值来自配置（见 {@code CWServer#gasThermalConductivity}），铜接头可以用更大的值覆盖。
     * {@code k} 的物理含义是“每刻消掉的两侧温差比例”（0 = 绝热，1 = 一 tick 均温）。
     *
     * <p><b>数学</b>：两侧节点热容 C₁、C₂（气体 + 管壁，{@link GasMix#nodeHeatCapacity()}）、温差 ΔT。
     * 取从热侧搬走的热量
     * <pre>Q = k·ΔT·C₁C₂/(C₁+C₂)      // C₁=C₂=C 时 Q = k·ΔT·C/2</pre>
     * 两侧温度各走 {@code ±Q/C}，温差乘上 {@code (1-k)}。因为两侧用的是同一个 Q，
     * 节点总热能 {@code C·T} 守恒；C 越大温度变化越小，即“大质量/大热容节点升温更慢”。
     *
     * <p>没有气体的节点（真空）跳过：此时 {@code heatCapacity()=0}，只剩管壁，
     * 让它参与导热会把热量沿着空管“瞬移”到远处，与“气体网络”的语义不符。
     */
    private static void conduct(GasNode a, GasNode b, double linkConductivity) {
        double conductivity = Math.max(GasPhysics.thermalConductivity(), linkConductivity);
        if (conductivity <= 0) {
            return;
        }
        GasMix mixA = a.gasMix();
        GasMix mixB = b.gasMix();
        if (mixA.isEmpty() || mixB.isEmpty()) {
            return;
        }
        double deltaT = mixB.temperature() - mixA.temperature();
        if (Math.abs(deltaT) <= MIN_DELTA_T) {
            return;
        }
        double heatA = mixA.nodeHeatCapacity();
        double heatB = mixB.nodeHeatCapacity();
        if (heatA <= 0 || heatB <= 0) {
            return;
        }
        double joules = conductivity * deltaT * (heatA * heatB / (heatA + heatB));
        mixA.setTemperature(mixA.temperature() + joules / heatA);
        mixB.setTemperature(mixB.temperature() - joules / heatB);
        a.gasChanged();
        b.gasChanged();
    }

    /**
     * 泄漏：每个泄漏面按配置比例流失质量（旧版 {@code DuctPipeNode.getLeakRatio} = 泄漏面 / 6）。
     */
    private static void leak(GasNode node) {
        int leakFaces = node.gasLeakFaces();
        GasMix mix = node.gasMix();
        if (leakFaces <= 0 || mix.isEmpty()) {
            return;
        }
        double ratio = Math.min(1.0, leakFaces / 6.0) * GasPhysics.gasPressureDecay();
        if (ratio <= 0) {
            return;
        }
        mix.scale(1.0 - ratio);
        node.gasChanged();
    }

    /** 与环境换热：按配置比例把温度拉向环境温度（多方块节点按 {@link GasNode#gasAmbientExchangeScale()} 分摊）。 */
    private static void equalizeWithAmbient(GasNode node) {
        double exchange = GasPhysics.ambientHeatExchange() * node.gasAmbientExchangeScale();
        GasMix mix = node.gasMix();
        if (exchange <= 0 || mix.isEmpty()) {
            return;
        }
        double ambient = GasPhysics.ambientTemperature();
        double delta = (ambient - mix.temperature()) * exchange;
        if (delta == 0) {
            return;
        }
        mix.setTemperature(mix.temperature() + delta);
        node.gasChanged();
    }
}
