package org.valkyrienskies.clockwork.content.kinetics.universal_joint;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.valkyrienskies.clockwork.ClockworkMod;
import org.valkyrienskies.clockwork.content.kinetics.CWKineticsSounds;

/**
 * 通用轴 / 伸缩杆的"连接器"物品：右键第一个接头记住它，再右键第二个接头把两者连起来。
 *
 * <p>对应旧版 {@code org.valkyrienskies.clockwork.util.universal_joint.UniversalJointItem}。
 *
 * <p>与旧版的差异：旧版把"第一个接头"存在物品实例的一个字段里（服务端物品是单例，
 * 所以两个玩家同时操作会互相覆盖）。这里改成按玩家 UUID 记录，行为更符合直觉，
 * 也不再需要旧版那个 {@code UniversalJointItemPacket}（选择状态只活在服务端）。
 */
public class UniversalJointItem extends Item {

    /** 每个玩家当前选中的第一个接头；服务端权威，客户端不参与。 */
    private final Map<UUID, UniversalJoint> selections = new HashMap<>();

    public UniversalJointItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            // 选择/连接全部在服务端判定，客户端只负责挥手动画
            return InteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(context.getClickedPos());
        if (!isJoint(be)) {
            selections.remove(player.getUUID());
            return InteractionResult.PASS;
        }

        UniversalJoint target = (UniversalJoint) be;
        UniversalJoint first = selections.get(player.getUUID());

        if (first == null) {
            selections.put(player.getUUID(), target);
            message(player, "connection_start");
            return InteractionResult.SUCCESS;
        }

        BlockPos firstPos = first.getJointPos();
        double distance = Math.sqrt(firstPos.distSqr(target.getJointPos()));
        if (distance > target.getMaxCreationDistance()) {
            selections.remove(player.getUUID());
            message(player, "connection_failed.too_far");
            return InteractionResult.PASS;
        }
        if (first == target || firstPos.equals(target.getJointPos())) {
            selections.remove(player.getUUID());
            message(player, "connection_failed.to_itself");
            return InteractionResult.PASS;
        }
        if (!first.tryConnect(level, context.getClickedPos())) {
            selections.remove(player.getUUID());
            message(player, "connection_failed");
            return InteractionResult.PASS;
        }

        message(player, "connection_end");
        level.playSound(null, context.getClickedPos(), CWKineticsSounds.HOSE_ATTACH, SoundSource.BLOCKS, 1f, 1f);
        // 连接器是一次性的：连好之后消耗一个
        context.getItemInHand().shrink(1);
        selections.remove(player.getUUID());
        return InteractionResult.SUCCESS;
    }

    /** 目标方块实体是否可以被本连接器连接；子类可放宽（例如伸缩杆软管也能接软管接口）。 */
    protected boolean isJoint(BlockEntity be) {
        return be instanceof UniversalJoint;
    }

    private static void message(Player player, String suffix) {
        player.displayClientMessage(
                Component.translatable(ClockworkMod.MOD_ID + ".universal_shaft." + suffix), true);
    }
}
