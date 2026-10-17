package org.valkyrienskies.clockwork.content.contraptions.phys.gimbal;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.valkyrienskies.clockwork.config.ClockworkConfig;
import org.valkyrienskies.clockwork.content.contraptions.phys.bearing.AbstractBearingBlockEntity;
import org.valkyrienskies.clockwork.content.contraptions.phys.bearing.BearingControl;
import org.valkyrienskies.clockwork.content.contraptions.phys.bearing.BearingControls;

import java.util.List;

/**
 * 万向轴承方块实体。
 *
 * <p>对应旧版 {@code content/contraptions/phys/gimbal/GimbalBearingBlockEntity}（970 行）。
 *
 * <p><b>约束链路</b>：装配链路与物理轴承完全一样（共用 {@link AbstractBearingBlockEntity}），
 * 只把约束换成 Sable 的 {@code FreeConstraintConfiguration} —— 它"不强加任何锁定"，
 * 由我们随后用 {@code setMotor} 逐关节轴分配自由度，等价于旧版的
 * {@code VSD6Joint} / {@code VSSphericalJoint}。
 * 关节坐标系（{@code orientation} 参数，语义见 Sable 的 Javadoc：
 * "Motor axes will be relative to this frame"）被旋转到"轴承朝向 = 坐标系 X 轴"，
 * 因此 ANGULAR_X 是绕朝向轴、ANGULAR_Y / ANGULAR_Z 是两个垂直的摆动轴，含义确定。
 *
 * <p><b>与旧版差异</b>：
 * <ul>
 *   <li>旧版球形关节带最大摆角限位锥（{@code LimitCone}）；Sable 的 {@code setMotor}
 *       不暴露限位参数，所以摆角不限（见汇报"未完成项"）。</li>
 *   <li>旧版用红石方向信号算目标轴、再用 D6 关节电机把轴承轴掰过去；
 *       这里只保留"自由 / 锁定"两档，锁定档把子结构固定在装配时的相对姿态。</li>
 * </ul>
 */
public class GimbalBearingBlockEntity extends AbstractBearingBlockEntity {

    private static final MutableComponent MODE_TITLE =
            Component.translatable("vs_clockwork.gimbal_bearing.mode");

    /** 关节平移阻尼：照抄 PhysicsAssemblerBlockEntity.LINEAR_DAMPING。 */
    private static final double LINEAR_DAMPING = 50.0;
    /** 平移刚度给一个极小值（而非 0），否则关节会在受力时漂移。 */
    private static final double LINEAR_STIFFNESS = 0.000001;

    /** 万向轴承模式（对应旧版 {@code GimbalMode}）。 */
    public enum GimbalMode implements INamedIconOptions {
        /** 自由云台：三个转动自由度全部放开（等价旧版球形关节）。 */
        FREE(AllIcons.I_CONFIG_UNLOCKED, "vs_clockwork.gimbal_bearing.mode.free"),
        /** 锁定云台：把子结构固定在装配时的相对姿态。 */
        LOCKED(AllIcons.I_CONFIG_LOCKED, "vs_clockwork.gimbal_bearing.mode.locked");

        private final AllIcons icon;
        private final String translationKey;

        GimbalMode(final AllIcons icon, final String translationKey) {
            this.icon = icon;
            this.translationKey = translationKey;
        }

        @Override
        public AllIcons getIcon() {
            return this.icon;
        }

        @Override
        public String getTranslationKey() {
            return this.translationKey;
        }
    }

    private final BearingControl freeControl = new BearingControls.Free();
    private ScrollOptionBehaviour<GimbalMode> modeBehaviour;

    public GimbalBearingBlockEntity(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(final List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        this.modeBehaviour = new ScrollOptionBehaviour<>(GimbalMode.class, MODE_TITLE, this, new ModeValueBox());
        behaviours.add(this.modeBehaviour);
    }

    @Override
    protected Direction getFacing() {
        return this.getBlockState().getValue(GimbalBearingBlock.FACING);
    }

    @Override
    protected BearingControl getControl() {
        // 万向轴承的目标角不由输入轴推进；固定用"自由铰链"策略，
        // 真正的自由度分配在 applyServo / applyFriction 里按模式处理。
        return this.freeControl;
    }

    public GimbalMode getMode() {
        return this.modeBehaviour == null ? GimbalMode.FREE : this.modeBehaviour.get();
    }

    @Override
    protected PhysicsConstraintConfiguration<?> createConstraint(final Vector3d anchorPos, final Vector3d attachPos,
                                                                final Vector3d axis) {
        final Quaterniond frame = new Quaterniond().rotationTo(new Vector3d(1.0, 0.0, 0.0), new Vector3d(axis));
        return new FreeConstraintConfiguration(anchorPos, attachPos, frame);
    }

    @Override
    protected void applyServo(final PhysicsConstraintHandle constraint, final BearingControl.Context ctx,
                              final double goalRadians, final double stiffness, final double damping) {
        this.applyFriction(constraint, this.getControl());
        if (this.getMode() != GimbalMode.LOCKED) {
            return;
        }
        // 锁定：三个转动轴都伺服回装配时的相对姿态（目标 0）
        final double lockStiffness = ClockworkConfig.server().bearingLockStiffness.getF();
        final double lockDamping = ClockworkConfig.server().bearingLockDamping.getF();
        for (final ConstraintJointAxis angular : ConstraintJointAxis.ANGULAR) {
            constraint.setMotor(angular, 0.0, lockStiffness, lockDamping, false, 0.0);
        }
        constraint.setContactsEnabled(false);
    }

    @Override
    protected void applyFriction(final PhysicsConstraintHandle constraint, final BearingControl control) {
        // 平移焊死、转动几乎无阻力（旧版球形关节同样不提供转动阻力）
        constraint.setMotor(ConstraintJointAxis.LINEAR_X, 0.0, LINEAR_STIFFNESS, LINEAR_DAMPING, false, 0.0);
        constraint.setMotor(ConstraintJointAxis.LINEAR_Y, 0.0, LINEAR_STIFFNESS, LINEAR_DAMPING, false, 0.0);
        constraint.setMotor(ConstraintJointAxis.LINEAR_Z, 0.0, LINEAR_STIFFNESS, LINEAR_DAMPING, false, 0.0);
        final double friction = control.friction();
        for (final ConstraintJointAxis angular : ConstraintJointAxis.ANGULAR) {
            constraint.setMotor(angular, 0.0, 0.0, friction, false, 0.0);
        }
        constraint.setContactsEnabled(false);
    }

    private static class ModeValueBox extends CenteredSideValueBoxTransform {

        ModeValueBox() {
            super((state, direction) -> direction.getAxis() != state.getValue(GimbalBearingBlock.FACING).getAxis());
        }

        @Override
        protected Vec3 getSouthLocation() {
            return net.createmod.catnip.math.VecHelper.voxelSpace(8, 8, 15.75);
        }

        @Override
        public float getScale() {
            return 0.35f;
        }
    }
}
