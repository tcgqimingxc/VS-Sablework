package org.valkyrienskies.clockwork.content.logistics.gas.generator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.clockwork.config.ClockworkConfig;
import org.valkyrienskies.clockwork.content.logistics.gas.GasNodeBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.GasPhysics;
import org.valkyrienskies.clockwork.content.logistics.gas.GasType;
import org.valkyrienskies.clockwork.content.logistics.gas.GasTypes;

/**
 * 创造气源方块实体。
 *
 * <p>对应旧版 {@code CreativeGeneratorBlockEntity}：旧版每刻把"配置质量 - 当前质量"的差额补进节点，
 * 直到达到配置的质量；这里把同一思路换成"补到目标压力"：
 * <pre>补气量 = 目标压力对应的质量 - 当前质量</pre>
 * 由 {@code P = P₀·(m/m₀)·(T/T₀)} 反解，温度同时被拉到配置的目标温度。
 *
 * <p>与旧版差异：气体种类用右键循环（旧版是 GUI），目标压力/温度来自配置项
 * （{@code creativeGasGeneratorPressure} / {@code creativeGasGeneratorTemperature}）。
 */
public class CreativeGasGeneratorBlockEntity extends GasNodeBlockEntity {

    private GasType gasType = GasTypes.AIR;

    public CreativeGasGeneratorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, 1.0);
    }

    public GasType gasType() {
        return gasType;
    }

    /** 切换到下一种气体并同步，返回新的气体种类。 */
    public GasType cycleGas() {
        int next = (gasType.index() + 1) % GasTypes.count();
        gasType = GasTypes.byIndex(next);
        sendData();
        gasChanged();
        return gasType;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }

        double targetPressure = ClockworkConfig.server().creativeGasGeneratorPressure.get();
        double targetTemperature = ClockworkConfig.server().creativeGasGeneratorTemperature.get();

        gasMix().setTemperature(targetTemperature);
        double ambientPressure = GasPhysics.ambientPressure();
        double ambientTemperature = GasPhysics.ambientTemperature();
        // 由 P = P₀·(m/m₀)·(T/T₀) 反解目标质量
        double targetMass = gasCapacityKg() * (targetPressure / ambientPressure)
                * (ambientTemperature / targetTemperature);
        double current = gasMix().massOf(gasType);
        if (current < targetMass) {
            gasMix().addMass(gasType, targetMass - current);
            gasChanged();
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        tag.putString("GasType", gasType.id().toString());
        super.write(tag, provider, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        GasType loaded = GasTypes.byId(ResourceLocation.tryParse(tag.getString("GasType")));
        if (loaded != null) {
            gasType = loaded;
        }
    }
}
