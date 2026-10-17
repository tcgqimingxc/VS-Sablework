package org.valkyrienskies.clockwork.content.physicalities.extendon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.valkyrienskies.clockwork.ClockworkMod;
import org.valkyrienskies.clockwork.content.physicalities.extendon.ExtendonBlock;
import org.valkyrienskies.clockwork.content.physicalities.extendon.ExtendonBlockEntity;

/**
 * 可伸缩杆（{@code extendon}）的方块实体渲染器：卷轴支架 + 活塞筒 + 两端之间的软管。
 *
 * <p>对应官方（1.20.1）{@code content/physicalities/extendon/ExtendonRenderer.kt}（212 行）。
 *
 * <h2>为什么必须有它（实机“活塞 / 软管卷轴不显示”的根因）</h2>
 * <p>{@code blockstates/extendon.json} → {@code block/extendon/block}，只有 3 个 element（底座）；
 * 官方把可动部分拆成了 {@code extendon/axis0}（两侧卷轴支架，4 个 element）、
 * {@code extendon/axis1}（中间活塞筒，3 个 element）两个局部模型，外加一段<b>程序化绘制</b>的软管。
 * 工程里既没有 {@code .renderer(...)} 也没有 BER，所以世界里只剩一块底板 ——
 * 活塞筒与卷轴支架（即用户说的“气动活塞 / 软管卷轴”）全都看不见。
 *
 * <h2>与旧版的差异（以及为什么不能照搬）</h2>
 * <p>旧版把“两端方块坐标相减”的方向<b>直接当模型局部方向</b>用（{@code getEulerAngles(direction)}
 * → {@code yaw}/{@code pitch}），再用 {@code CachedBuffers.partial(..., state)} 拿到<b>没有按
 * blockstate 旋转</b>的局部模型。也就是说：只有 {@code facing=up}（模型导出时的默认姿态）是对的，
 * 其余 5 个朝向里底座被 blockstate 转了、支架/活塞筒却没转。旧版还叠了 VS2 的 ship→world
 * 变换与船缩放，本移植不接那套（同结构内 scale=1）。
 *
 * <h2>姿态算法（这次改成“矩阵 + 逆变换”，不再按方向写分支）</h2>
 * <ol>
 *   <li><b>底座朝向</b> {@code base}：把模型局部 +Y 摆到 blockstate 的 {@code facing}。
 *       数值必须与 {@code blockstates/extendon.json} 的 x/y 完全一致（见 {@link #facingRotation}）。</li>
 *   <li><b>卷轴支架</b>：{@code base·Ry(yaw)}，跟着底座走，再绕<b>底座自身的法线</b>转到软管的水平方位
 *       —— 方位角由 {@code base} 的逆变换对连接向量求出（{@link Quaternionf#transformInverse}），
 *       不是“若 north 则 yaw=…”的枚举。</li>
 *   <li><b>活塞筒 / 软管</b>：{@code base·Ry(yaw)·Rx(-pitch)}，即在支架姿态上再绕<b>支架的销轴</b>
 *       俯仰。销轴就是 {@code axis1.json} 里那根横穿筒身、两端正好顶在 {@code axis0} 两块侧板上的
 *       横杆（局部 X）；所以活塞筒只能绕它转，不能独立选滚转 —— 软管方截面也因此与筒口对齐。</li>
 * </ol>
 * <p>这样做一次覆盖 {@code 6 朝向 × 任意连接方向}：yaw/pitch 全部由 {@code base} 的逆变换
 * 和 {@code atan2} 解析求出，组合再多也不会漏。旧实现把 {@code x} 的符号写错
 * （vanilla 的 {@code x=90} 是 {@code Rx(-90)}，不是 {@code Rx(+90)}）：{@code up}/{@code down}
 * 因 x=0/180 正负号等价而碰巧逐位正确；north/south/east/west 下整台机构（支架 + 活塞筒）绕
 * 模型局部 X 翻了 180° —— 支架从底座背面穿出，活塞筒虽然仍指向对方（同一错号 {@code base}
 * 的逆变换把方向抵消了），但销轴和软管的滚转相对底座翻了 180°。逐组合的对照表见
 * {@code docs/EXTENDON-ORIENTATION.md}。
 *
 * <h2>软管</h2>
 * <p>官方没有用 {@code extendon/hose.json}，而是用 {@code textures/block/hose.png}（9×15，直接绑纹理、
 * 不经过图集）画一条 4 侧面的方管，UV 沿 V 平铺（一“方块单位”对应 15/16，因为贴图高 15 px）。
 * 这里逐字复刻，改动是<b>姿态与起终点</b>：姿态与本端活塞筒完全相同（见上），所以方截面与方形
 * 筒口对齐；起终点从“两端方块中心”改成“两端活塞筒出口”（局部 y=13/16，见 {@code axis1.json}
 * 的最高点），于是软管两端正好顶在筒口上，不会从筒身侧面穿出、也不会悬空
 * （{@link #HOSE_START} 的推导）。V 沿长度连续取值，跨方块没有接缝。
 *
 * <p>已知限制（如实记录）：
 * <ul>
 *   <li>两端若在<b>不同</b>的物理结构（Sable 子空间）里，用双方块坐标直接相减得到的连线是错的
 *       —— 官方靠 VS2 的 {@code ClientShip} 变换处理，本移植没有接那套变换。同结构内正确。</li>
 *   <li>直方管只有一个滚转：这里对齐本端（主端）的活塞筒；若两端 {@code facing} 不同，
 *       远端筒口相对软管可能有一点滚转差。官方用世界系 yaw/pitch，是两端都不对齐，
 *       所以这里至少保证看得见的主端是贴合的。</li>
 * </ul>
 */
public class ExtendonRenderer extends SmartBlockEntityRenderer<ExtendonBlockEntity> {

    /** 官方写死的软管贴图（非图集贴图，直接绑定，UV 可平铺）。 */
    private static final ResourceLocation HOSE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, "textures/block/hose.png");
    /** 官方半径：{@code 13/16/2/sqrt(2)}。 */
    private static final float HOSE_RADIUS = 13f / 16f / 2f / (float) Math.sqrt(2);
    /** 官方 UV 换算：软管贴图高 15 px，所以 1 个方块长度 = 15/16 个 UV 单位。 */
    private static final float HOSE_UV_PER_BLOCK = 15f / 16f;
    /**
     * 活塞筒出口在模型局部坐标里的 y：{@code axis1.json} 里筒顶 element 的 {@code to[1]=13}。
     * 方块中心在 8/16，所以出口比中心远 {@link #HOSE_START}。
     */
    private static final float BARREL_MOUTH = 13f / 16f;
    /** 软管从方块中心沿连接方向退后的距离：13/16（筒口）− 8/16（中心）= 5/16。 */
    private static final float HOSE_START = BARREL_MOUTH - 0.5f;
    /** 软管至少保留这么长：相邻两方块时筒口间距是 6/16，这里只是防御“两端重合”的坏数据。 */
    private static final float MIN_HOSE_LENGTH = 1f / 16f;

    public ExtendonRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    /** 软管可以伸到方块外很远，必须允许越界渲染（官方同样重写这两个方法）。 */
    @Override
    public boolean shouldRenderOffScreen(ExtendonBlockEntity be) {
        return true;
    }

    @Override
    public boolean shouldRender(ExtendonBlockEntity be, Vec3 cameraPos) {
        return true;
    }

    @Override
    protected void renderSafe(ExtendonBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        BlockState state = be.getBlockState();
        Direction facing = state.getValue(ExtendonBlock.FACING);
        // 注意：这两个 SuperByteBuffer 是缓存里共享的实例，必须先 reset() 再叠加本帧变换
        SuperByteBuffer axis0 = CachedBuffers.partial(ExtendonPartials.AXIS0, state).reset();
        SuperByteBuffer axis1 = CachedBuffers.partial(ExtendonPartials.AXIS1, state).reset();
        Quaternionf base = facingRotation(facing);

        BlockPos otherPos = be.getConnectedPos();
        double dx = 0;
        double dy = 0;
        double dz = 0;
        float length = 0;
        if (otherPos != null) {
            dx = otherPos.getX() - be.getBlockPos().getX();
            dy = otherPos.getY() - be.getBlockPos().getY();
            dz = otherPos.getZ() - be.getBlockPos().getZ();
            length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        Quaternionf mount = base;
        Quaternionf barrel = base;
        if (length > 1e-4f) {
            // 局部方位角/俯仰角由 base 的逆变换求出：任意 facing、任意连接方向都成立，
            // 不需要按方向枚举。local 的水平分量接近 0 时（连接方向沿底座法线）只剩浮点噪声，
            // atan2 会给出随机 yaw；这种“正对着一根杆”的情况本来就不该 yaw，显式取 0。
            Vector3f local = base.transformInverse(new Vector3f((float) dx, (float) dy, (float) dz));
            float horizontal = (float) Math.hypot(local.x, local.z);
            float yaw = horizontal > 1e-5f ? (float) Math.atan2(local.x, local.z) : 0f;
            float pitch = (float) (Math.atan2(local.y, horizontal) + Math.PI * 3.0 / 2.0);
            // 支架 = base·Ry(yaw)；活塞筒/软管 = 支架·Rx(-pitch)。pitch 绕的是支架的销轴
            //（axis1 筒身上那根 x=2..14 的横杆，正好插进 axis0 两块侧板的孔），
            // 所以三者共用同一套“先绕法线 yaw、再绕销轴俯仰”的姿态 —— 销轴不会脱离支架。
            // 这也是为什么不能像别的方块那样把活塞筒直接对准世界方向：那样虽然指向对，
            // 筒的滚转却会和支架侧板错开 90°/180°。
            mount = new Quaternionf(base).mul(new Quaternionf().rotationY(yaw));
            barrel = new Quaternionf(mount).mul(new Quaternionf().rotationX(-pitch));
        }
        axis0.rotateCentered(mount);
        axis1.rotateCentered(barrel);

        VertexConsumer vb = buffer.getBuffer(RenderType.cutout());
        axis0.light(light).renderInto(ms, vb);
        axis1.light(light).renderInto(ms, vb);

        // 软管只由“主端”画一次（官方 {@code if (be.main)}），否则两端会各画一根重叠
        if (length > 1e-4f && be.isMain()) {
            renderHose(be, ms, buffer, barrel, length, otherPos);
        }
    }

    /**
     * blockstate 的 {@code (x, y)} 旋转：{@code R = Ry(-y)·Rx(-x)}，与 vanilla
     * {@code BlockModelRotation} 的 {@code new Quaternionf().rotateYXZ(-y, -x, 0)} 逐项相同。
     *
     * <p><b>为什么这里是一张 6 行表</b>：官方 {@code blockstates/extendon.json} 的六个变体并不服从
     * 同一个“把 +Y 转到 facing”的规则 —— {@code south} 用的是 {@code x=270}（而不是与 north 对称的
     * {@code x=90,y=180}），两者相差一个绕模型自身 +Y 的 180°。对这个模型这并非无关紧要：
     * {@code axis0} 东西两块侧板的 UV 完全不同（滚转 180° 会让两块侧板的贴图互换），而且
     * 活塞筒/软管的滚转是通过支架销轴（{@code [E5b]}）绑在 {@code base} 上的。
     * 所以不能自己发明 yaw/pitch，只能照抄 blockstate 的数值；
     * {@code tools/selftest_extendon_orient.py} 会解析 {@code blockstates/extendon.json} 逐项核对，
     * blockstate 一改自检立刻变红，而不是又“悄悄漏一组朝向”。
     *
     * <p><b>x 的符号</b>：vanilla 的 {@code x=90} 是 {@code Rx(-90)}。上一版写成 {@code Rx(+90)}，
     * 于是所有 x=90/270 的朝向（north/south/east/west）整台机构绕局部 X 翻到 facing 的反侧；
     * 只有 x=0（up）与 x=180（down，正负号等价）碰巧逐位正确。
     */
    private static Quaternionf facingRotation(Direction facing) {
        return switch (facing) {
            case DOWN -> blockstateRotation(180, 0);
            case UP -> blockstateRotation(0, 0);
            case NORTH -> blockstateRotation(90, 0);
            case SOUTH -> blockstateRotation(270, 0);
            case EAST -> blockstateRotation(90, 90);
            case WEST -> blockstateRotation(90, 270);
        };
    }

    /** 由 blockstate 的 {@code (x, y)} 造旋转：{@code Ry(-y)·Rx(-x)}。 */
    private static Quaternionf blockstateRotation(float xDeg, float yDeg) {
        return new Quaternionf().rotationYXZ(toRadians(-yDeg), toRadians(-xDeg), 0f);
    }

    /**
     * 画两端之间的软管。
     *
     * <p>PoseStack 依次：平移到方块中心 → 叠加 {@code barrel}（与活塞筒完全相同的姿态）。
     * 因为软管方截面要跟活塞筒的方形筒口对齐，两者必须共用同一个滚转；之后局部 +Y 正好指向另一端。
     * 管子从 {@link #HOSE_START}（本端筒口）画到 {@code length - HOSE_START}（另一端筒口）：
     * 两端活塞筒镜像对称，筒口都在各自中心靠对方 5/16 处，所以这两点恰好重合于两端的筒口。
     *
     * <p>官方从中心画到中心（整根埋在筒里），这里改成筒口到筒口：既不会有多余的一段从筒身
     * 侧面穿出（软管方截面 9.2/16 比筒身 11/16 窄，埋在里面的部分看不见），也让“软管端面
     * 顶在活塞筒端面上”这件事可以被静态断言检查。
     */
    private static void renderHose(ExtendonBlockEntity be, PoseStack ms, MultiBufferSource buffer,
                                   Quaternionf barrel, float length, BlockPos otherPos) {
        if (be.getLevel() == null) {
            return;
        }
        int lightNear = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos());
        int lightFar = LevelRenderer.getLightColor(be.getLevel(), otherPos);

        float start = HOSE_START;
        float end = Math.max(start + MIN_HOSE_LENGTH, length - HOSE_START);
        float maxV = (end - start) / HOSE_UV_PER_BLOCK;
        float r = HOSE_RADIUS;

        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(barrel);

        VertexConsumer vb = buffer.getBuffer(RenderType.entitySolid(HOSE_TEXTURE));
        PoseStack.Pose pose = ms.last();

        // 四个侧面，逐项照搬官方 renderPart：+Z / +X / -Z / -X
        quad(vb, pose, start, end, -r, r, r, r, 0, 0, 1, maxV, lightNear, lightFar);
        quad(vb, pose, start, end, r, r, r, -r, 1, 0, 0, maxV, lightNear, lightFar);
        quad(vb, pose, start, end, r, -r, -r, -r, 0, 0, -1, maxV, lightNear, lightFar);
        quad(vb, pose, start, end, -r, -r, -r, r, -1, 0, 0, maxV, lightNear, lightFar);

        ms.popPose();
    }

    /**
     * 一个侧面（官方 {@code renderQuad}）：V=0 在远端（另一端）、V=maxV 在近端（本端），
     * U 沿侧边铺满 0..1。每端取各自方块位置的亮度，避免长软管中间亮度跳变。
     */
    private static void quad(VertexConsumer vb, PoseStack.Pose pose, float yNear, float yFar,
                             float minX, float maxX, float minZ, float maxZ,
                             float nx, float ny, float nz, float maxV, int lightNear, int lightFar) {
        vertex(vb, pose, minX, yFar, minZ, nx, ny, nz, 1f, 0f, lightFar);
        vertex(vb, pose, minX, yNear, minZ, nx, ny, nz, 1f, maxV, lightNear);
        vertex(vb, pose, maxX, yNear, maxZ, nx, ny, nz, 0f, maxV, lightNear);
        vertex(vb, pose, maxX, yFar, maxZ, nx, ny, nz, 0f, 0f, lightFar);
    }

    private static void vertex(VertexConsumer vb, PoseStack.Pose pose, float x, float y, float z,
                               float nx, float ny, float nz, float u, float v, int light) {
        vb.addVertex(pose.pose(), x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    private static float toRadians(float degrees) {
        return (float) Math.toRadians(degrees);
    }
}
