package org.valkyrienskies.clockwork.content.logistics.gas.valve;

import com.simibubi.create.AllShapes;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.valkyrienskies.clockwork.content.logistics.gas.CWGasIndex;
import org.valkyrienskies.clockwork.content.logistics.gas.IGasNodeBlock;

/**
 * 气体阀门：按旋转开度节流。
 *
 * <p>对应旧版 {@code ValveDuctBlock}（{@code DirectionalAxisKineticBlock} + 旧版 {@code getDuctAxis}）。
 * 方块状态用 Create 现成的 {@code facing} + {@code axis_along_first}，与资源里的方块状态 JSON 完全对应。
 *
 * <p>导管轴（"气体从哪两个面流过"）与朝向轴不同：{@code axis_along_first} 决定在剩余两个轴里取哪一个，
 * 这里照抄旧版 {@code getDuctAxis} 的取值顺序（X→Y→Z），保证与资源里的模型旋转变换一致。
 */
public class ValveDuctBlock extends DirectionalAxisKineticBlock implements IBE<ValveDuctBlockEntity>, IGasNodeBlock {

    public ValveDuctBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean gasConnectable(BlockState state, Direction dir) {
        return dir.getAxis() == getDuctAxis(state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return AllShapes.FLUID_VALVE.get(getDuctAxis(state));
    }

    @Override
    public Class<ValveDuctBlockEntity> getBlockEntityClass() {
        return ValveDuctBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ValveDuctBlockEntity> getBlockEntityType() {
        return CWGasIndex.VALVE_DUCT_BE.get();
    }

    /** 气体流经的轴（对应旧版 {@code ValveDuctBlock.getDuctAxis}）。 */
    public static Direction.Axis getDuctAxis(BlockState state) {
        Direction facing = state.getValue(FACING);
        boolean alongFirst = !state.getValue(AXIS_ALONG_FIRST_COORDINATE);
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (axis == facing.getAxis()) {
                continue;
            }
            if (!alongFirst) {
                alongFirst = true;
                continue;
            }
            return axis;
        }
        throw new IllegalStateException("Valve duct axis resolution failed");
    }
}
