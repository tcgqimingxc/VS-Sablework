package org.valkyrienskies.clockwork.content.logistics.gas.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.registration.IModIngredientRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.valkyrienskies.clockwork.ClockworkMod;
import org.valkyrienskies.clockwork.content.logistics.gas.CWGasIndex;
import org.valkyrienskies.clockwork.content.logistics.gas.GasPhysics;
import org.valkyrienskies.clockwork.content.logistics.gas.GasType;
import org.valkyrienskies.clockwork.content.logistics.gas.GasTypes;
import org.valkyrienskies.clockwork.content.logistics.gas.crafter.GasCraftingRecipe;

import java.util.ArrayList;
import java.util.List;

/**
 * Clockwork 的 JEI 插件入口。
 *
 * <p>对应旧版 {@code compat/jei/ClockworkJEI.kt}（311 行的 CategoryBuilder 全家桶）。移植差异：
 * <ul>
 *   <li>旧版有 3 个类别：气体合成（GasCrafter）、气体反应（Kelvin 提供，属 Kelvin 模组）、
 *       管道统计（DuctStats）。<b>气体反应</b>依赖 Kelvin 的 {@code GasBaseRecipe}，1.21.1 没有这个库，
 *       本模组也没有对应的配方类型，因此不移植；<b>管道统计</b>依赖 {@code IHaveDuctStats}
 *       那套"生产数据表"，本移植没有实现该接口（气体网络是自研的），也一并省略。
 *       取而代之的是新的「气体资料」页（见 {@link GasInfoJeiCategory}），把压力 / 温度等读数直接展示出来。</li>
 *   <li>旧版用 Create 的 {@code CreateRecipeCategory.Info} + {@code consumeTypedRecipes}；
 *       这里直接用客户端 {@code RecipeManager} 取原版 {@code RecipeHolder}。</li>
 * </ul>
 *
 * <p><b>没装 JEI 时不会崩</b>，依据有两条：
 * <ol>
 *   <li>本类与同包另外三个类是全模组<b>唯一</b>引用 {@code mezz.jei.api.**} 的地方（编译期依赖是
 *       {@code compileOnly}，运行期发布包里根本没有 mezz/jei 的类）。</li>
 *   <li>JEI 通过扫描 {@code @JeiPlugin} 注解来加载插件，而本类<b>只被 JEI 加载</b>：
 *       {@code ClockworkMod} 等公共代码从不 import / new 它。JVM 的类加载是惰性的，
 *       因此 JEI 不在时没有任何代码路径会去解析这个类，不会抛 {@code NoClassDefFoundError}。</li>
 * </ol>
 * 反过来说：<b>不要</b>从通用代码里引用本类或 {@link GasIngredientJei}，否则上述保证失效
 * （同理，本包之外也不要引用 {@code GasCrafterJeiCategory} / {@code GasInfoJeiCategory}）。
 */
@JeiPlugin
public class ClockworkJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    /**
     * 把"气体"这个自定义原料类型登记给 JEI。
     *
     * <p>必须登记：JEI 只认通过 {@link IModIngredientRegistration} 注册过的原料类型，否则
     * 往配方布局里 {@code addIngredient(GasIngredientJei.TYPE, ...)} 时 JEI 的
     * {@code RegisteredIngredients.getIngredientInfo} 会抛
     * {@code IllegalArgumentException: Unknown ingredient type: class ...GasIngredientJei}
     * ——实机日志里 GasInfoJeiCategory 的 9 种气体各刷一条 ERROR（"Found a broken recipe"）。
     * 登记后气体也会出现在 JEI 的原料搜索里，点进去能看到用到它的配方。
     */
    @Override
    public void registerIngredients(final IModIngredientRegistration registration) {
        final List<GasIngredientJei> gases = new ArrayList<>(GasTypes.count());
        for (final GasType gas : GasTypes.all()) {
            // 搜索列表里的代表条目：质量取 1 kg（真实质量由配方槽决定），温度取环境温度
            gases.add(GasIngredientJei.of(gas, 1.0));
        }
        registration.register(GasIngredientJei.TYPE, gases,
                new GasIngredientJei.Helper(), new GasIngredientJei.Renderer(), GasIngredientJei.CODEC);
    }

    @Override
    public void registerCategories(final IRecipeCategoryRegistration registration) {
        final IDrawable crafterIcon = registration.getJeiHelpers().getGuiHelper()
                .createDrawableItemStack(new ItemStack(CWGasIndex.GAS_CRAFTER.get()));
        final IDrawable ductIcon = registration.getJeiHelpers().getGuiHelper()
                .createDrawableItemStack(new ItemStack(CWGasIndex.DUCT.get()));
        registration.addRecipeCategories(
                new GasCrafterJeiCategory(registration.getJeiHelpers().getGuiHelper(), crafterIcon),
                new GasInfoJeiCategory(registration.getJeiHelpers().getGuiHelper(), ductIcon));
    }

    @Override
    public void registerRecipes(final IRecipeRegistration registration) {
        registration.addRecipes(GasCrafterJeiCategory.TYPE, gasCraftingRecipes());

        final List<GasInfoJeiCategory.GasInfo> gasInfos = new ArrayList<>();
        for (final GasType gas : GasTypes.all()) {
            gasInfos.add(new GasInfoJeiCategory.GasInfo(gas));
        }
        registration.addRecipes(GasInfoJeiCategory.TYPE, gasInfos);

        // 把气体网络的读数挂到导管的物品信息上（读数的含义见 GasNodeTooltip）；
        // 注意：1.21 的 ItemStack 不再实现 ItemLike，这个重载要传 Item/BlockEntry
        registration.addIngredientInfo(CWGasIndex.DUCT,
                Component.translatable("vs_clockwork.jei.duct.info.1"),
                Component.translatable("vs_clockwork.jei.duct.info.2"),
                Component.translatable("vs_clockwork.jei.duct.info.3",
                        GasIngredientJei.formatPressure(GasPhysics.ambientPressure())));
    }

    @Override
    public void registerRecipeCatalysts(final IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(CWGasIndex.GAS_CRAFTER.get()), GasCrafterJeiCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(CWGasIndex.DUCT.get()), GasInfoJeiCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(CWGasIndex.AIR_COMPRESSOR.get()), GasInfoJeiCategory.TYPE);
    }

    /** 客户端配方表里的气体合成配方（只保留本移植能真正执行的，与合成器的判断一致）。 */
    private static List<RecipeHolder<GasCraftingRecipe>> gasCraftingRecipes() {
        final List<RecipeHolder<GasCraftingRecipe>> recipes = new ArrayList<>();
        if (Minecraft.getInstance().getConnection() == null) {
            return recipes;
        }
        for (final RecipeHolder<?> holder : Minecraft.getInstance().getConnection().getRecipeManager().getRecipes()) {
            if (holder.value() instanceof final GasCraftingRecipe recipe && recipe.supportsContent()) {
                recipes.add(new RecipeHolder<>(holder.id(), recipe));
            }
        }
        return recipes;
    }
}
