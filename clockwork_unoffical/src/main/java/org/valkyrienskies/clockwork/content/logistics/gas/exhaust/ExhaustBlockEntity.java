package org.valkyrienskies.clockwork.content.logistics.gas.exhaust;

import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.IAirCurrentSource;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.clockwork.content.logistics.gas.GasNodeBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.GasNodeTooltip;
import org.valkyrienskies.clockwork.content.logistics.gas.GasPhysics;
import org.valkyrienskies.clockwork.content.logistics.gas.GasType;
import org.valkyrienskies.clockwork.content.logistics.gas.GasTypes;

import java.util.List;

/**
 * 排气口方块实体：每刻把本节点内的气体全部排空（旧版行为），并把排气量接进 Create 的气流系统。
 *
 * <p>对应旧版 {@code ExhaustBlockEntity}：服务端把节点里所有气体移除，客户端按压力喷粒子。
 *
 * <p><b>Create 气流集成</b>（1.21.1 新增，旧版是另一套写法）：
 * 本类实现 Create 的 {@link IAirCurrentSource}，并自己维护一个 {@link AirCurrent}
 * —— 这正是 Create 6.0.10 里 {@code EncasedFanBlockEntity} 的做法（已用 javap 逐行核对
 * {@code EncasedFanBlockEntity#tick} 的调用序列）。于是排气口会：
 * <ul>
 *   <li>把气流沿 {@code FACING} 推出去，吹动实体与掉落物（{@code AirCurrent} 内部自己找实体）；</li>
 *   <li>让气流穿过沿途的"加工源"方块（点燃的营火 / 灵魂营火 / 装水的炼药锅等）而获得
 *       {@code FanProcessingType}（Create 在 {@code AirCurrent.rebuild()} 里对每格调用
 *       {@code FanProcessingType.getAt}），从而触发烟熏 / 洗刷 / 高炉等加工 ——
 *       这就是旧版"按温度切换烟熏/高炉"的等价效果，且完全走 Create 的正式 API，不需要 mixin。</li>
 * </ul>
 *
 * <p>风速：由节点压力相对环境压力换算，上限 128 RPM（Create 的风扇满速）。
 * 压力越高、排得越猛。没有气体时风速为 0，气流会被重建为空（不消耗性能）。
 *
 * <p>与旧版的差异：旧版按气体温度硬切换加工类型；本移植改为"由气流沿途的方块决定"，
 * 更贴合 Create 的机制，也让玩家能自己摆放营火/炼药锅来决定加工方式。
 */
public class ExhaustBlockEntity extends GasNodeBlockEntity implements IAirCurrentSource {

    /** 满风速 [RPM]（对齐 Create 风扇的最大转速）。 */
    private static final int MAX_RPM = 128;

    /** 当前风速 [RPM]，同步给客户端（客户端也要 tick 自己的气流才能看到效果）。 */
    private float airflowSpeed;
    private AirCurrent airCurrent;
    private int airCurrentUpdateCooldown;
    private int entitySearchCooldown;
    private boolean updateAirFlow = true;

    public ExhaustBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, 1.0);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) {
            return;
        }

        if (level.isClientSide) {
            tickAirCurrent();
            return;
        }

        // 服务端：先把节点里的气体排空（旧版行为），排气量决定风速
        final double pressure = gasMix().pressure(gasCapacityKg());
        if (!gasMix().isEmpty()) {
            final double temperature = gasMix().temperature();
            for (final GasType type : GasTypes.all()) {
                gasMix().removeMass(type, gasMix().massOf(type));
            }
            gasChanged();
            burnNearbyEntities(temperature);
        }
        final double ambient = GasPhysics.ambientPressure();
        final int speed = pressure <= ambient ? 0
                : (int) Math.min(MAX_RPM, (pressure - ambient) / ambient * MAX_RPM);

        if (speed != (int) airflowSpeed) {
            airflowSpeed = speed;
            updateAirFlow = true;
            sendData();
        }
        tickAirCurrent();
    }

    /**
     * Create 的 {@code EncasedFanBlockEntity#tick} 的气流部分（调用序列逐行照搬）。
     *
     * <p>为什么要在客户端也 tick：{@code AirCurrent} 自己负责找实体/推送/粒子，
     * 两端各跑一份才能让玩家看见并让服务端真正推动实体。
     */
    private void tickAirCurrent() {
        if (airCurrent == null) {
            airCurrent = new AirCurrent(this);
        }
        if (--airCurrentUpdateCooldown <= 0) {
            airCurrentUpdateCooldown = AllConfigs.server().kinetics.fanBlockCheckRate.get();
            updateAirFlow = true;
        }
        if (updateAirFlow) {
            updateAirFlow = false;
            airCurrent.rebuild();
        }
        if (airflowSpeed == 0) {
            return;
        }
        if (--entitySearchCooldown <= 0) {
            entitySearchCooldown = 5;
            airCurrent.findEntities();
        }
        airCurrent.tick();
    }

    /** 面向方向上的实体会被高温气体烫伤（每超过 350K 25K 半颗心，与加热器一致）。 */
    private void burnNearbyEntities(double temperature) {
        if (temperature < 350) {
            return;
        }
        final Direction facing = getBlockState().getValue(ExhaustBlock.FACING);
        final BlockPos target = getBlockPos().relative(facing);
        final float damage = (float) ((temperature - 350) / 25.0);
        for (final net.minecraft.world.entity.Entity entity
                : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,
                new net.minecraft.world.phys.AABB(target).inflate(0.5))) {
            entity.hurt(level.damageSources().hotFloor(), damage);
        }
    }

    // ===================== IAirCurrentSource =====================

    @Override
    public AirCurrent getAirCurrent() {
        if (airCurrent == null) {
            airCurrent = new AirCurrent(this);
        }
        return airCurrent;
    }

    @Override
    public Level getAirCurrentWorld() {
        return level;
    }

    @Override
    public BlockPos getAirCurrentPos() {
        return getBlockPos();
    }

    @Override
    public float getSpeed() {
        return airflowSpeed;
    }

    /** 气流的"起点面"：排气口正面。 */
    @Override
    public Direction getAirflowOriginSide() {
        return getBlockState().getValue(ExhaustBlock.FACING);
    }

    /** 气流方向与排气方向一致（气体往外吹）。 */
    @Override
    public Direction getAirFlowDirection() {
        return getBlockState().getValue(ExhaustBlock.FACING);
    }

    @Override
    public boolean isSourceRemoved() {
        return isRemoved();
    }

    // ===================== NBT / 护目镜 =====================

    @Override
    protected void write(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider,
                         boolean clientPacket) {
        tag.putFloat("AirflowSpeed", airflowSpeed);
        super.write(tag, provider, clientPacket);
    }

    @Override
    protected void read(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider,
                        boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        airflowSpeed = tag.getFloat("AirflowSpeed");
        updateAirFlow = true;
    }

    @Override
    public boolean addToGoggleTooltip(List<net.minecraft.network.chat.Component> tooltip, boolean isPlayerSneaking) {
        GasNodeTooltip.addFunctionLine(getBlockState().getBlock(), tooltip);
        tooltip.add(net.minecraft.network.chat.Component.literal("    ")
                .append(net.minecraft.network.chat.Component.literal("Airflow: ")
                        .withStyle(net.minecraft.ChatFormatting.GRAY))
                .append(net.minecraft.network.chat.Component.literal(String.format("%.0f RPM", airflowSpeed))
                        .withStyle(net.minecraft.ChatFormatting.AQUA)));
        return GasNodeTooltip.add(this, tooltip, isPlayerSneaking);
    }
}
