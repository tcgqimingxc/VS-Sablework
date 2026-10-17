package org.valkyrienskies.clockwork.content.curiosities;

import com.simibubi.create.content.materials.ExperienceBlock;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 游荡石矿（Wanderlite Ore）。
 *
 * <p>对应旧版 {@code curiosities/WanderliteOreBlock.kt}。两个关键行为：
 * <ol>
 *   <li>继承 Create 的 {@link ExperienceBlock} —— 采掘时掉经验 + 冒经验粒子（旧版同样继承它）；</li>
 *   <li>"游荡"：右键 / 攻击 / 踩上去（不潜行）时，把相连的游荡石矿整体变成一块**独立漂浮的结构**
 *       （旧版 {@code IWanderliteBlock.shipifyBlock} 的语义）。</li>
 * </ol>
 *
 * <p>移植差异（VS2 → Sable）：
 * <ul>
 *   <li>旧版 {@code ShipAssembler.assembleToShip(level, blocks, 1.0)} + {@code WanderShipControl} 记录每格质量；
 *       这里用 {@code SubLevelAssemblyHelper.assembleBlocks(level, anchor, blocks, bounds)} 装配成 Sable 子空间，
 *       质量由 Sable 自己的质量追踪器计算，不需要额外的数据包（旧版的 {@code data/vs_clockwork/vs_mass/}
 *       已按本次数据包迁移删除）。</li>
 *   <li>旧版的连通收集靠 {@code IWanderliteBlock} 标记接口（游荡石块 / 台阶 / 砖都实现它）；
 *       那几种方块的注册在 Lead 的 {@code index/CWBlocks} 里（本切片不能改），因此这里只把
 *       {@code WanderliteOreBlock} 自身视为连通（三种矿变体都用这个类注册即可覆盖）。</li>
 *   <li>加了 {@link #MAX_BLOCKS} 上限：旧版递归收集没有上限，踩到一大片游荡石矿会把整片地形
 *       变成漂浮结构。这里以 256 格为上限，超过就只收集到上限为止。</li>
 * </ul>
 *
 * <p><b>接线提示（给 Lead）</b>：本类尚未被 {@code index/CWBlocks} 使用 —— 那里目前把三个矿注册成普通
 * {@code Block}。要用上"掉经验 + 游荡"这两个旧版行为，请把
 * {@code WANDERLITE_DEEPSLATE_ORE / WANDERLITE_END_ORE / WANDERLITE_NYX_ORE}
 * 的工厂从 {@code Block::new} 换成 {@code WanderliteOreBlock::new}（本切片不允许改 {@code index/}）。
 */
public class WanderliteOreBlock extends ExperienceBlock {

    /** 旧版 {@code collectBlockPositions(level, blockPos, 4)} 的深度上限。 */
    private static final int MAX_DEPTH = 4;
    /** 一次"游荡"最多带走多少格（安全上限，见类注释）。 */
    private static final int MAX_BLOCKS = 256;

    public WanderliteOreBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        return shipify(serverLevel, pos) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel serverLevel) {
            shipify(serverLevel, pos);
        }
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (entity instanceof Player player && !player.isSteppingCarefully()
                && level instanceof ServerLevel serverLevel) {
            shipify(serverLevel, pos);
        }
    }

    /** 收集连通的游荡石矿并装配成独立子空间；返回是否真的装配了东西。 */
    public static boolean shipify(ServerLevel level, BlockPos origin) {
        if (!(level.getBlockState(origin).getBlock() instanceof WanderliteOreBlock)) {
            return false;
        }
        List<BlockPos> cluster = collectCluster(level, origin);
        if (cluster.isEmpty()) {
            return false;
        }
        BoundingBox3i bounds = BoundingBox3i.from(cluster);
        return SubLevelAssemblyHelper.assembleBlocks(level, cluster.get(0), cluster, bounds) != null;
    }

    /** 广度优先收集相连的游荡石矿（等价于旧版直径 4 的递归收集，但不会栈溢出且带上限）。 */
    private static List<BlockPos> collectCluster(ServerLevel level, BlockPos origin) {
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> result = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin);
        visited.add(origin);

        while (!queue.isEmpty() && result.size() < MAX_BLOCKS) {
            BlockPos current = queue.poll();
            // 深度 = 与起点的曼哈顿距离 / 相邻步数；旧版是 4 层邻接扩展，这里用步数近似同一量级。
            if (current.distManhattan(origin) > MAX_DEPTH * 2) {
                continue;
            }
            result.add(current);
            for (Direction direction : Direction.values()) {
                BlockPos neighbour = current.relative(direction);
                if (!visited.add(neighbour)) {
                    continue;
                }
                if (level.getBlockState(neighbour).getBlock() instanceof WanderliteOreBlock) {
                    queue.add(neighbour);
                }
            }
        }
        return result;
    }
}
