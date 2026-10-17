package org.valkyrienskies.clockwork.content.logistics.gas.compressor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.clockwork.config.ClockworkConfig;
import org.valkyrienskies.clockwork.content.logistics.gas.GasKineticNodeBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.GasPhysics;
import org.valkyrienskies.clockwork.content.logistics.gas.GasTypes;

/**
 * 空气压缩机方块实体。
 *
 * <p>对应旧版 {@code AirCompressorBlockEntity}。每刻（有转速且上方为空气时）：
 * <pre>
 * 空气质量 = airCompressorSpeed · |RPM|
 * 以太占比 = max(0, 1 - 空气密度 / airCompressorHeliumAirDensity)
 * </pre>
 * 空气与以太按 {@code getAirTemperature()} 注入自己的节点；节点压力超过
 * {@code airCompressorMaxPressure} 时停机（旧版状态机里的 EXHAUSTED）。
 *
 * <p>与旧版的差异：
 * <ul>
 *   <li>旧版用 VS2 的 {@code aerodynamicUtils} 取空气密度/温度；这里用
 *       {@link GasPhysics#ambientAirDensity}（海拔模型）与环境温度配置。</li>
 *   <li>旧版有 4 态状态机并同步给客户端做贴图/粒子（ACTIVE/INACTIVE/EXHAUSTED/OBSTRUCTED）；
 *       这里保留同样的 4 个状态并同步（后续渲染可用），但没有做压缩机的动画渲染器。</li>
 * </ul>
 */
public class AirCompressorBlockEntity extends GasKineticNodeBlockEntity {

    /** 状态机：对应旧版 {@code CompressorStatus}。 */
    public enum CompressorStatus {
        ACTIVE,
        INACTIVE,
        EXHAUSTED,
        OBSTRUCTED
    }

    private CompressorStatus status = CompressorStatus.INACTIVE;

    // ==================== 客户端动画状态 ====================
    // 旧版 AirCompressorBlockEntity 里叫 clientSize / clientParticles，由 AirCompressorRenderer
    // 每帧推进（服务端不写、不同步）。这两个字段必须放在 BE 上而不是渲染器里：
    // BlockEntityRendererProvider 对一种 BE 只创建一个渲染器实例，所有压缩机共用它，
    // 状态放进渲染器会被别的方块覆盖。

    /** 风箱充气进度：0 = 完全瘪，1 = 完全鼓。 */
    private float clientInflate;
    /** 本充气循环是否已经放过"充饱"的粒子与音效（旧版 clientParticles）。 */
    private boolean clientBurst;

    public AirCompressorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, 1.0);
    }

    public CompressorStatus status() {
        return status;
    }

    public float getClientInflate() {
        return clientInflate;
    }

    public void setClientInflate(float value) {
        clientInflate = Mth.clamp(value, 0f, 1f);
    }

    public boolean hasClientBurst() {
        return clientBurst;
    }

    public void setClientBurst(boolean value) {
        clientBurst = value;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }

        double rpm = Math.abs(getSpeed());
        if (!level.getBlockState(getBlockPos().above()).isAir()) {
            setStatus(CompressorStatus.OBSTRUCTED);
            return;
        }
        if (rpm <= 0) {
            setStatus(CompressorStatus.INACTIVE);
            return;
        }
        if (gasMix().pressure(gasCapacityKg()) >= ClockworkConfig.server().airCompressorMaxPressure.get()) {
            setStatus(CompressorStatus.EXHAUSTED);
            return;
        }

        double density = GasPhysics.ambientAirDensity(level, getBlockPos());
        double heliumShare = Math.max(0.0, 1.0 - density / ClockworkConfig.server().airCompressorHeliumAirDensity.get());
        double deltaMass = ClockworkConfig.server().airCompressorSpeed.get() * rpm;
        double temperature = GasPhysics.ambientTemperature();

        gasMix().setTemperature(temperature);
        gasMix().addMass(GasTypes.AIR, (1.0 - heliumShare) * deltaMass);
        if (heliumShare > 0) {
            gasMix().addMass(GasTypes.AETHER, heliumShare * deltaMass);
        }
        gasChanged();
        setStatus(CompressorStatus.ACTIVE);
    }

    private void setStatus(CompressorStatus newStatus) {
        if (status == newStatus) {
            return;
        }
        status = newStatus;
        sendData();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        tag.putString("Status", status.name());
        super.write(tag, provider, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        status = tag.getString("Status").isEmpty()
                ? CompressorStatus.INACTIVE
                : CompressorStatus.valueOf(tag.getString("Status"));
    }
}
