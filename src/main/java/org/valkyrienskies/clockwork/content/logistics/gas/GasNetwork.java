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
 * <p>每刻顺序：① 连接流动 → ② 泄漏 → ③ 与环境换热 → ④ 节点自身逻辑（{@link GasNode#afterGasTick()}）。
 */
final class GasNetwork {

    /** 单条连接每刻最多搬运源节点质量的比例（避免一步搬空造成数值振荡）。 */
    private static final double MAX_FLOW_RATIO = 0.25;

    /** 小于该流量视为 0 [kg]。 */
    private static final double MIN_FLOW = 1.0E-9;

    private GasNetwork() {
    }

    static void simulate(Level level, List<GasNode> nodes) {
        // ⓪ 清零流量计数（燃气机等机器靠它判断"这一刻有多少气穿过我"）
        for (GasNode node : nodes) {
            node.gasState().resetThroughput();
        }

        // ① 连接流动。
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
                    transfer(a, b, dir);
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
     * 在相邻两节点之间搬运气体。
     *
     * @param dir 从 a 指向 b 的方向
     */
    private static void transfer(GasNode a, GasNode b, Direction dir) {
        GasLink linkA = a.gasLink(dir);
        GasLink linkB = b.gasLink(dir.getOpposite());
        double aperture = Math.min(linkA.aperture(), linkB.aperture());
        if (aperture <= 0) {
            return;
        }

        // bias 统一到 a→b 坐标系：b 的 link 以 b→a 为正，故取负
        double biasA = linkA.bias();
        double biasB = -linkB.bias();
        boolean forbidAToB = (linkA.oneWay() && biasA < 0) || (linkB.oneWay() && biasB < 0);
        boolean forbidBToA = (linkA.oneWay() && biasA > 0) || (linkB.oneWay() && biasB > 0);

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

        double flow = Math.abs(delta) * GasPhysics.flowPerPascal() * aperture;
        GasNode from = forward ? a : b;
        GasNode to = forward ? b : a;
        double limit = MAX_FLOW_RATIO * Math.min(a.gasCapacityKg(), b.gasCapacityKg());
        flow = Math.min(flow, limit);
        flow = Math.min(flow, from.gasMix().totalMass());
        if (flow <= MIN_FLOW) {
            return;
        }

        // 滤管：连接只搬运指定气体（对应旧版 ClockworkFilteredDuctEdge）。
        // 两类过滤源合并处理：① 连接自身的 filter（滤管本侧）；② 节点级 gasFilter()（滤管两侧都算）。
        GasType filter = linkA.filter() != null ? linkA.filter() : linkB.filter();
        if (filter == null) {
            filter = a.gasFilter() != null ? a.gasFilter() : b.gasFilter();
        }
        GasMix parcel;
        if (filter != null) {
            double available = from.gasMix().massOf(filter);
            if (available <= MIN_FLOW) {
                return;
            }
            double moved = Math.min(flow, available);
            parcel = new GasMix();
            parcel.setTemperature(from.gasMix().temperature());
            parcel.addMass(filter, moved);
            from.gasMix().removeMass(filter, moved);
        } else {
            parcel = from.gasMix().extract(flow);
        }
        to.gasMix().absorb(parcel);
        from.gasChanged();
        to.gasChanged();
        // 只记在接收侧：一刻内某节点的"进 + 出"之和会重复计同一股气流，
        // 只记流入则等价于旧版 min(总流入, 总流出) 的量级（稳定流动时两者相等）。
        to.gasState().recordThroughput(flow);
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
