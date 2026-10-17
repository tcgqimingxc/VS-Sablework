package org.valkyrienskies.clockwork.content.kinetics.universal_shaft;

import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.clockwork.content.kinetics.CWKineticsIndex;
import org.valkyrienskies.clockwork.content.kinetics.CWKineticsShapes;
import org.valkyrienskies.clockwork.content.kinetics.CWKineticsSounds;

/**
 * 通用轴：可传递旋转的"万向节"轴。
 *
 * <p>对应旧版 {@code content.kinetics.universal_shaft.UniversalShaftBlock}。
 * 玩法：只能朝 FACING 方向与相邻动力方块对接（和 Create 传动轴一样），
 * 但用「可压缩轴」物品把两根通用轴连起来后，旋转可以跨任意距离 / 任意朝向传递。
 *
 * <p>与旧版的差异：
 * <ul>
 *   <li>旧版 {@code getRenderShape()} 返回 {@code ENTITYBLOCK_ANIMATED}，方块模型完全交给 BER 绘制；
 *       1.21.1 的 Create 6 把动力方块的旋转交给了 Flywheel visual，本项目不注册 visual，
 *       因此这里改用默认的 {@code MODEL} 渲染形状（模型常驻显示），BER 只负责画连接软管，
 *       这样无论 Flywheel 开不开都不会出现"方块消失"或重复绘制。</li>
 *   <li>断开连接从旧版的 {@code use(...)} 换成了 1.21 的 {@code useWithoutItem(...)}（空手潜行右键）。</li>
 * </ul>
 */
public class UniversalShaftBlock extends DirectionalKineticBlock implements IBE<UniversalShaftBlockEntity> {

    public UniversalShaftBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // 与旧版一致：模型/碰撞箱的轴向朝 FACING 的反方向（方块状态 JSON 里 north 用 x:90 的约定）
        return CWKineticsShapes.UNIVERSAL_SHAFT.get(state.getValue(FACING).getOpposite());
    }

    /**
     * 摆放时优先朝向"愿意对接"的邻居。
     *
     * <p>注意这里返回的是 {@code side.getOpposite()}，而 Create 的默认实现返回 {@code side}；
     * {@link DirectionalKineticBlock#getStateForPlacement} 会再取一次 opposite，
     * 所以最终 FACING 指向邻居，与 {@link #hasShaftTowards} 自洽（旧版就是这个写法）。
     */
    @Override
    public @Nullable Direction getPreferredFacing(BlockPlaceContext context) {
        Direction preferred = null;
        for (Direction side : Iterate.directions) {
            BlockPos neighbourPos = context.getClickedPos().relative(side);
            BlockState neighbour = context.getLevel().getBlockState(neighbourPos);
            if (!(neighbour.getBlock() instanceof IRotate rotate)) {
                continue;
            }
            if (!rotate.hasShaftTowards(context.getLevel(), neighbourPos, neighbour, side.getOpposite())) {
                continue;
            }
            if (preferred != null && preferred.getAxis() != side.getAxis()) {
                return null;
            }
            preferred = side.getOpposite();
        }
        return preferred;
    }

    /** 空手潜行右键 = 拆掉连接并返还「可压缩轴」。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof UniversalShaftBlockEntity be) || !be.isLinked()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        be.disconnect();
        level.playSound(null, pos, CWKineticsSounds.HOSE_RELEASE, SoundSource.BLOCKS, 1f, 1f);
        if (!player.isCreative()) {
            player.addItem(be.getConnectionItem());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public Class<UniversalShaftBlockEntity> getBlockEntityClass() {
        return UniversalShaftBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends UniversalShaftBlockEntity> getBlockEntityType() {
        return CWKineticsIndex.UNIVERSAL_SHAFT_BE.get();
    }
}
