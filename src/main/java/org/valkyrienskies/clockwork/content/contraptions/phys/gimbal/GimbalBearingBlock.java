package org.valkyrienskies.clockwork.content.contraptions.phys.gimbal;

import com.simibubi.create.foundation.block.IBE;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.valkyrienskies.clockwork.content.contraptions.CWContraptionsIndex;
import org.valkyrienskies.clockwork.content.contraptions.phys.bearing.AbstractBearingBlock;

/**
 * 万向轴承（旧版 {@code content/contraptions/phys/gimbal/GimbalBearingBlock}）。
 *
 * <p>和物理轴承的区别在约束类型：物理轴承只有一个转动自由度，
 * 万向轴承锁住平移、放开三个转动自由度（等价于旧版 {@code VSSphericalJoint}），
 * 于是挂在它上面的子结构可以像喷口/云台一样被自由摆布。
 */
public class GimbalBearingBlock extends AbstractBearingBlock implements IBE<GimbalBearingBlockEntity> {

    public GimbalBearingBlock(final Properties properties) {
        super(properties);
    }

    @Override
    public Class<GimbalBearingBlockEntity> getBlockEntityClass() {
        return GimbalBearingBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends GimbalBearingBlockEntity> getBlockEntityType() {
        return CWContraptionsIndex.GIMBAL_BEARING_BE.get();
    }
}
