package org.valkyrienskies.clockwork.integration.ponder.instruction;

import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.AnimateElementInstruction;
import net.minecraft.world.phys.Vec3;

/**
 * 给一个独立结构段施加"初速度 + 逐渐衰减到 0"的平移与旋转 —— Ponder 里模拟"飞船被物理引擎接管"的效果。
 *
 * <p>对应旧版 {@code content/ponders/AnimateShipSectionInstruction.kt}（VS2 时代的
 * {@code moveSectionAsShip}）。旧版靠这个类把结构当成"有惯性"的飞船推着走/转；
 * 1.21.1 没有 VS2 的指令，但 Ponder 的 {@link AnimateElementInstruction} 语义完全一样，
 * 因此这里逐行移植，场景的观感与旧版一致。
 *
 * <p>实现要点：
 * <ul>
 *   <li>父类的 setter/getter 用不到（旧版也传的空实现），真正的位置由本类每刻
 *       追加 {@code 初速度 × 剩余比例} 得到，因此速度线性衰减；</li>
 *   <li>父类 {@code firstTick} 会做 {@code totalDelta / totalTicks} 的除法，所以
 *       <b>ticks 必须 > 0</b>（调用方 {@code ClockworkPonderScenes#animateAsShip} 传入的也都是正数）。</li>
 * </ul>
 *
 * <p>单位：{@code initialVelocity} 是 格/tick，{@code initialRotationVelocity} 是 度/tick（欧拉角）。
 */
public class AnimateShipSectionInstruction extends AnimateElementInstruction<WorldSectionElement> {

    private final Vec3 initialVelocity;
    private final Vec3 initialRotationVelocity;

    public AnimateShipSectionInstruction(final ElementLink<WorldSectionElement> link, final int ticks,
                                         final Vec3 initialVelocity, final Vec3 initialRotationVelocity) {
        // 父类的 target/delta 与 setter/getter 本类都不使用，和旧版一样传空实现
        super(link, Vec3.ZERO, ticks, (element, offset) -> {
        }, element -> Vec3.ZERO);
        this.initialVelocity = initialVelocity;
        this.initialRotationVelocity = initialRotationVelocity;
    }

    @Override
    public void tick(final PonderScene scene) {
        super.tick(scene);
        // 进度 0 -> 1，速度按 (1 - 进度) 线性衰减（等价于向零向量 lerp）
        final double remaining = 1.0 - (double) this.remainingTicks / (double) this.totalTicks;
        this.element.setAnimatedOffset(
                this.element.getAnimatedOffset().add(this.initialVelocity.scale(remaining)), false);
        this.element.setAnimatedRotation(
                this.element.getAnimatedRotation().add(this.initialRotationVelocity.scale(remaining)), false);
    }
}
