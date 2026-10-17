package org.valkyrienskies.clockwork.content.logistics.gas.filter;

import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.valkyrienskies.clockwork.content.logistics.gas.CWGasIndex;
import org.valkyrienskies.clockwork.content.logistics.gas.IGasNodeBlock;

/**
 * 滤管：只允许指定气体通过的气体导管。
 *
 * <p><b>旧版与新仓库都没有这个方块的实现源码</b>（只有 {@code blockstates/filter_duct.json}
 * 指向 {@code block/filter/block}）。旧版对应的玩法是 Kelvin 的
 * {@code ClockworkFilteredDuctEdge} / {@code ClockworkFilteredOnewayDuctEdge}（按边过滤，
 * 配置界面在 {@code filter/FilterScreen.kt}）。本实现是<b>简化的重新设计</b>：
 *
 * <ul>
 *   <li>方块状态只有 {@code axis}（与资源 JSON 一致），只朝轴向两面连通。</li>
 *   <li>节点带一个"目标气体 + 白/黑名单"设置：
 *       <b>空手右键</b>循环切换目标气体（跳过当前网络里没有的气体，方便选到想要的那种），
 *       <b>潜行右键</b>在白名单 / 黑名单之间切换。</li>
 *   <li>白名单：本节点参与的所有连接只搬运目标气体；黑名单：只挡目标气体。</li>
 *   <li>设置保存在方块实体的 NBT 里（不掉落物品，破坏再放会重置）。</li>
 * </ul>
 */
public class FilterDuctBlock extends RotatedPillarBlock implements IBE<FilterDuctBlockEntity>, IGasNodeBlock {

    private static final VoxelShape CORE = box(3.0, 3.0, 3.0, 13.0, 13.0, 13.0);
    private static final VoxelShape ARM_X = box(0.0, 3.0, 3.0, 16.0, 13.0, 13.0);
    private static final VoxelShape ARM_Y = box(3.0, 0.0, 3.0, 13.0, 16.0, 13.0);
    private static final VoxelShape ARM_Z = box(3.0, 3.0, 0.0, 13.0, 13.0, 16.0);

    public FilterDuctBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, context.getNearestLookingDirection().getAxis());
    }

    @Override
    public boolean gasConnectable(BlockState state, Direction dir) {
        return dir.getAxis() == state.getValue(AXIS);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(AXIS)) {
            case X -> Shapes.or(CORE, ARM_X);
            case Y -> Shapes.or(CORE, ARM_Y);
            case Z -> Shapes.or(CORE, ARM_Z);
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof FilterDuctBlockEntity filter)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            filter.toggleMode();
        } else {
            filter.cycleGas();
        }
        player.displayClientMessage(Component.literal(String.format("Filter: %s %s",
                filter.isWhitelist() ? "only" : "block",
                filter.filterType() == null ? "all gases" : filter.filterType().displayName())), true);
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.6F, 1.2F);
        filter.sendData();
        return InteractionResult.SUCCESS;
    }

    @Override
    public Class<FilterDuctBlockEntity> getBlockEntityClass() {
        return FilterDuctBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends FilterDuctBlockEntity> getBlockEntityType() {
        return CWGasIndex.FILTER_DUCT_BE.get();
    }
}
