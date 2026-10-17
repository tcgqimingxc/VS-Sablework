package org.valkyrienskies.clockwork.content.logistics.gas;

import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * 一条气体连接的参数。
 *
 * <p>对应旧版 Kelvin 的 {@code DuctEdge}（PIPE / ONEWAY / APERTURE / PUMP 等子类）。
 * 旧版把连接建成独立 Edge 对象存进求解器；本移植把参数挂在节点上按方向查询，
 * 省掉一整套边对象与其生命周期管理。
 *
 * @param aperture 通径 0..1（1 = 全开）。阀门按开度给出，其他方块恒为 1。
 * @param bias     附加压差 [Pa]，<b>正值表示沿"本节点 → 邻居"方向推动</b>。
 *                 泵给两侧各一个相反的 bias（进气侧为负、出气侧为正），从而把气体从背面推到正面。
 * @param oneWay   是否单向：为 true 时只允许沿 bias 的方向流动（bias = 0 时不做限制）。
 * @param filter   过滤的气体种类；非 null 时这条连接只搬运该气体（滤管用）。
 *                 对应旧版 Kelvin 的 {@code ClockworkFilteredDuctEdge}。
 */
public record GasLink(double aperture, double bias, boolean oneWay, @Nullable GasType filter) {

    /** 普通管道连接。 */
    public static final GasLink PIPE = new GasLink(1.0, 0.0, false, null);

    /** 不可通行的连接（通径 0）。 */
    public static final GasLink CLOSED = new GasLink(0.0, 0.0, false, null);

    public GasLink {
        aperture = Mth.clamp(aperture, 0.0, 1.0);
    }

    /** 不过滤的便捷构造（泵 / 阀门等老调用点继续可用）。 */
    public GasLink(final double aperture, final double bias, final boolean oneWay) {
        this(aperture, bias, oneWay, null);
    }

    /** 带过滤的连接（其余参数同普通管道）。 */
    public static GasLink filtered(final GasType type) {
        return new GasLink(1.0, 0.0, false, type);
    }

    public boolean isOpen() {
        return aperture > 0;
    }
}
