package org.valkyrienskies.clockwork.content.logistics.gas;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 每个维度一份的气体网络管理器。
 *
 * <p>对应旧版 Kelvin 的 {@code DuctNetwork}（全局求解器）：旧版由 Kelvin 持有全部节点与边并每刻求解；
 * 这里改为"节点自注册 + 每刻按连通域分组后做一次模拟"。
 *
 * <p>设计要点：
 * <ul>
 *   <li>节点在第一次服务端 tick 时注册；{@link #tick} 每次会剔除已卸载/被替换的节点
 *       （旧实例与 {@code level.getBlockEntity(pos)} 不一致即视为失效），因此不依赖卸载回调。</li>
 *   <li>{@link #tick} 由任意节点的 tick 驱动，用 {@code level.getGameTime()} 去重，保证每刻
 *       每个连通域只模拟一次。</li>
 *   <li>连通域用洪泛填充（BFS）划分：两侧都"愿意连通"、且至少一侧是管道时才算连通。</li>
 * </ul>
 *
 * <p><b>身份 vs 物理位置（本类最容易被改错的地方）</b>：多方块储罐整罐只有一个节点，节点身份
 * （{@link GasNode#gasPos()}）是主方块坐标，但它占据的方块有多个（{@link GasNode#gasEndpoints()}）。
 * 网络必须同时维护两张表：
 * <ul>
 *   <li>{@link #nodes}：身份 → 节点，用于遍历连通域、按身份去重；</li>
 *   <li>{@link #endpoints}：物理位置（任意一段）→ 节点，用于 {@link #nodeAt} 和找邻居。</li>
 * </ul>
 * 旧实现只有 {@code nodes}，于是"导管贴在非主方块段上"时，BFS 用物理位置找到了那一段、
 * 但边的去重/反向遍历用主方块坐标算，导致某些方向上这条边永远处理不到（见
 * {@link GasNetwork#simulate} 的注释）。现在所有遍历都从真实端点出发，边按<b>节点身份</b>去重，
 * 任意一段接导管都等价。
 */
public final class GasNetworks {

    /** 单个连通域的节点上限，防止极端建造导致单刻开销爆炸（超出部分下一 tick 继续处理）。 */
    private static final int MAX_NETWORK_NODES = 1024;

    private static final Map<ResourceKey<Level>, GasNetworks> BY_DIMENSION = new HashMap<>();

    /** 节点身份（{@link GasNode#gasPos()}）→ 节点。 */
    private final Map<BlockPos, GasNode> nodes = new HashMap<>();
    /** 物理方块位置（多方块的任意一段）→ 节点身份。 */
    private final Map<BlockPos, GasNode> endpoints = new HashMap<>();
    /** 节点身份 → 端点列表缓存；只在结构事件时整体失效，避免每刻重新扫整座罐子。 */
    private final Map<BlockPos, List<BlockPos>> endpointCache = new HashMap<>();
    private long lastTick = Long.MIN_VALUE;

    private GasNetworks() {
    }

    public static GasNetworks get(Level level) {
        return BY_DIMENSION.computeIfAbsent(level.dimension(), key -> new GasNetworks());
    }

    /**
     * 查询某物理位置上的气体节点。
     *
     * <p>先查端点表（任意一段都能命中整罐节点），再查身份表（单方块节点），未注册时回落到方块实体
     * 查询 —— 刚放置还没 tick 过的节点也要能被邻接方发现，否则会出现"放下去第一刻不导通"的问题。
     *
     * <p>回落到方块实体时若它是多方块的成员（{@code gasPos()} 指向别处），优先返回已经注册的主方块
     * 节点；否则返回它自己 —— 它的 {@code gasPos()}/{@code gasEndpoints()} 语义仍然正确，
     * BFS 的 visited 按 gasPos 去重，不会把一座罐子当成多个节点。
     *
     * <p><b>未注册且 {@code gasNodeActive()=false} 的方块返回 null</b>：这类方块不是气体网络的一部分
     * （热管、以及尚未来得及注册的储罐成员）。旧实现直接返回它自己，于是热管会被 BFS 当成节点、
     * 气体能搬进热管并从另一侧穿出 —— 热管本应"导热不搬气"。储罐成员段不受影响：它们会先命中
     * 上面的端点表；即使端点表缺失，只要控制器已注册就会在下一行被换成控制器。
     */
    @Nullable
    public static GasNode nodeAt(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        GasNetworks manager = get(level);
        GasNode node = manager.endpoints.get(pos);
        if (node == null) {
            node = manager.nodes.get(pos);
        }
        if (node != null) {
            return node;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof GasNode gasNode)) {
            return null;
        }
        GasNode controllerNode = manager.nodes.get(gasNode.gasPos());
        if (controllerNode != null) {
            return controllerNode;
        }
        return gasNode.gasNodeActive() ? gasNode : null;
    }

    /**
     * 节点的物理端点列表（带缓存）。多方块节点的端点可能很多，BFS/模拟每刻都要用，不能每次重扫。
     * 缓存只在 {@link #structureChanged} / {@link #register} / {@link #unregister} 时失效。
     */
    public static List<BlockPos> endpointsOf(Level level, GasNode node) {
        GasNetworks manager = get(level);
        return manager.endpointCache.computeIfAbsent(node.gasPos(), key -> {
            List<BlockPos> list = node.gasEndpoints();
            return list.isEmpty() ? List.of(node.gasPos()) : List.copyOf(list);
        });
    }

    public void register(GasNode node) {
        nodes.put(node.gasPos(), node);
        List<BlockPos> points = node.gasEndpoints();
        if (points.isEmpty()) {
            points = List.of(node.gasPos());
        }
        endpointCache.put(node.gasPos(), List.copyOf(points));
        for (BlockPos pos : points) {
            endpoints.put(pos, node);
        }
    }

    /**
     * 按<b>对象身份</b>删表，而不是按当前 {@code gasPos()}。
     *
     * <p>为什么不能用 {@code nodes.remove(node.gasPos())}：多方块成型后主方块换了，成员段
     * 的 {@code gasPos()} 已经指到新主方块，但它当初是以"自己那块的位置"为 key 注册的。
     * 按 gasPos 删会删到新主方块的条目、却把幽灵条目留下（旧实现的真实问题）。
     */
    public void unregister(GasNode node) {
        nodes.entrySet().removeIf(entry -> entry.getValue() == node);
        endpoints.entrySet().removeIf(entry -> entry.getValue() == node);
        endpointCache.remove(node.gasPos());
    }

    /**
     * 结构成型/拆分事件：以一次显式的"清表 + 重新注册"取代等待下一次 tick 的惰性修复。
     *
     * <p>调用时机（服务端）：{@code DuctTankBlockEntity#notifyMultiUpdated}（Create 的
     * {@code formMulti} 在结构字段写好之后对每一段回调）与 {@code removeController}。事件顺序：
     * <pre>
     *   放置方块 → onPlace → formMulti → 对每段 setController/setHeight/setWidth
     *            → notifyMultiUpdated（本方法）→ 之后才是 tick
     * </pre>
     * 如果等 tick：旧主方块仍以旧 key 挂在表里（gasPos 已指新主方块），新主方块还没注册；
     * 这一小段"半新半旧"的拓扑会被网络当成两个节点算，且幽灵条目会一直留到区块卸载。
     * 本方法把这一步提前到结构事件里，且只做一次，不是每 tick。
     */
    public void structureChanged(Level level, GasNode node) {
        List<BlockPos> scope = node.gasEndpoints();
        if (scope.isEmpty()) {
            return;
        }
        // 1) 清掉所有"身份或注册 key 落在这个物理范围内"的登记。旧主方块现在可能以
        //    key=旧位置 挂在表里（它的 gasPos 已经指到新主方块），只按 gasPos 找不到它。
        Iterator<Map.Entry<BlockPos, GasNode>> iterator = nodes.entrySet().iterator();
        List<GasNode> removed = new ArrayList<>();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, GasNode> entry = iterator.next();
            GasNode registered = entry.getValue();
            if (scope.contains(entry.getKey()) || scope.contains(registered.gasPos())) {
                removed.add(registered);
                iterator.remove();
            }
        }
        for (GasNode registered : removed) {
            endpoints.entrySet().removeIf(entry -> entry.getValue() == registered);
            registered.gasState().resetRegistration();
        }
        // 端点表里可能还留着这些位置的旧映射（例如方块被替换过）
        for (BlockPos pos : scope) {
            endpoints.remove(pos);
        }
        endpointCache.clear();

        // 2) 重新注册范围内仍然是节点的方块：储罐只有主方块 active，其余段落不注册，
        //    但它们的物理位置会作为主方块节点的端点由 register() 写进 endpoints 表。
        for (BlockPos pos : scope) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (level.getBlockEntity(pos) instanceof GasNode part) {
                part.gasState().resetRegistration();
                if (part.gasNodeActive()) {
                    part.gasState().registerNow();
                }
            }
        }
    }

    /**
     * 拆分/单段结构失效：把某个物理位置上的登记摘掉，让存活段下一 tick 重新自注册。
     *
     * <p>只在 {@code pos} 本身是节点身份（主方块）时才摘整个节点；若 {@code pos} 只是某个
     * 多方块节点的一个端点，就只删端点映射，不能把整罐的节点一起摘掉（拆一段时其它段还要用）。
     * 旧 controller 的节点会在它自己的段被拆/重新成型时由 {@link #structureChanged} 或
     * {@code prune} 处理。
     */
    public void unregisterAt(BlockPos pos) {
        GasNode node = nodes.get(pos);
        if (node == null) {
            GasNode byEndpoint = endpoints.get(pos);
            if (byEndpoint != null && byEndpoint.gasPos().equals(pos)) {
                node = byEndpoint;
            }
        }
        if (node != null) {
            unregister(node);
            node.gasState().resetRegistration();
        }
        // 无论是不是节点身份，这个位置已经不再是该节点的物理端点了
        endpoints.remove(pos);
        endpointCache.clear();
    }

    /** 由任意节点的服务端 tick 驱动；内部按游戏刻去重。 */
    public void tick(Level level) {
        if (level.isClientSide) {
            return;
        }
        long time = level.getGameTime();
        if (time == lastTick) {
            return;
        }
        lastTick = time;

        prune(level);

        Set<BlockPos> visited = new HashSet<>();
        List<GasNode> group = new ArrayList<>();
        List<GasNode> snapshot = new ArrayList<>(nodes.values());
        for (GasNode node : snapshot) {
            if (visited.contains(node.gasPos())) {
                continue;
            }
            group.clear();
            collect(level, node, visited, group);
            if (!group.isEmpty()) {
                GasNetwork.simulate(level, group);
            }
        }
    }

    /**
     * 剔除失效节点。
     *
     * <p>除了"方块实体被卸载/替换"，还要剔除：
     * <ul>
     *   <li>{@code !gasNodeActive()}：储罐成员段不再是节点（旧实现漏了这条，成员段会永久留在表里，
     *       且 {@code registered} 标志不清，等它再次成为主方块时也不会重新注册）；</li>
     *   <li>注册 key 与当前 {@code gasPos()} 不一致：主方块换了之后旧 key 必须作废。</li>
     * </ul>
     * 清掉时同步把 {@code registered} 置回 false，保证该段以后成为主方块时还能再注册。
     */
    private void prune(Level level) {
        Iterator<Map.Entry<BlockPos, GasNode>> iterator = nodes.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, GasNode> entry = iterator.next();
            GasNode node = entry.getValue();
            if (!(node instanceof BlockEntity be) || be.isRemoved() || be.getLevel() != level
                    || level.getBlockEntity(be.getBlockPos()) != be
                    || !node.gasNodeActive()
                    || !entry.getKey().equals(node.gasPos())) {
                iterator.remove();
                GasNode stale = node;
                endpoints.entrySet().removeIf(e -> e.getValue() == stale);
                endpointCache.remove(stale.gasPos());
                stale.gasState().resetRegistration();
            }
        }
        // 端点映射必须还能在身份表里找到同一个对象；结构变化后残留的旧映射在这里兜底清掉。
        endpoints.entrySet().removeIf(entry -> nodes.get(entry.getValue().gasPos()) != entry.getValue());
    }

    /** 从起点洪泛出一个连通域，写入 {@code out} 并标记 visited（按节点身份去重）。 */
    private void collect(Level level, GasNode start, Set<BlockPos> visited, List<GasNode> out) {
        List<GasNode> queue = new ArrayList<>();
        queue.add(start);
        visited.add(start.gasPos());
        for (int head = 0; head < queue.size() && out.size() < MAX_NETWORK_NODES; head++) {
            GasNode current = queue.get(head);
            out.add(current);
            for (BlockPos endpoint : endpointsOf(level, current)) {
                for (Direction dir : Direction.values()) {
                    // 连通判定必须用"真实端点"：主方块旁边的邻居和某一段旁边的邻居不是一回事。
                    if (!current.gasConnectedAt(endpoint, dir)) {
                        continue;
                    }
                    BlockPos nextPos = endpoint.relative(dir);
                    GasNode next = nodeAt(level, nextPos);
                    if (next == null || visited.contains(next.gasPos())) {
                        continue;
                    }
                    if (!next.gasConnectedAt(nextPos, dir.getOpposite())) {
                        continue;
                    }
                    // 旧版 ponder：气体机器之间必须靠管道连接
                    if (!current.isGasPipe() && !next.isGasPipe()) {
                        continue;
                    }
                    visited.add(next.gasPos());
                    queue.add(next);
                }
            }
        }
    }
}
