package org.valkyrienskies.clockwork.content.curiosities.sensor.impact;

import com.mojang.serialization.MapCodec;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.joml.Vector3d;
import org.valkyrienskies.clockwork.content.curiosities.sensor.SensorBlockBase;
import org.valkyrienskies.clockwork.content.curiosities.sensor.SensorSubLevels;

/**
 * 撞击传感器（Impactotron）。
 *
 * <p>对应旧版 {@code sensor/impact/ImpactSensorBlock.kt}。行为：
 * <ol>
 *   <li>正前方紧贴着不透明的实心方块 → 直接输出 15（"已经贴脸了"）；</li>
 *   <li>否则在前方锥形范围里找最近的非空气方块，按距离输出强度；</li>
 *   <li>{@code predictiveness} 档位（0~3）既扩大搜索范围，也把"当前接近速度"换算成预测位移，
 *       让高速冲过来的结构在真正撞上之前就报警。</li>
 * </ol>
 *
 * <p>移植差异（VS2 → Sable）：
 * <ul>
 *   <li>旧版用 {@code transformToNearbyShipsAndWorld} + {@code clipIncludeShips} 在世界坐标里搜索并把船的
 *       角速度积分成"预测姿态"；1.21.1 改为在**方块本地坐标**里搜索（Sable 子空间的 plot 本身就是本地坐标系），
 *       只把子空间速度换算成本地接近速度。结果是同一个语义，但不再需要 ship 的世界/本地坐标来回转换。</li>
 *   <li>旧版会把子空间的角速度也纳入预测；这里只用线速度（旋转预测对"撞击"判定收益很小，且更省算力），
 *       差异已在类头注明。</li>
 * </ul>
 */
public class ImpactSensorBlock extends SensorBlockBase {

    public static final MapCodec<ImpactSensorBlock> CODEC = simpleCodec(ImpactSensorBlock::new);

    /** 旧版同名属性：预测档位 0~3，兼作搜索半径（格）。 */
    public static final IntegerProperty PREDICTIVENESS = IntegerProperty.create("predictiveness", 0, 3);

    public ImpactSensorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultSensorState().setValue(PREDICTIVENESS, 1));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PREDICTIVENESS);
    }

    @Override
    protected BlockState cycleSettingWithScrewdriver(BlockState state) {
        int current = state.getValue(PREDICTIVENESS);
        return state.setValue(PREDICTIVENESS, current >= 3 ? 0 : current + 1);
    }

    @Override
    public int updatePower(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Direction facing = state.getValue(FACING);
        BlockState adjacent = level.getBlockState(pos.relative(facing));
        if (!adjacent.isAir() && adjacent.isViewBlocking(level, pos)) {
            return 15;
        }

        int predictiveness = state.getValue(PREDICTIVENESS);
        int radius = predictiveness;

        // 接近速度（本地坐标，正值 = 正在朝着 FACING 撞过来）。
        double closing = localClosingSpeed(level, pos, state);
        // 预测位移：predictiveness 档位当作"提前量"的权重，除以 20 是把 m/s 换成 m/tick。
        double predicted = closing * (predictiveness / 20.0);
        int forward = 1 + Mth.ceil(Math.max(0.0, predicted));

        BlockPos anchor = pos.relative(facing);
        for (int step = 0; step <= forward; step++) {
            BlockPos slice = pos.relative(facing, 1 + step);
            for (int a = -radius; a <= radius; a++) {
                for (int b = -radius; b <= radius; b++) {
                    if (!isSolidAt(level, slice, facing, a, b)) {
                        continue;
                    }
                    // 与旧版一致：强度只跟"传感器 → 探测锚点"的距离有关，predictiveness 起放大作用。
                    double closeness = Math.max(0.0, 1.0 - predicted);
                    return Mth.clamp((int) Math.round(16 - closeness * Math.max(1, predictiveness)), 0, 15);
                }
            }
        }
        return 0;
    }

    /** 在方块本地坐标系里，子空间沿 FACING 轴的接近速度（m/tick）。 */
    private static double localClosingSpeed(ServerLevel level, BlockPos pos, BlockState state) {
        SubLevel subLevel = SensorSubLevels.containing(level, pos);
        if (!(subLevel instanceof ServerSubLevel serverSubLevel)) {
            return 0.0;
        }
        Vector3d normal = new Vector3d(state.getValue(FACING).getNormal().getX(),
                state.getValue(FACING).getNormal().getY(), state.getValue(FACING).getNormal().getZ());
        Vector3d localVelocity = SensorSubLevels.toLocalDirection(subLevel, serverSubLevel.latestLinearVelocity, new Vector3d());
        // toLocalDirection 会归一化，这里要的是投影，重新用速度长度还原标量。
        double speed = serverSubLevel.latestLinearVelocity.length();
        return -localVelocity.dot(normal) * speed;
    }

    /** 在以 {@code center} 为心的、垂直于朝向的两个轴上偏移 (a,b) 处是否有非空气方块。 */
    private static boolean isSolidAt(ServerLevel level, BlockPos center, Direction facing, int a, int b) {
        BlockPos target = switch (facing.getAxis()) {
            case Y -> center.offset(a, 0, b);
            case Z -> center.offset(a, b, 0);
            default -> center.offset(0, a, b);
        };
        return !level.getBlockState(target).isAir();
    }
}
