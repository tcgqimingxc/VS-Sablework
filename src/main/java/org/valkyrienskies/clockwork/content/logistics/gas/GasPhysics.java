package org.valkyrienskies.clockwork.content.logistics.gas;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.valkyrienskies.clockwork.config.ClockworkConfig;

/**
 * 气体物理常数与环境模型。
 *
 * <p>对应旧版：旧版这些量分散在 Kelvin 求解器与 {@code ClockworkConfig.SERVER} 里，
 * 部分（空气密度）来自 VS2 的 {@code aerodynamicUtils}。
 *
 * <p>与旧版的差异（重要）：
 * <ul>
 *   <li>旧版用 VS2 的 {@code getAirDensityForY(y, dimension)} 取外部大气密度；
 *       1.21.1 没有 VS2，这里改用简单的海拔模型 {@link #ambientAirDensity}：
 *       海平面（y=64）为 1.0，每升高 1 格减少 1/256，y=320 以上视为真空。</li>
 *   <li>外部大气温度取配置项定值（旧版按维度/海拔给出）。</li>
 * </ul>
 */
public final class GasPhysics {

    /** 海平面高度 [格]，空气密度模型与压缩机取气都用它做基准。 */
    public static final int SEA_LEVEL = 64;

    /** 空气密度衰减尺度 [格]：每上升该高度，密度降低 1.0。 */
    public static final double DENSITY_FALLOFF = 256.0;

    private GasPhysics() {
    }

    /** 环境温度 [K]。 */
    public static double ambientTemperature() {
        return ClockworkConfig.server().gasAmbientTemperature.get();
    }

    /** 环境压力 [Pa]（1 atm）。 */
    public static double ambientPressure() {
        return ClockworkConfig.server().gasAmbientPressure.get();
    }

    /** 每刻与环境交换的温度差比例（0 = 绝热）。 */
    public static double ambientHeatExchange() {
        return ClockworkConfig.server().gasAmbientHeatExchange.get();
    }

    /** 每个泄漏面每刻流失的质量比例（配置项 gasPressureDecay）。 */
    public static double gasPressureDecay() {
        return ClockworkConfig.server().gasPressureDecay.get();
    }

    /** 管壁热容 [J/K]，取自旧版 {@code DuctPipeNode.heatCapacity = 44.9}。 */
    public static double wallHeatCapacity() {
        return 44.9;
    }

    /** 压差 → 质量流量系数 [kg/(tick·Pa)]。 */
    public static double flowPerPascal() {
        return ClockworkConfig.server().gasFlowPerPascal.get();
    }

    /**
     * 相邻节点之间的基础导热率 [1/tick]：每刻消掉的两侧温差比例。
     *
     * <p>对应旧版 {@code DuctNetworkUtils.configureMetalDuctEdge} 给每条金属边写的
     * {@code thermalConductivityMultiplier}：导热是连接（管壁 + 管内气体）的属性，
     * 与有没有气流无关。没有它，压力均衡后静止的支路永远到不了热源温度。
     * 详见 {@code CWServer#gasThermalConductivity}。
     */
    public static double thermalConductivity() {
        return ClockworkConfig.server().gasThermalConductivity.get();
    }

    /**
     * 外部空气密度（无量纲倍率，1.0 = 海平面）。
     *
     * @param pos 取气位置（压缩机进气口）
     */
    public static double ambientAirDensity(Level level, BlockPos pos) {
        return Mth.clamp(1.0 - (pos.getY() - SEA_LEVEL) / DENSITY_FALLOFF, 0.0, 1.0);
    }

    /**
     * 单节点额定气体容量 [kg]。
     *
     * <p>来源：配置项 {@code ductNodeCapacity}（注释为"单个管道节点可容纳的气体量"），单位按克解释，
     * 默认 1000 g = 1 kg。该质量在 1 atm / 环境温度下正好把节点填到额定压力，
     * 因此压力公式（见 {@link GasMix#pressure}）里可直接用它做归一化。
     */
    public static double nodeCapacityKg() {
        return ClockworkConfig.server().ductNodeCapacity.get() / 1000.0;
    }
}
