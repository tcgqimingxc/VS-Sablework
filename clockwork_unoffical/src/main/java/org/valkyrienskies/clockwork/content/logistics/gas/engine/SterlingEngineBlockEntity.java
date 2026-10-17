package org.valkyrienskies.clockwork.content.logistics.gas.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.clockwork.config.ClockworkConfig;
import org.valkyrienskies.clockwork.content.logistics.gas.GasKineticNodeBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.GasNodeTooltip;

import java.util.List;

/**
 * 斯特林机方块实体：热 → 转速的转换器。
 *
 * <p>对应旧版 {@code SterlingEngineBlockEntity}：
 * <ul>
 *   <li>效率 = 温度效率（{@link HeatEngineLogic#temperatureEfficiency}）</li>
 *   <li>产生转速 = {@code 16 · 转速倍率}，方向由 {@code FACING} 决定</li>
 *   <li>应力容量 = {@code floor(效率 · sterlingEngineStressCapacity) / 转速}</li>
 *   <li>每刻按 {@code 效率 · sterlingEngineMaxHeatLoss} 从网络里扣热（[J/tick]）</li>
 * </ul>
 *
 * <p>与旧版差异：旧版自己实现了 {@code applyNewSpeed} 等一整套生成逻辑（Create 1.20.1 的写法）；
 * 1.21.1 Create 6.0.10 已经在 {@code GeneratingKineticBlockEntity} 里提供了
 * {@code updateGeneratedRotation()}，这里直接复用（基类见 {@link GasKineticNodeBlockEntity}）。
 */
public class SterlingEngineBlockEntity extends GasKineticNodeBlockEntity {

    private float efficiency;

    public SterlingEngineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, 1.0);
    }

    public float efficiency() {
        return efficiency;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }

        // 每刻扣热：效率越高、产出的动能越多，热量消耗越大
        double heatLoss = efficiency * ClockworkConfig.server().sterlingEngineMaxHeatLoss.get();
        if (heatLoss > 0 && !gasMix().isEmpty()) {
            gasMix().addHeat(-Math.min(heatLoss, gasMix().thermalEnergy()));
            gasChanged();
        }
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (level == null || level.isClientSide) {
            return;
        }
        float newEfficiency = HeatEngineLogic.temperatureEfficiency(
                gasMix().temperature(),
                ClockworkConfig.server().sterlingEngineTemperatureIncrement.get());
        if (newEfficiency != efficiency) {
            efficiency = newEfficiency;
            updateGeneratedRotation();
            sendData();
        }
    }

    @Override
    public float getGeneratedSpeed() {
        if (efficiency <= 0) {
            return 0;
        }
        Direction facing = getBlockState().getValue(SterlingEngineBlock.FACING);
        return convertToDirection(1.0F, facing) * HeatEngineLogic.BASE_SPEED * HeatEngineLogic.speedModifier(efficiency);
    }

    @Override
    public float calculateAddedStressCapacity() {
        float capacity = HeatEngineLogic.stressCapacity(efficiency,
                ClockworkConfig.server().sterlingEngineStressCapacity.get());
        lastCapacityProvided = capacity;
        return capacity;
    }

    @Override
    public boolean gasConnected(Direction dir) {
        return getBlockState().getBlock() instanceof SterlingEngineBlock engine
                && engine.gasConnectable(getBlockState(), dir);
    }

    @Override
    public boolean addToGoggleTooltip(List<net.minecraft.network.chat.Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        GasNodeTooltip.addEfficiency(tooltip, efficiency, gasMix().temperature(),
                ClockworkConfig.server().sterlingEngineTemperatureIncrement.get());
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        tag.putFloat("Efficiency", efficiency);
        super.write(tag, provider, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        efficiency = tag.getFloat("Efficiency");
    }
}
