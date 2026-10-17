package org.valkyrienskies.clockwork.content.contraptions.flap;

import com.simibubi.create.foundation.block.IBE;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.valkyrienskies.clockwork.content.contraptions.CWContraptionsIndex;

/**
 * 安山襟翼轴承（旧版 {@code andesite_flap_bearing}）。
 *
 * <p>行为全部来自 {@link FlapBearingBlock} + {@link FlapBearingBlockEntity}，
 * 这个类只负责绑定方块实体类型。
 */
public class AndesiteFlapBearingBlock extends FlapBearingBlock implements IBE<FlapBearingBlockEntity> {

    public AndesiteFlapBearingBlock(final Properties properties) {
        super(properties);
    }

    @Override
    public Class<FlapBearingBlockEntity> getBlockEntityClass() {
        return FlapBearingBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends FlapBearingBlockEntity> getBlockEntityType() {
        return CWContraptionsIndex.FLAP_BEARING_BE.get();
    }
}
