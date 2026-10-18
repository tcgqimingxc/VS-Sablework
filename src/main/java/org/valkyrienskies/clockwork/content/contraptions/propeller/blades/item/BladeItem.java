package org.valkyrienskies.clockwork.content.contraptions.propeller.blades.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 螺旋桨叶片物品（旧版 {@code blades/item/BladeItem.kt}，一把带耐久的小剑）。
 *
 * <p>旧版把展长写在物品 NBT 的 {@code BladeLength} 里（默认 1.0，由加长 / 切割配方改写）。
 * 1.21.1 移除了物品自由 NBT，展长因此改成注册的
 * {@link org.valkyrienskies.clockwork.index.CWDataComponents#BLADE_LENGTH 数据组件}，
 * 读写统一走 {@link BladeItems}（含旧 NBT 迁移，见 {@link #inventoryTick}）。
 *
 * <p>攻击力沿用旧版：{@code propeller_blade} = 木剑 +2 伤害 / +0.4 攻速、
 * {@code wide_propeller_blade} = +4 / +0.2。
 */
public class BladeItem extends SwordItem {

    private final boolean wide;

    public BladeItem(final Tier tier, final boolean wide, final Properties properties) {
        super(tier, properties);
        this.wide = wide;
    }

    /** 宽叶：弦长更大（0.375 格 vs 0.25 格），同样转速下推力更大。 */
    public boolean isWide() {
        return this.wide;
    }

    /**
     * 旧存档迁移：1.20.1 的 {@code BladeLength} 根 NBT 在 1.20.5+ 被原版搬进了
     * {@code minecraft:custom_data}，这里把它写进组件并清掉旧键（幂等，见 {@link BladeItems})。
     *
     * <p>为什么不放在 {@code read} / 配方里：物品没有"加载回调"，{@code inventoryTick} 是唯一
     * 每件物品都会稳定走到的入口；且它同时覆盖"背包里的老物品"和"从老箱子取出的物品"。
     */
    @Override
    public void inventoryTick(final ItemStack stack, final Level level, final Entity entity, final int slotId,
                              final boolean isSelected) {
        // 只在服务端迁移：组件是会被同步的权威数据，客户端改了也会被下一次同步覆盖；
        // 且客户端读长度时有 legacy 回退（BladeItems#getLength），不会出现"迁移前显示 1 格"的错觉。
        if (level != null && !level.isClientSide) {
            BladeItems.migrateLegacyLength(stack);
        }
        super.inventoryTick(stack, level, entity, slotId, isSelected);
    }

    /**
     * 物品提示：当前展长 / 上限（旧版没有这条提示，属于本次"完整版"按需求补的可见性）。
     *
     * <p>最大长度读服务端配置，所以玩家能直接看出"为什么两片 4 格叶片合不了"。
     */
    @Override
    public void appendHoverText(final ItemStack stack, final Item.TooltipContext context,
                                final List<Component> tooltip, final TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("vs_clockwork.blade.length",
                        BladeItems.getLength(stack), BladeItems.maxLength())
                .withStyle(ChatFormatting.GRAY));
        if (flag.isAdvanced()) {
            // 合成 / 切割规则不占常规提示行：只在 F3+H 高级提示里出现，避免刷屏。
            tooltip.add(Component.translatable("vs_clockwork.blade.length.hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
