package org.valkyrienskies.clockwork.content.curiosities.tools.gravitron;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 服务端 → 客户端：重力枪表盘指针该指到哪个角度（旧版 {@code GravitronDialPacket}）。
 *
 * <p>抓住结构时按质量占比给一个角度、放开时归零；指针只在客户端模型里画（见
 * {@code client/GravitronItemRenderer}），所以这个包只带一个 float。
 */
public record GravitronDialPacket(float angle) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GravitronDialPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, "gravitron_dial"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GravitronDialPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT, GravitronDialPacket::angle,
                    GravitronDialPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final GravitronDialPacket payload, final IPayloadContext context) {
        // 目标只是几个 float 的静态字段（GravitronDialState 不依赖任何客户端类型），
        // 因此这里不需要再区分物理端；专用服务器上没有任何渲染器会读它。
        GravitronDialState.set(payload.angle());
    }
}
