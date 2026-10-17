package org.valkyrienskies.clockwork.integration.cc;

import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 外设基类：只提供 {@link IPeripheral} 的公共样板（equals / 持有方块实体）。
 *
 * <p>对应旧版 {@code integration/cc/*.kt} 里每个外设各自写一遍的
 * {@code override fun equals(p0: IPeripheral?) = be.blockPos == (p0 as? XxxPeripheral)?.be?.blockPos}。
 * 移植后统一到基类：<b>同一个方块位置 + 同一个外设类型 = 同一个外设</b>，
 * 这是 CC 判断"外设是否被替换"的依据（旧版语义完全一致）。
 *
 * @param <T> 该外设对应的方块实体类型
 */
public abstract class ClockworkPeripheral<T extends BlockEntity> implements IPeripheral {

    protected final T blockEntity;

    protected ClockworkPeripheral(final T blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public boolean equals(final IPeripheral other) {
        return other != null
                && other.getClass() == this.getClass()
                && ((ClockworkPeripheral<?>) other).blockEntity.getBlockPos().equals(this.blockEntity.getBlockPos());
    }

    @Override
    public Object getTarget() {
        return this.blockEntity;
    }
}
