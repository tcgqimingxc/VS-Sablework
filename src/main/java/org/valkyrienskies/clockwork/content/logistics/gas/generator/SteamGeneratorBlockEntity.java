package org.valkyrienskies.clockwork.content.logistics.gas.generator;

import com.simibubi.create.content.fluids.tank.BoilerData;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.clockwork.config.ClockworkConfig;
import org.valkyrienskies.clockwork.content.logistics.gas.GasNodeBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.GasTypes;

/**
 * 蒸汽提取器方块实体。
 *
 * <p>对应旧版 {@code SteamGeneratorBlockEntity}。每刻（锅炉有热且效率 &gt; 0）：
 * <pre>
 * 产汽量   = steamGeneratorMaxMass · 锅炉效率                [kg/tick]
 * 产汽温度 = 80 · (max(0.25, activeHeat) · 100)^0.34         [K]
 * 压力上限 = (17000000 / 8) · max(1, activeHeat)             [Pa]
 * </pre>
 * 三个公式与常量都取自旧版（0.1 kg/tick 与 17000000/8 在旧版是硬编码）。
 *
 * <p>与旧版的差异：旧版用 {@code WeakReference} 缓存锅炉控制器；这里每次现查背面方块实体
 * （一次查询开销可忽略，也不会留下悬空引用）。
 */
public class SteamGeneratorBlockEntity extends GasNodeBlockEntity {

    /** 旧版硬编码：每刻最大产汽质量 [kg/tick]。 */
    private static final double MAX_MASS_PER_TICK = 0.1;
    /** 旧版硬编码：每级热量允许的压力上限 [Pa]（17000000 / 8）。 */
    private static final double MAX_PRESSURE_PER_LEVEL = 17000000.0 / 8.0;

    public SteamGeneratorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, 1.0);
    }

    /** 背面（贴锅炉的一侧）的锅炉控制器，没有则返回 null。 */
    @Nullable
    private FluidTankBlockEntity getBoilerController() {
        if (level == null) {
            return null;
        }
        Direction facing = SteamGeneratorBlock.getFacing(getBlockState());
        if (!(level.getBlockEntity(getBlockPos().relative(facing.getOpposite()))
                instanceof FluidTankBlockEntity tank)) {
            return null;
        }
        return tank.getControllerBE();
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }
        FluidTankBlockEntity boiler = getBoilerController();
        if (boiler == null) {
            return;
        }
        BoilerData data = boiler.boiler;
        float efficiency = net.minecraft.util.Mth.clamp(data.getEngineEfficiency(boiler.getTotalTankSize()), 0.0F, 1.0F);
        if (efficiency <= 0 || (data.activeHeat == 0 && !data.passiveHeat)) {
            return;
        }
        double limit = MAX_PRESSURE_PER_LEVEL * Math.max(1, data.activeHeat);
        if (gasMix().pressure(gasCapacityKg()) > limit) {
            return;
        }
        double temperature = 80.0 * Math.pow(Math.max(0.25, data.activeHeat) * 100.0, 0.34);
        gasMix().setTemperature(temperature);
        gasMix().addMass(GasTypes.STEAM, ClockworkConfig.server().steamGeneratorMaxMass.get() * efficiency);
        gasChanged();
    }
}
