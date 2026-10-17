import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

public class Probe {
    static RegistryOps<JsonElement> ops;

    /** Stub FML's LoadingModList so NeoForge's patched Bootstrap can run outside the mod loader. */
    static void stubFml() throws Exception {
        Class<?> c = Class.forName("net.neoforged.fml.loading.LoadingModList");
        java.lang.reflect.Constructor<?> ctor = c.getDeclaredConstructor(
                java.util.List.class, java.util.List.class, java.util.List.class, java.util.Map.class);
        ctor.setAccessible(true);
        Object inst = ctor.newInstance(new java.util.ArrayList<>(), new java.util.ArrayList<>(),
                new java.util.ArrayList<>(), new java.util.HashMap<>());
        Field f = c.getDeclaredField("INSTANCE");
        f.setAccessible(true);
        f.set(null, inst);
        System.out.println("### stubbed LoadingModList: " + c.getMethod("get").invoke(null));
    }

    public static void main(String[] args) throws Exception {
        stubFml();
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        System.out.println("### bootstrap OK");

        // --- 1) which ingredient JSON forms are accepted, and how many items result? ---
        ingredient("\"minecraft:iron_nugget\"");
        ingredient("{\"item\":\"minecraft:iron_nugget\"}");
        ingredient("{\"tag\":\"c:nuggets/iron\"}");
        ingredient("\"#c:nuggets/iron\"");
        ingredient("[{\"item\":\"minecraft:iron_nugget\"},{\"item\":\"create:zinc_nugget\"}]");
        ingredient("[{\"item\":\"minecraft:iron_nugget\"}]");
        ingredient("[]");
        ingredient("{\"item\":\"vs_clockwork:does_not_exist\"}");

        // --- 2) full SequencedAssemblyRecipe decode with Create's real serializer ---
        unfreeze(BuiltInRegistries.RECIPE_SERIALIZER);
        RecipeSerializer<?> sa = null;
        for (AllRecipeTypes t : AllRecipeTypes.values()) {
            try {
                RecipeSerializer<?> s = (RecipeSerializer<?>) t.serializerSupplier.get();
                Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, t.getId(), s);
                if (t == AllRecipeTypes.SEQUENCED_ASSEMBLY) sa = s;
            } catch (Throwable e) {
                System.out.println("  (skip register " + t.getId() + ": " + e + ")");
            }
        }
        System.out.println("### registered create recipe serializers; SA serializer = " + sa);
        if (sa == null) { System.out.println("FATAL: no SA serializer"); return; }

        for (String f : args) {
            System.out.println("\n================ " + f);
            JsonElement json = JsonParser.parseString(Files.readString(Path.of(f)));
            testWhole(sa, json);
            // per-step params level detail
            JsonObject o = json.getAsJsonObject();
            if (o.has("sequence")) {
                int i = 0;
                for (JsonElement step : o.getAsJsonArray("sequence")) {
                    String type = step.getAsJsonObject().get("type").getAsString();
                    MapCodec<? extends ProcessingRecipeParams> mc;
                    if (type.equals("create:deploying")) {
                        mc = com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipeParams.CODEC;
                    } else {
                        mc = ProcessingRecipeParams.CODEC;
                    }
                    try {
                        DataResult<? extends ProcessingRecipeParams> r = mc.codec().parse(ops, step);
                        if (r.result().isPresent()) {
                            System.out.println("   step" + i + " " + type + " -> items=" + listSize(r.result().get(), "ingredients")
                                    + " fluids=" + listSize(r.result().get(), "fluidIngredients")
                                    + " results=" + listSize(r.result().get(), "results")
                                    + " itemList=" + itemsOf(r.result().get()));
                        } else {
                            System.out.println("   step" + i + " " + type + " -> DECODE ERROR: " + r.error().map(e -> e.message()).orElse("?"));
                        }
                    } catch (Throwable t) {
                        System.out.println("   step" + i + " " + type + " -> THREW " + t);
                        t.printStackTrace(System.out);
                    }
                    i++;
                }
            }
        }
    }

    static void ingredient(String json) {
        try {
            DataResult<Ingredient> r = Ingredient.CODEC.parse(ops, JsonParser.parseString(json));
            if (r.result().isPresent()) {
                Ingredient ing = r.result().get();
                System.out.println("ING " + json + "  -> OK items=" + ing.getItems().length + " empty=" + ing.isEmpty()
                        + " [" + java.util.Arrays.toString(java.util.Arrays.stream(ing.getItems()).map(s -> s.getItem().toString()).toArray()) + "]");
            } else {
                System.out.println("ING " + json + "  -> ERROR: " + r.error().map(e -> e.message()).orElse("?"));
            }
        } catch (Throwable t) {
            System.out.println("ING " + json + "  -> THREW " + t);
        }
    }

    static void testWhole(RecipeSerializer<?> sa, JsonElement json) {
        try {
            DataResult<?> r = sa.codec().codec().parse(ops, json);
            if (r.result().isPresent()) {
                System.out.println("   >>> FULL DECODE OK: " + r.result().get());
            } else {
                System.out.println("   >>> FULL DECODE ERROR: " + r.error().map(e -> e.message()).orElse("?"));
            }
        } catch (Throwable t) {
            System.out.println("   >>> FULL DECODE THREW: " + t);
            StackTraceElement[] st = t.getStackTrace();
            for (int i = 0; i < Math.min(6, st.length); i++) System.out.println("        at " + st[i]);
        }
    }

    static int listSize(Object params, String field) {
        try {
            Field f = ProcessingRecipeParams.class.getDeclaredField(field);
            f.setAccessible(true);
            return ((java.util.List<?>) f.get(params)).size();
        } catch (Exception e) {
            return -1;
        }
    }

    static String itemsOf(Object params) {
        try {
            Field f = ProcessingRecipeParams.class.getDeclaredField("ingredients");
            f.setAccessible(true);
            java.util.List<?> l = (java.util.List<?>) f.get(params);
            StringBuilder sb = new StringBuilder("[");
            for (Object o : l) {
                sb.append(java.util.Arrays.toString(((Ingredient) o).getItems())).append(",");
            }
            return sb.append("]").toString();
        } catch (Exception e) {
            return "?";
        }
    }

    static void unfreeze(Registry<?> reg) {
        try {
            Method m = reg.getClass().getMethod("unfreeze");
            m.invoke(reg);
            System.out.println("### unfroze " + reg + " via unfreeze()");
            return;
        } catch (Throwable ignored) {
        }
        try {
            Field f = null;
            Class<?> c = reg.getClass();
            while (c != null && f == null) {
                try { f = c.getDeclaredField("frozen"); } catch (NoSuchFieldException e) { c = c.getSuperclass(); }
            }
            if (f != null) { f.setAccessible(true); f.setBoolean(reg, false); System.out.println("### unfroze via field"); }
        } catch (Throwable t) {
            System.out.println("### could not unfreeze: " + t);
        }
    }
}
