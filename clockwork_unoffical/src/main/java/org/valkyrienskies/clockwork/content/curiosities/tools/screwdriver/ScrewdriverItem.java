package org.valkyrienskies.clockwork.content.curiosities.tools.screwdriver;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 螺丝刀（Screwdriver）。
 *
 * <p>对应旧版 {@code tools/screwdriver/ScrewdriverItem.kt}，行为完全一致：
 * 对方块做"精细调节"而不是扳手式的拆装。只有实现了 {@link IScrewdrivable} 的方块会响应，
 * 普通右键与潜行右键分别走两个钩子。
 *
 * <p>本切片里实现该接口的方块：三个传感器（循环射程 / 预测档位 / 参考轴）与后续切片里
 * 各种"多档位"机器。手持指令（{@code /give}）能直接看出它的作用。
 */
public class ScrewdriverItem extends Item {

    public ScrewdriverItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.mayBuild()) {
            return super.useOn(context);
        }
        BlockState state = context.getLevel().getBlockState(context.getClickedPos());
        if (!(state.getBlock() instanceof IScrewdrivable screwdrivable)) {
            return super.useOn(context);
        }
        return player.isShiftKeyDown()
                ? screwdrivable.onSneakScrewdrived(state, context)
                : screwdrivable.onScrewdrived(state, context);
    }
}
