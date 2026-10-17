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
 */
public final class GasNetworks {

    /** 单个连通域的节点上限，防止极端建造导致单刻开销爆炸（超出部分下一 tick 继续处理）。 */
    private static final int MAX_NETWORK_NODES = 1024;

    private static final Map<ResourceKey<Level>, GasNetworks> BY_DIMENSION = new HashMap<>();

    private final Map<BlockPos, GasNode> nodes = new HashMap<>();
    private long lastTick = Long.MIN_VALUE;

    private GasNetworks() {
    }

    public static GasNetworks get(Level level) {
        return BY_DIMENSION.computeIfAbsent(level.dimension(), key -> new GasNetworks());
    }

    /**
     * 查询某位置的气体节点。
     *
     * <p>先查注册表（未加载区块直接跳过），未注册时回落到方块实体查询 —— 刚放置还没 tick 过的
     * 节点也要能被邻接方发现，否则会出现"放下去第一刻不导通"的问题。
     */
    @Nullable
    public static GasNode nodeAt(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        GasNetworks manager = get(level);
        GasNode node = manager.nodes.get(pos);
        if (node != null) {
            return node;
        }
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof GasNode gasNode ? gasNode : null;
    }

    public void register(GasNode node) {
        nodes.put(node.gasPos(), node);
    }

    public void unregister(GasNode node) {
        nodes.remove(node.gasPos(), node);
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

    private void prune(Level level) {
        Iterator<Map.Entry<BlockPos, GasNode>> it = nodes.entrySet().iterator();
        while (it.hasNext()) {
            GasNode node = it.next().getValue();
            if (!(node instanceof BlockEntity be) || be.isRemoved() || be.getLevel() != level
                    || level.getBlockEntity(be.getBlockPos()) != be) {
                it.remove();
            }
        }
    }

    /** 从起点洪泛出一个连通域，写入 {@code out} 并标记 visited。 */
    private void collect(Level level, GasNode start, Set<BlockPos> visited, List<GasNode> out) {
        List<GasNode> queue = new ArrayList<>();
        queue.add(start);
        visited.add(start.gasPos());
        for (int head = 0; head < queue.size() && out.size() < MAX_NETWORK_NODES; head++) {
            GasNode current = queue.get(head);
            out.add(current);
            BlockPos currentPos = current.gasPos();
            for (Direction dir : Direction.values()) {
                if (!current.gasConnected(dir)) {
                    continue;
                }
                BlockPos nextPos = currentPos.relative(dir);
                if (visited.contains(nextPos)) {
                    continue;
                }
                GasNode next = nodeAt(level, nextPos);
                if (next == null || !next.gasConnected(dir.getOpposite())) {
                    continue;
                }
                // 旧版 ponder：气体机器之间必须靠管道连接
                if (!current.isGasPipe() && !next.isGasPipe()) {
                    continue;
                }
                visited.add(nextPos);
                queue.add(next);
            }
        }
    }
}
