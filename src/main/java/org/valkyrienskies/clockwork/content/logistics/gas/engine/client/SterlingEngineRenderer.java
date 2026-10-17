package org.valkyrienskies.clockwork.content.logistics.gas.engine.client;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.clockwork.content.logistics.gas.engine.SterlingEngineBlockEntity;

/**
 * 斯特林机（{@code sterling_engine}）的方块实体渲染器：把 Create 的"机械泵齿轮"当飞轮转起来。
 *
 * <p>对应官方 / 旧版 {@code content/logistics/gas/engine/SterlingEngineRenderer.kt}
 * （{@code ClockworkBlockEntities.kt:665} 的 {@code .renderer { SterlingEngineRenderer(...) }}）：
 * 旧版类体只有一个 {@code getRotatedModel} 覆写，返回
 * {@code CachedBuffers.partialFacing(AllPartialModels.MECHANICAL_PUMP_COG, state)}，这里逐字照抄。
 *
 * <p>为什么必须有它：{@code blockstates/sterling_engine.json} 只有 {@code block/sterling_engine/block}
 * （机身），齿轮是 Create 的 {@code MECHANICAL_PUMP_COG} 局部模型 —— 没有 BER 就<b>没有齿轮</b>，
 * 玩家看到的是一个不会动的空壳（与实机反馈"气泵无齿轮"同类）。
 *
 * <p>唯一与旧版不同的地方：不调 {@code super.renderSafe} 也不写 {@code renderSafe}，
 * 而是让它走父类的默认实现。注意 Create 6 的父类实现第一句是
 * {@code if (VisualizationManager.supportsVisualization(level)) return;}（javap -c 核实），
 * 因为 Create 自己的动力方块都靠 Flywheel visual 画。本工程不注册 visual，
 * 所以这里<b>必须</b>自己把 {@code renderSafe} 写出来（见下），否则齿轮永远不画。
 */
public class SterlingEngineRenderer extends KineticBlockEntityRenderer<SterlingEngineBlockEntity> {

    public SterlingEngineRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    /**
     * 与旧版渲染器等价的一帧：只画那枚转动的齿轮。
     *
     * <p>{@code getRotatedModel} 仍是旧版覆写的那一行（便于与旧源码对照），
     * 这里显式调用 {@code renderRotatingBuffer} 补上父类在 "无 Flywheel visual" 分支里做的事。
     */
    @Override
    protected void renderSafe(SterlingEngineBlockEntity be, float partialTicks, com.mojang.blaze3d.vertex.PoseStack ms,
                              net.minecraft.client.renderer.MultiBufferSource buffer, int light, int overlay) {
        BlockState state = be.getBlockState();
        renderRotatingBuffer(be, getRotatedModel(be, state), ms,
                buffer.getBuffer(getRenderType(be, state)), light);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(SterlingEngineBlockEntity be, BlockState state) {
        return CachedBuffers.partialFacing(AllPartialModels.MECHANICAL_PUMP_COG, state);
    }
}
