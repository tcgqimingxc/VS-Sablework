package org.valkyrienskies.clockwork.content.curiosities.tools.drill;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 手持动力钻（Power Drill）。
 *
 * <p>对应旧版 {@code tools/drill/HandheldMechanicalDrill.kt}：镐子 + "挖一格带出一片"。
 *
 * <p>移植差异：
 * <ul>
 *   <li>旧版是 {@code PickaxeItem(tier, attackBonus, attackSpeedBonus, properties)}；
 *       1.21.1 的攻击属性改由 {@code DiggerItem.createAttributes} 写进物品属性组件
 *       （见 {@code CWToolsIndex} 的注册），所以这里的构造器只收 {@code (Tier, Properties)}。</li>
 *   <li>旧版 {@code mineTunnel} 会沿视线挖一条 3×3×N 的长隧道（可配置直径/深度）；
 *       这里保留 3×3 断面但**深度只有 1 层**：长隧道太容易把玩家的建筑挖穿，
 *       而且旧版本质上也是"挖一格带一片"。差异仅此一处。</li>
 *   <li>旧版用 Create 的 {@code BacktankUtil} 让气罐吸收耐久消耗；那属于气体物流切片，
 *       本切片不做（每挖一格正常消耗 1 点耐久）。</li>
 *   <li><b>触发点从 {@code Item#mineBlock} 换成服务端 {@code BlockEvent.BreakEvent}</b>：
 *       1.21.1 的 {@code ServerPlayerGameMode#destroyBlock}:263 在**创造模式**下
 *       {@code removeBlock(...)} 后直接 return，{@code ItemStack#mineBlock} 只在生存模式（:270）被调用。
 *       也就是说只写 {@code mineBlock} 的话，创造模式下 3×3 完全不生效（实机反馈"工具没反应"即此）。
 *       {@code BreakEvent} 由 {@code destroyBlock}:249 在创造/生存两条路径之前统一触发，见
 *       {@link DrillBreakHandler}。</li>
 * </ul>
 */
public class HandheldDrillItem extends PickaxeItem {

    /** 断面半径：1 表示 3×3。 */
    private static final int PLANE_RADIUS = 1;

    public HandheldDrillItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    /**
     * 旧版旧行为：镐 + 铲都算"正确工具"，因此钻头能挖土。
     *
     * <p>注意 1.21.1 的挖掘行为已经完全由 {@code DataComponents.TOOL} 组件驱动
     * （{@code Item#getDestroySpeed}/{@code Item#isCorrectToolForDrops} 都只读该组件，
     * {@code DiggerItem} 也不再重写它们，构造时用 {@code tier.createToolProperties(MINEABLE_WITH_PICKAXE)} 写入）。
     * 只重写本方法能让"能不能掉落"变对，但**挖掘速度**仍只认镐子标签，铲类方块会慢得像空手，
     * 所以 {@link #getDestroySpeed} 也必须一起重写。
     */
    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return state.is(BlockTags.MINEABLE_WITH_PICKAXE)
                || state.is(BlockTags.MINEABLE_WITH_SHOVEL)
                || super.isCorrectToolForDrops(stack, state);
    }

    /** 与 {@link #isCorrectToolForDrops} 保持一致：镐/铲类方块都按下界合金速度挖。 */
    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return isCorrectToolForDrops(stack, state) ? getTier().getSpeed() : super.getDestroySpeed(stack, state);
    }

    /** 对应旧版 {@code DISABLE_SWING_ANIMATION} 标签（旧版靠 mixin，这里用物品钩子）。 */
    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity, InteractionHand hand) {
        return true;
    }

    /**
     * 挖中心格后带出与视线垂直的 3×3 断面（{@code PLANE_RADIUS = 1}）。
     *
     * <p>由 {@link DrillBreakHandler} 在 {@code BlockEvent.BreakEvent} 里调用，
     * 因此创造模式与生存模式都会生效。
     */
    public void cw$minePlane(Level level, BlockPos center, Player player) {
        ItemStack stack = player.getMainHandItem();
        Direction.Axis axis = player.getDirection().getAxis();
        for (int a = -PLANE_RADIUS; a <= PLANE_RADIUS; a++) {
            for (int b = -PLANE_RADIUS; b <= PLANE_RADIUS; b++) {
                if (a == 0 && b == 0) {
                    continue; // 中心那格已经被原版流程挖掉了
                }
                BlockPos target = switch (axis) {
                    case Y -> center.offset(a, 0, b);
                    case Z -> center.offset(a, b, 0);
                    default -> center.offset(0, a, b);
                };
                breakBlockIfMineable(stack, level, target, player);
            }
        }
    }

    /** 只挖"这把钻挖得动"的方块；基岩等不可破坏方块（默认硬度 < 0）直接跳过。 */
    private static void breakBlockIfMineable(ItemStack stack, Level level, BlockPos pos, @Nullable Player player) {
        BlockState target = level.getBlockState(pos);
        if (target.isAir() || !stack.isCorrectToolForDrops(target) || target.getBlock().defaultDestroyTime() < 0) {
            return;
        }
        if (level.destroyBlock(pos, true, player, 512) && player != null) {
            // 附带挖掉的格子也要吃耐久，否则一次右键白拿 8 格；创造模式由原版自行忽略损耗。
            stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }
    }
}
