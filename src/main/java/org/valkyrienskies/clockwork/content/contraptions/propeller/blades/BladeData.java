package org.valkyrienskies.clockwork.content.contraptions.propeller.blades;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import org.valkyrienskies.clockwork.content.contraptions.propeller.blades.item.BladeItem;
import org.valkyrienskies.clockwork.content.contraptions.propeller.blades.item.BladeItems;

import java.util.ArrayList;
import java.util.List;

/**
 * 一片桨叶的气动参数（宽/窄、桨距角、展长）。
 *
 * <p>对应旧版 {@code content/contraptions/propeller/blades/BladeData.kt}
 * （Kotlin data class + {@code fromTag}，从螺旋桨 contraption 的方块 NBT 解析）。
 *
 * <p>移植差异：
 * <ul>
 *   <li>旧版要从 contraption 方块 NBT 里"反过来"读控制器的数据（控制器方块被搬进 contraption 后
 *       只剩 NBT），新架构下控制器就是子空间里的普通方块实体，螺旋桨轴承直接在内存里读它
 *       （{@code LevelPlot#getBlockEntityActors}），因此不再需要 NBT 中转，本类退化为纯数据载体。</li>
 *   <li>展长与旧版一样是"每片叶子各不相同"的：旧版读物品 NBT 的 {@code BladeLength}，
 *       这里读数据组件（{@link BladeItems#getLength}，见
 *       {@link org.valkyrienskies.clockwork.index.CWDataComponents#BLADE_LENGTH}）。
 *       宽 / 窄仍是物品的固有属性。</li>
 * </ul>
 */
public record BladeData(boolean wide, double angleDegrees, double length) {

    /** 宽叶弦长 [m]（旧版 {@code PropellerController#computeBladeForce} 的 bladeWidth 常量 0.375）。 */
    public static final double WIDE_CHORD = 0.375;
    /** 窄叶弦长 [m]（同上，0.25）。 */
    public static final double NARROW_CHORD = 0.25;

    /** 弦长 [m]。 */
    public double chord() {
        return this.wide ? WIDE_CHORD : NARROW_CHORD;
    }

    /** 叶素面积 [m²]：弦长 × 展长（旧版 {@code dA = bladeWidth * r}）。 */
    public double area() {
        return this.chord() * this.length;
    }

    /**
     * 解析控制器里的全部叶片（空槽跳过）。控制器只接受 {@link BladeItem}，
     * 所以这里的 {@code instanceof} 同时充当"非法物品过滤"。
     *
     * @param blades       控制器的 8 个叶片槽位
     * @param angleDegrees 控制器当前的桨距角 [度]，同一控制器里所有叶片共用
     */
    public static List<BladeData> of(final NonNullList<ItemStack> blades, final double angleDegrees) {
        final List<BladeData> data = new ArrayList<>(blades.size());
        for (final ItemStack stack : blades) {
            if (stack.getItem() instanceof final BladeItem blade) {
                data.add(new BladeData(blade.isWide(), angleDegrees, BladeItems.getLength(stack)));
            }
        }
        return data;
    }
}
