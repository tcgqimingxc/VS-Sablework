package org.valkyrienskies.clockwork.content.contraptions.flap;

import com.simibubi.create.foundation.block.IBE;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.valkyrienskies.clockwork.content.contraptions.CWContraptionsIndex;

/**
 * 智能襟翼轴承（旧版 {@code content/contraptions/flap/smart_flap/SmartFlapBearingBlock}）。
 *
 * <p>方块与资源名沿用旧版；行为差异见 {@link SmartFlapBearingBlockEntity}。
 */
public class SmartFlapBearingBlock extends FlapBearingBlock implements IBE<SmartFlapBearingBlockEntity> {

    public SmartFlapBearingBlock(final Properties properties) {
        super(properties);
    }

    @Override
    public Class<SmartFlapBearingBlockEntity> getBlockEntityClass() {
        return SmartFlapBearingBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SmartFlapBearingBlockEntity> getBlockEntityType() {
        return CWContraptionsIndex.SMART_FLAP_BEARING_BE.get();
    }
}
