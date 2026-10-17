package org.valkyrienskies.clockwork.content.kinetics.sequenced_seat;

import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * 一条座椅规则的参数值（旧版 {@code SequencedSeatValue.kt}）。
 *
 * <p>与旧版差异：旧版把"面板上怎么调"（{@code configureInput(ScrollInput)}）写进值对象里，
 * 这里改成界面的 {@code step(int)}（按一次 +/- 走一档），值对象因此不依赖任何客户端类，
 * 可以在服务端安全加载。
 */
public interface SeatValue {

    /** 显示文本（旧版 {@code asComponent}）。 */
    Component asComponent();

    Tag serializeNBT();

    void deserializeNBT(Tag tag);

    /** 面板上按 +/- 时调整一档；{@code direction} 为 +1 / -1（步长与范围沿用旧版 ScrollInput 配置）。 */
    void step(int direction);

    /** 角度值 [度]（旧版 {@code AngleValue}，范围 ±180，面板步长 15°）。 */
    final class AngleValue implements SeatValue {

        private int degrees;

        public AngleValue(final int degrees) {
            this.degrees = Mth.clamp(degrees, -180, 180);
        }

        public int degrees() {
            return this.degrees;
        }

        @Override
        public Component asComponent() {
            return Component.literal(this.degrees + "\u00b0");
        }

        @Override
        public Tag serializeNBT() {
            return IntTag.valueOf(this.degrees);
        }

        @Override
        public void deserializeNBT(final Tag tag) {
            this.degrees = Mth.clamp(((IntTag) tag).getAsInt(), -180, 180);
        }

        @Override
        public void step(final int direction) {
            this.degrees = Mth.clamp(this.degrees + 15 * direction, -180, 180);
        }
    }

    /** 距离值 [m]（旧版 {@code DistanceValue}，范围 ≥1，面板步长 1）。 */
    final class DistanceValue implements SeatValue {

        private int meters;

        public DistanceValue(final int meters) {
            this.meters = Math.max(1, meters);
        }

        public int meters() {
            return this.meters;
        }

        @Override
        public Component asComponent() {
            return Component.literal(this.meters + "m");
        }

        @Override
        public Tag serializeNBT() {
            return IntTag.valueOf(this.meters);
        }

        @Override
        public void deserializeNBT(final Tag tag) {
            this.meters = Math.max(1, ((IntTag) tag).getAsInt());
        }

        @Override
        public void step(final int direction) {
            this.meters = Mth.clamp(this.meters + direction, 1, 1024);
        }
    }

    /** 倍率（旧版 {@code MultiplyValue}，范围 ±8，面板步长 0.5）。 */
    final class MultiplyValue implements SeatValue {

        private float multiplier;

        public MultiplyValue(final float multiplier) {
            this.multiplier = Mth.clamp(multiplier, -8.0f, 8.0f);
        }

        public float multiplier() {
            return this.multiplier;
        }

        @Override
        public Component asComponent() {
            return Component.literal(this.multiplier + "x");
        }

        @Override
        public Tag serializeNBT() {
            return FloatTag.valueOf(this.multiplier);
        }

        @Override
        public void deserializeNBT(final Tag tag) {
            this.multiplier = Mth.clamp(((FloatTag) tag).getAsFloat(), -8.0f, 8.0f);
        }

        @Override
        public void step(final int direction) {
            this.multiplier = Mth.clamp(this.multiplier + 0.5f * direction, -8.0f, 8.0f);
        }
    }

    static SeatValue angle(final int degrees) {
        return new AngleValue(degrees);
    }

    static SeatValue distance(final int meters) {
        return new DistanceValue(meters);
    }

    static SeatValue multiply(final float multiplier) {
        return new MultiplyValue(multiplier);
    }
}
