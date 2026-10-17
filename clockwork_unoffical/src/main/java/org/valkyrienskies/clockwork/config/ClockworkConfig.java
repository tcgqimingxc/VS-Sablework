package org.valkyrienskies.clockwork.config;

import net.createmod.catnip.config.ConfigBase;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 配置入口。沿用 Create 生态的 {@link ConfigBase} 写法（与 Create: Aeronautics 一致），
 * 这样配置文件带注释、可热重载，后续要加配置项只需在 {@link CWServer}/{@link CWClient} 里加字段。
 */
public final class ClockworkConfig {

    private static final Map<ModConfig.Type, ConfigBase> CONFIGS = new EnumMap<>(ModConfig.Type.class);

    private ClockworkConfig() {
    }

    public static CWServer server() {
        return (CWServer) CONFIGS.get(ModConfig.Type.SERVER);
    }

    public static CWClient client() {
        return (CWClient) CONFIGS.get(ModConfig.Type.CLIENT);
    }

    public static void register(ModContainer container) {
        register(container, ModConfig.Type.SERVER, CWServer::new);
        register(container, ModConfig.Type.CLIENT, CWClient::new);
    }

    private static <T extends ConfigBase> void register(ModContainer container, ModConfig.Type type, Supplier<T> factory) {
        Pair<T, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(builder -> {
            T config = factory.get();
            config.registerAll(builder);
            return config;
        });

        T config = pair.getLeft();
        config.specification = pair.getRight();
        CONFIGS.put(type, config);

        container.registerConfig(type, config.specification);
    }
}
