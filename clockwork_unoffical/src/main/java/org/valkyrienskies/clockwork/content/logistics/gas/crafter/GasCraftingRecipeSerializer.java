package org.valkyrienskies.clockwork.content.logistics.gas.crafter;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 气体合成配方的序列化器（JSON ↔ {@link GasCraftingRecipe} ↔ 网络）。
 *
 * <p>对应旧版 {@code GasCraftingRecipeSerializer} + Kelvin 的 {@code KelvinGasRecipeSerializer}。
 *
 * <p><b>这里没有 JSON 翻译代码，是有原因的</b>：1.21.1 的 {@link RecipeSerializer} 只有
 * {@code codec()} 与 {@code streamCodec()} 两个方法（1.20 的 {@code fromJson} 钩子已被移除），
 * 而 {@code RecipeManager} 读数据包时走的就是 {@code codec()}。所以旧版 JSON 的字段名
 * （{@code input_gasses} / {@code result_gasses} / {@code requirements.kelvin:min_temperature}）
 * 由 {@link GasCraftingRecipe#CODEC} 自己描述 —— 之前那套"先翻译成新字段名再解析"的写法
 * 从未被调用过（配方照旧按新字段名解析 → 气体与温度全丢、含流体原料的配方直接解析报错）。
 */
public final class GasCraftingRecipeSerializer implements RecipeSerializer<GasCraftingRecipe> {

    /** 旧版资源里的 {@code type} 值。 */
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, "gas_crafting");

    /** 注册表条目（在 {@code CWGasIndex} 里注册）。 */
    public static final RecipeType<GasCraftingRecipe> TYPE = new RecipeType<>() {
        @Override
        public String toString() {
            return ID.toString();
        }
    };

    public static final GasCraftingRecipeSerializer INSTANCE = new GasCraftingRecipeSerializer();

    private GasCraftingRecipeSerializer() {
    }

    @Override
    public MapCodec<GasCraftingRecipe> codec() {
        return GasCraftingRecipe.CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, GasCraftingRecipe> streamCodec() {
        return GasCraftingRecipe.STREAM_CODEC;
    }
}
