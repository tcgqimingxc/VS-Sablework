package org.valkyrienskies.clockwork.integration.ponder;

import net.createmod.ponder.foundation.PonderIndex;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * Ponder 插件的客户端注册入口。
 *
 * <p>旧版在 Forge/Fabric 各自的客户端初始化里 {@code ClockworkPonderPlugin()} 直接交给 Create；
 * 1.21.1 的写法是 {@code PonderIndex.addPlugin(...)}。时机很关键：必须早于 Ponder 第一次
 * {@code registerAll()}，而 Create 自己就是在 {@link FMLClientSetupEvent}
 * （{@code CreateClient.clientInit}）里注册的，所以这里用同一个事件。
 *
 * <p>{@code value = Dist.CLIENT} 让这个类只在客户端加载 —— 专用服务器上
 * {@code PonderIndex} 与 {@code CreateSceneBuilder} 都不该被触碰（Ponder 的场景注册是纯客户端行为）。
 */
@SuppressWarnings("removal")   // bus() 在 NeoForge 21.1 已标记删除，但仍是唯一能挂到 mod 总线上的写法
@EventBusSubscriber(modid = ClockworkMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClockworkPonderClient {

    private ClockworkPonderClient() {
    }

    @SubscribeEvent
    public static void onClientSetup(final FMLClientSetupEvent event) {
        PonderIndex.addPlugin(new ClockworkPonderPlugin());
    }
}
