package org.valkyrienskies.clockwork.content.contraptions.client;

import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.core.Direction;

/**
 * 物理机械（{@code content/contraptions/**}）渲染器共用的姿态工具。
 *
 * <h2>为什么不能直接用 Catnip 的 {@code CachedBuffers#partialFacing}</h2>
 * <p>{@code partialFacing(model, state, facing)} 内部是 {@code rotateToFace(facing)}
 * = {@code T(0.5)·Ry(horizontalAngle(facing))·Rx(verticalAngle(facing))·T(-0.5)}
 * （javap 反编译 {@code ponder-neoforge-1.0.82} 核实），它把<b>模型自身的 +Z（南）轴</b>
 * 转到 {@code facing} —— 这是 Create 局部模型的约定（Create 的 {@code AllPartialModels}
 * 都是"朝南导出"的，所以 Create 的 {@code BearingRenderer} 才那么用）。
 *
 * <p>本模组从旧版搬来的方块局部模型（{@code gyro/base}、{@code phys_bearing/*}、
 * {@code propeller_bearing/top}、{@code copter_bearing/*}、{@code gimbal_bearing/*}）
 * 都是<b>以 {@code facing=up} 为默认姿态导出</b>的，证据是它们与同一目录下的方块状态模型
 * 共用坐标系：{@code phys_bearing/shaft} 是 y=0..1 的底板、{@code copter_bearing/piston_*}
 * 是 y=6..14 的四根立柱、{@code propeller_bearing/top} 是 y=12..16 的顶盖，
 * 而 {@code blockstates/*.json} 里 {@code facing=up} 的变体没有任何 x/y 旋转。
 *
 * <p>所以这类模型必须用「与 blockstate JSON 的 x/y 完全等价」的旋转：
 * <pre>
 *   facing=up     x=0   y=0     → identity
 *   facing=down   x=180 y=0     → Rx(180)
 *   facing=north  x=90  y=0     → Rx(-90)
 *   facing=south  x=90  y=180   → Ry(180)·Rx(-90)
 *   facing=east   x=90  y=90    → Ry(-90)·Rx(-90)
 *   facing=west   x=90  y=270   → Ry(90)·Rx(-90)
 * </pre>
 * Create 惯用的那对角度恰好逐项等于上表
 * （{@code Ry(horizontalAngle(facing.getOpposite()))·Rx(-90 - verticalAngle(facing))}），
 * 已用离线探针把结果与 {@code BlockModelRotation.by(x, y).getRotation().getMatrix()}
 * 逐个 facing 比对：旋转部分完全一致，且都以方块中心 (0.5,0.5,0.5) 为支点，
 * 因此这里直接复用这对角度，不再引入第二套朝向约定。
 */
public final class ContraptionRenderUtil {

    private ContraptionRenderUtil() {
    }

    /**
     * 把"以 {@code facing=up} 导出"的局部模型摆到 {@code facing} 方向。
     *
     * <p>副作用直接作用在传入的 {@link SuperByteBuffer} 上（Flywheel 的变换栈是就地累加的），
     * 调用方负责先 {@code reset()} 拿到干净缓冲。
     */
    public static SuperByteBuffer orientToFacing(final SuperByteBuffer buffer, final Direction facing) {
        buffer.center();                                                            // T(0.5,0.5,0.5)
        buffer.rotateYDegrees(AngleHelper.horizontalAngle(facing.getOpposite()));   // 竖直朝向时该角为 0
        buffer.rotateXDegrees(-90.0f - AngleHelper.verticalAngle(facing));
        buffer.uncenter();                                                          // T(-0.5,-0.5,-0.5)
        return buffer;
    }
}
