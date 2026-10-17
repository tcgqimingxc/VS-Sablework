package org.valkyrienskies.clockwork.content.curiosities.sensor.distance;

import com.mojang.serialization.MapCodec;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.clockwork.content.curiosities.sensor.CWSensorsIndex;
import org.valkyrienskies.clockwork.content.curiosities.sensor.SensorBlockBase;
import org.valkyrienskies.clockwork.content.curiosities.sensor.SensorSubLevels;

/**
 * 测距传感器（Peepotron）。
 *
 * <p>对应旧版 {@code sensor/distance/DistanceSensorBlock.kt}。行为：沿朝向射线检测，把"到命中点的距离"
 * 映射成红石强度；命中透明方块（玻璃）时继续往前，玻璃可当透镜用（{@code #vs_clockwork:sensor_lens}）。
 *
 * <p>移植差异（VS2 → Sable）：
 * <ul>
 *   <li>旧版用 {@code clipIncludeShips} 让射线能命中船体；这里改成先把起止点用子空间
 *       {@code logicalPose()} 变换到世界坐标，再用原版 {@code level.clip} 检测——在 Sable 里子空间的方块
 *       本来就位于同一 Level 的独立 plot 中并被投影到世界，所以直接用世界坐标射线即可命中地形与子空间。</li>
 *   <li>旧版把命中点从世界坐标转回船本地再加偏移；由于是刚体变换，等价于直接沿"世界朝向"推进参考点，
 *       这里按等价写法实现，少了两次无意义的坐标往返。</li>
 *   <li>蓝色/红色染色玻璃的"探测水/探测全部流体"彩蛋行为保留。</li>
 * </ul>
 */
public class DistanceSensorBlock extends SensorBlockBase {

    public static final MapCodec<DistanceSensorBlock> CODEC = simpleCodec(DistanceSensorBlock::new);

    /** 旧版同名属性：射程档位，实际射程 = 档位 * 16 - 1 格。 */
    public static final IntegerProperty MAX_DISTANCE = IntegerProperty.create("max_distance", 1, 4);

    /** 射线最多穿过多少片透镜，防止透镜阵列导致死循环。 */
    private static final int MAX_LENS_PASSES = 32;

    public DistanceSensorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultSensorState().setValue(MAX_DISTANCE, 1));
    }

    @Override
    protected MapCodec<? extends net.minecraft.world.level.block.DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MAX_DISTANCE);
    }

    @Override
    protected BlockState cycleSettingWithScrewdriver(BlockState state) {
        int current = state.getValue(MAX_DISTANCE);
        return state.setValue(MAX_DISTANCE, current >= 4 ? 1 : current + 1);
    }

    @Override
    public int updatePower(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Direction facing = state.getValue(FACING);
        BlockState adjacent = level.getBlockState(pos.relative(facing));

        boolean hasAdjacentLens = adjacent.is(CWSensorsIndex.SENSOR_LENS);
        boolean spotWater = adjacent.is(Blocks.BLUE_STAINED_GLASS);
        boolean spotAllFluids = adjacent.is(Blocks.RED_STAINED_GLASS);

        if (!adjacent.isAir() && adjacent.isViewBlocking(level, pos)) {
            return 0;
        }

        SubLevel subLevel = SensorSubLevels.containing(level, pos);
        int offset = hasAdjacentLens ? 1 : 0;
        int maxDistance = state.getValue(MAX_DISTANCE);
        double range = maxDistance * 16.0 - 1.0 + offset;

        Vec3 normal = Vec3.atLowerCornerOf(facing.getNormal());
        Vector3dc worldNormal = SensorSubLevels.toGlobalDirection(subLevel, new Vector3d(normal.x, normal.y, normal.z), new Vector3d());

        Vec3 refLocal = Vec3.atCenterOf(pos).add(normal.scale(0.5 + offset));
        Vector3d refWorld = SensorSubLevels.toGlobal(subLevel, refLocal, new Vector3d());

        for (int pass = 0; pass < MAX_LENS_PASSES; pass++) {
            Vector3d targetWorld = new Vector3d(refWorld).fma(range, worldNormal);
            ClipContext.Fluid fluid = spotAllFluids ? ClipContext.Fluid.ANY
                    : spotWater ? ClipContext.Fluid.WATER : ClipContext.Fluid.NONE;
            BlockHitResult hit = level.clip(new ClipContext(toVec(refWorld), toVec(targetWorld),
                    ClipContext.Block.COLLIDER, fluid, (Entity) null));

            if (hit.getType() == HitResult.Type.MISS) {
                return 15;
            }

            BlockState hitState = level.getBlockState(hit.getBlockPos());
            if (!hitState.is(CWSensorsIndex.SENSOR_LENS)) {
                double distance = refWorld.distance(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z);
                return Mth.clamp((int) (distance / maxDistance), 0, 15);
            }

            // 命中透镜：记录彩色玻璃的特殊语义，并把参考点推到透镜前方继续检测。
            if (hitState.is(Blocks.BLUE_STAINED_GLASS)) {
                spotWater = true;
            } else if (hitState.is(Blocks.RED_STAINED_GLASS)) {
                spotAllFluids = true;
            }
            refWorld = new Vector3d(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z).fma(1.0, worldNormal);
        }

        return 0;
    }

    private static Vec3 toVec(Vector3d v) {
        return new Vec3(v.x, v.y, v.z);
    }
}
