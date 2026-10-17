package org.valkyrienskies.clockwork.content.logistics.gas.thruster;

import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.valkyrienskies.clockwork.config.ClockworkConfig;
import org.valkyrienskies.clockwork.content.logistics.gas.GasMix;
import org.valkyrienskies.clockwork.content.logistics.gas.GasNodeBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.GasNodeTooltip;
import org.valkyrienskies.clockwork.content.logistics.gas.GasPhysics;
import org.valkyrienskies.clockwork.content.logistics.gas.GasType;
import org.valkyrienskies.clockwork.content.logistics.gas.GasTypes;
import org.valkyrienskies.clockwork.platform.CWForces;

import java.util.List;

/**
 * 气体推进器方块实体：把节点里的气体喷出去，换来对所在子空间的推力。
 *
 * <p>对应旧版 {@code GasThrusterBlockEntity}（实现 VS2 的 {@code BlockEntityPhysicsListener}）。
 * 旧版公式（原样保留其结构，单位与常量在下面注明）：
 * <pre>
 * 最大质量流量 = 喷口面积 · 压力 / √温度 · √(比热/通用气体常数) · ((比热+1)/2)^(-(比热+1)/(2(比热-1)))
 * 实际流量     = min(最大质量流量, 本刻穿过的气量)
 * 喷气速度     = 流量 / (气体密度 · 喷口面积)
 * 推力         = (流量 · 喷气速度 + (节点压力 - 环境压力)) · 力倍率        // 旧版 gasThrusterForceMul = 200
 * </pre>
 * 其中"比热/通用气体常数"这一整段在旧版是 {@code ClockworkConstants.Misc.DUCT_AREA} 与
 * {@code AerodynamicUtils.UNIVERSAL_GAS_CONSTANT} 的组合；本移植把它收拢成
 * {@link #exhaustVelocity} 一个可读的表达式：
 * <b>喷气速度 = √(比热 × 温度) × 压力比</b>，压力比用"节点压力/环境压力"近似。
 * 这是对旧版公式的<b>重新推导</b>（旧版那段引用了 1.21.1 不存在的 VS2 工具类），
 * 量级一致、随温度与压力的单调性与旧版相同。
 *
 * <p>施力方式：旧版 {@code physShip.applyModelForce(force, blockPos)}；
 * 本移植在 {@link #sable$physicsTick} 里用 {@link CWForces#pointForce} 向
 * {@code ForceGroups.PROPULSION} 记一笔点力（作用点 = 方块中心）。
 */
public class GasThrusterBlockEntity extends GasNodeBlockEntity implements BlockEntitySubLevelActor {

    /** 喷口面积 [m²]（旧版 {@code ClockworkConstants.Misc.DUCT_AREA}，按 0.1 m 半径的导管）。 */
    private static final double DUCT_AREA = 0.01;

    /** 通用气体常数 [J/(kg·K)] 量级（旧版 {@code AerodynamicUtils.UNIVERSAL_GAS_CONSTANT}）。 */
    private static final double GAS_CONSTANT = 287.05;

    /** 本刻喷出的质量占"穿过本节点气量"的比例上限。 */
    private static final double FLOW_RATIO = 0.5;

    /** 本刻计算出的推力 [N]，物理子步里使用（服务端与客户端各自算，客户端只用于显示）。 */
    private double thrust;
    /** 本刻质量流量 [kg/tick]，护目镜显示用。 */
    private double massFlow;

    public GasThrusterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, 1.0);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }
        final GasMix mix = gasMix();
        if (mix.isEmpty()) {
            massFlow = 0;
            thrust = 0;
            return;
        }

        final double pressure = mix.pressure(gasCapacityKg());
        final double ambient = GasPhysics.ambientPressure() * GasPhysics.ambientAirDensity(level, getBlockPos());
        if (pressure <= ambient) {
            // 环境压力不低于节点压力 → 气体出不去（旧版同样在此清空流量）
            massFlow = 0;
            thrust = 0;
            return;
        }

        final double temperature = mix.temperature();
        final double specificHeat = Math.max(1.0E-6, mix.specificHeatOfMix());
        final double pressureRatio = pressure / ambient;
        final double maxFlow = DUCT_AREA * pressure / Math.sqrt(temperature) * Math.sqrt(pressureRatio);
        // 可喷出的气量：优先用"本刻穿过本节点的气量"，没有流动时退化为"按压力比抽走一部分"
        final double available = Math.max(gasState().throughputPerTick(), mix.totalMass() * 0.05);
        massFlow = Math.min(maxFlow, available * FLOW_RATIO);
        if (massFlow <= 1.0E-9) {
            thrust = 0;
            return;
        }

        removeGasProportionally(mix, massFlow);
        gasChanged();

        final double exhaustVelocity = Math.sqrt(specificHeat * temperature / GAS_CONSTANT) * pressureRatio;
        thrust = (massFlow * exhaustVelocity + (pressure - ambient) * DUCT_AREA)
                * ((Number) ClockworkConfig.server().gasThrusterForceMultiplier.get()).doubleValue();
    }

    /** 按各组分等比抽走 {@code kg} 质量（与 {@link GasMix#extract} 的语义一致，但不产生中间对象）。 */
    private static void removeGasProportionally(final GasMix mix, final double kg) {
        final double total = mix.totalMass();
        if (total <= 0) {
            return;
        }
        final double fraction = Math.min(1.0, kg / total);
        for (final GasType type : GasTypes.all()) {
            mix.removeMass(type, mix.massOf(type) * fraction);
        }
    }

    @Override
    public void sable$physicsTick(final ServerSubLevel subLevel, final RigidBodyHandle handle, final double timeStep) {
        if (thrust <= 0) {
            return;
        }
        final Direction facing = getBlockState().getValue(GasThrusterBlock.FACING);
        final Vector3d force = new Vector3d(facing.getStepX(), facing.getStepY(), facing.getStepZ()).mul(thrust);
        final Vector3d position = new Vector3d(getBlockPos().getX() + 0.5,
                getBlockPos().getY() + 0.5, getBlockPos().getZ() + 0.5);
        CWForces.pointForce(subLevel, CWForces.propulsion(), position, force, timeStep);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ")
                .append(Component.literal("Thrust: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.format("%.0f N", thrust)).withStyle(ChatFormatting.AQUA)));
        tooltip.add(Component.literal("    ")
                .append(Component.literal("Mass Flow: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.format("%.4f kg/tick", massFlow)).withStyle(ChatFormatting.AQUA)));
        return GasNodeTooltip.add(this, tooltip, isPlayerSneaking);
    }
}
