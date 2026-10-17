package org.valkyrienskies.clockwork.content.logistics.gas.backtank;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.clockwork.content.logistics.gas.GasMix;

import java.util.List;

/**
 * 气体背罐的物品形态：可以像胸甲一样穿在身上，随身携带一份气体。
 *
 * <p>对应旧版 {@code GasBackTankItem}（{@code BlockItem} + 原版 {@link Equipable}，
 * 装备槽 {@code CHEST}）。旧版还带 Create 的 {@code BacktankItem} 集成（给装备供气）
 * 与 Curios 兼容层；本移植只保留原版可穿戴 + 右键充放气，理由：
 * Create 6.0.10 的 {@code BacktankItem} 只认自己的 {@code BacktankBlock} 体系，
 * 硬接会把本模组的气体模型绑死在 Create 的氧气机制上。
 *
 * <p>数据格式与方块实体完全一致（{@code GasMass} 数组 + {@code GasTemperature}），
 * 因此"放下来 / 拿起来"是无损的。
 */
public class GasBacktankItem extends BlockItem implements Equipable {

    public GasBacktankItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.CHEST;
    }

    /** 装备栏里的那一件（没有则 null），供护目镜/后续逻辑查询。 */
    @Nullable
    public static ItemStack getWornBy(final Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return null;
        }
        final ItemStack stack = living.getItemBySlot(EquipmentSlot.CHEST);
        return stack.getItem() instanceof GasBacktankItem ? stack : null;
    }

    /** 该物品堆里的气体（没有数据时返回空混合物）。 */
    public static GasMix readGas(final ItemStack stack) {
        final GasMix mix = new GasMix();
        readInto(stack, mix);
        return mix;
    }

    /** 把物品里的气体写回物品堆（1.21 起物品数据走 {@code DataComponents.CUSTOM_DATA}）。 */
    public static void writeGas(final ItemStack stack, final GasMix mix) {
        final CompoundTag tag = new CompoundTag();
        mix.save(tag);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
    }

    private static void readInto(final ItemStack stack, final GasMix mix) {
        final CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            mix.load(data.copyTag());
        }
    }

    @Override
    public void inventoryTick(final ItemStack stack, final Level level, final Entity entity,
                              final int slotId, final boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        // 旧版在这里把 Air（Create 的氧气刻数）与 kelvin:air 的质量对齐；
        // 本移植没有 Create 的供气机制，只保留"数据格式自洽"这一语义，故无额外动作。
    }

    @Override
    public void appendHoverText(final ItemStack stack, final Item.TooltipContext context,
                                final List<Component> tooltip, final TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        final GasMix mix = readGas(stack);
        tooltip.add(Component.literal(String.format("Gas: %.3f kg / %.2f kg",
                        mix.totalMass(), GasBacktankBlock.CAPACITY_KG))
                .withStyle(ChatFormatting.GRAY));
        if (!mix.isEmpty()) {
            tooltip.add(Component.literal(String.format("Temperature: %.1f K", mix.temperature()))
                    .withStyle(ChatFormatting.GOLD));
        }
        tooltip.add(Component.translatable("vs_clockwork.gas_backtank.function1")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    /** 供 {@code GasBacktankBlock#getCloneItemStack} 复用：带上气体数据的物品堆。 */
    public static ItemStack withGas(final Block block, final GasMix mix) {
        final ItemStack stack = new ItemStack(block);
        writeGas(stack, mix);
        return stack;
    }
}
