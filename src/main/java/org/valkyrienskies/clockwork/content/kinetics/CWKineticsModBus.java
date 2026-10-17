package org.valkyrienskies.clockwork.content.kinetics;

import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.valkyrienskies.clockwork.ClockworkMod;
import org.valkyrienskies.clockwork.content.kinetics.sequenced_seat.SeatInputPacket;
import org.valkyrienskies.clockwork.content.kinetics.sequenced_seat.SeatRulesPacket;
import org.valkyrienskies.clockwork.content.physicalities.wing.WingColorSyncPacket;

/**
 * 动力部件切片自己的 mod 事件总线订阅（音效 / 网络包）。
 *
 * <p>为什么不写在 {@code ClockworkMod} 里：按 PORT-CONVENTIONS，各切片不许改 {@code index/} 与主类，
 * 所以这里用 {@link EventBusSubscriber} 自动挂载，Lead 只需要调用
 * {@link CWKineticsIndex#register()} 触发内容注册。
 */
@EventBusSubscriber(modid = ClockworkMod.MOD_ID)
public final class CWKineticsModBus {

    /** 网络协议版本号：本切片自己的字段有变化时才需要 +1。 */
    private static final String PROTOCOL_VERSION = "1";

    private CWKineticsModBus() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.SOUND_EVENT)) {
            CWKineticsSounds.registerAll(event);
        }
    }

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .playToClient(WingColorSyncPacket.TYPE, WingColorSyncPacket.STREAM_CODEC, WingColorSyncPacket::handle);
        // 命令座椅：按键状态与规则表都是客户端 → 服务端
        event.registrar(PROTOCOL_VERSION)
                .playToServer(SeatInputPacket.TYPE, SeatInputPacket.STREAM_CODEC, SeatInputPacket::handle);
        event.registrar(PROTOCOL_VERSION)
                .playToServer(SeatRulesPacket.TYPE, SeatRulesPacket.STREAM_CODEC, SeatRulesPacket::handle);
    }
}
