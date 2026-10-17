package org.valkyrienskies.clockwork.content.logistics.gas.engine;

import org.valkyrienskies.clockwork.config.ClockworkConfig;

/**
 * 燃气机的"流量效率"换算（温度效率部分在 {@link HeatEngineLogic}，两者都是旧版 {@code GasEngineLogic} 的拆分）。
 *
 * <p>与旧版的唯一单位差异：旧版读 Kelvin 求解器的 {@code DuctEdge.currentFlowRate}，
 * 单位是 <b>kg/s</b>；本移植的网络是"每刻搬运质量"，得到的是 <b>kg/tick</b>。
 * 配置项保持旧版的 kg/s 写法（便于对照旧配置文件），在 {@link #flowPerTick()} 里统一除以 20 换算。
 */
public final class GasEngineLogic {

    /** 一秒的刻数：kg/s ↔ kg/tick 的换算。 */
    private static final double TICKS_PER_SECOND = 20.0;

    private GasEngineLogic() {
    }

    /** 满效率所需流量 [kg/tick]。 */
    public static double flowForFullEfficiencyPerTick() {
        return ((Number) ClockworkConfig.server().gasEngineFlowForFullEfficiency.get()).doubleValue()
                / TICKS_PER_SECOND;
    }

    /** 视为无流量的阈值 [kg/tick]。 */
    public static double minimumFlowRatePerTick() {
        return ((Number) ClockworkConfig.server().gasEngineMinimumFlowRate.get()).doubleValue()
                / TICKS_PER_SECOND;
    }

    /** 流量分档步长 [kg/tick]。 */
    public static double flowRateIncrementPerTick() {
        return ((Number) ClockworkConfig.server().gasEngineFlowRateIncrement.get()).doubleValue()
                / TICKS_PER_SECOND;
    }

    /** 燃气机的温度增量 [K]（与 {@link HeatEngineLogic} 共用配置语义，但项名不同）。 */
    public static double temperatureIncrement() {
        return ((Number) ClockworkConfig.server().gasEngineTemperatureIncrement.get()).doubleValue();
    }

    /** 温度 → 效率 0..1（公式与旧版完全一致，见 {@link HeatEngineLogic#temperatureEfficiency}）。 */
    public static float temperatureEfficiency(double temperature) {
        return HeatEngineLogic.temperatureEfficiency(temperature, temperatureIncrement());
    }

    /**
     * 流量 → 效率 0..1。
     *
     * <p>旧版 {@code GasEngineLogic.flowToEfficiency}：{@code (flow / flowForFullEfficiency).clamp(0,1)}；
     * 若 {@code flowForFullEfficiency <= 0}，则"只要有流量就满效率"。
     */
    public static float flowEfficiency(double flowPerTick, double flowForFullEfficiency) {
        if (flowForFullEfficiency <= 0) {
            return flowPerTick > 0 ? 1.0F : 0.0F;
        }
        return (float) Math.max(0.0, Math.min(1.0, flowPerTick / flowForFullEfficiency));
    }

    /**
     * 分档后的有效流量：低于阈值归零，否则向下取整到步长（旧版 {@code getEffectiveThroughput}）。
     */
    public static double effectiveThroughput(double throughput, double minimumFlowRate, double flowRateIncrement) {
        if (throughput < minimumFlowRate) {
            return 0;
        }
        if (flowRateIncrement <= 0) {
            return throughput;
        }
        return Math.floor(throughput / flowRateIncrement) * flowRateIncrement;
    }

    /** 距离"流量满效率"还差多少 kg/tick（护目镜提示用，旧版 {@code flowUntilNextTier}）。 */
    public static double flowUntilNextTier(double throughput, double flowForFullEfficiency,
                                           double minimumFlowRate, double flowRateIncrement) {
        if (flowForFullEfficiency <= 0) {
            return 0;
        }
        double raw = Math.max(0, throughput);
        double minimum = Math.max(0, minimumFlowRate);
        if (raw < minimum) {
            return minimum - raw;
        }
        double tierSize = flowRateIncrement > 0 ? flowRateIncrement : flowForFullEfficiency / HeatEngineLogic.EFFICIENCY_STEPS;
        if (tierSize <= 0) {
            return 0;
        }
        double fullTier = flowRateIncrement > 0
                ? Math.floor((flowForFullEfficiency + tierSize - 1.0E-9) / tierSize) * tierSize
                : flowForFullEfficiency;
        if (raw >= fullTier) {
            return 0;
        }
        double currentTier = Math.floor(raw / tierSize) * tierSize;
        double nextTier = Math.max(currentTier + tierSize, minimum);
        nextTier = Math.min(nextTier, fullTier);
        return Math.max(0, nextTier - raw);
    }

    /**
     * 热效率 → 应力容量（旧版 {@code roundEfficiencyForWholeStress} + {@code getEngineEfficiency} 的组合）。
     *
     * <p>旧版把它算成"整应力 / 蒸汽机基础转速"，目的是让锅炉把应力按整数分配给各引擎；
     * 本移植保留这一取整语义：
     * <pre>
     * 可用应力 = floor(效率 × 蒸汽机容量)      // 旧版 16 × capacity 是为蒸汽机转速 16 RPM 归一
     * 容量     = 可用应力 / 16
     * </pre>
     * {@code capacity = BlockStressValues.getCapacity(AllBlocks.STEAM_ENGINE)}（javap 已核实该静态方法存在）。
     */
    public static float wholeStressEfficiency(float efficiency, double steamEngineCapacity) {
        if (efficiency <= 0 || steamEngineCapacity <= 0) {
            return 0;
        }
        double atBaseSpeed = steamEngineCapacity * 16.0;
        return (float) Math.max(0.0, Math.min(efficiency,
                Math.floor(efficiency * atBaseSpeed) / atBaseSpeed));
    }
}
