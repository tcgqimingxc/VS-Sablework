package org.valkyrienskies.clockwork.content.kinetics.sequenced_seat;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 一条座椅规则的"做什么"（旧版 {@code SequencedSeatOperation.kt}）。
 *
 * <p>与旧版差异：旧版每种操作还带一个 {@code AllIcons} 图标（GUI 用），
 * 移植后的界面用文字标签 + 原版按钮，不再需要图标（翻译键沿用旧版）。
 */
public enum SeatOperation {

    /** 不做事。 */
    NOTHING("nothing"),
    /** 按住按键时把该侧输出转到固定角度。 */
    TURN_ANGLE("angle"),
    /** 按住按键时按"距离"折算的角度增量。 */
    TURN_DISTANCE("distance"),
    /** 按住按键时按倍率加速该侧输出。 */
    MULTIPLY("multiply");

    private final String key;

    SeatOperation(final String key) {
        this.key = key;
    }

    public Component asComponent() {
        return Component.translatable("sequenced_seat.operation." + this.key);
    }

    /** 该操作的默认参数值（旧版 {@code defaultValue()}）。 */
    public @Nullable SeatValue defaultValue() {
        return switch (this) {
            case NOTHING -> null;
            case TURN_ANGLE -> SeatValue.angle(90);
            case TURN_DISTANCE -> SeatValue.distance(1);
            case MULTIPLY -> SeatValue.multiply(1.0f);
        };
    }

    /** 反序列化用：越界（旧存档 / 破坏的 NBT）退回 {@link #NOTHING}，不抛异常。 */
    public static SeatOperation byOrdinal(final int ordinal) {
        final SeatOperation[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NOTHING;
    }
}
