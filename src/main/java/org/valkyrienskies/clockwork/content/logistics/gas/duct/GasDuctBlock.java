package org.valkyrienskies.clockwork.content.logistics.gas.duct;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.valkyrienskies.clockwork.content.logistics.gas.IGasNodeBlock;

import java.util.EnumMap;
import java.util.Map;

/**
 * 气体导管：气体网络的骨架。
 *
 * <p>对应旧版 {@code DuctBlock}。行为一致的部分：6 个面的连接状态（扳手切换 / 自动重连 /
 * 爆炸变泄漏）、含水（waterlogged）、管道形状、泄漏粒子。
 *
 * <p>与旧版的差异：
 * <ul>
 *   <li>旧版 {@code onWrenched} 会把连接切成 {@code TEMP_ON} 再靠 {@code neighborChanged} 修回；
 *       这里直接 SIDE ↔ FORCED_OFF 两态循环，语义更直观（见 {@link DuctConnectionType}）。</li>
 *   <li>旧版用螺丝刀（{@code IScrewdrivable}）循环边的类型（单向/过滤/智能）并打开配置界面；
 *       螺丝刀与其界面不在本次移植范围，因此只保留扳手的连接开关。边类型（单向/过滤）的
 *       玩法在 {@code GasLink} 里有对应字段，但暂未提供方块入口。</li>
 *   <li>旧版 {@code animateTick} 会按压力差喷发气体粒子，并且受客户端配置开关控制；
 *       这里只在泄漏面上喷烟，避免每刻对每个导管做压力读取（更省，也更符合"看得见的问题"）。</li>
 * </ul>
 */
public class GasDuctBlock extends Block
        implements IBE<GasDuctBlockEntity>, IGasNodeBlock, SimpleWaterloggedBlock, IWrenchable {

    public static final EnumProperty<DuctConnectionType> NORTH = EnumProperty.create("north", DuctConnectionType.class);
    public static final EnumProperty<DuctConnectionType> EAST = EnumProperty.create("east", DuctConnectionType.class);
    public static final EnumProperty<DuctConnectionType> SOUTH = EnumProperty.create("south", DuctConnectionType.class);
    public static final EnumProperty<DuctConnectionType> WEST = EnumProperty.create("west", DuctConnectionType.class);
    public static final EnumProperty<DuctConnectionType> UP = EnumProperty.create("up", DuctConnectionType.class);
    public static final EnumProperty<DuctConnectionType> DOWN = EnumProperty.create("down", DuctConnectionType.class);

    /** 方向 → 连接属性。顺序与 Direction.values() 无关，用 EnumMap 保证查表快。 */
    public static final Map<Direction, EnumProperty<DuctConnectionType>> DIR_TO_CONNECTION =
            new EnumMap<>(Direction.class);

    static {
        DIR_TO_CONNECTION.put(Direction.NORTH, NORTH);
        DIR_TO_CONNECTION.put(Direction.EAST, EAST);
        DIR_TO_CONNECTION.put(Direction.SOUTH, SOUTH);
        DIR_TO_CONNECTION.put(Direction.WEST, WEST);
        DIR_TO_CONNECTION.put(Direction.UP, UP);
        DIR_TO_CONNECTION.put(Direction.DOWN, DOWN);
    }

    private static final VoxelShape CORE = box(3.0, 3.0, 3.0, 13.0, 13.0, 13.0);
    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Direction.class);

    static {
        ARMS.put(Direction.NORTH, box(3.0, 3.0, 0.0, 13.0, 13.0, 3.0));
        ARMS.put(Direction.SOUTH, box(3.0, 3.0, 13.0, 13.0, 13.0, 16.0));
        ARMS.put(Direction.WEST, box(0.0, 3.0, 3.0, 3.0, 13.0, 13.0));
        ARMS.put(Direction.EAST, box(13.0, 3.0, 3.0, 16.0, 13.0, 13.0));
        ARMS.put(Direction.UP, box(3.0, 13.0, 3.0, 13.0, 16.0, 13.0));
        ARMS.put(Direction.DOWN, box(3.0, 0.0, 3.0, 13.0, 3.0, 13.0));
    }

    /** 按 6 位连接掩码缓存碰撞箱。 */
    private final VoxelShape[] shapeCache = new VoxelShape[64];

    public GasDuctBlock(Properties properties) {
        super(properties);
        BlockState state = defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, false);
        for (EnumProperty<DuctConnectionType> property : DIR_TO_CONNECTION.values()) {
            state = state.setValue(property, DuctConnectionType.NONE);
        }
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN, BlockStateProperties.WATERLOGGED);
    }

    @Override
    public Class<GasDuctBlockEntity> getBlockEntityClass() {
        return GasDuctBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends GasDuctBlockEntity> getBlockEntityType() {
        return org.valkyrienskies.clockwork.content.logistics.gas.CWGasIndex.DUCT_BE.get();
    }

    // ===================== 连接判定 =====================

    /**
     * 计算某个面的连接状态。
     *
     * <p>规则（对应旧版 {@code DuctBlock.getConnection}）：
     * <ul>
     *   <li>LEAK / FORCED_OFF 是"粘性"状态，邻居变化不会自动覆盖，只有扳手能改；</li>
     *   <li>相邻导管：只要对方不是 FORCED_OFF / LEAK 就互连（旧版 {@code canBeChanged()} 语义）；</li>
     *   <li>相邻气体方块：由对方的 {@link IGasNodeBlock#gasConnectable} 决定。</li>
     * </ul>
     */
    public DuctConnectionType getConnection(BlockState state, BlockPos pos, BlockState neighborState,
                                            BlockPos neighborPos, Direction dir, Level level) {
        DuctConnectionType current = state.getValue(DIR_TO_CONNECTION.get(dir));
        if (current == DuctConnectionType.LEAK || current == DuctConnectionType.FORCED_OFF) {
            return current;
        }
        boolean neighborAllows = false;
        if (neighborState.getBlock() instanceof GasDuctBlock) {
            DuctConnectionType neighborValue = neighborState.getValue(DIR_TO_CONNECTION.get(dir.getOpposite()));
            neighborAllows = neighborValue != DuctConnectionType.FORCED_OFF
                    && neighborValue != DuctConnectionType.LEAK;
        } else if (neighborState.getBlock() instanceof IGasNodeBlock nodeBlock) {
            neighborAllows = nodeBlock.gasConnectable(neighborState, dir.getOpposite());
        }
        return neighborAllows ? DuctConnectionType.SIDE : DuctConnectionType.NONE;
    }

    /** 按周围方块重算全部 6 个面。 */
    public BlockState getConnectedState(Level level, BlockState state, BlockPos pos) {
        BlockState result = state;
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.relative(dir);
            result = result.setValue(DIR_TO_CONNECTION.get(dir),
                    getConnection(result, pos, level.getBlockState(neighborPos), neighborPos, dir, level));
        }
        return result.setValue(BlockStateProperties.WATERLOGGED,
                level.getFluidState(pos).getType() == Fluids.WATER);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide) {
            level.setBlockAndUpdate(pos, getConnectedState(level, state, pos));
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
                                boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        Direction dir = Direction.fromDelta(fromPos.getX() - pos.getX(), fromPos.getY() - pos.getY(),
                fromPos.getZ() - pos.getZ());
        if (dir == null) {
            return;
        }
        DuctConnectionType connection = getConnection(state, pos, level.getBlockState(fromPos), fromPos, dir, level);
        if (connection != state.getValue(DIR_TO_CONNECTION.get(dir))) {
            level.setBlockAndUpdate(pos, state.setValue(DIR_TO_CONNECTION.get(dir), connection));
        }
    }

    @Override
    public void wasExploded(Level level, BlockPos pos, Explosion explosion) {
        // 旧版行为：被炸掉的导管会把相邻导管的对应面打成泄漏
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.relative(dir);
            BlockState neighborState = level.getBlockState(neighborPos);
            if (neighborState.getBlock() instanceof GasDuctBlock) {
                level.setBlockAndUpdate(neighborPos, neighborState.setValue(
                        DIR_TO_CONNECTION.get(dir.getOpposite()), DuctConnectionType.LEAK));
            }
        }
    }

    // ===================== 扳手 =====================

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Direction changeDirection = pickConnectionDirection(context.getClickedFace(), context.getClickLocation(),
                context.getClickedPos());
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockState(pos.relative(changeDirection)).getBlock() instanceof IGasNodeBlock)) {
            return InteractionResult.FAIL;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        DuctConnectionType current = state.getValue(DIR_TO_CONNECTION.get(changeDirection));
        DuctConnectionType next = current == DuctConnectionType.SIDE
                ? DuctConnectionType.FORCED_OFF
                : DuctConnectionType.SIDE;
        level.setBlockAndUpdate(pos, state.setValue(DIR_TO_CONNECTION.get(changeDirection), next));
        level.playSound(null, pos, AllSoundEvents.WRENCH_ROTATE.getMainEvent(), SoundSource.BLOCKS, 1.0F, 1.0F);
        return InteractionResult.SUCCESS;
    }

    /**
     * 判断玩家想改的是哪个面。
     *
     * <p>简化自旧版 {@code DuctBlock.getUseDirection}（旧版按命中点在该面平面内的相对位置做一串
     * 轴向旋转查表）：这里取"命中点相对方块中心、垂直于点击面"的两个分量，谁更大就取谁的正负方向；
     * 命中点靠近中心时直接用点击面本身。手感等价，代码短得多。
     */
    public static Direction pickConnectionDirection(Direction face, Vec3 hit, BlockPos pos) {
        double dx = hit.x - (pos.getX() + 0.5);
        double dy = hit.y - (pos.getY() + 0.5);
        double dz = hit.z - (pos.getZ() + 0.5);
        double ax = face.getAxis() == Direction.Axis.X ? 0 : Math.abs(dx);
        double ay = face.getAxis() == Direction.Axis.Y ? 0 : Math.abs(dy);
        double az = face.getAxis() == Direction.Axis.Z ? 0 : Math.abs(dz);
        if (ax < 0.25 && ay < 0.25 && az < 0.25) {
            return face;
        }
        if (ax >= ay && ax >= az) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        }
        if (ay >= az) {
            return dy > 0 ? Direction.UP : Direction.DOWN;
        }
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    // ===================== 形状 / 水 / 粒子 =====================

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int mask = 0;
        for (Direction dir : Direction.values()) {
            if (state.getValue(DIR_TO_CONNECTION.get(dir)) == DuctConnectionType.SIDE) {
                mask |= 1 << dir.get3DDataValue();
            }
        }
        VoxelShape cached = shapeCache[mask];
        if (cached == null) {
            VoxelShape shape = CORE;
            for (Direction dir : Direction.values()) {
                if ((mask & (1 << dir.get3DDataValue())) != 0) {
                    shape = Shapes.or(shape, ARMS.get(dir));
                }
            }
            cached = shape;
            shapeCache[mask] = shape;
        }
        return cached;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
        return defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, fluid.getType() == Fluids.WATER);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(BlockStateProperties.WATERLOGGED)
                ? Fluids.WATER.getSource(false)
                : super.getFluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(BlockStateProperties.WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        for (Direction dir : Direction.values()) {
            if (state.getValue(DIR_TO_CONNECTION.get(dir)) != DuctConnectionType.LEAK) {
                continue;
            }
            if (random.nextFloat() > 0.4F) {
                continue;
            }
            Vec3 normal = Vec3.atLowerCornerOf(dir.getNormal());
            double x = pos.getX() + 0.5 + normal.x * 0.6 + (random.nextDouble() - 0.5) * 0.4;
            double y = pos.getY() + 0.5 + normal.y * 0.6 + (random.nextDouble() - 0.5) * 0.4;
            double z = pos.getZ() + 0.5 + normal.z * 0.6 + (random.nextDouble() - 0.5) * 0.4;
            level.addParticle(ParticleTypes.CLOUD, x, y, z,
                    normal.x * 0.05 + (random.nextDouble() - 0.5) * 0.02,
                    normal.y * 0.05 + (random.nextDouble() - 0.5) * 0.02,
                    normal.z * 0.05 + (random.nextDouble() - 0.5) * 0.02);
        }
    }
}
