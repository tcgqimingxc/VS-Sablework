package org.valkyrienskies.clockwork.content.kinetics.sequenced_seat;

import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.valkyrienskies.clockwork.content.kinetics.sequenced_seat.client.CommandSeatClient;

import java.util.Set;

/**
 * 命令座椅的"座位实体"（旧版 {@code SequencedSeatEntity.kt}）。
 *
 * <p>就是一个不可见的座位（复用 Create 的 {@link SeatEntity}），额外负责：
 * 客户端每刻把本地玩家的方向键 / 跳跃键变化发给服务端（见 {@link #clientTick}）；
 * 服务端发现座椅方块没了就自己消失并清空输入。
 *
 * <p>与旧版差异：旧版直接把 {@code Minecraft.getInstance()} 的按键读取写在实体里，
 * 这里挪到客户端专用类 {@code client/CommandSeatClient}，避免服务端加载客户端类。
 */
public class CommandSeatEntity extends SeatEntity {

    /** 上一次发给服务端的按键集合，用来去重（旧版 {@code prevKeys}）。 */
    private Set<InputKey> previousKeys = Set.of();

    public CommandSeatEntity(final EntityType<?> type, final Level level) {
        super(type, level);
    }

    /**
     * 注意：<b>不能</b>调用 {@code super.tick()} —— Create 的 {@code SeatEntity#tick()} 会在
     * "脚下的方块不是 {@code SeatBlock}" 时把自己删掉，而命令座椅是动力方块，
     * 所以必须照抄它的逻辑但换成 {@link CommandSeatBlock}（旧版同样是整个覆写、不调 super）。
     */
    @Override
    public void tick() {
        if (this.level().isClientSide) {
            this.clientTick();
            return;
        }
        final BlockPos pos = this.blockPosition();
        if (this.isVehicle() && this.level().getBlockState(pos).getBlock() instanceof CommandSeatBlock) {
            return;
        }
        // 没人在上面，或者座椅方块被拆了：清空输入并自己消失
        this.discard();
        if (this.level().getBlockEntity(pos) instanceof final CommandSeatBlockEntity seat) {
            seat.updateInput(Set.of());
        }
    }

    /** 客户端：按键集合变化时通知服务端。 */
    private void clientTick() {
        final Set<InputKey> keys = CommandSeatClient.collectKeys(this);
        if (keys == null || keys.equals(this.previousKeys)) {
            return;
        }
        this.previousKeys = keys;
        CommandSeatClient.sendInput(this, keys);
    }
}
