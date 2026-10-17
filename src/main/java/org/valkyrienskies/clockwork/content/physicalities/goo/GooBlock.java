package org.valkyrienskies.clockwork.content.physicalities.goo;

import com.simibubi.create.foundation.block.IBE;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.valkyrienskies.clockwork.content.kinetics.CWKineticsIndex;

/**
 * 黏液块（Goo）：蜂蜜块手感的建筑方块，时钟机构用来做缓冲 / 粘附面。
 *
 * <p>对应旧版 {@code content.physicalities.goo.GooBlock}，行为与旧版完全一致：
 * 方块属性直接继承蜂蜜块（{@code Blocks.HONEY_BLOCK}），自身不额外覆写任何逻辑，
 * 只挂一个空方块实体给渲染 / 后续扩展留位置。
 *
 * <p>旧版还有一个 {@code GooBlockEntityRenderer}，但它的 {@code renderSafe} 只调用了
 * {@code super.renderSafe(...)}（纯空实现），所以本移植不注册渲染器，省掉一个无用的 BER。
 */
public class GooBlock extends Block implements IBE<GooBlockEntity> {

    public GooBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Class<GooBlockEntity> getBlockEntityClass() {
        return GooBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends GooBlockEntity> getBlockEntityType() {
        return CWKineticsIndex.GOO_BLOCK_BE.get();
    }
}
