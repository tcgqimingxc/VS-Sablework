package org.valkyrienskies.clockwork.content.logistics.gas.valve;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.clockwork.content.logistics.gas.GasKineticNodeBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.GasLink;

/**
 * 气体阀门方块实体。
 *
 * <p>对应旧版 {@code ValveDuctBlockEntity}：开度 {@code pointer} ∈ [0,1] 以
 * {@code clamp(|speed| / 16 / 40, 0, 1)} 的速度追向目标（转速 &gt; 0 时目标为 1），
 * 通径 = {@code pointer - 0.3125}（旧版 {@code ApertureDuctEdge} 的半径补偿）。
 *
 * <p>与旧版的差异：
 * <ul>
 *   <li>旧版用 {@code LerpedFloat} 做客户端插值并给渲染器读数；本切片没有阀门渲染器，
 *       因此改成一个普通的 double（服务端权威、随节点数据同步），插值留给以后的渲染层。</li>
 *   <li>旧版支持 ComputerCraft 设置 {@code computerTarget}；本切片未移植 CC 集成。</li>
 * </ul>
 */
public class ValveDuctBlockEntity extends GasKineticNodeBlockEntity {

    /** 旧版 {@code ApertureDuctEdge.radius}：开度减去它才是有效通径。 */
    private static final double APERTURE_RADIUS = 0.3125;

    private double openness;

    public ValveDuctBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, 1.0);
    }

    /** 开度 0..1（0 = 全关）。 */
    public double openness() {
        return openness;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }
        double target = getSpeed() > 0 ? 1.0 : 0.0;
        double chaseSpeed = Mth.clamp(Math.abs(getSpeed()) / 16.0 / 40.0, 0.0, 1.0);
        if (openness == target || chaseSpeed <= 0) {
            return;
        }
        double step = chaseSpeed;
        openness = openness < target ? Math.min(target, openness + step) : Math.max(target, openness - step);
        sendData();
        gasChanged();
    }

    @Override
    public boolean gasConnected(Direction dir) {
        return getBlockState().getBlock() instanceof ValveDuctBlock valve
                && valve.gasConnectable(getBlockState(), dir);
    }

    @Override
    public GasLink gasLink(Direction dir) {
        if (!(getBlockState().getBlock() instanceof ValveDuctBlock valve)
                || !valve.gasConnectable(getBlockState(), dir)) {
            return GasLink.CLOSED;
        }
        return new GasLink(openness - APERTURE_RADIUS, 0, false);
    }

    @Override
    public boolean isGasPipe() {
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        tag.putDouble("Openness", openness);
        super.write(tag, provider, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        openness = tag.getDouble("Openness");
    }
}
