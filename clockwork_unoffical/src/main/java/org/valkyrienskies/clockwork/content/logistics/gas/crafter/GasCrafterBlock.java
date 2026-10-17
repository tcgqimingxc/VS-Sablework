package org.valkyrienskies.clockwork.content.logistics.gas.crafter;

import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.valkyrienskies.clockwork.content.logistics.gas.CWGasIndex;
import org.valkyrienskies.clockwork.content.logistics.gas.IGasNodeBlock;

/**
 * 气体合成器：贴在 Create 工作盆上方，用「物品 + 气体 + 温度」执行气体合成配方。
 *
 * <p>对应旧版 {@code GasCrafterBlock}。资源只用到 {@code facing}（含 up/down 共 6 个朝向，
 * 见 {@code blockstates/gas_crafter.json}），因此这里用带 6 向 {@code FACING} 的方块。
 *
 * <p>与旧版的差异：旧版要求"必须贴在工作盆上方"（{@code worldPosition.below(1)} 是
 * {@code BasinBlockEntity}）；本移植保留这一判定，只是把"找盆"改成通过方块实体查询而不是
 * Create 的 {@code BasinOperatingBlockEntity} 体系（本模组没有搅拌器/压床那套）。
 */
public class GasCrafterBlock extends DirectionalBlock implements IBE<GasCrafterBlockEntity>, IGasNodeBlock {

    /** 6 向朝向（旧版资源里 facing 有 up/down）。 */
    public static final DirectionProperty FACING = DirectionalBlock.FACING;

    private static final VoxelShape SHAPE = Shapes.box(0.0, 0.5, 0.0, 1.0, 1.0, 1.0);

    public GasCrafterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP));
    }

    /** 1.21 起 {@code Block} 要求自带 codec（{@code BlockBehaviour.simpleCodec} 是标准写法）。 */
    @Override
    protected com.mojang.serialization.MapCodec<? extends DirectionalBlock> codec() {
        return BlockBehaviour.simpleCodec(GasCrafterBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 旧版用玩家看向的方向（最近的视线方向）：贴盆时自然朝下
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public Class<GasCrafterBlockEntity> getBlockEntityClass() {
        return GasCrafterBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends GasCrafterBlockEntity> getBlockEntityType() {
        return CWGasIndex.GAS_CRAFTER_BE.get();
    }
}
