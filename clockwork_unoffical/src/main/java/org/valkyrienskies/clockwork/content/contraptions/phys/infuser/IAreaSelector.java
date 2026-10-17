package org.valkyrienskies.clockwork.content.contraptions.phys.infuser;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * "能提供一块选定区域"的物品接口。
 *
 * <p>旧版物理灌注器把 {@code WanderwandItem} 收进自己的容器槽，再从物品 NBT 里读
 * {@code selectedBlocks}（见旧版 {@code PhysicsInfuserBlockEntity#assemble}）。
 * 移植后游荡权杖属于"奇物工具"切片，物理切片不应该反向依赖它，
 * 因此在这里定义一个最小契约：谁的物品实现它，就能被灌注器识别。
 *
 * <p><b>需要使用方配合</b>：{@code content/curiosities/tools/wanderwand/WanderwandItem}
 * 只需实现本接口并返回当前选中的方块集合（可以为 null 表示未选择）。
 */
public interface IAreaSelector {

    /**
     * @param stack 玩家手里的物品
     * @param level 当前世界
     * @return 选中的方块集合；未选择时返回 null 或空集合
     */
    @Nullable
    Collection<BlockPos> cw$getSelectedBlocks(ItemStack stack, Level level);
}
