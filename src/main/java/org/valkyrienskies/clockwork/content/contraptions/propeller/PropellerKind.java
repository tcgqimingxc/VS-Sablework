package org.valkyrienskies.clockwork.content.contraptions.propeller;

import org.valkyrienskies.clockwork.config.ClockworkConfig;

/**
 * 螺旋桨规格。
 *
 * <p>对应旧版：黄铜螺旋桨 = {@code PropellerController#computeForce}（桨距可控，{@code physProp.brass == true}）；
 * 拼装螺旋桨 = {@code PropellerController#computeBladeForce}（固定桨距，靠叶片数/长度算升力）。
 * 旧版把叶片信息存在控制器的数据对象里（{@code PropData.blades}），移植后简化为静态规格，
 * 因为新底座的子结构装配已经把叶片方块搬进子空间，逐个叶片建模收益不大：
 * 这里用"当量叶片"（叶数 × 单叶弦长/展长）复现同样的量级。
 */
public enum PropellerKind {

    /**
     * 黄铜螺旋桨轴承：4 片宽叶、桨距较大（旧版 {@code brass = true}，会自动调整桨距到最佳攻角）。
     */
    BRASS(4, 2.0, 0.375, 0.30, 0.9),

    /**
     * 拼装螺旋桨轴承：3 片窄叶、固定桨距（旧版 {@code brass = false}）。
     */
    JURYRIGGED(3, 1.5, 0.25, 0.22, 0.7),

    /**
     * 直升机旋翼：6 片长叶、低桨距高转速（旧版 {@code CopterBearingBlockEntity}）。
     */
    COPTER(6, 3.0, 0.25, 0.18, 0.8);

    /** 当量叶片数。 */
    public final int blades;
    /** 单叶展长 [m]（从桨毂到叶尖）。 */
    public final double length;
    /** 单叶弦长 [m]。 */
    public final double chord;
    /** 固定桨距角 [度]（黄铜的"最佳攻角"目标值，其它为固定值）。 */
    public final double pitchDegrees;
    /** 效率系数：把理想叶素理论结果压到游戏手感的量级。 */
    public final double efficiency;

    PropellerKind(final int blades, final double length, final double chord,
                  final double pitchDegrees, final double efficiency) {
        this.blades = blades;
        this.length = length;
        this.chord = chord;
        this.pitchDegrees = pitchDegrees;
        this.efficiency = efficiency;
    }

    /** 单个螺旋桨每物理刻允许施加的最大力 [N]（接配置，避免硬编码魔法数）。 */
    public double maxForce() {
        return ClockworkConfig.server().propellerMaxForce.get();
    }

    /** 全局推力倍率（配置项，1.0 = 默认手感）。 */
    public double forceMultiplier() {
        return ClockworkConfig.server().propellerForceMultiplier.get();
    }
}
