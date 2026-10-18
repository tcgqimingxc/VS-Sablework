package org.valkyrienskies.clockwork.content.contraptions.propeller.blades.item;

import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * 动力锯"截短叶片"配方：输入一片叶片，输出同一片、展长<b>减半（向下取整，最低 1 格）</b>。
 *
 * <p>对应旧版 {@code blades/item/CuttingBladeRecipe.kt}
 * （{@code CuttingRecipe} 子类 + 伴生对象里的 {@code cutBlade}）。
 *
 * <p><b>为什么继承 Create 的 {@link CuttingRecipe}</b>：动力锯的
 * {@code SawBlockEntity#applyRecipe}（javap 核实，1.21.1 Create 6.0.10）只对
 * {@code instanceof CuttingRecipe} 的配方调用 {@code rollResults(RandomSource)}，
 * 其它类型走 {@code StonecutterRecipe} 分支。继承它能让"锯子把结果槽填满"这条既有链路
 * 原样复用，我们只需要在 mixin 里把 {@code rollResults} 的返回值换成"截短后的输入"。
 *
 * <p><b>为什么必须覆写三个访问器</b>：{@link CuttingRecipe} 的构造器把
 * {@code AllRecipeTypes.CUTTING} 写死传给了 {@code ProcessingRecipe}（javap 核实），
 * 于是 {@code getType()/getSerializer()/getTypeInfo()} 默认都指向 {@code create:cutting}。
 * 不覆写的话：① 配方会以 {@code create:cutting} 的身份出现在配方管理器里，
 * 动力锯按我们自己的类型根本查不到；② 客户端同步时会用错误的序列化器编码而崩。
 * 因此三者全部指回 {@link BladeRecipes} 里的注册项。
 */
public class BladeCuttingRecipe extends CuttingRecipe {

    public BladeCuttingRecipe(final ProcessingRecipeParams params) {
        super(params);
    }

    @Override
    public RecipeType<?> getType() {
        return BladeRecipes.CUTTING_TYPE;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return BladeRecipes.CUTTING_SERIALIZER;
    }

    @Override
    public IRecipeTypeInfo getTypeInfo() {
        return BladeRecipes.CUTTING_INFO;
    }

    /**
     * 截短一片叶片（旧版 {@code CuttingBladeRecipe.Companion.cutBlade}）。
     *
     * <p>旧版公式 {@code max(length / 2, 0.25)}；本移植是整数格，对应
     * {@code max(length / 2, 1)}：1→1、2→1、3→1、4→2、8→4。
     * 结果用 {@code copyWithCount(1)} 保留输入上的其它组件（耐久、自定义名等），只改长度。
     */
    public static ItemStack cut(final ItemStack input) {
        final ItemStack result = input.copyWithCount(1);
        BladeItems.setLength(result, Math.max(BladeItems.MIN_LENGTH, BladeItems.getLength(result) / 2));
        return result;
    }
}
