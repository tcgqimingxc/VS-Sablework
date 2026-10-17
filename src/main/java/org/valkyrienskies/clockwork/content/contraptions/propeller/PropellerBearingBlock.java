package org.valkyrienskies.clockwork.content.contraptions.propeller;

import com.simibubi.create.foundation.block.IBE;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.valkyrienskies.clockwork.content.contraptions.CWContraptionsIndex;
import org.valkyrienskies.clockwork.content.contraptions.phys.bearing.AbstractBearingBlock;

/**
 * 螺旋桨轴承方块。
 *
 * <p>对应旧版 {@code content/contraptions/propeller/PropellerBearingBlock}。
 * 玩法和物理轴承一样（空手右键装配/解体），区别只在方块实体的受力模型：
 * 螺旋桨会把"叶素升力"沿朝向方向施加到母体上（见 {@link PropellerBearingBlockEntity}）。
 */
public class PropellerBearingBlock extends AbstractBearingBlock implements IBE<PropellerBearingBlockEntity> {

    private final PropellerKind kind;

    public PropellerBearingBlock(final Properties properties, final PropellerKind kind) {
        super(properties);
        this.kind = kind;
    }

    public PropellerKind getKind() {
        return this.kind;
    }

    @Override
    public Class<PropellerBearingBlockEntity> getBlockEntityClass() {
        return PropellerBearingBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends PropellerBearingBlockEntity> getBlockEntityType() {
        return CWContraptionsIndex.PROPELLER_BEARING_BE.get();
    }
}
