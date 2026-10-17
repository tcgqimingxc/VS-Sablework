package org.valkyrienskies.clockwork.content.logistics.gas.compat.jei;

import com.simibubi.create.foundation.item.ItemHelper;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.createmod.catnip.data.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.apache.commons.lang3.mutable.MutableInt;
import org.valkyrienskies.clockwork.ClockworkMod;
import org.valkyrienskies.clockwork.content.logistics.gas.GasType;
import org.valkyrienskies.clockwork.content.logistics.gas.GasTypes;
import org.valkyrienskies.clockwork.content.logistics.gas.crafter.GasCraftingRecipe;

import java.util.List;
import java.util.Map;

/**
 * JEI 类别：气体合成（{@code gas_crafter}）。
 *
 * <p>对应旧版 {@code compat/jei/categories/GasCrafterCategory.kt} + {@code ClockworkJEI.CategoryBuilder}
 * 里那一套 {@code CreateRecipeCategory.Info} 的搭建。移植差异：
 * <ul>
 *   <li>旧版继承 Create 的 {@code CreateRecipeCategory}（自带滚动条、随机产出 tooltip、催化剂注册等）；
 *       Create 6.0.10 仍提供该类，但它的 {@code Info} 需要一个 Create 的 {@code RecipeType}，
 *       而本模组的气体合成配方是原版 {@code Recipe} 体系（见 {@link GasCraftingRecipe}），
 *       所以这里直接实现 JEI 的 {@link IRecipeCategory}，只保留旧版真正可见的部分。</li>
 *   <li>旧版画了 {@code AnimatedGasCrafter} / {@code AnimatedBlazeBurner} 两个动画方块与热度条；
 *       本移植用文字标签表达同样的信息（需要加热 / 需要超级加热），避免搬运 3D 动画模型。</li>
 *   <li>旧版的气体槽用 Kelvin 的贴图，这里用 {@link GasIngredientJei} 的色块。</li>
 * </ul>
 *
 * <p>布局（宽 160、高 44 + 需求行）：左列 = 物品原料 + 气体原料，右列 = 产物，中间箭头。
 */
public final class GasCrafterJeiCategory implements IRecipeCategory<RecipeHolder<GasCraftingRecipe>> {

    public static final mezz.jei.api.recipe.RecipeType<RecipeHolder<GasCraftingRecipe>> TYPE =
            mezz.jei.api.recipe.RecipeType.createRecipeHolderType(
                    ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, "gas_crafting"));

    private static final int WIDTH = 160;
    private static final int SLOT = 19;
    private static final int LEFT_X = 5;
    private static final int RIGHT_X = 5 * SLOT;
    private static final int INPUT_ROW_Y = 5;

    private final IDrawable icon;
    private final IDrawable slotBackground;

    public GasCrafterJeiCategory(final IGuiHelper guiHelper, final IDrawable icon) {
        this.icon = icon;
        this.slotBackground = guiHelper.getSlotDrawable();
    }

    @Override
    public mezz.jei.api.recipe.RecipeType<RecipeHolder<GasCraftingRecipe>> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("vs_clockwork.jei.category.gas_crafting");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        // 3 行槽（5..62）+ 温度 / 加热档位 / 热能收支三行文字
        return 95;
    }

    @Override
    public void setRecipe(final IRecipeLayoutBuilder builder, final RecipeHolder<GasCraftingRecipe> holder,
                          final IFocusGroup focuses) {
        final GasCraftingRecipe recipe = holder.value();

        // ---- 左侧：物品原料（同类合并计数，照抄旧版的 ItemHelper.condenseIngredients 用法）----
        int slot = 0;
        final List<Pair<Ingredient, MutableInt>> condensed = ItemHelper.condenseIngredients(recipe.getIngredients());
        for (final Pair<Ingredient, MutableInt> pair : condensed) {
            final List<ItemStack> stacks = new java.util.ArrayList<>();
            for (final ItemStack stack : pair.getFirst().getItems()) {
                final ItemStack copy = stack.copy();
                copy.setCount(pair.getSecond().getValue());
                stacks.add(copy);
            }
            builder.addSlot(RecipeIngredientRole.INPUT, x(slot), y(slot))
                    .setBackground(slotBackground, -1, -1)
                    .addItemStacks(stacks);
            slot++;
        }

        // ---- 左侧：气体原料 ----
        for (final Map.Entry<ResourceLocation, Double> entry : recipe.inputGases().entrySet()) {
            final GasType gas = GasTypes.byId(entry.getKey());
            if (gas == null) {
                continue;                      // 旧版配方里引用了本移植没有的气体 → 跳过（合成器同样跳过）
            }
            GasIngredientJei.addSlot(builder, RecipeIngredientRole.INPUT, x(slot), y(slot),
                    GasIngredientJei.of(gas, entry.getValue()), slotBackground);
            slot++;
        }

        // ---- 右侧：产物物品；气体产物接在后面（旧版也是"物品产物在前、气体产物在后"）----
        int outputSlot = 0;
        for (final ItemStack result : recipe.results()) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, x(outputSlot) + RIGHT_X, y(outputSlot))
                    .setBackground(slotBackground, -1, -1)
                    .addItemStack(result);
            outputSlot++;
        }
        for (final Map.Entry<ResourceLocation, Double> entry : recipe.resultGases().entrySet()) {
            final GasType gas = GasTypes.byId(entry.getKey());
            if (gas == null) {
                continue;
            }
            GasIngredientJei.addSlot(builder, RecipeIngredientRole.OUTPUT, x(outputSlot) + RIGHT_X, y(outputSlot),
                    GasIngredientJei.of(gas, entry.getValue()), slotBackground);
            outputSlot++;
        }
    }

    /** 第 {@code slot} 个槽的 x 坐标：每列 3 个，排满往右接一列。 */
    private static int x(final int slot) {
        return LEFT_X + (slot / 3) * SLOT;
    }

    /** 第 {@code slot} 个槽的 y 坐标。 */
    private static int y(final int slot) {
        return INPUT_ROW_Y + (slot % 3) * SLOT;
    }

    @Override
    public void draw(final RecipeHolder<GasCraftingRecipe> holder, final IRecipeSlotsView slots,
                     final GuiGraphics graphics, final double mouseX, final double mouseY) {
        final GasCraftingRecipe recipe = holder.value();
        final Minecraft minecraft = Minecraft.getInstance();

        // 箭头（用 fill 拼一个三角形 + 一条横线，避免依赖贴图资源；与左列槽位竖直居中对齐）
        final int arrowY = 43;
        graphics.fill(RIGHT_X - 26, arrowY, RIGHT_X - 8, arrowY + 2, 0xFF808080);
        graphics.fill(RIGHT_X - 12, arrowY - 3, RIGHT_X - 10, arrowY + 5, 0xFF808080);
        graphics.fill(RIGHT_X - 10, arrowY - 2, RIGHT_X - 8, arrowY + 4, 0xFF808080);

        // 需求：温度与加热档位（旧版画的是热度条 + 烈焰人燃烧室动画）
        int textY = 5 + 3 * SLOT;
        graphics.drawString(minecraft.font, Component.translatable("vs_clockwork.jei.gas_crafting.temperature",
                        String.format("%.1f K", recipe.minTemperature())).withStyle(ChatFormatting.GOLD),
                5, textY, 0xFFFFFFFF, false);
        textY += 10;
        final String heatKey = recipe.superHeated() ? "vs_clockwork.jei.gas_crafting.superheated"
                : recipe.heated() ? "vs_clockwork.jei.gas_crafting.heated"
                : "vs_clockwork.jei.gas_crafting.no_heat";
        graphics.drawString(minecraft.font, Component.translatable(heatKey).withStyle(ChatFormatting.GRAY),
                5, textY, 0xFFFFFFFF, false);
        if (recipe.energy() != 0) {
            graphics.drawString(minecraft.font, Component.translatable(
                            recipe.energy() > 0 ? "vs_clockwork.jei.gas_crafting.absorbs"
                                    : "vs_clockwork.jei.gas_crafting.releases",
                            String.format("%.0f J", Math.abs(recipe.energy()))).withStyle(ChatFormatting.RED),
                    5, textY + 10, 0xFFFFFFFF, false);
        }
    }
}
