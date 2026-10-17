package org.valkyrienskies.clockwork.content.curiosities.tools.screwdriver;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.Create;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * "可被螺丝刀调节"的方块契约。
 *
 * <p>对应旧版 {@code tools/screwdriver/IScrewdrivable.kt}，方法名与语义保持一致：
 * <ul>
 *   <li>{@link #onScrewdrived} —— 普通右键；</li>
 *   <li>{@link #onSneakScrewdrived} —— 潜行右键（旧版用于"反向/拆开"一类操作）。</li>
 * </ul>
 *
 * <p>移植差异：旧版两个方法直接收 {@link UseOnContext}，这里沿用（1.21.1 仍然存在该类型），
 * 因此方块侧能拿到点击面、手持物等全部信息，不需要额外的适配层。
 */
public interface IScrewdrivable {

    InteractionResult onScrewdrived(BlockState state, UseOnContext context);

    InteractionResult onSneakScrewdrived(BlockState state, UseOnContext context);

    default void playUnscrewSound(Level level, BlockPos pos) {
        AllSoundEvents.WRENCH_REMOVE.playOnServer(level, pos, 1f, Create.RANDOM.nextFloat() * 0.5f + 0.5f);
    }

    default void playScrewSound(Level level, BlockPos pos) {
        AllSoundEvents.WRENCH_ROTATE.playOnServer(level, pos, 1f, Create.RANDOM.nextFloat() + 0.5f);
    }
}
