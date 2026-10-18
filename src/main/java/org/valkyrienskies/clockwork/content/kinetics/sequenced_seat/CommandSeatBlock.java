package org.valkyrienskies.clockwork.content.kinetics.sequenced_seat;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.valkyrienskies.clockwork.content.kinetics.CWKineticsIndex;
import org.valkyrienskies.clockwork.content.kinetics.sequenced_seat.client.CommandSeatClient;

/**
 * 命令座椅方块（旧版 {@code SequencedSeatBlock.kt}）。
 *
 * <p>玩法：从下方接入旋翼动力，四个水平面各接一根输出轴；坐在椅子上按 WASD / 空格
 * 就能按"规则表"分别驱动这四根轴（默认规则 = 按住哪个方向键，对应侧面就以 1 倍速输出）。
 * 潜行右键打开规则编辑界面。
 *
 * <p>与旧版差异：
 * <ul>
 *   <li>1.21 把 {@code use(...)} 拆成 {@code useItemOn} / {@code useWithoutItem}，
 *       两者的判定逻辑完全一样（见 {@link #interact}），只是入口不同。</li>
 *   <li>旧版界面走 Create 的 {@code AbstractSimiScreen} + 自定义控件；本移植按
 *       Simulated 的 {@code NameplateScreen} 写法改用纯 {@code Screen} + 原版控件
 *       （见 {@code client/CommandSeatScreen}），因此这里只需要在客户端打开它。</li>
 * </ul>
 *
 * <p><b>朝向属性（不要再加一个 FACING）：</b>旧版 {@code SequencedSeatBlock.kt:27} 就是
 * {@code HorizontalKineticBlock}，只注册 4 向的 {@code HORIZONTAL_FACING}（属性名
 * {@code facing}，合法值 north/south/east/west）；{@code blockstates/command_seat.json}
 * 也正好是这 4 个变体。1.21 的 Create {@code HorizontalKineticBlock#createBlockStateDefinition}
 * 已经 {@code builder.add(HORIZONTAL_FACING)}，本类<b>不能</b>再 add 6 向的
 * {@code BlockStateProperties.FACING}：两者属性名都叫 {@code facing}，但取值集合不同
 * （4 向 vs 6 向），在同一个 StateDefinition 里无法共存。实机日志里出现
 * “Cannot get property DirectionProperty{name=facing, ... up, down}”不是本方块漏注册，
 * 而是有代码把本方块的 state 交给了另一个需要 6 向 FACING 的方块（见
 * {@link #useItemOn} 的注释）。
 */
public class CommandSeatBlock extends HorizontalKineticBlock implements IBE<CommandSeatBlockEntity> {

    public CommandSeatBlock(final Properties properties) {
        super(properties);
    }

    @Override
    public BlockState getStateForPlacement(final BlockPlaceContext context) {
        final Direction preferred = this.getPreferredHorizontalFacing(context);
        if (preferred != null && (context.getPlayer() == null || !context.getPlayer().isShiftKeyDown())) {
            return this.defaultBlockState().setValue(HORIZONTAL_FACING, preferred);
        }
        return this.defaultBlockState().setValue(HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public boolean hasShaftTowards(final LevelReader level, final BlockPos pos, final BlockState state,
                                   final Direction face) {
        return face != Direction.UP;
    }

    @Override
    public Direction.Axis getRotationAxis(final BlockState state) {
        return Direction.Axis.Y;
    }

    @Override
    protected ItemInteractionResult useItemOn(final ItemStack stack, final BlockState state, final Level level,
                                              final BlockPos pos, final Player player, final InteractionHand hand,
                                              final BlockHitResult hitResult) {
        // 扳手留给 Create 的滚轮/拆卸逻辑
        if (AllItems.WRENCH.isIn(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // 手里拿着“能接到这一面的动力方块”时让位给放置逻辑（旧版同规则）。
        //
        // 这里必须调用本方块自己的 hasShaftTowards，绝不能写成 kinetic.hasShaftTowards(...)：
        // 1.20.1 的 SequencedSeatBlock.kt:48 没有限定接收者，Kotlin 解析到的是座椅自己
        // （face != UP），不是手里那个方块。1.21 移植时误改成 kinetic.…，等于把
        // command_seat 的 BlockState 交给另一个方块的方法；Create 的 CreativeMotorBlock
        // 会从这个 state 读 6 向 FACING，而 command_seat 只有 4 向 HORIZONTAL_FACING
        // （两者属性名都叫 facing，但不是同一个 Property），于是 2026-10-18 实机抛
        // IllegalArgumentException: Cannot get property DirectionProperty{name=facing,
        // values=[north, east, south, west, up, down]} as it does not exist in
        // Block{vs_clockwork:command_seat}。
        //
        // 修法不是给 command_seat 补 6 向 FACING（同名属性不能与 4 向的共存），而是恢复旧版
        // 语义：只问“本座椅在这一面有没有轴”。这样无论手里拿的是电机、手摇曲柄还是别的
        // DirectionalKineticBlock，都不会再拿错 state 去碰别人的属性。
        if (stack.getItem() instanceof final BlockItem blockItem
                && blockItem.getBlock() instanceof KineticBlock
                && this.hasShaftTowards(level, pos, state, hitResult.getDirection())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return this.interact(state, level, pos, player);
    }

    @Override
    protected InteractionResult useWithoutItem(final BlockState state, final Level level, final BlockPos pos,
                                               final Player player, final BlockHitResult hitResult) {
        return this.interact(state, level, pos, player) == ItemInteractionResult.SUCCESS
                ? InteractionResult.SUCCESS
                : InteractionResult.PASS;
    }

    /** 潜行 = 打开规则界面，否则坐下（旧版 {@code use} 的后半段）。 */
    private ItemInteractionResult interact(final BlockState state, final Level level, final BlockPos pos,
                                           final Player player) {
        if (player.isShiftKeyDown()) {
            if (level.isClientSide && level.getBlockEntity(pos) instanceof final CommandSeatBlockEntity seat) {
                CommandSeatClient.openScreen(seat);
            }
            return ItemInteractionResult.SUCCESS;
        }
        sitDown(level, pos, player);
        return ItemInteractionResult.SUCCESS;
    }

    /** 生成座椅实体并让玩家骑上去（旧版 {@code SequencedSeatBlock.sitDown}）。 */
    public static void sitDown(final Level level, final BlockPos pos, final Entity rider) {
        if (level.isClientSide) {
            return;
        }
        final CommandSeatEntity seat = CWKineticsIndex.COMMAND_SEAT_ENTITY.get().create(level);
        if (seat == null) {
            return;
        }
        seat.setPos(pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5);
        level.addFreshEntity(seat);
        rider.startRiding(seat, true);
        if (rider instanceof final TamableAnimal animal) {
            animal.setInSittingPose(true);
        }
    }

    @Override
    public Class<CommandSeatBlockEntity> getBlockEntityClass() {
        return CommandSeatBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CommandSeatBlockEntity> getBlockEntityType() {
        return CWKineticsIndex.COMMAND_SEAT_BE.get();
    }
}
