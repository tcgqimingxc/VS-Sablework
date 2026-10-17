package org.valkyrienskies.clockwork.content.curiosities.tools.wanderwand;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.clockwork.config.ClockworkConfig;
import org.valkyrienskies.clockwork.content.contraptions.phys.infuser.IAreaSelector;
import org.valkyrienskies.clockwork.content.contraptions.phys.infuser.PhysicsInfuserBlockEntity;

import java.util.Collection;
import java.util.List;

/**
 * 漫游之杖（Wanderwand）。
 *
 * <p>对应旧版 {@code tools/wanderwand/WanderwandItem.kt}。核心玩法：把若干方块区域"记"进法杖，
 * 之后交给物理灌注器（Physics Infuser）或重力枪把选区分组装配成结构。
 *
 * <p>本移植实现的是**最小可用核心流程**（旧版 beta 也只实现了这两项，语言文件里写着
 * "BETA WARNING : Currently, only SELECT and DESELECT function."）：
 * <ul>
 *   <li>右键方块：第一次记角点 A，第二次记角点 B 并把这块区域并入选区（自动合并相邻区域）；</li>
 *   <li>潜行右键：在 SELECT / DESELECT 之间切换模式；</li>
 *   <li>左键（挥动手臂）：清空选区 —— 对应旧版"左键删掉指向的那块选区 / 重来"的核心手感。</li>
 * </ul>
 *
 * <h2>返回值语义（1.21.1 实机踩坑，动这个类之前必读）</h2>
 * 右键链路上**不能返回 {@link InteractionResult#SUCCESS}**：原版在"交互成功"时会顺手挥手，
 * 而本物品用 {@code onEntitySwing} 实现"左键清空选区"，于是右键会把自己的选区清掉：
 * <ol>
 *   <li>客户端 {@code Minecraft#startUseItem}（:1744-1754 方块、:1765-1774 空气）：
 *       {@code interactionresult.shouldSwing()} 为真就 {@code player.swing(hand)}；</li>
 *   <li>{@code InteractionResult#shouldSwing()} 对 SUCCESS / SUCCESS_NO_ITEM_USED 为真，对 CONSUME 为假
 *       （{@code consumesAction()} 对两者都为真）；</li>
 *   <li>{@code LocalPlayer#swing}（:307-309）在 {@code super.swing()} 之后**无条件**发
 *       {@code ServerboundSwingPacket}（本地挥手被 {@code onEntitySwing} 拦掉也不影响发包）；</li>
 *   <li>服务端 {@code ServerGamePacketListenerImpl#handleAnimate}（:1437）→ {@code player.swing(hand)}
 *       → {@code LivingEntity#swing}（:1812-1814）→ {@code stack.onEntitySwing(...)} → 清空选区；</li>
 *   <li>服务端自己也会挥手：{@code handleUseItemOn}（:1134-1136）与 {@code handleUseItem}（:1172-1174）
 *       同样按 {@code shouldSwing()} 调 {@code player.swing(hand, true)}。</li>
 * </ol>
 * 症状就是"右键有提示（写了角点）但选区永远累积不起来"，玩家描述为"法杖无法使用"。
 * 因此本类所有右键路径统一返回 {@link InteractionResult#CONSUME}：既吃掉这次右键
 * （不再交给方块），又不挥手（不触发左键逻辑）。
 *
 * <p>移植差异（明确列出，避免误以为功能缺失）：
 * <ul>
 *   <li>旧版是"客户端 GUI 选工具 + 网络包"驱动的：{@code WanderwandSelectionScreen} 选模式、
 *       {@code WandwandHandler#onMouseInput} 在客户端吞掉**所有**左右键（含对着空气、对着实体、
 *       对着箱子一类会消费交互的方块），再发 {@code WandSelectionPacket} 到服务端。
 *       本移植不做 GUI/网络包（那是客户端的独立切片），改为用 NeoForge 的物品钩子实现等价手感：
 *       见下面对 {@link #onItemUseFirst} / {@link #use} 的说明。WELD / ATTACH / BIND 三种模式旧版
 *       同样只有 {@code //TODO} 空实现，因此这里不注册它们，不制造"能选但没反应"的假功能。</li>
 *   <li>选区从"物品 NBT"改为 1.21.1 的数据组件 {@link DataComponents#CUSTOM_DATA}，结构见 {@link WandSelection}。</li>
 *   <li>旧版点到物理灌注器、或点到"在飞船上的方块"时会把右键让给原版（{@code SelectTool.kt:26-31}）。
 *       这里保留了灌注器的让路（否则灌注器收不到手持法杖的右键，装配流程失效）；
 *       飞船方块的让路暂未移植——本移植的选区是普通世界坐标，点到子等级（sub-level）方块上选点不会出错，
 *       等 Sable 侧的"方块属于哪个子等级"查询接进来再补。</li>
 *   <li>旧版潜行时是"让路"（{@code SelectTool.kt:21 if (crouching) return false}），模式靠 GUI 切换；
 *       本移植没有 GUI，改用潜行右键切换模式，**并且**补了两个防呆：
 *       (a) 切换模式时除了行动栏，额外发一条聊天栏提示（旧的模式图标/全息图没移植，这条是唯一的持久提示）；
 *       (b) "取消选择"模式下选区为空时自动切回"选择"模式并说明原因
 *       —— 否则玩家被误切到取消选择模式后，右键永远选不中任何东西（实机就是这么卡住的）。</li>
 * </ul>
 *
 * <h2>选区可视化（只读，不在本类）</h2>
 * <p>手持本物品时世界里的"已选区线框 + 角点标记 + 预览框"由客户端
 * {@code client/WanderwandSelectionRenderer} 画（对应 Create 的强力胶/蓝图选区）。
 * 它只调用本类的 {@link #getMode} / {@link #getPendingCorner} 与 {@link WandSelection} 的只读方法，
 * 不改任何数据、也不经过下面这些交互入口 —— 所以本类的返回值语义与选区写入时机完全没被影响。
 */
public class WanderwandItem extends Item implements IAreaSelector {

    private static final String KEY_CORNER = WandSelection.KEY_CORNER;
    private static final String KEY_DESELECT = "WandDeselect";
    /** 提示文案 key 前缀，具体条目见 {@code assets/vs_clockwork/lang/*.json}。 */
    private static final String MSG = "vs_clockwork.wanderwand.msg.";

    /** 选区模式。旧版 {@code tool/ToolType.kt} 里可用的是前两个。 */
    public enum Mode {
        SELECT, DESELECT
    }

    public WanderwandItem(Properties properties) {
        super(properties);
    }

    /**
     * 把选中的若干长方体展开成方块坐标集合，供物理灌注器（{@code IAreaSelector} 契约）装配使用。
     *
     * <p>展开是"现算"的：法杖只存长方体，避免把成千上万个坐标写进物品数据里。
     * 未选择时返回 {@code null}，调用方（灌注器）会退化成"装配相邻连通结构"。
     * 选区在写入时已按 {@code infuser.maxBlocks} 限过体积（见 {@link #select}），所以这里不会展开出天量坐标。
     */
    @Override
    public java.util.@Nullable Collection<BlockPos> cw$getSelectedBlocks(ItemStack stack, Level level) {
        List<WandSelection.Box> boxes = WandSelection.read(stack);
        if (boxes.isEmpty()) {
            return null;
        }
        // LinkedHashSet：保持遍历顺序稳定，去重相邻长方体之间的重叠部分
        java.util.Set<BlockPos> blocks = new java.util.LinkedHashSet<>();
        for (WandSelection.Box box : boxes) {
            BlockPos min = box.min();
            BlockPos max = box.max();
            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int y = min.getY(); y <= max.getY(); y++) {
                    for (int z = min.getZ(); z <= max.getZ(); z++) {
                        blocks.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return blocks;
    }

    public static Mode getMode(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return Mode.SELECT;
        }
        CompoundTag tag = data.copyTag();
        return tag.getBoolean(KEY_DESELECT) ? Mode.DESELECT : Mode.SELECT;
    }

    private static void setMode(ItemStack stack, Mode mode) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
                tag -> tag.putBoolean(KEY_DESELECT, mode == Mode.DESELECT));
    }

    /**
     * 右键方块的接线点。
     *
     * <p><b>为什么是 {@code onItemUseFirst} 而不是只写 {@code useOn}</b>：
     * 1.21.1 + NeoForge 的交互链是
     * {@code ServerPlayerGameMode#useItemOn}:356 / {@code MultiPlayerGameMode#performUseItemOn}:317
     * 先调 {@code ItemStack#onItemUseFirst}，只有它返回 PASS 才会在 :362/:324 调**方块自己的**
     * {@code useItemOn}/{@code useWithoutItem}，最后才轮到 {@code ItemStack#useOn}。
     * 只要目标方块消费了交互（箱子、熔炉、门、拉杆、Create 的机器…），后面的链路根本不会被调用。
     * 旧版是在客户端吞掉所有右键的，所以"对着任何东西右键都有反应"。
     * 这里把逻辑放在 {@code onItemUseFirst}，就恢复了旧版的手感：任何方块上右键都由法杖接管。
     */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return handleWandClick(context);
    }

    /**
     * 兜底入口：某些第三方链路（例如别的模组直接调 {@code Item#useOn}）不会走 {@code onItemUseFirst}。
     *
     * <p>注意 NeoForge 21.1 的服务端 {@code ItemStack#useOn}:357-361 **不会**调 {@code Item#useOn}
     * （它走 {@code CommonHooks.onPlaceItemIntoWorld} 的放置流程），所以这个覆写只在客户端/第三方调用时生效；
     * 逻辑与 {@link #onItemUseFirst} 共用一份实现，不会重复执行——因为 {@code onItemUseFirst} 一旦返回 CONSUME，
     * 原版就不会再走到这里。
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        return handleWandClick(context);
    }

    private InteractionResult handleWandClick(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        // 把右键让给物理灌注器：它的 useItemOn 要靠"手持 IAreaSelector（本物品就是）"来触发装配，
        // 而 onItemUseFirst 在方块之前执行，不让路的话灌注器永远收不到这次右键。
        // 旧版 SelectTool.kt:26-31 有同样的让路（"if phys infuser, don't let select → return false"）。
        // 这个判断必须在潜行判断之前：潜行右键灌注器时也不该被法杖吃掉（虽然原版对潜行方块交互另有让路规则）。
        if (level.getBlockEntity(context.getClickedPos()) instanceof PhysicsInfuserBlockEntity) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            // 客户端只负责"吃掉这次右键"（让原版不再把这次右键交给方块）；
            // 真正的数据改动与提示都在服务端。必须是 CONSUME 而不是 SUCCESS —— 见类头"返回值语义"。
            return InteractionResult.CONSUME;
        }

        ItemStack stack = context.getItemInHand();
        BlockPos clicked = context.getClickedPos();

        if (player.isShiftKeyDown()) {
            return toggleMode(level, player, stack, clicked);
        }

        Mode mode = getMode(stack);
        // 防呆（实机踩过）：取消选择模式下选区为空时，"取消"什么也做不了，
        // 玩家会以为法杖坏了。这个状态本身没有意义，直接切回选择模式并说明。
        if (mode == Mode.DESELECT && WandSelection.read(stack).isEmpty()) {
            setMode(stack, Mode.SELECT);
            mode = Mode.SELECT;
            actionBar(player, Component.translatable(MSG + "auto_select").withStyle(ChatFormatting.YELLOW));
        }

        BlockPos corner = readCorner(stack);
        if (corner == null) {
            writeCorner(stack, clicked);
            actionBar(player, Component.translatable(MSG + "corner", format(clicked), status(stack)));
            playClick(level, clicked);
            return InteractionResult.CONSUME;
        }

        WandSelection.Box box = WandSelection.Box.of(corner, clicked);
        clearCorner(stack);
        if (mode == Mode.SELECT) {
            select(player, stack, box);
        } else {
            deselect(player, stack, box);
        }
        playClick(level, clicked);
        return InteractionResult.CONSUME;
    }

    /** 潜行右键：切换模式。清掉半截的角点（换模式后沿用旧角点只会让人困惑）。 */
    private static InteractionResult toggleMode(Level level, Player player, ItemStack stack, BlockPos clicked) {
        Mode next = getMode(stack) == Mode.SELECT ? Mode.DESELECT : Mode.SELECT;
        setMode(stack, next);
        clearCorner(stack);
        Component message = Component.translatable(MSG + "mode_switch", modeName(next), status(stack));
        // 聊天栏 + 行动栏各发一条：旧版的模式指示（工具 GUI / 全息图）没移植，行动栏一闪而过容易漏看，
        // 而"当前是什么模式"直接决定右键能不能选中，必须让玩家看得见（实机就是因为看不见才卡住）。
        player.displayClientMessage(message, false);
        actionBar(player, message);
        playClick(level, clicked);
        return InteractionResult.CONSUME;
    }

    /** 选择模式：把 A→B 的盒并入选区，并按配置的上限拒绝过大的选区。 */
    private static void select(Player player, ItemStack stack, WandSelection.Box box) {
        List<WandSelection.Box> merged = WandSelection.merged(stack, box);
        long total = WandSelection.totalVolume(merged);
        int max = ClockworkConfig.server().infuserMaxBlocks.get();
        if (total > max) {
            // 上限与灌注器共用（infuser.maxBlocks）：超过上限的选区灌注器也会整块跳过，
            // 而且 cw$getSelectedBlocks 会把长方体展开成逐格坐标，超大选区会直接卡死服务端线程。
            actionBar(player, Component.translatable(MSG + "too_big", total, max).withStyle(ChatFormatting.RED));
            return;
        }
        WandSelection.write(stack, merged);
        actionBar(player, Component.translatable(MSG + "selected", box.volume(), status(stack)));
    }

    /** 取消选择模式：删掉与 A→B 相交的已选区域。 */
    private static void deselect(Player player, ItemStack stack, WandSelection.Box box) {
        List<WandSelection.Box> before = WandSelection.read(stack);
        List<WandSelection.Box> after = WandSelection.removeIntersecting(stack, box);
        int removed = before.size() - after.size();
        if (removed <= 0) {
            actionBar(player, Component.translatable(MSG + "nothing_to_deselect").withStyle(ChatFormatting.YELLOW));
            return;
        }
        actionBar(player, Component.translatable(MSG + "deselected", removed, status(stack)));
    }

    /**
     * 右键空气：旧版客户端吞掉所有右键，所以在空中右键也会走工具逻辑；
     * 1.21.1 里"没打到方块"的右键走 {@code Item#use}，不补这个钩子就完全没反馈。
     * 这里给出行动栏状态（模式 + 已选区域数/方块数 + 按键提示），让玩家随时能确认法杖当前状态。
     *
     * <p>客户端返回 {@link InteractionResultHolder#consume} 而不是 success：理由同 {@link #handleWandClick}
     * （SUCCESS 会让客户端挥手 → 服务端 {@code onEntitySwing} 把选区清掉）。
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            actionBar(player, status(stack).append(hint()));
        }
        return InteractionResultHolder.consume(stack);
    }

    /**
     * 左键语义：旧版 {@code WanderwandHandler#onMouseInput(button = 0)} 会吞掉左键并删掉点击处的选区
     * （{@code SelectionToolBase#handleLeftClick} → {@code WandSelectionPacket(..., leftClick = true)}，
     * 服务端只删"包含该点的那一块"）。
     *
     * <p>1.21.1 只有 {@code onEntitySwing} 这个通用钩子（拿不到被指向的方块），因此这里把"左键"映射为**清空选区**。
     *
     * <p>返回 {@code true} 表示"这次挥动由物品处理"：原版 {@code LivingEntity#swing} 会直接 return，
     * 不再播放普通的挥手动画（旧版对 gravitron/drill/saw 用的是 {@code disable_swing_animation} 标签，
     * 对应 NeoForge 的 {@code IItemExtension#onEntitySwing}，已核实 1.21.1 存在且由 {@code LivingEntity#swing} 调用）。
     *
     * <p><b>注意</b>：挥动也会由别的动作触发（原版左键挖/打、丢弃物品 Q）。
     * 右键已经改成 CONSUME 不挥手了（见类头），所以这里只会在真正的左键动作上被调到；
     * 而且只有"确实有东西可清"时才提示，避免长按左键时每 tick 刷一行行动栏。
     */
    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity, InteractionHand hand) {
        if (entity instanceof Player player && !player.level().isClientSide) {
            List<WandSelection.Box> boxes = WandSelection.read(stack);
            if (!boxes.isEmpty() || readCorner(stack) != null) {
                WandSelection.clear(stack);
                clearCorner(stack);
                actionBar(player, Component.translatable(MSG + "cleared", boxes.size()).withStyle(ChatFormatting.GRAY));
            }
        }
        return true;
    }

    /**
     * 左键不要挖方块。
     *
     * <p>1.21.1 的两端都会看这个返回值，确认过（{@code _refs/nf_src} 反编译源）：
     * <ul>
     *   <li>客户端 {@code MultiPlayerGameMode#destroyBlock}:113 问 {@code getMainHandItem().getItem().canAttackBlock(...)}，
     *       返回 false 就不做本地破坏预测；</li>
     *   <li>服务端 {@code CommonHooks#fireBlockBreak}:569 用同一个判断**预先取消** {@code BlockEvent.BreakEvent}，
     *       于是 {@code ServerPlayerGameMode#destroyBlock}:249-251 直接 return false——方块真的不会被挖掉
     *       （创造模式也不会），并回发方块更新纠正客户端。</li>
     * </ul>
     * 挥手本身不受影响（{@code Minecraft#startAttack}:1693-1694 / {@code #continueAttack}:1639-1642 无条件
     * {@code player.swing(...)}），所以 {@link #onEntitySwing} 的"左键清空"照常生效。
     */
    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("vs_clockwork.wanderwand.warning").withStyle(ChatFormatting.RED));
        tooltip.add(status(stack).copy().withStyle(ChatFormatting.GRAY));
        // 按当前模式给对应的说明（旧代码不分模式永远显示"选择"的说明，玩家在取消选择模式下会被误导）
        tooltip.add(Component.translatable(getMode(stack) == Mode.SELECT
                        ? "vs_clockwork.wanderwand.tool.select.description.1"
                        : "vs_clockwork.wanderwand.tool.deselect.description.1")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(MSG + "hint").withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    /**
     * "当前模式 + 已选区域/方块数"的一行状态，行动栏提示与物品悬浮提示共用。
     *
     * <p>挂了半截角点时再追加一段坐标：这是"只选了一个角点"唯一的持久提示
     * （世界里的角点标记会被方块挡住，行动栏文案一闪而过）。服务端没加额外包，
     * 悬浮提示本来就在客户端读物品组件，这里是同一份数据的第 4 个 {@code %s}。
     */
    private static MutableComponent status(ItemStack stack) {
        List<WandSelection.Box> boxes = WandSelection.read(stack);
        MutableComponent status = Component.translatable(MSG + "status", modeName(getMode(stack)), boxes.size(),
                WandSelection.totalVolume(boxes), cornerName(stack));
        return status;
    }

    /** 角点坐标文案；没有挂角点时给空串（会覆盖掉 "待定角点: %s" 里的占位符）。 */
    private static MutableComponent cornerName(ItemStack stack) {
        BlockPos corner = readCorner(stack);
        return corner == null
                ? Component.empty()
                : Component.translatable(MSG + "corner_pending", format(corner));
    }

    /** 按键提示（短，跟在状态后面）。 */
    private static MutableComponent hint() {
        return Component.literal("  ").append(Component.translatable(MSG + "hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static MutableComponent modeName(Mode mode) {
        return Component.translatable(mode == Mode.SELECT
                ? "vs_clockwork.wanderwand.tool.select"
                : "vs_clockwork.wanderwand.tool.deselect");
    }

    /** 行动栏提示。{@code displayClientMessage} 只在服务端会真的发包，本类所有调用点都在服务端分支。 */
    private static void actionBar(Player player, Component text) {
        player.displayClientMessage(text, true);
    }

    private static void playClick(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.PLAYERS, 0.6f, 1.4f);
    }

    private static String format(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static BlockPos readCorner(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag tag = data.copyTag();
        return tag.contains(KEY_CORNER) ? BlockPos.of(tag.getLong(KEY_CORNER)) : null;
    }

    /**
     * 只读查询：这把法杖当前是否挂着一个"半截角点"（选了 A、还没选 B）。
     *
     * <p>给客户端选区可视化用（{@code client/WanderwandSelectionRenderer}）——它不是新状态，
     * 就是 {@link #readCorner} 的公开入口：客户端渲染需要知道"要不要画角点标记 + 预览框"，
     * 而键名的读写仍然只在本类里发生，渲染器不碰 NBT。
     *
     * <p>为什么不用加个 {@code isCornerSet} 之类的布尔方法：渲染器还需要这个坐标本身来画标记
     * （渲染器自己再读一次 {@code getLong} 就会出现两处键名），所以直接返回坐标。
     * 同理，渲染器<b>不应该每帧调它</b>（每次都会 {@code copyTag}），要缓存，见该渲染器的 Cache。
     */
    public static BlockPos getPendingCorner(ItemStack stack) {
        return readCorner(stack);
    }

    private static void writeCorner(ItemStack stack, BlockPos pos) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putLong(KEY_CORNER, pos.asLong()));
    }

    private static void clearCorner(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(KEY_CORNER));
    }
}
