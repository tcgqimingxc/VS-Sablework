package org.valkyrienskies.clockwork.content.logistics.gas.nozzle;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.valkyrienskies.clockwork.content.logistics.gas.CWGasIndex;
import org.valkyrienskies.clockwork.content.logistics.gas.IGasNodeBlock;

/**
 * 气嘴：沿 {@code FACING} 喷出气体，反作用力推动所在子空间。
 *
 * <p>对应旧版 {@code content/logistics/gas/pockets/nozzle/GasNozzleBlock}（Kotlin，
 * {@code HorizontalKineticBlock} + {@code INodeBlock}）。旧版依赖 VS2 的气囊体系，本实现是重新设计
 * （详见 {@link GasNozzleBlockEntity} 类头），因此这里也不再是动力方块：
 * <ul>
 *   <li>旧版用 {@code HORIZONTAL_FACING.clockWise.axis} 作为输入轴，转速决定喷气开度；
 *       本实现没有输入轴，进气只在背面（{@link #gasConnectable}），气压决定喷气量。</li>
 *   <li>放置朝向沿用旧版 {@code HorizontalKineticBlock} 的规则：{@code FACING} = 玩家视线的反方向
 *       （旧版资源模型 {@code block/gas_nozzle/block} 就是按这个朝向做的）。</li>
 * </ul>
 */
public class GasNozzleBlock extends HorizontalDirectionalBlock implements IBE<GasNozzleBlockEntity>, IGasNodeBlock {

    public static final MapCodec<GasNozzleBlock> CODEC = simpleCodec(GasNozzleBlock::new);

    public GasNozzleBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** 只有背面接气：喷口朝向的一面不参与导管网络。 */
    @Override
    public boolean gasConnectable(BlockState state, Direction dir) {
        return dir == state.getValue(FACING).getOpposite();
    }

    @Override
    public Class<GasNozzleBlockEntity> getBlockEntityClass() {
        return GasNozzleBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends GasNozzleBlockEntity> getBlockEntityType() {
        return CWGasIndex.GAS_NOZZLE_BE.get();
    }
}
