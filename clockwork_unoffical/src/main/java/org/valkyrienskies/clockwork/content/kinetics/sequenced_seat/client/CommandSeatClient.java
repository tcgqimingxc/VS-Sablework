package org.valkyrienskies.clockwork.content.kinetics.sequenced_seat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.valkyrienskies.clockwork.content.kinetics.sequenced_seat.CommandSeatBlockEntity;
import org.valkyrienskies.clockwork.content.kinetics.sequenced_seat.CommandSeatEntity;
import org.valkyrienskies.clockwork.content.kinetics.sequenced_seat.InputKey;
import org.valkyrienskies.clockwork.content.kinetics.sequenced_seat.SeatInputPacket;

import java.util.EnumSet;
import java.util.Set;

/**
 * 命令座椅的客户端入口（旧版 {@code SequencedSeatEntity#checkKeybinds} + {@code SequencedSeatBlock#displayScreen}）。
 *
 * <p>单独一个类是为了让 {@link CommandSeatEntity} / {@code CommandSeatBlock} 只引用客户端类名而不加载它：
 * 服务端永远走不到这些方法，JVM 也就不会解析到 {@code net.minecraft.client.**}。
 */
public final class CommandSeatClient {

    private CommandSeatClient() {
    }

    /**
     * 本地玩家坐在该座位上时返回当前按下的键；否则返回 {@code null}
     * （旧版用 {@code firstPassenger is LocalPlayer} 判断）。
     */
    public static Set<InputKey> collectKeys(final CommandSeatEntity seat) {
        final LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || seat.getFirstPassenger() != player) {
            return null;
        }
        final Input input = player.input;
        final Set<InputKey> keys = EnumSet.noneOf(InputKey.class);
        if (input.left) {
            keys.add(InputKey.LEFT);
        }
        if (input.right) {
            keys.add(InputKey.RIGHT);
        }
        if (input.up) {
            keys.add(InputKey.FORWARD);
        }
        if (input.down) {
            keys.add(InputKey.BACKWARD);
        }
        if (input.jumping) {
            keys.add(InputKey.JUMP);
        }
        return keys;
    }

    public static void sendInput(final CommandSeatEntity seat, final Set<InputKey> keys) {
        PacketDistributor.sendToServer(new SeatInputPacket(seat.getId(), InputKey.toMask(keys)));
    }

    public static void openScreen(final CommandSeatBlockEntity seat) {
        Minecraft.getInstance().setScreen(new CommandSeatScreen(seat));
    }
}
