package org.valkyrienskies.clockwork.integration.cc;

import dan200.computercraft.api.lua.LuaFunction;
import org.valkyrienskies.clockwork.content.logistics.gas.valve.ValveDuctBlockEntity;

/**
 * 气体阀门外设（{@code valve_duct}）。
 *
 * <p>对应旧版 {@code integration/cc/ValveDuctPeripheral.kt}。差异：
 * <ul>
 *   <li>旧版阀门用 {@code computerTarget}（0..1 目标开度）+ {@code updateTarget()} 让电脑直接设定开度。
 *       本移植的阀门开度（{@link ValveDuctBlockEntity#openness()}）完全由输入轴转速驱动、
 *       没有独立的电脑目标值，因此这里只提供<b>只读</b>的 {@code getOpenness()} /
 *       {@code getLinkRadius()} / {@code getRPM()} / {@code isOpen()}：
 *       电脑读得到状态，但控制阀门仍然要靠转速（这是玩法上的有意差异，避免出现两套互相打架的开度来源）。</li>
 *   <li>旧版 {@code detach()} 会把阀门恢复成"无电脑控制"；这里没有可恢复的状态，故不实现。</li>
 * </ul>
 *
 * <p>本类只被 {@link ClockworkComputerCraft} 引用，而后者只在装了 CC:Tweaked 时被
 * {@code ClockworkMod} 条件加载（没装 CC 时不会进 JVM）。
 */
public class ValveDuctPeripheral extends ClockworkPeripheral<ValveDuctBlockEntity> {

    /** 与方块实体同源的通径补偿：{@code 通径 = 开度 - 0.3125}（旧版 {@code ApertureDuctEdge.radius}）。 */
    private static final double APERTURE_RADIUS = 0.3125;

    public ValveDuctPeripheral(final ValveDuctBlockEntity blockEntity) {
        super(blockEntity);
    }

    @Override
    public String getType() {
        return "cw_valve_duct";
    }

    /** 当前开度 0..1（0 = 全关）。客户端每 10 刻同步一次，读数可能有轻微滞后。 */
    @LuaFunction
    public double getOpenness() {
        return this.blockEntity.openness();
    }

    /** 当前有效通径 = 开度 - 0.3125，负数表示完全关闭。 */
    @LuaFunction
    public double getLinkRadius() {
        return this.blockEntity.openness() - APERTURE_RADIUS;
    }

    /** 输入轴转速 [RPM]；> 0 时阀门开，否则关。 */
    @LuaFunction
    public float getRPM() {
        return this.blockEntity.getSpeed();
    }

    @LuaFunction
    public boolean isOpen() {
        return this.blockEntity.openness() > APERTURE_RADIUS;
    }
}
