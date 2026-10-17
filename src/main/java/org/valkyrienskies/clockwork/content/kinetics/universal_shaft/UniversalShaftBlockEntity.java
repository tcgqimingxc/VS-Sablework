package org.valkyrienskies.clockwork.content.kinetics.universal_shaft;

import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
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
 * 通用轴的方块实体：在 Create 的动力学网络里额外挂一条"远程连接"。
 *
 * <p>对应旧版 {@code content.kinetics.universal_shaft.UniversalShaftBlockEntity}。
 *
 * <p>实现要点（Create 6.0.10 的机制与 1.20.1 相同，已核对源码）：
 * <ul>
 *   <li>{@link #addPropagationLocations} 把远程端点塞进"候选邻居"列表，
 *       让 {@code RotationPropagator} 会去检查那个位置；</li>
 *   <li>{@link #isCustomConnection} 返回 true，使传播器认为两端"已连接"
 *       （否则轴向不一致会被判定为不相连）；</li>
 *   <li>{@link #propagateRotationTo} 只对真正的远程端点返回 1.0（等速、同向传递），
 *       其余情况返回 0 走 Create 的默认规则。</li>
 * </ul>
 *
 * <p>与旧版的差异：距离判断用同一 Level 的方块坐标（旧版用 VS2 的世界坐标），
 * 因此跨 Sable 子空间的连接长度不做换算；连接状态仍在 NBT 里持久化并在两端互相同步。
 */
public class UniversalShaftBlockEntity extends KineticBlockEntity implements UniversalJoint {

    /** 远程端点坐标；未连接为 null。服务端与客户端都保存（客户端渲染软管要用）。 */
    @Nullable
    private BlockPos connectedPos;
    /** 远程端点的方块实体缓存（仅服务端使用）。 */
    @Nullable
    private UniversalShaftBlockEntity connectedBe;
    /** 是否为连接的"主端"：主端负责渲染软管与距离校验（与旧版一致）。 */
    private boolean main;

    public UniversalShaftBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ------------------------------------------------------------------ 连接状态

    public boolean isLinked() {
        return connectedPos != null;
    }

    public boolean isMain() {
        return main;
    }

    @Nullable
    public BlockPos getConnectedPos() {
        return connectedPos;
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
        return CWKineticsIndex.UNIVERSAL_SHAFT_ITEM.asStack();
    }

    @Override
    public void connectTo(UniversalJoint other) {
        if (connectedPos != null) {
            return;
        }
        if (!(other instanceof UniversalShaftBlockEntity otherBe)) {
            return;
        }
        connectedPos = otherBe.worldPosition;
        connectedBe = otherBe;
        main = true;

        otherBe.connectedPos = worldPosition;
        otherBe.connectedBe = this;
        otherBe.main = false;

        sendData();
        attachKinetics();
        otherBe.sendData();
        otherBe.attachKinetics();
    }

    @Override
    public void disconnect() {
        UniversalShaftBlockEntity other = connectedBe;
        clearLink();
        if (other != null && other.connectedPos != null && other.connectedPos.equals(worldPosition)) {
            other.clearLink();
        }
    }

    private void clearLink() {
        connectedPos = null;
        connectedBe = null;
        main = false;
        setChanged();
        sendData();
        detachKinetics();
        clearKineticInformation();
    }

    // ------------------------------------------------------------------ 动力学传播

    @Override
    public float propagateRotationTo(KineticBlockEntity target, BlockState stateFrom, BlockState stateTo,
                                     BlockPos diff, boolean connectedViaAxes, boolean connectedViaCogs) {
        if (connectedPos == null || !target.getBlockPos().equals(connectedPos)) {
            return 0f;
        }
        return 1f;
    }

    @Override
    public List<BlockPos> addPropagationLocations(IRotate block, BlockState state, List<BlockPos> neighbours) {
        if (connectedPos != null) {
            neighbours.add(connectedPos);
        }
        return neighbours;
    }

    @Override
    public boolean isCustomConnection(KineticBlockEntity other, BlockState state, BlockState otherState) {
        return true;
    }

    // ------------------------------------------------------------------ tick / 生命周期

    @Override
    public void tick() {
        super.tick();

        if (level == null) {
            return;
        }
        // 客户端只持有坐标，用于渲染软管；服务端需要拿到对方实体才能建/断网络
        if (level.isClientSide) {
            return;
        }

        if (connectedPos == null) {
            connectedBe = null;
            main = false;
            return;
        }

        if (connectedBe == null || connectedBe.isRemoved()) {
            if (level.getBlockEntity(connectedPos) instanceof UniversalShaftBlockEntity other) {
                connectedBe = other;
                other.connectedBe = this;
            } else {
                // 对方已经不存在了，直接清掉本端的连接
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
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        if (connectedPos != null) {
            compound.putInt("OtherPosX", connectedPos.getX());
            compound.putInt("OtherPosY", connectedPos.getY());
            compound.putInt("OtherPosZ", connectedPos.getZ());
            compound.putBoolean("Main", main);
        }
        super.write(compound, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        if (compound.contains("OtherPosX")) {
            connectedPos = new BlockPos(compound.getInt("OtherPosX"), compound.getInt("OtherPosY"),
                    compound.getInt("OtherPosZ"));
            main = compound.getBoolean("Main");
        } else {
            connectedPos = null;
            connectedBe = null;
            main = false;
            // 旧版在两端都会清转速；这里只在服务端清，客户端由随后的数据同步修正，避免读档瞬间转速闪一下
            if (!clientPacket) {
                setSpeed(0f);
            }
        }
        super.read(compound, registries, clientPacket);
    }
}
