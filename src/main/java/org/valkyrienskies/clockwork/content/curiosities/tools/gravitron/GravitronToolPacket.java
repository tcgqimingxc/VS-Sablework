package org.valkyrienskies.clockwork.content.curiosities.tools.gravitron;

import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 客户端 → 服务端：轮盘菜单选中的模式变了（旧版 {@code ToolType} + {@code GravitronDialPacket} 里的同步）。
 *
 * <p>为什么必须发包：右键的判定发生在服务端（{@code Item#use}），客户端自己改状态服务端看不到，
 * 所以菜单一改就发这个包；服务端顺手回一条行动栏文字，这就是"模式已切换"的可见反馈。
 */
public record GravitronToolPacket(int tool) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GravitronToolPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, "gravitron_tool"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GravitronToolPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, GravitronToolPacket::tool,
                    GravitronToolPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final GravitronToolPacket payload, final IPayloadContext context) {
        // 不需要 context.enqueueWork：注册用的是默认的 HandlerThread.MAIN，
        // NeoForge 已经用 MainThreadPayloadHandler 把处理器调度到主线程（PayloadRegistrar#register）。
        if (!(context.player() instanceof final ServerPlayer sender)) {
            return;
        }
        final GravitronTools tool = GravitronTools.byIndex(payload.tool());
        GravitronTools.set(sender, tool);
        sender.displayClientMessage(
                Component.translatable("vs_clockwork.gravitron.tool.selected", tool.displayName())
                        .withStyle(ChatFormatting.AQUA),
                true);
    }
}
