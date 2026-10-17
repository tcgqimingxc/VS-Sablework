import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Probe v3: run Create's REAL SequencedAssemblyRecipeSerializer (the exact code path the game uses at
 * RecipeManager.apply) over the given sequenced_assembly recipe JSON files, and report every step's
 * item-ingredient count. A step with 0 item ingredients is exactly what makes
 * SequencedRecipe.initFromSequencedAssembly throw IndexOutOfBoundsException.
 */
public class Probe3 {
    static RegistryOps<JsonElement> ops;

    public static void main(String[] args) throws Exception {
        stubFml();
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));

        unfreeze(BuiltInRegistries.ITEM);
        unfreeze(BuiltInRegistries.RECIPE_TYPE);
        unfreeze(BuiltInRegistries.RECIPE_SERIALIZER);

        for (String id : new String[]{
                "vs_clockwork:incomplete_wanderwand", "vs_clockwork:incomplete_hose_spool",
                "vs_clockwork:incomplete_telescoping_mechanism", "vs_clockwork:duct",
                "vs_clockwork:universal_shaft_item", "vs_clockwork:extendon_hose",
                "simulated:incomplete_engine_assembly", "simulated:incomplete_gyroscopic_mechanism", "simulated:engine_assembly", "simulated:gyroscopic_mechanism", "create:cogwheel", "create:shaft", "create:zinc_nugget", "create:brass_nugget",
                "create:iron_sheet", "create:andesite_alloy", "create:crushed_raw_iron",
                "create:industrial_iron_block", "create:brass_ingot", "create:golden_sheet",
                "create:crushed_raw_gold", "create:chute", "create:unprocessed_obsidian_sheet",
                "create:precision_mechanism", "create:sturdy_sheet", "create:incomplete_precision_mechanism",
                "create:track", "create:large_cogwheel", "minecraft:dried_kelp", "minecraft:dried_kelp_block"}) {
            try {
                Registry.register(BuiltInRegistries.ITEM, ResourceLocation.parse(id), new Item(new Item.Properties()));
            } catch (Throwable ignored) {
            }
        }

        // AllRecipeTypes.getType() reads the private `type` Supplier field; bind it to our own RecipeType.
        Field typeField = AllRecipeTypes.class.getDeclaredField("type");
        typeField.setAccessible(true);

        RecipeSerializer<?> sa = null;
        for (AllRecipeTypes t : AllRecipeTypes.values()) {
            ResourceLocation id = t.getId();
            RecipeType<?> type = RecipeType.simple(id);
            Registry.register(BuiltInRegistries.RECIPE_TYPE, id, type);
            typeField.set(t, (java.util.function.Supplier<RecipeType<?>>) () -> type);

            RecipeSerializer<?> ser = (RecipeSerializer<?>) t.serializerSupplier.get();
            Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, id, ser);
            if (t == AllRecipeTypes.SEQUENCED_ASSEMBLY) sa = ser;
        }
        System.out.println("### recipe types + serializers registered; SA serializer = " + sa + "\n");

        for (String f : args) {
            System.out.println("================ " + f);
            JsonElement json = JsonParser.parseString(Files.readString(Path.of(f)));
            try {
                DataResult<?> r = sa.codec().codec().parse(ops, json);
                if (r.result().isPresent()) {
                    Object recipe = r.result().get();
                    System.out.println("   >>> DECODE OK");
                    inspect(recipe);
                } else {
                    System.out.println("   >>> DECODE ERROR: " + r.error().map(e -> e.message()).orElse("?"));
                }
            } catch (Throwable t) {
                System.out.println("   >>> *** THREW " + t + " ***");
                StackTraceElement[] st = t.getStackTrace();
                for (int i = 0; i < Math.min(5, st.length); i++) {
                    System.out.println("        at " + st[i]);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    static void inspect(Object recipe) throws Exception {
        Field seqF = SequencedAssemblyRecipe.class.getDeclaredField("sequence");
        seqF.setAccessible(true);
        List<SequencedRecipe<?>> seq = (List<SequencedRecipe<?>>) seqF.get(recipe);
        Field wrappedF = SequencedRecipe.class.getDeclaredField("wrapped");
        wrappedF.setAccessible(true);
        System.out.println("   steps: " + seq.size());
        int i = 0;
        boolean bad = false;
        for (SequencedRecipe<?> sr : seq) {
            Object wrapped = wrappedF.get(sr);
            Field ingF = com.simibubi.create.content.processing.recipe.ProcessingRecipe.class.getDeclaredField("ingredients");
            ingF.setAccessible(true);
            List<Ingredient> ings = (List<Ingredient>) ingF.get(wrapped);
            StringBuilder sb = new StringBuilder();
            for (Ingredient ing : ings) {
                sb.append(ing.getItems().length).append(" items, ");
            }
            String flag = ings.isEmpty() ? "  <<<<<< WOULD CRASH (initFromSequencedAssembly set(0) on length 0)" : "";
            if (ings.isEmpty()) {
                bad = true;
            }
            System.out.printf("     step%d %-45s ingredients=%d [%s]%s%n", i++,
                    wrapped.getClass().getSimpleName(), ings.size(), sb.toString().trim(), flag);
        }
        System.out.println(bad ? "   >>> RESULT: UNSAFE" : "   >>> RESULT: SAFE (no empty ingredient list; initFromSequencedAssembly ran without throwing)");
    }

    static void stubFml() throws Exception {
        Class<?> c = Class.forName("net.neoforged.fml.loading.LoadingModList");
        java.lang.reflect.Constructor<?> ctor = c.getDeclaredConstructor(List.class, List.class, List.class, java.util.Map.class);
        ctor.setAccessible(true);
        Object inst = ctor.newInstance(new java.util.ArrayList<>(), new java.util.ArrayList<>(),
                new java.util.ArrayList<>(), new java.util.HashMap<>());
        Field f = c.getDeclaredField("INSTANCE");
        f.setAccessible(true);
        f.set(null, inst);
    }

    static void unfreeze(Registry<?> reg) {
        try {
            reg.getClass().getMethod("unfreeze").invoke(reg);
        } catch (Throwable t) {
            try {
                Field f = null;
                Class<?> c = reg.getClass();
                while (c != null && f == null) {
                    try { f = c.getDeclaredField("frozen"); } catch (NoSuchFieldException e) { c = c.getSuperclass(); }
                }
                if (f != null) { f.setAccessible(true); f.setBoolean(reg, false); }
            } catch (Throwable t2) {
                System.out.println("### unfreeze failed: " + t2);
            }
        }
    }
}

