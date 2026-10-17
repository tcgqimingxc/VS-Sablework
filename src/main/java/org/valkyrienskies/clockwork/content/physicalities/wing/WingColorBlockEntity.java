package org.valkyrienskies.clockwork.content.physicalities.wing;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 机翼 / 襟翼共用的"染料颜色"方块实体。
 *
 * <p>对应旧版 {@code content.generic.ColorBlockEntity}（旧版放在 generic 包里，被机翼、旗帜等多处复用）。
 * 本移植只服务机翼 / 襟翼，所以收进 wing 包，避免与 Lead 维护的公共内容抢文件。
 *
 * <p>与旧版的差异：
 * <ul>
 *   <li>旧版靠自定义网络通道 {@code ClockworkPackets.sendToNear} 广播颜色；
 *       这里改用 NeoForge 的 {@code PacketDistributor} 把 {@link WingColorSyncPacket} 发给跟踪该区块的玩家。</li>
 *   <li>颜色以 24 位 RGB 存储（-1 表示未染色），渲染时再补上不透明度。</li>
 * </ul>
 */
public class WingColorBlockEntity extends SmartBlockEntity {

    /** 未染色。 */
    public static final int NO_COLOR = -1;
    /** 物品 NBT / 存档里的颜色键名，沿用旧版（含 {@code $}）。 */
    public static final String COLOR_KEY = "Clockwork$color";

    private int color = NO_COLOR;

    public WingColorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public int getColor() {
        return color;
    }

    public void setColor(int rgb) {
        if (color == rgb) {
            return;
        }
        color = rgb;
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            PacketDistributor.sendToPlayersTrackingChunk(serverLevel, serverLevel.getChunkAt(worldPosition).getPos(),
                    new WingColorSyncPacket(worldPosition, rgb));
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        if (color != NO_COLOR) {
            tag.putInt(COLOR_KEY, color);
        }
        super.write(tag, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        color = tag.contains(COLOR_KEY) ? tag.getInt(COLOR_KEY) : NO_COLOR;
        super.read(tag, registries, clientPacket);
    }
}
