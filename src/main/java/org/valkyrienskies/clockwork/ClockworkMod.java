package org.valkyrienskies.clockwork;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.valkyrienskies.clockwork.config.ClockworkConfig;
import org.valkyrienskies.clockwork.content.contraptions.CWContraptionsIndex;
import org.valkyrienskies.clockwork.content.curiosities.CWCuriositiesIndex;
import org.valkyrienskies.clockwork.content.kinetics.CWKineticsIndex;
import org.valkyrienskies.clockwork.content.logistics.gas.CWGasIndex;
import org.valkyrienskies.clockwork.index.CWBlocks;
import org.valkyrienskies.clockwork.index.CWCreativeTabs;
import org.valkyrienskies.clockwork.index.CWDataComponents;
import org.valkyrienskies.clockwork.index.CWItems;
import org.valkyrienskies.clockwork.index.CWSounds;
import org.valkyrienskies.clockwork.integration.cc.ClockworkComputerCraft;

/**
 * Clockwork 主入口（Minecraft 1.21.1 / NeoForge）。
 *
 * <p>本移植以 Create 6.0.10 + Sable 2.0.5 + Create: Aeronautics(Simulated) 1.3.2 为前置。
 * 物理结构不再是 Valkyrien Skies 2 的 "Ship"，而是 Sable 的 "SubLevel"（子空间结构）。
 *
 * <p>初始化顺序（保持简单，便于维护）：
 * <ol>
 *   <li>创造标签 + Registrate 事件监听（必须先于任何内容注册）</li>
 *   <li>配置</li>
 *   <li>各内容索引类（方块 / 物品 / 方块实体 / 声音 / 数据组件 / 配方类型）</li>
 *   <li>网络通道与事件监听</li>
 * </ol>
 */
@Mod(ClockworkMod.MOD_ID)
public final class ClockworkMod {

    /** mod id，必须与 gradle.properties 的 mod_id 一致。 */
    public static final String MOD_ID = "vs_clockwork";

    public static final Logger LOGGER = LoggerFactory.getLogger("Clockwork");

    /** 全局注册器：所有内容都通过它注册，风格与 Create / Create: Aeronautics 保持一致。 */
    public static final ClockworkRegistrate REGISTRATE = ClockworkRegistrate.create(MOD_ID);

    public ClockworkMod(IEventBus modBus, ModContainer container) {
        // 1) 标签与注册器事件
        CWCreativeTabs.register(modBus);
        CWSounds.register(modBus);
        CWDataComponents.register(modBus);
        REGISTRATE.registerEventListeners(modBus);

        // 2) 配置
        ClockworkConfig.register(container);

        // 3) 内容注册（顺序：材料 -> 各玩法切片）
        CWBlocks.register();
        CWItems.register();
        CWKineticsIndex.register();
        CWGasIndex.register();
        CWCuriositiesIndex.register();
        CWContraptionsIndex.register();

        // 4) 可选 mod 集成：CC:Tweaked 的类只在它存在时才被加载（JVM 惰性解析，没装 CC 也不会崩）。
        //    这里只能用字符串判断，绝不能让 dan200.** 的 import/类型出现在公共代码里。
        if (ModList.get().isLoaded("computercraft")) {
            ClockworkComputerCraft.register(modBus);
        }

        LOGGER.info("Clockwork (1.21.1 port) loaded.");
    }
}
