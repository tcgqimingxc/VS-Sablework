package org.valkyrienskies.clockwork.content.curiosities.tools.wanderwand.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.clockwork.ClockworkMod;
import org.valkyrienskies.clockwork.content.curiosities.tools.CWToolsIndex;
import org.valkyrienskies.clockwork.content.curiosities.tools.wanderwand.WandSelection;
import org.valkyrienskies.clockwork.content.curiosities.tools.wanderwand.WanderwandItem;

import java.util.List;

/**
 * 漫游之杖的<b>选区可视化</b>：手持法杖时在世界里画出已选区（半透明面 + 线框）、待定角点标记，
 * 以及"下一个角点会框住什么"的预览。
 *
 * <h2>对应 Create 的哪个类，以及为什么画法不能照抄</h2>
 * <p>需求指的两个权威参考是 Create 6.0.10 的
 * {@code com.simibubi.create.content.contraptions.glue.SuperGlueSelectionHandler}（强力胶选区）与
 * {@code com.simibubi.create.content.schematics.client.SchematicAndQuillHandler}（蓝图/示意图选区）。
 * 用 {@code javap -p -c} 核实它们的画法后，结论是：<b>风格参数照抄，"提交 → 绘制"的管线不能照抄</b>。
 *
 * <ul>
 *   <li>{@code SuperGlueSelectionHandler#tick()}：拿主手物品 → {@code isGlue} → 取当前选择盒 →
 *       {@code Outliner.getInstance().showCluster(slot, cluster)}；
 *       其 {@code getCurrentSelectionBox()} 就是"两个角点的外接 AABB"。
 *       {@code SchematicAndQuillHandler#tick()}：{@code outliner().chaseAABB(slot, box)
 *       .colored(0x6893C9).withFaceTextures(AllSpecialTextures.CHECKERED, HIGHLIGHT_CHECKERED)
 *       .lineWidth(0.0625f).highlightFace(selectedFace)} —— 即"每客户端 tick 提交一次选区几何
 *       + 颜色 + 线宽 + 面着色"。</li>
 *   <li>但 {@code net.createmod.catnip.outliner.Outliner#renderOutlines(PoseStack, SuperRenderTypeBuffer,
 *       Vec3, float)} 在 Create 6.0.10 里<b>没有任何调用点</b>：把 {@code libs/} 下 16 个 jar 全部按字节扫过
 *       {@code renderOutlines} 这个常量池字符串，命中的只有 {@code ponder-neoforge-1.0.82} 的
 *       {@code PonderClient / PonderScene}（Ponder 场景内用）与 {@code Outliner} 自己；
 *       {@code CreateClient.onCtorClient} 注册的世界渲染入口
 *       {@code ClientEvents#onRenderWorld(RenderLevelStageEvent)} 的字节码里
 *       （{@code getStage() == Stage.AFTER_PARTICLES} → {@code DefaultSuperRenderTypeBuffer.getInstance()}
 *       → 各 RenderHandler.render(...) → {@code draw()}）也没有它的位置。</li>
 * </ul>
 *
 * <p>也就是说 1.21.1 的 Create 6 里 {@code Outliner} 是<b>只提交不绘制</b>的空管线，
 * 照抄它只会得到"什么都看不见"的法杖。因此本类沿用它的<b>风格参数</b>（颜色 {@code 0x6893C9}、
 * 线宽 {@code 0.0625f}、面着色 + 线框），绘制落到原版接口上：
 * {@link LevelRenderer#renderLineBox} 与 {@link LevelRenderer#addChainedFilledBoxVertices}
 * —— 这正是需求允许的退路（"若不可用则用原版 RenderType.lines() + LevelRenderer.renderLineBox"）。
 * 另外，1.21.1 的 Create 6 里<b>没有</b> {@code net.createmod.catnip.render.ColoredRenderType}
 * （已 javap 确认缺失），所以线框走 {@link RenderType#lines()}、面走 {@link RenderType#debugFilledBox()}。
 * 面层不能随手挑一个"看起来能画四边形"的类型：{@code debugFilledBox()} 是
 * {@code TRIANGLE_STRIP + POSITION_COLOR}，与 {@link LevelRenderer#addChainedFilledBoxVertices}
 * （按三角条带写顶点、盒与盒之间用退化三角形断开）严格配对，原版 {@code HeightMapRenderer}
 * 用的就是这一对；{@code debugQuads()} 是 {@code QUADS} 层，顶点序与条带写法不符。
 *
 * <h2>为什么用 RenderLevelStageEvent 而不是 BER（方块实体渲染器）</h2>
 * <p>{@code BlockEntityRenderer} 的调用点在 {@code LevelRenderer#renderLevel} 里，对象是
 * <b>可见区块内的方块实体</b>。而选区可视化的主体是"玩家手上的物品 + 存在物品数据组件里的若干坐标"，
 * <b>根本没有对应的方块实体</b>；就算硬造一个虚拟 BER，也还要被"所在区块已加载 / 在视锥内 /
 * 该 BE 注册了渲染器"三条卡住，选区一旦跨出加载范围或背对相机就会整块消失。
 * {@link RenderLevelStageEvent} 是每帧世界渲染的全局钩子（Create 自己也用它画世界空间特效），
 * 不依赖任何区块或方块实体，这才是叠加层该待的地方。
 *
 * <h2>为什么每帧都从手上物品读数据（以及为什么这不等于"每帧解析 NBT"）</h2>
 * <p>选区是<b>服务端权威</b>写进 {@link DataComponents#CUSTOM_DATA} 的，客户端手上的
 * {@link ItemStack} 只是同步结果的副本。客户端没有"选区变了"的事件可订阅（服务端改完整包同步，
 * 客户端只看到组件被替换），所以只能在渲染时<b>拉取</b>当前状态 —— 这是只读的，不会与服务端打架。
 *
 * <p>但"读组件"本身不便宜：{@link CustomData#copyTag()} 每次复制一整棵 NBT 树，{@link BlockPos#of(long)}
 * 每次新建对象。这个钩子每秒要跑 60~240 次，所以下面用 {@link Cache} 缓存<b>解析结果</b>，
 * 只在"物品实例变了 / 组件值变了"时重算：{@code CustomData} 是 record，值比较即可，
 * 而它平时就是同一个实例，比较退化成引用比较。稳态下每帧只做两次引用比较。
 *
 * <p><b>不改任何交互语义</b>：本类只调用 {@link WanderwandItem} / {@link WandSelection} 的只读查询，
 * 不写组件，也不碰 {@code onItemUseFirst} / {@code onEntitySwing} 的返回值
 * （那两条必须保持 CONSUME / {@code true}，理由见 {@code WanderwandItem} 类头的"返回值语义"）。
 *
 * <p>挂载点：{@code RenderLevelStageEvent} 与 {@code ClientTickEvent.Post} 都是<b>游戏总线</b>事件
 * （javap {@code RenderLevelStageEvent} 的 javadoc：fired on the main event bus）。
 * 这里<b>不写</b> {@code bus = Bus.GAME}：NeoForge 的 {@code AutomaticEventSubscriber.inject}
 * 是按"监听方法参数类型是否 {@code IModBusEvent}"分流的（javap FML 4.0.42 的字节码：
 * {@code Class.isAssignableFrom(IModBusEvent)} → {@code getEventBus()}，否则
 * {@code getGameBus()}，两个都有时"registering them separately"），
 * 而 {@code EventBusSubscriber.Bus} 属性本身已被标记待删除（编译 javac 会出 removal 警告，见 {@code CWClientSetup}）。
 */
@EventBusSubscriber(modid = ClockworkMod.MOD_ID, value = Dist.CLIENT)
public final class WanderwandSelectionRenderer {

    /** 已选区域的线框/面色，取自 Create 蓝图选区提交时用的 {@code 0x6893C9}（javap 见类头）。 */
    private static final float SELECT_R = 0x68 / 255f;
    private static final float SELECT_G = 0x93 / 255f;
    private static final float SELECT_B = 0xC9 / 255f;
    /** 面填充的不透明度：比线框淡得多，只用来"读出体积"，不挡住里面的机器。 */
    private static final float SELECT_FACE_ALPHA = 0.1f;
    /** 线框不透明度。 */
    private static final float SELECT_LINE_ALPHA = 0.85f;

    /** 待定角点标记的颜色：Create 的 {@code Color.SPRING_GREEN}，与蓝色选区一眼区分。 */
    private static final float CORNER_R = 0.25f;
    private static final float CORNER_G = 0.9f;
    private static final float CORNER_B = 0.25f;
    /** 角点标记的边长（格）。整格框会把标记埋进方块里，取 1/4 格才看得清"点在哪"。 */
    private static final double CORNER_MARKER_SIZE = 0.25;

    /**
     * 已选区域线框在<b>世界空间</b>的粗细（格）。
     *
     * <p>Create 提交给 Outliner 的是 {@code lineWidth(0.0625f)} = 1/16 格，但原版
     * {@link LevelRenderer#renderLineBox} 没有线宽参数（画的是几何意义上的 12 条边，
     * 粗细由渲染管线决定），所以这里把线框盒<b>外扩</b>半个 0.0625 格来还原 World 空间的观感粗细：
     * 近处看得到"包了一层"，远处与原版线宽不可分辨。
     */
    private static final double BOX_LINE_INFLATE = 0.0625 / 2;

    /** 预览框（角点 → 准星所指方块，尚未提交）的琥珀色：明确区别于已提交的蓝色选区。 */
    private static final float PREVIEW_R = 1.0f;
    private static final float PREVIEW_G = 0.78f;
    private static final float PREVIEW_B = 0.2f;
    /** 取消选择模式下的预览改成红色：这次右键是"减掉"而不是"加上"。 */
    private static final float PREVIEW_DESELECT_R = 1.0f;
    private static final float PREVIEW_DESELECT_G = 0.35f;
    private static final float PREVIEW_DESELECT_B = 0.3f;
    private static final float PREVIEW_ALPHA = 0.7f;

    /** 预览距离的余量（格）：在原版交互距离（{@code Attributes.BLOCK_INTERACTION_RANGE}）上再加这么多。 */
    private static final double PREVIEW_RANGE_PADDING = 1.0;

    private WanderwandSelectionRenderer() {
    }

    // ------------------------------------------------------------------ 数据缓存

    /**
     * 从"手上的法杖"解析出来的一份只读快照。
     *
     * <p>为什么除物品栈引用之外还要记 {@link CustomData}：法杖换手、被别的物品替换时
     * {@link ItemStack} 实例会变；而服务端改完选区同步过来时，<b>物品栈实例往往不变、变的只是组件</b>。
     * 两个条件都看，才能既不过度重算、也不显示过期选区。
     */
    private static final class Cache {
        @Nullable
        private ItemStack stack;
        @Nullable
        private CustomData data;
        private List<WandSelection.Box> boxes = List.of();
        @Nullable
        private BlockPos corner;

        /** 只在物品实例或组件实例真的换了时才重新解析。 */
        private void refresh(final ItemStack current) {
            final CustomData currentData = current.get(DataComponents.CUSTOM_DATA);
            // 两条都要比：换手/换物品时 ItemStack 变了而组件没变；服务端改完选区同步过来时
            // 往往是 ItemStack 没变、组件被整体替换。CustomData 是 record（不可变），
            // 所以"实例换了"就是"值变了"，不会出现漏刷。
            if (current == this.stack && currentData == this.data) {
                return;
            }
            this.stack = current;
            this.data = currentData;
            this.boxes = List.copyOf(WandSelection.read(current));
            this.corner = WanderwandItem.getPendingCorner(current);
        }

        private boolean isEmpty() {
            return this.stack == null;
        }

        private void clear() {
            this.stack = null;
            this.data = null;
            this.boxes = List.of();
            this.corner = null;
        }
    }

    /** 在 ClientTickEvent 里刷新、渲染钩子里只读：渲染线程不再做任何解析或分配。 */
    private static final Cache CACHED = new Cache();

    // ------------------------------------------------------------------ 事件入口

    /**
     * 每客户端 tick 刷新一次缓存。
     *
     * <p>为什么把"解析"放在 tick 而不是渲染里：客户端 tick 固定 20 次/秒且不在渲染线程上，
     * 放这里可以把每帧的开销压到两次引用比较。代价是选区最多晚 1 tick（50ms）才画出来，不可感知。
     */
    @SubscribeEvent
    public static void onClientTick(final ClientTickEvent.Post event) {
        final Minecraft mc = Minecraft.getInstance();
        final LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        final ItemStack wand = heldWand(player);
        if (wand == null) {
            // 没拿法杖就清缓存：否则玩家把法杖放进箱子后，旧选区还会留在他自己屏幕上。
            if (!CACHED.isEmpty()) {
                CACHED.clear();
            }
            return;
        }
        CACHED.refresh(wand);
    }

    /**
     * 世界渲染钩子：画已选区域、待定角点标记，以及预览框。
     *
     * <p>阶段选择（javap 核实）：{@code AFTER_TRANSLUCENT_BLOCKS} 由
     * {@code LevelRenderer#renderSectionLayer} 在"半透明区块已画完、{@code clearRenderState()} 还未调用"
     * 的位置派发（1.21.1 的 LevelRenderer.java:1337；实体、方块实体、命中框都在它之前画完），
     * 此时深度缓冲已经是最终值，半透明面与线框才会被方块正确遮挡 —— 这正是选区叠加层要的效果。
     * 不选 Create 用的 {@code AFTER_PARTICLES} 只是避免与它的世界特效挤在同一批。
     *
     * <p><b>阶段不决定 buffer 能不能用</b>：本类用的 {@code mc.renderBuffers().bufferSource()}
     * 与阶段无关；决定"能不能直接写顶点"的是同一批里混用了几个 RenderType，
     * 原因见 {@link #drawFilledFaces} 的注释。
     */
    @SubscribeEvent
    public static void onRenderLevelStage(final RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        final LocalPlayer player = mc.player;
        final ClientLevel level = mc.level;
        if (player == null || level == null || player.isSpectator()) {
            return;
        }

        final ItemStack wand = heldWand(player);
        if (wand == null) {
            return;
        }
        // 兜底：正常时序下 tick 已经刷过缓存（同一 tick 的 tick 一定先于 render）。
        // 这里再比一次是为了"绝不画不属于手上这把法杖的选区"，代价只是两次引用比较。
        if (CACHED.stack != wand) {
            CACHED.refresh(wand);
        }
        final List<WandSelection.Box> boxes = CACHED.boxes;
        final BlockPos corner = CACHED.corner;
        if (boxes.isEmpty() && corner == null) {
            // 空选区 + 无待定角点：一帧的代价只有上面几次判断，什么都不画。
            return;
        }

        final PoseStack ms = event.getPoseStack();
        final Vec3 camera = event.getCamera().getPosition();
        // 世界空间叠加层的 buffer 取自原版 RenderBuffers。lines() 与 debugFilledBox() 都"不在"
        // RenderBuffers 的 fixedBuffers 里（javap 核实：fixedBuffers 只有 solid/cutout/cutoutMipped/
        // translucent、各 entity/glint 层与 DESTROY_TYPES），属于 BufferSource 的"共享缓冲"，
        // 由它用 lastSharedType 串行化。所以下面严格按 RenderType 分批，绝不把某个 consumer 留到
        // 取下一个 RenderType 之后 —— 这正是本次崩溃的根因。
        final MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        ms.pushPose();
        try {
            // 平移矩阵累积在 PoseStack 上（与 Sable 的 LevelRendererMixin、VS2 的 MixinDebugRenderer 同一写法）：
            // 下面所有坐标都是"相对相机的世界坐标"，把相机平移交给 PoseStack，就不用在每个顶点上做减法。
            ms.translate(-camera.x, -camera.y, -camera.z);

            // 先画完所有面（一个 debugFilledBox 批次），再画线：线盖在面上，观感与 Create 选区一致。
            drawFilledFaces(ms, buffers, boxes);

            // 必须等面的批次 endBatch 之后再 getBuffer(lines)：两者都是共享类型，
            // 取 debugFilledBox 时 BufferSource 会隐式 endBatch 掉上一个 lastSharedType（lines），
            // 把它的 BufferBuilder build() 掉（building=false）；旧代码把 lines consumer 存在循环外，
            // 之后 renderLineBox 再写顶点就抛 IllegalStateException: Not building!（本次实机崩溃）。
            final VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            try {
                // 逐盒画：合并后的选区仍可能是多个互不相邻的长方体，逐个画才看得出是几组。
                for (int i = 0; i < boxes.size(); i++) {
                    drawSelectionOutline(ms, lines, worldBox(boxes.get(i)));
                }

                if (corner != null) {
                    drawCornerMarker(ms, lines, corner);
                    final BlockPos lookedAt = lookedAtBlock(player, level);
                    if (lookedAt != null) {
                        drawPreview(ms, lines, wand, WandSelection.Box.of(corner, lookedAt), corner);
                    }
                }
            } finally {
                // BufferSource 是共享的：不收尾就只进 CPU 缓冲、不进 GPU，什么都看不到。
                // endBatch(type) 对没有 builder 的类型是 no-op（javap：Map#remove 返回 null 就直接返回），
                // 所以异常路径重复调用也安全；它只收我们自己的 RenderType，不会动别人的批次。
                buffers.endBatch(RenderType.lines());
            }
        } finally {
            // 本阶段 event 给的是新建的空 PoseStack（javap：RenderLevelStageEvent 的构造器把 null 换成
            // new PoseStack()），仍要 push/pop 成对，异常时也不能把栈留给下一个监听器。
            ms.popPose();
        }
    }

    // ------------------------------------------------------------------ 画法

    /**
     * 把所有已选盒的<b>半透明面</b>写进同一个 {@link RenderType#debugFilledBox()} 批次。
     *
     * <p><b>为什么是这个 RenderType</b>（javap 1.21.1 的 {@code RenderType} 静态初始化）：
     * {@code debug_filled_box} = {@code TRIANGLE_STRIP + POSITION_COLOR}，而
     * {@link LevelRenderer#addChainedFilledBoxVertices} 正是按三角条带写顶点（每个盒首尾各 3 个
     * 重复顶点，盒与盒之间的连接三角形因此全是退化三角形，不会连出多余的带）。原版
     * {@code HeightMapRenderer} 就是 {@code getBuffer(debugFilledBox())} 之后连写多个盒子的
     * {@code addChainedFilledBoxVertices}（javap 核实），这一对是原版验证过的。
     * 反过来说：{@code RenderType.translucent()} 要 UV/lightmap，顶点格式与这个写顶点函数不兼容；
     * 自建 {@code RenderType.create} 只是重复原版已有的层，没有必要。
     *
     * <p><b>为什么整批只取一次 consumer、画完立刻 endBatch</b>：
     * 它不是 fixed buffer，而是 BufferSource 的共享缓冲。一旦中途去取另一个共享 RenderType，
     * BufferSource 会隐式收掉这一批（{@code BufferBuilder#build()} 把 building 置 false），
     * 旧引用再写顶点就是 "Not building!"。整批写完立刻收尾，既只有一次 draw call，
     * 也保证异常路径上不残留未 end 的批次。
     */
    private static void drawFilledFaces(final PoseStack ms, final MultiBufferSource.BufferSource buffers,
                                        final List<WandSelection.Box> boxes) {
        if (boxes.isEmpty()) {
            return;
        }
        final VertexConsumer faces = buffers.getBuffer(RenderType.debugFilledBox());
        try {
            for (int i = 0; i < boxes.size(); i++) {
                final AABB box = worldBox(boxes.get(i));
                LevelRenderer.addChainedFilledBoxVertices(ms, faces,
                        box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                        SELECT_R, SELECT_G, SELECT_B, SELECT_FACE_ALPHA);
            }
        } finally {
            buffers.endBatch(RenderType.debugFilledBox());
        }
    }

    /**
     * 一个已选盒的线框。
     *
     * <p>{@link RenderType#lines()} 的顶点格式是 {@code POSITION_COLOR_NORMAL}，
     * {@link LevelRenderer#renderLineBox} 正好逐顶点补 pos/color/normal（javap 核实），两者严格配对；
     * LINES 不是 connected 图元，所以所有线框可以共用一个批次（一次 draw call）。
     * 盒稍微外扩半个线宽，还原 Create 的 {@code lineWidth(0.0625f)} 粗细（见 {@link #BOX_LINE_INFLATE}）。
     */
    private static void drawSelectionOutline(final PoseStack ms, final VertexConsumer lines, final AABB box) {
        LevelRenderer.renderLineBox(ms, lines, box.inflate(BOX_LINE_INFLATE),
                SELECT_R, SELECT_G, SELECT_B, SELECT_LINE_ALPHA);
    }

    /**
     * 待定角点标记：方块中心处一个小立方体线框，外加两条横贯方块中心的短线。
     *
     * <p>为什么还画那两条短线：小立方体在远处只有一个像素，光靠它玩家分不清角点落在哪个方块。
     * 横向短线让标记在任何距离都能被认出来（Create 的各类 gizmo 也是这个做法）。
     */
    private static void drawCornerMarker(final PoseStack ms, final VertexConsumer lines, final BlockPos corner) {
        final double cx = corner.getX() + 0.5;
        final double cy = corner.getY() + 0.5;
        final double cz = corner.getZ() + 0.5;
        // 以方块中心为中心的 CORNER_MARKER_SIZE 立方体
        final double h = CORNER_MARKER_SIZE * 0.5;
        LevelRenderer.renderLineBox(ms, lines,
                cx - h, cy - h, cz - h, cx + h, cy + h, cz + h,
                CORNER_R, CORNER_G, CORNER_B, 1f);

        final double arm = 0.5;
        final double thin = 0.005;
        LevelRenderer.renderLineBox(ms, lines,
                cx - arm, cy - thin, cz - thin, cx + arm, cy + thin, cz + thin,
                CORNER_R, CORNER_G, CORNER_B, 1f);
        LevelRenderer.renderLineBox(ms, lines,
                cx - thin, cy - thin, cz - arm, cx + thin, cy + thin, cz + arm,
                CORNER_R, CORNER_G, CORNER_B, 1f);
    }

    /**
     * 预览：从待定角点到准星所指方块的那个盒，随时告诉玩家"再点一下会框住什么"。
     *
     * <p>对应蓝图/示意图工具的手感（{@code SchematicAndQuillHandler} 在第二个角点落定前也一直
     * {@code chaseAABB} 同一个盒）。这里只画线框不画面，让它与"已提交"的蓝色区域一眼可分。
     */
    private static void drawPreview(final PoseStack ms, final VertexConsumer lines, final ItemStack wand,
                                    final WandSelection.Box preview, final BlockPos corner) {
        final boolean deselect = WanderwandItem.getMode(wand) == WanderwandItem.Mode.DESELECT;
        final float r = deselect ? PREVIEW_DESELECT_R : PREVIEW_R;
        final float g = deselect ? PREVIEW_DESELECT_G : PREVIEW_G;
        final float b = deselect ? PREVIEW_DESELECT_B : PREVIEW_B;

        LevelRenderer.renderLineBox(ms, lines, worldBox(preview), r, g, b, PREVIEW_ALPHA);

        // 角点 → 预览体中心：镜头偏开时也能看出这个盒是从哪个角点长出来的。
        // 退化线段要跳过：准星正指着待定角点所在方块时，预览体就是这一格，两个端点完全重合；
        // 而 renderLineBox 对每条边算 sqrt(dx²+dy²+dz²) 再做归一化（javap LevelRenderer#renderLineBox），
        // 长度为 0 的边会写出 NaN 法线 —— 这在 lines 顶点格式（POSITION_COLOR_NORMAL）里是脏数据。
        final double cx = corner.getX() + 0.5;
        final double cy = corner.getY() + 0.5;
        final double cz = corner.getZ() + 0.5;
        final double tx = (preview.min().getX() + preview.max().getX() + 1) * 0.5;
        final double ty = (preview.min().getY() + preview.max().getY() + 1) * 0.5;
        final double tz = (preview.min().getZ() + preview.max().getZ() + 1) * 0.5;
        if (Math.abs(tx - cx) > 1e-4 || Math.abs(ty - cy) > 1e-4 || Math.abs(tz - cz) > 1e-4) {
            LevelRenderer.renderLineBox(ms, lines, cx, cy, cz, tx, ty, tz, r, g, b, PREVIEW_ALPHA);
        }
    }

    // ------------------------------------------------------------------ 查询

    /**
     * 玩家哪只手上拿着漫游之杖（主手优先）。
     *
     * <p><b>只认本模组的法杖</b>：选区数据只有 {@link WanderwandItem} 会写，
     * 别的物品即使碰巧带了同名数据组件也不该被画出来。
     */
    @Nullable
    private static ItemStack heldWand(final LocalPlayer player) {
        final ItemStack main = player.getMainHandItem();
        if (main.is(CWToolsIndex.WANDERWAND.get())) {
            return main;
        }
        final ItemStack off = player.getOffhandItem();
        return off.is(CWToolsIndex.WANDERWAND.get()) ? off : null;
    }

    /** 准星指向的方块；没指到方块、指得太远、或指到了空气时返回 {@code null}。 */
    @Nullable
    private static BlockPos lookedAtBlock(final LocalPlayer player, final ClientLevel level) {
        if (!(Minecraft.getInstance().hitResult instanceof BlockHitResult hit)) {
            return null;
        }
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        // 取"玩家到该方块中心的距离"和交互距离比：玩家把方块放下/垫脚时命中点可能落在方块背面，
        // 用方块中心比用命中点更稳定。交互距离取原版属性（创造模式更远），加 1 格余量。
        final double reach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + PREVIEW_RANGE_PADDING;
        if (player.getEyePosition().distanceToSqr(Vec3.atCenterOf(hit.getBlockPos())) > reach * reach) {
            return null;
        }
        // 客户端这边可能还没同步"方块已被挖掉"，此时落点仍在；指到空气就不画预览，免得凭空多一个框。
        return level.getBlockState(hit.getBlockPos()).isAir() ? null : hit.getBlockPos();
    }

    /** 方块盒 → 世界 {@link AABB}（上界 +1，因为方块盒的两个角都是"含"的）。 */
    private static AABB worldBox(final WandSelection.Box box) {
        return new AABB(box.min().getX(), box.min().getY(), box.min().getZ(),
                box.max().getX() + 1.0, box.max().getY() + 1.0, box.max().getZ() + 1.0);
    }
}
