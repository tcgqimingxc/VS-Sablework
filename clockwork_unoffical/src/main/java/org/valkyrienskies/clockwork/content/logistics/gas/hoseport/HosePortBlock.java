package org.valkyrienskies.clockwork.content.logistics.gas.hoseport;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.valkyrienskies.clockwork.content.logistics.gas.CWGasIndex;
import org.valkyrienskies.clockwork.content.logistics.gas.IGasNodeBlock;

/**
 * 软管接口：两个接口配对后，各自所在的气体网络「远程连通」。
 *
 * <p>对应旧版 {@code content/logistics/gas/hoseport/HosePortBlock}（Kotlin，{@code DirectionalBlock}
 * + {@code INodeBlock} + {@code IUniversalJoint}）。旧版靠 VS2 的"距离关节 + Kelvin 管道边"实现，
 * 那两样在 1.21.1 都不存在，因此本实现是<b>重新设计</b>（详见 {@link HosePortBlockEntity} 类头）：
 * <ul>
 *   <li>配对沿用本移植已有的「接头」机制（{@code UniversalJoint}）：手持<b>软管卷轴</b>
 *       （{@code extendon_hose}，与旧版同一个物品）右键一个接口 → 再右键另一个接口即连通；
 *       空手右键已连通的接口 = 断开并把软管卷轴退还（旧版 {@code HosePortBlock.use} 的行为）。</li>
 *   <li>朝向规则与旧版一致：默认背对玩家（接口朝向玩家），潜行放置时朝向玩家视线方向；
 *       朝向的那一面留给软管，不参与导管连接。</li>
 * </ul>
 */
public class HosePortBlock extends DirectionalBlock implements IBE<HosePortBlockEntity>, IGasNodeBlock {

    public static final MapCodec<HosePortBlock> CODEC = simpleCodec(HosePortBlock::new);

    public HosePortBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** 旧版规则：默认背对玩家；潜行时朝向玩家视线（便于把接口对准某个方向）。 */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        final Direction looking = context.getNearestLookingDirection();
        final Player player = context.getPlayer();
        final boolean sneaking = player != null && player.isShiftKeyDown();
        return defaultBlockState().setValue(FACING, sneaking ? looking : looking.getOpposite());
    }

    /** 接口朝向的那一面留给软管，其余 5 面都可以接导管。 */
    @Override
    public boolean gasConnectable(BlockState state, Direction dir) {
        return dir != state.getValue(FACING);
    }

    /** 空手右键：断开并把软管卷轴退还（与旧版 {@code HosePortBlock.use} 一致）。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof HosePortBlockEntity port) || !port.isLinked()) {
            return InteractionResult.PASS;
        }
        port.disconnectAndRefund(player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public Class<HosePortBlockEntity> getBlockEntityClass() {
        return HosePortBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends HosePortBlockEntity> getBlockEntityType() {
        return CWGasIndex.HOSE_PORT_BE.get();
    }
}
