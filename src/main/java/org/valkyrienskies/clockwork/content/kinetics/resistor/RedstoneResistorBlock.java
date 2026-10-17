package org.valkyrienskies.clockwork.content.kinetics.resistor;

import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.AbstractEncasedShaftBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.clockwork.content.kinetics.CWKineticsIndex;

/**
 * 红石电阻：转速随红石信号线性衰减的传动轴（封装外壳）。
 *
 * <p>对应旧版 {@code content.kinetics.resistor.RedstoneResistorBlock}。
 *
 * <p>工作方式：方块实体每刻把红石强度写进自己的 {@code signal} 字段，
 * 强度一变就 {@code detachKinetics()} 断开动力学网络，并给方块排一个 0 tick 的调度刻；
 * 本类的 {@link #tick} 再把网络重新接上（{@link RotationPropagator#handleAdded}），
 * 这样 {@code getRotationSpeedModifier} 的新倍率立刻生效。这条链路与 1.20.1 完全一致。
 */
public class RedstoneResistorBlock extends AbstractEncasedShaftBlock implements IBE<RedstoneResistorBlockEntity> {

    public RedstoneResistorBlock(Properties properties) {
        super(properties);
    }

    /** 被 {@link RedstoneResistorBlockEntity#detachKinetics()} 排队的重连刻。 */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof KineticBlockEntity kbe) {
            RotationPropagator.handleAdded(level, pos, kbe);
        }
    }

    @Override
    public Class<RedstoneResistorBlockEntity> getBlockEntityClass() {
        return RedstoneResistorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RedstoneResistorBlockEntity> getBlockEntityType() {
        return CWKineticsIndex.REDSTONE_RESISTOR_BE.get();
    }
}
