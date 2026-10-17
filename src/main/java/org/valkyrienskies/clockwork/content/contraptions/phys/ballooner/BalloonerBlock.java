package org.valkyrienskies.clockwork.content.contraptions.phys.ballooner;

import com.simibubi.create.foundation.block.IBE;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.valkyrienskies.clockwork.content.contraptions.CWContraptionsIndex;

/**
 * 热气球浮力方块（旧版 {@code ballooner}）。
 *
 * <p>把它装在一层密封外壳内部，外壳包起来的空气体积就会变成浮力
 * （见 {@link BalloonerBlockEntity} 的体积扫描）。
 */
public class BalloonerBlock extends Block implements IBE<BalloonerBlockEntity> {

    public BalloonerBlock(final Properties properties) {
        super(properties);
    }

    @Override
    public Class<BalloonerBlockEntity> getBlockEntityClass() {
        return BalloonerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends BalloonerBlockEntity> getBlockEntityType() {
        return CWContraptionsIndex.BALLOONER_BE.get();
    }
}
