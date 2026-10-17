package org.valkyrienskies.clockwork.content.logistics.gas.intake;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.clockwork.config.ClockworkConfig;
import org.valkyrienskies.clockwork.content.logistics.gas.GasKineticNodeBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.GasNodeTooltip;
import org.valkyrienskies.clockwork.content.logistics.gas.GasPhysics;
import org.valkyrienskies.clockwork.content.logistics.gas.GasTypes;

import java.util.List;

/**
 * 进气口方块实体：把环境空气泵进网络。
 *
 * <p>见 {@link IntakeBlock} 的类头注释（本方块旧版没有实现源码，属重新设计）。
 *
 * <p>产量公式（沿用空气压缩机的口径，但只有空气、没有以太副产物、不受"上方是否开阔"限制）：
 * <pre>
 * 每刻空气质量 = 进气速度 · |RPM| · 环境空气密度
 * </pre>
 * {@code 进气速度} 复用配置 {@code airCompressorSpeed} 的一半（1.0E-4 → 5.0E-5 kg/(tick·RPM)），
 * 让进气口明显弱于压缩机；吸入温度取环境温度。
 */
public class IntakeBlockEntity extends GasKineticNodeBlockEntity {

    /** 相对空气压缩机的产量系数（压缩机为 1.0）。 */
    private static final double SPEED_FACTOR = 0.5;

    public IntakeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, 1.0);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }
        final double rpm = Math.abs(getSpeed());
        if (rpm <= 0) {
            return;
        }
        final double density = GasPhysics.ambientAirDensity(level, getBlockPos());
        if (density <= 0) {
            // 真空里没有空气可吸
            return;
        }
        final double mass = ClockworkConfig.server().airCompressorSpeed.get() * SPEED_FACTOR * rpm * density;
        if (mass <= 0) {
            return;
        }
        gasMix().setTemperature(GasPhysics.ambientTemperature());
        gasMix().addMass(GasTypes.AIR, mass);
        gasChanged();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ")
                .append(Component.literal("Intake: ").withStyle(net.minecraft.ChatFormatting.GRAY))
                .append(Component.literal(String.format("%.0f RPM", Math.abs(getSpeed())))
                        .withStyle(net.minecraft.ChatFormatting.AQUA)));
        return GasNodeTooltip.add(this, tooltip, isPlayerSneaking);
    }
}
