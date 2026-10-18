package org.valkyrienskies.clockwork.content.logistics.gas.duct;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.clockwork.config.ClockworkConfig;
import org.valkyrienskies.clockwork.content.logistics.gas.GasType;
import org.valkyrienskies.clockwork.content.logistics.gas.GasTypes;

import java.util.List;

/**
 * 一个导管面的边配置：{@link DuctEdgeType 类型} + 该类型用到的参数。
 *
 * <p>对应旧版两处数据的合并：
 * <ul>
 *   <li>{@code DuctBlockEntity.DIR_TO_CONNECTION_TYPE}（类型，见 {@code DuctBlockEntity.kt:24}）；</li>
 *   <li>旧版每条边自己的参数：{@code ClockworkFilteredDuctEdge.filter/blacklist}
 *       （{@code filter/edges/ClockworkFilteredDuctEdge.kt:25-26}）与
 *       {@code ClockworkSmartEdge.filter/comparisonValue/moreThan}
 *       （{@code smart/ClockworkSmartEdge.kt:16-19,29-31}）。</li>
 * </ul>
 * 旧版每种类型是一个 Edge 子类，参数就长在对象上；本移植一个面一个配置对象，
 * 理由见 {@link GasDuctBlockEntity} 类头（面向"面"而不是"边对象"）。
 *
 * <p><b>参数为什么在切类型时不重置</b>：旧版 {@code edgeData} 按接缝（nodeA/nodeB）保存，
 * 换边类型时会 {@code deserialize} 回新边对象 —— 也就是参数跨类型保留。这里保持一致：
 * 来回切一遍类型不该把玩家调好的过滤/阈值清空。
 * 唯一例外是 {@link FilterType} 变化时阈值会重置：以 Pa 为单位的 200000 当温度用没有意义，
 * 所以切到 {@link FilterType#PRESSURE} / {@link FilterType#TEMPERATURE} 时会换成该指标的默认阈值。
 *
 * <p>线程模型：只在服务端被方块交互修改，客户端反序列化后只读（BER / 护目镜）。
 */
public final class DuctEdgeSettings {

    /** 阈值指标（旧版 {@code SmartEdge.FilterType}：{@code NONE / PRESSURE / TEMPERATURE}）。 */
    public enum FilterType implements StringRepresentable {
        /** 不看阈值，恒放行（旧版 {@code FilterType.NONE}）。 */
        NONE("none"),
        /** 比较两侧压差（旧版 {@code PRESSURE}，阈值单位 Pa，显示为 kPa）。 */
        PRESSURE("pressure"),
        /** 比较被搬走气体的温度（旧版 {@code TEMPERATURE}，阈值单位 K）。 */
        TEMPERATURE("temperature");

        private static final FilterType[] VALUES = values();

        private final String name;

        FilterType(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        public FilterType next() {
            return VALUES[(ordinal() + 1) % VALUES.length];
        }

        public static FilterType byName(final String name) {
            for (FilterType type : VALUES) {
                if (type.name.equals(name)) {
                    return type;
                }
            }
            return NONE;
        }
    }

    /**
     * 压差档位 [Pa]（显示为 1..1000 kPa）。
     *
     * <p>为什么是固定档位而不是旧版 GUI 的自由输入：本移植没有那个滚动输入框，
     * 用螺丝刀循环必须落在离散值上。档位按"每档约 ×2"取，覆盖
     * 1 kPa（微风）到 1000 kPa（高压罐），与旧版 {@code SmartDuctScreen} 的 0..99999 kPa 范围相比
     * 只少了极端值 —— 这是明确的简化，写在文档 §4.2。
     */
    private static final double[] PRESSURE_PRESETS_PA = {
            1_000.0, 2_000.0, 5_000.0, 10_000.0, 20_000.0,
            50_000.0, 100_000.0, 200_000.0, 500_000.0, 1_000_000.0
    };

    /** 温度档位 [K]（环境 293K 到煤炉 850K 都被覆盖，另有高温段）。 */
    private static final double[] TEMPERATURE_PRESETS_K = {
            250.0, 300.0, 350.0, 400.0, 500.0,
            600.0, 800.0, 1_000.0, 1_500.0, 2_000.0
    };

    private DuctEdgeType type = DuctEdgeType.NORMAL;

    /** 过滤目标气体；{@code null} = 全部气体（过滤边刚切过来时的默认值，行为不变）。 */
    @Nullable
    private GasType gas;

    /** {@code false} = 白名单（只允许 {@link #gas}），{@code true} = 黑名单（除 {@link #gas} 外都允许）。 */
    private boolean blacklist;

    private FilterType filterType = FilterType.NONE;

    /** 阈值：{@link FilterType#PRESSURE} 时单位 Pa，{@link FilterType#TEMPERATURE} 时单位 K。 */
    private double threshold;

    /** 旧版 {@code moreThan}：{@code true} = 大于阈值才放行，{@code false} = 小于阈值才放行。 */
    private boolean moreThan = true;

    private DuctEdgeSettings() {
    }

    /** 新放置的导管默认：普通边。 */
    public static DuctEdgeSettings normal() {
        return new DuctEdgeSettings();
    }

    public DuctEdgeType type() {
        return type;
    }

    void setType(final DuctEdgeType type) {
        this.type = type;
    }

    @Nullable
    public GasType gas() {
        return gas;
    }

    public boolean blacklist() {
        return blacklist;
    }

    public FilterType filterType() {
        return filterType;
    }

    public double threshold() {
        return threshold;
    }

    public boolean moreThan() {
        return moreThan;
    }

    /** 是否完全等于默认值（NBT 只写非默认面，空导管不产生额外数据）。 */
    public boolean isDefault() {
        return type == DuctEdgeType.NORMAL && gas == null && !blacklist
                && filterType == FilterType.NONE && threshold == 0.0 && moreThan;
    }

    // ===================== 螺丝刀循环 =====================

    /** 过滤边：全部气体 → 空气 → 蒸汽 → … → 臭氧石气 → 全部气体。 */
    void cycleGas() {
        final List<GasType> all = GasTypes.all();
        if (all.isEmpty()) {
            return;
        }
        if (gas == null) {
            gas = all.get(0);
        } else {
            int next = gas.index() + 1;
            gas = next >= all.size() ? null : all.get(next);
        }
    }

    /** 智能边：循环阈值指标，并把阈值换成该指标的默认值（见类注释）。 */
    void cycleFilterType() {
        filterType = filterType.next();
        threshold = defaultThreshold(filterType);
    }

    /** 过滤边：白名单 ↔ 黑名单。 */
    void toggleBlacklist() {
        blacklist = !blacklist;
    }

    /**
     * 智能边：阈值档位循环（只对当前指标有效）。
     *
     * <p>取"第一个严格大于当前值的档位"，超出最大档就回到最小档；
     * 这样从配置默认值（可能不在档位上）出发，第一下也一定会落到档位上。
     */
    void cycleThreshold() {
        final double[] presets = presetsFor(filterType);
        if (presets.length == 0) {
            return;
        }
        for (double preset : presets) {
            if (preset > threshold) {
                threshold = preset;
                return;
            }
        }
        threshold = presets[0];
    }

    /** 智能边：大于 ↔ 小于。 */
    void toggleMoreThan() {
        moreThan = !moreThan;
    }

    /**
     * 该指标切换时的默认阈值。
     *
     * <p>取自 {@code CWServer#smartDuctPressureThreshold} / {@code smartDuctTemperatureThreshold}
     * —— 这两个配置项在上一版是"所有智能边共享的阈值"，现在改成"新切到该指标时的初始值"，
     * 仍然真实生效（不是死配置）。
     */
    private static double defaultThreshold(final FilterType filterType) {
        return switch (filterType) {
            case PRESSURE -> ClockworkConfig.server().smartDuctPressureThreshold.get();
            case TEMPERATURE -> ClockworkConfig.server().smartDuctTemperatureThreshold.get();
            case NONE -> 0.0;
        };
    }

    private static double[] presetsFor(final FilterType filterType) {
        return switch (filterType) {
            case PRESSURE -> PRESSURE_PRESETS_PA;
            case TEMPERATURE -> TEMPERATURE_PRESETS_K;
            case NONE -> new double[0];
        };
    }

    // ===================== 网络语义 =====================

    /**
     * 智能边的阈值闸（旧版 {@code SmartEdge.hasFlow} 的等价物）。
     *
     * <p>压差取绝对值：旧版 GUI 的"大于/小于"配的是压差<b>大小</b>（{@code SmartDuctScreen.kt:87}
     * 只显示 kPa，没有方向符号），所以这里也只看大小。温度用"将要被搬走的那股气"的温度。
     */
    boolean gateOpen(final double pressureDelta, final double gasTemperature) {
        if (type != DuctEdgeType.SMART || filterType == FilterType.NONE) {
            return true;
        }
        return switch (filterType) {
            case PRESSURE -> moreThan
                    ? Math.abs(pressureDelta) > threshold
                    : Math.abs(pressureDelta) < threshold;
            case TEMPERATURE -> moreThan
                    ? gasTemperature > threshold
                    : gasTemperature < threshold;
            case NONE -> true;
        };
    }

    // ===================== 读数（护目镜 / 行动栏） =====================

    /** 这一面的配置摘要（照旧版 GUI 的读数，改成一行文字）。 */
    public Component describe() {
        return switch (type) {
            case NORMAL -> Component.translatable("vs_clockwork.duct.edge.normal");
            case ONEWAY_FORWARD -> Component.translatable("vs_clockwork.duct.edge.oneway_out");
            case ONEWAY_BACKWARD -> Component.translatable("vs_clockwork.duct.edge.oneway_in");
            case FILTERED -> describeFilter();
            case SMART -> describeSmart();
        };
    }

    private Component describeFilter() {
        if (gas == null) {
            return Component.translatable("vs_clockwork.duct.edge.filtered.any");
        }
        return Component.translatable(blacklist
                        ? "vs_clockwork.duct.edge.filtered.except"
                        : "vs_clockwork.duct.edge.filtered.only",
                Component.literal(gas.displayName()).withStyle(ChatFormatting.AQUA));
    }

    private Component describeSmart() {
        if (filterType == FilterType.NONE) {
            return Component.translatable("vs_clockwork.duct.edge.smart.off");
        }
        final Component value = switch (filterType) {
            case PRESSURE -> Component.literal(String.format("%.0f kPa", threshold / 1000.0));
            case TEMPERATURE -> Component.literal(String.format("%.0f K", threshold));
            case NONE -> Component.empty();
        };
        return Component.translatable(filterType == FilterType.PRESSURE
                        ? "vs_clockwork.duct.edge.smart.pressure"
                        : "vs_clockwork.duct.edge.smart.temperature",
                Component.translatable(moreThan
                        ? "vs_clockwork.duct.edge.gt" : "vs_clockwork.duct.edge.lt"),
                value);
    }

    // ===================== NBT =====================

    void save(final CompoundTag tag) {
        tag.putString("Type", type.getSerializedName());
        tag.putString("Gas", gas == null ? "" : gas.id().toString());
        tag.putBoolean("Blacklist", blacklist);
        tag.putString("Sensor", filterType.getSerializedName());
        tag.putDouble("Threshold", threshold);
        tag.putBoolean("MoreThan", moreThan);
    }

    void load(final CompoundTag tag) {
        type = DuctEdgeType.byName(tag.getString("Type"));
        final String gasId = tag.getString("Gas");
        gas = gasId.isEmpty() ? null : GasTypes.byId(ResourceLocation.tryParse(gasId));
        blacklist = tag.getBoolean("Blacklist");
        filterType = FilterType.byName(tag.getString("Sensor"));
        threshold = tag.getDouble("Threshold");
        moreThan = !tag.contains("MoreThan") || tag.getBoolean("MoreThan");
    }
}
