package org.valkyrienskies.clockwork.content.logistics.gas.heatpipe;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.clockwork.content.logistics.gas.CWGasIndex;
import org.valkyrienskies.clockwork.content.logistics.gas.IGasNodeBlock;

import java.util.Map;
import java.util.function.Function;

/**
 * 热管：把热量从一个气体节点"导"到相邻节点，但<b>不搬运气体</b>。
 *
 * <h2>对应 Create 的哪个类</h2>
 * <p>连接架构逐条对应 Create 1.21.1（6.0.10）的
 * {@code com.simibubi.create.content.fluids.pipes.FluidPipeBlock}：
 * 它继承原版 {@link PipeBlock}，用 {@link PipeBlock#PROPERTY_BY_DIRECTION} 的六个布尔属性表示
 * "这一面通/不通"，用 {@code canConnectTo(...)} 判邻居、用 {@code updateBlockState(...)} 写回状态。
 * 本类同样 {@code extends PipeBlock}，因此：
 * <ul>
 *   <li>六个连接属性、{@code true/false} 的默认值、以及 64 种组合的<b>碰撞箱缓存</b>全部由原版
 *       {@code PipeBlock} 提供（{@code super(3 / 16f, properties)} = 6px 管径，见构造函数）；</li>
 *   <li>blockstate 只写"这一面 true/false"，模型按面拼接（见 {@code blockstates/heat_pipe.json}），
 *       与 Create 的 {@code fluid_pipe.json}（30 条平面 multipart）是同一套思路；</li>
 *   <li>1.21.1 的 Create <b>没有</b> {@code PipeRenderer}/{@code PipeModel}/{@code AllPartialModels.PIPE_*}：
 *       {@code FluidPipeBlock} 没有注册 BER，管体/收口环全部由烘焙模型画（已用 {@code javap} +
 *       {@code jar tf} 核实）。本类照此用烘焙模型，不引入 BER/partial。</li>
 * </ul>
 *
 * <h2>与 Create 的三处必要差异（都有实机/语义原因）</h2>
 * <ol>
 *   <li><b>连接对象</b>：Create 的 {@code canConnectTo} 判的是流体 capability / 管道 / 支架；
 *       这里判的是 {@link HeatNode}（所有气体节点经 {@code GasNode} 继承它）+ 气体方块的端口方向。
 *       画臂与传热共用这一个谓词，不再出现"臂在、温度不动"。</li>
 *   <li><b>不强制补对端</b>：Create 的 {@code updateBlockState} 在只有一个连接时会自动把对面试为 true
 *       （让单放的管道看起来像一段直管）。热管的臂同时是"热接触"（见
 *       {@code HeatPipeBlockEntity#tick}），补一个假的对端会画出一条不传热的臂。因此这里保留
 *       "属性 = 真实邻居"；单放时六面都是<b>端环</b>（{@code cap_*} 模型），不会退化成满方块。</li>
 *   <li><b>只重算变化的那一面</b>：Create 每次 {@code updateBlockState} 重算所有面。热管的扳手可以
 *       单面关断；如果重算所有面，被关掉的那面会在任何邻居更新时立刻被重新打开。这里
 *       {@link #updateShape}/{@link #neighborChanged} 只重算发生变化的 face，让"关掉"一直保持到
 *       这一面的邻居再次变化为止（见 {@link #onWrenched}）。</li>
 * </ol>
 *
 * <h2>放置时为什么要把相邻热管"配对"</h2>
 * <p>两根热管的连接位互为镜像：A 看 B 的 {@code opposite} 位，B 看 A 的位。如果两根都还是
 * {@code false}，任何一侧都不会先变 true —— 旧实现因此有个死锁：两根新放的热管永远连不上，
 * 也就永远不换热。Create 的 {@code canConnectTo} 对管道邻居直接返回 true，从根上避免了这个问题；
 * 本类为了保留"单面扳手关断"仍以对方的位为准，但在<b>放置</b>路径上用
 * {@code forPlacement = true} 先把自己这侧置位，再让对方的 {@code updateShape} 跟着置位。
 * 稳态下（{@link #updateShape}/{@link #neighborChanged}）仍然读对方的位，所以任一侧扳手关掉，
 * 两侧会一起断开。
 *
 * <p><b>只有"从别的方块变成热管"才算放置</b>：原版 {@code LevelChunk#setBlockState} 对同一方块的
 * 状态变化（HEAT 换档、扳手、连接位更新）也会调用 {@code onPlace}，此时 {@code oldState} 仍是热管。
 * {@link #onPlace} 在这种情况下直接返回——否则重算所有面会把扳手刚关掉的位打开，HEAT 每 20 tick
 * 换档时也会把闭锁冲掉。
 *
 * <p>热管仍然不参与气体网络：{@code gasNodeActive=false}、{@code gasConnected=false}、
 * {@code isGasPipe=false}；官方"不搬气体"的语义不变。{@link #gasConnectable} 返回连接位只是给
 * 邻居导管画接口短管用的，与"气体能不能过"是两件事。
 */
public class HeatPipeBlock extends PipeBlock implements IBE<HeatPipeBlockEntity>, IGasNodeBlock, IWrenchable {

    /**
     * 方块 codec。{@link PipeBlock} 是抽象类且要求 {@code codec()}，Create 的
     * {@code FluidPipeBlock} 同样用 {@code simpleCodec(FluidPipeBlock::new)} 实现。
     */
    public static final MapCodec<HeatPipeBlock> CODEC = simpleCodec(HeatPipeBlock::new);

    /**
     * 六个连接属性：直接复用原版 {@link PipeBlock} 的 {@code NORTH/EAST/...}。
     *
     * <p>这样属性名、默认值、以及 {@code PipeBlock} 内部的 AABB 缓存都与 Create 的管道逐字一致；
     * 本类别名只是给方块实体/自检一个稳定的入口（{@code HeatPipeBlock.DIR_TO_CONNECTION}）。
     */
    public static final Map<Direction, BooleanProperty> DIR_TO_CONNECTION = PROPERTY_BY_DIRECTION;

    /**
     * 外观热度档（6 档，对应资源里的 {@code heatpipe_{core,connection,rim}<后缀>.png} 与
     * {@code models/block/heat_pipe/{core,arm_*,cap_*}<后缀>.json}）。
     *
     * <p>这是<b>纯外观</b>属性：只由 {@link HeatPipeBlockEntity} 按气体温度切换，
     * 不参与气体/传热计算（热管的物理行为仍只看 {@link #DIR_TO_CONNECTION}）。
     * 用 BlockState 而不是 BER 换贴图，是因为 blockstate 是原版烘焙管线，切换后自动同步给客户端、
     * 不需要每帧重画几何。
     *
     * <p>阈值是<b>命名常量、只影响观感</b>；前四档刻意对齐气体加热器的三段温度
     * （{@code CWServer.heaterSmoulderingTemp=500}、{@code heaterKindledTemp=800}、
     * {@code heaterSeethingTemp=1300}），最后一档 2000 K 留给创造气源/聚变级热源。
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

    public HeatPipeBlock(Properties properties) {
        // 3/16 = 6px 管径的一半：PipeBlock 用它生成 5..11 的核心与逐方向 0..5 / 11..16 的臂，
        // 与资源里的 heat_pipe 模型是同一套尺寸。Create 的 FluidPipeBlock 传的是 4/16（8px）。
        super(3 / 16f, properties);
        registerDefaultState(defaultBlockState().setValue(HEAT, Heat.COOL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        // PipeBlock 自己不声明这六个属性，由子类加（Create 的 FluidPipeBlock 也是这么写的）。
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN, HEAT);
        super.createBlockStateDefinition(builder);
    }

    @Override
    protected MapCodec<? extends PipeBlock> codec() {
        return CODEC;
    }

    // ===================== 连接判定 =====================

    /**
     * 某个面是否与邻居建立<b>热接触</b>（= 是否画臂 = 是否换热）。
     *
     * <p>规则（与 Create 的 {@code canConnectTo} 同形，只是把"流体 capability"换成"热节点"）：
     * <ul>
     *   <li>相邻热管：看对方朝向本方的连接位（互连；任一侧用扳手关掉都会让两侧一起断开）；</li>
     *   <li>相邻气体方块：必须<b>有热质量</b>（{@link HeatNode#at} 非空）且自报这一面可连
     *       （{@link IGasNodeBlock#gasConnectable}：加热器/斯特林/燃气机只在端口方向为真）；</li>
     *   <li>相邻的非气体热节点（接口留给以后的炉子/热源）：整块都是热质量，任何一面都算。</li>
     * </ul>
     *
     * <p><b>为什么这里要查一次方块实体</b>：只按方块类型判定的旧写法，在"气体方块但不是热节点"
     * 时会画出一条永远不会传热的臂 —— 实机反馈的"臂伸过去了、温度不动"就是这种不一致。
     * 多一次 {@code getBlockEntity} 换来"臂 ⇔ 传热"永远成立。
     *
     * @param state      当前方块状态（保留 Create {@code canConnectTo} 的入口形状；本判定只看邻居）
     * @param pos        当前方块坐标（保留签名；邻居坐标由 {@code neighbourPos} 给出）
     * @param level 用 {@link BlockGetter} 而不是 {@link Level}：{@link #updateShape} 拿到的是
     *              {@link LevelAccessor}，两者都满足 {@code getBlockEntity/getBlockState}。
     */
    public boolean getConnection(BlockState state, BlockPos pos, BlockState neighbourState,
                                 BlockPos neighbourPos, Direction dir, BlockGetter level) {
        return getConnection(neighbourState, neighbourPos, dir, level, false);
    }

    private boolean getConnection(BlockState neighbourState, BlockPos neighbourPos, Direction dir,
                                  BlockGetter level, boolean forPlacement) {
        if (neighbourState.getBlock() instanceof HeatPipeBlock) {
            // 稳态：读对方朝向本方的位，任一侧关掉 → 两侧一起断开。
            // 放置：对方一定还是 false（它的 onPlace 还没跑），先按"可连"算，由 onPlace 配对。
            return forPlacement || neighbourState.getValue(DIR_TO_CONNECTION.get(dir.getOpposite()));
        }
        return canConnect(neighbourState, neighbourPos, dir, level);
    }

    /**
     * 非热管邻居能否形成热接触：是热节点，且（若是气体方块）端口方向允许。
     * 扳手"打开"这一面时复用同一判据，避免造出"有臂但不传热"的死臂。
     */
    private boolean canConnect(BlockState neighbourState, BlockPos neighbourPos, Direction dir, BlockGetter level) {
        if (HeatNode.at(level, neighbourPos) == null) {
            // 没有热质量的邻居：画臂也导不了热，不画。
            return false;
        }
        if (neighbourState.getBlock() instanceof IGasNodeBlock nodeBlock) {
            return nodeBlock.gasConnectable(neighbourState, dir.getOpposite());
        }
        // 非气体的热节点：没有"端口方向"的概念，贴着就算接触。
        return true;
    }

    /**
     * 重新计算六个面（放置/初始化用）。
     *
     * <p>{@code forPlacement=true}：相邻热管即使对方位还是 false 也先算连上，随后
     * {@link #onPlace} 会触发对方的 {@link #updateShape}，两边同时置位（见类注释的死锁说明）。
     */
    public BlockState getConnectedState(BlockGetter level, BlockState state, BlockPos pos) {
        return updateConnections(state, null, level, pos, true);
    }

    /**
     * 重新计算除 {@code ignore} 外的五个面。
     *
     * <p>这是 {@code getConnection} 的批量版本，语义与 Create 的
     * {@code updateBlockState(state, preferredDirection, ignore, world, pos)} 对应。
     * {@code forPlacement=true} 用于 {@link #getConnectedState}（相邻热管先算连上）；
     * {@code false} 是稳态规则（相邻热管读对方的位）。{@link #updateShape}/{@link #neighborChanged}
     * 只重算变化的那一面（见类注释），所以不会走这个批量方法。
     */
    public BlockState updateConnections(BlockState state, @Nullable Direction ignore, BlockGetter level,
                                        BlockPos pos, boolean forPlacement) {
        BlockState result = state;
        for (Direction dir : Direction.values()) {
            if (dir == ignore) {
                continue;
            }
            final BlockPos neighbourPos = pos.relative(dir);
            final BlockState neighbourState = level.getBlockState(neighbourPos);
            final boolean connected = forPlacement
                    ? getConnection(neighbourState, neighbourPos, dir, level, true)
                    : getConnection(state, pos, neighbourState, neighbourPos, dir, level);
            result = result.setValue(DIR_TO_CONNECTION.get(dir), connected);
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
        if (level.isClientSide) {
            return;
        }
        // 只有"从别的方块变成热管"才是新放置。HEAT 档位更新、扳手、连接位更新也会触发
        // LevelChunk#setBlockState -> onPlace，但 oldState 仍是热管；此时重算所有面会
        // 把扳手刚关掉的位立刻打开（甚至每 20 tick 换档时把闭锁冲掉），所以必须直接返回。
        if (oldState.getBlock() == state.getBlock()) {
            return;
        }
        // 放置时用 forPlacement 规则：相邻热管还没机会看到本方块，先把本侧置位，
        // 设置方块状态会触发对方的 updateShape/neighborChanged，对方随即置位。
        final BlockState connected = getConnectedState(level, state, pos);
        if (connected != state) {
            level.setBlockAndUpdate(pos, connected);
        }
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        // 只更新变化的那一面。重算所有面会把扳手关掉的位在任意邻居更新时重新打开；
        // Create 没有"单面关断"，所以它可以每次重算全部，这里不能照抄。
        final boolean connected = getConnection(state, pos, neighbourState, neighbourPos, direction, level);
        return state.setValue(DIR_TO_CONNECTION.get(direction), connected);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
                                boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        // 客户端的方块状态由服务端同步；这里再写一次可能造成"客户端自己改了、服务端没有"的漂移。
        // 形状/连接位的客户端即时更新由 updateShape 负责。
        if (level.isClientSide) {
            return;
        }
        final Direction dir = Direction.fromDelta(fromPos.getX() - pos.getX(), fromPos.getY() - pos.getY(),
                fromPos.getZ() - pos.getZ());
        if (dir == null) {
            return;
        }
        final BlockState neighbourState = level.getBlockState(fromPos);
        final boolean connected = getConnection(state, pos, neighbourState, fromPos, dir, level);
        if (connected != state.getValue(DIR_TO_CONNECTION.get(dir))) {
            level.setBlockAndUpdate(pos, state.setValue(DIR_TO_CONNECTION.get(dir), connected));
        }
    }

    /**
     * 邻居问"这一面能不能连"时返回自己的连接位。
     *
     * <p>注意这里的"连"对热管是<b>结构/热接触</b>，不是"气体能通过"：导管据此画自己的接口短管
     * （否则两根管子之间会留 3px 空隙），而热管方块实体把 {@code gasConnected} 恒定为 false，
     * 保证气体网络不会把热管当节点搬气。两件事分开声明，见 {@code HeatPipeBlockEntity#gasConnected}。
     */
    @Override
    public boolean gasConnectable(BlockState state, Direction dir) {
        return state.getValue(DIR_TO_CONNECTION.get(dir));
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
        final boolean current = state.getValue(DIR_TO_CONNECTION.get(dir));
        // 关掉永远允许；打开则必须"打得开也导得了热"，否则会造出一条不传热的死臂。
        // 相邻热管例外：它的连接位会在 neighborChanged/updateShape 里跟着本方块一起置位。
        if (!current && !canWrenchOn(level, pos, dir)) {
            return InteractionResult.FAIL;
        }
        // 只改这一面：updateShape/neighborChanged 只重算变化面，所以关掉的位不会被下一次
        // 无关的邻居更新重新打开（这正是类注释里的"扳手闭锁"）。
        level.setBlockAndUpdate(pos, state.setValue(DIR_TO_CONNECTION.get(dir), !current));
        level.playSound(null, pos, AllSoundEvents.WRENCH_ROTATE.getMainEvent(),
                net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
        return InteractionResult.SUCCESS;
    }

    /** 扳手"打开"这一面时，邻居是否真的能形成热接触（规则与 {@link #getConnection} 一致，热管除外）。 */
    private boolean canWrenchOn(Level level, BlockPos pos, Direction dir) {
        final BlockPos neighbourPos = pos.relative(dir);
        final BlockState neighbourState = level.getBlockState(neighbourPos);
        if (neighbourState.getBlock() instanceof HeatPipeBlock) {
            // 对面热管会在 updateShape/neighborChanged 里用本方块的新连接位重算，不需要它先为 true。
            return true;
        }
        return canConnect(neighbourState, neighbourPos, dir, level);
    }

    // ===================== 旋转/镜像 =====================

    /**
     * 六个连接位随方块旋转/镜像重排。Create 的 {@code FluidPipeBlock} 同样覆写 rotate/mirror
     * （{@code FluidPipeBlockRotation}）；worldgen/结构粘贴/Schematic 都依赖它。
     */
    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return remapDirections(state, rotation::rotate);
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return remapDirections(state, mirror::mirror);
    }

    private static BlockState remapDirections(BlockState state, Function<Direction, Direction> mapping) {
        BlockState result = state;
        for (Direction dir : Direction.values()) {
            result = result.setValue(DIR_TO_CONNECTION.get(mapping.apply(dir)),
                    state.getValue(DIR_TO_CONNECTION.get(dir)));
        }
        return result;
    }

    // ===================== IBE =====================

    @Override
    public Class<HeatPipeBlockEntity> getBlockEntityClass() {
        return HeatPipeBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends HeatPipeBlockEntity> getBlockEntityType() {
        return CWGasIndex.HEAT_PIPE_BE.get();
    }
}
