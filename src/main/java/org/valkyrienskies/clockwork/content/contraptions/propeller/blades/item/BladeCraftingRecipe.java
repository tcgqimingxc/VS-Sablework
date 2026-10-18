package org.valkyrienskies.clockwork.content.contraptions.propeller.blades.item;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.valkyrienskies.clockwork.content.contraptions.CWContraptionsIndex;

/**
 * 工作台"接长叶片"配方：把两片以上同款叶片按展长<b>相加</b>成一片更长的叶片。
 *
 * <p>对应旧版 {@code blades/item/CraftingTableBladeRecipe.kt}（{@code CustomRecipe}，
 * 资源 {@code data/vs_clockwork/recipe/blade_crafting_recipe.json}）。
 *
 * <p><b>与旧版逐条对照</b>
 * <ul>
 *   <li>旧版要求 {@code numBlades >= 2}、所有物品都是 {@code BladeItem}、总长 {@code <= maxBladeSize}
 *       ——这里一致。</li>
 *   <li>旧版结果 = {@code new ItemStack(items.first().item)} 且长度 = 各片之和
 *       ——这里一致（取第一片的物品类型，因此宽叶 + 宽叶 = 宽叶）。</li>
 *   <li><b>差异 1：宽度必须一致</b>。旧版只禁止"宽叶后面再放窄叶"，反过来的"窄叶 + 宽叶"能匹配，
 *       但结果取第一片的类型，宽叶会被静默降级成窄叶（吞掉一个昂贵材料）。这里判定为不匹配，
 *       宁可合成不了也不吞物品。</li>
 *   <li><b>差异 2：整数长度</b>。旧版是 double；本移植按需求用整数格（见 {@link BladeItems}），
 *       长度超上限时 {@code matches()} 直接不匹配（与旧版相同），{@code assemble()} 再夹一次兜底。</li>
 *   <li><b>差异 3：{@code getIngredients()} 提供 2 个"任意叶片"原料</b>。旧版没覆写（返回空表），
 *       JEI / 配方书里这条配方是"没有原料、只有产物"的空壳。这里的原料表<b>只用于展示</b>，
 *       真正的匹配规则仍在 {@link #matches}（例如混放宽窄叶不会匹配）。</li>
 * </ul>
 *
 * <p>为什么是 {@code CustomRecipe} 而不是 {@code ShapelessRecipe}：形状不限、格数不限（2 格以上工作台），
 * 且结果依赖输入状态（各片长度），原版只有 {@code CustomRecipe} 能在 {@code assemble} 里读到输入。
 */
public class BladeCraftingRecipe extends CustomRecipe {

    public BladeCraftingRecipe(final CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(final CraftingInput input, final Level level) {
        int blades = 0;
        int totalLength = 0;
        boolean wide = false;
        for (final ItemStack stack : input.items()) {
            if (stack.isEmpty()) {
                continue;
            }
            if (!(stack.getItem() instanceof final BladeItem blade)) {
                return false;
            }
            if (blades == 0) {
                wide = blade.isWide();
            } else if (blade.isWide() != wide) {
                return false; // 宽窄混放：旧版会吞掉宽叶（见类注释"差异 1"）
            }
            totalLength += BladeItems.getLength(stack);
            blades++;
        }
        // 上限判定放在 matches：超长时工作台里不显示可合成，避免"合出来又被夹回去"的隐性损耗。
        return blades >= 2 && totalLength <= BladeItems.maxLength();
    }

    @Override
    public ItemStack assemble(final CraftingInput input, final HolderLookup.Provider registries) {
        ItemStack first = ItemStack.EMPTY;
        int totalLength = 0;
        int blades = 0;
        for (final ItemStack stack : input.items()) {
            if (stack.isEmpty()) {
                continue;
            }
            if (!(stack.getItem() instanceof BladeItem)) {
                return ItemStack.EMPTY;
            }
            if (first.isEmpty()) {
                first = stack;
            }
            totalLength += BladeItems.getLength(stack);
            blades++;
        }
        if (blades < 2 || first.isEmpty()) {
            return ItemStack.EMPTY;
        }
        final ItemStack result = new ItemStack(first.getItem());
        // setLength 内部会夹到 [1, maxBladeSize]；matches 已经保证不超上限，这里是兜底。
        BladeItems.setLength(result, totalLength);
        return result;
    }

    @Override
    public boolean canCraftInDimensions(final int width, final int height) {
        return width * height >= 2;
    }

    /** 仅用于 JEI / 配方书的原料展示（见类注释"差异 3"）。 */
    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.withSize(2, Ingredient.of(
                CWContraptionsIndex.PROPELLER_BLADE.get(),
                CWContraptionsIndex.WIDE_PROPELLER_BLADE.get()));
    }

    /** 仅用于 JEI 展示：长度由输入决定，这里给一片默认长度（1 格）的窄叶当图标。 */
    @Override
    public ItemStack getResultItem(final HolderLookup.Provider registries) {
        return new ItemStack(CWContraptionsIndex.PROPELLER_BLADE.get());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return BladeRecipes.CRAFTING_SERIALIZER;
    }
}
