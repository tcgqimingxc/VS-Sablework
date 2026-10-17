package org.valkyrienskies.clockwork.content.curiosities.sensor.rotation;

import com.mojang.serialization.MapCodec;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.clockwork.content.curiosities.sensor.SensorBlockBase;
import org.valkyrienskies.clockwork.content.curiosities.sensor.SensorSubLevels;

/**
 * 陀螺传感器（Spinotron）。
 *
 * <p>对应旧版 {@code sensor/rotation/GyroscopicSensorBlock.kt}。行为：把"结构当前姿态下的朝向"与
 * "结构的静止参考朝向"之间的夹角，投影到 {@code reference_axis} 指定的轴上，输出 0~15 的双向信号
 * （正负用 {@code negative} 属性区分，两侧各有一条红石输出方向）。
 *
 * <p>若正前方是 {@link LodefocusBlockEntity}（装了指南针的透镜），则以"指向磁石的方位"作为比较基准，
 * 于是传感器变成"结构相对目标方向的偏航/俯仰表"。
 *
 * <p>移植差异（VS2 → Sable）：
 * <ul>
 *   <li>{@code ship.shipToWorld.transformDirection} 换成 {@link SubLevel#logicalPose()} 的
 *       {@code transformNormal}（语义相同：本地方向 → 世界方向）。</li>
 *   <li>旧版 {@code rotate()} 会把 {@code reference_axis} 设成**旋转前**朝向的轴（疑似笔误）；
 *       这里改成旋转后的朝向轴，并在扳手旋转时给出可预期的结果。</li>
 *   <li>{@code negative} 属性在资源文件里没有被用到（multipart 只区分 facing/reference_axis），
 *       保留它是为了与旧版红石语义（双向输出）一致。</li>
 * </ul>
 */
public class GyroscopicSensorBlock extends SensorBlockBase {

    public static final MapCodec<GyroscopicSensorBlock> CODEC = simpleCodec(GyroscopicSensorBlock::new);

    /** 旧版同名属性：参考轴。 */
    public static final EnumProperty<Direction.Axis> REFERENCE_AXIS =
            EnumProperty.create("reference_axis", Direction.Axis.class);
    /** 旧版同名属性：输出方向取反（把 15 从"正向"挪到"反向"）。 */
    public static final BooleanProperty NEGATIVE = BooleanProperty.create("negative");

    public GyroscopicSensorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultSensorState()
                .setValue(REFERENCE_AXIS, Direction.SOUTH.getClockWise().getAxis())
                .setValue(NEGATIVE, false));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(REFERENCE_AXIS, NEGATIVE);
    }

    @Override
    protected BlockState applySampledPower(BlockState state, int signedPower) {
        return state.setValue(POWER, Mth.clamp(Math.abs(signedPower), 0, 15))
                .setValue(NEGATIVE, signedPower < 0);
    }

    @Override
    protected BlockState cycleSettingWithScrewdriver(BlockState state) {
        Direction.Axis current = state.getValue(REFERENCE_AXIS);
        Direction.Axis next = switch (current) {
            case X -> Direction.Axis.Y;
            case Y -> Direction.Axis.Z;
            default -> Direction.Axis.X;
        };
        return state.setValue(REFERENCE_AXIS, next);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        Direction rotated = rotation.rotate(state.getValue(FACING));
        return state.setValue(FACING, rotated).setValue(REFERENCE_AXIS, rotated.getClockWise().getAxis());
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (state.getValue(POWER) == 0) {
            return 0;
        }
        Direction positive = positiveOutput(state.getValue(FACING));
        Direction negative = positive.getOpposite();
        if (state.getValue(NEGATIVE) ? direction == negative : direction == positive) {
            return state.getValue(POWER);
        }
        return 0;
    }

    /**
     * 旧版 {@code getOutputDirections}：朝向为上下/南北时基准轴是 X，东西时是 Z。
     *
     * <p>这正是"传感器贴在结构的哪个面上"的物理直觉——上下/南北面看到的是绕 X 的翻滚，东西面看到的是绕 Z 的俯仰。
     */
    public static Direction positiveOutput(Direction facing) {
        Direction.Axis axis = switch (facing) {
            case UP, DOWN, NORTH, SOUTH -> Direction.Axis.X;
            default -> Direction.Axis.Z;
        };
        return Direction.get(Direction.AxisDirection.POSITIVE, axis);
    }

    @Override
    public int updatePower(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        SubLevel subLevel = SensorSubLevels.containing(level, pos);
        if (subLevel == null) {
            return 0;
        }

        Direction facing = state.getValue(FACING);
        Vector3d localFacing = toVector(facing.getNormal());
        Vector3dc targetDirection = resolveTargetDirection(level, pos, subLevel, state, localFacing);
        Vector3dc transformedFacing = SensorSubLevels.toGlobalDirection(subLevel, localFacing, new Vector3d());

        Direction.Axis axis = state.getValue(REFERENCE_AXIS);
        Vector3d localReference = toVector(Direction.get(Direction.AxisDirection.POSITIVE, axis).getNormal());
        Vector3dc referenceDir = SensorSubLevels.toGlobalDirection(subLevel, localReference, new Vector3d());

        double difference = signedAngleAround(targetDirection, transformedFacing, referenceDir);
        if (!Double.isFinite(difference)) {
            return 0;
        }
        return Mth.clamp((int) Math.round(difference * 15.0), -15, 15);
    }

    /** 有透镜（Lodefocus）时用"指向磁石的方位"，否则用方块自身的静止朝向。 */
    private static Vector3dc resolveTargetDirection(ServerLevel level, BlockPos pos, SubLevel subLevel,
                                                    BlockState state, Vector3d localFacing) {
        BlockPos lodefocusPos = pos.relative(state.getValue(FACING));
        if (!(level.getBlockEntity(lodefocusPos) instanceof LodefocusBlockEntity lodefocus)) {
            return localFacing;
        }
        BlockPos target = lodefocus.getTargetPosition();
        if (target == null) {
            return localFacing;
        }
        Vector3d worldPos = SensorSubLevels.toGlobal(subLevel, Vec3.atCenterOf(pos), new Vector3d());
        Vector3d targetWorld = new Vector3d(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        Vector3d delta = targetWorld.sub(worldPos, new Vector3d());
        if (delta.lengthSquared() < 1.0E-8) {
            return localFacing;
        }
        return delta.normalize();
    }

    /**
     * 旧版 {@code Vector3d.axialDistanceTo} 的等价实现：把两个向量投影到垂直于 {@code axis} 的平面，
     * 返回"从 {@code from} 转到 {@code to}"的有符号夹角 / π。
     */
    private static double signedAngleAround(Vector3dc from, Vector3dc to, Vector3dc axis) {
        Vector3d axisNorm = new Vector3d(axis).normalize();
        if (axisNorm.lengthSquared() < 1.0E-8) {
            return 0;
        }
        Vector3d a = new Vector3d(from).fma(-from.dot(axisNorm), axisNorm);
        Vector3d b = new Vector3d(to).fma(-to.dot(axisNorm), axisNorm);
        if (a.lengthSquared() < 1.0E-8 || b.lengthSquared() < 1.0E-8) {
            return 0;
        }
        a.normalize();
        b.normalize();
        double dot = Mth.clamp(a.dot(b), -1.0, 1.0);
        double sign = a.cross(b, new Vector3d()).dot(axisNorm);
        double mul = sign > 0 ? 1.0 : sign < 0 ? -1.0 : 0.0;
        return (Math.acos(dot) / Math.PI) * mul;
    }

    private static Vector3d toVector(net.minecraft.core.Vec3i vec) {
        return new Vector3d(vec.getX(), vec.getY(), vec.getZ());
    }
}
