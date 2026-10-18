package org.valkyrienskies.clockwork.content.logistics.gas;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 带动力（Create 旋转）的气体节点方块实体基类。
 *
 * <p>对应旧版 {@code KNodeKineticBlockEntity}（KineticBlockEntity + 节点能力）。
 *
 * <p>为什么选 {@link GeneratingKineticBlockEntity} 而不是 {@code KineticBlockEntity}：
 * Create 6.0.10 里"自产转速"所需的 {@code updateGeneratedRotation()} / {@code applyNewSpeed()} /
 * {@code createNetworkId()} 都在 {@code GeneratingKineticBlockEntity} 上（已用 javap 核实）。
 * 纯耗能机器（泵 / 阀 / 压缩机）用同一个基类也完全正常：它们的 {@code getGeneratedSpeed()} 默认返回 0，
 * 不会自转。
 *
 * <p>与 {@link GasNodeBlockEntity} 一样，本基类的子类（斯特林 / 燃气机 / 泵 / 阀 / 压缩机 / 进气口）
 * 经 {@link GasNode} 同时是热节点，热管可以直接与它们换热。
 */
public abstract class GasKineticNodeBlockEntity extends GeneratingKineticBlockEntity
        implements GasNode, com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {

    private final GasNodeState state;

    protected GasKineticNodeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState,
                                       double capacityScale) {
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
        // Create 的 KineticBlockEntity 会在 super 里追加转速/应力信息，保持它
        return GasNodeTooltip.add(this, tooltip, isPlayerSneaking)
                & super.addToGoggleTooltip(tooltip, isPlayerSneaking);
    }
}
