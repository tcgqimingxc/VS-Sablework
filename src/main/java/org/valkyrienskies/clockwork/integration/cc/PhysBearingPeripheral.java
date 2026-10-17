package org.valkyrienskies.clockwork.integration.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import net.minecraft.core.Direction;
import org.valkyrienskies.clockwork.content.contraptions.phys.bearing.PhysBearingBlock;
import org.valkyrienskies.clockwork.content.contraptions.phys.bearing.PhysBearingBlockEntity;

import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

/**
 * 物理轴承外设（{@code phys_bearing}）。
 *
 * <p>对应旧版 {@code integration/cc/PhysBearingPeripheral.kt}。差异：
 * <ul>
 *   <li>旧版 {@code getRPM()} 直接读 {@code be.speed}；这里用 Create 的
 *       {@code KineticBlockEntity#getSpeed()}（同一个值，负值 = 反向）。</li>
 *   <li>旧版的 {@code setFollowAngleMode()} → {@code FOLLOW_ANGLE} 模式在 Sable 上没有对应实现
 *       （见 {@link PhysBearingBlockEntity} 类注释），最接近的档位是 {@code ROTATE}（跟随输入轴），
 *       因此本移植提供 {@code setRotateMode()}；{@code setUnlockedMode()} 映射到 {@code FREE}。</li>
 *   <li>旧版 {@code getConnectedToShip()} 返回 VS2 的 ship id（整数）；Sable 的子空间 id 是 UUID，
 *       这里返回 UUID 字符串，未装配时返回 {@code nil}（Lua 里判空更自然）。</li>
 *   <li>旧版 {@code isBeingDisassembled()} 读 {@code aligning}；本移植没有"对齐中"这个中间态，
 *       改为 {@code isAssembled()} 的反面语义由 Lua 自行判断，故不提供该函数。</li>
 * </ul>
 *
 * <p>本类只被 {@link ClockworkComputerCraft} 引用，而后者只在装了 CC:Tweaked 时被
 * {@code ClockworkMod} 条件加载（没装 CC 时不会进 JVM）。
 */
public class PhysBearingPeripheral extends ClockworkPeripheral<PhysBearingBlockEntity> {

    public PhysBearingPeripheral(final PhysBearingBlockEntity blockEntity) {
        super(blockEntity);
    }

    @Override
    public String getType() {
        return "cw_phys_bearing";
    }

    @LuaFunction
    public void assemble() {
        if (!this.blockEntity.isAssembled()) {
            this.blockEntity.toggleAssembled();
        }
    }

    @LuaFunction
    public void disassemble() {
        if (this.blockEntity.isAssembled()) {
            this.blockEntity.toggleAssembled();
        }
    }

    @LuaFunction
    public boolean isAssembled() {
        return this.blockEntity.isAssembled();
    }

    /** 目标角度 [度]，累积可超过 360。 */
    @LuaFunction
    public double getTargetAngle() {
        return this.blockEntity.getTargetAngleDegrees();
    }

    @LuaFunction
    public void setAngle(final double degrees) {
        this.blockEntity.setTargetAngleDegrees(degrees);
    }

    /** 输入轴转速 [RPM]，负值表示反向。 */
    @LuaFunction
    public float getRPM() {
        return this.blockEntity.getSpeed();
    }

    @LuaFunction
    public String getFacingDirection() {
        final Direction facing = this.blockEntity.getBlockState().getValue(PhysBearingBlock.FACING);
        return facing.getName();
    }

    /** 当前模式名：{@code ROTATE} / {@code LOCK} / {@code FREE}。 */
    @LuaFunction
    public String getRotationMode() {
        return this.blockEntity.getRotationMode().name();
    }

    @LuaFunction
    public void setRotationMode(final String mode) throws LuaException {
        for (final PhysBearingBlockEntity.RotationMode candidate : PhysBearingBlockEntity.RotationMode.values()) {
            if (candidate.name().equals(mode.toUpperCase(Locale.ROOT))) {
                this.blockEntity.setRotationMode(candidate);
                return;
            }
        }
        throw new LuaException("Mode must be one of: "
                + String.join(", ", Arrays.stream(PhysBearingBlockEntity.RotationMode.values())
                        .map(Enum::name).toList()));
    }

    /** 沿用旧版的方法名：跟随输入轴旋转。 */
    @LuaFunction
    public void setRotateMode() {
        this.blockEntity.setRotationMode(PhysBearingBlockEntity.RotationMode.ROTATE);
    }

    /** 沿用旧版的方法名：自由铰链（不驱动）。 */
    @LuaFunction
    public void setUnlockedMode() {
        this.blockEntity.setRotationMode(PhysBearingBlockEntity.RotationMode.FREE);
    }

    /** 已装配结构的 UUID 字符串；未装配返回 {@code null}（Lua 里是 {@code nil}）。 */
    @LuaFunction
    public String getSubLevelId() {
        final UUID id = this.blockEntity.getSubLevelID();
        return id == null ? null : id.toString();
    }
}
