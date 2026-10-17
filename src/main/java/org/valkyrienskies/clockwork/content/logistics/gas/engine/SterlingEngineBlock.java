package org.valkyrienskies.clockwork.content.logistics.gas.engine;

import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.clockwork.content.logistics.gas.CWGasIndex;
import org.valkyrienskies.clockwork.content.logistics.gas.IGasNodeBlock;

/**
 * 斯特林机（热气机）：把网络里的热能直接变成旋转动力。
 *
 * <p>对应旧版 {@code SterlingEngineBlock}（{@code DirectionalKineticBlock} + {@code ICogWheel}）。
 * 资源 JSON 只有 {@code facing}，与 {@code DirectionalKineticBlock} 的方块状态一致。
 *
 * <p>连接规则：只在朝向轴上连通（旧版通过 {@code IConnectable} 只给正面/背面建边）。
 */
public class SterlingEngineBlock extends DirectionalKineticBlock
        implements IBE<SterlingEngineBlockEntity>, ICogWheel, IGasNodeBlock {

    public SterlingEngineBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING);
    }

    @Override
    public boolean gasConnectable(BlockState state, Direction dir) {
        return dir.getAxis() == state.getValue(FACING).getAxis();
    }

    @Override
    public Class<SterlingEngineBlockEntity> getBlockEntityClass() {
        return SterlingEngineBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SterlingEngineBlockEntity> getBlockEntityType() {
        return CWGasIndex.STERLING_ENGINE_BE.get();
    }
}
