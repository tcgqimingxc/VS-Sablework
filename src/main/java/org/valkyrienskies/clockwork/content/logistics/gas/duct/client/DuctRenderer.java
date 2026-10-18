package org.valkyrienskies.clockwork.content.logistics.gas.duct.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.clockwork.content.logistics.gas.duct.DuctConnectionType;
import org.valkyrienskies.clockwork.content.logistics.gas.duct.DuctEdgeType;
import org.valkyrienskies.clockwork.content.logistics.gas.duct.DuctPartials;
import org.valkyrienskies.clockwork.content.logistics.gas.duct.GasDuctBlock;
import org.valkyrienskies.clockwork.content.logistics.gas.duct.GasDuctBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.duct.StraightDuctBlock;
import org.valkyrienskies.clockwork.content.logistics.gas.filter.FilterDuctBlock;
import org.valkyrienskies.clockwork.content.logistics.gas.pump.PumpDuctBlock;
import org.valkyrienskies.clockwork.content.logistics.gas.redstone.RedstoneDuctBlock;
import org.valkyrienskies.clockwork.content.logistics.gas.valve.ValveDuctBlock;

/**
 * 气体导管（{@code duct}）的方块实体渲染器：按每一面的连接状态画接口圈 / 泄漏面 / 边类型件 / 收口环。
 *
 * <p>对应旧版 {@code content/logistics/gas/duct/DuctRenderer.kt}
 * （{@code ClockworkBlockEntities.kt:462} 的 {@code .renderer { DuctRenderer(...) }}）。
 *
 * <p>为什么必须有它：{@code blockstates/duct.json} 是 multipart，但只引用 {@code duct/core} /
 * {@code duct/core_straight}（光管身）。接口圈（{@code duct/connection}）、泄漏面（{@code duct/leak}）、
 * 收口环（{@code duct/rim}）与四种边类型件（{@code duct/custom/*}）<b>只</b>能被 BER 画出来 ——
 * 缺了这个渲染器，导管会是一根没有接口的裸管，连"哪一面接通了、哪一面是单向"都看不出来。
 *
 * <p><b>边类型件的位置</b>：四个 {@code duct/custom/*} 模型都是一块 3 px 厚的法兰盘，
 * 几何位于模型局部 {@code z ∈ [0,3]}（即作者的 north 侧）。旧版用
 * {@code CachedBuffers.partialFacing(partial, state, dir.opposite)} 把它贴到 {@code dir} 那一面，
 * 这里照抄同一套调用（{@link #render} 的 {@code face = dir.getOpposite()}），
 * 所以"模型画在接缝处"的位置与旧版逐像素一致。
 *
 * <p><b>两侧都配置时会重叠</b>：旧版把边类型镜像到接缝两侧，两侧各画一个同样的模型（重叠）。
 * 本移植的边类型是"单面配置"（见 {@link org.valkyrienskies.clockwork.content.logistics.gas.duct.DuctEdgeInteraction}），
 * 默认只画配置过的那一面；只有玩家把接缝两侧都配成非普通类型时才会出现两个模型叠在接缝上 ——
 * 与旧版的重叠效果相同，且网络语义上两侧规则会同时生效（取交集），文档里已写明。
 */
public class DuctRenderer extends SmartBlockEntityRenderer<GasDuctBlockEntity> {

    public DuctRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(GasDuctBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        // 父类负责 Create 的过滤/频率悬浮显示（导管没有这些行为，照调保持一致）
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);

        final BlockState state = be.getBlockState();
        final Level level = be.getLevel();
        final BlockPos pos = be.getBlockPos();
        final VertexConsumer vb = buffer.getBuffer(RenderType.cutout());

        for (Direction dir : Direction.values()) {
            final DuctConnectionType type = state.getValue(GasDuctBlock.DIR_TO_CONNECTION.get(dir));
            // 接口件是"朝外"的：接在 dir 这一面，就要朝向 dir 的反面（见类注释）
            final Direction face = dir.getOpposite();

            if (type == DuctConnectionType.LEAK) {
                render(DuctPartials.LEAK, state, face, ms, vb, light, overlay);
                continue;
            }
            if (type != DuctConnectionType.SIDE) {
                continue;
            }
            render(DuctPartials.CONNECTION, state, face, ms, vb, light, overlay);

            // 旧版逻辑（DuctRenderer.kt:57-73）：连通的面上，边类型件优先；普通边才考虑收口环。
            final PartialModel edge = edgeModel(be.edgeType(dir), dir);
            if (edge != null) {
                render(edge, state, face, ms, vb, light, overlay);
            } else if (level != null && !isDuctFamily(level.getBlockState(pos.relative(dir)).getBlock())) {
                render(DuctPartials.RIM, state, face, ms, vb, light, overlay);
            }
        }
    }

    /** 边类型 → 接缝上的模型；{@link DuctEdgeType#NORMAL} 返回 null（只画接口圈 / 收口环）。 */
    private static PartialModel edgeModel(DuctEdgeType type, Direction dir) {
        return switch (type) {
            case NORMAL -> null;
            // 旧版命名错位：FILTERED 用 smart 模型、SMART 用 copper 模型（DuctRenderer.kt:60-61）
            case FILTERED -> DuctPartials.EDGE_FILTERED;
            case SMART -> DuctPartials.EDGE_SMART;
            case ONEWAY_FORWARD -> onewayModel(dir, true);
            case ONEWAY_BACKWARD -> onewayModel(dir, false);
        };
    }

    /**
     * 单向件的两个模型只有 UV 镜像，差别就是<b>箭头朝向</b>：
     * {@code oneway_forward} 的箭头朝坐标轴负方向、{@code oneway_backward} 朝正方向。
     *
     * <p>这个"哪个模型朝哪边"的对应关系不是猜的，而是从旧版渲染规则反推出来的
     * （{@code DuctRenderer.kt:63-66}）：
     * <pre>
     * ONEWAY_FORWARD -> 方向为负轴时画 oneway_forward，否则 oneway_backward
     * ONEWAY_BACKWARD -> 方向为正轴时画 oneway_forward，否则 oneway_backward
     * </pre>
     * 只有"forward 模型 = 箭头朝负轴"这一种解释能同时满足上面两条，并且让箭头永远指向
     * 气体实际流过的方向（出口面朝外、入口面朝内）。
     *
     * @param outOfThisFace true = 这一面是出口（气体从本导管流出），false = 入口
     */
    private static PartialModel onewayModel(Direction dir, boolean outOfThisFace) {
        final boolean negativeAxis = dir.getAxisDirection().getStep() < 0;
        // 出口面：流向 = dir 的外法线；入口面：流向 = -dir。两者都归到"峰值朝负轴吗"一个布尔
        final boolean flowNegative = outOfThisFace == negativeAxis;
        return flowNegative ? DuctPartials.EDGE_ONEWAY_FORWARD : DuctPartials.EDGE_ONEWAY_BACKWARD;
    }

    private static void render(PartialModel partial, BlockState state, Direction face, PoseStack ms,
                               VertexConsumer vb, int light, int overlay) {
        CachedBuffers.partialFacing(partial, state, face).light(light).overlay(overlay).renderInto(ms, vb);
    }

    /** 旧版 {@code IDuct} 的等价物：导管家族（不含储罐 / 机器）。 */
    private static boolean isDuctFamily(Block block) {
        return block instanceof GasDuctBlock
                || block instanceof StraightDuctBlock
                || block instanceof FilterDuctBlock
                || block instanceof PumpDuctBlock
                || block instanceof RedstoneDuctBlock
                || block instanceof ValveDuctBlock;
    }
}
