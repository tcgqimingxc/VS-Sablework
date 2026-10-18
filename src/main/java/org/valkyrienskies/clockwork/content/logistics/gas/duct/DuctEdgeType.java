package org.valkyrienskies.clockwork.content.logistics.gas.duct;

import net.minecraft.util.StringRepresentable;

/**
 * 导管<b>一个面</b>的"边类型"：这一面的连接按什么规则走气。
 *
 * <p>对应旧版 {@code DuctEdgeType}
 * （{@code Clockwork-1.20.1-main/common/.../content/logistics/gas/duct/DuctEdgeType.kt:16-23}）。
 * 旧版有六个值：{@code NONE / PIPE / ONEWAY_FORWARD / ONEWAY_BACKWARD / FILTERED / SMART}。
 *
 * <p><b>与旧版的枚举差异</b>：旧版的 {@code NONE} 与 {@code PIPE} 区分"这一面没有边 / 普通管道边"，
 * 本移植的"这一面有没有连接"由方块状态 {@link DuctConnectionType} 表达
 * （{@code NONE / SIDE / FORCED_OFF / LEAK}），所以这里只保留"已连接的面怎么走气"的五个值，
 * 并把 {@code PIPE} 改名为 {@link #NORMAL} —— 否则同一份代码里会有两个叫 PIPE/NONE 的枚举，
 * 读代码时必须先确认是哪一个，属于纯粹的命名陷阱。
 *
 * <p>循环顺序与旧版 {@code nextScrewdrivable()} 逐字一致（PIPE→ONEWAY_FORWARD→ONEWAY_BACKWARD→
 * FILTERED→SMART→PIPE）：螺丝刀右键就是按这个顺序切，见 {@link DuctEdgeInteraction}。
 *
 * <p><b>方向约定（改语义前必读）</b>：单向类型以<b>被点击的这一面</b>为参照，而不是旧版那种
 * "nodeA→nodeB + 坐标大小"的全局约定：
 * <ul>
 *   <li>{@link #ONEWAY_FORWARD} = 气体可以从本导管<b>经这一面流出</b>（这一面是出口）；</li>
 *   <li>{@link #ONEWAY_BACKWARD} = 气体可以从对侧<b>经这一面流入</b>本导管（这一面是入口）。</li>
 * </ul>
 * 旧版 {@code setEdgeType} 用 {@code magnitudeSqr()} 决定 nodeA/nodeB 的顺序
 * （{@code DuctBlockEntity.kt:126-133}），对负坐标并不对称；本移植把方向钉在"哪一面"上，
 * 才不会出现"同一根管子东边能出、西边也能出"这种只能读源码才能解释的行为。
 */
public enum DuctEdgeType implements StringRepresentable {

    /** 普通管道边（旧版 {@code PIPE}）：只受通径/压差约束，没有额外规则。 */
    NORMAL("normal"),
    /** 单向·出口面（旧版 {@code ONEWAY_FORWARD}）：只允许气体从本导管经这一面流出。 */
    ONEWAY_FORWARD("oneway_forward"),
    /** 单向·入口面（旧版 {@code ONEWAY_BACKWARD}）：只允许气体从对侧经这一面流入本导管。 */
    ONEWAY_BACKWARD("oneway_backward"),
    /** 过滤边（旧版 {@code FILTERED}）：按气体种类白/黑名单拦截。 */
    FILTERED("filtered"),
    /** 智能边（旧版 {@code SMART}）：按压差 / 温度阈值开关；旧版外观就是铜接头（见 {@link DuctPartials}）。 */
    SMART("smart");

    private static final DuctEdgeType[] VALUES = values();

    private final String name;

    DuctEdgeType(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** 旧版 {@code DuctEdgeType.nextScrewdrivable()}：螺丝刀右键的循环顺序。 */
    public DuctEdgeType next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    /** NBT 反序列化：未知/缺失值回落到 {@link #NORMAL}（旧存档/坏数据不应让方块实体加载失败）。 */
    public static DuctEdgeType byName(final String name) {
        for (DuctEdgeType type : VALUES) {
            if (type.name.equals(name)) {
                return type;
            }
        }
        return NORMAL;
    }
}
