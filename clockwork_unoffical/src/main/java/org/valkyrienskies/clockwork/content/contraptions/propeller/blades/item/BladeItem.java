package org.valkyrienskies.clockwork.content.contraptions.propeller.blades.item;

import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;

/**
 * 螺旋桨叶片物品（旧版 {@code blades/item/BladeItem.kt}，一把带耐久的小剑）。
 *
 * <p>移植差异：旧版把展长写在物品 NBT 的 {@code BladeLength} 里（默认 1.0，切割 / 加长配方改写），
 * 并在 {@code inventoryTick} 里补写默认值；1.21.1 移除了物品 NBT，且那两个自定义配方类型没有移植，
 * 因此展长改成"每种叶片的固有属性"（构造参数），物品类里不再有任何状态。
 *
 * <p>攻击力沿用旧版：{@code propeller_blade} = 木剑 +2 伤害 / +0.4 攻速、{@code wide_propeller_blade} = +4 / +0.2。
 */
public class BladeItem extends SwordItem {

    private final boolean wide;
    private final double bladeLength;

    public BladeItem(final Tier tier, final boolean wide, final double bladeLength, final Properties properties) {
        super(tier, properties);
        this.wide = wide;
        this.bladeLength = bladeLength;
    }

    /** 宽叶：弦长更大（0.375 格 vs 0.25 格），同样转速下推力更大。 */
    public boolean isWide() {
        return this.wide;
    }

    /** 展长 [m]（旧版物品 NBT 的 {@code BladeLength}，默认 1.0）。 */
    public double getBladeLength() {
        return this.bladeLength;
    }
}
