package org.valkyrienskies.clockwork.content.curiosities.tools.gravitron;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 重力枪切片的事件订阅（音效注册 + 网络包 + 下线清理）。
 *
 * <p>为什么不写进 {@code ClockworkMod}：按 PORT-CONVENTIONS，切片不改主类，
 * 用 {@link EventBusSubscriber} 自动挂载，Lead 只要保证 {@code CWToolsIndex} 被类加载即可。
 *
 * <p>本类同时有 mod 总线事件（注册/网络包）与游戏总线事件（玩家下线）：NeoForge 21.1.228 的
 * {@code AutomaticEventSubscriber#inject} 会逐个检查方法订阅的事件类型
 * （{@code IModBusEvent} → mod 总线，否则游戏总线，混着就"两边各注册一次"），
 * 所以不需要（也不能再）用 {@code bus = Bus.MOD}——该属性已标记 {@code @Deprecated(forRemoval = true)}。
 */
@EventBusSubscriber(modid = ClockworkMod.MOD_ID)
public final class GravitronModBus {

    /** 网络协议版本号：本切片的包结构变化时才需要 +1。 */
    private static final String PROTOCOL_VERSION = "1";

    private GravitronModBus() {
    }

    @SubscribeEvent
    public static void onRegister(final RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.SOUND_EVENT)) {
            GravitronSounds.registerAll(event);
        }
    }

    @SubscribeEvent
    public static void onRegisterPayloads(final RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .playToServer(GravitronToolPacket.TYPE, GravitronToolPacket.STREAM_CODEC, GravitronToolPacket::handle)
                .playToClient(GravitronDialPacket.TYPE, GravitronDialPacket.STREAM_CODEC, GravitronDialPacket::handle);
    }

    /**
     * 下线时忘掉该玩家选中的模式。
     *
     * <p>必须和客户端的清理（{@code GravitronToolMenuHandler#onLoggingOut}）成对做：
     * 否则服务端留着 DESTROY、客户端镜像已回到 GRAB，重新进服后菜单显示的与实际行为不符。
     */
    @SubscribeEvent
    public static void onLoggedOut(final PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof Player player) {
            GravitronTools.clear(player);
        }
    }
}
