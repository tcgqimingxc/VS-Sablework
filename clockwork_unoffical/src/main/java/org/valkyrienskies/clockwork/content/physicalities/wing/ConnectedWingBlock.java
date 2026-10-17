package org.valkyrienskies.clockwork.content.physicalities.wing;

import com.simibubi.create.foundation.block.IBE;
import java.util.List;
import java.util.function.Predicate;

import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.valkyrienskies.clockwork.content.kinetics.CWKineticsIndex;
import org.valkyrienskies.clockwork.content.kinetics.CWKineticsShapes;
import org.valkyrienskies.clockwork.platform.CWWingForces;

import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * 机翼 / 襟翼的共用基类：一个"有朝向的平面方块"，会记录同一平面内的相邻同类方块。
 *
 * <p>对应旧版 {@code util.blocktype.ConnectedWingAlike}（骨架）+ {@code util.blocktype.DyedWing}（染色）
 * 两个类。旧版把这两块拆开是因为别的功能也要用；本移植只有机翼和襟翼需要，所以合成一个基类，
 * 并由 {@link WingBlock} / {@link FlapBlock} 各写几行差异（气动参数不同）。
 *
 * <p>气动受力通过实现 Sable 的 {@link BlockSubLevelLiftProvider} 完成：
 * Sable 在物理子步里会把子空间下的升力方块按连通分组成 {@code LiftProviderGroup}，
 * 再逐个调用 {@code sable$contributeLiftAndDrag}，我们把计算委托给
 * {@link org.valkyrienskies.clockwork.platform.CWWingForces}（与旧版 VS2 的叶素/薄翼模型一致）。
 *
 * <p>与旧版的差异：
 * <ul>
 *   <li>旧版 1.20.1 的 {@code use(...)} 在 1.21 拆成了 {@code useItemOn(...)}（手持物品）；
 *       染料与"同平面延伸"都发生在 {@code useItemOn} 里。</li>
 *   <li>邻居变化时只在连接状态真的改变才写回方块状态（旧版无条件 {@code setBlockAndUpdate}），
 *       减少无谓的方块更新。</li>
 *   <li>颜色混合用自己的 RGB 线性插值，不再依赖 Create 的 {@code Color.mixColors}
 *       （那个函数的 alpha 语义在 1.21 里不明确，直接按 24 位 RGB 处理更稳）。</li>
 *   <li>受力不再是每刻遍历船上的方块自行累加，而是交给 Sable 的升力提供者管线（自动按连通域分组）。</li>
 * </ul>
 */
public abstract class ConnectedWingBlock extends Block
        implements IBE<WingColorBlockEntity>, BlockSubLevelLiftProvider {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;

    private static final int PLACEMENT_HELPER_ID = PlacementHelpers.register(new WingPlacementHelper());

    // ---- 气动参数：机翼与襟翼共用，数值来源旧版 getWing()，供物理切片读取 ----

    /** 升力系数（旧版 wingPower）。 */
    public static final double WING_POWER = 150.0;
    /** 阻力系数（旧版 wingDrag）。 */
    public static final double WING_DRAG = 150.0;
    /** 结构承受的破坏力上限（旧版 wingBreakingForce）。 */
    public static final double WING_BREAKING_FORCE = 10.0;

    protected ConnectedWingBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.UP)
                .setValue(NORTH, false).setValue(SOUTH, false)
                .setValue(EAST, false).setValue(WEST, false)
                .setValue(UP, false).setValue(DOWN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, NORTH, SOUTH, EAST, WEST, UP, DOWN);
    }

    // ------------------------------------------------------------------ 形状与朝向

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CWKineticsShapes.WING.get(state.getValue(FACING).getAxis());
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState()
                .setValue(FACING, context.getNearestLookingDirection().getOpposite());
        return getUpdatedState(state, context.getLevel(), context.getClickedPos());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        if (rotation != Rotation.CLOCKWISE_90 && rotation != Rotation.COUNTERCLOCKWISE_90) {
            return state;
        }
        return switch (state.getValue(FACING)) {
            case NORTH -> state.setValue(FACING, Direction.EAST);
            case EAST -> state.setValue(FACING, Direction.UP);
            case UP -> state.setValue(FACING, Direction.NORTH);
            default -> state;
        };
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (level.isClientSide) {
            return;
        }
        BlockState updated = getUpdatedState(state, level, pos);
        if (updated != state) {
            level.setBlockAndUpdate(pos, updated);
        }
    }

    /**
     * 重新计算六个面的连接标记，并把 FACING 归一化到该轴的正方向。
     *
     * <p>归一化的意义：{@code facing=north} 与 {@code facing=south} 是同一个平面，
     * 归一化后能少一半方块状态组合（旧版同样处理）。
     */
    public BlockState getUpdatedState(BlockState state, Level level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        Axis axis = facing.getAxis();
        for (Direction side : Iterate.directions) {
            BooleanProperty property = propertyFor(side);
            boolean connected = side.getAxis() != axis && isSameKind(level.getBlockState(pos.relative(side)), facing);
            state = state.setValue(property, connected);
        }
        return state.setValue(FACING, fromAxis(axis));
    }

    /** 只有同一种机翼（同为机翼或同为襟翼）且朝向一致才算相连。 */
    protected boolean isSameKind(BlockState other, Direction facing) {
        return other.getBlock().getClass() == getClass() && other.getValue(FACING) == facing;
    }

    /**
     * 升力方向（世界坐标轴单位向量，一律取<b>正</b>方向）。
     *
     * <p>交给物理切片的契约数据，数值来自旧版 {@code getWing()}：
     * FACING 为东西 → X 轴、南北 → Z 轴、上下 → Y 轴。本切片自己不施力。
     */
    public Vec3 liftAxis(BlockState state) {
        return switch (state.getValue(FACING).getAxis()) {
            case X -> new Vec3(1, 0, 0);
            case Y -> new Vec3(0, 1, 0);
            case Z -> new Vec3(0, 0, 1);
        };
    }

    private static Direction fromAxis(Axis axis) {
        return switch (axis) {
            case X -> Direction.EAST;
            case Y -> Direction.UP;
            case Z -> Direction.NORTH;
        };
    }

    private static BooleanProperty propertyFor(Direction side) {
        return switch (side) {
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            case UP -> UP;
            case DOWN -> DOWN;
        };
    }

    // ------------------------------------------------------------------ 交互：染色 / 同平面延伸

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        // 1) 染料：第一次染色直接取染料色，之后再染是 50% 混合
        if (stack.getItem() instanceof DyeItem dye
                && level.getBlockEntity(pos) instanceof WingColorBlockEntity colorBe) {
            int dyeColor = dye.getDyeColor().getTextColor();
            int current = colorBe.getColor();
            if (current != dyeColor) {
                colorBe.setColor(current == WingColorBlockEntity.NO_COLOR ? dyeColor : blend(current, dyeColor, 0.5f));
                if (!level.isClientSide && !player.isCreative()) {
                    stack.shrink(1);
                }
                return ItemInteractionResult.SUCCESS;
            }
        }

        // 2) 手持机翼/襟翼右键：沿同一平面继续铺
        if (player.isShiftKeyDown() || !player.mayBuild()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        IPlacementHelper helper = PlacementHelpers.get(PLACEMENT_HELPER_ID);
        if (!helper.matchesItem(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        PlacementOffset offset = helper.getOffset(player, level, state, pos, hitResult);
        if (!offset.isSuccessful()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // Create 6 的 placeInWorld 返回的是 ItemInteractionResult（1.20.1 是 InteractionResult）
        ItemInteractionResult placed = offset.placeInWorld(level, (BlockItem) stack.getItem(), player, hand, hitResult);
        if (!placed.consumesAction()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // 新放下的那一片继承手上的颜色
        if (level.getBlockEntity(offset.getBlockPos()) instanceof WingColorBlockEntity newBe) {
            newBe.setColor(DyedWingBlockItem.getColor(stack));
        }
        return ItemInteractionResult.SUCCESS;
    }

    /** 24 位 RGB 线性插值（{@code factor} = 0 取 a，1 取 b）。 */
    private static int blend(int a, int b, float factor) {
        int r = (int) (((a >> 16) & 0xFF) + ((((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * factor));
        int g = (int) (((a >> 8) & 0xFF) + ((((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * factor));
        int bl = (int) ((a & 0xFF) + (((b & 0xFF) - (a & 0xFF)) * factor));
        return (r << 16) | (g << 8) | bl;
    }

    // ------------------------------------------------------------------ 掉落 / 复制

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof WingColorBlockEntity colorBe && colorBe.getColor() != WingColorBlockEntity.NO_COLOR) {
            for (ItemStack stack : drops) {
                if (stack.getItem() instanceof DyedWingBlockItem) {
                    DyedWingBlockItem.setColor(stack, colorBe.getColor());
                }
            }
        }
        return drops;
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos,
                                       Player player) {
        // 用 NeoForge 的"更精确版本"（带 player / 命中结果），避免覆盖原版那个已过时的重载
        ItemStack stack = new ItemStack(this);
        if (level.getBlockEntity(pos) instanceof WingColorBlockEntity colorBe) {
            DyedWingBlockItem.setColor(stack, colorBe.getColor());
        }
        return stack;
    }

    // ------------------------------------------------------------------ 方块实体

    @Override
    public Class<WingColorBlockEntity> getBlockEntityClass() {
        return WingColorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends WingColorBlockEntity> getBlockEntityType() {
        return CWKineticsIndex.WING_COLOR_BE.get();
    }

    // ===================== Sable 升力 / 阻力提供者 =====================

    /** 翼面法向就是方块的朝向（Sable 用它判断"迎风面"）。 */
    @Override
    public Direction sable$getNormal(BlockState state) {
        return state.getValue(FACING);
    }

    /**
     * 每个物理子步被 Sable 调用一次，把升力/阻力累加进冲量累加器。
     *
     * <p>攻角由子类给出（机翼 = 10° 弯度偏置，襟翼 = 0），
     * 其余气动计算统一在 {@link CWWingForces} 里（薄翼模型，与旧版同系数）。
     */
    @Override
    public void sable$contributeLiftAndDrag(LiftProviderContext ctx,
                                            ServerSubLevel subLevel,
                                            Pose3d localPose,
                                            double timeStep,
                                            Vector3dc linearVelocity,
                                            Vector3dc angularVelocity,
                                            Vector3d linearImpulse,
                                            Vector3d angularImpulse,
                                            LiftProviderGroup group) {
        CWWingForces.contribute(ctx, subLevel, localPose, timeStep, linearVelocity, angularVelocity,
                linearImpulse, angularImpulse, getAngleOfAttackRadians(ctx.state()));
    }

    /** 该翼面在给定方块状态下的攻角 [rad]（子类实现：机翼有弯度，襟翼没有）。 */
    protected abstract double getAngleOfAttackRadians(BlockState state);

    /** 沿同一平面把下一片贴在相邻空格上（旧版 {@code ConnectedWingAlike.PlacementHelper}）。 */
    private static final class WingPlacementHelper implements IPlacementHelper {
        @Override
        public Predicate<ItemStack> getItemPredicate() {
            return stack -> stack.getItem() instanceof DyedWingBlockItem;
        }

        @Override
        public Predicate<BlockState> getStatePredicate() {
            return state -> state.getBlock() instanceof ConnectedWingBlock;
        }

        @Override
        public PlacementOffset getOffset(Player player, Level level, BlockState state, BlockPos pos,
                                         BlockHitResult ray) {
            List<Direction> directions = IPlacementHelper.orderedByDistanceExceptAxis(pos, ray.getLocation(),
                    state.getValue(FACING).getAxis(),
                    dir -> level.getBlockState(pos.relative(dir)).canBeReplaced());
            if (directions.isEmpty()) {
                return PlacementOffset.fail();
            }
            Direction side = directions.get(0);
            return PlacementOffset.success(pos.relative(side), s -> s.setValue(FACING, state.getValue(FACING)));
        }
    }
}
