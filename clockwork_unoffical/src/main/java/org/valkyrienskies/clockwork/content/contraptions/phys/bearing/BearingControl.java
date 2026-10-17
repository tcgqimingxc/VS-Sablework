package org.valkyrienskies.clockwork.content.contraptions.phys.bearing;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.valkyrienskies.clockwork.config.ClockworkConfig;

/**
 * 轴承"旋转控制"策略。
 *
 * <p>旧版 Clockwork 里每个轴承方块实体都自己算角度、自己下发力矩
 * （{@code PhysBearingBlockEntity} / {@code PropellerBearingBlockEntity} / {@code FlapBearingBlockEntity} …），
 * 移植后把"目标角怎么走 + 要不要驱动"抽成本接口，装配 / 约束 / 持久化 / 同步由
 * {@link AbstractBearingBlockEntity} 写一次即可。
 *
 * <p>角度单位：接口对外用<b>度</b>（方便与 Create 的 {@code convertToAngular} 对齐），
 * 只有交给 Sable 伺服电机的目标角才转成<b>弧度</b>。
 */
public interface BearingControl {

    /** 每刻调用，返回推进后的目标角（度，折回 [0,360)）。默认：跟随输入轴旋转。 */
    default double advanceDegrees(final double targetDegrees, final double angularSpeedDegreesPerTick) {
        double next = (targetDegrees + angularSpeedDegreesPerTick) % 360.0;
        // Java 的 % 对负数返回负数（反转的轴承会遇到），必须是正的 [0,360)：
        // Sable 的伺服目标是绝对关节角，负数会让偏差凭空差出一整圈。详见
        // AbstractBearingBlockEntity#wrapDegrees 的说明。
        if (next < 0.0) {
            next += 360.0;
        }
        return next;
    }

    /** 是否施加伺服力矩；false 时轴承退化为"自由铰链 + 被动摩擦"（对标旧版未锁定的物理轴承）。 */
    default boolean driven(final Context ctx) {
        return true;
    }

    /** 交给 Sable 伺服电机的目标角（弧度）。 */
    double goalRadians(Context ctx);

    /** 伺服刚度（会乘以等效惯量）。 */
    default double stiffness() {
        return ClockworkConfig.server().bearingStiffness.getF();
    }

    /** 伺服阻尼（会乘以等效惯量）。 */
    default double damping() {
        return ClockworkConfig.server().bearingDamping.getF();
    }

    /** 自由状态下的被动摩擦（直接作为电机 damping 传入，不乘惯量，对齐 SwivelBearing）。 */
    default double friction() {
        return ClockworkConfig.server().bearingFriction.getF();
    }

    /** 输入轴速度上限 [RPM]，<= 0 表示不限制。 */
    default float maxSpeed() {
        return ClockworkConfig.server().bearingMaxSpeed.getF();
    }

    /**
     * 每刻传给策略的只读上下文。
     *
     * @param level            服务端世界
     * @param pos              轴承自身方块位置（在所属子空间的 plot 坐标系里）
     * @param attached         被驱动的子空间
     * @param lastTargetDegrees 上一刻目标角
     * @param targetDegrees    当前目标角
     * @param partialTick      物理插值系数
     * @param redstoneSignal   该位置的最强红石信号
     */
    record Context(Level level, BlockPos pos, ServerSubLevel attached,
                   double lastTargetDegrees, double targetDegrees,
                   double partialTick, int redstoneSignal) {

        /**
         * 插值后的目标角（度）：把"一游戏刻的目标角增量"平摊到各物理子步。
         *
         * <p><b>必须走 {@link AngleHelper#angleLerp}（跨 0 点安全的角度插值），不能写成
         * {@code last + (target - last) * partialTick}。</b>
         * 目标角每绕一圈都会折回（359.9° → 0.1°），朴素线性插值此时拿到的是 −359.8° 的差：
         * {@code partialTick = 0.5} 的子步会给出 180° 这种"倒着拧半圈"的目标，下一秒又跳回 0.1°，
         * 伺服于是每转一圈被猛拽两次 —— 实机现象就是"转着转着周期性抽搐"。
         *
         * <p>证据（javap 反编译 {@code ponder-neoforge-1.0.82+mc1.21.1.jar} 里的
         * {@code net.createmod.catnip.math.AngleHelper}）：
         * {@code angleLerp(pct, cur, tgt) = cur + getShortestAngleDiff(cur, tgt) * pct}，而
         * {@code getShortestAngleDiff = ((tgt%360 - cur%360) % 360 + 540) % 360 - 180}，
         * 即差值一定落在 (−180, 180]。参考实现 {@code SwivelBearingBlockEntity.java:395} 用的就是它。
         */
        public double interpolatedDegrees() {
            return AngleHelper.angleLerp(partialTick, lastTargetDegrees, targetDegrees);
        }
    }
}
