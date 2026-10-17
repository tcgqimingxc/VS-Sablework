import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipeParams;

import java.lang.reflect.Field;
import java.util.List;

/** Probe v2: does a failed ingredient element get DROPPED (partial success) or fail the whole list? */
public class Probe2 {
    static RegistryOps<JsonElement> ops;

    public static void main(String[] args) throws Exception {
        stubFml();
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));

        // register dummy items so real ids resolve
        unfreeze(BuiltInRegistries.ITEM);
        for (String id : new String[]{
                "vs_clockwork:incomplete_wanderwand", "vs_clockwork:incomplete_hose_spool",
                "vs_clockwork:incomplete_telescoping_mechanism", "vs_clockwork:duct",
                "vs_clockwork:universal_shaft_item", "vs_clockwork:extendon_hose",
                "simulated:incomplete_engine_assembly", "simulated:incomplete_gyroscopic_mechanism",
                "simulated:engine_assembly", "simulated:gyroscopic_mechanism",
                "create:cogwheel", "create:shaft", "create:zinc_nugget", "create:brass_nugget",
                "create:iron_sheet", "create:andesite_alloy", "create:crushed_raw_iron",
                "create:industrial_iron_block", "create:brass_ingot", "create:golden_sheet",
                "create:crushed_raw_gold", "create:andesite_alloy_block", "create:chute",
                "create:unprocessed_obsidian_sheet", "create:precision_mechanism", "create:sturdy_sheet",
                "create:incomplete_precision_mechanism", "create:track", "create:large_cogwheel",
                "create:brass_nugget", "create:iron_nugget", "minecraft:iron_nugget", "minecraft:gold_nugget",
                "minecraft:dried_kelp", "minecraft:iron_ingot", "minecraft:iron_block", "minecraft:compass",
                "minecraft:clock", "minecraft:iron_bars", "minecraft:iron_helmet", "minecraft:dried_kelp_block",
                "minecraft:barrier"}) {
            try {
                Item item = new Item(new Item.Properties());
                Registry.register(BuiltInRegistries.ITEM, ResourceLocation.parse(id), item);
            } catch (Throwable t) {
                // already registered (e.g. minecraft:iron_nugget) -> ignore
            }
        }
        System.out.println("### dummy items registered\n");

        String base = "\"processing_time\":100,\"results\":[{\"id\":\"minecraft:iron_ingot\"}]";

        // ---- ingredient-list semantics through the real params codec
        t("A two valid items", "{\"ingredients\":[{\"item\":\"minecraft:iron_nugget\"},{\"item\":\"minecraft:gold_nugget\"}]," + base + "}");
        t("B valid + MISSING item", "{\"ingredients\":[{\"item\":\"minecraft:iron_nugget\"},{\"item\":\"vs_clockwork:NOT_THERE\"}]," + base + "}");
        t("C only MISSING item", "{\"ingredients\":[{\"item\":\"vs_clockwork:NOT_THERE\"}]," + base + "}");
        t("D nested array both valid", "{\"ingredients\":[[{\"item\":\"minecraft:iron_nugget\"},{\"item\":\"minecraft:gold_nugget\"}]]," + base + "}");
        t("E nested array + missing", "{\"ingredients\":[[{\"item\":\"minecraft:iron_nugget\"},{\"item\":\"vs_clockwork:NOT_THERE\"}]]," + base + "}");
        t("F empty array", "{\"ingredients\":[]," + base + "}");
        t("G tag", "{\"ingredients\":[{\"tag\":\"c:nuggets/iron\"}]," + base + "}");
        t("H our hose step", "{\"ingredients\":[{\"item\":\"vs_clockwork:incomplete_hose_spool\"},{\"item\":\"minecraft:dried_kelp\"}]," + base + "}");
        t("I our wanderwand step", "{\"ingredients\":[{\"item\":\"vs_clockwork:incomplete_wanderwand\"},[{\"item\":\"minecraft:iron_nugget\"},{\"item\":\"create:zinc_nugget\"}]]," + base + "}");
        t("J fluid + item (filling)", "{\"ingredients\":[{\"item\":\"minecraft:iron_nugget\"},{\"type\":\"neoforge:single\",\"amount\":500,\"fluid\":\"minecraft:lava\"}]," + base + "}");
        t("K fluid only (filling)", "{\"ingredients\":[{\"type\":\"neoforge:single\",\"amount\":500,\"fluid\":\"minecraft:lava\"}]," + base + "}");
        t("L string item (old format)", "{\"ingredients\":[\"minecraft:iron_nugget\"]," + base + "}");

        // ---- deploying params codec (ItemApplicationRecipeParams)
        System.out.println("--- create:deploying params codec ---");
        d("M deploying 2 items", "{\"ingredients\":[{\"item\":\"vs_clockwork:incomplete_hose_spool\"},{\"item\":\"minecraft:dried_kelp\"}]," + base + "}");
        d("N deploying missing 2nd", "{\"ingredients\":[{\"item\":\"vs_clockwork:incomplete_hose_spool\"},{\"item\":\"vs_clockwork:NOT_THERE\"}]," + base + "}");
        d("O deploying nested array", "{\"ingredients\":[{\"item\":\"vs_clockwork:incomplete_wanderwand\"},[{\"item\":\"minecraft:iron_nugget\"},{\"item\":\"create:zinc_nugget\"}]]," + base + "}");
        d("P deploying keep_held_item", "{\"ingredients\":[{\"item\":\"vs_clockwork:incomplete_hose_spool\"},{\"item\":\"minecraft:dried_kelp\"}],\"keep_held_item\":true," + base + "}");
    }

    static void t(String label, String json) {
        run(label, ProcessingRecipeParams.CODEC, json);
    }

    static void d(String label, String json) {
        run(label, ItemApplicationRecipeParams.CODEC, json);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void run(String label, MapCodec<? extends ProcessingRecipeParams> mc, String json) {
        try {
            DataResult<? extends ProcessingRecipeParams> r = ((MapCodec) mc).codec().parse(ops, JsonParser.parseString(json));
            if (r.result().isPresent()) {
                ProcessingRecipeParams p = r.result().get();
                System.out.printf("  %-28s DECODED  items=%d fluids=%d results=%d  %s%n", label,
                        size(p, "ingredients"), size(p, "fluidIngredients"), size(p, "results"),
                        items(p));
            } else {
                System.out.printf("  %-28s ERROR: %s%n", label, r.error().map(e -> e.message()).orElse("?"));
            }
        } catch (Throwable t) {
            System.out.printf("  %-28s THREW %s: %s%n", label, t.getClass().getSimpleName(), t.getMessage());
            for (int i = 0; i < Math.min(4, t.getStackTrace().length); i++) {
                System.out.println("        at " + t.getStackTrace()[i]);
            }
        }
    }

    static int size(Object p, String f) {
        try {
            Field fd = ProcessingRecipeParams.class.getDeclaredField(f);
            fd.setAccessible(true);
            return ((List<?>) fd.get(p)).size();
        } catch (Exception e) {
            return -1;
        }
    }

    static String items(Object p) {
        try {
            Field fd = ProcessingRecipeParams.class.getDeclaredField("ingredients");
            fd.setAccessible(true);
            List<?> l = (List<?>) fd.get(p);
            StringBuilder sb = new StringBuilder("[");
            for (Object o : l) {
                sb.append(((Ingredient) o).getItems().length).append(" items,");
            }
            return sb.append("]").toString();
        } catch (Exception e) {
            return "?";
        }
    }

    static void stubFml() throws Exception {
        Class<?> c = Class.forName("net.neoforged.fml.loading.LoadingModList");
        java.lang.reflect.Constructor<?> ctor = c.getDeclaredConstructor(
                List.class, List.class, List.class, java.util.Map.class);
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
