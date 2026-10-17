package org.valkyrienskies.clockwork.content.logistics.gas.heatpipe;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.valkyrienskies.clockwork.content.logistics.gas.CWGasIndex;
import org.valkyrienskies.clockwork.content.logistics.gas.IGasNodeBlock;

import java.util.EnumMap;
import java.util.Map;

/**
 * 热管：把热量从一个气体节点"导"到相邻节点（包括<b>不通气体</b>的邻居）。
 *
 * <p><b>旧版与新仓库都没有这个方块的实现源码</b>（只有 {@code blockstates/heat_pipe.json}
 * 的 multipart 与 15 张贴图）。本实现是按 blockstate 已声明的 6 个连接布尔属性
 * （{@code up/down/north/south/east/west}）自行设计的：
 *
 * <ul>
 *   <li>连接规则与普通导管一致：相邻导管互连、相邻气体方块看 {@link IGasNodeBlock#gasConnectable}，
 *       用<b>扳手</b>可以单独关掉某一面（与资源里的 multipart 形状对应）。</li>
 *   <li>它<b>不算气体管道</b>：本体的气体不被模拟（{@code gasPressureDecay} 不在它身上生效），
 *       因此不会把网络里的气体从"接缝"漏出去，也不会把两台机器直接连通。</li>
 *   <li>它仍然按气体节点注册，所以邻接的机器/导管会自动与它建边 —— 但因为没有气体流动，
 *       这些边的唯一作用是"知道谁是邻居"，供传热使用。</li>
 *   <li>每刻与相邻的热节点交换热量（见 {@link HeatPipeBlockEntity}）。</li>
 * </ul>
 */
public class HeatPipeBlock extends Block implements IBE<HeatPipeBlockEntity>, IGasNodeBlock, IWrenchable {

    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty WEST = BooleanProperty.create("west");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");

    /**
     * 外观热度档（6 档，对应资源里的 {@code heatpipe_{core,connection,rim}<后缀>.png} 与
     * {@code models/block/heat_pipe/*<后缀>.json}）。
     *
     * <p>这是<b>纯外观</b>属性：只由 {@link HeatPipeBlockEntity} 按气体温度切换，
     * 不参与气体/传热计算（热管的物理行为仍只看 {@link #DIR_TO_CONNECTION}）。
     * 用 BlockState 而不是 BER 换贴图，是因为：blockstate 是原版烘焙管线，切换后自动同步给客户端、
     * 不需要每帧画整块几何；资源里的六档热度模型（228 个）也能直接被引用（否则是死资源）。
     *
     * <p>阈值是<b>命名常量、只影响观感</b>；前四档刻意对齐气体加热器的三段温度
     * （{@code CWServer.heaterSmoulderingTemp=500}、{@code heaterKindledTemp=800}、
     * {@code heaterSeethingTemp=1300}），这样"加热器到哪一档、热管就红到哪一档"，
     * 最后一档 2000 K 留给创造气源/聚变级热源。
     */
    public enum Heat implements StringRepresentable {
        COOL("cool", 0.0),
        WARM("warm", 373.15),
        HOT("hot", 500.0),
        VERY_HOT("very_hot", 800.0),
        MOLTEN("molten", 1300.0),
        SUPER_HOT("super_hot", 2000.0);

        private static final Heat[] VALUES = values();

        private final String name;
        /** 进入该档所需的最低温度 [K]。 */
        private final double minKelvin;

        Heat(String name, double minKelvin) {
            this.name = name;
            this.minKelvin = minKelvin;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        /** 温度 → 档位；无分配，热管每 20 tick 调一次。 */
        public static Heat forTemperature(double kelvin) {
            Heat result = COOL;
            for (Heat heat : VALUES) {
                if (kelvin >= heat.minKelvin) {
                    result = heat;
                }
            }
            return result;
        }
    }

    /** 外观热度。默认 COOL；方块实体在温度跨档时更新。 */
    public static final EnumProperty<Heat> HEAT = EnumProperty.create("heat", Heat.class);

    /** 方向 → 连接属性（与 blockstate 的 multipart 条件逐字对应）。 */
    public static final Map<Direction, BooleanProperty> DIR_TO_CONNECTION = new EnumMap<>(Direction.class);

    static {
        DIR_TO_CONNECTION.put(Direction.NORTH, NORTH);
        DIR_TO_CONNECTION.put(Direction.EAST, EAST);
        DIR_TO_CONNECTION.put(Direction.SOUTH, SOUTH);
        DIR_TO_CONNECTION.put(Direction.WEST, WEST);
        DIR_TO_CONNECTION.put(Direction.UP, UP);
        DIR_TO_CONNECTION.put(Direction.DOWN, DOWN);
    }

    private static final VoxelShape CORE = box(5.0, 5.0, 5.0, 11.0, 11.0, 11.0);
    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Direction.class);

    static {
        ARMS.put(Direction.NORTH, box(5.0, 5.0, 0.0, 11.0, 11.0, 5.0));
        ARMS.put(Direction.SOUTH, box(5.0, 5.0, 11.0, 11.0, 11.0, 16.0));
        ARMS.put(Direction.WEST, box(0.0, 5.0, 5.0, 5.0, 11.0, 11.0));
        ARMS.put(Direction.EAST, box(11.0, 5.0, 5.0, 16.0, 11.0, 11.0));
        ARMS.put(Direction.UP, box(5.0, 11.0, 5.0, 11.0, 16.0, 11.0));
        ARMS.put(Direction.DOWN, box(5.0, 0.0, 5.0, 11.0, 5.0, 11.0));
    }

    private static final VoxelShape[] SHAPE_CACHE = new VoxelShape[64];

    public HeatPipeBlock(Properties properties) {
        super(properties);
        BlockState state = defaultBlockState();
        for (BooleanProperty property : DIR_TO_CONNECTION.values()) {
            state = state.setValue(property, false);
        }
        state = state.setValue(HEAT, Heat.COOL);
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN, HEAT);
    }

    // ===================== 连接判定 =====================

    /** 与 {@code GasDuctBlock#getConnection} 同规则，但只有两态（连 / 不连）。 */
    public boolean getConnection(BlockState state, BlockPos pos, BlockState neighborState,
                                 BlockPos neighborPos, Direction dir, Level level) {
        if (neighborState.getBlock() instanceof HeatPipeBlock) {
            return neighborState.getValue(DIR_TO_CONNECTION.get(dir.getOpposite()));
        }
        if (neighborState.getBlock() instanceof IGasNodeBlock nodeBlock) {
            return nodeBlock.gasConnectable(neighborState, dir.getOpposite());
        }
        return false;
    }

    public BlockState getConnectedState(Level level, BlockState state, BlockPos pos) {
        BlockState result = state;
        for (Direction dir : Direction.values()) {
            result = result.setValue(DIR_TO_CONNECTION.get(dir),
                    getConnection(result, pos, level.getBlockState(pos.relative(dir)), pos.relative(dir), dir, level));
        }
        return result;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return getConnectedState(context.getLevel(), defaultBlockState(), context.getClickedPos());
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
        final Direction dir = Direction.fromDelta(fromPos.getX() - pos.getX(), fromPos.getY() - pos.getY(),
                fromPos.getZ() - pos.getZ());
        if (dir == null) {
            return;
        }
        final boolean connection = getConnection(state, pos, level.getBlockState(fromPos), fromPos, dir, level);
        if (connection != state.getValue(DIR_TO_CONNECTION.get(dir))) {
            level.setBlockAndUpdate(pos, state.setValue(DIR_TO_CONNECTION.get(dir), connection));
        }
    }

    /** 热管本身愿意与任意方向的气体节点相通（是否真连通由双方的连接状态决定）。 */
    @Override
    public boolean gasConnectable(BlockState state, Direction dir) {
        return state.getValue(DIR_TO_CONNECTION.get(dir));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int mask = 0;
        for (Direction dir : Direction.values()) {
            if (state.getValue(DIR_TO_CONNECTION.get(dir))) {
                mask |= 1 << dir.get3DDataValue();
            }
        }
        VoxelShape cached = SHAPE_CACHE[mask];
        if (cached == null) {
            VoxelShape shape = CORE;
            for (Direction dir : Direction.values()) {
                if ((mask & (1 << dir.get3DDataValue())) != 0) {
                    shape = Shapes.or(shape, ARMS.get(dir));
                }
            }
            cached = shape;
            SHAPE_CACHE[mask] = shape;
        }
        return cached;
    }

    // ===================== 扳手 =====================

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        final Level level = context.getLevel();
        final BlockPos pos = context.getClickedPos();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        final Direction dir = context.getClickedFace();
        final boolean next = !state.getValue(DIR_TO_CONNECTION.get(dir));
        level.setBlockAndUpdate(pos, state.setValue(DIR_TO_CONNECTION.get(dir), next));
        level.playSound(null, pos, AllSoundEvents.WRENCH_ROTATE.getMainEvent(),
                net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
        return InteractionResult.SUCCESS;
    }

    @Override
    public Class<HeatPipeBlockEntity> getBlockEntityClass() {
        return HeatPipeBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends HeatPipeBlockEntity> getBlockEntityType() {
        return CWGasIndex.HEAT_PIPE_BE.get();
    }
}
