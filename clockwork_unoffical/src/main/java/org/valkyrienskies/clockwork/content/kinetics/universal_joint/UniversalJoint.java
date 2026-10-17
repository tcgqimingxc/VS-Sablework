package org.valkyrienskies.clockwork.content.kinetics.universal_joint;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * "万向节"：两个方块实体之间可以建立一条远距离连接（通用轴 / 伸缩杆共用）。
 *
 * <p>对应旧版 {@code org.valkyrienskies.clockwork.util.universal_joint.IUniversalJoint}。
 *
 * <p>与旧版的差异：旧版用 VS2 的船坐标（{@code toWorldCoordinates}）计算连接长度，
 * 这样跨越两条船的两个接头也能正确比较距离；1.21.1 里结构是 Sable 的子空间，
 * 这里只比较<b>同一 Level 内的方块坐标</b>距离，跨子空间的连接（以及相应的物理约束）
 * 留给 platform/ 层去做，本切片不引入 Sable 依赖。
 */
public interface UniversalJoint {

    /** 已连接的另一端，未连接时为 null。 */
    @Nullable
    UniversalJoint getConnectedJoint();

    /** 本方块实体的坐标，用于距离判断与序列化。 */
    BlockPos getJointPos();

    /** 允许建立连接的最大距离（格）。 */
    double getMaxCreationDistance();

    /** 建立双向连接。实现方负责处理动力学/物理侧的重连与数据同步。 */
    void connectTo(UniversalJoint other);

    /** 断开双向连接。 */
    void disconnect();

    /** 断开连接时返还给玩家的物品。 */
    ItemStack getConnectionItem();

    /** 只有同类接头才能互连（旧版比较 {@code javaClass}）。 */
    default boolean isSameKind(UniversalJoint other) {
        return other.getClass() == getClass();
    }

    /**
     * 尝试把 {@code otherPos} 处的接头与本方连接。
     *
     * @return 是否真的建立了连接（任一端已有连接、类型不符、目标不是接头都返回 false）
     */
    default boolean tryConnect(Level level, BlockPos otherPos) {
        if (getConnectedJoint() != null) {
            return false;
        }
        if (!(level.getBlockEntity(otherPos) instanceof UniversalJoint other)) {
            return false;
        }
        if (other.getConnectedJoint() != null || !isSameKind(other)) {
            return false;
        }
        connectTo(other);
        return true;
    }
}
