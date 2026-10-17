package org.valkyrienskies.clockwork.content.logistics.gas.duct;

import net.minecraft.util.StringRepresentable;

/**
 * 导管每个面的连接状态。
 *
 * <p>对应旧版 {@code DuctConnectionType}。<b>与旧版的差异</b>：旧版还有一个 {@code TEMP_ON}（"临时连通"）
 * 中间态，用于绕开 {@code neighborChanged()} 的时序问题。本移植的连接判定直接读邻居当前状态
 * （相邻导管默认互连、机器看 {@code gasConnectable}），不再需要这个中间态；
 * 去掉它的另一个好处是方块状态 JSON 里只需要 4 个值，不会出现"没有对应模型"的状态。
 *
 * <p>{@code getSerializedName()} 必须与资源里的方块状态 JSON 一致（"true"/"forced"/"false"/"leak"）。
 */
public enum DuctConnectionType implements StringRepresentable {

    /** 已连通（正常管道）。 */
    SIDE("true"),
    /** 被扳手强制关闭，邻居变化不会自动重连。 */
    FORCED_OFF("forced"),
    /** 未连通。 */
    NONE("false"),
    /** 破损泄漏：不导气，但每刻漏气（爆炸会把相邻导管打成这个状态）。 */
    LEAK("leak");

    private final String name;

    DuctConnectionType(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
