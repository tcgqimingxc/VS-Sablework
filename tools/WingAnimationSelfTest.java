// 物理轴承翼面状态机自检（不启动游戏、不依赖 JUnit）。
// 由 tools/selftest_wing_animation.ps1 着 javac 一起编译，直接驱动生产代码 WingAnimation.java。
package org.valkyrienskies.clockwork.content.contraptions.phys.bearing;

/**
 * 用例来自实机反馈的三步复现：
 * <ol>
 *   <li>首次装配 → 打开到 70°；</li>
 *   <li>解体 → 精确回到 0°（不允许停在 0.95 / 4.45°）；</li>
 *   <li>再次装配 → 必须重新完整播放 0→70（不允许跳变）；</li>
 *   <li>再次解体 → 精确 0°。</li>
 * </ol>
 * 另外验证中途反向、端点吸附、partialTick 插值与角度边界。
 */
public final class WingAnimationSelfTest {

    private static int checks;

    private WingAnimationSelfTest() {
    }

    public static void main(final String[] args) {
        testFourFlips();
        testMidFlightReversal();
        testPartialTickInterpolation();
        testBounds();
        System.out.println("[PASS] WingAnimation 自检全部通过，断言数 = " + checks);
    }

    /** 实机三步复现（外加第四次翻转），每个阶段都跑满 80 tick 以确认不会过冲/回弹。 */
    private static void testFourFlips() {
        final WingAnimation animation = new WingAnimation();

        // 阶段 1：首次装配 —— 20 tick 到 70°，之后保持 70°
        int ticks = runUntilSettled(animation, true, 80);
        expect("阶段1 打开耗时", ticks, 20);
        expectFloat("阶段1 进度", animation.getProgress(), 1.0f);
        expectFloat("阶段1 角度", animation.getAngleDegrees(), 70.0f);
        expect("阶段1 isSettled", animation.isSettled(true), true);

        // 阶段 2：解体 —— 必须精确回到 0°（旧实现卡在 0.95 / 4.45°）
        ticks = runUntilSettled(animation, false, 80);
        expect("阶段2 闭合耗时", ticks, 20);
        expectFloat("阶段2 进度", animation.getProgress(), 0.0f);
        expectFloat("阶段2 角度", animation.getAngleDegrees(), 0.0f);
        expect("阶段2 isSettled", animation.isSettled(false), true);

        // 阶段 3：再次装配 —— 必须从 0 完整播放，不允许直接 70°
        animation.tick(true);
        expectFloat("阶段3 首 tick 进度", animation.getProgress(), 0.05f);
        expectFloat("阶段3 首 tick 角度", animation.getAngleDegrees(), 3.5f);
        expect("阶段3 首 tick 未完成", animation.isSettled(true), false);
        ticks = 1 + runUntilSettled(animation, true, 80);
        expect("阶段3 总耗时", ticks, 20);
        expectFloat("阶段3 角度", animation.getAngleDegrees(), 70.0f);

        // 阶段 4：再次解体 —— 仍然精确 0°
        ticks = runUntilSettled(animation, false, 80);
        expect("阶段4 闭合耗时", ticks, 20);
        expectFloat("阶段4 进度", animation.getProgress(), 0.0f);
        expectFloat("阶段4 角度", animation.getAngleDegrees(), 0.0f);
        expect("阶段4 isSettled", animation.isSettled(false), true);

        System.out.println("[PASS] 四次翻转序列 = 70 / 0 / 70 / 0（精确端点）");
    }

    /** 解体到一半又重新装配 / 张开到一半又解体：方向反转，且最终都能精确收敛。 */
    private static void testMidFlightReversal() {
        final WingAnimation animation = new WingAnimation();
        for (int i = 0; i < 10; i++) {
            animation.tick(true);
        }
        expectFloat("中途张开进度", animation.getProgress(), 0.5f);

        // 在 0.5 处反向闭合：再 10 tick 应精确到 0
        int ticks = runUntilSettled(animation, false, 80);
        expect("中途反向闭合耗时", ticks, 10);
        expectFloat("中途反向闭合角度", animation.getAngleDegrees(), 0.0f);

        // 在 0.3 处反向打开：再 14 tick 应精确到 1
        for (int i = 0; i < 6; i++) {
            animation.tick(true);
        }
        expectFloat("二次中途张开进度", animation.getProgress(), 0.3f);
        ticks = runUntilSettled(animation, true, 80);
        expect("二次反向打开耗时", ticks, 14);
        expectFloat("二次反向打开角度", animation.getAngleDegrees(), 70.0f);

        System.out.println("[PASS] 中途目标翻转：从当前进度反向推进并精确收敛（0 与 70）");
    }

    /** partialTick 只影响渲染插值：0 → 上一 tick，1 → 当前 tick，中间值严格夹在两者之间。 */
    private static void testPartialTickInterpolation() {
        final WingAnimation animation = new WingAnimation();
        animation.tick(true); // previous = 0.0, current = 0.05

        expectFloat("插值 partial=0", animation.getAngleDegrees(0.0f), 0.0f);
        expectFloat("插值 partial=1", animation.getAngleDegrees(1.0f), animation.getAngleDegrees());
        expectFloat("插值 partial=0.5", animation.getAngleDegrees(0.5f), 1.75f);
        // 越界 partialTick 必须被夹紧，不允许出现负角度/超过 70°
        expectFloat("插值 partial=-1 被夹紧", animation.getAngleDegrees(-1.0f), 0.0f);
        expectFloat("插值 partial=2 被夹紧", animation.getAngleDegrees(2.0f), animation.getAngleDegrees());

        System.out.println("[PASS] partialTick 插值单调且被夹在 [0,70]");
    }

    /** 任意长时间/任意目标下，进度与角度都不越界，且角度 = 进度 × 70。 */
    private static void testBounds() {
        final WingAnimation animation = new WingAnimation();
        boolean assembled = true;
        for (int i = 0; i < 500; i++) {
            if (i % 7 == 0) {
                assembled = !assembled; // 高频翻转，专门制造时序压力
            }
            animation.tick(assembled);
            final float progress = animation.getProgress();
            final float angle = animation.getAngleDegrees();
            if (progress < 0.0f || progress > 1.0f) {
                throw new AssertionError("进度越界: " + progress + " (tick " + i + ")");
            }
            if (angle < 0.0f || angle > 70.0f) {
                throw new AssertionError("角度越界: " + angle + " (tick " + i + ")");
            }
            expectFloat("角度 = 进度 × 70", angle, progress * 70.0f);
        }
        // 压力测试结束后再给 100 tick 稳定时间，必须精确收敛到目标
        final boolean finalTarget = true;
        for (int i = 0; i < 100; i++) {
            animation.tick(finalTarget);
        }
        expectFloat("压力测试后精确张开", animation.getAngleDegrees(), 70.0f);
        expect("压力测试后 isSettled", animation.isSettled(finalTarget), true);

        System.out.println("[PASS] 500 tick 高频翻转压力下无越界，最终精确收敛");
    }

    // ==================== 工具 ====================

    /** 反复 tick 直到精确落到目标端点（与生产代码同一个 isSettled 判定），返回消耗的 tick 数。 */
    private static int runUntilSettled(final WingAnimation animation, final boolean assembled, final int maxTicks) {
        for (int i = 1; i <= maxTicks; i++) {
            animation.tick(assembled);
            if (animation.isSettled(assembled)) {
                return i;
            }
        }
        throw new AssertionError("动画在 " + maxTicks + " tick 内没有收敛到 " + assembled
                + "，停在 progress=" + animation.getProgress());
    }

    private static void expect(final String what, final int actual, final int expected) {
        checks++;
        if (actual != expected) {
            throw new AssertionError(what + ": 期望 " + expected + "，实际 " + actual);
        }
    }

    private static void expect(final String what, final boolean actual, final boolean expected) {
        checks++;
        if (actual != expected) {
            throw new AssertionError(what + ": 期望 " + expected + "，实际 " + actual);
        }
    }

    private static void expectFloat(final String what, final float actual, final float expected) {
        checks++;
        if (Math.abs(actual - expected) > 1.0e-6f) {
            throw new AssertionError(what + ": 期望 " + expected + "，实际 " + actual);
        }
    }
}
