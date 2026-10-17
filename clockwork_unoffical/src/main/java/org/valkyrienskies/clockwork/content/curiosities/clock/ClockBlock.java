package org.valkyrienskies.clockwork.content.curiosities.clock;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllShapes;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.valkyrienskies.clockwork.content.curiosities.CWInstrumentsIndex;

/**
 * 落地钟（Clock）。
 *
 * <p>对应旧版 {@code curiosities/clock/ClockBlock.kt}：一个挂墙的时钟装饰方块，
 * 指针按世界时间走动并伴随滴答声。
 *
 * <p>移植差异：旧版用 {@code HorizontalKineticBlock.HORIZONTAL_FACING}（与
 * {@link BlockStateProperties#HORIZONTAL_FACING} 是同一个属性），这里直接用原版属性，
 * 资源文件里的 {@code facing=north/south/east/west} 变体完全对得上。
 */
public class ClockBlock extends HorizontalDirectionalBlock implements IBE<ClockBlockEntity> {

    public static final MapCodec<ClockBlock> CODEC = simpleCodec(ClockBlock::new);

    public ClockBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // 旧版用的是 Create 的 AllShapes.PLACARD。
        return AllShapes.PLACARD.get(state.getValue(FACING));
    }

    @Override
    public Class<ClockBlockEntity> getBlockEntityClass() {
        return ClockBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ClockBlockEntity> getBlockEntityType() {
        return CWInstrumentsIndex.CLOCK_BE.get();
    }
}
