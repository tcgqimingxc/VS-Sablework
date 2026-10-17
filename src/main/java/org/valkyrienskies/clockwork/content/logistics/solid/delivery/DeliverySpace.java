package org.valkyrienskies.clockwork.content.logistics.solid.delivery;

import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.valkyrienskies.clockwork.content.curiosities.sensor.SensorSubLevels;

/**
 * 交付方块用的坐标换算：把方块坐标（子空间 plot 坐标）换算到世界坐标。
 *
 * <p>为什么需要：投掷物是真实的世界实体。方块实体坐在已装配的子空间上时，它的 {@code worldPosition}
 * 是"子空间本地坐标"，直接在那里生成实体，玩家在世界里看不到（那片区域是船坞）。
 * 旧版用 VS2 的 {@code toWorldCoordinates} 处理这件事。
 *
 * <p>实现复用 curiosities 切片的 {@link SensorSubLevels}（仓库里现成的"本地坐标 ↔ 世界坐标"封装），
 * 不再抄一遍同样的刚体变换。
 */
public final class DeliverySpace {

    private DeliverySpace() {
    }

    /** 方块中心的世界坐标；不在任何子空间上时就是普通方块坐标。 */
    public static Vec3 center(Level level, BlockPos pos) {
        final SubLevel subLevel = SensorSubLevels.containing(level, pos);
        final Vector3d world = SensorSubLevels.toGlobal(subLevel, Vec3.atCenterOf(pos), new Vector3d());
        return new Vec3(world.x, world.y, world.z);
    }

    /** 方块本地方向 → 世界方向（炮口的偏移量要跟着子空间一起转）。 */
    public static Vec3 direction(Level level, BlockPos pos, Direction dir) {
        final SubLevel subLevel = SensorSubLevels.containing(level, pos);
        final Vector3d world = SensorSubLevels.toGlobalDirection(subLevel,
                new Vector3d(dir.getStepX(), dir.getStepY(), dir.getStepZ()), new Vector3d());
        return new Vec3(world.x, world.y, world.z);
    }
}
