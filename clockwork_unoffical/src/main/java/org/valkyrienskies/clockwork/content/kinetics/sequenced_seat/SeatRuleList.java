package org.valkyrienskies.clockwork.content.kinetics.sequenced_seat;

import net.minecraft.core.Direction;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.Rotation;

/**
 * 一侧输出轴的规则表（旧版 {@code SequencedSeatRuleList.kt}）。
 *
 * <p>固定 {@link #MAX_RULES} 条槽位，逐条询问 {@link SeatRule#calculateModifier}，
 * 第一条非 0 的规则生效（旧版同样是"取第一个想要生效的"）。
 */
public class SeatRuleList {

    /** 每侧的规则槽位数（旧版 {@code MAX_RULES = 5}）。 */
    public static final int MAX_RULES = 5;

    private final SeatRule[] rules = new SeatRule[MAX_RULES];

    public SeatRuleList() {
        for (int i = 0; i < MAX_RULES; i++) {
            this.rules[i] = SeatRule.empty();
        }
    }

    public SeatRule getRule(final int index) {
        return this.rules[index];
    }

    public void setRule(final int index, final SeatRule rule) {
        this.rules[index] = rule;
    }

    public void addKey(final int index, final InputKey key) {
        this.rules[index].inputKeys().add(key);
    }

    public void removeKey(final int index, final InputKey key) {
        this.rules[index].inputKeys().remove(key);
    }

    /** 换操作时重置参数为该操作的默认值（旧版 {@code setOperation}）。 */
    public void setOperation(final int index, final SeatOperation operation) {
        this.rules[index] = new SeatRule(this.rules[index].inputKeys(), operation, operation.defaultValue());
    }

    /** 当前这一侧应该用的倍率（0 = 不输出）。 */
    public float currentModifier(final CommandSeatBlockEntity be, final Direction face) {
        for (final SeatRule rule : this.rules) {
            final float result = rule.calculateModifier(be, face, be.pressedKeys());
            if (result != 0.0f) {
                return result;
            }
        }
        return 0.0f;
    }

    public ListTag serializeNBT() {
        final ListTag list = new ListTag();
        for (final SeatRule rule : this.rules) {
            list.add(rule.serializeNBT());
        }
        return list;
    }

    public void deserializeNBT(final ListTag tag) {
        for (int i = 0; i < MAX_RULES; i++) {
            this.rules[i] = i < tag.size() ? SeatRule.deserializeNBT(tag.getCompound(i)) : SeatRule.empty();
        }
    }

    /**
     * 默认规则表（旧版 {@code defaultList(Rotation)}）：第 2 条规则是"按住对应方向键 = 1 倍输出"，
     * 四个侧面各绑定一个方向键，于是出厂状态就是一个"方向键直接驱动四轴"的变速箱。
     */
    public static SeatRuleList defaultList(final Rotation rotation) {
        final SeatRuleList list = new SeatRuleList();
        list.setOperation(1, SeatOperation.MULTIPLY);
        list.addKey(1, switch (rotation) {
            case NONE -> InputKey.FORWARD;
            case CLOCKWISE_90 -> InputKey.RIGHT;
            case CLOCKWISE_180 -> InputKey.BACKWARD;
            case COUNTERCLOCKWISE_90 -> InputKey.LEFT;
        });
        return list;
    }
}
