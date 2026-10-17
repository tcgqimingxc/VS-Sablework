package org.valkyrienskies.clockwork.content.contraptions.phys.bearing;

/**
 * 物理轴承四片翼面的开合动画 —— 纯客户端、纯逻辑的状态机（不依赖任何 Minecraft 类，
 * 因此可以被 {@code tools/selftest_wing_animation.ps1} 直接编译运行）。
 *
 * <p><b>单一真相源</b>：目标状态只有服务端经 {@code writeClient/readClient} 同步下来的
 * {@link AbstractBearingBlockEntity#isAssembled()}（即 {@code subLevelID != null}）。
 * 本类<b>不保存</b>"是否已装配 / 是否正在开 / 是否已开"之类的布尔副本，
 * 只有一个连续的 {@link #progress}（0 = 完全闭合，1 = 完全张开）。
 * 客户端每 tick 调 {@link #tick(boolean)}，把 progress 以固定步长朝目标推进。
 *
 * <p><b>为什么不再照抄旧版的两个布尔量</b>：旧版 {@code open} / {@code opening} 是"到顶 / 正在开"
 * 的闩锁，只在 progress 到 1 时置位、到 0 时复位；一旦因为同步时序停在中途，闩锁就与真实状态
 * 脱节。上一版移植正是被 {@code wingsOpened == true && wingsOpenProgress == 0.95} 这个不一致状态
 * 卡住：解体时只退了一 tick 就冻结，再次装配时跳过打开动画直接跳到 70°。
 * 这里把两个布尔量全部删掉，方向完全由 {@link #tick(boolean)} 的参数决定。
 *
 * <p><b>目标翻转时的"起点"</b>：不需要额外的 reset 方法。progress 本身就是动画起点，
 * 翻转时它保留当前值、只是把推进方向反过来 —— 这天然满足"解体到一半又装配"的连续播放；
 * 而一旦朝一个方向走到底，progress 会 snap 到精确的 0 或 1，所以
 * "装配 → 解体 → 装配 → 解体"任意次之后必然精确收敛到 1 / 0 / 1 / 0，不会停在 0.95。
 *
 * <p><b>角度约定</b>：张开目标 = {@link #OPEN_DEGREES}（旧版 {@code getWingRotOffset} 的
 * {@code openProgressMax = 70f}），闭合目标 = {@link #CLOSED_DEGREES}（精确 0°），
 * 中间值线性插值，单调、无分支跳变。
 */
public final class WingAnimation {

    /** 完全张开时翼面相对静止姿态的角度 [度]（旧版 {@code getWingRotOffset} 的 70f）。 */
    public static final float OPEN_DEGREES = 70.0f;

    /** 完全闭合的目标角度 [度]，必须精确为 0。 */
    public static final float CLOSED_DEGREES = 0.0f;

    /**
     * 每客户端 tick 的进度增量：0.05 → 20 tick（约 1 秒）走完 0↔1。
     * 与旧版 {@code tickAnimationLogic} 的 {@code openProgress += 0.05f} 一致。
     */
    public static final float STEP_PER_TICK = 0.05f;

    /**
     * 距端点小于该值时直接 snap 到端点。
     * 0.05f 无法被二进制浮点精确表示，累加 20 次会得到 1.0000001 / -1.5e-8 之类的结果；
     * 不 snap 的话"精确闭合 0°"和 {@link #isSettled(boolean)} 都会失效。
     */
    private static final float SNAP_EPSILON = 1.0e-4f;

    /** 当前张开进度，范围 [0,1]；本类唯一的状态字段。 */
    private float progress;

    /** 上一 tick 的进度，只用于渲染的 partialTick 插值，不参与状态判定。 */
    private float previousProgress;

    /**
     * 客户端每游戏刻调用一次。
     *
     * @param assembled 服务端同步的 {@code isAssembled()}：true = 目标完全张开，false = 目标完全闭合
     */
    public void tick(final boolean assembled) {
        this.previousProgress = this.progress;

        if (assembled) {
            if (this.progress < 1.0f) {
                this.progress += STEP_PER_TICK;
                if (this.progress >= 1.0f - SNAP_EPSILON) {
                    this.progress = 1.0f;
                }
            }
        } else if (this.progress > 0.0f) {
            this.progress -= STEP_PER_TICK;
            if (this.progress <= SNAP_EPSILON) {
                this.progress = 0.0f;
            }
        }
    }

    /**
     * 当前翼面角度 [度]，范围 [0, 70]；完全闭合时精确为 0。
     *
     * <p>这是状态机"当前值"，渲染器应优先用 {@link #getAngleDegrees(float)} 取带 partialTick
     * 插值的版本，避免 20 tick/秒的阶梯感。
     */
    public float getAngleDegrees() {
        return this.progress * OPEN_DEGREES;
    }

    /**
     * 渲染用插值角度：在上一 tick 与当前 tick 的进度之间按 {@code partialTick} 线性插值。
     *
     * <p>纯视觉平滑，<b>不</b>参与状态判定；即使 partialTick 异常也只会被夹到 [0,1]。
     */
    public float getAngleDegrees(final float partialTick) {
        final float alpha = Math.max(0.0f, Math.min(1.0f, partialTick));
        final float interpolated = this.previousProgress + (this.progress - this.previousProgress) * alpha;
        return interpolated * OPEN_DEGREES;
    }

    /** 当前张开进度 [0,1]（自检 / 调试用）。 */
    public float getProgress() {
        return this.progress;
    }

    /** 是否已经静止在目标姿态（进度精确等于目标端点，不含阈值）。 */
    public boolean isSettled(final boolean assembled) {
        return assembled ? this.progress == 1.0f : this.progress == 0.0f;
    }
}
