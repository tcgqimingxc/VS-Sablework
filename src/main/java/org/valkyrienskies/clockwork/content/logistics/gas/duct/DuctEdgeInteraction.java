package org.valkyrienskies.clockwork.content.logistics.gas.duct;

import com.simibubi.create.AllSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 导管"边类型"的交互入口（螺丝刀 + 空手；服务端权威）。
 *
 * <p>对应旧版 {@code DuctBlock.onScrewdrived / onSneakScrewdrived}
 * （{@code DuctBlock.kt:405-439}）与 {@code DuctBlockEntity.cycleEdgeType}
 * （{@code DuctBlockEntity.kt:91-101}）。
 *
 * <p><b>手势对照（本移植按任务要求重排，差异必读）</b>
 * <table border="1">
 *   <tr><th>手势</th><th>旧版</th><th>本移植</th></tr>
 *   <tr><td>螺丝刀右键</td><td>打开该边的配置界面（Smart / Filter / Redstone Screen）</td>
 *       <td><b>循环该面的边类型</b>（普通→单向出口→单向入口→过滤→智能→普通）</td></tr>
 *   <tr><td>潜行 + 螺丝刀右键</td><td>循环该面的边类型</td>
 *       <td><b>循环主参数</b>：过滤→气体种类；智能→阈值指标（NONE/PRESSURE/TEMPERATURE）</td></tr>
 *   <tr><td>空手右键</td><td>同"螺丝刀右键"（也是开界面）</td>
 *       <td>次参数：过滤→白/黑名单；智能→阈值档位</td></tr>
 *   <tr><td>潜行 + 空手右键</td><td>—</td><td>第三参数：智能→大于/小于</td></tr>
 * </table>
 *
 * <p><b>为什么这么改</b>：旧版的主操作是"打开 GUI"，而本移植没有 GUI（见
 * {@code docs/DUCT-EDGE-VARIANTS.md} §4.2）。没有界面时，最常用的操作（切类型）必须放在
 * 不需要潜行的右键上，否则玩家每切一次都要按住潜行 —— 这是"能玩"与"能查"的分界线。
 * 参数被拆成"主/次/第三"三个旋钮，每个旋钮只循环一个维度，配合护目镜读数可以完整配置
 * （旧版 GUI 一次能看到全部参数，本移植需要多看两行读数 —— 差异写在文档里）。
 *
 * <p><b>为什么全部逻辑集中在服务端</b>：边类型会影响气体模拟（单向/过滤/阈值），
 * 客户端无权决定。客户端只回 {@link InteractionResult#SUCCESS} 让手臂动画与音效正常，
 * 真正改数据的分支都在 {@code level.isClientSide} 之后。
 * 反馈（行动栏 + 音效）由服务端发出，这样多人游戏里旁边的玩家也能听见。
 */
public final class DuctEdgeInteraction {

    /** 四种手势；顺序不代表频率，只用于 switch 可读性。 */
    public enum Gesture {
        /** 螺丝刀右键：循环边类型。 */
        CYCLE_TYPE,
        /** 潜行 + 螺丝刀：循环该类型的主参数。 */
        PRIMARY,
        /** 空手右键：次参数。 */
        SECONDARY,
        /** 潜行 + 空手右键：第三参数。 */
        TERTIARY
    }

    private DuctEdgeInteraction() {
    }

    /**
     * 处理一次交互。
     *
     * @param clickedFace 玩家点击的面（射线命中的那个面）
     * @param hit         命中的精确位置，用来判断"想改的是方块哪一面"（与扳手同一套判定）
     * @return 与 {@code BlockInteraction} 约定一致的结果；不适用的手势返回 PASS，避免抢占交互
     */
    public static InteractionResult adjust(final Level level, final BlockPos pos, final Direction clickedFace,
                                           final Vec3 hit, @Nullable final Player player,
                                           final Gesture gesture) {
        final Direction face = GasDuctBlock.pickConnectionDirection(clickedFace, hit, pos);
        final BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof GasDuctBlock)
                || !(level.getBlockEntity(pos) instanceof GasDuctBlockEntity duct)) {
            return InteractionResult.PASS;
        }
        final DuctConnectionType connection = state.getValue(GasDuctBlock.DIR_TO_CONNECTION.get(face));

        if (connection == DuctConnectionType.LEAK) {
            // 旧版：螺丝刀点泄漏面 = 封好（DuctBlock.kt:405-417）。只有普通右键做这件事，
            // 免得调参数时把泄漏误封（潜行右键在旧版是"切类型"，这里留给参数）。
            if (gesture != Gesture.CYCLE_TYPE) {
                return InteractionResult.PASS;
            }
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }
            level.setBlockAndUpdate(pos,
                    state.setValue(GasDuctBlock.DIR_TO_CONNECTION.get(face), DuctConnectionType.NONE));
            AllSoundEvents.WRENCH_ROTATE.playOnServer(level, pos, 1.0F, 0.8F);
            if (player != null) {
                player.displayClientMessage(
                        Component.translatable("vs_clockwork.duct.edge.sealed"), true);
            }
            return InteractionResult.SUCCESS;
        }

        if (connection != DuctConnectionType.SIDE) {
            // 没有连接就没有"边"可配（旧版同样直接返回）。只对螺丝刀右键给反馈，
            // 空手点一个没连接的面不该弹提示（玩家可能只是想确认一下方块）。
            if (gesture != Gesture.CYCLE_TYPE) {
                return InteractionResult.PASS;
            }
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }
            if (player != null) {
                player.displayClientMessage(
                        Component.translatable("vs_clockwork.duct.edge.not_connected"), true);
            }
            return InteractionResult.SUCCESS;
        }

        if (!appliesTo(duct.edge(face), gesture)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        final Component message = switch (gesture) {
            case CYCLE_TYPE -> duct.cycleType(face);
            case PRIMARY -> duct.cyclePrimary(face);
            case SECONDARY -> duct.cycleSecondary(face);
            case TERTIARY -> duct.cycleTertiary(face);
        };
        if (message == null) {
            return InteractionResult.PASS;
        }
        // 切类型与调参数用不同音高：闭着眼也能分清刚才动的是哪一层。
        AllSoundEvents.WRENCH_ROTATE.playOnServer(level, pos, 1.0F,
                gesture == Gesture.CYCLE_TYPE ? 1.0F : 1.4F);
        if (player != null) {
            player.displayClientMessage(message, true);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * 该手势对这种边类型是否有意义。
     *
     * <p>客户端与服务端必须得到同一个答案，否则会出现"客户端回 SUCCESS、服务端回 PASS"的
     * 不一致（手臂挥了但什么都没发生）。所以这里只读已经同步到客户端的边配置，不读服务端独有状态。
     */
    private static boolean appliesTo(final DuctEdgeSettings edge, final Gesture gesture) {
        return switch (gesture) {
            case CYCLE_TYPE -> true;
            case PRIMARY -> edge.type() == DuctEdgeType.FILTERED || edge.type() == DuctEdgeType.SMART;
            case SECONDARY -> edge.type() == DuctEdgeType.FILTERED
                    || (edge.type() == DuctEdgeType.SMART
                    && edge.filterType() != DuctEdgeSettings.FilterType.NONE);
            case TERTIARY -> edge.type() == DuctEdgeType.SMART
                    && edge.filterType() != DuctEdgeSettings.FilterType.NONE;
        };
    }
}
