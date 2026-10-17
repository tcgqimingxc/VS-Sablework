package org.valkyrienskies.clockwork.content.kinetics.sequenced_seat;

import java.util.EnumSet;
import java.util.Set;

/**
 * 命令座椅能识别的玩家按键（旧版 {@code InputKey.kt}）。
 *
 * <p>序号的位就是它在网络包 / NBT 里的掩码位，与旧版 {@code 1 shl ordinal} 完全一致。
 */
public enum InputKey {
    FORWARD,
    BACKWARD,
    LEFT,
    RIGHT,
    JUMP;

    public int mask() {
        return 1 << this.ordinal();
    }

    /** 按键集合 → 位掩码（旧版 {@code InputKey.asInt}）。 */
    public static int toMask(final Set<InputKey> keys) {
        int mask = 0;
        for (final InputKey key : keys) {
            mask |= key.mask();
        }
        return mask;
    }

    /** 位掩码 → 按键集合（旧版 {@code InputKey.fromInt}）。 */
    public static Set<InputKey> fromMask(final int mask) {
        final Set<InputKey> keys = EnumSet.noneOf(InputKey.class);
        for (final InputKey key : values()) {
            if ((mask & key.mask()) != 0) {
                keys.add(key);
            }
        }
        return keys;
    }
}
