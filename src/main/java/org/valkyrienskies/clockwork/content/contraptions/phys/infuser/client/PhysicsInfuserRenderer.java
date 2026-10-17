package org.valkyrienskies.clockwork.content.contraptions.phys.infuser.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.valkyrienskies.clockwork.ClockworkMod;
import org.valkyrienskies.clockwork.content.contraptions.client.ContraptionPartials;
import org.valkyrienskies.clockwork.content.contraptions.phys.infuser.PhysicsInfuserBlockEntity;

/**
 * 物理灌注器（{@code physics_infuser}）轴心三层水晶核心的渲染器。
 *
 * <p>对应旧版 {@code content/contraptions/phys/infuser/PhysicsInfuserRenderer.kt}（108 行）：
 * 旧版这个渲染器<b>只画核心</b>（方块本体由 blockstate 模型负责），核心由三层同心的
 * 水晶立方体叠成，用 {@code RenderUtil.renderCubeMatrix} 统一变换：
 * <pre>
 *   内层 CRYSTAL_INNER  RenderType.endPortal() + disableDiffuse（内旋：Y 与 X 同时转）
 *   中层 CRYSTAL        自研 ClockworkRenderTypes.CRYSTAL（带 shader）+ 矩阵贴图（只绕 Y 转）
 *   外层 CRYSTAL_OUTER  RenderType.entityTranslucent(purple_hue)（只绕 Y 转，紫色辉光壳）
 *   变换：scale(1.5) → translate(-(1/(1.5·4))) → translateY(上浮量·2) → 绕 Y/Z/X 转 → translateY(-4.5/16)
 * </pre>
 * 那串平移不是随手写的：水晶模型中心在 (8, 12.5, 8)/16，先减 4.5/16 把中心挪到方块中心，
 * 再减 1/6 并乘 1.5 后正好回到 (0.5,0.5,0.5)，即"核心悬在方块正中、放大到 1.5 倍"。
 *
 * <p><b>与旧版的取舍（关键差异）</b>
 * <ul>
 *   <li><b>中层</b>：旧版用的是自研 shader（{@code ClockworkRenderTypes.CRYSTAL}，本移植未移植，
 *       照抄会崩）。本移植改用 {@code RenderType.translucent()}（模型自带的水晶贴图 + 半透明混合），
 *       即"这一层不发光、只是半透明水晶"——与 {@code WanderwandItemRenderer} 对同一批模型的处理一致。</li>
 *   <li><b>内层</b>：{@code RenderType.endPortal()} 是原版渲染层，行为与旧版完全一致
 *       （已用 javap 核实：1.21.1 的 {@code BufferBuilder} 对格式里没有的元素会静默跳过，
 *       所以 POSITION-only 的 endPortal 层可以安全接收 SuperByteBuffer 写出的完整顶点）。</li>
 *   <li><b>外层</b>：照抄旧版用 {@code entityTranslucent(purple_hue)}，贴图在资源里存在。</li>
 *   <li><b>旋转</b>：旧版靠一个"每帧自增"的 {@code coreAngle} 字段（渲染时改状态），
 *       这里改用 {@code AnimationTickHolder.getRenderTime()} 直接算角度，等价且无副作用。</li>
 *   <li><b>上浮</b>：旧版 {@code getCoreOffset} 的 ASSEMBLY 分支读服务端同步的插值器；
 *       这里读 {@link PhysicsInfuserBlockEntity#getCoreRiseProgress(float)}（客户端按同步下来的总刻数自算）。</li>
 *   <li>旧版那段 {@code translateY(coreOffset.z * 2)} / {@code translateY(coreOffset.x * 2)}
 *       是复制粘贴的笔误（把 x/z 分量也当成 Y 平移；两个调用点传的 offset 都只有 Y 分量，所以无观感差异）。
 *       这里只保留一次 {@code translateY}。</li>
 * </ul>
 *
 * <p>旧版另外声明过 {@code PHYSICS_CORE} / {@code STRANGE_FLUID} / {@code ZAP} 三个局部模型，
 * 但全工程没有一处引用（grep 核实），所以本渲染器也不画它们（模型文件仍在资源里，未删除）。
 */
public class PhysicsInfuserRenderer extends SmartBlockEntityRenderer<PhysicsInfuserBlockEntity> {

    /** 核心放大倍数（旧版 {@code renderCubeMatrix(..., 1.5f, ...)}）。 */
    private static final float CORE_SCALE = 1.5f;
    /** 按 1.5 倍缩放后要把中心对回方块中心所需的平移量：1 / (scale · 4)（旧版常量）。 */
    private static final double SCALE_CENTER_OFFSET = 1.0 / (CORE_SCALE * 4.0);
    /** 水晶模型中心在 y=12.5/16，相对方块中心 (8/16) 高出 4.5/16（旧版常量）。 */
    private static final float MODEL_CENTER_Y = 4.5f / 16f;
    /** 外层紫色辉光壳的贴图（旧版 {@code RenderUtil.PURPLE_HUE}）。 */
    private static final ResourceLocation PURPLE_HUE =
            ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, "textures/block/purple_hue.png");
    /** 核心自转角速度 [度/刻]：旧版每次渲染调用把 {@code coreAngle} 加 1（≈60°/s @60fps），
     *  这里按 20 刻/秒折算成 3°/刻，转速与旧版同量级且不受帧率影响。 */
    private static final float SPIN_DEGREES_PER_TICK = 3.0f;

    public PhysicsInfuserRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(final PhysicsInfuserBlockEntity be, final float partialTicks, final PoseStack ms,
                              final MultiBufferSource buffer, final int light, final int overlay) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);

        final BlockState state = be.getBlockState();
        final float renderTicks = AnimationTickHolder.getRenderTime(be.getLevel());
        final float spin = renderTicks * SPIN_DEGREES_PER_TICK % 360.0f;
        final float rise = be.getCoreRiseProgress(partialTicks);

        // 内层：Y 与 X 同时转（旧版 innerData.rotation = (angle, angle, 0)）
        final VertexConsumer innerBuffer = buffer.getBuffer(RenderType.endPortal());
        coreLayer(ContraptionPartials.CRYSTAL_INNER, state, rise, spin, spin, 0.0f)
                .light(light).overlay(overlay).disableDiffuse().renderInto(ms, innerBuffer);

        // 中层：只绕 Y 转（旧版 data.rotation = (0, angle, 0)）
        final VertexConsumer crystalBuffer = buffer.getBuffer(RenderType.translucent());
        coreLayer(ContraptionPartials.CRYSTAL, state, rise, 0.0f, spin, 0.0f)
                .light(light).overlay(overlay).disableDiffuse().renderInto(ms, crystalBuffer);

        // 外层：紫壳，只绕 Y 转（旧版 outerData.rotation = (0, angle, 0)）
        final VertexConsumer outerBuffer = buffer.getBuffer(RenderType.entityTranslucent(PURPLE_HUE));
        coreLayer(ContraptionPartials.CRYSTAL_OUTER, state, rise, 0.0f, spin, 0.0f)
                .light(light).overlay(overlay).renderInto(ms, outerBuffer);
    }

    /**
     * 一层核心的完整变换（旧版 {@code RenderUtil.renderAndTransform} 的 SuperByteBuffer 版本）。
     *
     * <p>变换顺序不能改：{@code SuperByteBuffer} 的变换是右乘，后调用的先作用在顶点上，
     * 所以"先把模型中心挪到方块中心 → 再上浮 → 再旋转 → 最后缩放"必须按这个顺序写。
     *
     * @param rise        上浮量 0..1（旧版 {@code coreOffset.y}，先按 2 倍平移再被 1.5 倍缩放放大）
     * @param rotationX   绕 X 轴旋转 [度]（旧版 rotation.x）
     * @param rotationY   绕 Y 轴旋转 [度]（旧版 rotation.y）
     * @param rotationZ   绕 Z 轴旋转 [度]（旧版 rotation.z）
     */
    private static SuperByteBuffer coreLayer(final PartialModel model, final BlockState state, final float rise,
                                             final float rotationX, final float rotationY, final float rotationZ) {
        final SuperByteBuffer buffer = CachedBuffers.partial(model, state).reset();
        buffer.scale(CORE_SCALE);
        buffer.translate(-SCALE_CENTER_OFFSET, -SCALE_CENTER_OFFSET, -SCALE_CENTER_OFFSET);
        buffer.translateY(rise * 2.0f);
        buffer.rotateCenteredDegrees(rotationY, Direction.UP);
        buffer.rotateCenteredDegrees(rotationZ, Direction.NORTH);
        buffer.rotateCenteredDegrees(rotationX, Direction.EAST);
        buffer.translateY(-MODEL_CENTER_Y);
        return buffer;
    }

    /**
     * 装配动画里核心会升到方块上方最多 3 格（{@code rise·2} 之后再被 1.5 倍缩放放大），
     * 不放大包围盒的话核心会在方块本体离开视锥时被整块剔掉（旧版没处理，属移植新增，
     * 与 {@code PhysBearingRenderer} 放大包围盒同理）。
     */
    @Override
    public AABB getRenderBoundingBox(final PhysicsInfuserBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(4.0);
    }
}
