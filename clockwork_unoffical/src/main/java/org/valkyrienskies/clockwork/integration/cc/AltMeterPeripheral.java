package org.valkyrienskies.clockwork.integration.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import org.valkyrienskies.clockwork.content.curiosities.altmeter.AltMeterBlockEntity;

import java.util.Arrays;
import java.util.Locale;

/**
 * 高度计外设（{@code alt_meter}）。
 *
 * <p>对应旧版 {@code integration/cc/AltMeterPeripheral.kt}。差异：
 * <ul>
 *   <li>旧版 {@code getHeight()} 通过 VS2 的 {@code getLoadedShipManagingPos} 取高度；
 *       这里改为 {@link AltMeterBlockEntity#getCurrentHeight()} —— 它内部已经用 Sable 的
 *       {@code SubLevel#logicalPose()} 做了世界坐标换算（旧版那段其实漏接返回值，见方块实体类注释）。</li>
 *   <li>旧版 {@code setDirection} 用 {@code AltMeterDirection.valueOf(...)} 抛异常；
 *       这里在枚举里查找并统一抛 {@link LuaException}（对外错误信息一致）。</li>
 *   <li>旧版直接改字段 + {@code notifyUpdate()}；这里走方块实体的 setter（自动标脏 + 同步 + 刷新红石）。</li>
 * </ul>
 *
 * <p>本类只被 {@link ClockworkComputerCraft} 引用，而后者只在装了 CC:Tweaked 时被
 * {@code ClockworkMod} 条件加载 —— 没装 CC 时这两个类都不会进 JVM（惰性解析）。
 */
public class AltMeterPeripheral extends ClockworkPeripheral<AltMeterBlockEntity> {

    public AltMeterPeripheral(final AltMeterBlockEntity blockEntity) {
        super(blockEntity);
    }

    @Override
    public String getType() {
        return "cw_alt_meter";
    }

    /** 当前世界高度（整数，与护目镜显示一致）。 */
    @LuaFunction
    public int getHeight() {
        return this.blockEntity.getCurrentHeight();
    }

    /** 当前红石输出强度 0..15。 */
    @LuaFunction
    public int getOutput() {
        return this.blockEntity.getSignalStrength();
    }

    @LuaFunction
    public int getTargetHeight() {
        return this.blockEntity.getTriggerHeight();
    }

    @LuaFunction
    public void setTargetHeight(final int height) {
        this.blockEntity.setTriggerHeight(height);
    }

    @LuaFunction
    public int getSensitivity() {
        return this.blockEntity.getTriggerSensitivity();
    }

    @LuaFunction
    public void setSensitivity(final int sensitivity) {
        this.blockEntity.setTriggerSensitivity(sensitivity);
    }

    @LuaFunction
    public String getDirection() {
        return this.blockEntity.getTriggerDirection().name();
    }

    @LuaFunction
    public void setDirection(final String direction) throws LuaException {
        for (final AltMeterBlockEntity.TriggerDirection candidate : AltMeterBlockEntity.TriggerDirection.values()) {
            if (candidate.name().equals(direction.toUpperCase(Locale.ROOT))) {
                this.blockEntity.setTriggerDirection(candidate);
                return;
            }
        }
        throw new LuaException("Direction must be one of: "
                + String.join(", ", Arrays.stream(AltMeterBlockEntity.TriggerDirection.values())
                        .map(Enum::name).toList()));
    }
}
