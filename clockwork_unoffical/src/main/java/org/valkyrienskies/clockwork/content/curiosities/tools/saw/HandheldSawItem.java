package org.valkyrienskies.clockwork.content.curiosities.tools.saw;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 手持切割锯（Buzzsaw）。
 *
 * <p>对应旧版 {@code tools/saw/HandheldMechanicalSaw.kt}：斧子 + "砍一格锯倒整棵树"。
 *
 * <p>移植差异：
 * <ul>
 *   <li>旧版用 Create 的 {@code TreeCutter.findTree / findDynamicTree} 递归砍掉整棵树（含树叶与
 *       "动态树"逻辑）；本切片只沿树干柱状连续砍同种方块（最多 {@link #MAX_FELL_HEIGHT} 格），
 *       不掉树叶。差异原因：{@code TreeCutter} 的行为与 Create 的锯片方块强耦合，
 *       照抄会把 Create 的自动化逻辑带进手持工具，收益不匹配风险。</li>
 *   <li>旧版靠 {@code BacktankUtil} 用气罐抵扣耐久，本切片不做（气体物流切片负责）。</li>
 *   <li><b>触发点从 {@code Item#mineBlock} 换成服务端 {@code BlockEvent.BreakEvent}</b>：
 *       1.21.1 的 {@code ServerPlayerGameMode#destroyBlock}:263 在创造模式下
 *       {@code removeBlock(...)} 后直接 return，{@code ItemStack#mineBlock} 只在生存分支 :270 被调用，
 *       只写 {@code mineBlock} 的话创造模式下锯树完全不生效。详见 {@link SawBreakHandler}。</li>
 * </ul>
 */
public class HandheldSawItem extends AxeItem {

    /** 一次最多锯多少格，防止巨型树把区块改烂。 */
    private static final int MAX_FELL_HEIGHT = 48;

    public HandheldSawItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    /** 对应旧版 {@code DISABLE_SWING_ANIMATION} 标签（旧版靠 mixin，这里用物品钩子）。 */
    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity, InteractionHand hand) {
        return true;
    }

    /**
     * 从被砍的那一格往上连续砍同种方块（树干）。
     *
     * <p>由 {@link SawBreakHandler} 在 {@code BlockEvent.BreakEvent} 里调用，
     * 因此创造模式与生存模式都会生效。
     */
    public void cw$fellTrunk(Level level, BlockPos pos, BlockState felled, Player player) {
        ItemStack stack = player.getMainHandItem();
        for (int i = 1; i <= MAX_FELL_HEIGHT; i++) {
            BlockPos above = pos.above(i);
            BlockState state = level.getBlockState(above);
            if (state.getBlock() != felled.getBlock()) {
                break;
            }
            if (level.destroyBlock(above, true, player, 512)) {
                // 附带锯掉的格子也要吃耐久，否则一次挥砍白拿一整棵树；创造模式由原版自行忽略损耗。
                stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            }
        }
    }
}
