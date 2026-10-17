package org.valkyrienskies.clockwork.content.logistics.gas.heater;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.valkyrienskies.clockwork.content.logistics.gas.CWGasIndex;
import org.valkyrienskies.clockwork.content.logistics.gas.IGasNodeBlock;

/**
 * 气体加热器：把网络里的热气体变成 Create 的热源。
 *
 * <p>对应旧版 {@code GasHeaterBlock}：{@code HorizontalDirectionalBlock} + {@code facing} +
 * {@code BlazeBurnerBlock.HEAT_LEVEL}。因为用的就是 Create 的 {@code heat_level} 属性，
 * 所以锅炉 / 盆会像识别烈焰人燃烧室一样识别它（Create 用
 * {@code BlazeBurnerBlock.getHeatLevelOf(state)} 读这个属性），无需额外适配。
 *
 * <p>连接规则与旧版一致：朝向轴两侧 + 底面参与气体连接。
 */
public class GasHeaterBlock extends HorizontalDirectionalBlock
        implements IBE<GasHeaterBlockEntity>, IGasNodeBlock {

    public static final MapCodec<GasHeaterBlock> CODEC = simpleCodec(GasHeaterBlock::new);

    public GasHeaterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(BlazeBurnerBlock.HEAT_LEVEL, HeatLevel.NONE));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BlazeBurnerBlock.HEAT_LEVEL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public boolean gasConnectable(BlockState state, Direction dir) {
        return dir.getAxis() == state.getValue(FACING).getAxis() || dir == Direction.DOWN;
    }

    @Override
    public Class<GasHeaterBlockEntity> getBlockEntityClass() {
        return GasHeaterBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends GasHeaterBlockEntity> getBlockEntityType() {
        return CWGasIndex.GAS_HEATER_BE.get();
    }

    /** 掉到热源上会被烫伤（沿用旧版 {@code updateEntityAfterFallOn}：每超过 350K 25K 造成半颗心）。 */
    @Override
    public void updateEntityAfterFallOn(BlockGetter level, Entity entity) {
        super.updateEntityAfterFallOn(level, entity);
        if (level.getBlockEntity(entity.blockPosition().below()) instanceof GasHeaterBlockEntity heater) {
            heater.burnEntity(entity);
        }
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        // 旧版行为：放置时通知上方的盆重新判断热源
        if (!level.isClientSide
                && level.getBlockEntity(pos.above()) instanceof BasinBlockEntity basin) {
            basin.notifyChangeOfContents();
        }
    }
}
