package org.valkyrienskies.clockwork.content.kinetics.sequenced_seat;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 客户端 → 服务端：把界面上编辑好的四张规则表发回方块实体
 * （旧版 {@code UpdateSeatRulesPacket.kt}）。
 *
 * <p>四个方向的规则打包成一个 {@link CompoundTag}（键 {@code Forward/Backward/Left/Right}），
 * 与方块实体自己的 NBT 布局一致。
 */
public record SeatRulesPacket(BlockPos pos, CompoundTag rules) implements CustomPacketPayload {

    public static final String FORWARD = "Forward";
    public static final String BACKWARD = "Backward";
    public static final String LEFT = "Left";
    public static final String RIGHT = "Right";

    public static final CustomPacketPayload.Type<SeatRulesPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, "seat_rules"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SeatRulesPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SeatRulesPacket::pos,
            ByteBufCodecs.COMPOUND_TAG, SeatRulesPacket::rules,
            SeatRulesPacket::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 把方块实体当前的规则表打包（界面关闭时用）。 */
    public static SeatRulesPacket of(final CommandSeatBlockEntity be) {
        final CompoundTag tag = new CompoundTag();
        tag.put(FORWARD, be.getList(net.minecraft.world.level.block.Rotation.NONE).serializeNBT());
        tag.put(BACKWARD, be.getList(net.minecraft.world.level.block.Rotation.CLOCKWISE_180).serializeNBT());
        tag.put(LEFT, be.getList(net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90).serializeNBT());
        tag.put(RIGHT, be.getList(net.minecraft.world.level.block.Rotation.CLOCKWISE_90).serializeNBT());
        return new SeatRulesPacket(be.getBlockPos(), tag);
    }

    public static void handle(final SeatRulesPacket payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof final ServerPlayer sender)) {
                return;
            }
            if (sender.level().getBlockEntity(payload.pos()) instanceof final CommandSeatBlockEntity be
                    && be.canPlayerUse(sender)) {
                be.updateRules(read(payload.rules(), FORWARD), read(payload.rules(), BACKWARD),
                        read(payload.rules(), LEFT), read(payload.rules(), RIGHT));
            }
        });
    }

    private static SeatRuleList read(final CompoundTag tag, final String key) {
        final SeatRuleList list = new SeatRuleList();
        list.deserializeNBT(tag.getList(key, CompoundTag.TAG_COMPOUND));
        return list;
    }
}
