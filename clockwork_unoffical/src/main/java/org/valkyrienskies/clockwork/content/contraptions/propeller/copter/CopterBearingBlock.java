package org.valkyrienskies.clockwork.content.contraptions.propeller.copter;

import com.simibubi.create.foundation.block.IBE;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.valkyrienskies.clockwork.content.contraptions.CWContraptionsIndex;
import org.valkyrienskies.clockwork.content.contraptions.phys.bearing.AbstractBearingBlock;

/**
 * 直升机旋翼轴承（旧版 {@code content/contraptions/propeller/copter/CopterBearingBlock}）。
 *
 * <p>玩法和螺旋桨轴承完全相同（共用装配/约束/施力链路），
 * 只是气动规格换成"多叶、低桨距、高转速"的 {@code PropellerKind.COPTER}。
 * 因为 {@code IBE<T>} 的泛型不能协变，这里没法直接继承 {@code PropellerBearingBlock}，
 * 于是改为继承公共方块基类，只多写三行。
 */
public class CopterBearingBlock extends AbstractBearingBlock implements IBE<CopterBearingBlockEntity> {

    public CopterBearingBlock(final Properties properties) {
        super(properties);
    }

    @Override
    public Class<CopterBearingBlockEntity> getBlockEntityClass() {
        return CopterBearingBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CopterBearingBlockEntity> getBlockEntityType() {
        return CWContraptionsIndex.COPTER_BEARING_BE.get();
    }
}
