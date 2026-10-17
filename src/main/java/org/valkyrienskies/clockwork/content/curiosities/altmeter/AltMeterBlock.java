package org.valkyrienskies.clockwork.content.curiosities.altmeter;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.clockwork.content.curiosities.CWInstrumentsIndex;
import org.valkyrienskies.clockwork.content.curiosities.tools.screwdriver.IScrewdrivable;

/**
 * 高度计（Altimeter）。
 *
 * <p>对应旧版 {@code curiosities/altmeter/AltMeterBlock.kt}：一个"到达设定高度就输出红石"的仪表。
 * 自身没有朝向属性（资源文件里只有单一变体），六个面都输出同一个信号强度（写在 {@code power} 属性上）。
 *
 * <p>配置方式（旧版是客户端 GUI 屏幕 + 网络包）：
 * <ul>
 *   <li><b>潜行 + 空手右键</b>：把目标高度设为"玩家/方块当前所在的世界高度"（旧版 GUI 里的 Trigger Height）；</li>
 *   <li><b>空手右键</b>：在动作栏显示当前高度 / 目标高度 / 触发方向 / 灵敏度；</li>
 *   <li><b>螺丝刀右键</b>：循环触发方向 UP → DOWN → BOTH；</li>
 *   <li><b>螺丝刀潜行右键</b>：循环灵敏度 1 → 2 → 4 → 8 → 16。</li>
 * </ul>
 * 差异原因：旧版的配置界面（{@code AltMeterScreen} + {@code UpdateAltMeterPacket}）属于客户端/网络切片，
 * 本切片不引入 GUI 与网络包（也避免与其它切片抢注册），因此用方块自带的交互 + Create 的螺丝刀机制替代，
 * 玩家侧能完成的配置完全等价。
 */
public class AltMeterBlock extends Block implements IBE<AltMeterBlockEntity>, IScrewdrivable {

    public static final MapCodec<AltMeterBlock> CODEC = simpleCodec(AltMeterBlock::new);

    /** 与旧版一致：复用原版 {@code power} 属性承载信号强度。 */
    public static final IntegerProperty POWER = BlockStateProperties.POWER;

    public AltMeterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POWER, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWER);
    }

    @Override
    public Class<AltMeterBlockEntity> getBlockEntityClass() {
        return AltMeterBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends AltMeterBlockEntity> getBlockEntityType() {
        return CWInstrumentsIndex.ALT_METER_BE.get();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        // 旧版行为：放置时通知六个方向的邻居，让红石立刻刷新。
        for (Direction direction : Direction.values()) {
            level.updateNeighborsAt(pos.relative(direction), this);
        }
        super.onPlace(state, level, pos, oldState, isMoving);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!isMoving) {
            for (Direction direction : Direction.values()) {
                level.updateNeighborsAt(pos.relative(direction), this);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    protected int getSignal(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWER);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               net.minecraft.world.phys.BlockHitResult hitResult) {
        if (!level.isClientSide) {
            withBlockEntityDo(level, pos, be -> {
                if (player.isShiftKeyDown()) {
                    int height = be.getCurrentHeight();
                    be.setTriggerHeight(height);
                    AllSoundEvents.WRENCH_ROTATE.playOnServer(level, pos);
                    player.displayClientMessage(Component.literal("Altimeter target set to Y=" + height), true);
                } else {
                    player.displayClientMessage(Component.literal("Altimeter: Y=" + be.getCurrentHeight()
                            + " target=" + be.getTriggerHeight()
                            + " dir=" + be.getTriggerDirection()
                            + " sensitivity=" + be.getTriggerSensitivity()), true);
                }
            });
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onScrewdrived(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        withBlockEntityDo(level, context.getClickedPos(), be -> {
            be.setTriggerDirection(be.getTriggerDirection().next());
            sendMessage(context.getPlayer(), "Altimeter direction: " + be.getTriggerDirection());
        });
        playScrewSound(level, context.getClickedPos());
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onSneakScrewdrived(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        withBlockEntityDo(level, context.getClickedPos(), be -> {
            be.setTriggerSensitivity(be.getTriggerSensitivity() >= 16 ? 1 : be.getTriggerSensitivity() * 2);
            sendMessage(context.getPlayer(), "Altimeter sensitivity: " + be.getTriggerSensitivity());
        });
        playScrewSound(level, context.getClickedPos());
        return InteractionResult.SUCCESS;
    }

    private static void sendMessage(@Nullable Player player, String message) {
        if (player != null) {
            player.displayClientMessage(Component.literal(message), true);
        }
    }
}
