package org.valkyrienskies.clockwork.content.curiosities.clock.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.valkyrienskies.clockwork.content.curiosities.clock.ClockBlockEntity;

/**
 * 落地钟渲染器（客户端）：三根指针按世界时间绕钟面轴心旋转。
 *
 * <p>对应官方（1.20.1）{@code content/curiosities/clock/ClockRenderer.kt}，逐条变换照搬。
 *
 * <h2>为什么指针会"错位"（本轮修的根因）</h2>
 * <ol>
 *   <li><b>漏了朝向旋转</b>：{@code models/block/clock/hand_*.json} 都是以
 *       {@code facing=north} 的局部坐标导出的（指针是竖条 y=7..12/14，贴在方块南面
 *       z≈14.8/16，0° 指向 12 点）。{@code blockstates/clock.json} 对 facing 做 y=0/90/180/270
 *       的旋转，但指针是 BER 单独画的、不经过 blockstate —— 上一版没补这个角，
 *       于是 facing≠north 时指针停在北向姿态，与钟面差 90°/180°/270°。这就是实机看到的错位。</li>
 *   <li><b>转向反了</b>：官方用 {@code rotate(angle, Direction.SOUTH)}（绕 +Z 正向），
 *       上一版写成 {@code -angle}。钟面 quad（{@code clockback}）只有 {@code north} 面，
 *       玩家从北侧看钟；从北往南看时 +Z 正向旋转在屏幕上就是顺时针，与真实钟表一致。
 *       取负会让指针倒着走。</li>
 * </ol>
 *
 * <h2>角度约定</h2>
 * <p>朝向角用官方同一个表达式 {@code AngleHelper.horizontalAngle(facing.getOpposite())}：
 * SOUTH→0、NORTH→180、WEST→-90、EAST→+90（JOML 右手系），与 {@code blockstates/clock.json}
 * 的 y=0/180/270/90 逐项等价，不引入第二套约定。
 *
 * <h2>支点</h2>
 * <p>官方先 {@code translate((8,8,14)/16)} 再绕 Z 转、再平移回去。绕 Z 轴旋转与支点的 z 无关，
 * 所以与 {@code rotateCentered(angle, Z)}（支点=方块中心）几何等价；这里仍显式写支点，
 * 保持与上游可逐行对照。
 *
 * <h2>与官方的差异（如实记录，不是遗漏）</h2>
 * <p>官方在同一个渲染器里还画了钟体外框 {@code ClockworkPartials.CLOCK_FRAME} =
 * {@code block/clock/clock_ring}（OBJ 圆环）。本移植没有画：那是独立的一处"缺件"，
 * 与指针定位无关，且本工程尚无"OBJ 作为 partial 单独烘焙"的先例
 * （物品模型走的是 {@code block/clock/item} 那条 {@code neoforge:obj} 链），
 * 留作后续单独处理，避免在本次修复里引入黑紫风险。
 */
public class ClockRenderer extends SmartBlockEntityRenderer<ClockBlockEntity> {

    /** 官方 {@code rotateHand} 的支点，模型坐标 (8,8,14)/16 → 方块局部坐标。 */
    private static final double PIVOT_X = 8 / 16.0;
    private static final double PIVOT_Y = 8 / 16.0;
    private static final double PIVOT_Z = 14 / 16.0;

    public ClockRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(ClockBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        BlockState state = be.getBlockState();
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        // 与 blockstate 的 y 旋转等价：SOUTH→0、NORTH→180、WEST→-90、EAST→+90（弧度）
        float facingAngle = AngleHelper.rad(AngleHelper.horizontalAngle(facing.getOpposite()));

        VertexConsumer vb = buffer.getBuffer(RenderType.cutoutMipped());

        renderHand(ClockPartialModels.HOUR_HAND, state, facingAngle, be.getHourHandAngle(partialTicks), ms, vb, light);
        renderHand(ClockPartialModels.MINUTE_HAND, state, facingAngle, be.getMinuteHandAngle(partialTicks), ms, vb, light);
        renderHand(ClockPartialModels.SECOND_HAND, state, facingAngle, be.getSecondHandAngle(partialTicks), ms, vb, light);
    }

    private static void renderHand(PartialModel model, BlockState state, float facingAngle, float angle,
                                   PoseStack ms, VertexConsumer vb, int light) {
        // 用不带 facing 的 partial()：clock 方块虽然有 facing 属性，但这里要自己控制变换顺序
        // （先绕支点转、再整体摆朝向），用 partialFacing() 反而拿不到支点。
        // reset() 是防御性的：CachedBuffers.partial 返回的是 SuperByteBufferCache 里的共享实例，
        // renderInto() 结束时会自行 reset（javap 核实 DefaultSuperByteBuffer#renderInto 末尾调用 reset），
        // 所以正常情况下本来就是干净的；万一以后有人插了"只变换不渲染"的调用，这里也不会串味。
        SuperByteBuffer hand = CachedBuffers.partial(model, state).reset();

        hand.rotateCentered(facingAngle, Direction.UP);          // 朝向：等价 blockstate 的 y
        hand.translate(PIVOT_X, PIVOT_Y, PIVOT_Z);               // 平移到支点
        hand.rotate(angle, Direction.SOUTH);                     // 绕 +Z 正向 = 从北侧看顺时针
        hand.translate(-PIVOT_X, -PIVOT_Y, -PIVOT_Z);            // 平移到支点

        hand.light(light);
        hand.renderInto(ms, vb);
    }
}
