package org.valkyrienskies.clockwork.content.curiosities.tools.gravitron.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.AllKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.valkyrienskies.clockwork.ClockworkMod;
import org.valkyrienskies.clockwork.content.curiosities.tools.gravitron.GravitronItem;
import org.valkyrienskies.clockwork.content.curiosities.tools.gravitron.GravitronToolPacket;
import org.valkyrienskies.clockwork.content.curiosities.tools.gravitron.GravitronTools;

/**
 * 模式轮盘的客户端入口（对应旧版 {@code GravitronHandler.kt} 的按键/滚轮部分）。
 *
 * <p>按键沿用 Create 的 {@code AllKeys.TOOL_MENU}（javap 核实默认键码 342 = 左 ALT，描述
 * "Focus Schematic Overlay"）：旧版 Clockwork 用的就是这个键，用户截图里的左 ALT 也是它，
 * 复用现成的 KeyMapping 就不必自己注册按键、也不会和 Create 抢同一个键位。
 *
 * <p>打开条件与旧版一致：按住键的<b>按下瞬间</b>、手里拿着重力枪、没有别的界面、非旁观者。
 * 关闭由菜单自己负责（松开按键 / 左键确认 / 右键取消 / 换手），这里只在关闭后设一小段冷却，
 * 避免同时按着 Create 的工具箱键时两边反复抢屏幕。
 */
@EventBusSubscriber(modid = ClockworkMod.MOD_ID, value = Dist.CLIENT)
public final class GravitronToolMenuHandler {

    /** 关闭后的冷却 [客户端刻]（Create 的 RadialWrenchHandler 同样用冷却避免立刻重开）。 */
    private static final int REOPEN_COOLDOWN = 3;
    private static int cooldown;

    private GravitronToolMenuHandler() {
    }

    @SubscribeEvent
    public static void onKeyInput(final InputEvent.Key event) {
        if (cooldown > 0 || event.getAction() != InputConstants.PRESS) {
            return;
        }
        if (!AllKeys.TOOL_MENU.doesModifierAndCodeMatch(event.getKey())) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || minecraft.gameMode == null
                || minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR) {
            return;
        }
        if (!holdingGravitron()) {
            return;
        }
        minecraft.setScreen(new GravitronToolMenu(GravitronTools.get(minecraft.player)));
    }

    @SubscribeEvent
    public static void onClientTick(final ClientTickEvent.Post event) {
        if (cooldown > 0) {
            cooldown--;
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(final ClientPlayerNetworkEvent.LoggingOut event) {
        // 退出世界时把客户端镜像清回默认，避免下次进服与服务端状态不一致
        GravitronTools.clear(event.getPlayer());
        cooldown = 0;
    }

    /** 主手是不是重力枪（旧版 {@code findGravitronInHand} 的等价判据）。 */
    static boolean holdingGravitron() {
        final LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        final ItemStack stack = player.getMainHandItem();
        return stack.getItem() instanceof GravitronItem;
    }

    /** 选中模式：客户端存一份镜像供菜单/渲染用，同时发给服务端（服务端才是右键判定的权威）。 */
    static void select(final GravitronTools tool) {
        final LocalPlayer player = Minecraft.getInstance().player;
        GravitronTools.set(player, tool);
        PacketDistributor.sendToServer(new GravitronToolPacket(tool.ordinal()));
    }

    static void onMenuClosed() {
        cooldown = REOPEN_COOLDOWN;
    }
}
