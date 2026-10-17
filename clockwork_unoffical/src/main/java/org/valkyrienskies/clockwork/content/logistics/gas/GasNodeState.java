package org.valkyrienskies.clockwork.content.logistics.gas;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;

/**
 * 节点状态：气体内容、容量、注册与同步。被 {@link GasNodeBlockEntity} 与
 * {@link GasKineticNodeBlockEntity} 组合复用（两类方块实体的父类不同，无法共用继承）。
 *
 * <p>对应旧版 {@code KNodeBlockEntity} 里"把数据存到 Kelvin 求解器 / 从求解器取回"的那部分职责；
 * 区别是本移植的状态就存在自己的字段里，不再有 {@code dataToLoad} 之类的延迟加载。
 *
 * <p>同步策略：仅服务端模拟。状态变化超过阈值时通过 {@code sendData()} 把气体内容写给客户端
 * （护目镜 / 后续渲染用）。旧版是客户端镜像整个 Kelvin 网络，这里退化为"每个节点自己同步自己"，
 * 网络拓扑（连接关系）由方块状态天然同步，因此不需要额外的包。
 */
public final class GasNodeState {

    /** 触发同步的阈值：压力变化 [Pa]、温度变化 [K]、质量变化比例。 */
    private static final double SYNC_PRESSURE_STEP = 500.0;
    private static final double SYNC_TEMPERATURE_STEP = 1.0;
    private static final double SYNC_MASS_RATIO = 0.05;

    private final SmartBlockEntity host;
    private final GasMix mix = new GasMix();
    /** 节点额定容量 [kg]。 */
    private final double capacityKg;
    /** 本刻穿过本节点的气体质量 [kg/tick]，由 {@link GasNetwork} 在搬运时累加（燃气机/流量显示用）。 */
    private double throughputPerTick;
    private boolean registered;
    private boolean dirty;

    // 上一次同步出去的值，用于抑制无意义的数据包
    private double syncedPressure = -1;
    private double syncedTemperature = -1;
    private double syncedMass = -1;

    public GasNodeState(SmartBlockEntity host, double capacityScale) {
        this.host = host;
        this.capacityKg = Math.max(1.0E-6, GasPhysics.nodeCapacityKg() * capacityScale);
    }

    public GasMix mix() {
        return mix;
    }

    public double capacityKg() {
        return capacityKg;
    }

    public BlockPos hostPos() {
        return host.getBlockPos();
    }

    public Level hostLevel() {
        return host.getLevel();
    }

    /** 默认连通规则：交给方块（{@link IGasNodeBlock}）裁决。 */
    public boolean blockAllowsConnection(Direction dir) {
        if (host.getLevel() == null) {
            return false;
        }
        BlockState state = host.getBlockState();
        Block block = state.getBlock();
        return !(block instanceof IGasNodeBlock nodeBlock) || nodeBlock.gasConnectable(state, dir);
    }

    /** 服务端每刻调用：驱动整个维度的网络模拟，并按需同步。 */
    public void serverTick() {
        Level level = host.getLevel();
        if (level == null || !((GasNode) host).gasNodeActive()) {
            return;
        }
        GasNetworks.get(level).tick(level);
        if (!registered) {
            GasNetworks.get(level).register((GasNode) host);
            registered = true;
        }
        if (dirty) {
            dirty = false;
            host.setChanged();
        }
    }

    /**
     * 本刻穿过本节点的气体质量 [kg/tick]。
     *
     * <p>语义对应旧版 Kelvin 的 {@code min(总流入, 总流出)}（"真正穿过去的气量"），
     * 但本移植用更省算力的等价量：进出两侧的搬运质量之和——单向管道与双向流动下，
     * 两者在数量级上一致，对"燃气机需要多少流量"的玩法判定足够。
     */
    public double throughputPerTick() {
        return throughputPerTick;
    }

    /** 由 {@link GasNetwork} 在每次搬运后调用，累加穿过的质量 [kg]。 */
    public void recordThroughput(double kg) {
        if (kg > 0) {
            throughputPerTick += kg;
        }
    }

    /** 由 {@link GasNetwork} 在每个连通域模拟开始时调用，清零上一刻的流量。 */
    public void resetThroughput() {
        throughputPerTick = 0;
    }

    /** 供 {@code lazyTick} 调用：变化够大就同步给客户端。 */
    public void syncIfChanged() {
        Level level = host.getLevel();
        if (level == null || level.isClientSide || !registered) {
            return;
        }
        double pressure = mix.pressure(capacityKg);
        double mass = mix.totalMass();
        double temperature = mix.temperature();
        boolean pressureChanged = Math.abs(pressure - syncedPressure) > SYNC_PRESSURE_STEP;
        boolean temperatureChanged = Math.abs(temperature - syncedTemperature) > SYNC_TEMPERATURE_STEP;
        boolean massChanged = Math.abs(mass - syncedMass) > Math.max(1.0E-4, syncedMass * SYNC_MASS_RATIO);
        if (!pressureChanged && !temperatureChanged && !massChanged) {
            return;
        }
        syncedPressure = pressure;
        syncedTemperature = temperature;
        syncedMass = mass;
        host.sendData();
    }

    /**
     * 立即把当前状态写给客户端（不等 {@link #syncIfChanged()} 的 10 刻阈值）。
     *
     * <p><b>为什么必须单独有这一条路径</b>：{@code syncIfChanged} 是"数值慢慢漂移"用的节流同步
     * （压力 500 Pa / 温度 1 K / 质量 5% 才发），而结构事件（多方块成型、拆分、容量变化）会让
     * 容量与压力**瞬间跳变**，且跳变后的值往往正好落在阈值内（例如拆分后容量减半、压力翻倍，
     * 但刻度上的差值判定用的是"上一次发出去的值"），于是客户端会一直显示上一个结构的读数，
     * 直到下一次阈值触发——实机表现就是"护目镜/压力要退出重进才对"。
     *
     * <p>因此结构变化必须走本方法：无论差值多少都发一次包，并把"已同步值"重置为当前值，
     * 免得随后的 lazyTick 又因为差值过大而重复发一次。
     */
    public void syncNow() {
        Level level = host.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        syncedPressure = mix.pressure(capacityKg);
        syncedTemperature = mix.temperature();
        syncedMass = mix.totalMass();
        host.sendData();
    }

    /** 本刻是否载有指定气体（滤管切换种类时用来挑"当前有的那种"）。 */
    public boolean contains(GasType type) {
        return mix.massOf(type) > 1.0E-6;
    }

    public void markDirty() {
        dirty = true;
    }

    public void unregister() {
        if (!registered) {
            return;
        }
        registered = false;
        Level level = host.getLevel();
        if (level != null) {
            GasNetworks.get(level).unregister((GasNode) host);
        }
    }

    public void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        mix.save(tag);
    }

    public void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        mix.load(tag);
        if (clientPacket) {
            // 客户端只做展示，同步来的值直接当作"已同步"
            syncedPressure = mix.pressure(capacityKg);
            syncedTemperature = mix.temperature();
            syncedMass = mix.totalMass();
        }
    }
}
