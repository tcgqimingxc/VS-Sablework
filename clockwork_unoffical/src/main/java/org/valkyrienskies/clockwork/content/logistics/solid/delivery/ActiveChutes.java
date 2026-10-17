package org.valkyrienskies.clockwork.content.logistics.solid.delivery;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.clockwork.content.logistics.solid.delivery.chute.DeliveryChuteBlockEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 每个维度一份的「已加载交付滑道」索引，供交付加农炮找目标。
 *
 * <p>对应旧版 {@code content/logistics/solid/delivery/ActiveChutes.kt}。差异：
 * <ul>
 *   <li>旧版由滑道在 {@code tick} 里登记、在 {@code remove}/{@code destroy} 里注销，一旦区块被卸载
 *       而没走回调就会留下"幽灵条目"；这里在查询时顺手剔除失效条目（方块实体对不上就删），
 *       与 {@code GasNetworks} 的 prune 思路一致。</li>
 *   <li>旧版按 VS2 世界坐标算距离（{@code ClockworkUtils.getRealPos}）；1.21.1 的子空间里
 *       方块坐标本身就是子空间坐标，这里直接用方块坐标（与 {@code UniversalJoint} 的距离口径一致）。</li>
 * </ul>
 */
public final class ActiveChutes {

    private static final Map<ResourceKey<Level>, Set<BlockPos>> BY_DIMENSION = new HashMap<>();

    private ActiveChutes() {
    }

    public static void register(Level level, BlockPos pos) {
        if (!level.isClientSide) {
            BY_DIMENSION.computeIfAbsent(level.dimension(), key -> new HashSet<>()).add(pos);
        }
    }

    public static void unregister(Level level, BlockPos pos) {
        final Set<BlockPos> positions = BY_DIMENSION.get(level.dimension());
        if (positions != null) {
            positions.remove(pos);
        }
    }

    /**
     * 距离 {@code origin}（世界坐标）在 {@code maxDistance} 内、频率匹配的滑道，按距离升序。
     *
     * @param frequency 加农炮的频率物品；为空表示"不限频率"
     */
    public static List<BlockPos> findSorted(Level level, Vec3 origin, double maxDistance, ItemStack frequency) {
        final Set<BlockPos> positions = BY_DIMENSION.get(level.dimension());
        if (positions == null || positions.isEmpty()) {
            return List.of();
        }
        final double maxSqr = maxDistance * maxDistance;
        final List<Map.Entry<BlockPos, Double>> found = new ArrayList<>();
        final Iterator<BlockPos> iterator = positions.iterator();
        while (iterator.hasNext()) {
            final BlockPos pos = iterator.next();
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (!(level.getBlockEntity(pos) instanceof DeliveryChuteBlockEntity chute)) {
                iterator.remove();
                continue;
            }
            if (!frequency.isEmpty() && !chute.hasFrequency(frequency)) {
                continue;
            }
            // 按世界坐标比距离：滑道可能在另一条子空间上（本地坐标之间没有可比性）
            final double distanceSqr = DeliverySpace.center(level, pos).distanceToSqr(origin);
            if (distanceSqr > maxSqr) {
                continue;
            }
            found.add(Map.entry(pos, distanceSqr));
        }
        found.sort(Comparator.comparingDouble(Map.Entry::getValue));
        return found.stream().map(Map.Entry::getKey).toList();
    }
}
