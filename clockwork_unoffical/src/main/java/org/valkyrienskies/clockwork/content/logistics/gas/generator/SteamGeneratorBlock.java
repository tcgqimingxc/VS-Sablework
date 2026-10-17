package org.valkyrienskies.clockwork.content.logistics.gas.generator;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import org.valkyrienskies.clockwork.content.logistics.gas.CWGasIndex;
import org.valkyrienskies.clockwork.content.logistics.gas.IGasNodeBlock;

/**
 * 蒸汽提取器：贴在 Create 锅炉（流体储罐）上，把锅炉里的蒸汽抽进气体网络。
 *
 * <p>对应旧版 {@code SteamGeneratorBlock}（{@code FaceAttachedHorizontalDirectionalBlock}）。
 * 资源 JSON 用的是 {@code face} + {@code facing}，与此完全对应。
 *
 * <p>与旧版差异：旧版实现 {@code IDuct}（带 6 个连接属性）但并未把它们加进方块状态；
 * 这里只实现 {@link IGasNodeBlock}（默认全连通），语义不变。
 */
public class SteamGeneratorBlock extends FaceAttachedHorizontalDirectionalBlock
        implements IBE<SteamGeneratorBlockEntity>, IGasNodeBlock {

    public static final MapCodec<SteamGeneratorBlock> CODEC = simpleCodec(SteamGeneratorBlock::new);

    public SteamGeneratorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACE, AttachFace.FLOOR)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return canAttach(level, pos, getConnectedDirection(state).getOpposite());
    }

    @Override
    public Class<SteamGeneratorBlockEntity> getBlockEntityClass() {
        return SteamGeneratorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SteamGeneratorBlockEntity> getBlockEntityType() {
        return CWGasIndex.STEAM_GENERATOR_BE.get();
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        FluidTankBlock.updateBoilerState(state, level, pos.relative(getFacing(state).getOpposite()));
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.hasBlockEntity() && (!state.is(newState.getBlock()) || !newState.hasBlockEntity())) {
            level.removeBlockEntity(pos);
        }
        IBE.onRemove(state, level, pos, newState);
        super.onRemove(state, level, pos, newState, isMoving);
        FluidTankBlock.updateBoilerState(state, level, pos.relative(getFacing(state).getOpposite()));
    }

    /** 本方块"朝向"所指的方向（旧版直接借用 Create 蒸汽机的 {@code getFacing}）。 */
    public static Direction getFacing(BlockState state) {
        return SteamEngineBlock.getFacing(state);
    }

    @Override
    public boolean gasConnectable(BlockState state, Direction dir) {
        // 背面（贴锅炉的一侧）不参与气体连接
        return dir != getFacing(state).getOpposite();
    }
}
