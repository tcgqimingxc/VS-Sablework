package org.valkyrienskies.clockwork.content.logistics.gas.compressor;

import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.clockwork.content.logistics.gas.CWGasIndex;
import org.valkyrienskies.clockwork.content.logistics.gas.IGasNodeBlock;

/**
 * 空气压缩机：从上方吸气，把空气（高空还混有以太）压进网络。
 *
 * <p>对应旧版 {@code AirCompressorBlock}：竖直轴输入动力、轴在底面、上方不能有方块（"堵塞"）。
 * 与旧版差异：旧版靠 {@code getProductionStats()} 给 JEI 提供"产气说明"，本切片未接入 JEI。
 */
public class AirCompressorBlock extends KineticBlock implements IBE<AirCompressorBlockEntity>, IGasNodeBlock {

    public AirCompressorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return Direction.Axis.Y;
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face == Direction.DOWN;
    }

    @Override
    public Class<AirCompressorBlockEntity> getBlockEntityClass() {
        return AirCompressorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends AirCompressorBlockEntity> getBlockEntityType() {
        return CWGasIndex.AIR_COMPRESSOR_BE.get();
    }
}
