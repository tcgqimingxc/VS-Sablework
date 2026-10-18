package org.valkyrienskies.clockwork.content.logistics.gas.duct;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.clockwork.config.ClockworkConfig;
import org.valkyrienskies.clockwork.content.logistics.gas.GasLink;
import org.valkyrienskies.clockwork.content.logistics.gas.GasNode;
import org.valkyrienskies.clockwork.content.logistics.gas.GasNodeBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.GasNodeTooltip;

import java.util.EnumMap;
import java.util.List;

/**
 * 导管方块实体：气体网络里最基础的"管道节点"，并保存<b>每个面</b>的边类型。
 *
 * <p>对应旧版 {@code DuctBlockEntity}（{@code DIR_TO_CONNECTION_TYPE} + {@code edgeData}，
 * 见 {@code Clockwork-1.20.1-main/common/.../duct/DuctBlockEntity.kt:24,91-101}）。
 * 旧版把边类型存在方块实体里、由 Kelvin 求解器按"边对象"建模；本移植没有边对象，
 * 所以边类型同样存在方块实体里，由 {@link GasNode#gasLink(Direction)} /
 * {@link GasNode#gasFlowGateOpen} 按方向回答"这一面怎么走气"。
 *
 * <p><b>为什么边类型存方块实体而不是方块状态</b>：每个面 5 种类型，若做成方块状态属性，
 * 变体数会从 64 涨到 64×5⁶（≈100 万），blockstate JSON 与守门 [12] 的覆盖检查都无法维护；
 * 而且边类型是"配置"而不是"形状"——它不参与碰撞箱、不参与模型烘焙（外观由 BER 按方块实体画），
 * 正好是旧版把它放方块实体的理由。
 *
 * <p><b>同步</b>：写入 NBT 的 {@code DuctEdges}（只写非默认面，普通导管不产生额外数据）。
 * 服务端改动后 {@code setChanged() + sendData()}；Create 的 {@code SyncedBlockEntity.getUpdateTag()}
 * 会把 {@code write(tag, provider, clientPacket = true)} 的结果发给客户端，BER 在客户端读到的
 * 就是同一份数据 —— 存档与更新包共用同一种格式，不存在"存档对、联机看不到"的两套逻辑。
 *
 * <p>容量：1.0 倍额定值（旧版 {@code ductVolme = 0.4 m³}）。
 */
public class GasDuctBlockEntity extends GasNodeBlockEntity {

    /** 每个面的边配置，默认全部 {@link DuctEdgeType#NORMAL}。 */
    private final EnumMap<Direction, DuctEdgeSettings> edges = new EnumMap<>(Direction.class);

    public GasDuctBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, 1.0);
        for (Direction dir : Direction.values()) {
            edges.put(dir, DuctEdgeSettings.normal());
        }
    }

    /** 某一面的边配置（永远非 null）。 */
    public DuctEdgeSettings edge(Direction dir) {
        return edges.get(dir);
    }

    /** 某一面的边类型（网络层 / BER 查询入口）。 */
    public DuctEdgeType edgeType(Direction dir) {
        return edges.get(dir).type();
    }

    /**
     * 设置某一面的边类型（服务端权威）。参数保留，理由见 {@link DuctEdgeSettings} 类注释。
     *
     * <p>公开是给 Ponder 场景 / 调试命令用的入口；玩家交互走 {@link #cycleType(Direction)}。
     */
    public void setEdgeType(Direction dir, DuctEdgeType type) {
        edges.get(dir).setType(type);
        edgeDataChanged();
    }

    /** 边配置变更：标脏 + 同步给客户端（BER 要按它换模型）。 */
    private void edgeDataChanged() {
        setChanged();
        sendData();
    }

    // ===================== 连接判定（沿用上一轮实现） =====================

    private DuctConnectionType connection(Direction dir) {
        if (!(getBlockState().getBlock() instanceof GasDuctBlock duct)) {
            return DuctConnectionType.NONE;
        }
        return getBlockState().getValue(GasDuctBlock.DIR_TO_CONNECTION.get(dir));
    }

    @Override
    public boolean gasConnected(Direction dir) {
        return connection(dir) == DuctConnectionType.SIDE;
    }

    @Override
    public int gasLeakFaces() {
        int leaks = 0;
        for (Direction dir : Direction.values()) {
            if (connection(dir) == DuctConnectionType.LEAK) {
                leaks++;
            }
        }
        return leaks;
    }

    @Override
    public boolean isGasPipe() {
        return true;
    }

    // ===================== 网络语义（边类型 → GasLink / 阈值闸） =====================

    /**
     * 这一面的连接参数。
     *
     * <p>{@link org.valkyrienskies.clockwork.content.logistics.gas.GasNetwork} 只会查询
     * "已连接"的面（见 {@code GasNetwork#simulate} 的 {@code gasConnectedAt} 过滤），
     * 所以这里不重复判断连接状态：未连接的面返回什么都不影响模拟。
     *
     * <p><b>单向不节流</b>：旧版 {@code ClockworkOnewayDuct} 的 {@code radius/length}
     * 与普通管逐字相同（{@code oneway/ClockworkOnewayDuct.kt:9-10}），所以这里只挂方向闸，
     * 不动 {@code aperture} / {@code flowScale}。
     */
    @Override
    public GasLink gasLink(Direction dir) {
        final DuctEdgeSettings edge = edges.get(dir);
        return switch (edge.type()) {
            case NORMAL -> GasLink.PIPE;
            case ONEWAY_FORWARD -> GasLink.PIPE.withOnlyOut();
            case ONEWAY_BACKWARD -> GasLink.PIPE.withOnlyIn();
            case FILTERED -> edge.gas() == null
                    ? GasLink.PIPE
                    : edge.blacklist() ? GasLink.blocking(edge.gas()) : GasLink.filtered(edge.gas());
            // 智能边在旧版里画的就是铜模型（DuctRenderer.kt:61），本移植把"铜体"落实成材质差异：
            // 通流 ×copperDuctFlowScale、导热 copperDuctThermalConductivity。
            // 这样"铜"不再是独立方块，而是智能边的一个固有属性（详见 docs §2.3）。
            case SMART -> GasLink.PIPE
                    .withFlowScale(ClockworkConfig.server().copperDuctFlowScale.get())
                    .withConductivity(ClockworkConfig.server().copperDuctThermalConductivity.get());
        };
    }

    @Override
    public boolean gasFlowGateOpen(Direction dir, double pressureDelta, double gasTemperature) {
        return edges.get(dir).gateOpen(pressureDelta, gasTemperature);
    }

    // ===================== 螺丝刀循环（服务端；返回给玩家看的读数） =====================

    /** 循环该面的边类型（旧版 {@code DuctBlockEntity.cycleEdgeType}）。 */
    public Component cycleType(Direction dir) {
        final DuctEdgeSettings edge = edges.get(dir);
        edge.setType(edge.type().next());
        edgeDataChanged();
        return describeFace(dir);
    }

    /** 循环该类型的主参数：过滤 → 气体种类，智能 → 阈值指标。不适用时返回 null。 */
    @Nullable
    public Component cyclePrimary(Direction dir) {
        final DuctEdgeSettings edge = edges.get(dir);
        switch (edge.type()) {
            case FILTERED -> edge.cycleGas();
            case SMART -> edge.cycleFilterType();
            default -> {
                return null;
            }
        }
        edgeDataChanged();
        return describeFace(dir);
    }

    /** 次参数：过滤 → 白/黑名单，智能 → 阈值档位。不适用时返回 null。 */
    @Nullable
    public Component cycleSecondary(Direction dir) {
        final DuctEdgeSettings edge = edges.get(dir);
        switch (edge.type()) {
            case FILTERED -> edge.toggleBlacklist();
            case SMART -> {
                if (edge.filterType() == DuctEdgeSettings.FilterType.NONE) {
                    return null;
                }
                edge.cycleThreshold();
            }
            default -> {
                return null;
            }
        }
        edgeDataChanged();
        return describeFace(dir);
    }

    /** 第三参数：智能 → 大于/小于。不适用时返回 null。 */
    @Nullable
    public Component cycleTertiary(Direction dir) {
        final DuctEdgeSettings edge = edges.get(dir);
        if (edge.type() != DuctEdgeType.SMART || edge.filterType() == DuctEdgeSettings.FilterType.NONE) {
            return null;
        }
        edge.toggleMoreThan();
        edgeDataChanged();
        return describeFace(dir);
    }

    /** "面 + 配置摘要"，供行动栏反馈。 */
    public Component describeFace(Direction dir) {
        return Component.translatable("vs_clockwork.duct.edge.set",
                Component.translatable("vs_clockwork.duct.edge.face." + dir.getSerializedName()),
                edges.get(dir).describe());
    }

    // ===================== 护目镜 =====================

    /**
     * 护目镜读数：列出所有非普通面。
     *
     * <p>旧版是把光标指到某条边、按 E 打开该边的 GUI 看参数（{@code DuctBlock.onScrewdrived} →
     * {@code edge.interact}）；{@code IHaveGoggleInformation} 拿不到"玩家正看着哪一面"，
     * 所以这里一次性列出所有配置过的面，比只显示一个不知道哪来的面更可靠。
     */
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        GasNodeTooltip.addFunctionLine(getBlockState().getBlock(), tooltip);
        boolean any = false;
        for (Direction dir : Direction.values()) {
            final DuctEdgeSettings edge = edges.get(dir);
            if (edge.type() == DuctEdgeType.NORMAL) {
                continue;
            }
            if (!any) {
                tooltip.add(Component.literal("Edges:").withStyle(ChatFormatting.GRAY));
                any = true;
            }
            tooltip.add(Component.literal("    ")
                    .append(Component.literal(faceLabel(dir) + ": ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(edge.describe().copy().withStyle(ChatFormatting.AQUA)));
        }
        if (!any) {
            tooltip.add(Component.literal("    ")
                    .append(Component.literal("Edges: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.translatable("vs_clockwork.duct.edge.normal")
                            .copy().withStyle(ChatFormatting.DARK_GRAY)));
        }
        return GasNodeTooltip.add(this, tooltip, isPlayerSneaking);
    }

    private static String faceLabel(Direction dir) {
        final String name = dir.getSerializedName();
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    // ===================== NBT =====================

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        final CompoundTag edgeTags = new CompoundTag();
        for (Direction dir : Direction.values()) {
            final DuctEdgeSettings edge = edges.get(dir);
            if (edge.isDefault()) {
                continue;
            }
            final CompoundTag edgeTag = new CompoundTag();
            edge.save(edgeTag);
            edgeTags.put(dir.getSerializedName(), edgeTag);
        }
        tag.put("DuctEdges", edgeTags);
        super.write(tag, provider, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        final CompoundTag edgeTags = tag.getCompound("DuctEdges");
        for (Direction dir : Direction.values()) {
            final DuctEdgeSettings edge = DuctEdgeSettings.normal();
            if (edgeTags.contains(dir.getSerializedName(), Tag.TAG_COMPOUND)) {
                edge.load(edgeTags.getCompound(dir.getSerializedName()));
            }
            // 用新对象替换而不是就地改：客户端收到更新包时必须丢掉旧值，
            // 否则某个面"改回普通"以后客户端还留着上一次的类型（BER 就会继续画旧模型）。
            edges.put(dir, edge);
        }
    }
}
