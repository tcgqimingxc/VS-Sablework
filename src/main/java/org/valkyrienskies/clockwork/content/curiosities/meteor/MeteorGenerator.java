package org.valkyrienskies.clockwork.content.curiosities.meteor;

import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import org.valkyrienskies.clockwork.index.CWBlocks;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 陨石生成器（metaball 算法）。
 *
 * <p>对应旧版 {@code curiosities/meteor/MeteorGenerator.kt}：以若干"金属球"（metaball）为中心，
 * 用 metaball 场 + Perlin 噪声扰动决定每个方块是空气 / 陨星岩 / 游荡石陨星岩矿。
 *
 * <p>移植差异：
 * <ul>
 *   <li>旧版 {@code metaBall()} 里写的是整数除法 {@code 1 / ball.distSqr(pos)}，当 pos 正好落在某个
 *       ball 上时 {@code distSqr == 0} → 会抛 {@code ArithmeticException}（也就是说旧版这个调试方块
 *       一放就会崩服）。这里改成 {@code 1.0 / max(1, distSqr)}，是修正而不是行为变更。</li>
 *   <li>密度取自旧版配置 {@code ClockworkConfig.SERVER.meteor_density}（默认 0.0，等于整颗都是矿）。
 *       新项目的 {@code config/CWServer} 里没有这一项且本切片不能改 config，因此用常量
 *       {@link #METEOR_DENSITY} = 0.5（矿脉只占内核），更接近"陨石"的观感。</li>
 *   <li>旧版靠 VS2 {@code ShipAssembler} 先把落点装配成船再填方块；这里只负责"填方块"，
 *       装配交给 {@link MeteorTestBlock} 用 Sable 的 {@code SubLevelAssemblyHelper} 完成。</li>
 *   <li>{@code PerlinNoise.create} 在 1.21.1 收的是 {@code IntStream} 而不是 List，八度数写法不变。</li>
 * </ul>
 */
public final class MeteorGenerator {

    /** 旧版 {@code val resolution = 0.25}。 */
    private static final double RESOLUTION = 0.25;
    /** 见类注释：旧版默认 0.0（全矿石），这里取 0.5。 */
    private static final double METEOR_DENSITY = 0.5;
    /** 内核掺入充能陨星岩的比例（旧版 MeteorRenderer 里有对应的"发光核心"设定）。 */
    private static final float CHARGED_NYX_CHANCE = 0.08f;

    private MeteorGenerator() {
    }

    /**
     * 生成一颗陨石并直接写入世界。
     *
     * @param threshold      metaball 阈值（旧版调用点传 0.035）
     * @param maxDistance    metaball 相对中心的随机散布半径（旧版传 10）
     * @param metaBallsCount metaball 数量（旧版传 3）
     * @return 实际被填成实心方块的坐标列表（供装配成子空间用）
     */
    public static List<BlockPos> generate(ServerLevel level, BlockPos center, double threshold,
                                          int maxDistance, int metaBallsCount) {
        PerlinNoise noise = PerlinNoise.create(RandomSource.create(level.getSeed()), IntStream.of(1, 1));
        RandomSource random = level.getRandom();

        List<BlockPos> balls = new ArrayList<>();
        balls.add(center);
        for (int i = 1; i < metaBallsCount; i++) {
            balls.add(randomizedPos(random, center, maxDistance));
        }

        BoundingBox3i bounds = BoundingBox3i.from(balls);
        int padding = (int) (0.5 / threshold);
        List<BlockPos> placed = new ArrayList<>();

        for (int x = bounds.minX - padding; x <= bounds.maxX + padding; x++) {
            for (int y = bounds.minY - padding; y <= bounds.maxY + padding; y++) {
                for (int z = bounds.minZ - padding; z <= bounds.maxZ + padding; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    double perturbed = threshold - noise.getValue(x * RESOLUTION, y * RESOLUTION, z * RESOLUTION) / 500.0;
                    double field = metaBall(balls, pos);
                    BlockState state = chooseState(level, pos, field, perturbed);
                    if (state == null) {
                        continue;
                    }
                    // 旧版用 level.getChunk(pos).setBlockState(pos, state, false)：不触发方块更新，
                    // 免得几千次邻接更新把服务器卡住。1.21.1 的签名一致，照抄。
                    level.getChunkAt(pos).setBlockState(pos, state, false);
                    placed.add(pos);
                }
            }
        }
        return placed;
    }

    private static BlockState chooseState(ServerLevel level, BlockPos pos, double field, double threshold) {
        if (field > METEOR_DENSITY * threshold) {
            return CWBlocks.WANDERLITE_NYX_ORE.get().defaultBlockState();
        }
        if (field > threshold) {
            // 内核掺一点充能陨星岩，纯外观（旧版渲染器里的"发光核心"）。
            if (level.getRandom().nextFloat() < CHARGED_NYX_CHANCE) {
                return CWBlocks.CHARGED_NYX.get().defaultBlockState();
            }
            return CWBlocks.NYX.get().defaultBlockState();
        }
        return null; // 空气：不改动世界
    }

    private static BlockPos randomizedPos(RandomSource random, BlockPos center, int maxDistance) {
        return center.offset(random.nextInt(maxDistance * 2 + 1) - maxDistance,
                random.nextInt(maxDistance * 2 + 1) - maxDistance,
                random.nextInt(maxDistance * 2 + 1) - maxDistance);
    }

    /** metaball 场：所有球对当前位置的 1/r² 之和。 */
    private static double metaBall(List<BlockPos> balls, BlockPos pos) {
        double sigma = 0.0;
        for (BlockPos ball : balls) {
            sigma += 1.0 / Math.max(1.0, ball.distSqr(pos));
        }
        return sigma;
    }
}
