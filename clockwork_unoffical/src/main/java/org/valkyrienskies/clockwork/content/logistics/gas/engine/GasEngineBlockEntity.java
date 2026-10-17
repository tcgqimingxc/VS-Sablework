package org.valkyrienskies.clockwork.content.logistics.gas.engine;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.stress.BlockStressValues;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.clockwork.config.ClockworkConfig;
import org.valkyrienskies.clockwork.content.logistics.gas.GasNodeBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.GasNodeTooltip;

import java.util.List;

/**
 * 燃气机方块实体。
 *
 * <p>对应旧版 {@code GasEngineBlockEntity}。职责只有一个：
 * <b>按"温度效率 × 流量效率"给出一个 0..1 的效率</b>，供贴在它上面的 Create 蒸汽机读取
 * （见 {@code MixinSteamEngineBlockEntity}），同时按效率抽取气体网络的热能。
 *
 * <p>效率公式（旧版 {@code GasEngineLogic} 原样）：
 * <pre>
 * 温度效率 = clamp(floor((T - 290K) / 温度增量) / 20, 0, 1)        // 温度增量默认 60K
 * 流量效率 = clamp(有效流量 / 满效率流量, 0, 1)                     // 默认 5 kg/s 满效率
 * 有效流量 = 低于 0.5 kg/s 记 0，否则向下取整到 0.25 kg/s 的整数倍
 * 总效率   = 温度效率 × 流量效率
 * </pre>
 *
 * <p>与旧版的差异：
 * <ul>
 *   <li>旧版的流量来自 Kelvin 求解器的 {@code DuctEdge.currentFlowRate}（kg/s）；
 *       本移植用网络每刻记录到本节点的搬运量（kg/tick），见
 *       {@link org.valkyrienskies.clockwork.content.logistics.gas.GasNodeState#throughputPerTick()}。</li>
 *   <li>旧版把 {@code attachedEngines} 记在方块实体上（由 {@code GasEngineBlock.updateEngineState} 维护）；
 *       本移植不需要它——Create 锅炉每刻重扫并直接数燃气机（{@code MixinBoilerData}），
 *       {@code getEngineEfficiency()} 因此也不做"除以台数"的二次分配。</li>
 *   <li>旧版还接管了蒸汽机的全部 tick 逻辑；本移植只替换效率这一个输入（见 mixin 注释）。</li>
 * </ul>
 */
public class GasEngineBlockEntity extends GasNodeBlockEntity {

    /** 流量一阶低通的保持系数：用于护目镜的稳定读数（不影响效率计算）。 */
    private static final double FLOW_SMOOTHING = 0.85;

    private float temperatureEfficiency;
    private float flowEfficiency;
    /** 平滑后的流量 [kg/tick]，仅用于显示。 */
    private double smoothedFlow;
    /** 本刻流量 [kg/tick]，供 {@link #afterGasTick()} 使用。 */
    private double currentFlow;

    public GasEngineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        // 旧版燃气机本身只是"能量转换器"，容量与单根导管一致
        super(type, pos, state, 1.0);
    }

    @Override
    public void tick() {
        if (level != null && !level.isClientSide) {
            // ① 本刻穿过的气体（GasNetwork 在搬运时记录）→ 有效流量 → 流量效率
            currentFlow = gasState().throughputPerTick();
            smoothedFlow = smoothedFlow * FLOW_SMOOTHING + currentFlow * (1.0 - FLOW_SMOOTHING);
            double effective = GasEngineLogic.effectiveThroughput(
                    currentFlow, GasEngineLogic.minimumFlowRatePerTick(),
                    GasEngineLogic.flowRateIncrementPerTick());
            flowEfficiency = GasEngineLogic.flowEfficiency(effective, GasEngineLogic.flowForFullEfficiencyPerTick());
            // ② 温度效率
            temperatureEfficiency = GasEngineLogic.temperatureEfficiency(gasMix().temperature());
            // ③ 满效率时每刻抽取热能（旧版把这一步放在 tick 里）
            double heatLoss = totalEfficiency() * heatLossPerTick();
            if (heatLoss > 0) {
                gasMix().addHeat(-heatLoss);
                gasChanged();
            }
        }
        super.tick();
    }

    /** 总效率 0..1 = 温度效率 × 流量效率（旧版 {@code EfficiencyComponents.totalEfficiency}）。 */
    public float totalEfficiency() {
        return temperatureEfficiency * flowEfficiency;
    }

    /**
     * 提供给 Create 蒸汽机的效率（旧版 {@code getEngineEfficiency()}）。
     *
     * <p>旧版还要除以 {@code attachedEngines} 并在锅炉侧按整数应力取整；本移植的语义是
     * "一台燃气机对应一台蒸汽机"，所以这里只保留整数应力取整（避免出现 0.37 这种应力容量）。
     */
    public float getEngineEfficiency() {
        return GasEngineLogic.wholeStressEfficiency(totalEfficiency(),
                BlockStressValues.getCapacity(AllBlocks.STEAM_ENGINE.get()));
    }

    /** 满效率时每刻消耗的热能 [J/tick]。 */
    private static double heatLossPerTick() {
        return ((Number) ClockworkConfig.server().gasEngineMaxHeatLoss.get()).doubleValue();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        GasNodeTooltip.addFunctionLine(getBlockState().getBlock(), tooltip);
        GasNodeTooltip.addEfficiency(tooltip, temperatureEfficiency, gasMix().temperature(),
                GasEngineLogic.temperatureIncrement());

        int bars = Math.round(flowEfficiency * HeatEngineLogic.EFFICIENCY_STEPS);
        tooltip.add(Component.literal("    ")
                .append(Component.literal("Flowrate: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("|".repeat(bars) + ".".repeat(HeatEngineLogic.EFFICIENCY_STEPS - bars))
                        .withStyle(ChatFormatting.AQUA))
                .append(Component.literal(String.format(" %.4f kg/tick", smoothedFlow))
                        .withStyle(ChatFormatting.AQUA)));
        tooltip.add(Component.literal("    ")
                .append(Component.literal("Engine Output: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.format("%.0f%%", getEngineEfficiency() * 100))
                        .withStyle(ChatFormatting.GREEN)));
        return super.addToGoggleTooltip(tooltip, isPlayerSneaking);
    }
}
