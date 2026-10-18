package org.valkyrienskies.clockwork.content.logistics.gas;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 非动力气体节点方块实体的基类。
 *
 * <p>对应旧版 {@code KNodeBlockEntity}（SmartBlockEntity + 气体节点能力）。差异：
 * <ul>
 *   <li>旧版把状态寄存在 Kelvin 求解器、靠 {@code dataToLoad} 处理"先有方块实体后有求解器节点"
 *       的时序问题；本移植状态就在自己字段里，没有这个时序问题。</li>
 *   <li>旧版每 lazyTick 往 30 格范围内广播 {@code KNodeSyncPacket}；本移植用 Create 现成的
 *       {@code sendData()/readClient}（只在数值变化超过阈值时发）。</li>
 * </ul>
 *
 * <p>作为 {@link GasNode} 的实现，本基类的所有子类（加热器 / 煤炉 / 燃气机 / 导管 / 储罐 / …）
 * 同时是热节点：热管可以直接从它们身上取热或放热，见 {@link GasNode} 的 HeatNode 默认方法。
 * 这样"热管画了臂"与"这条臂能传热"是同一件事。
 *
 * @param capacityScale 额定容量倍率（1.0 = 单根导管），见 {@link GasPhysics#nodeCapacityKg()}
 */
public abstract class GasNodeBlockEntity extends SmartBlockEntity implements GasNode, IHaveGoggleInformation {

    private final GasNodeState state;

    protected GasNodeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState, double capacityScale) {
        super(type, pos, blockState);
        this.state = new GasNodeState(this, capacityScale);
        // 旧版 kelvinNodeBlockEntityLazyTickRate = 10：每 10 刻同步一次气体状态给客户端
        setLazyTickRate(10);
    }

    @Override
    public GasNodeState gasState() {
        return state;
    }

    @Override
    public void gasChanged() {
        state.markDirty();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void tick() {
        super.tick();
        if (level != null && !level.isClientSide) {
            state.serverTick();
        }
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        state.syncIfChanged();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        state.write(tag, provider, clientPacket);
        super.write(tag, provider, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        state.read(tag, provider, clientPacket);
    }

    @Override
    public void onChunkUnloaded() {
        state.unregister();
        super.onChunkUnloaded();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        return GasNodeTooltip.add(this, tooltip, isPlayerSneaking);
    }
}
