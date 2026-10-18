package org.valkyrienskies.clockwork.content.contraptions.propeller.blades.item;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 两个叶片自定义配方类型的注册中心（旧版 {@code ClockworkRecipes.kt} 的对应物）。
 *
 * <p>旧版在 {@code ClockworkRecipes} 里注册了
 * {@code blade_crafting}（{@code SimpleCraftingRecipeSerializer}）与
 * {@code blade_cutting}（Create 的 {@code ProcessingRecipeSerializer}）。
 * 1.21.1 的 Create 6 已<b>删除</b> {@code ProcessingRecipeSerializer}（javap 核实：jar 里没有这个类），
 * 配方序列化改成数据驱动的 {@code MapCodec} / {@code StreamCodec}，因此这里手写一个
 * {@link RecipeSerializer}：codec 直接复用 Create 的
 * {@link ProcessingRecipe#codec} 与 {@code streamCodec}（对 {@link ProcessingRecipeParams} 的解析逻辑
 * 与原版 Create 的 {@code create:cutting} 完全一致，所以 JSON 字段名也一样）。
 *
 * <p><b>{@code blade_crafting} 只注册序列化器，不注册 RecipeType</b>：它本质是原版工作台配方，
 * {@code getType()} 走 {@code CraftingRecipe} 的默认值 {@code minecraft:crafting}，
 * 这样它才会出现在原版配方书 / JEI 的合成分类里（旧版同样只注册了 serializer）。
 * {@code blade_cutting} 则需要自己的 {@code RecipeType}：动力锯的 mixin 要按这个类型
 * 从 {@code RecipeManager} 里把配方捞出来（旧版 {@code SawBladeRecipeMixin} 同样如此）。
 */
public final class BladeRecipes {

    /** 旧版资源里的 id：{@code vs_clockwork:blade_cutting}。 */
    public static final ResourceLocation CUTTING_ID =
            ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, "blade_cutting");

    /** 工作台加长配方（原版 {@code CustomRecipe} 的通用序列化器，JSON 只需要 {@code category}）。 */
    public static final SimpleCraftingRecipeSerializer<BladeCraftingRecipe> CRAFTING_SERIALIZER =
            new SimpleCraftingRecipeSerializer<>(BladeCraftingRecipe::new);

    /** 锯切割配方类型（锯的 mixin 用它查配方）。 */
    public static final RecipeType<BladeCuttingRecipe> CUTTING_TYPE = new RecipeType<>() {
        @Override
        public String toString() {
            return CUTTING_ID.toString();
        }
    };

    /**
     * 锯切割配方序列化器：字段与 {@code create:cutting} 完全一致
     * （{@code ingredients} / {@code results} / {@code processing_time}），
     * 因为解析与网络编码都直接委托给 Create 的 {@code ProcessingRecipe} 实现。
     */
    public static final RecipeSerializer<BladeCuttingRecipe> CUTTING_SERIALIZER =
            new RecipeSerializer<>() {
                @Override
                public MapCodec<BladeCuttingRecipe> codec() {
                    return ProcessingRecipe.codec(BladeCuttingRecipe::new, ProcessingRecipeParams.CODEC);
                }

                @Override
                public StreamCodec<RegistryFriendlyByteBuf, BladeCuttingRecipe> streamCodec() {
                    return ProcessingRecipe.streamCodec(BladeCuttingRecipe::new,
                            ProcessingRecipeParams.STREAM_CODEC);
                }
            };

    /**
     * {@code blade_cutting} 的类型信息（Create 的 {@link IRecipeTypeInfo}）。
     *
     * <p>为什么需要它：{@link CuttingRecipe} 的构造器把 {@code AllRecipeTypes.CUTTING} 写死传给了
     * {@code ProcessingRecipe}，所以 {@link BladeCuttingRecipe} 必须覆写
     * {@code getType/getSerializer/getTypeInfo}（见该类注释）。这里提供的就是那三个访问器背后的对象。
     */
    public static final IRecipeTypeInfo CUTTING_INFO = new IRecipeTypeInfo() {
        @Override
        public ResourceLocation getId() {
            return CUTTING_ID;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends RecipeSerializer<?>> T getSerializer() {
            return (T) CUTTING_SERIALIZER;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <I extends RecipeInput, R extends Recipe<I>> RecipeType<R> getType() {
            return (RecipeType<R>) CUTTING_TYPE;
        }
    };

    private BladeRecipes() {
    }

    /**
     * 注册配方类型与序列化器（由 {@code CWContraptionsIndex#register()} 调用）。
     *
     * <p>用 NeoForge 的 {@link DeferredRegister} 而不是 Registrate：配方类型/序列化器不是
     * Registrate 的 builder 类型（与 {@code CWGasIndex} 里 {@code gas_crafting} 的写法一致）。
     */
    public static void register() {
        final IEventBus modBus = ClockworkMod.REGISTRATE.getModEventBus();

        final DeferredRegister<RecipeType<?>> types =
                DeferredRegister.create(Registries.RECIPE_TYPE, ClockworkMod.MOD_ID);
        types.register("blade_cutting", () -> CUTTING_TYPE);
        types.register(modBus);

        final DeferredRegister<RecipeSerializer<?>> serializers =
                DeferredRegister.create(Registries.RECIPE_SERIALIZER, ClockworkMod.MOD_ID);
        serializers.register("blade_crafting", () -> CRAFTING_SERIALIZER);
        serializers.register("blade_cutting", () -> CUTTING_SERIALIZER);
        serializers.register(modBus);
    }
}
