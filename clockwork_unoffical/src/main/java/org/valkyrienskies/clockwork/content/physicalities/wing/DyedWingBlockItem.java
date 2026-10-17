package org.valkyrienskies.clockwork.content.physicalities.wing;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 机翼 / 襟翼的物品形态：把染料颜色随物品携带，放下时写回方块实体。
 *
 * <p>对应旧版 {@code content.physicalities.wing.DyedWingBlockItem}。
 *
 * <p>与旧版的差异：1.21 起 {@code ItemStack} 上的裸 NBT 被 {@code DataComponents.CUSTOM_DATA} 取代，
 * 所以颜色的读写都走 {@link CustomData}（键名仍是旧版的 {@code Clockwork$color}，
 * 这样旧的资源包 / 配方里引用这个键的地方不用改）。提示文本也改成了 1.21 的
 * {@code appendHoverText(ItemStack, TooltipContext, List, TooltipFlag)} 签名。
 */
public class DyedWingBlockItem extends BlockItem {

    public DyedWingBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    /** 读取物品上携带的颜色，未染色返回 {@link WingColorBlockEntity#NO_COLOR}。 */
    public static int getColor(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return WingColorBlockEntity.NO_COLOR;
        }
        return data.copyTag().getInt(WingColorBlockEntity.COLOR_KEY);
    }

    /** 写入颜色；{@code NO_COLOR} 表示清掉颜色数据。 */
    public static void setColor(ItemStack stack, int rgb) {
        if (rgb == WingColorBlockEntity.NO_COLOR) {
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            if (data == null) {
                return;
            }
            CustomData.update(DataComponents.CUSTOM_DATA, stack,
                    tag -> tag.remove(WingColorBlockEntity.COLOR_KEY));
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
                tag -> tag.putInt(WingColorBlockEntity.COLOR_KEY, rgb));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int color = getColor(stack);
        if (color != WingColorBlockEntity.NO_COLOR) {
            MutableComponent hex = Component.literal("#" + String.format("%06X", color))
                    .setStyle(Style.EMPTY.withColor(color));
            tooltip.add(hex.withStyle(ChatFormatting.GRAY));
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        if (!super.placeBlock(context, state)) {
            return false;
        }
        Level level = context.getLevel();
        if (level.isClientSide) {
            return true;
        }
        BlockEntity be = level.getBlockEntity(context.getClickedPos());
        if (be instanceof WingColorBlockEntity colorBe) {
            colorBe.setColor(getColor(context.getItemInHand()));
        }
        return true;
    }
}
