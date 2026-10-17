package org.valkyrienskies.clockwork.content.contraptions.propeller;

import com.simibubi.create.content.contraptions.AssemblyException;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.block.propeller.BlockEntityPropeller;
import dev.ryanhcode.sable.api.block.propeller.BlockEntitySubLevelPropellerActor;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.clockwork.content.contraptions.phys.bearing.AbstractBearingBlockEntity;
import org.valkyrienskies.clockwork.content.contraptions.phys.bearing.BearingControl;
import org.valkyrienskies.clockwork.content.contraptions.phys.bearing.BearingControls;
import org.valkyrienskies.clockwork.content.contraptions.propeller.blades.BladeControllerBlock;
import org.valkyrienskies.clockwork.content.contraptions.propeller.blades.BladeControllerBlockEntity;
import org.valkyrienskies.clockwork.content.contraptions.propeller.blades.BladeData;

import java.util.ArrayList;
import java.util.List;

/**
 * 螺旋桨轴承方块实体（黄铜 / 拼装共用）。
 *
 * <p>对应旧版 {@code content/contraptions/propeller/PropellerBearingBlockEntity} +
 * {@code content/forces/PropellerController}。
 *
 * <p><b>施力链路（与旧版的差异）</b>：
 * 旧版自己算叶素受力，然后 {@code physShip.applyWorldForceToModelPos(force, ...)}；
 * 这里改为实现 Sable 的 {@link BlockEntityPropeller} + {@link BlockEntitySubLevelPropellerActor}，
 * 由 Sable 在物理子步里把 {@code thrust * dt} 记进 {@code ForceGroups.PROPULSION} 分组
 * （见 {@code BlockEntitySubLevelPropellerActor#applyForces}），
 * 与 Aeronautics 的螺旋桨走完全相同的代码路径 —— 因此力量级、分组可视化、气压衰减都自动一致。
 *
 * <p><b>推力符号约定</b>：正的输入轴转速 = 沿 facing 方向推（Aeronautics 的
 * {@code getDirectionIndependentSpeed} 里混了轴方向符号，两种摆放会得到同一世界方向，这里做了修正）。
 *
 * <p><b>叶片与桨距（可变桨距）</b>：叶片装在 {@link BladeControllerBlock} 里，控制器随结构一起被装进子空间，
 * 于是"控制器 → 轴承"的绑定就是"子空间里的块实体演员表"：
 * {@link #refreshBlades()} 每刻通过 {@code LevelPlot#getBlockEntityActors()} 收集子空间里所有
 * {@link BladeControllerBlockEntity}，把它们当前的桨距角与叶片几何取出来喂给叶素模型
 * （旧版是遍历 Create contraption 的方块表 + 从方块 NBT 反解 {@code BladeData}）。
 * 没有控制器的螺旋桨退回 {@link PropellerKind} 的当量叶片参数（旧版"黄铜螺旋桨挂帆"的情形）。
 *
 * <p><b>红石总距</b>：黄铜螺旋桨沿用旧版 {@code applyPowerEffect} —— 两个垂直于转轴的方向做
 * 红石差分，每刻最多把桨距推 ±2°，范围 ±90°。
 */
public class PropellerBearingBlockEntity extends AbstractBearingBlockEntity
        implements BlockEntitySubLevelPropellerActor, BlockEntityPropeller {

    /** 海平面空气密度 [kg/m³]，与旧版 {@code aerodynamicUtils.getAirDensityForY} 的基准一致。 */
    private static final double AIR_DENSITY = 1.225;
    /** 空气密度对应的"来流速度"换算系数：把叶尖切向速度折算成螺旋桨能推到的最大前进速度。 */
    private static final double AIRFLOW_SCALE = 4.0;

    private final BearingControl control = new BearingControls.Continuous();

    /** 子空间里所有叶片控制器当前提供的叶片（空 = 没有控制器，退回当量叶片参数）。 */
    private List<BladeData> blades = List.of();
    /** 收集到的控制器（用于红石总距回写）；与 {@link #blades} 一起在 {@link #refreshBlades()} 里刷新。 */
    private List<BladeControllerBlockEntity> controllers = List.of();

    public PropellerBearingBlockEntity(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    // ==================== 轴承骨架 ====================

    @Override
    protected Direction getFacing() {
        return this.getBlockState().getValue(PropellerBearingBlock.FACING);
    }

    @Override
    protected BearingControl getControl() {
        return this.control;
    }

    // ==================== 叶片控制器的绑定 ====================

    @Override
    protected void onServerTick() {
        this.refreshBlades();
        this.applyCollectivePitch();
    }

    /**
     * 装配前的检查：朝向的那一格必须是"同向的叶片控制器"。
     *
     * <p>旧版把这条规则放在 {@code PropellerContraption#assembleProp} 里
     * （不是叶片控制器 → {@code contraptions.propeller.not_prop}；
     * 控制器朝向不同 → {@code contraptions.propeller.controller_wrong_way}）。
     * 这里提前到装配入口，失败时通过 {@code IDisplayAssemblyExceptions} 把原因显示给玩家。
     */
    @Override
    public void assemble() {
        if (this.level == null || this.level.isClientSide || this.isAssembled()) {
            return;
        }
        final BlockState front = this.level.getBlockState(this.getAssemblyOrigin());
        if (!(front.getBlock() instanceof final BladeControllerBlock controller)) {
            this.setLastAssemblyException(new AssemblyException(
                    Component.translatable("contraptions.propeller.not_prop")));
            return;
        }
        if (front.getValue(BladeControllerBlock.FACING) != this.getFacing()) {
            this.setLastAssemblyException(new AssemblyException(
                    Component.translatable("contraptions.propeller.controller_wrong_way")));
            return;
        }
        super.assemble();
    }

    /** 从子空间里收集叶片控制器（对应旧版 {@code PropellerBearingBlockEntity#getBlades}）。 */
    private void refreshBlades() {
        final ServerSubLevel attached = this.getAttachedSubLevel();
        if (attached == null) {
            this.blades = List.of();
            this.controllers = List.of();
            return;
        }
        final List<BladeData> found = new ArrayList<>();
        final List<BladeControllerBlockEntity> foundControllers = new ArrayList<>();
        for (final BlockEntitySubLevelActor actor : attached.getPlot().getBlockEntityActors()) {
            if (actor instanceof final BladeControllerBlockEntity controller) {
                foundControllers.add(controller);
                found.addAll(controller.bladeData());
            }
        }
        this.blades = found;
        this.controllers = foundControllers;
    }

    /**
     * 红石总距（旧版 {@code applyPowerEffect}，只对黄铜螺旋桨生效）：
     * 取两个垂直于转轴的方向做红石差分，每刻把桨距推最多 ±2°，范围 ±90°。
     */
    private void applyCollectivePitch() {
        if (this.getKind() != PropellerKind.BRASS || this.controllers.isEmpty() || this.level == null) {
            return;
        }
        final Direction.Axis rotationAxis = this.getFacing().getAxis();
        int power = 0;
        for (final Direction.Axis axis : Direction.Axis.values()) {
            if (axis == rotationAxis) {
                continue;
            }
            for (final Direction.AxisDirection direction : Direction.AxisDirection.values()) {
                final Direction side = Direction.get(direction, axis);
                power += direction == Direction.AxisDirection.POSITIVE
                        ? this.level.getSignal(this.worldPosition.relative(side), side)
                        : -this.level.getSignal(this.worldPosition.relative(side), side);
            }
        }
        final double angleChange = 2.0 * Mth.clamp(power / 30.0, -1.0, 1.0);
        if (angleChange == 0.0) {
            return;
        }
        for (final BladeControllerBlockEntity controller : this.controllers) {
            controller.setPitchDegrees(controller.pitchDegrees() + angleChange);
        }
    }

    // ==================== 螺旋桨参数 ====================

    protected PropellerKind getKind() {
        return this.getBlockState().getBlock() instanceof final PropellerBearingBlock block
                ? block.getKind()
                : PropellerKind.BRASS;
    }

    /** 输入轴角速度 [rad/s]（已按配置限速）。 */
    protected double rotationRateRadPerSec() {
        final float maxSpeed = this.getControl().maxSpeed();
        final float rpm = maxSpeed > 0 ? Mth.clamp(this.getSpeed(), -maxSpeed, maxSpeed) : this.getSpeed();
        return rpm / 60.0 * 2.0 * Math.PI;
    }

    @Override
    public Direction getBlockDirection() {
        return this.getFacing();
    }

    /** Sable 通过这个入口读取推力 / 气流（{@code BlockEntitySubLevelPropellerActor} 的实现依赖它）。 */
    @Override
    public BlockEntityPropeller getPropeller() {
        return this;
    }

    @Override
    public boolean isActive() {
        return this.isAssembled() && Math.abs(this.getSpeed()) > 0.01f;
    }

    /**
     * 气流速度 [m/s]（Sable 用它做前进比衰减：{@code (airflow + v·thrustDir) / airflow}）。
     *
     * <p>符号必须与推力方向相反：飞机加速到该速度时推力衰减到 0，对应旧版
     * {@code velocityFalloff} 的"接近声速/前进比极限"效果。
     */
    @Override
    public double getAirflow() {
        final double omega = this.rotationRateRadPerSec();
        if (Math.abs(omega) < 1.0E-4) {
            return 0.0;
        }
        final PropellerKind kind = this.getKind();
        final double tipSpeed = Math.abs(omega) * this.bladeSpan() * 0.5;
        return -Math.signum(omega) * tipSpeed * kind.efficiency * AIRFLOW_SCALE;
    }

    /**
     * 推力 [N]（Sable 会取负号后沿 facing 施加，见 {@code BlockEntityPropeller#getScaledThrust}）。
     *
     * <p>叶素理论（与旧版 {@code PropellerController#computeBladeForce} 同一个模型）：
     * <pre>
     *   r     = 叶片展长（叶素代表半径）
     *   vTan  = |ω| · r                            叶素切向速度
     *   q     = 0.5 · ρ · vTan²                    动压
     *   Cl    = 2π·α ，Cd = 0.01 + 0.01·Cl²        薄翼升力/阻力系数（α = 该叶片的桨距角）
     *   T     = Σ (q·A·Cl·cosα − q·A·Cd·sinα) · η  逐叶片求和（A = 弦长 × 展长）
     * </pre>
     * 桨距角与叶片几何来自子空间里的 {@link BladeControllerBlockEntity}；
     * 一个控制器都没有时退回 {@link PropellerKind} 的当量叶片（旧版黄铜螺旋桨挂帆的情形）。
     */
    @Override
    public double getThrust() {
        final double omega = this.rotationRateRadPerSec();
        if (Math.abs(omega) < 1.0E-3) {
            return 0.0;
        }
        final PropellerKind kind = this.getKind();
        double alongFacing = 0.0;
        if (this.blades.isEmpty()) {
            alongFacing = kind.blades * elementThrust(omega, kind.length * 0.5,
                    kind.chord * kind.length, Math.toRadians(kind.pitchDegrees), kind.efficiency);
        } else {
            for (final BladeData blade : this.blades) {
                alongFacing += elementThrust(omega, blade.length(), blade.area(),
                        Math.toRadians(blade.angleDegrees()), kind.efficiency);
            }
        }
        alongFacing *= kind.forceMultiplier();
        final double clamped = Math.min(alongFacing, kind.maxForce());
        return -Math.signum(omega) * clamped;
    }

    /** 单个叶素的推力 [N]（旧版 {@code PropellerController#computeBladeForce} 的逐叶模型）。 */
    private static double elementThrust(final double omega, final double radius, final double area,
                                        final double pitchRadians, final double efficiency) {
        final double vTan = Math.abs(omega) * radius;
        final double liftCoefficient = 2.0 * Math.PI * pitchRadians;
        final double dragCoefficient = 0.01 + 0.01 * liftCoefficient * liftCoefficient;
        final double dynamicPressure = 0.5 * AIR_DENSITY * vTan * vTan;
        return (dynamicPressure * area * liftCoefficient * Math.cos(pitchRadians)
                - dynamicPressure * area * dragCoefficient * Math.sin(pitchRadians)) * efficiency;
    }

    /** 叶片展长 [m]：有控制器时取最长的那片，否则用方块规格的当量展长。 */
    private double bladeSpan() {
        double span = 0.0;
        for (final BladeData blade : this.blades) {
            span = Math.max(span, blade.length());
        }
        return span > 0.0 ? span : this.getKind().length;
    }

    /** 当前公共桨距角 [度]（仅用于 tooltip；实际受力是逐叶片的）。 */
    private double averagePitchDegrees() {
        if (this.blades.isEmpty()) {
            return this.getKind().pitchDegrees;
        }
        double sum = 0.0;
        for (final BladeData blade : this.blades) {
            sum += blade.angleDegrees();
        }
        return sum / this.blades.size();
    }

    // ==================== 调试信息 ====================

    @Override
    public boolean addToGoggleTooltip(final List<Component> tooltip, final boolean isPlayerSneaking) {
        final boolean added = super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        if (!this.isActive()) {
            return added;
        }
        tooltip.add(Component.literal("    ").append(Component.translatable("vs_clockwork.propeller.blades"))
                .append(Component.literal(" " + this.blades.size())));
        tooltip.add(Component.literal("    ").append(Component.translatable("vs_clockwork.blade_controller.angle"))
                .append(Component.literal(String.format(" %.0f°", this.averagePitchDegrees()))));
        tooltip.add(Component.literal("    ").append(Component.translatable("vs_clockwork.propeller.thrust"))
                .append(Component.literal(String.format(" %.1f N", Math.abs(this.getThrust())))));
        tooltip.add(Component.literal("    ").append(Component.translatable("vs_clockwork.propeller.airflow"))
                .append(Component.literal(String.format(" %.1f m/s", Math.abs(this.getAirflow())))));
        return true;
    }
}
