package org.valkyrienskies.clockwork.content.logistics.gas.hoseport;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.clockwork.config.ClockworkConfig;
import org.valkyrienskies.clockwork.content.kinetics.CWKineticsIndex;
import org.valkyrienskies.clockwork.content.kinetics.CWKineticsSounds;
import org.valkyrienskies.clockwork.content.kinetics.universal_joint.UniversalJoint;
import org.valkyrienskies.clockwork.content.logistics.gas.GasMix;
import org.valkyrienskies.clockwork.content.logistics.gas.GasNodeBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.GasNodeTooltip;
import org.valkyrienskies.clockwork.content.logistics.gas.GasPhysics;

import java.util.List;

/**
 * 软管接口方块实体：配对成功后，每刻在两端之间按压差搬运气体（含热量）。
 *
 * <p><b>重新设计说明（重要）</b>：旧版 {@code HosePortBlockEntity}（Kotlin）做了两件事，两件都依赖 VS2：
 * <ol>
 *   <li>气体侧：往 Kelvin 求解器里加一条 {@code PipeDuctEdge}，把两个节点远程连成一条管道；</li>
 *   <li>物理侧：加一条 {@code VSDistanceJoint}（最大距离 = 装配时两口的距离 + 1），
 *       让两根"软管"在物理上互相拉住，还带一整套关节 ID 的持久化/恢复逻辑。</li>
 * </ol>
 * 1.21.1 没有 Kelvin，Sable 的约束也必须挂在物理管线上、以两个子空间的 plot 局部坐标为锚点，
 * 跨子空间恢复关节的成本远超本次移植范围。因此本实现只保留<b>玩法上的等价效果</b>：
 * 两口配对后各自的 {@link GasMix} 之间每刻按压差搬运气体（也就是"它们的管道网络连通了"），
 * 但不产生物理拉力。配对机制复用本移植已有的 {@link UniversalJoint}（软管卷轴右键配对），
 * 配对信息写在两端方块实体的 NBT 里并随 {@code sendData()} 同步到客户端。
 *
 * <p>搬运规则（与 {@code GasNetwork} 的单条连接同尺度）：
 * <pre>流量 = |压差| · gasFlowPerPascal · 软管通径</pre>
 * 上限为两端额定容量较小者的 {@link #MAX_FLOW_RATIO}，并且只能搬走源端已有的质量。
 * 只有"主端"（连接发起方）执行搬运，避免两端各搬一次。
 */
public class HosePortBlockEntity extends GasNodeBlockEntity implements UniversalJoint {

    /** 软管通径 0..1：旧版 {@code PipeDuctEdge(radius = 0.3125, length = 0.375)} 的等效开度。 */
    private static final double PORT_APERTURE = 0.5;

    /** 单次搬运上限（源节点质量比例），与 {@code GasNetwork} 的单条连接一致。 */
    private static final double MAX_FLOW_RATIO = 0.25;

    /** 小于该流量视为 0 [kg/tick]。 */
    private static final double MIN_FLOW = 1.0E-9;

    /** 低于该压差不再搬运 [Pa]，避免两端在平衡点附近来回抖。 */
    private static final double MIN_PRESSURE_DELTA = 1.0;

    @Nullable
    private BlockPos connectedPos;
    @Nullable
    private HosePortBlockEntity connectedBe;
    /** 主端：连接发起方，只有它每刻搬运气体。 */
    private boolean main;

    public HosePortBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, 1.0);
    }

    // ===================== UniversalJoint（配对机制） =====================

    public boolean isLinked() {
        return connectedPos != null;
    }

    @Override
    public @Nullable UniversalJoint getConnectedJoint() {
        return connectedBe;
    }

    @Override
    public BlockPos getJointPos() {
        return worldPosition;
    }

    /** 软管长度上限沿用「通用轴 / 伸缩杆」的配置（默认 32 格）。 */
    @Override
    public double getMaxCreationDistance() {
        return ClockworkConfig.server().maxUniversalJointDistance.get();
    }

    @Override
    public ItemStack getConnectionItem() {
        return CWKineticsIndex.EXTENDON_HOSE.asStack();
    }

    @Override
    public void connectTo(UniversalJoint other) {
        if (connectedPos != null || !(other instanceof HosePortBlockEntity otherPort)) {
            return;
        }
        connectedPos = otherPort.worldPosition;
        connectedBe = otherPort;
        main = true;
        otherPort.connectedPos = worldPosition;
        otherPort.connectedBe = this;
        otherPort.main = false;
        // 不在这里播音效：配对是由「软管卷轴」发起的，UniversalJointItem 已经播过 HOSE_ATTACH 了
        sendData();
        otherPort.sendData();
    }

    @Override
    public void disconnect() {
        final HosePortBlockEntity other = connectedBe;
        clearLink();
        if (other != null && worldPosition.equals(other.connectedPos)) {
            other.clearLink();
        }
    }

    /** 空手右键时调用：断开并把软管卷轴退还给玩家（创造模式不返还）。 */
    public void disconnectAndRefund(Player player) {
        final ItemStack refund = getConnectionItem();
        disconnect();
        if (!player.isCreative()) {
            player.getInventory().placeItemBackInInventory(refund);
        }
    }

    private void clearLink() {
        if (connectedPos == null) {
            return;
        }
        connectedPos = null;
        connectedBe = null;
        main = false;
        playSound(CWKineticsSounds.HOSE_RELEASE);
        setChanged();
        sendData();
    }

    private void playSound(SoundEvent event) {
        if (level != null) {
            level.playSound(null, worldPosition, event, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    // ===================== tick / 气体搬运 =====================

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || connectedPos == null) {
            return;
        }
        // 重新解析对端（区块重载后对象会换新）
        if (connectedBe == null || connectedBe.isRemoved() || connectedBe.getLevel() != level) {
            if (level.getBlockEntity(connectedPos) instanceof HosePortBlockEntity other
                    && worldPosition.equals(other.connectedPos)) {
                connectedBe = other;
            } else {
                clearLink();
                return;
            }
        }
        if (main) {
            transferGas(connectedBe);
        }
    }

    /** 按压差在两端之间搬运气体（气体带走热量，与导管网络里的流动一致）。 */
    private void transferGas(HosePortBlockEntity other) {
        final GasMix self = gasMix();
        final GasMix remote = other.gasMix();
        final double delta = self.pressure(gasCapacityKg()) - remote.pressure(other.gasCapacityKg());
        if (Math.abs(delta) < MIN_PRESSURE_DELTA) {
            return;
        }
        final GasMix source = delta > 0 ? self : remote;
        final GasMix sink = delta > 0 ? remote : self;
        double flow = Math.abs(delta) * GasPhysics.flowPerPascal() * PORT_APERTURE;
        flow = Math.min(flow, MAX_FLOW_RATIO * Math.min(gasCapacityKg(), other.gasCapacityKg()));
        flow = Math.min(flow, source.totalMass());
        if (flow <= MIN_FLOW) {
            return;
        }
        sink.absorb(source.extract(flow));
        gasChanged();
        other.gasChanged();
        // 流量记在接收侧（与 GasNetwork#transfer 的记账方式一致）
        (delta > 0 ? other.gasState() : gasState()).recordThroughput(flow);
    }

    @Override
    public void remove() {
        disconnect();
        super.remove();
    }

    // ===================== 持久化 / 护目镜 =====================

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        if (connectedPos != null) {
            tag.putInt("OtherPosX", connectedPos.getX());
            tag.putInt("OtherPosY", connectedPos.getY());
            tag.putInt("OtherPosZ", connectedPos.getZ());
            tag.putBoolean("Main", main);
        }
        super.write(tag, provider, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        if (tag.contains("OtherPosX")) {
            connectedPos = new BlockPos(tag.getInt("OtherPosX"), tag.getInt("OtherPosY"), tag.getInt("OtherPosZ"));
            main = tag.getBoolean("Main");
        } else {
            connectedPos = null;
            connectedBe = null;
            main = false;
        }
        super.read(tag, provider, clientPacket);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        GasNodeTooltip.addFunctionLine(getBlockState().getBlock(), tooltip);
        if (connectedPos == null) {
            tooltip.add(Component.translatable("vs_clockwork.hose_port.disconnected").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("vs_clockwork.hose_port.connected").withStyle(ChatFormatting.GREEN));
            tooltip.add(Component.translatable("vs_clockwork.hose_port.connected_to",
                    Component.literal(connectedPos.getX() + ", " + connectedPos.getY() + ", " + connectedPos.getZ())
                            .withStyle(ChatFormatting.AQUA)));
        }
        return GasNodeTooltip.add(this, tooltip, isPlayerSneaking);
    }
}
