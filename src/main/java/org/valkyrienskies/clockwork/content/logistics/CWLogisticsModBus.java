package org.valkyrienskies.clockwork.content.logistics;

import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 物流切片自己的 mod 事件总线订阅（音效注册）。
 *
 * <p>与 {@code CWKineticsModBus} 同样的理由：按 PORT-CONVENTIONS，各切片不改 {@code index/} 与主类，
 * 所以用 {@link EventBusSubscriber} 自动挂载，Lead 只需要调用 {@code CWGasIndex#register()}。
 */
@EventBusSubscriber(modid = ClockworkMod.MOD_ID)
public final class CWLogisticsModBus {

    private CWLogisticsModBus() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.SOUND_EVENT)) {
            CWLogisticsSounds.registerAll(event);
        }
    }
}
