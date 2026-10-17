package org.valkyrienskies.clockwork.content.logistics.gas.heatpipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.clockwork.content.logistics.gas.GasMix;
import org.valkyrienskies.clockwork.content.logistics.gas.GasNodeBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.GasNodeTooltip;
import org.valkyrienskies.clockwork.content.logistics.gas.GasPhysics;

import java.util.List;

/**
 * 热管方块实体：每刻与相邻热节点做一次"完全混合"式换热。
 *
 * <p>见 {@link HeatPipeBlock} 的类头注释（本方块旧版没有实现源码，属自行设计）。
 *
 * <p><b>换热模型</b>（与 Kelvin 求解器的"节点内完全混合"一致，但不做亚步迭代）：
 * <pre>
 * 新温度 = (C₁·T₁ + C₂·T₂) / (C₁ + C₂)      C = 气体热容 + 管壁热容 [J/K]
 * </pre>
 * 与相邻节点<b>双向</b>进行：一侧被加热、另一侧被冷却，总热量守恒。
 * 已经"气体连通"的邻居跳过（气流本身就会把热量带走，再算一遍等于双重换热）。
 *
 * <p>热管不参与气体流动：{@link #isGasPipe()} 为 false；
 * {@link #gasNodeActive()} 为 false 让气体模拟忽略它（既不流动也不泄漏），
 * 但它仍会注册到网络里，因此邻接机器能被发现并建边（建边只是"知道邻居"，不搬运气体）。
 */
public class HeatPipeBlockEntity extends GasNodeBlockEntity implements HeatNode {

    /**
     * 外观热度的检查周期 [tick]。
     *
     * <p>不能每 tick 刷：温度是连续量，每 tick 都会在阈值附近抖动；而每次 {@code setBlockAndUpdate}
     * 都会让客户端重建该方块所在 section 的模型，大量热管同时每 tick 更新会明显掉帧。
     * 20 tick（1 秒）对手感足够；再用 {@code worldPosition} 的哈希错开初相位，避免所有热管同一 tick 更新。
     * 只有档位真的变化时才写方块状态，所以稳态下 0 次写入。
     */
    private static final int HEAT_REFRESH_TICKS = 20;

    private int heatRefresh;

    public HeatPipeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, 1.0);
        // 错开初相位：否则一整排热管会在同一 tick 一起刷新。
        this.heatRefresh = (pos.hashCode() & 0x7FFFFFFF) % HEAT_REFRESH_TICKS;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }
        final GasMix mix = gasMix();
        double selfCapacity = mix.heatCapacity() + GasPhysics.wallHeatCapacity();
        for (final Direction dir : Direction.values()) {
            if (gasConnected(dir)) {
                // 气体本身就连通 → 气流已经带热了，跳过
                continue;
            }
            final HeatNode other = HeatNode.neighbor(level, getBlockPos(), dir);
            if (other == null || other == this) {
                continue;
            }
            final double otherCapacity = other.heatNodeCapacity();
            final double total = selfCapacity + otherCapacity;
            if (total <= 0) {
                continue;
            }
            final double selfTemperature = mix.temperature();
            final double otherTemperature = other.heatNodeTemperature();
            if (Math.abs(selfTemperature - otherTemperature) < 1.0E-6) {
                continue;
            }
            final double equilibrium = (selfCapacity * selfTemperature + otherCapacity * otherTemperature) / total;
            mix.setTemperature(equilibrium);
            other.setHeatNodeTemperature(equilibrium);
            gasChanged();
        }

        // 换热跑完再按当前温度刷新外观（每 20 tick 一次 + 跨档才写，见 HEAT_REFRESH_TICKS）。
        if (--this.heatRefresh > 0) {
            return;
        }
        this.heatRefresh = HEAT_REFRESH_TICKS;
        final HeatPipeBlock.Heat next = HeatPipeBlock.Heat.forTemperature(mix.temperature());
        final BlockState state = getBlockState();
        if (state.getValue(HeatPipeBlock.HEAT) != next) {
            // setBlockAndUpdate = UPDATE_ALL：客户端自动收到新 blockstate 并换模型。
            level.setBlockAndUpdate(worldPosition, state.setValue(HeatPipeBlock.HEAT, next));
        }
    }

    /** 热管不搬运气体：不参与流动、不泄漏（否则会变成网络的漏洞）。 */
    @Override
    public boolean gasNodeActive() {
        return false;
    }

    @Override
    public boolean gasConnected(Direction dir) {
        return getBlockState().getBlock() instanceof HeatPipeBlock
                && getBlockState().getValue(HeatPipeBlock.DIR_TO_CONNECTION.get(dir));
    }

    @Override
    public boolean isGasPipe() {
        return false;
    }

    // ===================== HeatNode =====================

    @Override
    public double heatNodeTemperature() {
        return gasMix().temperature();
    }

    @Override
    public double heatNodeCapacity() {
        return gasMix().heatCapacity() + GasPhysics.wallHeatCapacity();
    }

    @Override
    public BlockPos heatNodePos() {
        return getBlockPos();
    }

    @Override
    public void setHeatNodeTemperature(double kelvin) {
        gasMix().setTemperature(kelvin);
        gasChanged();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        GasNodeTooltip.addFunctionLine(getBlockState().getBlock(), tooltip);
        tooltip.add(Component.literal("    ")
                .append(Component.literal("Heat Pipe: ").withStyle(net.minecraft.ChatFormatting.GRAY))
                .append(Component.literal("conducting").withStyle(net.minecraft.ChatFormatting.GOLD)));
        return GasNodeTooltip.add(this, tooltip, isPlayerSneaking);
    }
}
