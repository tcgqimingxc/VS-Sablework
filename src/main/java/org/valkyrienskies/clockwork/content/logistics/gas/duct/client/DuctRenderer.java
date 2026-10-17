package org.valkyrienskies.clockwork.content.logistics.gas.duct.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
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
import org.valkyrienskies.clockwork.content.logistics.gas.duct.DuctPartials;
import org.valkyrienskies.clockwork.content.logistics.gas.duct.GasDuctBlock;
import org.valkyrienskies.clockwork.content.logistics.gas.duct.GasDuctBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.duct.StraightDuctBlock;
import org.valkyrienskies.clockwork.content.logistics.gas.filter.FilterDuctBlock;
import org.valkyrienskies.clockwork.content.logistics.gas.pump.PumpDuctBlock;
import org.valkyrienskies.clockwork.content.logistics.gas.redstone.RedstoneDuctBlock;
import org.valkyrienskies.clockwork.content.logistics.gas.valve.ValveDuctBlock;

/**
 * 气体导管（{@code duct}）的方块实体渲染器：按每一面的连接状态画接口圈 / 泄漏面 / 收口环。
 *
 * <p>对应官方 / 旧版 {@code content/logistics/gas/duct/DuctRenderer.kt}
 * （{@code ClockworkBlockEntities.kt:462} 的 {@code .renderer { DuctRenderer(...) }}）。
 *
 * <p>为什么必须有它：{@code blockstates/duct.json} 是 multipart，但只引用 {@code duct/core} /
 * {@code duct/core_straight}（光管身）。接口圈（{@code duct/connection}）、泄漏面（{@code duct/leak}）、
 * 收口环（{@code duct/rim}）三个子模型<b>只</b>能被 BER 画出来 —— 缺了这个渲染器，
 * 导管会是一根没有接口的裸管，连"哪一面接通了"都看不出来。
 *
 * <p>与旧版的差异（都由本移植的方块实现决定，不是取舍）：
 * <ul>
 *   <li>旧版用 Kelvin 的 {@code ConnectionType} 与 {@code DuctEdgeType}（单向/过滤/智能四种边）；
 *       本移植的 {@link DuctConnectionType} 只有 {@code SIDE / FORCED_OFF / NONE / LEAK}
 *       （见该枚举注释：边类型的玩法入口尚未移植），因此这里只画 connection / leak / rim，
 *       {@code duct/custom/*} 四个边件暂无人使用（已由 {@code tools/check_render_coverage.py} 的 [RC6] 记录）。</li>
 *   <li>旧版判"邻居是不是导管"用 {@code IDuct}；本移植没有这个接口，改为枚举导管家族类
 *       （{@link #isDuctFamily(Block)}），语义相同：接在机器上时补一个收口环。</li>
 * </ul>
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

        BlockState state = be.getBlockState();
        Level level = be.getLevel();
        BlockPos pos = be.getBlockPos();
        VertexConsumer vb = buffer.getBuffer(RenderType.cutout());

        for (Direction dir : Direction.values()) {
            DuctConnectionType type = state.getValue(GasDuctBlock.DIR_TO_CONNECTION.get(dir));
            // 接口件是"朝外"的：接在 dir 这一面，就要朝向 dir 的反面
            Direction face = dir.getOpposite();

            if (type == DuctConnectionType.LEAK) {
                render(DuctPartials.LEAK, state, face, ms, vb, light, overlay);
                continue;
            }
            if (type != DuctConnectionType.SIDE) {
                continue;
            }
            render(DuctPartials.CONNECTION, state, face, ms, vb, light, overlay);
            if (level != null && !isDuctFamily(level.getBlockState(pos.relative(dir)).getBlock())) {
                render(DuctPartials.RIM, state, face, ms, vb, light, overlay);
            }
        }
    }

    private static void render(dev.engine_room.flywheel.lib.model.baked.PartialModel partial, BlockState state,
                               Direction face, PoseStack ms, VertexConsumer vb, int light, int overlay) {
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
