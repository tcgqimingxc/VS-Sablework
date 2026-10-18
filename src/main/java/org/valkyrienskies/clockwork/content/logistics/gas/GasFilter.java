package org.valkyrienskies.clockwork.content.logistics.gas;

/**
 * 一条连接上的<b>气体种类</b>过滤条件：白名单或黑名单，二选一。
 *
 * <p>对应旧版 {@code ClockworkFilteredDuctEdge} 的两个字段
 * （{@code filter: HashSet<GasType>} + {@code blacklist: Boolean}，
 * 见 {@code content/logistics/gas/filter/edges/ClockworkFilteredDuctEdge.kt:25-26}）。
 *
 * <p><b>与旧版的差异（为什么是"一种气体"而不是一个集合）</b>：旧版的集合由
 * {@code FilterScreen} 多选配置（{@code filter/FilterScreen.kt}），而本移植没有那个界面，
 * 过滤种类靠螺丝刀循环（见 {@code docs/DUCT-EDGE-VARIANTS.md} §3）。
 * 更关键的是搬运语义：{@link GasNetwork#transfer} 一次只搬一种气体，
 * 一个集合里"同时允许 N 种气"要么变成 N 次搬运抽样，要么变成按比例搬 ——
 * 两种都会让"过滤"的实际流量取决于气体种类数量，反而更难解释。
 * 所以这里把集合收缩成单值，白/黑名单保留，差异在文档 §4.2 明确列出。
 *
 * <p>不变式：{@code gas} 非 null（本类只描述"有过滤"的连接，不过滤用 null 表示）。
 */
public record GasFilter(GasType gas, boolean blacklist) {

    /** 白名单：只允许 {@code gas} 通过（旧版 {@code blacklist = false}）。 */
    public static GasFilter only(final GasType gas) {
        return new GasFilter(gas, false);
    }

    /** 黑名单：除 {@code gas} 外都允许通过（旧版 {@code blacklist = true}）。 */
    public static GasFilter except(final GasType gas) {
        return new GasFilter(gas, true);
    }
}
