package org.valkyrienskies.clockwork.content.logistics.gas;

import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * 一条气体连接的参数。
 *
 * <p>对应旧版 Kelvin 的 {@code DuctEdge}（PIPE / ONEWAY / APERTURE / PUMP / FILTERED / SMART 等子类）。
 * 旧版把连接建成独立 Edge 对象存进求解器；本移植把参数挂在节点上按方向查询，
 * 省掉一整套边对象与其生命周期管理 —— 方向就是"方块的哪一面"，
 * 而面级的边类型由 {@link org.valkyrienskies.clockwork.content.logistics.gas.duct.GasDuctBlockEntity} 保存。
 *
 * <p><b>各参数的物理含义与叠加规则</b>（{@link GasNetwork} 每次都把相邻两侧的 link 合起来裁决）：
 * <ul>
 *   <li>{@code aperture} 通径 0..1，<b>两侧取 min</b> —— 它是"节流件"（阀门开度），最窄的一侧决定截面。</li>
 *   <li>{@code bias} 附加压差 [Pa]，正值沿"本节点 → 邻居"推动。泵给两侧各一个相反的 bias。</li>
 *   <li>{@code oneWay} 与 bias 配合：为 true 时只允许沿 bias 的方向流动（泵用；bias = 0 时不限制）。</li>
 *   <li>{@code flow} 方向闸（单向边类型用），相对<b>持有该 link 的节点</b>：只出不进 / 只进不出。</li>
 *   <li>{@code filter} 气体种类过滤（过滤边类型用）：{@link GasFilter} 带白/黑名单，
 *       非 null 时这条连接只按该条件搬运气体。</li>
 *   <li>{@code flowScale} 通流能力倍率（≥1，铜体智能边用），<b>两侧取 max</b> —— 理由见
 *       {@link #withFlowScale(double)}。</li>
 *   <li>{@code conductivity} 本连接要求的导热率 0..1（每刻消掉的温差比例，<b>两侧取 max</b>），
 *       与基础值 {@code gasThermalConductivity} 再取 max 后生效（见 {@link GasNetwork#conduct}）。
 *       普通导管留 0 表示"不比基础值更好"；铜体给出更大的值。</li>
 * </ul>
 *
 * <p>为什么 {@code aperture} 取 min 而 {@code flowScale} 取 max：二者描述的是不同东西。
 * 通径是"管子有多细"——串联管道里最细的一段就是瓶颈，所以 min；
 * 通流能力是"这一段管壁/内衬能让多少气通过"——接口件本身不是节流件，相邻一段铜管就足以让
 * 整个接口变通畅，所以 max（{@code docs/DUCT-EDGE-VARIANTS.md} §2 有完整推导与举证）。
 */
public record GasLink(double aperture, double bias, boolean oneWay, @Nullable GasFilter filter,
                      Flow flow, double flowScale, double conductivity) {

    /** 方向闸：相对持有该 link 的节点，这条连接允许气体往哪个方向走。 */
    public enum Flow {
        /** 双向（普通管道）。 */
        BOTH,
        /** 只出不进：气体只能从本节点流向邻居（单向边类型的出口面）。 */
        OUT,
        /** 只进不出：气体只能从邻居流进本节点（单向边类型的入口面）。 */
        IN
    }

    /** 普通管道连接。 */
    public static final GasLink PIPE = new GasLink(1.0, 0.0, false, null, Flow.BOTH, 1.0, 0.0);

    /** 不可通行的连接（通径 0）。 */
    public static final GasLink CLOSED = new GasLink(0.0, 0.0, false, null, Flow.BOTH, 1.0, 0.0);

    public GasLink {
        aperture = Mth.clamp(aperture, 0.0, 1.0);
        flowScale = Math.max(0.0, flowScale);
        conductivity = Mth.clamp(conductivity, 0.0, 1.0);
    }

    /** 旧版 {@code DuctEdge} 的四参数写法（泵 / 阀门 / 过滤边等调用点继续可用）。 */
    public GasLink(final double aperture, final double bias, final boolean oneWay, @Nullable final GasFilter filter) {
        this(aperture, bias, oneWay, filter, Flow.BOTH, 1.0, 0.0);
    }

    /** 不过滤的便捷构造（泵 / 阀门等老调用点继续可用）。 */
    public GasLink(final double aperture, final double bias, final boolean oneWay) {
        this(aperture, bias, oneWay, null);
    }

    /** 白名单连接：只搬运 {@code type}（过滤边类型用）。 */
    public static GasLink filtered(final GasType type) {
        return new GasLink(1.0, 0.0, false, GasFilter.only(type));
    }

    /** 黑名单连接：除 {@code type} 外都搬运（过滤边类型的黑名单模式）。 */
    public static GasLink blocking(final GasType type) {
        return new GasLink(1.0, 0.0, false, GasFilter.except(type));
    }

    /** 方向闸：只出不进（单向边类型的出口面）。 */
    public GasLink withOnlyOut() {
        return new GasLink(aperture, bias, oneWay, filter, Flow.OUT, flowScale, conductivity);
    }

    /** 方向闸：只进不出（单向边类型的入口面）。 */
    public GasLink withOnlyIn() {
        return new GasLink(aperture, bias, oneWay, filter, Flow.IN, flowScale, conductivity);
    }

    /**
     * 通流能力倍率（1.0 = 普通铁管；铜体智能边用 {@code CWServer#copperDuctFlowScale}）。
     *
     * <p>为什么允许 &gt; 1 而不复用 {@code aperture}：{@code aperture} 被语义约束在 0..1（通径比例），
     * 而铜的差异是"同样的通径下阻力更小"，两者相乘才是最终流量，混在一起会让阀门开度算错。
     */
    public GasLink withFlowScale(final double scale) {
        return new GasLink(aperture, bias, oneWay, filter, flow, scale, conductivity);
    }

    /** 导热率（每刻消掉的温差比例，0..1；与基础值取 max 后生效）。 */
    public GasLink withConductivity(final double value) {
        return new GasLink(aperture, bias, oneWay, filter, flow, flowScale, value);
    }

    public boolean isOpen() {
        return aperture > 0;
    }
}
