package org.valkyrienskies.clockwork.content.curiosities.tools.gravitron;

/**
 * 重力枪表盘指针的角度（旧版 {@code GravitronState.getDialAngle} + 服务器发的 {@code GravitronDialPacket}）。
 *
 * <p>指针指示的是"抓住的结构有多重"：质量占 {@code gravitronMaxGrabMass} 的比例映射到 10°..350°。
 * 角度由服务端在抓住/放开时通过 {@link GravitronDialPacket} 发给玩家，客户端把它缓动到指针上。
 *
 * <p>为什么不放在 {@code client/} 包里：这个类只保存几个 float，没有任何客户端专用类型，
 * 这样 {@link GravitronDialPacket} 的处理器（通用代码，服务端也可能加载）不必引用客户端类。
 * 缓动用 {@link System#nanoTime()} 而不是渲染帧计数，因为写入发生在网络线程、读取在渲染线程。
 */
public final class GravitronDialState {

    /** 空载/满负载对应的指针角度 [度]（旧版 {@code mapValueToAngle}：value∈[0,100] → 10..350）。 */
    private static final float MIN_ANGLE = 10f;
    private static final float MAX_ANGLE = 350f;
    /** 指针从旧角度扫到新角度的时间 [ns]：旧版按 32 个 partial tick 缓动，约 0.5 s。 */
    private static final long SWEEP_NANOS = 600_000_000L;

    private static volatile float from;
    private static volatile float to;
    private static volatile long sweepStartNanos = System.nanoTime();

    private GravitronDialState() {
    }

    /** 服务端侧：把质量换算成指针角度（超出上限的结构会被抓住逻辑拦下，这里再夹一次保险）。 */
    public static float angleForMass(final double mass, final double maxMass) {
        if (!(maxMass > 0.0)) {
            return MIN_ANGLE;
        }
        final float fraction = (float) Math.min(1.0, Math.max(0.0, mass / maxMass));
        // 旧版写的是 value * 3.5f + 10，但注释与端点都表明斜率是 (350 - 10) / 100 = 3.4，这里按 3.4 算
        return MIN_ANGLE + fraction * (MAX_ANGLE - MIN_ANGLE);
    }

    /** 收到服务端角度：从"当前实际显示的角度"开始扫，避免连续抓取时指针跳变。 */
    public static void set(final float angle) {
        final float current = current();
        from = current;
        to = angle;
        sweepStartNanos = System.nanoTime();
    }

    /** 渲染用：当前指针角度 [度]。 */
    public static float current() {
        final float start = from;
        final float end = to;
        if (start == end) {
            return end;
        }
        final long elapsed = System.nanoTime() - sweepStartNanos;
        if (elapsed >= SWEEP_NANOS) {
            return end;
        }
        final float t = elapsed / (float) SWEEP_NANOS;
        // 旧版用的是 EaseHelper.easeOutOvershoot（回弹一下再停住），常数照抄
        final float c1 = 1.70158f;
        final float c3 = c1 + 1f;
        final float eased = 1f + c3 * (t - 1f) * (t - 1f) * (t - 1f) + c1 * (t - 1f) * (t - 1f);
        return start + (end - start) * eased;
    }
}
