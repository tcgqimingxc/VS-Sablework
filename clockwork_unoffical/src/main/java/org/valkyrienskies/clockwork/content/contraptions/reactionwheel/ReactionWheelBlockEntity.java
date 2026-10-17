package org.valkyrienskies.clockwork.content.contraptions.reactionwheel;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelReactionWheel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.valkyrienskies.clockwork.config.ClockworkConfig;

/**
 * 反作用轮方块实体。
 *
 * <p>对应旧版 {@code content/physicalities/reactionwheel/ReactionWheelBlockEntity} +
 * {@code content/forces/ReactionWheelController}。
 *
 * <p><b>施力链路（与旧版的差异）</b>：旧版把"每刻转速"同步给挂在飞船上的
 * {@code ReactionWheelController}，控制器直接 {@code applyWorldTorque(-dir · rpm · 64 · 倍率)} ——
 * 那是个凭空造力矩的"魔法"实现，不守恒角动量。
 * Sable 2.0.5 内置了 {@code ReactionWheelManager}：它每物理子步把
 * {@code Σ 质量 × 惯量 × 轮的角速度} 的<b>变化量</b>作为反向角冲量施加给子空间，
 * 也就是说只要实现 {@link BlockEntitySubLevelReactionWheel#sable$getAngularVelocity} 返回轮子自身的
 * 角速度（local 坐标系、rad/s），角动量守恒、加减速的过渡过程都会自动正确。
 * 因此这里<b>不</b>再有任何手动施力代码。
 *
 * <p>副作用：轮子加速/减速时才会产生力矩，匀速旋转不再持续给力矩（旧版会一直给）。
 * 这是物理上正确的行为，也是"优先复用 Sable 原语"的直接结果。
 */
public class ReactionWheelBlockEntity extends KineticBlockEntity implements BlockEntitySubLevelReactionWheel {

    public ReactionWheelBlockEntity(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void sable$getAngularVelocity(final Vector3d dest) {
        final Direction.Axis axis = this.getBlockState().getValue(ReactionWheelBlock.AXIS);
        final double rpm = this.getSpeed() * ClockworkConfig.server().reactionWheelEffectiveness.get();
        // RPM -> rad/s
        final double omega = rpm / 60.0 * 2.0 * Math.PI;
        switch (axis) {
            case X -> dest.set(omega, 0.0, 0.0);
            case Y -> dest.set(0.0, omega, 0.0);
            case Z -> dest.set(0.0, 0.0, omega);
        }
    }
}
