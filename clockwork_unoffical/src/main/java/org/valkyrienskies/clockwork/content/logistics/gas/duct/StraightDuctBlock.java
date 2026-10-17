package org.valkyrienskies.clockwork.content.logistics.gas.duct;

import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.valkyrienskies.clockwork.content.logistics.gas.CWGasIndex;
import org.valkyrienskies.clockwork.content.logistics.gas.IGasNodeBlock;

/**
 * 直管：只有两端连接的导管。
 *
 * <p><b>旧版与新仓库都没有这个方块的实现源码</b>（只有 {@code blockstates/straight_duct.json}
 * 与 {@code models/item/straight_duct.json}，模型指向 {@code block/heat_pipe/straight}）。
 * 本实现是按"直管"的字面语义自行设计的：<b>它只是"朝轴向两端连接的一根管"</b>，
 * 用来省掉普通导管的 6 面连接判断、也方便在狭窄空间里明确"只走这一个方向"。
 *
 * <p>设计要点：
 * <ul>
 *   <li>方块状态只有 {@code axis}（与资源 JSON 一致）。</li>
 *   <li>只有轴向两面连通（与燃气机/推进器的"轴向连通"规则一致）。</li>
 *   <li>算管道（{@code isGasPipe}），因此两台机器之间夹一根直管就能连通。</li>
 *   <li>容量与普通导管相同（1.0 倍额定）。</li>
 * </ul>
 */
public class StraightDuctBlock extends RotatedPillarBlock implements IBE<StraightDuctBlockEntity>, IGasNodeBlock {

    private static final VoxelShape CORE = box(4.0, 4.0, 4.0, 12.0, 12.0, 12.0);
    private static final VoxelShape ARM_X = box(0.0, 4.0, 4.0, 16.0, 12.0, 12.0);
    private static final VoxelShape ARM_Y = box(4.0, 0.0, 4.0, 12.0, 16.0, 12.0);
    private static final VoxelShape ARM_Z = box(4.0, 4.0, 0.0, 12.0, 12.0, 16.0);

    public StraightDuctBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, context.getNearestLookingDirection().getAxis());
    }

    @Override
    public boolean gasConnectable(BlockState state, Direction dir) {
        return dir.getAxis() == state.getValue(AXIS);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(AXIS)) {
            case X -> Shapes.or(CORE, ARM_X);
            case Y -> Shapes.or(CORE, ARM_Y);
            case Z -> Shapes.or(CORE, ARM_Z);
        };
    }

    @Override
    public Class<StraightDuctBlockEntity> getBlockEntityClass() {
        return StraightDuctBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends StraightDuctBlockEntity> getBlockEntityType() {
        return CWGasIndex.STRAIGHT_DUCT_BE.get();
    }
}
