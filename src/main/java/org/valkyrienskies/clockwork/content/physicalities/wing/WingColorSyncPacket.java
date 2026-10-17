package org.valkyrienskies.clockwork.content.physicalities.wing;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 服务端 -> 客户端：同步机翼 / 襟翼的染料颜色。
 *
 * <p>对应旧版 {@code content.physicalities.wing.BlockEntityColorPacket}。
 *
 * <p>与旧版的差异：旧版走 Clockwork 自己的网络封装（{@code S2CCWPacket} + 手写 FriendlyByteBuf），
 * 这里改成 NeoForge 标准的 {@link CustomPacketPayload} + {@link StreamCodec}，
 * 由 {@code CWKineticsModBus} 统一注册。
 */
public record WingColorSyncPacket(BlockPos pos, int color) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<WingColorSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, "wing_color"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WingColorSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, WingColorSyncPacket::pos,
                    ByteBufCodecs.VAR_INT, WingColorSyncPacket::color,
                    WingColorSyncPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WingColorSyncPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Level level = context.player().level();
            if (level.getBlockEntity(payload.pos()) instanceof WingColorBlockEntity be) {
                be.setColor(payload.color());
            }
        });
    }
}
