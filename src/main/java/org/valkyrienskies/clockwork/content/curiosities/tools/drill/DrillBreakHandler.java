package org.valkyrienskies.clockwork.content.curiosities.tools.drill;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 手持动力钻的 3×3 断面触发点。
 *
 * <p><b>为什么不用 {@code Item#mineBlock}</b>：1.21.1 的
 * {@code ServerPlayerGameMode#destroyBlock} 里，创造模式在 :263-265 直接
 * {@code removeBlock(...)} 并 return，{@code ItemStack#mineBlock} 只在生存分支 :270 才被调用。
 * 只挂 {@code mineBlock} 会导致"创造模式下钻头完全没反应"。
 * {@code BlockEvent.BreakEvent} 由同一个方法的 :249 在两条分支之前触发（NeoForge 的
 * {@code CommonHooks#fireBlockBreak}），创造/生存都会走到，因此选它作为唯一触发点。
 *
 * <p>按 PORT-CONVENTIONS，切片不改 {@code index/} 与主类，所以用 {@link EventBusSubscriber}
 * 自动挂到游戏事件总线上（与 {@code CWKineticsModBus} 同一套做法）。
 */
@EventBusSubscriber(modid = ClockworkMod.MOD_ID)
public final class DrillBreakHandler {

    private DrillBreakHandler() {
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        // 别的 mod / 保护区取消了这次破坏时不要跟着连锁挖（NeoForge 的
        // CommonHooks#fireBlockBreak 除了事件取消，还会因为 canAttackBlock=false
        // 或保护区限制把事件预先标成 canceled）。
        if (event.isCanceled()) {
            return;
        }
        Player player = event.getPlayer();
        // 潜行 = 只挖一格（与旧版 mineTunnel 的"潜行关闭范围挖掘"一致）
        if (player == null || player.isShiftKeyDown()) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof HandheldDrillItem drill)) {
            return;
        }
        if (!(event.getLevel() instanceof Level level) || level.isClientSide) {
            return;
        }
        drill.cw$minePlane(level, event.getPos(), player);
    }
}
