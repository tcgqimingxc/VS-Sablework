package org.valkyrienskies.clockwork.content.curiosities.sensor;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.valkyrienskies.clockwork.content.curiosities.tools.screwdriver.IScrewdrivable;

/**
 * 三个传感器的公共基类。
 *
 * <p>对应旧版 {@code sensor/DistanceSensorBlock.kt}、{@code sensor/ImpactSensorBlock.kt}、
 * {@code sensor/GyroscopicSensorBlock.kt} 里逐字重复的那一大段（朝向 / 红石输出 / 2 tick 轮询 / 螺丝刀循环设置）。
 * 移植时把它抽成基类：旧版三份代码几乎完全相同，只是 {@code updatePower} 不同。
 *
 * <p>面向红石的语义与旧版一致：
 * <ul>
 *   <li>只有朝向 {@code FACING} 那一面输出信号（{@code getSignal} / {@code getDirectSignal}）；</li>
 *   <li>信号强度写在方块自身的 {@code power} 属性里，因此能被比较器 / 红石线直接读取；</li>
 *   <li>每隔 {@link ISensorBlock#SAMPLE_INTERVAL} tick 重新采样，方块更新时提前唤醒；</li>
 *   <li>用螺丝刀右键循环自身的档位（通过 {@link IScrewdrivable}，与旧版一致）。</li>
 * </ul>
 */
public abstract class SensorBlockBase extends DirectionalBlock implements ISensorBlock, IWrenchable, IScrewdrivable {

    protected SensorBlockBase(Properties properties) {
        super(properties);
    }

    /** 子类在构造器末尾调用，写入含自身属性的默认状态（旧版 Kotlin 的 {@code registerDefaultState} 写法）。 */
    protected BlockState defaultSensorState() {
        return defaultBlockState().setValue(FACING, Direction.SOUTH).setValue(POWER, 0);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        // 原版的 DirectionalBlock 只声明了 FACING 属性，并不会把它加进状态定义（1.21.1 实测），
        // 必须由子类自己 add，否则 defaultBlockState().setValue(FACING, ...) 会抛
        // "Cannot set property facing as it does not exist"（实机崩溃过一次）。
        builder.add(FACING);
        builder.add(POWER);
    }

    /** 采样后的状态修正钩子（陀螺传感器用它写入 {@code negative}），默认原样返回。 */
    protected BlockState applySampledPower(BlockState state, int signedPower) {
        return state.setValue(POWER, Mth.clamp(Math.abs(signedPower), 0, 15));
    }

    /** 用螺丝刀右键时的"循环设置"钩子；返回 null 表示该方块没有可循环的设置。 */
    protected BlockState cycleSettingWithScrewdriver(BlockState state) {
        return null;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!level.isClientSide) {
            level.scheduleTick(pos, this, SAMPLE_INTERVAL);
        }
        super.onPlace(state, level, pos, oldState, isMoving);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int sampled = updatePower(state, level, pos, random);
        BlockState updated = applySampledPower(state, sampled);
        if (updated != state) {
            // 旧版用 flag 2（只同步客户端），邻居更新在下面显式触发，避免自反馈死循环。
            level.setBlock(pos, updated, 2);
            level.updateNeighborsAt(pos, this);
        }
        level.scheduleTick(pos, this, SAMPLE_INTERVAL);
        updateNeighborsInFront(level, pos, state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                    LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        if (state.getValue(FACING) == direction && state.getValue(POWER) != 0 && !level.isClientSide()) {
            level.scheduleTick(currentPos, this, SAMPLE_INTERVAL);
        }
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    /** 旧版 {@code updateNeighborsInFront}：把变化通报给"背对朝向"的那一侧。 */
    protected void updateNeighborsInFront(Level level, BlockPos pos, BlockState state) {
        Direction direction = state.getValue(FACING);
        BlockPos behind = pos.relative(direction.getOpposite());
        level.neighborChanged(behind, this, pos);
        level.updateNeighborsAtExceptFromFacing(behind, this, direction);
    }

    @Override
    public InteractionResult onScrewdrived(BlockState state, UseOnContext context) {
        BlockState cycled = cycleSettingWithScrewdriver(state);
        if (cycled == null) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        level.setBlock(context.getClickedPos(), cycled, 2);
        playScrewSound(level, context.getClickedPos());
        // 旧版在这里立刻重新排一次采样，让设置生效不用等下一个周期。
        level.scheduleTick(context.getClickedPos(), this, SAMPLE_INTERVAL);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onSneakScrewdrived(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getSignal(level, pos, direction);
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (state.getValue(POWER) != 0 && state.getValue(FACING) == direction) {
            return state.getValue(POWER);
        }
        return 0;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 旧版写的是 nearestLookingDirection.opposite.opposite（等于不变），这里保留原语义。
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
