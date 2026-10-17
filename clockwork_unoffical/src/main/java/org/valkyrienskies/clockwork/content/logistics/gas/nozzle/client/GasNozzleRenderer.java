package org.valkyrienskies.clockwork.content.logistics.gas.nozzle.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.clockwork.content.logistics.gas.nozzle.GasNozzleBlock;
import org.valkyrienskies.clockwork.content.logistics.gas.nozzle.GasNozzleBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.nozzle.GasNozzlePartials;

/**
 * 气嘴（{@code gas_nozzle}）的方块实体渲染器：补上本体模型里没有的表盘与内部叶轮轴。
 *
 * <p>对应官方 / 旧版 {@code content/logistics/gas/pockets/nozzle/GasNozzleRenderer.kt}
 * （{@code ClockworkBlockEntities.kt:605} 的 {@code .renderer { GasNozzleRenderer(...) }}）。
 *
 * <p>为什么必须有它：{@code blockstates/gas_nozzle.json} 只引用 {@code gas_nozzle/block}
 * （喷嘴外壳），{@code dial}（表盘 + 指针）与 {@code axis}（叶轮轴）两个子模型
 * <b>只能</b>由 BER 画；缺了它们喷嘴正面是空的。
 *
 * <p><b>与旧版的关键差异：这里不转</b>。旧版是 {@code HorizontalKineticBlock}，有输入轴：
 * 表盘指针按 {@code pointer} 插值转（225°→135°）、叶轮轴按转速转。
 * 本移植的 {@link GasNozzleBlock} 是普通 {@code HorizontalDirectionalBlock}
 * （类注释："本实现没有输入轴，进气只在背面，气压决定喷气量"），客户端<b>没有</b>任何可读的转角/开度，
 * 因此这里把两个部件按 FACING 摆正后<b>静态</b>绘制：
 * 表盘指针固定在量程中点（180°，即旧版 {@code lerp(pointer,225,135)} 的中间值），
 * 叶轮轴用 {@code rotateToFace(FACING)} 与旧版同一姿态。等以后把"喷气量"接到表盘上，
 * 只需改 {@link #NEEDLE_DEGREES} 为动态值。
 */
public class GasNozzleRenderer extends SmartBlockEntityRenderer<GasNozzleBlockEntity> {

    /** 指针角度（弧度）：旧版量程是 225°（关）→135°（开），中点 180°。 */
    private static final float NEEDLE_DEGREES = 180f;
    /** 表盘相对方块中心的偏移（旧版 {@code dialOffset = Vec3(0,-0.1,0)}）。 */
    private static final float DIAL_Y_OFFSET = -0.1f;

    public GasNozzleRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(GasNozzleBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        // 父类只画 Create 的过滤/频率悬浮显示（喷嘴没有），照调保持一致
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);

        BlockState state = be.getBlockState();
        Direction facing = state.getValue(GasNozzleBlock.FACING);
        VertexConsumer vb = buffer.getBuffer(RenderType.solid());

        renderDial(state, facing, ms, vb, light);
        renderAxis(state, facing, ms, vb, light);
    }

    /** 表盘：旧版 {@code rotateBufferTowards(pointer, facing.clockwise)} + 中心偏移 + 指针角。 */
    private static void renderDial(BlockState state, Direction facing, PoseStack ms, VertexConsumer vb, int light) {
        // 旧版 rotateBufferTowards：(-target.toYRot() - 90) / 180 * PI，绕 UP 旋转
        float towards = (float) Math.toRadians((-facing.getClockWise().toYRot() - 90));

        SuperByteBuffer dial = CachedBuffers.partial(GasNozzlePartials.DIAL, state);
        dial.rotateCentered(towards, Direction.UP)
                .translate(0f, DIAL_Y_OFFSET, 0f)
                .rotateCentered((float) Math.toRadians(NEEDLE_DEGREES), Direction.NORTH)
                .translate(0f, -DIAL_Y_OFFSET, 0f)
                .light(light);
        dial.renderInto(ms, vb);
    }

    /** 叶轮轴：与旧版同一姿态（绕方块中心转到 FACING）。 */
    private static void renderAxis(BlockState state, Direction facing, PoseStack ms, VertexConsumer vb, int light) {
        SuperByteBuffer axis = CachedBuffers.partial(GasNozzlePartials.AXIS, state);
        axis.translate(0.5f, 0.5f, 0.5f)
                .rotateToFace(facing)
                .translate(-0.5f, -0.5f, -0.5f)
                .light(light);
        axis.renderInto(ms, vb);
    }
}
