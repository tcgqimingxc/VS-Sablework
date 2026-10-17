package org.valkyrienskies.clockwork.content.curiosities.sensor;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * 传感器用到的 Sable 坐标换算薄封装。
 *
 * <p>旧版这些换算全部走 VS2 的 {@code Ship#toWorldCoordinates / worldToShip}；
 * 1.21.1 换成 Sable 的 {@link SubLevel#logicalPose()}（子空间本地坐标 → 世界坐标的刚体变换）。
 * 因为变换是刚体变换，距离与角度在本地/世界两套坐标下完全一致，所以传感器的"测距/测角"逻辑可以原样保留。
 */
public final class SensorSubLevels {

    private SensorSubLevels() {
    }

    /** 方块所在的子空间；不在任何子空间上（普通地形）时返回 null。 */
    @Nullable
    public static SubLevel containing(Level level, BlockPos pos) {
        return Sable.HELPER.getContaining(level, pos);
    }

    /** 子空间本地坐标 → 世界坐标；{@code subLevel} 为 null 时视为已经在世界坐标里。 */
    public static Vector3d toGlobal(@Nullable SubLevel subLevel, Vec3 local, Vector3d dest) {
        Vector3d joml = JOMLConversion.toJOML(local);
        if (subLevel == null) {
            return dest.set(joml);
        }
        return subLevel.logicalPose().transformPosition(joml, dest);
    }

    /** 世界方向 → 子空间本地方向（用于把世界速度投影回方块本地轴）。 */
    public static Vector3d toLocalDirection(@Nullable SubLevel subLevel, Vector3dc worldDirection, Vector3d dest) {
        if (subLevel == null) {
            return dest.set(worldDirection).normalize();
        }
        return subLevel.logicalPose().transformNormalInverse(worldDirection, dest).normalize();
    }

    /** 方块本地方向 → 世界方向。 */
    public static Vector3d toGlobalDirection(@Nullable SubLevel subLevel, Vector3dc localDirection, Vector3d dest) {
        if (subLevel == null) {
            return dest.set(localDirection).normalize();
        }
        return subLevel.logicalPose().transformNormal(localDirection, dest).normalize();
    }
}
