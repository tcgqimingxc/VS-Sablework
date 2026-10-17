package org.valkyrienskies.clockwork.content.logistics.gas.engine;

import net.minecraft.util.Mth;

/**
 * 热气机的效率换算。
 *
 * <p>直接对应旧版 {@code GasEngineLogic} 里与温度有关的部分（旧版还包括"流量效率"，
 * 那是给燃气机用的；斯特林机只看温度，见旧版 {@code SterlingEngineBlockEntity}）。
 *
 * <p>规则（旧版原样）：
 * <pre>
 * 档位 = floor((T - 290K) / 温度增量)          // 温度增量默认 60K
 * 效率 = clamp(档位 / 20, 0, 1)                // 20 档满效率
 * 转速倍率 = 1 + (效率 ≥ 1 ? 3 : min(2, floor(效率 × 4)))   // 1x / 2x / 3x / 4x
 * </pre>
 */
public final class HeatEngineLogic {

    /** 旧版 {@code GasEngineLogic.TEMPERATURE_OFFSET}：效率起算温度 [K]。 */
    public static final double TEMPERATURE_OFFSET = 290.0;

    /** 旧版 {@code GasEngineLogic.BAR_SEGMENTS}：满效率所需档位数。 */
    public static final int EFFICIENCY_STEPS = 20;

    /** 斯特林机的基础转速 [RPM]（旧版 {@code 16f * getSpeedModifier}）。 */
    public static final float BASE_SPEED = 16.0F;

    private HeatEngineLogic() {
    }

    /** 温度 → 效率 0..1。 */
    public static float temperatureEfficiency(double temperature, double temperatureIncrement) {
        double increment = temperatureIncrement > 0 ? temperatureIncrement : 60.0;
        int steps = (int) Math.floor((temperature - TEMPERATURE_OFFSET) / increment);
        return Math.max(0.0F, Math.min(1.0F, steps / (float) EFFICIENCY_STEPS));
    }

    /** 效率 → 转速倍率 1..4。 */
    public static int speedModifier(float efficiency) {
        if (efficiency >= 1.0F) {
            return 4;
        }
        return 1 + (int) Math.min(2.0, Math.floor(efficiency * 4.0));
    }

    /** 根据效率算出应力容量（旧版把总容量按整数分配，避免出现小数容量）。 */
    public static float stressCapacity(float efficiency, double maxCapacity) {
        double generatedSpeed = BASE_SPEED * speedModifier(efficiency);
        if (efficiency <= 0 || maxCapacity <= 0 || generatedSpeed <= 0) {
            return 0;
        }
        double wholeStress = Math.floor(efficiency * maxCapacity);
        return (float) (wholeStress / generatedSpeed);
    }

    /** 距离下一档还差多少度（护目镜提示用）。 */
    public static double temperatureUntilNextTier(double temperature, double temperatureIncrement) {
        double increment = temperatureIncrement > 0 ? temperatureIncrement : 60.0;
        int tier = Mth.clamp((int) Math.floor((temperature - TEMPERATURE_OFFSET) / increment), 0, EFFICIENCY_STEPS);
        if (tier >= EFFICIENCY_STEPS) {
            return 0;
        }
        return Math.max(0, TEMPERATURE_OFFSET + (tier + 1) * increment - temperature);
    }
}
