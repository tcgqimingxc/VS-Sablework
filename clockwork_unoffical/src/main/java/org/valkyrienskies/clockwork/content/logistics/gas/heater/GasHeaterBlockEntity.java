package org.valkyrienskies.clockwork.content.logistics.gas.heater;

import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.clockwork.config.ClockworkConfig;
import org.valkyrienskies.clockwork.content.logistics.gas.GasNodeBlockEntity;

/**
 * 气体加热器方块实体。
 *
 * <p>对应旧版 {@code GasHeaterBlockEntity}：读节点温度 → 映射到 Create 的热等级
 * （SMouldering/KINDLED/SEETHING，阈值来自配置 500/800/1300 K）→ 写进方块状态，
 * 并按 {@code 热等级序号 × 100 J} 扣掉网络里的热能（旧版 {@code heatLossPerLevel = 100}）。
 *
 * <p>与旧版差异：旧版有护目镜里"下一档还差多少度"的提示（{@code EngineGoggleTooltip} 风格），
 * 本移植只保留基础的气体信息 + 当前热等级。
 */
public class GasHeaterBlockEntity extends GasNodeBlockEntity {

    /** 旧版硬编码：每档热等级每刻扣的热能 [J]。 */
    private static final double HEAT_LOSS_PER_LEVEL = 100.0;

    public GasHeaterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, 1.0);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof GasHeaterBlock)) {
            return;
        }

        double temperature = gasMix().temperature();
        HeatLevel heatLevel = heatLevelFor(temperature);

        // 按档位扣热：相当于"对外供热"的代价
        if (!gasMix().isEmpty()) {
            double loss = Math.min(heatLevel.ordinal() * HEAT_LOSS_PER_LEVEL, gasMix().thermalEnergy());
            if (loss > 0) {
                gasMix().addHeat(-loss);
                gasChanged();
            }
        }

        if (state.getValue(BlazeBurnerBlock.HEAT_LEVEL) != heatLevel) {
            level.setBlockAndUpdate(getBlockPos(), state.setValue(BlazeBurnerBlock.HEAT_LEVEL, heatLevel));
        }
    }

    private static HeatLevel heatLevelFor(double temperature) {
        if (temperature >= ClockworkConfig.server().heaterSeethingTemp.get()) {
            return HeatLevel.SEETHING;
        }
        if (temperature >= ClockworkConfig.server().heaterKindledTemp.get()) {
            return HeatLevel.KINDLED;
        }
        if (temperature >= ClockworkConfig.server().heaterSmoulderingTemp.get()) {
            return HeatLevel.SMOULDERING;
        }
        return HeatLevel.NONE;
    }

    /** 烫伤：每超过 350K 25K 造成半颗心（旧版公式）。 */
    public void burnEntity(Entity entity) {
        double temperature = gasMix().temperature();
        if (temperature < 350) {
            return;
        }
        float damage = (float) ((temperature - 350) / 25.0);
        entity.hurt(level.damageSources().hotFloor(), damage);
    }
}
