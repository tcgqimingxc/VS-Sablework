package org.valkyrienskies.clockwork.content.kinetics.resistor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Vector3f;
import org.valkyrienskies.clockwork.content.kinetics.resistor.RedstoneResistorBlockEntity;
import org.valkyrienskies.clockwork.content.kinetics.resistor.ResistorPartials;

/**
 * 红石电阻的方块实体渲染器：画两段"半个传动轴"（各自按该侧的倍率转动）+ 会变色的指示条。
 *
 * <p>对应旧版 {@code content.kinetics.resistor.RedstoneResistorRenderer}。
 *
 * <p>与旧版的差异：
 * <ul>
 *   <li>旧版用 {@code buffer.rotateCentered(angle, Direction)}；Create 6 的
 *       {@code SuperByteBuffer} 只保留了 {@code rotateCentered(float, Vector3fc)} 等重载，
 *       所以这里显式传轴向向量，语义相同（角度单位都是弧度）。</li>
 *   <li><b>不调 {@code super.renderSafe}</b>：Create 6 的父类实现第一句是
 *       {@code if (VisualizationManager.supportsVisualization(level)) return;}（javap -c 核实），
 *       而父类默认的"渲染用方块状态"就是本方块自己 —— Flywheel 关闭时会把整块再画一遍（叠一层），
 *       开启时又什么都不画。两段轴本类已经自己画全，因此不需要父类。</li>
 * </ul>
 */
public class RedstoneResistorRenderer extends KineticBlockEntityRenderer<RedstoneResistorBlockEntity> {

    /** 指示条在无红石 / 满红石时的颜色（旧版取的两个端点色）。 */
    private static final int COLOR_OFF = 0x2C0300;
    private static final int COLOR_ON = 0xCD0000;

    public RedstoneResistorRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(RedstoneResistorBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        BlockState state = be.getBlockState();
        Direction.Axis axis = ((IRotate) state.getBlock()).getRotationAxis(state);
        float time = AnimationTickHolder.getRenderTime(be.getLevel());
        float offset = getRotationOffsetForPosition(be, be.getBlockPos(), axis);

        // 两侧的半个轴各按自己那一侧的倍率转，玩家能一眼看出电阻在限速
        for (Direction direction : Iterate.directions) {
            if (direction.getAxis() != axis) {
                continue;
            }
            float angle = (time * be.getSpeed() * 3f / 10 % 360) * be.getRotationSpeedModifier(direction);
            angle = (angle + offset) / 180f * (float) Math.PI;
            SuperByteBuffer half = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, direction);
            kineticRotationTransform(half, be, axis, angle, light);
            half.renderInto(ms, buffer.getBuffer(RenderType.solid()));
        }

        // 指示条：颜色随红石强度在暗红 -> 亮红之间插值
        SuperByteBuffer indicator = CachedBuffers.partial(ResistorPartials.RESISTOR_INDICATOR, state);
        indicator = switch (state.getValue(BlockStateProperties.AXIS)) {
            case X -> indicator.rotateCentered((float) Math.toRadians(90), new Vector3f(0, 0, -1));
            case Z -> indicator.rotateCentered((float) Math.toRadians(90), new Vector3f(1, 0, 0));
            case Y -> indicator;
        };
        indicator.light(light)
                .color(Color.mixColors(COLOR_OFF, COLOR_ON, be.getSignal() / 15f))
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }
}
