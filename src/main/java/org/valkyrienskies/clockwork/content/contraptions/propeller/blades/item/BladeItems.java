package org.valkyrienskies.clockwork.content.contraptions.propeller.blades.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.valkyrienskies.clockwork.config.CWServer;
import org.valkyrienskies.clockwork.config.ClockworkConfig;
import org.valkyrienskies.clockwork.index.CWDataComponents;

/**
 * 叶片展长（旧版物品 NBT 键 {@code BladeLength}）的唯一读写入口。
 *
 * <p>对应旧版 {@code blades/item/BladeItem.kt}、{@code CraftingTableBladeRecipe.kt}、
 * {@code CuttingBladeRecipe.kt} 与 {@code BladeData.kt} 里所有 {@code getDouble("BladeLength")} /
 * {@code putDouble("BladeLength", …)} 的调用点。旧版这些地方各写各的（默认值散落在
 * {@code BladeItem#inventoryTick}、{@code BladeData.fromTag} 的 {@code ?: 1.0} 等），
 * 本移植统一收在这里，保证"默认值 / 上下限 / 旧存档迁移"只有一份定义。
 *
 * <p><b>与旧版的差异</b>
 * <ul>
 *   <li>旧版是 {@code double}（切割下限 0.25）；本移植按需求改为<b>整数格</b>，
 *       默认 1、最低 1、上限由 {@code CWServer.maxBladeSize}（旧版同名配置，默认 8）给出。</li>
 *   <li>存储从物品 NBT 换成 {@link CWDataComponents#BLADE_LENGTH 数据组件}
 *       （原因见该字段注释：1.21.1 没有自由 NBT，且组件自带持久化 + 同步）。</li>
 *   <li>旧版在 {@code BladeItem#inventoryTick} 里"发现 0 就补 1"；这里缺失即默认，
 *       另外保留一条<b>旧存档迁移</b>路径：1.20.1 的物品根 NBT 在 1.20.5+ 会被原版搬进
 *       {@code minecraft:custom_data}，{@link #migrateLegacyLength} 把其中的 {@code BladeLength}
 *       读出来写进组件并删掉旧键（见 {@link BladeItem#inventoryTick}）。</li>
 * </ul>
 */
public final class BladeItems {

    /** 默认展长 [格]（旧版 {@code BladeLength} 的默认 1.0）。 */
    public static final int DEFAULT_LENGTH = 1;
    /** 允许的最小展长 [格]：切割到底也保留 1 格（旧版下限 0.25，本移植按需求取整为 1）。 */
    public static final int MIN_LENGTH = 1;
    /** 配置还没加载（或读不到）时的展长上限兜底，与旧版 {@code maxBladeSize = 8.0} 一致。 */
    public static final int FALLBACK_MAX_LENGTH = 8;
    /** 旧版写在物品根 NBT 里的键名（迁移用）。 */
    public static final String LEGACY_LENGTH_KEY = "BladeLength";

    private BladeItems() {
    }

    /** 当前叶片展长 [格]；不是叶片、或没有组件时返回 {@link #DEFAULT_LENGTH}。 */
    public static int getLength(final ItemStack stack) {
        if (!(stack.getItem() instanceof BladeItem)) {
            return DEFAULT_LENGTH;
        }
        final Integer stored = stack.get(CWDataComponents.BLADE_LENGTH.get());
        if (stored != null) {
            return clamp(stored);
        }
        final int legacy = legacyLength(stack);
        return legacy == 0 ? DEFAULT_LENGTH : clamp(legacy);
    }

    /**
     * 写入展长（自动夹到 [{@link #MIN_LENGTH}, {@link #maxLength()}]）。
     *
     * <p>值为默认值时<b>移除组件</b>而不是存一个 1：这样"切割后回到 1 格"的叶片与刚合成的叶片
     * 组件完全一致，能正常堆叠（组件不同 = 不能堆叠）。
     */
    public static void setLength(final ItemStack stack, final int length) {
        if (!(stack.getItem() instanceof BladeItem)) {
            return;
        }
        final int clamped = clamp(length);
        if (clamped == DEFAULT_LENGTH) {
            stack.remove(CWDataComponents.BLADE_LENGTH.get());
        } else {
            stack.set(CWDataComponents.BLADE_LENGTH.get(), clamped);
        }
    }

    /** 展长上限 [格]：读服务端配置（旧版 {@code ClockworkConfig.SERVER.maxBladeSize}）。 */
    public static int maxLength() {
        final CWServer server = ClockworkConfig.server();
        if (server == null) {
            // 配置在 mod 构造期之后才可用；配方匹配 / 渲染都可能更早碰到这里。
            return FALLBACK_MAX_LENGTH;
        }
        return Math.max(MIN_LENGTH, server.maxBladeSize.get());
    }

    /** 夹到合法区间；所有对外写入口都经过它，避免"配置调小后老存档里的超长叶片"越界。 */
    public static int clamp(final int length) {
        return Mth.clamp(length, MIN_LENGTH, maxLength());
    }

    /**
     * 读旧版 NBT 里的 {@code BladeLength}；没有则返回 0（旧版把 0 也当成"未设置"）。
     *
     * <p>1.20.1 写的是 {@code putDouble}，所以统一用 {@code getDouble} 读：1.21.1 的
     * {@code CompoundTag#getDouble} 对整数标签同样会转成 double（{@code getNumber}），两种老数据都能读。
     */
    public static int legacyLength(final ItemStack stack) {
        final CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null || !custom.contains(LEGACY_LENGTH_KEY)) {
            return 0;
        }
        return (int) Math.round(custom.copyTag().getDouble(LEGACY_LENGTH_KEY));
    }

    /**
     * 把旧 NBT 的 {@code BladeLength} 迁移成数据组件，并从 {@code minecraft:custom_data} 里删掉旧键。
     *
     * <p>为什么挂在 {@code inventoryTick} 而不是读的时候顺手写：读路径（渲染、配方匹配、
     * 推力求值）必须是纯函数，尤其客户端渲染每帧都会调用；写入只发生在物品真正存在于背包/容器里时
     * 才安全。迁移本身是幂等的，重复调用无副作用。
     */
    public static void migrateLegacyLength(final ItemStack stack) {
        final CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null || !custom.contains(LEGACY_LENGTH_KEY)) {
            return;
        }
        final CompoundTag tag = custom.copyTag();
        final int legacy = (int) Math.round(tag.getDouble(LEGACY_LENGTH_KEY));
        tag.remove(LEGACY_LENGTH_KEY);
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
        }
        if (stack.get(CWDataComponents.BLADE_LENGTH.get()) == null) {
            setLength(stack, legacy == 0 ? DEFAULT_LENGTH : legacy);
        }
    }
}
