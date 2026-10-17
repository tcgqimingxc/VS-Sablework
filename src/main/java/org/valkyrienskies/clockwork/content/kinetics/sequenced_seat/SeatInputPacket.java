package org.valkyrienskies.clockwork.content.kinetics.sequenced_seat;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 客户端 → 服务端：座椅上玩家按下的键变了（旧版 {@code SequencedSeatDrivingPacket.kt}）。
 *
 * <p>只发"位掩码"，由服务端从实体 id 反查座椅方块实体；并且校验发包人确实是那个座位的乘客。
 */
public record SeatInputPacket(int seatId, int keyMask) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SeatInputPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, "seat_input"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SeatInputPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SeatInputPacket::seatId,
            ByteBufCodecs.VAR_INT, SeatInputPacket::keyMask,
            SeatInputPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final SeatInputPacket payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof final ServerPlayer sender)) {
                return;
            }
            final Level level = sender.level();
            if (level.getEntity(payload.seatId()) instanceof final CommandSeatEntity seat
                    && seat.getFirstPassenger() == sender
                    && level.getBlockEntity(seat.blockPosition()) instanceof final CommandSeatBlockEntity be) {
                be.updateInput(InputKey.fromMask(payload.keyMask()));
            }
        });
    }
}
