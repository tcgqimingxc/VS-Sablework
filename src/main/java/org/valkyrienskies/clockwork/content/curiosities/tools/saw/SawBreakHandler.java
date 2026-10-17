package org.valkyrienskies.clockwork.content.curiosities.tools.saw;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 手持切割锯的"锯倒树干"触发点。
 *
 * <p>与 {@code DrillBreakHandler} 同一个原因：1.21.1 的 {@code ServerPlayerGameMode#destroyBlock}
 * 在创造模式（:263-265）不调用 {@code ItemStack#mineBlock}，只有生存分支（:270）才调用；
 * 而 {@code BlockEvent.BreakEvent} 由 :249 在两条分支之前统一触发，创造/生存都覆盖。
 *
 * <p>按 PORT-CONVENTIONS，切片不改 {@code index/} 与主类，所以用 {@link EventBusSubscriber} 自动挂载。
 */
@EventBusSubscriber(modid = ClockworkMod.MOD_ID)
public final class SawBreakHandler {

    private SawBreakHandler() {
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        // 别的 mod / 保护区取消了这次破坏时不要跟着连锁砍（NeoForge 的
        // CommonHooks#fireBlockBreak 除了事件取消，还会因为 canAttackBlock=false
        // 或保护区限制把事件预先标成 canceled）。
        if (event.isCanceled()) {
            return;
        }
        Player player = event.getPlayer();
        // 潜行 = 只锯一格（与旧版一致）
        if (player == null || player.isShiftKeyDown()) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof HandheldSawItem saw)) {
            return;
        }
        // 只有"斧子砍得动"的方块才连锁（树叶/泥土等不触发）
        if (!stack.isCorrectToolForDrops(event.getState())) {
            return;
        }
        if (!(event.getLevel() instanceof Level level) || level.isClientSide) {
            return;
        }
        saw.cw$fellTrunk(level, event.getPos(), event.getState(), player);
    }
}
