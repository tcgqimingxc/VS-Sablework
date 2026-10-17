package org.valkyrienskies.clockwork.content.physicalities.extendon;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.clockwork.config.ClockworkConfig;
import org.valkyrienskies.clockwork.content.kinetics.CWKineticsIndex;
import org.valkyrienskies.clockwork.content.kinetics.CWKineticsSounds;
import org.valkyrienskies.clockwork.content.kinetics.universal_joint.UniversalJoint;

/**
 * 可伸缩杆的方块实体：维护与另一根伸缩杆之间的"软管"连接。
 *
 * <p>对应旧版 {@code content.physicalities.extendon.ExtendonBlockEntity}。
 *
 * <p><b>本移植已完成</b>：两端连接的建立 / 断开 / 持久化 / 距离校验 / 音效（复用
 * {@link UniversalJoint} 那套机制，和通用轴完全一致）。
 *
 * <p><b>尚未完成（明确留白，不是空壳）</b>：旧版在连接之上还做了两件依赖外部系统的事：
 * <ol>
 *   <li>把两端的 Kelvin 气体节点连成一条管道边，并按理想气体定律
 *       （{@code height = 4V/π}）算出杆长 —— 依赖气体物流切片的 KVantum 网络；</li>
 *   <li>用一对 VS2 关节（距离关节 + 球关节）把两根杆所在的船约束到"当前杆长"——
 *       1.21.1 里应改用 Sable 的约束（参考 {@code SlickerBlockEntity} 里的写法）。</li>
 * </ol>
 * 这两件事分别属于气体物流切片与物理切片，本切片不越界实现，避免写出无法验证的代码。
 * 目前连接本身是有效的（会持久化、有距离上限、能拆能返），只是不会产生物理拉力。
 */
public class ExtendonBlockEntity extends SmartBlockEntity implements UniversalJoint {

    /** 距离上限读取与通用轴相同的配置项。 */
    @Nullable
    private BlockPos connectedPos;
    @Nullable
    private ExtendonBlockEntity connectedBe;
    /** 主端负责距离校验（与通用轴一致）。 */
    private boolean main;

    public ExtendonBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    // ------------------------------------------------------------------ UniversalJoint

    public boolean isLinked() {
        return connectedPos != null;
    }

    /**
     * 另一端的位置（渲染器用）。
     *
     * <p>客户端不会走 {@link #tick()} 里那段"重新解析 {@code connectedBe}"的逻辑
     * （那里 {@code level.isClientSide} 直接 return），所以客户端只有同步过来的
     * {@code connectedPos} 可用 —— 渲染器也只读这个字段。
     */
    @Nullable
    public BlockPos getConnectedPos() {
        return connectedPos;
    }

    /** 是否是连接的主端（只有主端负责画软管，旧版 {@code be.main}）。 */
    public boolean isMain() {
        return main;
    }

    @Override
    public @Nullable UniversalJoint getConnectedJoint() {
        return connectedBe;
    }

    @Override
    public BlockPos getJointPos() {
        return worldPosition;
    }

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
        if (connectedPos != null || !(other instanceof ExtendonBlockEntity otherBe)) {
            return;
        }
        connectedPos = otherBe.worldPosition;
        connectedBe = otherBe;
        main = true;
        otherBe.connectedPos = worldPosition;
        otherBe.connectedBe = this;
        otherBe.main = false;

        if (level != null) {
            level.playSound(null, worldPosition, CWKineticsSounds.HOSE_ATTACH, SoundSource.BLOCKS, 1f, 1f);
        }
        sendData();
        otherBe.sendData();
    }

    @Override
    public void disconnect() {
        ExtendonBlockEntity other = connectedBe;
        clearLink();
        if (other != null && worldPosition.equals(other.connectedPos)) {
            other.clearLink();
        }
    }

    private void clearLink() {
        if (connectedPos == null) {
            return;
        }
        connectedPos = null;
        connectedBe = null;
        main = false;
        if (level != null) {
            level.playSound(null, worldPosition, CWKineticsSounds.HOSE_RELEASE, SoundSource.BLOCKS, 1f, 1f);
        }
        setChanged();
        sendData();
    }

    // ------------------------------------------------------------------ tick / 生命周期

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || connectedPos == null) {
            return;
        }
        if (connectedBe == null || connectedBe.isRemoved()) {
            if (level.getBlockEntity(connectedPos) instanceof ExtendonBlockEntity other) {
                connectedBe = other;
                other.connectedBe = this;
            } else {
                clearLink();
                return;
            }
        }
        if (!main) {
            return;
        }
        double limit = getMaxCreationDistance();
        if (connectedPos.distSqr(worldPosition) > limit * limit) {
            disconnect();
            level.playSound(null, worldPosition, CWKineticsSounds.JOINT_BREAK, SoundSource.BLOCKS, 1f, 1f);
        }
    }

    @Override
    public void remove() {
        disconnect();
        super.remove();
    }

    // ------------------------------------------------------------------ 持久化

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        if (connectedPos != null) {
            tag.putInt("OtherPosX", connectedPos.getX());
            tag.putInt("OtherPosY", connectedPos.getY());
            tag.putInt("OtherPosZ", connectedPos.getZ());
            tag.putBoolean("Main", main);
        }
        super.write(tag, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        if (tag.contains("OtherPosX")) {
            connectedPos = new BlockPos(tag.getInt("OtherPosX"), tag.getInt("OtherPosY"), tag.getInt("OtherPosZ"));
            main = tag.getBoolean("Main");
        } else {
            connectedPos = null;
            connectedBe = null;
            main = false;
        }
        super.read(tag, registries, clientPacket);
    }
}
