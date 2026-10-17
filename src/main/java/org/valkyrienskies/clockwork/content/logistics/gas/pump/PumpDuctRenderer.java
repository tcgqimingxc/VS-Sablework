package org.valkyrienskies.clockwork.content.logistics.gas.pump;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * 气体泵（{@code pump_duct}）的方块实体渲染器：补上随输入轴自转的<b>齿轮</b>。
 *
 * <p>对应官方（1.20.1）{@code content/logistics/gas/pump/PumpDuctRenderer.kt}（17 行），
 * 它只覆盖 {@code getRotatedModel}，让 {@code KineticBlockEntityRenderer} 把齿轮按转速转起来。
 *
 * <h2>根因（实机"气泵不显示齿轮"）</h2>
 * <p>泵的静态方块模型 {@code models/block/pump/block.json} 只有 5 个 element（管体 + 上下法兰），
 * 齿轮是独立模型、不走 blockstate；工程里既没有 {@code .renderer(...)} 也没有 BER，
 * 所以世界里永远只有一个管壳。
 *
 * <h2>用哪份齿轮模型（有官方证据，不是随手挑的）</h2>
 * <p>用 <b>Create 的 {@code MECHANICAL_PUMP_COG}</b>，与官方渲染器逐字一致。证据：
 * <ul>
 *   <li>{@code models/block/pump/item.json} 共 13 个 element = 管体 5 + 齿轮 8，
 *       且那 8 个齿轮用的贴图键是 {@code create:block/pump}；</li>
 *   <li>Create 的 {@code assets/create/models/block/mechanical_pump/cog.json} 正好也是 8 个 element，
 *       逐个比过坐标与旋转（{@code 6.5,-1,3 → 9.5,17,13}、45°/±22.5° 等）与上面 8 个完全相同；</li>
 *   <li>工程里另外还有一份 {@code models/block/pump/cog.json}（5 个 element、贴图 {@code block/gas_pump}），
 *       但官方资源树里<b>没有任何模型或 blockstate 引用它</b>，官方 item 模型用的也是 Create 那套，
 *       所以不拿它来臆造外观。</li>
 * </ul>
 *
 * <h2>为什么不照抄"只覆盖 getRotatedModel、让父类画"</h2>
 * <p>Create 6 的 {@code KineticBlockEntityRenderer#renderSafe} 第一句是
 * {@code if (VisualizationManager.supportsVisualization(level)) return;}（javap -c 核实）。
 * 本方块没有注册 Flywheel visual，可视化一旦开启（默认开启）父类就<b>什么都不画</b>；
 * 关掉可视化时父类才画。依赖父类会导致"齿轮时有时无"。
 * 所以这里自己画一次，两种模式下行为一致 —— 与 {@code PropellerBearingRenderer} /
 * {@code CopterBearingRenderer} 处理同一问题的做法相同。
 * 覆盖 {@code getRotatedModel} 仍然保留：它是"齿轮模型 + 朝向"的唯一来源，也便于与官方逐行对照。
 */
public class PumpDuctRenderer extends KineticBlockEntityRenderer<PumpDuctBlockEntity> {

    public PumpDuctRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    /** 齿轮模型 + 摆到输入轴方向（官方 2 参 {@code partialFacing} 的等价写法，见类注释）。 */
    @Override
    protected SuperByteBuffer getRotatedModel(PumpDuctBlockEntity be, BlockState state) {
        return CachedBuffers.partialFacing(AllPartialModels.MECHANICAL_PUMP_COG, state,
                state.getValue(BlockStateProperties.FACING));
    }

    @Override
    protected void renderSafe(PumpDuctBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        // 不调 super：理由见类注释（可视化开启时父类直接 return，齿轮就没了）
        SuperByteBuffer cog = getRotatedModel(be, be.getBlockState()).reset();
        standardKineticRotationTransform(cog, be, light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }
}
