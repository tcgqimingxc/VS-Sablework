package org.valkyrienskies.clockwork.content.logistics.gas.nozzle;

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.valkyrienskies.clockwork.config.ClockworkConfig;
import org.valkyrienskies.clockwork.content.logistics.CWLogisticsSounds;
import org.valkyrienskies.clockwork.content.logistics.gas.GasMix;
import org.valkyrienskies.clockwork.content.logistics.gas.GasNodeBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.GasNodeTooltip;
import org.valkyrienskies.clockwork.content.logistics.gas.GasPhysics;
import org.valkyrienskies.clockwork.platform.CWForces;

import java.util.List;

/**
 * 气嘴方块实体：把节点里的气体沿 {@code FACING} 喷出，反作用力推动所在子空间。
 *
 * <p><b>重新设计说明（重要）</b>：旧版 {@code GasNozzleBlockEntity}（Kotlin）依赖 VS2 的气囊体系
 * —— {@code GasNozzleInstance} / {@code BalloonStatusPacket} / {@code LeakParticle} / 循环音效，
 * 它做的是"往船体上的孤立空气块（balloon pocket）里灌热气、给热气球加热"这件事。
 * 1.21.1 的 Sable 没有 VS2 那种"船上的孤立空气块"概念（也没有 {@code shipObjectWorld} 空气组分），
 * 那套实现无法移植，因此按任务要求<b>重新设计为定向喷气推进器</b>：
 * <ol>
 *   <li>喷气方向 = {@code FACING}，反作用推力 = {@code -FACING}（交给 {@link CWForces} 的 PROPULSION 分组）；</li>
 *   <li>喷气量由节点压力与环境压力之差决定（公式与 {@code gas_thruster} 同一套，见下面 {@link #updateJet()}）；</li>
 *   <li>按温度喷不同粒子，并把喷流里的实体烫伤（阈值与伤害公式沿用排气口 {@code ExhaustBlockEntity}）；</li>
 *   <li>去掉旧版的"转速 → 开度 pointer"：本实现<b>有气压就喷</b>，压力越大推力越大。</li>
 * </ol>
 *
 * <p>与 {@code GasThrusterBlockEntity} 的差异（刻意的，避免两个方块手感重复）：气嘴的 {@code FACING}
 * 是<b>喷气</b>方向、推力反向；推进器的 {@code FACING} 直接就是<b>推力</b>方向（喷口在背面）。
 * 两者共用同一个推力倍率配置 {@code gasThrusterForceMultiplier}。
 */
public class GasNozzleBlockEntity extends GasNodeBlockEntity implements BlockEntitySubLevelActor {

    /** 喷口面积 [m²]（旧版 {@code ClockworkConstants.Misc.DUCT_AREA}，按 0.1 m 半径）。 */
    private static final double JET_AREA = 0.01;

    /** 通用气体常数 [J/(kg·K)]（旧版 {@code AerodynamicUtils.UNIVERSAL_GAS_CONSTANT}）。 */
    private static final double GAS_CONSTANT = 287.05;

    /** 本刻最多喷出"可用气量"的比例（与 gas_thruster 的 FLOW_RATIO 一致）。 */
    private static final double FLOW_RATIO = 0.5;

    /** 小于该流量视为 0 [kg/tick]。 */
    private static final double MIN_FLOW = 1.0E-9;

    /** 烫伤阈值 [K] 与伤害系数（每高出 25 K 半颗心，与排气口一致）。 */
    private static final double BURN_TEMPERATURE = 350.0;
    private static final double BURN_DAMAGE_PER_K = 25.0;

    /** 喷流的作用范围：沿 FACING 长 4 格、半径 0.75 格的圆柱。 */
    private static final int JET_LENGTH = 4;
    private static final double JET_RADIUS = 0.75;

    /** 本刻推力 [N]（服务端算；客户端只用于粒子）。 */
    private double thrust;
    /** 本刻质量流量 [kg/tick]，护目镜显示与粒子密度都用它。 */
    private double massFlow;
    /** 喷出的气体温度 [K]。 */
    private double jetTemperature = GasPhysics.ambientTemperature();

    /** 已同步给客户端的推力（取整），用于抑制无意义的数据包。 */
    private int syncedThrust = Integer.MIN_VALUE;
    /** 上一刻是否在喷气，用于只播一次"开启"音效。 */
    private boolean jetting;

    public GasNozzleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, 1.0);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) {
            return;
        }
        if (level.isClientSide) {
            emitJetParticles();
        } else {
            updateJet();
        }
    }

    /** 服务端每刻：按压力喷气、扣气体、算推力、烫伤喷流里的实体。 */
    private void updateJet() {
        final GasMix mix = gasMix();
        final double temperature = mix.temperature();
        if (mix.isEmpty()) {
            setJet(0, 0, temperature);
            return;
        }
        final double pressure = mix.pressure(gasCapacityKg());
        // 环境压力按海拔衰减（与 gas_thruster 一致：低空才喷得动）
        final double ambient = GasPhysics.ambientPressure() * GasPhysics.ambientAirDensity(level, getBlockPos());
        if (pressure <= ambient) {
            setJet(0, 0, temperature);
            return;
        }

        final double specificHeat = Math.max(1.0E-6, mix.specificHeatOfMix());
        final double pressureRatio = pressure / ambient;
        // 喷管最大质量流量：A·P/√T ·√(压力比)（与 gas_thruster 同一推导，量级一致）
        final double maxFlow = JET_AREA * pressure / Math.sqrt(temperature) * Math.sqrt(pressureRatio);
        // 可喷出的气量：优先用本刻穿过本节点的气量，没有流动时按压力抽走一部分
        final double available = Math.max(gasState().throughputPerTick(), mix.totalMass() * 0.05);
        final double flow = Math.min(maxFlow, available * FLOW_RATIO);
        if (flow <= MIN_FLOW) {
            setJet(0, 0, temperature);
            return;
        }

        // 按组分等比抽走（GasMix#extract 与 gas_thruster 的处理一致），热量随气体一起被带走
        mix.extract(flow);
        gasChanged();

        final double exhaustVelocity = Math.sqrt(specificHeat * temperature / GAS_CONSTANT) * pressureRatio;
        final double newThrust = (flow * exhaustVelocity + (pressure - ambient) * JET_AREA)
                * ClockworkConfig.server().gasThrusterForceMultiplier.get();
        setJet(newThrust, flow, temperature);
        burnJetTargets(temperature);
    }

    private void setJet(double newThrust, double newFlow, double temperature) {
        thrust = newThrust;
        massFlow = newFlow;
        jetTemperature = temperature;
        if (!jetting && newThrust > 0 && level != null) {
            level.playSound(null, getBlockPos(), CWLogisticsSounds.GAS_NOZZLE_START, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        jetting = newThrust > 0;
        final int quantized = (int) newThrust;
        if (quantized != syncedThrust) {
            syncedThrust = quantized;
            sendData();
        }
    }

    /** 喷流里超过 350 K 的实体会被烫伤（每刻结算，与排气口 {@code ExhaustBlockEntity#burnNearbyEntities} 同量级）。 */
    private void burnJetTargets(double temperature) {
        if (temperature < BURN_TEMPERATURE) {
            return;
        }
        final Direction facing = getBlockState().getValue(GasNozzleBlock.FACING);
        final AABB area = new AABB(getBlockPos()).inflate(JET_RADIUS)
                .expandTowards(facing.getStepX() * JET_LENGTH, facing.getStepY() * JET_LENGTH, facing.getStepZ() * JET_LENGTH);
        final float damage = (float) ((temperature - BURN_TEMPERATURE) / BURN_DAMAGE_PER_K);
        for (final Entity entity : level.getEntitiesOfClass(Entity.class, area)) {
            if (!entity.fireImmune()) {
                entity.hurt(level.damageSources().hotFloor(), damage);
            }
        }
    }

    /** 客户端粒子：温度决定粒子种类，流量决定密度，压力越大喷得越快。 */
    private void emitJetParticles() {
        if (massFlow <= MIN_FLOW || !(getBlockState().getBlock() instanceof GasNozzleBlock)) {
            return;
        }
        final Direction facing = getBlockState().getValue(GasNozzleBlock.FACING);
        final RandomSource random = level.random;
        final Vec3 mouth = Vec3.atCenterOf(getBlockPos())
                .add(facing.getStepX() * 0.6, facing.getStepY() * 0.6, facing.getStepZ() * 0.6);
        final double speed = Mth.clamp(0.05 + massFlow * 40.0, 0.05, 0.8);
        final ParticleOptions particle = jetTemperature > 800.0 ? ParticleTypes.FLAME
                : jetTemperature > BURN_TEMPERATURE ? ParticleTypes.SMALL_FLAME : ParticleTypes.CLOUD;
        final int count = Mth.clamp((int) (massFlow * 200.0), 1, 8);
        for (int i = 0; i < count; i++) {
            level.addParticle(particle,
                    mouth.x + (random.nextDouble() - 0.5) * 0.2,
                    mouth.y + (random.nextDouble() - 0.5) * 0.2,
                    mouth.z + (random.nextDouble() - 0.5) * 0.2,
                    facing.getStepX() * speed + (random.nextDouble() - 0.5) * 0.03,
                    facing.getStepY() * speed + (random.nextDouble() - 0.5) * 0.03,
                    facing.getStepZ() * speed + (random.nextDouble() - 0.5) * 0.03);
        }
        if (random.nextInt(20) == 0) {
            level.playSound(null, getBlockPos(), CWLogisticsSounds.GAS_HISS, SoundSource.BLOCKS, 0.4F,
                    0.8F + random.nextFloat() * 0.4F);
        }
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (thrust <= 0) {
            return;
        }
        final Direction facing = getBlockState().getValue(GasNozzleBlock.FACING);
        // 反作用力：气体朝 FACING 喷出，结构被推向 -FACING
        final Vector3d force = new Vector3d(-facing.getStepX(), -facing.getStepY(), -facing.getStepZ()).mul(thrust);
        final Vector3d position = new Vector3d(getBlockPos().getX() + 0.5, getBlockPos().getY() + 0.5,
                getBlockPos().getZ() + 0.5);
        CWForces.pointForce(subLevel, CWForces.propulsion(), position, force, timeStep);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        tag.putDouble("Thrust", thrust);
        tag.putDouble("MassFlow", massFlow);
        tag.putDouble("JetTemperature", jetTemperature);
        super.write(tag, provider, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        thrust = tag.getDouble("Thrust");
        massFlow = tag.getDouble("MassFlow");
        if (tag.contains("JetTemperature")) {
            jetTemperature = tag.getDouble("JetTemperature");
        }
        if (clientPacket) {
            syncedThrust = (int) thrust;
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        GasNodeTooltip.addFunctionLine(getBlockState().getBlock(), tooltip);
        tooltip.add(Component.literal("    ")
                .append(Component.literal("Jet: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.format("%.0f N", thrust)).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(String.format(" / %.4f kg/tick", massFlow)).withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.literal("    ")
                .append(Component.literal("Jet Temperature: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.format("%.1f K", jetTemperature)).withStyle(ChatFormatting.GOLD)));
        return GasNodeTooltip.add(this, tooltip, isPlayerSneaking);
    }
}
