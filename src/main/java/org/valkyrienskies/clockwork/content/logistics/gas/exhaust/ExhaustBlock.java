package org.valkyrienskies.clockwork.content.logistics.gas.exhaust;

import com.simibubi.create.AllShapes;
import com.simibubi.create.foundation.block.IBE;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.valkyrienskies.clockwork.content.logistics.gas.CWGasIndex;
import org.valkyrienskies.clockwork.content.logistics.gas.IGasNodeBlock;

/**
 * 排气口：把网络里的气体排到外部环境。
 *
 * <p>对应旧版 {@code ExhaustBlock}（{@code DirectionalBlock} + {@code facing}）。资源 JSON 只有 facing，
 * 与旧版一致（旧版虽然 {@code implements IDuct}，但并没有把 6 个连接属性加进方块状态）。
 *
 * <p>与旧版的差异：旧版把排气接进 Create 的风扇气流系统（{@code IAirCurrentSource} + mixin），
 * 排出气体同时吹动实体、并按温度触发烟熏/高炉加工。本移植保留"排空气体"这一核心行为与
 * 面向的烫伤判定，气流/加工集成留待后续（需要 mixin，不在本切片范围内）。
 */
public class ExhaustBlock extends DirectionalBlock implements IBE<ExhaustBlockEntity>, IGasNodeBlock {

    public static final MapCodec<ExhaustBlock> CODEC = simpleCodec(ExhaustBlock::new);

    public ExhaustBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return AllShapes.EIGHT_VOXEL_POLE.get(state.getValue(FACING).getAxis());
    }

    @Override
    public Class<ExhaustBlockEntity> getBlockEntityClass() {
        return ExhaustBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ExhaustBlockEntity> getBlockEntityType() {
        return CWGasIndex.EXHAUST_BE.get();
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    /**
     * 只在背面（{@code FACING} 的反面）接气。
     *
     * <p>旧版 {@code ExhaustBlock.canConnectTo} 的判定是
     * {@code direction != FACING.opposite() -> false}；原实现写成 {@code dir != FACING}
     * 会额外接通上/下/左/右四面，排气口会从侧面误连进管道网络，也会让相邻导管画出
     * 并不存在的接口圈。这里按旧版语义收紧到唯一背面。
     */
    @Override
    public boolean gasConnectable(BlockState state, Direction dir) {
        return dir == state.getValue(FACING).getOpposite();
    }

    /** 客户端按节点压力喷白烟（旧版是自定义气体粒子，这里用原版粒子替代）。 */
    @Override
    public void animateTick(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
                            net.minecraft.util.RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof ExhaustBlockEntity exhaust)) {
            return;
        }
        if (exhaust.gasMix().isEmpty() || random.nextFloat() > 0.5F) {
            return;
        }
        Direction facing = state.getValue(FACING);
        double speed = Math.min(0.1 + exhaust.gasMix().pressure(exhaust.gasCapacityKg()) / 1013250.0 * 0.4, 0.5);
        level.addParticle(net.minecraft.core.particles.ParticleTypes.WHITE_SMOKE,
                pos.getX() + 0.5 + facing.getStepX() * 0.7,
                pos.getY() + 0.5 + facing.getStepY() * 0.7,
                pos.getZ() + 0.5 + facing.getStepZ() * 0.7,
                facing.getStepX() * speed, facing.getStepY() * speed, facing.getStepZ() * speed);
    }
}
