package org.valkyrienskies.clockwork.content.logistics.gas.crafter;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.valkyrienskies.clockwork.content.logistics.gas.GasMix;
import org.valkyrienskies.clockwork.content.logistics.gas.GasType;
import org.valkyrienskies.clockwork.content.logistics.gas.GasTypes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 气体合成配方：在气体合成器（贴在工作盆上方）里，把「物品 + 气体 + 温度」变成「物品 + 气体」。
 *
 * <p>对应旧版 {@code GasCraftingRecipe}（继承 Create 的 {@code ProcessingRecipe}，
 * 额外字段由 Kelvin 的 {@code KelvinGasRecipeSerializer} 解析）。旧版那条路依赖
 * <b>Kelvin 的 GasBaseRecipe / KelvinGasIngredient</b>（1.21.1 不存在），因此这里按
 * <b>原版 Recipe 接口</b>重新实现，但<b>完全兼容旧版已有的配方 JSON</b>
 * （`data/vs_clockwork/recipe/gas_crafting/*.json`，字段名逐字保留）——
 * 1.21.1 的 {@link RecipeSerializer} 只有 {@code codec()} / {@code streamCodec()}，
 * <b>没有</b> 1.20 的 {@code fromJson} 钩子，所以 {@link #CODEC} 直接按旧版字段名描述配方：
 *
 * <pre>
 * {
 *   "type": "vs_clockwork:gas_crafting",
 *   "ingredients":   [ { "tag": "minecraft:coals" } ],           // 原版 Ingredient 格式
 *   "input_gasses":  { "vs_clockwork:steam": { "amount": 50, "unit": "g" } },
 *   "result_gasses": { "vs_clockwork:bog":   { "amount": 0.05, "unit": "kg" } },
 *   "results":       [ { "id": "minecraft:leather", "count": 1 } ],
 *   "requirements":  { "kelvin:min_temperature": 700.0 },
 *   "heatRequirement": "heated",
 *   "energy": 1000,
 *   "processing_time": 100
 * }
 * </pre>
 *
 * <p>与旧版的差异：
 * <ul>
 *   <li><b>只支持物品与气体</b>。旧版是 Create 的 {@code ProcessingRecipe}，还能吃流体
 *       （{@code "fluid": "create:potion"} 那三个 vitriol 配方）。本实现把这类条目在读取时
 *       丢弃、并让 {@link #supportsContent()} 返回 false，配方会被合成器与 JEI 直接跳过
 *       —— 不会崩、也不会误消耗（旧写法直接把流体条目塞给 {@code Ingredient.CODEC}，
 *       结果是整条配方解析失败、每次加载刷一条 ERROR）。</li>
 *   <li>{@code requirements} 只认 {@code kelvin:min_temperature}（其余 requirement 忽略）。</li>
 *   <li>{@code heatRequirement} 语义保留：{@code "heated"} = 需要下方有工作盆热源
 *       （烈焰人燃烧室 / 加热器），{@code "superheated"} = 需要超级加热。</li>
 *   <li>{@code energy} 旧版是给 Kelvin 的热能收支；本移植用它作为"每次合成额外消耗的热能 [J]"。
 *       正数 = 从网络吸热，负数 = 放热给网络（与旧版 JSON 里的正负号方向相反，
 *       旧版负值代表放热反应，这里同样把负数当放热）。</li>
 *   <li>{@code supportsContent} 不是 JSON 字段：读取时只要发现旧版流体原料就是 false
 *       （合成器与 JEI 都跳过这条配方）。</li>
 * </ul>
 */
public record GasCraftingRecipe(List<Ingredient> ingredients,
                                Map<ResourceLocation, Double> inputGases,
                                Map<ResourceLocation, Double> resultGases,
                                List<ItemStack> results,
                                double minTemperature,
                                boolean superHeated,
                                boolean heated,
                                double energy,
                                int processingTime,
                                boolean supportsContent) implements Recipe<RecipeInput> {

    /**
     * 一条气体条目：{@code {"amount": 50, "unit": "g"}}（单位缺省 kg）。
     * 气体 id 不在这里，而是它在 JSON 里的<b>键</b>（见 {@link #GAS_MAP_CODEC}）。
     */
    public record GasAmount(double amount, String unit) {

        public static final Codec<GasAmount> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.fieldOf("amount").forGetter(GasAmount::amount),
                Codec.STRING.optionalFieldOf("unit", "kg").forGetter(GasAmount::unit)
        ).apply(instance, GasAmount::new));

        /** 质量 [kg]：旧版单位只有 g 与 kg 两种。 */
        public double massKg() {
            return "g".equalsIgnoreCase(unit) ? amount / 1000.0 : amount;
        }
    }

    /** 旧版硬编码的默认处理时间 [tick]。 */
    public static final int DEFAULT_PROCESSING_TIME = 100;

    private static final Codec<List<ItemStack>> RESULTS_CODEC = ItemStack.CODEC.listOf();

    /** {@code {"vs_clockwork:steam": {"amount": 50, "unit": "g"}}}：气体 id 是键。 */
    private static final Codec<Map<ResourceLocation, GasAmount>> GAS_MAP_CODEC =
            Codec.unboundedMap(ResourceLocation.CODEC, GasAmount.CODEC);

    /**
     * 旧版的流体原料条目 {@code {"fluid": "create:potion", "amount": 100, "nbt": {...}}}。
     * 只需要能认出来（其余字段忽略），用来把它整条丢掉。
     */
    private static final Codec<String> FLUID_INGREDIENT_CODEC = Codec.STRING.fieldOf("fluid").codec();

    /**
     * 一条原料：原版 {@link Ingredient}，或旧版的流体原料。
     *
     * <p>流体原料在读取时被换成 {@link Ingredient#EMPTY} 当作"本移植不支持的原料"标记：
     * 直接交给 {@code Ingredient.CODEC} 会抛 {@code No key tag / No key item}，
     * 让整条配方解析失败并每次加载刷 ERROR（实机日志里 3 条 vitriol 配方就是这样）。
     */
    private static final Codec<Ingredient> INGREDIENT_CODEC = Codec
            .either(Ingredient.CODEC, FLUID_INGREDIENT_CODEC)
            .xmap(entry -> entry.left().orElse(Ingredient.EMPTY),
                    ingredient -> Either.<Ingredient, String>left(ingredient));

    private static final Codec<List<Ingredient>> INGREDIENTS_CODEC = INGREDIENT_CODEC.listOf();

    /** {@code requirements}：本移植只认 {@code kelvin:min_temperature}，其余 requirement（如 inhibited_by）忽略。 */
    private record Requirements(double minTemperature) {

        static final Codec<Requirements> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.optionalFieldOf("kelvin:min_temperature", 0.0).forGetter(Requirements::minTemperature)
        ).apply(instance, Requirements::new));
    }

    public static final MapCodec<GasCraftingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            INGREDIENTS_CODEC.optionalFieldOf("ingredients", List.of()).forGetter(GasCraftingRecipe::ingredients),
            GAS_MAP_CODEC.optionalFieldOf("input_gasses", Map.of())
                    .forGetter(recipe -> toGasAmounts(recipe.inputGases())),
            GAS_MAP_CODEC.optionalFieldOf("result_gasses", Map.of())
                    .forGetter(recipe -> toGasAmounts(recipe.resultGases())),
            RESULTS_CODEC.optionalFieldOf("results", List.of()).forGetter(GasCraftingRecipe::results),
            Requirements.CODEC.optionalFieldOf("requirements", new Requirements(0.0))
                    .forGetter((GasCraftingRecipe recipe) -> new Requirements(recipe.minTemperature())),
            Codec.STRING.optionalFieldOf("heatRequirement", "").forGetter(GasCraftingRecipe::heatRequirementName),
            Codec.DOUBLE.optionalFieldOf("energy", 0.0).forGetter(GasCraftingRecipe::energy),
            Codec.INT.optionalFieldOf("processing_time", DEFAULT_PROCESSING_TIME)
                    .forGetter(GasCraftingRecipe::processingTime)
    ).apply(instance, GasCraftingRecipe::fromCodecFields));

    public static final StreamCodec<RegistryFriendlyByteBuf, GasCraftingRecipe> STREAM_CODEC =
            StreamCodec.of(GasCraftingRecipe::writeNetwork, GasCraftingRecipe::readNetwork);

    private String heatRequirementName() {
        return superHeated ? "superheated" : heated ? "heated" : "";
    }

    /**
     * 组装 codec 读取到的字段。
     *
     * <p>{@code supportsContent} 在这里判定：只要出现过旧版流体原料（读取时被换成
     * {@link Ingredient#EMPTY}），整条配方就标记为不可执行 —— 合成器与 JEI 都会跳过它。
     */
    private static GasCraftingRecipe fromCodecFields(final List<Ingredient> ingredients,
                                                     final Map<ResourceLocation, GasAmount> inputGases,
                                                     final Map<ResourceLocation, GasAmount> resultGases,
                                                     final List<ItemStack> results,
                                                     final Requirements requirements,
                                                     final String heatRequirement,
                                                     final double energy,
                                                     final int processingTime) {
        final boolean unsupported = ingredients.contains(Ingredient.EMPTY);
        final List<Ingredient> itemIngredients = unsupported
                ? ingredients.stream().filter(ingredient -> ingredient != Ingredient.EMPTY).toList()
                : List.copyOf(ingredients);
        return new GasCraftingRecipe(
                itemIngredients,
                toMassMap(inputGases),
                toMassMap(resultGases),
                List.copyOf(results),
                requirements.minTemperature(),
                "superheated".equals(heatRequirement),
                "heated".equals(heatRequirement) || "superheated".equals(heatRequirement),
                energy,
                processingTime,
                !unsupported);
    }

    /** {@code {id: {amount, unit}}} → {@code {id: 质量 kg}}。 */
    private static Map<ResourceLocation, Double> toMassMap(final Map<ResourceLocation, GasAmount> gases) {
        final Map<ResourceLocation, Double> masses = new LinkedHashMap<>(gases.size());
        gases.forEach((id, amount) -> masses.put(id, amount.massKg()));
        return masses;
    }

    /** 编码方向：{@code {id: 质量 kg}} → {@code {id: {amount, unit: "kg"}}}（与 {@link #toMassMap} 对称）。 */
    private static Map<ResourceLocation, GasAmount> toGasAmounts(final Map<ResourceLocation, Double> masses) {
        final Map<ResourceLocation, GasAmount> gases = new LinkedHashMap<>(masses.size());
        masses.forEach((id, mass) -> gases.put(id, new GasAmount(mass, "kg")));
        return gases;
    }

    // ===================== 匹配与执行 =====================

    /**
     * 物品是否够、气体是否够、温度是否够。
     *
     * <p>{@code temperature} 传当前气体网络的温度；物品从 {@code input}（工作盆的输入栏）里找。
     */
    public boolean matches(final RecipeInput input, final GasMix mix, final double temperature) {
        if (!supportsContent || !hasTemperature(temperature)) {
            return false;
        }
        return hasItems(input) && hasGases(mix);
    }

    public boolean hasTemperature(final double temperature) {
        return temperature >= minTemperature;
    }

    /** 工作盆输入栏里是否有足够的物品（每种原料各算一次，允许重复使用同一格）。 */
    public boolean hasItems(final RecipeInput input) {
        if (ingredients.isEmpty()) {
            return true;
        }
        final List<ItemStack> available = new ArrayList<>();
        for (int slot = 0; slot < input.size(); slot++) {
            final ItemStack stack = input.getItem(slot);
            if (!stack.isEmpty()) {
                available.add(stack);
            }
        }
        return matchIngredients(available);
    }

    private boolean matchIngredients(final List<ItemStack> available) {
        final boolean[] used = new boolean[available.size()];
        for (final Ingredient ingredient : ingredients) {
            boolean matched = false;
            for (int i = 0; i < available.size(); i++) {
                if (!used[i] && ingredient.test(available.get(i))) {
                    used[i] = true;
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    /** 所需气体是否都在（质量按 kg 比较）。 */
    public boolean hasGases(final GasMix mix) {
        for (final Map.Entry<ResourceLocation, Double> entry : inputGases.entrySet()) {
            final GasType type = GasTypes.byId(entry.getKey());
            if (type == null || mix.massOf(type) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    /** 消耗物品与气体，产出物品与气体。调用前必须先用 {@link #matches} 确认。 */
    public void apply(final RecipeInput input, final GasMix mix) {
        consumeItems(input);
        inputGases.forEach((id, mass) -> {
            final GasType type = GasTypes.byId(id);
            if (type != null) {
                mix.removeMass(type, mass);
            }
        });
        resultGases.forEach((id, mass) -> {
            final GasType type = GasTypes.byId(id);
            if (type != null) {
                mix.addMass(type, mass);
            }
        });
        // energy > 0：反应吸热（气体温度下降）；energy < 0：放热
        if (energy != 0) {
            mix.addHeat(-energy);
        }
    }

    private void consumeItems(final RecipeInput input) {
        if (ingredients.isEmpty()) {
            return;
        }
        final boolean[] consumed = new boolean[ingredients.size()];
        for (int slot = 0; slot < input.size(); slot++) {
            final ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            for (int i = 0; i < ingredients.size(); i++) {
                if (!consumed[i] && ingredients.get(i).test(stack)) {
                    consumed[i] = true;
                    stack.shrink(1);
                    break;
                }
            }
        }
    }

    /** 产物物品（第一件为"主产物"）。 */
    public ItemStack primaryResult() {
        return results.isEmpty() ? ItemStack.EMPTY : results.get(0);
    }

    // ===================== 原版 Recipe 接口 =====================

    @Override
    public boolean matches(final RecipeInput input, final Level level) {
        // 气体状态不在 Container 里，所以原版意义上的 matches 只判物品（合成器用三参版本）
        return supportsContent && hasItems(input);
    }

    @Override
    public ItemStack assemble(final RecipeInput input, final HolderLookup.Provider registries) {
        return primaryResult().copy();
    }

    @Override
    public boolean canCraftInDimensions(final int width, final int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(final HolderLookup.Provider registries) {
        return primaryResult();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.copyOf(ingredients);
    }

    /**
     * 不进原版配方书。
     *
     * <p>原版 {@code ClientRecipeBook.categorizeAndGroupRecipes} 先看 {@code isSpecial()}、
     * 再看 {@code isIncomplete()}，两者都为 false 才会去取配方书类别；取不到就刷
     * {@code Unknown recipe category: vs_clockwork:gas_crafting/<配方 id>}（实机日志里 5 条配方各刷一次）。
     * 本类型既不是工作台配方也不是烧炼配方，NeoForge 那边也没有为它注册类别；
     * Create 的 {@code ProcessingRecipe}（同样是机器配方）就是返回 true 来绕开这一步，这里照抄该做法：
     * 机器配方本来也不该出现在工作台配方书里。
     */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return GasCraftingRecipeSerializer.INSTANCE;
    }

    @Override
    public RecipeType<?> getType() {
        return GasCraftingRecipeSerializer.TYPE;
    }

    // ===================== 网络同步 =====================

    private static void writeNetwork(final RegistryFriendlyByteBuf buffer, final GasCraftingRecipe recipe) {
        buffer.writeVarInt(recipe.ingredients().size());
        for (final Ingredient ingredient : recipe.ingredients()) {
            Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, ingredient);
        }
        writeGasMap(buffer, recipe.inputGases());
        writeGasMap(buffer, recipe.resultGases());
        buffer.writeVarInt(recipe.results().size());
        for (final ItemStack stack : recipe.results()) {
            ItemStack.STREAM_CODEC.encode(buffer, stack);
        }
        buffer.writeDouble(recipe.minTemperature());
        buffer.writeBoolean(recipe.superHeated());
        buffer.writeBoolean(recipe.heated());
        buffer.writeDouble(recipe.energy());
        buffer.writeVarInt(recipe.processingTime());
        buffer.writeBoolean(recipe.supportsContent());
    }

    private static GasCraftingRecipe readNetwork(final RegistryFriendlyByteBuf buffer) {
        final int ingredientCount = buffer.readVarInt();
        final List<Ingredient> ingredients = new ArrayList<>(ingredientCount);
        for (int i = 0; i < ingredientCount; i++) {
            ingredients.add(Ingredient.CONTENTS_STREAM_CODEC.decode(buffer));
        }
        final Map<ResourceLocation, Double> inputGases = readGasMap(buffer);
        final Map<ResourceLocation, Double> resultGases = readGasMap(buffer);
        final int resultCount = buffer.readVarInt();
        final List<ItemStack> results = new ArrayList<>(resultCount);
        for (int i = 0; i < resultCount; i++) {
            results.add(ItemStack.STREAM_CODEC.decode(buffer));
        }
        return new GasCraftingRecipe(ingredients, inputGases, resultGases, results,
                buffer.readDouble(), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readDouble(), buffer.readVarInt(), buffer.readBoolean());
    }

    private static void writeGasMap(final RegistryFriendlyByteBuf buffer,
                                    final Map<ResourceLocation, Double> gases) {
        buffer.writeVarInt(gases.size());
        gases.forEach((id, mass) -> {
            buffer.writeResourceLocation(id);
            buffer.writeDouble(mass);
        });
    }

    private static Map<ResourceLocation, Double> readGasMap(final RegistryFriendlyByteBuf buffer) {
        final int size = buffer.readVarInt();
        final Map<ResourceLocation, Double> map = new LinkedHashMap<>(size);
        for (int i = 0; i < size; i++) {
            map.put(buffer.readResourceLocation(), buffer.readDouble());
        }
        return map;
    }
}
