package org.valkyrienskies.clockwork.platform;

import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.clockwork.config.ClockworkConfig;

/**
 * 机翼 / 襟翼的升力与阻力计算（供"机翼方块"切片调用）。
 *
 * <p>这部分对应旧版 {@code content/physicalities/wing/**} 方块里内联的气动代码
 * （旧版把 {@code Wing} 交给 VS2 的船舶物理，受力由核心库算）。方块本体由另一个切片负责，
 * 本类只提供 platform 侧的计算，签名对齐 Sable 的
 * {@link BlockSubLevelLiftProvider#sable$contributeLiftAndDrag}，因此方块侧可以写成：
 *
 * <pre>{@code
 * public class WingBlock extends Block implements BlockSubLevelLiftProvider {
 *     @Override public Direction sable$getNormal(BlockState state) { return state.getValue(FACING); }
 *
 *     @Override
 *     public void sable$contributeLiftAndDrag(LiftProviderContext ctx, ServerSubLevel subLevel, Pose3d localPose,
 *                                             double timeStep, Vector3dc linearVelocity, Vector3dc angularVelocity,
 *                                             Vector3d linearImpulse, Vector3d angularImpulse, LiftProviderGroup group) {
 *         CWWingForces.contribute(ctx, subLevel, localPose, timeStep, linearVelocity, angularVelocity,
 *                 linearImpulse, angularImpulse, this.getAngleOfAttackRadians(ctx.state()));
 *     }
 * }
 * }</pre>
 *
 * <p><b>气动模型</b>（薄翼理论）：
 * <pre>
 *   n, t  = 翼面法向 / 切向（local 系，法向已由 localPose 变换过）
 *   vRel  = 相对气流（local 系；调用方需自行把结构速度、角速度贡献、风都算进去）
 *   vN    = n·vRel                         法向分量（产生压差阻力）
 *   vT    = |vRel − n·vN|                  切向分量（决定动压与升力方向）
 *   q     = 0.5·ρ·vT²                      动压（ρ = 空气密度，随气压缩放）
 *   Cl    = 2π·α                           升力系数（α = 攻角，弧度）
 *   Cd    = cd0 + k·Cl²                    阻力系数
 *   L     = q·A·Cl ，D = q·A·Cd            升力 / 阻力
 * </pre>
 * 升力沿"垂直于相对气流、位于 n 与 vRel 张成的平面内"的方向；
 * 切向速度趋近 0（纯法向来流，例如襟翼完全挡住气流）时退化为沿 −n·sign(vN) 的平板阻力。
 *
 * <p><b>为什么每一步都要守卫（"机翼让物理结构消失"的根因与修法）</b>：
 * 气动力是交给 Sable 的冲量累加器（{@code linearImpulse / angularImpulse}）的，Sable 会把它
 * 直接变成刚体冲量：任何 NaN/Inf 都会让刚体位姿变成 NaN（{@code SubLevelPhysicsSystem#updatePose}
 * 检测到后走 {@code recoverSubLevel}，救不回来就把子空间删掉），任何过大的值会把结构一两个子步
 * 推到 Y 保护范围之外（{@code ServerSubLevel.java:228-232}：{@code "Sub-level {} has an extreme Y
 * coordinate range, removing"} → {@code markRemoved()}）。而本模型的升力 ∝ v²，Sable 的质量尺度
 * 又是 <b>1 方块 ≈ 1 kg</b>（见 {@code CWServer.Comments.gravitronMaxGrabMass}），
 * 于是只要结构被外力加速到几百 m/s，或者质心/惯量一时算不出来（力臂巨大、力矩天文数字），
 * 就会触发上面两条删除路径 —— 玩家看到的就是"在没锁定的结构上放一块机翼，结构当场消失"。
 *
 * <p>因此这里按下面的顺序逐级设卡（全部是"宁可不出力，也不能把结构搞没"）：
 * <ol>
 *   <li>入参与中间量逐个 {@code isFinite} 检查，任何一项非有限立刻返回、不写累加器；</li>
 *   <li>来流速度按 {@code wing.maxAirspeed} 封顶（切断 v² 正反馈）；</li>
 *   <li>气压按 {@code wing.maxPressure} 封顶；</li>
 *   <li>力先按 {@code wing.maxForce} 裁，再按"结构质量 × {@code wing.maxAcceleration}"裁
 *       （轻结构不会被弹飞，重结构仍拿满力）；</li>
 *   <li>力臂（翼面位置 − 质心）长度封顶在 {@link #MAX_LEVER}，质心拿不到时力臂取 0
 *       （只施加纯力，不产生力矩）；</li>
 *   <li>写累加器前最后再检查一次冲量与角冲量，非有限就整份丢弃。</li>
 * </ol>
 */
public final class CWWingForces {

    /** 海平面空气密度 [kg/m³]（与旧版 aerodynamicUtils 的基准一致）。 */
    private static final double AIR_DENSITY = 1.225;
    /** 零攻角阻力系数。 */
    private static final double CD0 = 0.01;
    /** 诱导阻力系数（Cd = cd0 + k·Cl²）。 */
    private static final double INDUCED_DRAG = 0.01;
    /**
     * 力臂长度上限 [格]。
     *
     * <p>正常结构的力臂不会超过结构自身的包围盒半径（配置里"通用轴最大连接距离"也是 32 格）。
     * 超过它只可能来自质心尚未建立 / 数据异常，此时不夹住就会得到"力臂数万格 × 力"的天文角冲量，
     * 一子步把结构甩飞并被 Sable 删除。
     */
    private static final double MAX_LEVER = 32.0;

    private CWWingForces() {
    }

    /**
     * 按 Sable 的升力提供者签名计算并累加冲量。
     *
     * @param ctx              Sable 给的方块上下文（位置 / 状态 / 法向）
     * @param subLevel         所在子空间
     * @param localPose        子结构（Create 装置）的局部姿态，可为 null
     * @param timeStep         物理子步时长
     * @param linearVelocity   子空间线速度（世界系，Sable 直接给）
     * @param angularVelocity  子空间角速度（世界系）
     * @param linearImpulse    输出：线性冲量累加器（local 系）
     * @param angularImpulse   输出：角冲量累加器（local 系）
     * @param angleOfAttack    攻角（弧度）；由方块按自身襟翼角度/朝向给出
     */
    public static void contribute(final BlockSubLevelLiftProvider.LiftProviderContext ctx,
                                  final ServerSubLevel subLevel,
                                  final @Nullable Pose3dc localPose,
                                  final double timeStep,
                                  final Vector3dc linearVelocity,
                                  final Vector3dc angularVelocity,
                                  final Vector3d linearImpulse,
                                  final Vector3d angularImpulse,
                                  final double angleOfAttack) {
        // ---- 0) 入参守卫：速度 / 攻角 / 子步步长必须是有限数，否则一律不参与受力 ----
        if (!linearVelocity.isFinite() || !angularVelocity.isFinite()
                || !Double.isFinite(angleOfAttack) || !(timeStep > 0.0) || !Double.isFinite(timeStep)) {
            return;
        }

        // ---- 1) 几何：法向与作用点（local 系）；法向必须能归一化，否则后面全是 NaN ----
        final Vector3d normal = new Vector3d(ctx.dir().x, ctx.dir().y, ctx.dir().z);
        final Vector3d pos = new Vector3d(ctx.pos().getX() + 0.5, ctx.pos().getY() + 0.5, ctx.pos().getZ() + 0.5);
        if (localPose != null) {
            localPose.transformNormal(normal);
            localPose.transformPosition(pos);
        }
        if (!normal.isFinite() || !pos.isFinite() || normal.lengthSquared() < 1.0E-8) {
            return;
        }
        normal.normalize();

        final Pose3dc pose = subLevel.logicalPose();
        if (!pose.position().isFinite()) {
            return;
        }

        // 力臂：翼面相对刚体原点的世界系偏移（与 Sable 默认实现同一条算式）
        final Vector3d arm = pose.transformPosition(pos, new Vector3d()).sub(pose.position());
        if (!arm.isFinite()) {
            return;
        }
        // 相对气流 = -(线速度 + 角速度 × 力臂)，转回子空间 local 系
        final Vector3d relativeAir = new Vector3d(linearVelocity)
                .add(angularVelocity.cross(arm, new Vector3d())).negate();
        pose.transformNormalInverse(relativeAir);
        if (!relativeAir.isFinite()) {
            return;
        }
        // 来流速度封顶：升力 ∝ v²，不封顶就是正反馈（见类注释）
        final double airspeed = relativeAir.length();
        final double maxAirspeed = ClockworkConfig.server().wingMaxAirspeed.get();
        if (airspeed > maxAirspeed) {
            relativeAir.mul(maxAirspeed / airspeed);
        }

        final double normalSpeed = normal.dot(relativeAir);
        final Vector3d tangential = new Vector3d(relativeAir).fma(-normalSpeed, normal);
        final double tangentialSpeed = tangential.length();

        // ---- 2) 动压：气压封顶后再算密度 ----
        final double rawPressure = dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData
                .getAirPressure(subLevel.getLevel(), pose.transformPosition(pos, new Vector3d()));
        if (!Double.isFinite(rawPressure) || rawPressure <= 0.0) {
            return;                                    // 真空 / 气压数据异常：不产生气动力
        }
        final double pressure = Math.min(rawPressure, ClockworkConfig.server().wingMaxPressure.get());
        final double density = AIR_DENSITY * pressure;

        final double area = ClockworkConfig.server().wingArea.get();
        final double dynamicPressure = 0.5 * density * tangentialSpeed * tangentialSpeed;
        final double liftCoefficient = 2.0 * Math.PI * angleOfAttack;
        final double dragCoefficient = CD0 + INDUCED_DRAG * liftCoefficient * liftCoefficient;
        final double lift = dynamicPressure * area * liftCoefficient;
        final double drag = dynamicPressure * area * dragCoefficient;

        final Vector3d force = new Vector3d();
        if (tangentialSpeed > 1.0E-4) {
            // 升力方向：把法向投影到与气流垂直的平面内
            final Vector3d tangentDir = new Vector3d(tangential).div(tangentialSpeed);
            final Vector3d liftDir = new Vector3d(normal).fma(-tangentDir.dot(normal), tangentDir);
            if (liftDir.lengthSquared() > 1.0E-8) {
                liftDir.normalize();
            } else {
                liftDir.set(normal);
            }
            force.fma(lift, liftDir);
            force.fma(-drag, tangentDir);
        } else {
            // 纯法向来流：退化成平板阻力（襟翼全挡时的手感）
            force.fma(-drag - Math.abs(normalSpeed) * area, normal);
        }
        force.mul(ClockworkConfig.server().wingForceMultiplier.get());
        if (!force.isFinite()) {
            return;
        }

        // ---- 3) 力的量级守卫 ----
        CWForces.clampInPlace(force, ClockworkConfig.server().wingMaxForce.get());
        final MassData massTracker = subLevel.getMassTracker();
        final double mass = massTracker.getMass();
        if (!Double.isFinite(mass) || mass <= 0.0) {
            return;                                    // 质量不可用（惯量张量不可逆）：宁可不施力
        }
        CWForces.clampInPlace(force, mass * ClockworkConfig.server().wingMaxAcceleration.get());

        final Vector3d impulse = force.mul(timeStep, new Vector3d());

        // ---- 4) 力臂守卫，然后才写进 Sable 的累加器 ----
        final Vector3dc centerOfMass = massTracker.getCenterOfMass();
        final Vector3d lever = centerOfMass != null && centerOfMass.isFinite()
                ? new Vector3d(pos).sub(centerOfMass)
                : new Vector3d();                      // 质心拿不到：只施纯力，不产生力矩
        CWForces.clampInPlace(lever, MAX_LEVER);

        final Vector3d angularPart = lever.cross(impulse, new Vector3d());
        if (!impulse.isFinite() || !angularPart.isFinite()) {
            return;
        }
        linearImpulse.sub(impulse);
        // 角冲量：力臂 × 力（Sable 的默认实现同样是"减"，见 BlockSubLevelLiftProvider）
        angularImpulse.sub(angularPart);
    }
}
