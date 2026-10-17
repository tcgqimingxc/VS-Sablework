package org.valkyrienskies.clockwork.content.kinetics.sequenced_seat;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.EnumSet;
import java.util.Set;

/**
 * 一条座椅规则：按键组合 + 操作 + 参数（旧版 {@code SequencedSeatRule.kt}）。
 *
 * <p>规则决定"某一侧输出轴当前的转速倍率"，由 {@link SeatRuleList#currentModifier} 逐条询问，
 * 第一条给出非 0 结果的规则生效。
 *
 * <p>与旧版差异：
 * <ul>
 *   <li>参数存储用同一个 {@link SeatValue}（旧版 {@code TURN_DISTANCE} 默认给的是
 *       {@code DistanceValue}，但计算时却强制转成 {@code AngleValue} —— 那是旧版的类型不一致 bug，
 *       这里按操作取对应的值类型，行为与"面板显示什么就用什么"一致。</li>
 *   <li>旧版 {@code matches} 用的是集合相等（按下集合必须与规则完全一致），这里保持一致。</li>
 * </ul>
 */
public class SeatRule {

    private final Set<InputKey> inputKeys;
    private final SeatOperation operation;
    private final SeatValue value;
    /** 旧版的 {@code inAction}：一旦开始动作，就必须按键松开后才允许回到 0。 */
    private boolean inAction;

    public SeatRule(final Set<InputKey> inputKeys, final SeatOperation operation, final SeatValue value) {
        this.inputKeys = inputKeys;
        this.operation = operation;
        this.value = value;
    }

    public Set<InputKey> inputKeys() {
        return this.inputKeys;
    }

    public SeatOperation operation() {
        return this.operation;
    }

    public SeatValue value() {
        return this.value;
    }

    public boolean matches(final Set<InputKey> pressed) {
        return pressed.equals(this.inputKeys);
    }

    /** 这一侧的转速倍率（0 = 不输出）。 */
    public float calculateModifier(final CommandSeatBlockEntity be, final Direction face, final Set<InputKey> pressed) {
        final boolean matches = this.matches(pressed);
        return switch (this.operation) {
            case NOTHING -> 0.0f;
            case TURN_ANGLE -> this.angleRotate(be, face, matches);
            case TURN_DISTANCE -> this.distanceRotate(be, face, matches);
            case MULTIPLY -> matches ? ((SeatValue.MultiplyValue) this.value).multiplier() : 0.0f;
        };
    }

    /** 转到固定角度：倍率 = 剩余角度 / 每刻角度（旧版 {@code angleRotate}）。 */
    private float angleRotate(final CommandSeatBlockEntity be, final Direction face, final boolean matches) {
        if (!this.inAction && !matches) {
            return 0.0f;
        }
        final float target = -((SeatValue.AngleValue) this.value).degrees();
        final float degreesPerTick = KineticBlockEntity.convertToAngular(be.getSpeed());
        return this.rotateTowards(be, face, matches, target, degreesPerTick);
    }

    /** 按"距离"折算的角度增量（旧版 {@code distanceRotate}，用线速度而不是角速度）。 */
    private float distanceRotate(final CommandSeatBlockEntity be, final Direction face, final boolean matches) {
        if (!this.inAction && !matches) {
            return 0.0f;
        }
        final float target = -((SeatValue.DistanceValue) this.value).meters();
        final float metersPerTick = KineticBlockEntity.convertToLinear(be.getSpeed());
        return this.rotateTowards(be, face, matches, target, metersPerTick);
    }

    private float rotateTowards(final CommandSeatBlockEntity be, final Direction face, final boolean matches,
                                final float target, final float speedPerTick) {
        final float away = be.getDegreesAwayFromBase(face);
        float diff = matches ? target - away : -away;
        // 取最短转向（旧版把差值折到 ±180）
        if (diff > 180.0f) {
            diff -= 360.0f;
        } else if (diff < -180.0f) {
            diff += 360.0f;
        }
        if (diff < 0.1f && diff > -0.1f) {
            this.inAction = matches;
            return 0.0f;
        }
        this.inAction = true;
        if (Math.abs(speedPerTick) > Math.abs(diff)) {
            return diff / speedPerTick;
        }
        // 每刻转速与剩余角度方向相反时必须倒转（旧版用乘积 < 0 判断）
        return diff * speedPerTick < 0.0f ? -1.0f : 1.0f;
    }

    public CompoundTag serializeNBT() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("keys", InputKey.toMask(this.inputKeys));
        tag.putInt("operation", this.operation.ordinal());
        if (this.value != null) {
            tag.put("value", this.value.serializeNBT());
        }
        return tag;
    }

    public static SeatRule empty() {
        return new SeatRule(EnumSet.noneOf(InputKey.class), SeatOperation.NOTHING, null);
    }

    public static SeatRule deserializeNBT(final CompoundTag tag) {
        final Set<InputKey> keys = InputKey.fromMask(tag.getInt("keys"));
        final SeatOperation operation = SeatOperation.byOrdinal(tag.getInt("operation"));
        final SeatValue value = operation.defaultValue();
        // 蓝图 / 旧存档里可能没有 value（旧版同样做了容错）
        final Tag stored = tag.get("value");
        if (value != null && stored != null) {
            value.deserializeNBT(stored);
        }
        return new SeatRule(keys, operation, value);
    }
}
