package org.valkyrienskies.clockwork.content.physicalities.goo;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 黏液块的方块实体。
 *
 * <p>对应旧版 {@code content.physicalities.goo.GooBlockEntity}：一个没有任何行为的
 * {@link SmartBlockEntity}，只为"将来要加东西"占位（旧版的渲染器也是空实现）。
 * 保留它是因为方块状态与资源都已经按"有方块实体"的形态存在，删掉反而会让旧存档报错。
 */
public class GooBlockEntity extends SmartBlockEntity {

    public GooBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }
}
