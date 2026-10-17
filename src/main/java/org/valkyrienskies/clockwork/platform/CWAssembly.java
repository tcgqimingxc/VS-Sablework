package org.valkyrienskies.clockwork.platform;

import com.simibubi.create.content.contraptions.AssemblyException;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import dev.simulated_team.simulated.util.SimLevelUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * 装配 / 解体链路的薄封装。
 *
 * <p>旧版用 VS2 的 {@code ShipAssembler.assembleToShip(level, blocks, scale)} 与
 * {@code VSAssemblyEvents}；1.21.1 上装配入口换成 Create: Aeronautics(Simulated) 的
 * {@link SimAssemblyHelper}（它内部调用 Sable 的 {@link SubLevelAssemblyHelper}，
 * 并负责把已经存在的 Create 装置先解体、把胶水实体搬到新位置）。
 *
 * <p>这里的三个入口对应三条不同玩法：
 * <ul>
 *   <li>{@link #assembleSingle} —— 轴承 / 螺旋桨：从方块自己面前的相邻方块开始做连通搜索（对标 SwivelBearing）；</li>
 *   <li>{@link #assembleBlockSet} —— 物理灌注器：按玩家选定的方块集合直接装配（对标旧版 {@code ShipAssembler}）；</li>
 *   <li>{@link #disassemble} —— 把子空间放回世界（对标旧版 {@code PhysBearingAssembler.moveBlocksFromTo}）。</li>
 * </ul>
 */
public final class CWAssembly {

    private CWAssembly() {
    }

    /**
     * 从单个方块开始，把与之相连的一整块结构装配成子空间。
     *
     * @param controllerPos 控制器方块自身（一般不希望被装进去）
     * @param toAssemble    搜索起点（通常是控制器朝向的相邻方块）
     * @param includeStart  是否把 {@code controllerPos} 也装进子空间
     * @param includeGlue   是否连同包裹的胶水一起搜索
     */
    public static @Nullable SimAssemblyHelper.AssemblyResult assembleSingle(final Level level, final BlockPos controllerPos,
                                                                           final BlockPos toAssemble, final boolean includeStart,
                                                                           final boolean includeGlue) throws AssemblyException {
        return SimAssemblyHelper.assembleFromSingleBlock(level, controllerPos, toAssemble, includeStart, includeGlue);
    }

    /**
     * 按显式方块集合装配（物理灌注器的"选定区域"链路）。
     *
     * <p>{@code anchor} 会成为子空间 plot 的中心方块，因此调用方通常传集合里最靠近灌注器的方块。
     * 返回的 {@link ServerSubLevel} 的世界坐标已经由 Sable 摆好（质心与新 plot 中心对齐）。
     */
    public static @Nullable ServerSubLevel assembleBlockSet(final ServerLevel level, final BlockPos anchor,
                                                            final Collection<BlockPos> blocks) {
        if (blocks.isEmpty()) {
            return null;
        }
        final BoundingBox3i bounds = BoundingBox3i.from(blocks);
        return SubLevelAssemblyHelper.assembleBlocks(level, anchor, blocks, bounds);
    }

    /** 把子空间放回世界（对标旧版 PhysBearingAssembler 的方块搬运）。 */
    public static void disassemble(final Level level, final SubLevel subLevel, final BlockPos subLevelAnchor,
                                   final BlockPos goal, final Rotation rotation, final boolean playSound) {
        // 解体前先解锁：锁是"世界 ↔ 该子空间"的固定约束，子空间没了以后句柄就成了悬空约束。
        // 对标 Aeronautics 的 PhysicsAssemblerBlockEntity:354（那里解体时也会 removeLock）。
        CWSubLevelLocks.unlock(level instanceof ServerLevel serverLevel ? serverLevel : null, subLevel);
        SimAssemblyHelper.disassembleSubLevel(level, subLevel, subLevelAnchor, goal, rotation, playSound);
    }

    /**
     * 区域是否真的加载着（装配 / 拆装前必须确认，否则会把未加载的方块搬走）。
     *
     * <p>直接用 Simulated 的实现（与 SwivelBearing 的 checkPersistence 同一判据）。
     */
    public static boolean isAreaLoaded(final Level level, final BlockPos pos, final int padding) {
        return SimLevelUtil.isAreaActuallyLoaded(level, pos, padding);
    }

    /**
     * 在子空间里找任意一个非空气方块（用作解体时的参考锚点）。
     *
     * <p>扫描方式与 {@code SimAssemblyHelper#disassembleSubLevel} 内部一致：只扫已加载的 plot 区块，
     * 因此代价与结构体积成正比，而不是与整个 plot 尺寸成正比。
     *
     * @return 子空间（plot）坐标系下的方块位置；空子空间返回 null
     */
    public static @Nullable BlockPos findAnyBlock(final Level level, final SubLevel subLevel) {
        final LevelPlot plot = subLevel.getPlot();
        for (final PlotChunkHolder chunk : plot.getLoadedChunks()) {
            final BoundingBox3ic local = chunk.getBoundingBox();
            if (local == null || local == BoundingBox3i.EMPTY) {
                continue;
            }
            for (int x = local.minX(); x <= local.maxX(); x++) {
                for (int y = local.minY(); y <= local.maxY(); y++) {
                    for (int z = local.minZ(); z <= local.maxZ(); z++) {
                        final BlockPos pos = new BlockPos(x + chunk.getPos().getMinBlockX(), y,
                                z + chunk.getPos().getMinBlockZ());
                        if (!level.getBlockState(pos).isAir()) {
                            return pos;
                        }
                    }
                }
            }
        }
        return null;
    }

    /** 把一个子空间"原地"放回世界（方块落到它当前世界位置）。 */
    public static boolean disassembleInPlace(final Level level, final SubLevel subLevel) {
        final BlockPos localAnchor = findAnyBlock(level, subLevel);
        if (localAnchor == null) {
            return false;
        }
        final BlockPos goal = BlockPos.containing(subLevel.logicalPose()
                .transformPosition(net.minecraft.world.phys.Vec3.atCenterOf(localAnchor)));
        disassemble(level, subLevel, localAnchor, goal, Rotation.NONE, true);
        return true;
    }
}
