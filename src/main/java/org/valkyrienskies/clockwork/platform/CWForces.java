package org.valkyrienskies.clockwork.platform;

import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * 向子空间施力的薄封装。
 *
 * <p>旧版通过 VS2 的 {@code PhysShip#applyWorldForceToBodyPos / applyWorldTorque} 施力，
 * 这些类在 1.21.1 下不存在。新底座（Sable 2.0.5）的写法是：
 * <ol>
 *   <li>把力累积到一个 {@link QueuedForceGroup}（按 {@link ForceGroup} 分类，用于调试可视化）；</li>
 *   <li>Sable 在每个物理子步里调用 {@code ForceTotal#applyForces}，把累积量作为"冲量"交给刚体。</li>
 * </ol>
 * 因此这里所有带 {@code timeStep} 的方法接收的是<b>力 / 力矩</b>，内部乘 dt 转成冲量；
 * 名字里带 {@code Impulse} 的方法则直接接收冲量。
 *
 * <p>坐标系：Sable 的冲量接口使用子空间的 <b>local（plot）坐标系</b>；
 * 方块实体在子空间里的 {@code getBlockPos()} 正好就是这个坐标系下的位置。
 */
public final class CWForces {

    private CWForces() {
    }

    /** 在 local 位置施加一个冲量（单位 N·s）。 */
    public static void pointImpulse(final ServerSubLevel subLevel, final ForceGroup group,
                                    final Vector3dc localPos, final Vector3dc impulse) {
        subLevel.getOrCreateQueuedForceGroup(group).applyAndRecordPointForce(localPos, impulse);
    }

    /** 在 local 位置施加一个力（单位 N），内部乘物理子步 dt。 */
    public static void pointForce(final ServerSubLevel subLevel, final ForceGroup group,
                                  final Vector3dc localPos, final Vector3dc force, final double timeStep) {
        pointImpulse(subLevel, group, localPos, new Vector3d(force).mul(timeStep));
    }

    /** 施加一个纯力矩冲量（单位 N·m·s）。 */
    public static void torqueImpulse(final ServerSubLevel subLevel, final ForceGroup group, final Vector3dc impulse) {
        subLevel.getOrCreateQueuedForceGroup(group).getForceTotal().applyTorqueImpulse(impulse);
    }

    /** 施加一个纯力矩（单位 N·m），内部乘物理子步 dt。 */
    public static void torque(final ServerSubLevel subLevel, final ForceGroup group,
                              final Vector3dc torque, final double timeStep) {
        torqueImpulse(subLevel, group, new Vector3d(torque).mul(timeStep));
    }

    /** 推进力分组（螺旋桨 / 糖火箭）。 */
    public static ForceGroup propulsion() {
        return ForceGroups.PROPULSION.get();
    }

    /** 浮力分组（热气球）。 */
    public static ForceGroup balloonLift() {
        return ForceGroups.BALLOON_LIFT.get();
    }

    /** 姿态控制类力（陀螺稳定力矩）。 */
    public static ForceGroup magneticForce() {
        return ForceGroups.MAGNETIC_FORCE.get();
    }

    /**
     * 把向量长度裁剪到上限（旧版 {@code clampVectorMagnitudeInPlace}）。
     *
     * @param max 上限；<= 0 表示不限制
     */
    public static void clampInPlace(final Vector3d vec, final double max) {
        if (max <= 0) {
            return;
        }
        if (vec.lengthSquared() > max * max) {
            vec.normalize(max);
        }
    }

    /**
     * 取该子空间沿某个 local 方向的转动惯量（kg·m²）。
     *
     * <p>与 SwivelBearingBlockEntity#updateServoCoefficients 的做法一致：
     * 用惯量张量变换方向向量后与原方向点乘，得到绕该轴的等效惯量，用作伺服增益的尺度。
     * 这里<b>不</b>改用 {@code getSelfMassTracker()}：参考实现用的就是
     * {@code getMassTracker()}（= {@code MergedMassTracker}，含合并进来的 contraption 质量），
     * 换成"只算自身"会让带附着物的结构伺服变软，属于行为改动，需要实机验证后再单独评估。
     *
     * @param localAxis 方向向量，需与调用方（{@code setMotor} 的关节轴）使用同一个坐标系
     * @return 等效惯量；张量非有限（极端结构 / 质量异常）时返回 {@link Double#NaN}，
     *         由调用方决定回退值 —— 直接把 NaN 传给 {@code setMotor} 会让求解器发散
     */
    public static double inertiaAlong(final ServerSubLevel subLevel, final Vector3dc localAxis) {
        if (subLevel.isRemoved()) {
            return Double.NaN;
        }
        final Vector3d temp = new Vector3d();
        final double inertia = subLevel.getMassTracker().getInertiaTensor().transform(localAxis, temp).dot(localAxis);
        return Double.isFinite(inertia) && inertia > 0.0 ? inertia : Double.NaN;
    }
}
