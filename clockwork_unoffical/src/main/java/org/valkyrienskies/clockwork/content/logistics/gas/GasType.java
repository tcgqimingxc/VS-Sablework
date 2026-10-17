package org.valkyrienskies.clockwork.content.logistics.gas;

import net.minecraft.resources.ResourceLocation;

/**
 * 气体类型（不可变定义）。
 *
 * <p>对应旧版 {@code org.valkyrienskies.kelvin.api.GasType}（Kelvin 库提供）。1.21.1 没有 Kelvin，
 * 因此这里自定义一份精简版：只保留气体网络真正需要的物理量，去掉了粘度 / 萨瑟兰常数 / 绝热指数
 * 等旧版求解器内部才用得到的参数（本移植用不到）。
 *
 * <p>数值来源：旧版 {@code ClockworkGasses.kt}（vs_clockwork 的 8 种气体）+ Kelvin 的 {@code kelvin:air}。
 * 单位：
 * <ul>
 *   <li>{@code density} —— 标准状态密度 [kg/m³]</li>
 *   <li>{@code specificHeat} —— 定压比热容 [J/(kg·K)]（旧版 specificHeatCapacity）</li>
 * </ul>
 *
 * <p>{@code index} 由 {@link GasTypes} 在注册时分配，用作 {@link GasMix} 里质量数组的下标，
 * 避免在每刻模拟里做 Map 查找。
 */
public final class GasType {

    private final int index;
    private final ResourceLocation id;
    private final String displayName;
    private final double density;
    private final double specificHeat;
    private final int color;

    GasType(int index, ResourceLocation id, String displayName, double density, double specificHeat, int color) {
        this.index = index;
        this.id = id;
        this.displayName = displayName;
        this.density = density;
        this.specificHeat = specificHeat;
        this.color = color;
    }

    public int index() {
        return index;
    }

    public ResourceLocation id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** 标准状态密度 [kg/m³]。 */
    public double density() {
        return density;
    }

    /** 定压比热容 [J/(kg·K)]。 */
    public double specificHeat() {
        return specificHeat;
    }

    /** 摩尔质量 [kg/mol]，由标准摩尔体积 22.414 L/mol 换算：M = ρ · 0.022414。 */
    public double molarMass() {
        return density * 0.022414;
    }

    /** 护目镜 / 粒子用的颜色（0xRRGGBB）。 */
    public int color() {
        return color;
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
