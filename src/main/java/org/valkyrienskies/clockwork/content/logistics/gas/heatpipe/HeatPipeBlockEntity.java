package org.valkyrienskies.clockwork.content.logistics.gas.heatpipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.clockwork.content.logistics.gas.GasMix;
import org.valkyrienskies.clockwork.content.logistics.gas.GasNodeBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.GasNodeTooltip;

import java.util.List;

/**
 * 热管方块实体：每刻与<b>画了臂的那几面</b>的热节点做一次完全混合换热。
 *
 * <p>对应官方 0.6.0（1.21.1）的 {@code HeatPipeBlockEntity}（反编译见
 * {@code _refs/baseline_vs_clockwork_0.6.0-1.21.1.jar}）。差异有两处，都是本次实机反馈
 * "臂伸到气体设备上、温度却不动"的直接原因：
 * <ul>
 *   <li>官方用 {@code if (gasConnected(dir)) continue} 跳过"连接"的面；而热管的连接位就是连接臂，
 *       于是<b>臂越多越不换热</b>：两根热管接在一起 = 两面都跳过 = 完全不导热。本移植反过来，
 *       <b>只在画了臂的面换热</b>，与 {@link HeatPipeBlock#getConnection} 同一判据。</li>
 *   <li>官方只有热管自己实现 {@code HeatNode}，气体机器/导管都不是热节点，所以即使不跳过也没有
 *       换热对象。本移植让所有气体节点实现热节点（见 {@link HeatNode} / {@code GasNode}），
 *       热管贴着加热器、煤炉、斯特林、燃气机、导管、储罐都能取放热量。</li>
 * </ul>
 *
 * <p><b>换热模型</b>：取两侧热容 C₁、C₂ 与温差 ΔT，交换热量
 * <pre>Q = ΔT · C₁C₂ / (C₁ + C₂)</pre>
 * 两侧温度各走 {@code ±Q/C}，因此 {@code Σ C·T} 精确守恒；写成温度就是两者按热容加权的平衡温度。
 * 这等价于 {@code GasNetwork#conduct} 取 k=1（完全混合），而不是普通金属边的 0.1：
 * 热管本来就是"高导热"部件，且自身热容只有管壁 44.9 J/K，k=1 才能让一条链在几十 tick 内收敛；
 * 用 0.1 的话十节以外的机器几乎收不到热，"热管"名不副实。大热容的邻居（满罐气体）升温仍很慢，
 * 因为 Q 受串联热容 C₁C₂/(C₁+C₂) 限制，不会一跳就到平衡。
 *
 * <p>热管不搬气体：{@link #isGasPipe()} false、{@link #gasNodeActive()} false、
 * {@link #gasConnected(Direction)} 恒 false（不注册、也不接受任何气体边），
 * 所以它既不会让气体穿过，也不会从接缝漏气。
 */
public class HeatPipeBlockEntity extends GasNodeBlockEntity {

    /**
     * 外观热度的检查周期 [tick]。
     *
     * <p>不能每 tick 刷：温度是连续量，每 tick 都会在阈值附近抖动；而每次 {@code setBlockAndUpdate}
     * 都会让客户端重建该方块所在 section 的模型，大量热管同时每 tick 更新会明显掉帧。
     * 20 tick（1 秒）对手感足够；再用 {@code worldPosition} 的哈希错开初相位，避免所有热管同一 tick 更新。
     * 只有档位真的变化时才写方块状态，所以稳态下 0 次写入。
     */
    private static final int HEAT_REFRESH_TICKS = 20;

    /** 小于该温差视为等温 [K]。与 {@code GasNetwork#MIN_DELTA_T} 同值，避免无意义的写入。 */
    private static final double MIN_DELTA_T = 1.0E-6;

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
        final BlockState state = getBlockState();
        if (!(state.getBlock() instanceof HeatPipeBlock)) {
            return;
        }
        final GasMix mix = gasMix();
        // 旧实现（GasNetworks#nodeAt 的方块实体回落）会把气体搬进热管。热管不是容器，升级后
        // 残留的气体要清掉：否则它会以"气体热容"的形式参与换热，护目镜还会显示一根装着气的热管。
        if (!mix.isEmpty()) {
            mix.clearMass();
            gasChanged();
        }
        for (final Direction dir : Direction.values()) {
            // 臂 = 热接触：与 HeatPipeBlock#getConnection 同一判据。
            // 不带臂的面既不画几何、也不换热，玩家用扳手关掉一面就是断开这一面的热路。
            if (!state.getValue(HeatPipeBlock.DIR_TO_CONNECTION.get(dir))) {
                continue;
            }
            final HeatNode other = HeatNode.neighbor(level, getBlockPos(), dir);
            if (other == null || other == this) {
                continue;
            }
            // 每次重算自身热容：热管不含气体，容量恒定；但写成"当场取"可以避免以后
            // 如果热管获得气体/涂层时忘记同步 selfCapacity。
            final double selfCapacity = mix.nodeHeatCapacity();
            final double otherCapacity = other.heatNodeCapacity();
            if (selfCapacity <= 0 || otherCapacity <= 0) {
                continue;
            }
            final double selfTemperature = mix.temperature();
            final double otherTemperature = other.heatNodeTemperature();
            if (Math.abs(selfTemperature - otherTemperature) < MIN_DELTA_T) {
                continue;
            }
            final double equilibrium = (selfCapacity * selfTemperature + otherCapacity * otherTemperature)
                    / (selfCapacity + otherCapacity);
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
        if (state.getValue(HeatPipeBlock.HEAT) != next) {
            // setBlockAndUpdate = UPDATE_ALL：客户端自动收到新 blockstate 并换模型。
            level.setBlockAndUpdate(worldPosition, state.setValue(HeatPipeBlock.HEAT, next));
        }
    }

    /**
     * 热管在任何一面都不搬运气体。
     *
     * <p><b>为什么不复用连接位</b>：连接位现在是"热接触"（可以连到导管/机器），若拿它当气体连接，
     * 气体网络就会把热管当节点（{@code GasNetworks#nodeAt} 的方块实体回落能找到它），
     * 既能被灌气、也能形成"导管—热管—导管"的旁路。官方注释说
     * "{@code gasNodeActive=false} 让气体模拟忽略它"，但那只挡住自己的 tick，挡不住别人的 BFS；
     * 这里用最直接的方式保证隔离：热管永远拒绝气体边。
     */
    @Override
    public boolean gasConnected(Direction dir) {
        return false;
    }

    /** 热管不搬运气体：不参与流动、不泄漏（否则会变成网络的漏洞）。 */
    @Override
    public boolean gasNodeActive() {
        return false;
    }

    @Override
    public boolean isGasPipe() {
        return false;
    }

    // ===================== 护目镜 =====================

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        GasNodeTooltip.addFunctionLine(getBlockState().getBlock(), tooltip);
        int faces = 0;
        for (final Direction dir : Direction.values()) {
            if (getBlockState().getValue(HeatPipeBlock.DIR_TO_CONNECTION.get(dir))) {
                faces++;
            }
        }
        // 只报"连接了几面 / 当前外观档"，不报温度：热管不是气体节点、不注册，客户端没有
        // 实时温度可读（温度只在服务端换热时变化）；档位来自已同步的 blockstate，不会误导。
        tooltip.add(Component.literal("    ")
                .append(Component.literal("Heat Pipe: ").withStyle(net.minecraft.ChatFormatting.GRAY))
                .append(Component.literal("conducting " + faces + " face(s), ")
                        .withStyle(net.minecraft.ChatFormatting.GOLD))
                .append(Component.literal(getBlockState().getValue(HeatPipeBlock.HEAT).getSerializedName())
                        .withStyle(net.minecraft.ChatFormatting.RED)));
        return GasNodeTooltip.add(this, tooltip, isPlayerSneaking);
    }
}
