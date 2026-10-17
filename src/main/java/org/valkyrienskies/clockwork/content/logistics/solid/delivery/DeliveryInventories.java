package org.valkyrienskies.clockwork.content.logistics.solid.delivery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 交付方块与"下方储物方块"之间的物品能力查询小工具。
 *
 * <p>对应旧版 {@code SolidDeliveryMethodsImpl.grabCapability}（Forge 时代取
 * {@code be.blockPos.relative(DOWN)} 的 {@code ITEM_HANDLER}）。放在这里是为了让
 * 加农炮（抽物品）与滑道（推物品）共用同一段代码，避免两边各写一遍。
 */
public final class DeliveryInventories {

    private DeliveryInventories() {
    }

    /**
     * 取 {@code pos} 下方方块的物品能力（滑道 / 置物台 / 传送带等）。
     *
     * <p>先按"从上方插入"（{@code side = UP}）查询，失败再退回不区分面的查询 ——
     * Create 的方块按面注册能力，个别实现只注册了无面版本。
     */
    @Nullable
    public static IItemHandler below(Level level, BlockPos pos) {
        final BlockPos below = pos.below();
        final IItemHandler sided = level.getCapability(Capabilities.ItemHandler.BLOCK, below, Direction.UP);
        return sided != null ? sided : level.getCapability(Capabilities.ItemHandler.BLOCK, below, (Direction) null);
    }
}
