package org.valkyrienskies.clockwork.content.physicalities.spinoff_bearing;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.valkyrienskies.clockwork.content.kinetics.CWKineticsIndex;
import org.valkyrienskies.clockwork.content.kinetics.CWKineticsShapes;

/**
 * 对接口（惰性轴承）方块。
 *
 * <p>对应旧版 {@code content/physicalities/spinoff_bearing/SpinoffBearingBlock.kt}。
 * 两个对接口"面对面"放在两个结构上，它们之间就会形成一个自由铰链：
 * 转动照传、力不传（旧版是 {@code VSRevoluteJoint(driveFreeSpin = true)}）。
 *
 * <p>与旧版的差异：
 * <ul>
 *   <li>旧版实现 {@code ICopyableBlock}（VS2 蓝图粘贴时迁移 partner 坐标），1.21.1 没有这个接口；
 *       本移植改为在方块实体里校验"两端刚体是否还是同一个"，被搬走/重装时自动重新对接
 *       （见 {@code SpinoffBearingBlockEntity#tick}）。</li>
 *   <li>放置朝向沿用旧版：按住 Shift 放 = 朝玩家看的方向，否则朝反方向。</li>
 * </ul>
 */
public class SpinoffBearingBlock extends DirectionalBlock implements IBE<SpinoffBearingBlockEntity> {

    public static final MapCodec<SpinoffBearingBlock> CODEC = simpleCodec(SpinoffBearingBlock::new);

    public SpinoffBearingBlock(final Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        final Direction nearest = context.getNearestLookingDirection();
        return defaultBlockState().setValue(FACING, context.isSecondaryUseActive() ? nearest : nearest.getOpposite());
    }

    /** 碰撞箱沿用旧版 {@code ClockworkShapes.SPINOFF_BEARING}（下半砖，随朝向旋转）。 */
    @Override
    protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos,
                                  final CollisionContext context) {
        return CWKineticsShapes.SPINOFF_BEARING.get(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getVisualShape(final BlockState state, final BlockGetter level, final BlockPos pos,
                                        final CollisionContext context) {
        return this.getShape(state, level, pos, context);
    }

    /** 拆掉方块时断开连接，避免留下悬空的约束句柄。 */
    @Override
    protected void onRemove(final BlockState state, final Level level, final BlockPos pos, final BlockState newState,
                            final boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof final SpinoffBearingBlockEntity bearing) {
            bearing.disconnect();
        }
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<SpinoffBearingBlockEntity> getBlockEntityClass() {
        return SpinoffBearingBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SpinoffBearingBlockEntity> getBlockEntityType() {
        return CWKineticsIndex.SPINOFF_BEARING_BE.get();
    }
}
