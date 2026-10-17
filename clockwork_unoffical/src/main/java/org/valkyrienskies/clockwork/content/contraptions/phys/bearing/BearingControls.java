package org.valkyrienskies.clockwork.content.contraptions.phys.bearing;

import net.createmod.catnip.math.AngleHelper;
import org.valkyrienskies.clockwork.config.ClockworkConfig;

/**
 * {@link BearingControl} 的具体实现集合（策略模式）。
 *
 * <p>对应旧版：
 * <ul>
 *   <li>{@link Continuous} —— 机械轴承式驱动：目标角随输入轴推进，子结构被硬驱动到该角度。
 *       物理轴承的"旋转模式"、螺旋桨轴承、直升机旋翼都用它。</li>
 *   <li>{@link Locked} —— 伺服锁定：目标角由外部（红石 / 玩家 / 控制器）给定，子结构被夹住，
 *       对标旧版物理轴承的 {@code LockedMode.LOCKED} 与 SwivelBearing 的锁定模式。</li>
 *   <li>{@link Free} —— 自由铰链：只给被动摩擦，不施加伺服力矩，对标旧版未锁定时的
 *       {@code VSRevoluteJoint(driveFreeSpin = true)}。</li>
 *   <li>{@link Oscillating} —— 往复摆动：目标角按相位在 ±振幅之间摆动，相位由输入轴速度推进，
 *       对标旧版 {@code FlapBearingBlockEntity}（转速越高摆动越快）。</li>
 * </ul>
 */
public final class BearingControls {

    private BearingControls() {
    }

    /** 连续旋转（螺旋桨 / 旋翼 / 机械轴承式物理轴承）。 */
    public static final class Continuous implements BearingControl {

        @Override
        public double goalRadians(final Context ctx) {
            return AngleHelper.rad(ctx.interpolatedDegrees());
        }
    }

    /** 伺服锁定：目标角不由输入轴推进，而是外部设置（红石/控制器），子结构被夹在该角度。 */
    public static final class Locked implements BearingControl {

        @Override
        public double advanceDegrees(final double targetDegrees, final double angularSpeedDegreesPerTick) {
            return targetDegrees;
        }

        @Override
        public boolean driven(final Context ctx) {
            return true;
        }

        @Override
        public double goalRadians(final Context ctx) {
            return AngleHelper.rad(ctx.interpolatedDegrees());
        }

        /** 锁定模式需要更高的刚度，否则被负载拖走。 */
        @Override
        public double stiffness() {
            return ClockworkConfig.server().bearingLockStiffness.getF();
        }

        @Override
        public double damping() {
            return ClockworkConfig.server().bearingLockDamping.getF();
        }
    }

    /** 自由铰链（未锁定）：只给摩擦。 */
    public static final class Free implements BearingControl {

        @Override
        public boolean driven(final Context ctx) {
            return false;
        }

        @Override
        public double goalRadians(final Context ctx) {
            return 0.0;
        }
    }

    /**
     * 往复摆动（襟翼）。
     *
     * <p>旧版 {@code FlapBearingBlockEntity} 用 {@code Math.sin} 在配置的角度区间里摆动，
     * 摆动频率正比于输入轴转速；这里保持同样的手感，但相位推进量接到配置上。
     *
     * <p>{@code Math.sin} 会给出负角，这里折回 [0,360)：伺服目标是<b>绝对</b>关节角，
     * 跨过 0 点不折会让偏差突然差一整圈（见 {@code AbstractBearingBlockEntity#wrapDegrees}）。
     */
    public static final class Oscillating implements BearingControl {

        private double phase;

        @Override
        public double advanceDegrees(final double targetDegrees, final double angularSpeedDegreesPerTick) {
            final double perDegree = ClockworkConfig.server().flapPhasePerShaftDegree.getF();
            this.phase += Math.abs(angularSpeedDegreesPerTick) * perDegree;
            if (this.phase > Math.PI * 2) {
                this.phase -= Math.PI * 2;
            }
            final double degrees = Math.sin(this.phase) * ClockworkConfig.server().flapAmplitudeDegrees.getF();
            return degrees < 0.0 ? degrees + 360.0 : degrees;
        }

        @Override
        public double goalRadians(final Context ctx) {
            return AngleHelper.rad(ctx.interpolatedDegrees());
        }

        /** 让摆动从 0 开始，避免刚装配时突然甩一下。 */
        public void reset() {
            this.phase = 0.0;
        }
    }
}
