package org.valkyrienskies.clockwork.content.physicalities.slicker;

import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.WrenchableDirectionalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.valkyrienskies.clockwork.content.kinetics.CWKineticsIndex;

/**
 * 减摩方块（Slicker）：像 Create 的粘性方块一样可以伸出 / 收回，伸出后会把正前方的
 * 结构"吸"在自己身上（旧版用 VS2 关节，这里用 Sable 的固定约束，见 {@link SlickerBlockEntity}）。
 *
 * <p>对应旧版 {@code content.contraptions.phys.slicker.SlickerBlock}（旧版放在 contraptions 包下，
 * 按本项目的分工移到 physicalities 包）。
 *
 * <p>红石逻辑逐条照抄 Create 的 {@code StickerBlock}（旧版也是照抄它，连 {@code state.cycle} 的
 * 调用顺序都一样）：<b>每个上升沿翻转一次 EXTENDED</b>，下降沿只清 POWERED。
 * 也就是说给一次红石脉冲＝伸出并吸附，再给一次脉冲＝收回并释放。
 */
public class SlickerBlock extends WrenchableDirectionalBlock implements IBE<SlickerBlockEntity> {

    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    public static final BooleanProperty EXTENDED = BooleanProperty.create("extended");

    public SlickerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(POWERED, false)
                .setValue(EXTENDED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(POWERED, EXTENDED));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean shouldPower = context.getLevel().hasNeighborSignal(context.getClickedPos());
        return super.getStateForPlacement(context).setValue(POWERED, shouldPower);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
                                boolean isMoving) {
        if (level.isClientSide) {
            return;
        }
        boolean previouslyPowered = state.getValue(POWERED);
        if (previouslyPowered == level.hasNeighborSignal(pos)) {
            return;
        }
        BlockState updated = state.cycle(POWERED);
        if (updated.getValue(POWERED)) {
            updated = updated.cycle(EXTENDED);
        }
        level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
    }

    @Override
    public Class<SlickerBlockEntity> getBlockEntityClass() {
        return SlickerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SlickerBlockEntity> getBlockEntityType() {
        return CWKineticsIndex.SLICKER_BE.get();
    }
}
