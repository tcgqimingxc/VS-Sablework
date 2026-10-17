package org.valkyrienskies.clockwork.content.curiosities.tools.gravitron;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.clockwork.ClockworkMod;
import org.valkyrienskies.clockwork.config.CWServer;
import org.valkyrienskies.clockwork.config.ClockworkConfig;
import org.valkyrienskies.clockwork.content.contraptions.phys.infuser.IAreaSelector;
import org.valkyrienskies.clockwork.platform.CWAssembly;
import org.valkyrienskies.clockwork.platform.CWForces;
import org.valkyrienskies.clockwork.platform.CWSubLevelLocks;
import org.valkyrienskies.clockwork.platform.CWSubLevels;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 重力枪（Gravitron）。
 *
 * <p>对应旧版 {@code tools/gravitron/GravitronItem.kt} + {@code GravitronHandler.kt}：
 * <ul>
 *   <li><b>抓取</b>（旧版 GRAB 模式）：右键"抓住"视线指向的结构，之后结构会被拽到玩家视线前方；再右键放开。</li>
 *   <li><b>装配 / 解体</b>（旧版 ASSEMBLE 与 DESTROY 模式）：潜行右键 —— 指向<b>游离方块</b>时，
 *       把游荡法杖选区里包含该方块的那一个连通块装配成子空间；指向<b>子空间里的方块</b>时，
 *       把该子空间原地放回世界。与物理灌注器走同一条链路（{@link CWAssembly}）。</li>
 *   <li><b>锁定 / 解锁</b>（新增 LOCK 模式，轮盘第 5 项）：把目标结构焊在世界坐标系上，
 *       再点一次解锁。旧版没有这个功能，语义与实现照抄 Aeronautics 物理手杖的 LOCK
 *       （见 {@link #toggleLock} 与 {@link CWSubLevelLocks}）。</li>
 * </ul>
 *
 * <p>移植差异（VS2 → Sable），这些差异都是**必须**的，因为 Sable 没有 VS2 的 Ship 概念：
 * <ul>
 *   <li>旧版通过 {@code ShipObjectWorld} 拿 Ship 并改 {@code ship.velocity / transform}；
 *       这里用 {@code Sable.HELPER.getContaining} 找到方块所属的 {@link SubLevel}，
 *       再用 {@link RigidBodyHandle} 施加冲量（Sable 的物理入口）。</li>
 *   <li>旧版 4 种工具模式靠轮盘切换（{@code ToolType} + {@code GravitronDialPacket}）；
 *       本移植保留同一套模式与同一个按键（按住 Create 的 {@code AllKeys.TOOL_MENU}＝左 ALT + 滚轮，
 *       见 {@link GravitronTools} 与 {@code client/GravitronToolMenu}），并补回表盘指针的质量同步
 *       （{@link GravitronDialPacket}）。默认模式 GRAB 下右键仍是"抓取 / 放开"，潜行右键仍是
 *       "按目标自动判定装配 / 解体"，所以不用轮盘也能像以前一样玩。</li>
 *   <li>旧版要玩家背包里有游荡法杖，这里通过 {@link IAreaSelector} 契约读选区（法杖已实现该接口），
 *       因此任何实现该接口的物品都能当选择器用。</li>
 *   <li>被抓住的结构用物品数据组件记录其 UUID（旧版存在玩家/客户端状态里，重登即丢）。</li>
 *   <li><b>牵引在物理子步里施加</b>（见 {@link #physicsSubstep}）：Sable 一个游戏刻跑多个物理子步，
 *       只在游戏刻施力会让反馈滞后一整刻、手感"慢半拍"；每子步用当前位姿与速度重算才跟手。</li>
 *   <li>牵引是"速度伺服 + 质量缩放"的弹簧（见 {@link #applyHold}），不是旧版直接改位姿的硬跟随：
 *       旧版 {@code GravitronController} 的 PD 增益（kp=160 / kd=20）是按 VS2 的物理子步调的，
 *       而 {@code RigidBodyHandle} 的速度是 SI（m/s），必须按 {@code 力 = 质量 × 加速度}、
 *       {@code 冲量 = 力 × dt} 换算，否则会差出 20 倍（一游戏刻 = 1/20 s）。</li>
 * </ul>
 *
 * <p>所有手感数值都在 {@code config/CWServer.java} 的 {@code gravitron} 分组里，改完直接热重载。
 */
public class GravitronItem extends Item {

    private static final String KEY_GRABBED = "GrabbedSubLevel";

    /**
     * 正在被抓取的结构：玩家 UUID → (子空间, 上次"仍在抓"的游戏刻)。
     *
     * <p>为什么要这张表：牵引必须每物理子步做一次（40~200 Hz），而物品的
     * {@code inventoryTick} 只有 20 Hz，靠它驱动会漏掉子步。表里只存"当前有效的抓取"，
     * 过期条目由 {@link #pruneStale} 在每游戏刻清理（玩家松手 / 换物品 / 下线都会自动过期）。
     */
    private static final Map<UUID, Hold> HOLDS = new ConcurrentHashMap<>();
    /** 抓取超过这么多刻没有续期就丢弃 [游戏刻]（约 0.5 秒，足够容忍 tick 顺序差一帧）。 */
    private static final long HOLD_TIMEOUT_TICKS = 10L;
    /**
     * 锁定 / 解锁音效的播放参数。
     *
     * <p>单位：{@code volume} 是线性增益（1.0 = 采样原音量，0.5 约 −6 dB）；
     * {@code pitch} 是播放速率倍率（1.0 = 采样原音高，2.0 高一个八度，0.5 低一个八度）。
     *
     * <p><b>数值来源：照抄旧版 1.20.1，未做任何移调。</b>旧版与"锁定 / 解锁"唯一对应的动作是
     * 超充重力枪左键把 {@code ship.isStatic} 取反（{@code GravitronLeftClickPacket.kt:81}），
     * 而 {@code GravitronLeftClickPacket.kt:82-89} 对<b>取反的两个方向</b>播的是<b>同一条</b>事件
     * {@code ClockworkSounds.GRAVITRON_FREEZE}（采样 {@code gravitron_freeze.ogg}，定义见
     * {@code ClockworkSounds.kt:186-189}），参数写死 volume {@code 1f}（{@code :87}）、
     * pitch {@code 1f}（{@code :88}）。旧版既没有 pitch 变体也没有随机范围：
     * {@code ClockworkSounds.kt:290} 的 {@code playExisting(event, volume = 1f, pitch = 1f)}
     * 默认值就是 1f/1f，运行时只是把它乘上调用方传入的 pitch（{@code ClockworkSounds.kt:427}）。
     * 同一份参数也用在旧版另外几个重力枪音上（{@code GrabTool.kt:187-194 / 243-250} 的
     * RELEASE / GRAB 与 {@code GravitronLeftClickPacket.kt:67-74} 的 LAUNCH 全是 1f / 1f）。
     *
     * <p>因此 LOCK 与 UNLOCK <b>共用这一对常量</b>：本移植保留"两条不同 ogg"
     * （LOCK = {@code gravitron_freeze} 的亮"咔"，UNLOCK = {@code gravitron_fling} 的闷"砰"），
     * 区分度只来自采样本身。这里曾用过 1.1 / 0.9 人为拉开约 3.5 个半音，
     * 实机反馈"锁定/解锁的音调偏高"，已按旧版参数还原（见 {@code docs/GRAVITRON-SOUNDS.md}）。
     */
    private static final float LOCK_SOUND_VOLUME = 1.0f; // 旧版 GravitronLeftClickPacket.kt:87
    private static final float LOCK_SOUND_PITCH = 1.0f;  // 旧版 GravitronLeftClickPacket.kt:88
    /** 勾子/清理是否已注册。 */
    private static boolean hooksRegistered;

    /** 一条活跃抓取（记下是哪一把枪抓的：子类 {@link CreativeGravitronItem} 有额外行为）。 */
    private record Hold(GravitronItem tool, ServerSubLevel subLevel, Player player, long lastTouch) {
    }

    public GravitronItem(Properties properties) {
        super(properties);
        registerHooks();
    }

    /** 超充重力枪（创造模式道具）可以额外"瞬移对齐"结构。 */
    protected boolean isSupercharged() {
        return false;
    }

    // ==================== 物理子步牵引 ====================

    /**
     * 注册牵引回调。构造器里调用（注册物品时就会构造），因此只会在 FML 加载期的初始化线程上跑一次。
     *
     * <p>{@code onPhysicsTick} 是"每个物理子步一次"的回调（Sable 的 javadoc 明说一游戏刻会跑多次）；
     * {@code NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, ...)} 负责清理过期抓取。
     */
    private static synchronized void registerHooks() {
        if (hooksRegistered) {
            return;
        }
        hooksRegistered = true;
        SableEventPlatform.INSTANCE.onPhysicsTick(GravitronItem::physicsSubstep);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> pruneStale(event.getServer()));
        // 拿起 / 收起重力枪的音效（旧版 GravitronHandler.tick 的 GRAVITRON_START / SHUTDOWN）：
        // 用"主手物品变化"事件判定，无状态、不需要额外清理
        NeoForge.EVENT_BUS.addListener(LivingEquipmentChangeEvent.class, GravitronItem::onEquipmentChange);
        // LOCK 模式的锁需要在子空间重新载入时重建约束（对标物理手杖的 SubLevelObserver）
        CWSubLevelLocks.registerObserver();
    }

    /** 主手从别的物品换成重力枪（或反过来）时播一次启动 / 关闭音。 */
    private static void onEquipmentChange(final LivingEquipmentChangeEvent event) {
        if (event.getSlot() != EquipmentSlot.MAINHAND || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        final boolean was = event.getFrom().getItem() instanceof GravitronItem;
        final boolean now = event.getTo().getItem() instanceof GravitronItem;
        if (was == now) {
            return;
        }
        player.level().playSound(null, player.blockPosition(), now ? GravitronSounds.START : GravitronSounds.SHUTDOWN,
                SoundSource.PLAYERS, now ? 0.3f : 0.5f, 1.0f);
    }

    /** 一个物理子步：对每个还在被抓的结构刷一次牵引。 */
    private static void physicsSubstep(final SubLevelPhysicsSystem physicsSystem, final double timeStep) {
        if (HOLDS.isEmpty()) {
            return;
        }
        for (final Hold hold : HOLDS.values()) {
            final ServerSubLevel subLevel = hold.subLevel();
            if (subLevel.isRemoved() || subLevel.getLevel() != physicsSystem.getLevel()) {
                continue;                              // 没了/在别的维度：等 pruneStale 清
            }
            try {
                hold.tool().applyHold(subLevel, hold.player(), timeStep);
            } catch (final RuntimeException e) {
                // 单个结构出错不能带崩整个物理刻（Sable 会把异常包成 ReportedException 直接崩游戏）
                ClockworkMod.LOGGER.warn("Gravitron hold failed for sub-level {}", subLevel.getUniqueId(), e);
                HOLDS.remove(hold.player().getUUID());
            }
        }
    }

    /** 每游戏刻清理过期抓取（玩家松手、换物品、下线、结构消失）。 */
    private static void pruneStale(final MinecraftServer server) {
        if (HOLDS.isEmpty()) {
            return;
        }
        final long now = server.overworld().getGameTime();
        HOLDS.values().removeIf(hold -> now - hold.lastTouch() > HOLD_TIMEOUT_TICKS
                || hold.subLevel().isRemoved());
    }

    /** 续期：物品每游戏刻在被选中且确实抓着东西时调用。 */
    private static void touchHold(final GravitronItem tool, final Player player, final ServerSubLevel subLevel) {
        HOLDS.put(player.getUUID(), new Hold(tool, subLevel, player, subLevel.getLevel().getGameTime()));
    }

    /** 松开：立刻停止牵引（不等超时）。 */
    private static void dropHold(final Player player) {
        HOLDS.remove(player.getUUID());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }

        UUID grabbed = readGrabbed(stack);
        if (grabbed != null) {
            // 已经抓着东西时右键永远是"放开"，与所选模式无关（旧版 GrabTool 的 dropShip 分支）
            release(serverLevel, player, stack);
            return InteractionResultHolder.success(stack);
        }
        if (player.isShiftKeyDown()) {
            // 潜行右键保持引入轮盘之前的行为：按目标自动判定装配 / 解体，不受模式影响
            assembleOrDisassemble(serverLevel, player);
            return InteractionResultHolder.success(stack);
        }
        // 轮盘选中的模式决定普通右键干什么（默认 GRAB，与旧版普通重力枪一致）
        switch (GravitronTools.get(player)) {
            case ASSEMBLE -> assembleAt(serverLevel, player);
            case DESTROY -> disassembleAt(serverLevel, player);
            case GRAB_AND_ASSEMBLE -> grabAndAssemble(serverLevel, player, stack);
            case LOCK -> toggleLock(serverLevel, player);
            case GRAB -> grab(serverLevel, player, stack);
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (!selected || !(level instanceof ServerLevel serverLevel) || !(entity instanceof Player player)) {
            return;
        }
        UUID grabbed = readGrabbed(stack);
        if (grabbed == null) {
            dropHold(player);
            return;
        }
        SubLevel subLevel = SubLevelContainer.getContainer(serverLevel).getSubLevel(grabbed);
        if (!(subLevel instanceof ServerSubLevel serverSubLevel) || serverSubLevel.isRemoved()) {
            dropHold(player);
            clearGrabbed(stack);
            return;
        }
        // 只续期，真正的牵引在物理子步里做（见 physicsSubstep）
        touchHold(this, player, serverSubLevel);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("vs_clockwork.gravitron.tool.grab")
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("[RMB]").withStyle(ChatFormatting.AQUA)));
        tooltip.add(Component.translatable("vs_clockwork.gravitron.tool.assemble")
                .append(Component.literal(" / ").withStyle(ChatFormatting.GRAY))
                .append(Component.translatable("vs_clockwork.gravitron.tool.destroy"))
                .append(Component.literal(": [Sneak+RMB]").withStyle(ChatFormatting.AQUA)));
        // 轮盘的提示：键位名从语言文件里写死（不引用 Create 的 AllKeys，避免通用类依赖客户端按键类）
        tooltip.add(Component.translatable("vs_clockwork.gravitron.tool.menu_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    // ==================== 抓取 / 放开 ====================

    /** 右键抓取：先取视线目标，再交给 {@link #grab(ServerLevel, Player, ItemStack, SubLevel)}。 */
    private void grab(ServerLevel level, Player player, ItemStack stack) {
        BlockPos clicked = aimedBlock(player);
        if (clicked == null) {
            return;
        }
        SubLevel subLevel = Sable.HELPER.getContaining(level, clicked);
        if (subLevel == null) {
            player.displayClientMessage(Component.literal("Gravitron: target is not part of a sub-level"), true);
            return;
        }
        grab(level, player, stack, subLevel);
    }

    /**
     * 抓住一个已经确定的目标结构。
     *
     * <p>拆成两个重载是因为"组装并抓取"模式刚装配出来的结构手里就有引用，
     * 不必（也不可能）再从视线重新找一次；质量上限、音效、表盘同步都在这里做一次。
     */
    private void grab(ServerLevel level, Player player, ItemStack stack, SubLevel subLevel) {
        final double mass = subLevel instanceof ServerSubLevel serverSubLevel
                ? serverSubLevel.getMassTracker().getMass() : 0.0;
        if (mass > ClockworkConfig.server().gravitronMaxGrabMass.get()) {
            player.displayClientMessage(Component.literal("Gravitron: structure too heavy"), true);
            return;
        }
        // 抓取会立刻解锁：与物理手杖一致（{@code PhysicsStaffServerHandler#drag} 一进来就 removeLock）。
        // 锁着的结构是"世界 ↔ 子空间"的刚性约束，速度伺服顶不动它，所以抓住之前必须先解锁。
        CWSubLevelLocks.unlock(level, subLevel);
        writeGrabbed(stack, subLevel.getUniqueId());
        level.playSound(null, player.blockPosition(), GravitronSounds.GRAB, SoundSource.PLAYERS, 1.0f, 1.0f);
        // 表盘指针指示"抓住的结构有多重"（旧版 GrabTool 抓取成功后发 GravitronDialPacket 的角度）
        syncDial(player, GravitronDialState.angleForMass(mass, ClockworkConfig.server().gravitronMaxGrabMass.get()));
        player.displayClientMessage(Component.literal("Gravitron: grabbed structure"), true);
    }

    private void release(ServerLevel level, Player player, ItemStack stack) {
        dropHold(player);
        clearGrabbed(stack);
        level.playSound(null, player.blockPosition(), GravitronSounds.RELEASE, SoundSource.PLAYERS, 1.0f, 1.0f);
        syncDial(player, 0f);                          // 指针回到空载位置
        player.displayClientMessage(Component.literal("Gravitron: released"), true);
    }

    /** 把表盘角度发给玩家（旧版同样由服务端发 {@code GravitronDialPacket}）。 */
    private static void syncDial(Player player, float angle) {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new GravitronDialPacket(angle));
        }
    }

    /**
     * 一个物理子步的牵引：把被抓结构往"视线前方 holdDistance 格"拽。
     *
     * <p><b>单位换算（这里以前差 20 倍，是"结构移动缓慢"的主因）</b>：
     * {@code RigidBodyHandle} 的速度是 SI 的 <b>m/s</b>（{@code RigidBodyHandle.java:140} 的 javadoc、
     * 原生层 {@code rapier/src/lib.rs:1414} 直接返回 {@code rb.linvel()}），
     * 而冲量接口收的是 <b>N·s</b>。所以每次调用的冲量必须是：
     * <pre>
     *   牵引   pull = clamp((target − x) × positionGain, maxSpeed)   [m/s]   ← 期望速度
     *   加速   Δv₁  = clamp(pull − v, maxAcceleration × dt)          [m/s]   ← 本子步能补的加速
     *   阻尼   Δv₂  = −v × velocityDamping × dt                      [m/s]   ← 永远反向
     *   冲量   J    = m × (Δv₁ + Δv₂)                                 [N·s]
     * </pre>
     * 也就是说，{@code positionGain} / {@code velocityDamping} 是 <b>每秒</b> 的增益，
     * 乘 {@code dt} 之后才与子步数无关：Sable 把一游戏刻拆成几个子步，每个子步补一小份，
     * 一游戏刻累积起来正好是"每刻一份"，改 {@code substepsPerTick} 也不会变手感。
     * 旧写法每子步都补一整份，速度阻尼因此被放大 20~200 倍，结构一被拉起来就被自身阻尼摁住 ——
     * 手感就是"力气不够、慢慢挪"。
     *
     * <p><b>坐标系铁律</b>（Sable 2.0.5，务必照抄，搞错会让结构当场被删除）：
     * {@link RigidBodyHandle} 的力/力矩接口收的都是 <b>plot 内部局部坐标系</b>的向量 ——
     * 证据：{@code RapierPhysicsPipeline#applyImpulse} 会做 {@code position - massTracker.getCenterOfMass()}，
     * 而质心是 plot 局部坐标（{@code MassTracker#build} 遍历 {@code plot.getBoundingBox()}）；
     * 原生层 {@code rapier/src/lib.rs:1336-1346} 再把力和作用点各乘一次刚体旋转。
     * 所以：世界系算出来的速度增量必须先用 {@code pose.orientation().transformInverse(...)} 转回局部系，
     * 这与 Sable 自己的 {@code BlockEntitySubLevelPropellerActor}（传 {@code atCenterOf(blockPos)}）、
     * Simulated 的 {@code SpringBlockEntity:277-282} / {@code MagnetPair:185-190}、
     * 以及本项目 {@code GyroBlockEntity:92-97} 的写法完全一致。
     *
     * <p>旧写法把参数写反了（{@code applyImpulseAtPoint(impulse, pose.position())}）：
     * 第一个参数是作用点、第二个才是冲量。结果是给结构每 tick 施加了一个大小等于"世界坐标向量"
     * （y 分量 = 结构的世界高度！）的冲量，作用点却几乎落在 plot 原点 —— 力臂 ≈ 500 万格，
     * 角冲量天文数字。实机日志证据：{@code latest.log:1146/1147}
     * 「Sub-level ServerSubLevel[...] has an extreme Y coordinate range, removing」，
     * 出自 {@code ServerSubLevel.java:228-232}（Sable 的极端坐标保护会 {@code markRemoved()} 掉整个子空间），
     * 于是玩家看到的现象就是"抓一下结构整个消失了"。
     *
     * @param timeStep 本子步步长 [s]，即 {@code 1 / 20 / substepsPerTick}
     */
    private void applyHold(final ServerSubLevel subLevel, final Player player, final double timeStep) {
        if (!(player.level() instanceof ServerLevel level) || player.isRemoved() || level != subLevel.getLevel()) {
            return;
        }
        final CWServer config = ClockworkConfig.server();
        final RigidBodyHandle handle = RigidBodyHandle.of(subLevel);
        // RigidBodyHandle.of 声明可空（RigidBodyHandle.java:53 @Nullable），必须先判空再 isValid。
        if (handle == null || !handle.isValid()) {
            return;
        }
        // 冲量按质量缩放（旧版 GravitronController 也是 force = posDif * mass），
        // 这样手感与结构大小无关。质量为 0 时惯量张量不可逆，继续算只会得到 NaN。
        final MassData mass = subLevel.getMassTracker();
        final double bodyMass = mass.getMass();
        if (!(bodyMass > 0.0) || !Double.isFinite(bodyMass) || bodyMass > config.gravitronMaxGrabMass.get()) {
            return;
        }
        // 刚体位姿/速度都取"本子步"的值：Sable 每个子步后都会 updatePose（SubLevelPhysicsSystem.java:286），
        // 所以 logicalPose() 已经是当前子步的位姿，不需要再插值。
        final Pose3dc pose = subLevel.logicalPose();
        final Vector3dc position = pose.position();

        // ① 角速度阻尼：角冲量 = -I_local · ω_local · angularDamping · dt。
        //    getAngularVelocity 返回的是世界系向量，而惯量张量与冲量接口都在局部系，必须先转（同 GyroBlockEntity）。
        final double angularDamping = config.gravitronAngularDamping.get();
        if (angularDamping > 0.0) {
            final Vector3d localAngularVelocity = handle.getAngularVelocity(new Vector3d());
            if (localAngularVelocity.isFinite()) {
                pose.orientation().transformInverse(localAngularVelocity);
                final Vector3d angularImpulse = mass.getInertiaTensor()
                        .transform(localAngularVelocity, new Vector3d()).mul(-angularDamping * timeStep);
                if (angularImpulse.isFinite() && angularImpulse.lengthSquared() > 0.0) {
                    handle.applyAngularImpulse(angularImpulse);
                }
            }
        }

        final Vec3 look = player.getLookAngle();
        final Vec3 eye = player.getEyePosition();
        final double holdDistance = config.gravitronHoldDistance.get();
        final Vector3d target = new Vector3d(eye.x + look.x * holdDistance,
                eye.y + look.y * holdDistance,
                eye.z + look.z * holdDistance);

        if (isSupercharged() && player.isShiftKeyDown()) {
            // 超充模式：直接瞬移对齐（旧版 CreativeGravitronItem 的"冻结/对齐"手感）。
            // teleport 收的是世界坐标（刚体位姿 position 就是质心的世界位置，见
            // SubLevelAssemblyHelper#assembleBlocks:114-123），与上一条局部系规则不冲突。
            handle.teleport(target, pose.orientation());
            return;
        }

        // ② 位置弹簧：世界系分两项算，理由是"力气"和"刹住"要分别限幅：
        //      牵引项 v* = delta × positionGain              （只把结构拉向目标，封顶 maxSpeed）
        //      阻尼项     = −v × velocityDamping              （永远反向，用来自动刹车）
        //    再把"本子步该补的速度增量"乘 dt，转局部系、乘质量变成冲量，作用在质心上
        //    （applyLinearImpulse 不产生力矩，Sable 会把它当成质心处的推力）。
        //    注：不减去玩家自身速度 —— 服务端玩家是位置同步（absMoveTo）而非速度同步，
        //    服务端读到的 getDeltaMovement / 位置差在走路时都接近 0，加了也是假的，干脆不加。
        final Vector3d velocity = handle.getLinearVelocity(new Vector3d());
        if (!velocity.isFinite()) {
            return;
        }
        // 牵引：期望速度 = 目标 − 当前，一阶跟随；误差越大越快，但不超过 maxSpeed
        final Vector3d pull = target.sub(position, new Vector3d()).mul(config.gravitronPositionGain.get());
        CWForces.clampInPlace(pull, config.gravitronMaxSpeed.get());
        // 加速能力（力气）：与质量无关的加速度上限，是"结构移动快不快"的主要旋钮，
        // 用 CWForces.clampInPlace 逐轴裁剪，避免结构离得远时被一子步拽成炮弹
        CWForces.clampInPlace(pull, config.gravitronMaxAcceleration.get() * timeStep);
        // 阻尼：v 已经到位的部分不再补偿，剩下的差值里只补最大值的一小份（防止过冲振荡）
        final Vector3d deltaToPull = pull.sub(velocity, new Vector3d());
        final double maxDeltaPerStep = config.gravitronMaxAcceleration.get() * timeStep;
        if (deltaToPull.length() > maxDeltaPerStep) {
            deltaToPull.normalize(maxDeltaPerStep);
        }
        final Vector3d dampingDelta = velocity.mul(-config.gravitronVelocityDamping.get() * timeStep);
        // 力上限只管"往外推"：质量很大的结构即使加速度不大，冲量也可能大到让求解器发散；
        // 阻尼部分不受限，否则结构一旦被别的力加速就再也刹不住。
        final double maxPullImpulse = config.gravitronMaxHoldForce.get() * timeStep;
        if (deltaToPull.length() * bodyMass > maxPullImpulse) {
            deltaToPull.normalize(maxPullImpulse / bodyMass);
        }
        final Vector3d impulse = deltaToPull.add(dampingDelta).mul(bodyMass);
        pose.orientation().transformInverse(impulse);
        if (impulse.isFinite()) {
            handle.applyLinearImpulse(impulse);
        }
    }

    // ==================== 装配 / 解体 ====================

    /**
     * 潜行右键的两个动作，按瞄准目标自动判定（旧版靠工具轮盘在 ASSEMBLE / DESTROY 之间切换；
     * 本移植保留这条"不用轮盘也能用"的快捷路径，轮盘则用来显式指定模式）。
     *
     * <p>判定依据：方块是否已经在某个子空间里 —— 已经在 → 解体；还在地上 → 装配。
     * 这条判据是自洽的：装配只对"世界里的游离方块"有意义，解体只对"子空间里的方块"有意义。
     */
    private void assembleOrDisassemble(ServerLevel level, Player player) {
        final BlockPos clicked = aimedBlock(player);
        if (clicked == null) {
            return;
        }
        if (CWSubLevels.containingServer(level, clicked) != null) {
            disassemble(level, player, clicked);
        } else {
            assemble(level, player, clicked);
        }
    }

    /** 轮盘选中 ASSEMBLE：右键只做装配。 */
    private void assembleAt(ServerLevel level, Player player) {
        final BlockPos clicked = aimedBlock(player);
        if (clicked != null) {
            assemble(level, player, clicked);
        }
    }

    /** 轮盘选中 DESTROY：右键只做解体（旧版 DisassembleTool）。 */
    private void disassembleAt(ServerLevel level, Player player) {
        final BlockPos clicked = aimedBlock(player);
        if (clicked != null) {
            disassemble(level, player, clicked);
        }
    }

    /**
     * 轮盘选中 LOCK：把视线指向的结构锁在世界坐标系上，再点一次解锁。
     *
     * <p>锁定语义完全照抄 Aeronautics 的<b>物理手杖</b>（{@code physics_staff} 的
     * {@code PhysicsStaffAction.LOCK}）：锁 = 在"世界（bodyA = null）"与目标子空间之间插一个
     * {@link dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration} 固定约束，
     * 锚点/朝向取该子空间<b>当前</b>的 {@code logicalPose()}；解锁 = 把约束移除。
     * 实现与证据链见 {@link CWSubLevelLocks#toggle}。
     *
     * <p>锁定后结构就"钉"在那里：重力、推力、机翼升力都会被约束抵消，直到被解锁、
     * 被解体，或者（与物理手杖一致）被重新抓取 —— 抓取会自动解锁。
     *
     * <p>反馈：行动栏文字 + 音效。音效用<b>锁定 / 解锁各自的专用事件</b>
     * {@link GravitronSounds#LOCK} / {@link GravitronSounds#UNLOCK}，不复用抓取 / 放开：
     * 抓取是"拽着结构走"（长音），锁定是"约束一瞬间合上"，实机反馈里"锁定/解锁听起来像抓取/放开"
     * 就是这么来的。两条事件的采样是<b>本模组自带</b>的两个旧版重力枪音
     * （{@code vs_clockwork:gravitron_freeze} 用于 LOCK、{@code vs_clockwork:gravitron_fling}
     * 用于 UNLOCK，见 {@code sounds.json} 与 {@code docs/GRAVITRON-SOUNDS.md}）——
     * 不引用其它模组的采样，免得上游换版本时静默失效。播放参数用
     * {@link #LOCK_SOUND_VOLUME} / {@link #LOCK_SOUND_PITCH}（= 旧版值 {@code 1f} / {@code 1f}）：
     * <b>两个方向都不移调</b>，区分靠采样本身的音色（freeze 亮"咔"、fling 闷"砰"），
     * 而不是靠 pitch —— 与旧版 {@code GravitronLeftClickPacket.kt:82-89} 对 {@code isStatic}
     * 两个方向播同一条音、同一组参数的做法一致。
     */
    private void toggleLock(ServerLevel level, Player player) {
        final BlockPos clicked = aimedBlock(player);
        if (clicked == null) {
            return;
        }
        final ServerSubLevel subLevel = CWSubLevels.containingServer(level, clicked);
        if (subLevel == null) {
            player.displayClientMessage(Component.literal("Gravitron: target is not part of a sub-level"), true);
            return;
        }
        final boolean locked = CWSubLevelLocks.toggle(level, subLevel);
        level.playSound(null, clicked, locked ? GravitronSounds.LOCK : GravitronSounds.UNLOCK,
                SoundSource.PLAYERS, LOCK_SOUND_VOLUME, LOCK_SOUND_PITCH);
        player.displayClientMessage(Component.translatable(locked
                ? "vs_clockwork.gravitron.lock.locked"
                : "vs_clockwork.gravitron.lock.unlocked"), true);
    }

    /** 轮盘选中"组装并抓取"（旧版 GrabssembleTool）：先按选区并块，再抓住刚生成的结构。 */
    private void grabAndAssemble(ServerLevel level, Player player, ItemStack stack) {
        final BlockPos clicked = aimedBlock(player);
        if (clicked == null) {
            return;
        }
        ServerSubLevel target = CWSubLevels.containingServer(level, clicked);
        if (target == null) {
            target = assemble(level, player, clicked);
        }
        if (target != null) {
            grab(level, player, stack, target);
        }
    }

    /** 视线指向的方块；没命中就提示并返回 null（等价旧版 {@code GravitronToolBase.updateTargetPos}）。 */
    private static @Nullable BlockPos aimedBlock(Player player) {
        HitResult hit = player.pick(ClockworkConfig.server().gravitronReach.get(), 0.0f, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() == HitResult.Type.MISS) {
            player.displayClientMessage(Component.literal("Gravitron: nothing in range"), true);
            return null;
        }
        return blockHit.getBlockPos();
    }

    /** 解体：把 {@code clicked} 所在的子空间原地放回世界（旧版 GravitronDisassemblyPacket → ShipDestroyer）。 */
    private void disassemble(ServerLevel level, Player player, BlockPos clicked) {
        final ServerSubLevel contained = CWSubLevels.containingServer(level, clicked);
        if (contained == null) {
            player.displayClientMessage(Component.literal("Gravitron: nothing assembled there"), true);
            return;
        }
        if (CWAssembly.disassembleInPlace(level, contained)) {
            level.playSound(null, clicked, GravitronSounds.SHUTDOWN, SoundSource.PLAYERS, 1.0f, 1.0f);
            player.displayClientMessage(Component.translatable("vs_clockwork.gravitron.tool.destroy"), true);
        } else {
            player.displayClientMessage(Component.literal("Gravitron: empty structure"), true);
        }
    }

    /**
     * 装配：取游荡法杖选区里"包含 {@code clicked}"的那一个连通块，并成子空间
     * （旧版 {@code CreativeGravitronItem#grabssemble} + {@code WanderwandItem.findIsolatedComponents}）。
     *
     * @return 新装配出来的子空间；失败返回 null（调用方据此决定要不要继续抓取）
     */
    private @Nullable ServerSubLevel assemble(ServerLevel level, Player player, BlockPos clicked) {
        final Collection<BlockPos> selection = wandSelection(player, level);
        if (selection == null || !selection.contains(clicked)) {
            player.displayClientMessage(
                    Component.literal("Gravitron: select the area with a Wanderwand first"), true);
            return null;
        }
        final int maxBlocks = ClockworkConfig.server().infuserMaxBlocks.get();
        final Set<BlockPos> component = connectedComponent(selection, clicked, maxBlocks);
        if (component == null) {
            player.displayClientMessage(
                    Component.literal("Gravitron: selection group is larger than " + maxBlocks + " blocks"), true);
            return null;
        }
        final ServerSubLevel assembled = CWAssembly.assembleBlockSet(level, clicked, component);
        if (assembled == null) {
            player.displayClientMessage(Component.literal("Gravitron: assembly failed"), true);
            return null;
        }
        level.playSound(null, clicked, GravitronSounds.START, SoundSource.PLAYERS, 1.0f, 0.7f);
        player.displayClientMessage(Component.literal("Gravitron: assembled " + component.size() + " blocks"), true);
        return assembled;
    }

    /** 玩家背包（含快捷栏）里第一个"有选区"的选择器物品给出的方块集合。 */
    private static @Nullable Collection<BlockPos> wandSelection(Player player, Level level) {
        for (final ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof final IAreaSelector selector) {
                final Collection<BlockPos> blocks = selector.cw$getSelectedBlocks(stack, level);
                if (blocks != null && !blocks.isEmpty()) {
                    return blocks;
                }
            }
        }
        return null;
    }

    /**
     * 从 {@code origin} 出发在选区内做 26 邻接 BFS，返回包含 {@code origin} 的那个连通块。
     *
     * <p>与灌注器的 {@code splitIntoComponents} 同一判据：Sable 的 plot 必须是一块连通的方块集合，
     * 否则质心与碰撞体都会算错。
     *
     * @return 连通块；超过 {@code maxBlocks} 时返回 null（拒绝装配，避免卡死）
     */
    private static @Nullable Set<BlockPos> connectedComponent(Collection<BlockPos> selection, BlockPos origin,
                                                              int maxBlocks) {
        final Set<BlockPos> visited = new HashSet<>();
        final Deque<BlockPos> frontier = new ArrayDeque<>();
        frontier.add(origin);
        while (!frontier.isEmpty()) {
            final BlockPos current = frontier.poll();
            if (!visited.add(current)) {
                continue;
            }
            if (visited.size() > maxBlocks) {
                return null;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        final BlockPos next = current.offset(dx, dy, dz);
                        if (!visited.contains(next) && selection.contains(next)) {
                            frontier.add(next);
                        }
                    }
                }
            }
        }
        return visited;
    }

    // ==================== 物品数据 ====================

    @Nullable
    private static UUID readGrabbed(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag tag = data.copyTag();
        if (!tag.hasUUID(KEY_GRABBED)) {
            return null;
        }
        return tag.getUUID(KEY_GRABBED);
    }

    private static void writeGrabbed(ItemStack stack, UUID uuid) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putUUID(KEY_GRABBED, uuid));
    }

    private static void clearGrabbed(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(KEY_GRABBED));
    }
}
