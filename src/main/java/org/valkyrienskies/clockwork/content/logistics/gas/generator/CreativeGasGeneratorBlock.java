package org.valkyrienskies.clockwork.content.logistics.gas.generator;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.valkyrienskies.clockwork.content.logistics.gas.CWGasIndex;
import org.valkyrienskies.clockwork.content.logistics.gas.IGasNodeBlock;

/**
 * 创造气源：给调试与创造模式提供无限气体。
 *
 * <p>对应旧版 {@code CreativeGeneratorBlock}（旧版右键打开配置界面选择气体种类/质量/温度）。
 *
 * <p><b>与旧版的差异（重要）</b>：本切片没有移植 GUI（旧版是界面 + 网络包 + 滚轮控件 4 个类），
 * 改为<b>右键循环切换气体种类</b>，输出的目标压力/温度走配置项。玩法等价（选气体 → 无限供应），
 * 交互方式从界面变成了右键。
 */
public class CreativeGasGeneratorBlock extends Block
        implements IBE<CreativeGasGeneratorBlockEntity>, IGasNodeBlock, IWrenchable {

    public CreativeGasGeneratorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Class<CreativeGasGeneratorBlockEntity> getBlockEntityClass() {
        return CreativeGasGeneratorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CreativeGasGeneratorBlockEntity> getBlockEntityType() {
        return CWGasIndex.CREATIVE_GENERATOR_BE.get();
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return cycle(context.getLevel(), context.getClickedPos(), context.getPlayer());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                              BlockHitResult hit) {
        return cycle(level, pos, player);
    }

    private static InteractionResult cycle(Level level, BlockPos pos, Player player) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof CreativeGasGeneratorBlockEntity generator)) {
            return InteractionResult.PASS;
        }
        generator.cycleGas();
        if (player != null) {
            player.displayClientMessage(
                    Component.literal("Gas: " + generator.gasType().displayName()), true);
        }
        AllSoundEvents.WRENCH_ROTATE.playOnServer(level, pos);
        return InteractionResult.SUCCESS;
    }
}
