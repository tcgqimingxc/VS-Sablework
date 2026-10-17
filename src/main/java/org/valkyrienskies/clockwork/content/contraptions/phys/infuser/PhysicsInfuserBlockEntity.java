package org.valkyrienskies.clockwork.content.contraptions.phys.infuser;

import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.IDisplayAssemblyExceptions;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.clockwork.ClockworkMod;
import org.valkyrienskies.clockwork.config.ClockworkConfig;
import org.valkyrienskies.clockwork.platform.CWAssembly;
import org.valkyrienskies.clockwork.platform.CWSubLevels;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 物理灌注器方块实体。
 *
 * <p>对应旧版 {@code content/contraptions/phys/infuser/PhysicsInfuserBlockEntity}（455 行）。
 *
 * <p><b>装配链路</b>：
 * <ul>
 *   <li>选定区域：按 26 邻接连通性拆成若干"孤岛"，每岛调用
 *       {@code CWAssembly#assembleBlockSet} → Sable {@code SubLevelAssemblyHelper#assembleBlocks}，
 *       对应旧版的 {@code WanderwandItem.findIsolatedComponents} + {@code ShipAssembler.assembleToShip}；</li>
 *   <li>无选择器：对贴着灌注器的相邻方块调用 {@code SimAssemblyHelper#assembleFromSingleBlock}
 *       （与物理轴承同一条链路）。</li>
 * </ul>
 *
 * <p><b>与旧版的差异</b>：
 * <ul>
 *   <li>旧版把权杖放进灌注器的容器槽；这里改为"手持即用"（不占用槽位），
 *       权杖侧只需实现 {@link IAreaSelector}。</li>
 *   <li>旧版解体是空实现（{@code fun disassemble() {}}）；这里补上了"潜行右键 = 原地放回世界"，
 *       否则拆掉灌注器会留下一堆无主的子空间。</li>
 *   <li>装配耗时接配置（{@code infuser.assemblyTicks}），旧版是硬编码的 500 tick + 多段音效。</li>
 *   <li>旧版的动画状态（{@code Animation.ASSEMBLY/IDLE} + 三个进度插值器）只服务于渲染；
 *       这里只保留渲染真正需要的那一份：{@code assemblyTotal}（总刻数，开机时定格并同步）
 *       与客户端自增的 {@code clientAnimationTicks}，见 {@link #getCoreRiseProgress(float)}。</li>
 * </ul>
 */
public class PhysicsInfuserBlockEntity extends SmartBlockEntity implements IDisplayAssemblyExceptions {

    private boolean assembling;
    private int progress;
    private boolean skipRequested;
    /** 本次装配的总刻数（开始装配时从配置定格，随方块实体数据同步，供客户端推进核心上浮动画）。 */
    private int assemblyTotal = 1;
    /** 客户端本地的装配动画计时（仅客户端 tick 时累加；服务端恒为 0）。 */
    private int clientAnimationTicks;
    /** 本次装配要处理的方块集合（没有选择器时为 null，退化成"装配相邻连通块"）。 */
    @Nullable
    private Set<BlockPos> pendingSelection;
    /** 本灌注器创造出来的子空间，用于解体。 */
    private final List<UUID> created = new ArrayList<>();
    @Nullable
    private AssemblyException lastException;

    public PhysicsInfuserBlockEntity(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(final List<BlockEntityBehaviour> behaviours) {
        // 无行为（旧版同样为空）
    }

    // ==================== 玩家入口 ====================

    /** 开始装配；正在装配时再次调用 = 跳过动画（对标旧版 skipAssembly）。 */
    public void startAssembly(final @Nullable Collection<BlockPos> selection) {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        if (this.assembling) {
            this.skipRequested = true;
            return;
        }
        this.pendingSelection = selection == null ? null : new HashSet<>(selection);
        this.assembling = true;
        this.progress = 0;
        this.skipRequested = false;
        this.assemblyTotal = Math.max(1, ClockworkConfig.server().infuserAssemblyTicks.get());
        this.sendData();
    }

    /** 把本灌注器创造的所有子空间原地放回世界。 */
    public void disassembleAll() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        for (final UUID id : new ArrayList<>(this.created)) {
            if (CWSubLevels.byId(this.level, id) instanceof final ServerSubLevel sub && !sub.isRemoved()) {
                CWAssembly.disassembleInPlace(this.level, sub);
            }
        }
        this.created.clear();
        this.sendData();
    }

    // ==================== tick ====================

    @Override
    public void tick() {
        super.tick();
        if (this.level == null) {
            return;
        }
        if (this.level.isClientSide) {
            // 客户端只推进渲染动画的计时，不参与装配
            this.clientAnimationTicks = this.assembling
                    ? Math.min(this.clientAnimationTicks + 1, this.assemblyTotal)
                    : 0;
            return;
        }
        if (!this.assembling) {
            return;
        }
        this.progress++;
        if (!this.skipRequested && this.progress < this.assemblyTotal) {
            return;
        }
        this.assembling = false;
        this.progress = 0;
        this.skipRequested = false;
        if (this.level instanceof final ServerLevel serverLevel) {
            this.runAssembly(serverLevel);
        }
        this.sendData();
    }

    /**
     * 核心上浮进度 0..1（渲染用，对应旧版 {@code getCoreOffset} 的 ASSEMBLY 分支）：
     * 前 3/4 段按 {@code t⁴} 加速上升，最后 1/4 段用弹跳缓动落回。
     *
     * <p>旧版用一个每帧自增的 {@code coreAngle} 与三个 {@code LerpedFloat} 进度插值器算这个值，
     * 这里改由"客户端计时 / 同步下来的总刻数"直接算比例，等价且不依赖逐刻发包。
     *
     * @param partialTicks 帧内插值（0..1）
     */
    public float getCoreRiseProgress(final float partialTicks) {
        if (!this.assembling || this.assemblyTotal <= 0) {
            return 0.0f;
        }
        final float ticks = Math.min(this.clientAnimationTicks + partialTicks, this.assemblyTotal);
        final float t = ticks / this.assemblyTotal;
        if (t < 0.75f) {
            // 旧版：clamp(pow(ticks / ASSEMBLY_TIME * 3, 4)) —— t=1/3 时就已升到顶并一直保持
            return Mth.clamp((float) Math.pow(t * 3.0, 4.0), 0.0f, 1.0f);
        }
        return Mth.clamp(easeInBounce(Mth.clamp((1.0f - t) * 4.0f, 0.0f, 1.0f)), 0.0f, 1.0f);
    }

    /** 旧版 {@code EaseHelper.easeInBounce}：{@code 1 - easeOutBounce(1 - x)}。 */
    private static float easeInBounce(final float x) {
        return 1.0f - easeOutBounce(1.0f - x);
    }

    /** 旧版 {@code EaseHelper.easeOutBounce}：标准 CSS {@code bounce.out} 曲线（n1 = 7.5625, d1 = 2.75）。 */
    private static float easeOutBounce(final float x) {
        final double n1 = 7.5625;
        final double d1 = 2.75;
        if (x < 1.0 / d1) {
            return (float) (n1 * x * x);
        }
        if (x < 2.0 / d1) {
            return (float) (n1 * (x - 1.5 / d1) * x + 0.75);
        }
        if (x < 2.5 / d1) {
            return (float) (n1 * (x - 2.25 / d1) * x + 0.9375);
        }
        return (float) (n1 * (x - 2.625 / d1) * x + 0.984375);
    }

    // ==================== 装配 ====================

    private void runAssembly(final ServerLevel level) {
        this.lastException = null;
        final Set<BlockPos> selection = this.pendingSelection;
        this.pendingSelection = null;

        if (selection == null || selection.isEmpty()) {
            this.assembleAdjacent(level);
            return;
        }

        final int maxBlocks = ClockworkConfig.server().infuserMaxBlocks.get();
        for (final Set<BlockPos> component : splitIntoComponents(selection)) {
            if (component.size() > maxBlocks) {
                ClockworkMod.LOGGER.warn("[Clockwork] 物理灌注器：选定区域的一个连通块有 {} 个方块，超过上限 {}，已跳过",
                        component.size(), maxBlocks);
                continue;
            }
            final BlockPos anchor = component.stream()
                    .min(Comparator.comparingDouble(pos -> pos.distSqr(this.worldPosition)))
                    .orElse(null);
            if (anchor == null || CWSubLevels.containingServer(level, anchor) != null) {
                continue;
            }
            final ServerSubLevel subLevel = CWAssembly.assembleBlockSet(level, anchor, component);
            if (subLevel != null) {
                this.created.add(subLevel.getUniqueId());
            }
        }
    }

    /** 没有选择器时：把贴着灌注器的任意一块连通结构装配起来。 */
    private void assembleAdjacent(final ServerLevel level) {
        for (final Direction direction : Direction.values()) {
            final BlockPos target = this.worldPosition.relative(direction);
            if (level.getBlockState(target).isAir() || CWSubLevels.containingServer(level, target) != null) {
                continue;
            }
            try {
                final var result = CWAssembly.assembleSingle(level, this.worldPosition, target, false, false);
                if (result != null) {
                    this.created.add(result.subLevel().getUniqueId());
                    return;
                }
            } catch (final AssemblyException e) {
                this.lastException = e;
                return;
            }
        }
    }

    /**
     * 把选定的方块集合按 26 邻接拆成连通块（对应旧版 {@code findIsolatedComponents}）。
     *
     * <p>同一块结构如果被拆成多个不连通的部分，Sable 必须分别装配成独立的子空间，
     * 否则 plot 会包含空洞、质心也会算错。
     */
    private static List<Set<BlockPos>> splitIntoComponents(final Set<BlockPos> blocks) {
        final List<Set<BlockPos>> components = new ArrayList<>();
        final Set<BlockPos> remaining = new HashSet<>(blocks);
        while (!remaining.isEmpty()) {
            final BlockPos start = remaining.iterator().next();
            final Set<BlockPos> component = new HashSet<>();
            final Deque<BlockPos> frontier = new ArrayDeque<>();
            frontier.add(start);
            while (!frontier.isEmpty()) {
                final BlockPos current = frontier.poll();
                if (!remaining.remove(current)) {
                    continue;
                }
                component.add(current);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) {
                                continue;
                            }
                            final BlockPos next = current.offset(dx, dy, dz);
                            if (remaining.contains(next)) {
                                frontier.add(next);
                            }
                        }
                    }
                }
            }
            components.add(component);
        }
        return components;
    }

    // ==================== 持久化 ====================

    @Override
    protected void write(final CompoundTag compound, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putBoolean("Assembling", this.assembling);
        compound.putInt("Progress", this.progress);
        compound.putInt("AssemblyTotal", this.assemblyTotal);
        final long[] ids = new long[this.created.size() * 2];
        for (int i = 0; i < this.created.size(); i++) {
            ids[i * 2] = this.created.get(i).getMostSignificantBits();
            ids[i * 2 + 1] = this.created.get(i).getLeastSignificantBits();
        }
        compound.putLongArray("CreatedSubLevels", ids);
        AssemblyException.write(compound, registries, this.lastException);
    }

    @Override
    protected void read(final CompoundTag compound, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        this.assembling = compound.getBoolean("Assembling");
        this.progress = compound.getInt("Progress");
        this.assemblyTotal = Math.max(1, compound.getInt("AssemblyTotal"));
        this.created.clear();
        final long[] ids = compound.getLongArray("CreatedSubLevels");
        for (int i = 0; i + 1 < ids.length; i += 2) {
            this.created.add(new UUID(ids[i], ids[i + 1]));
        }
        this.lastException = AssemblyException.read(compound, registries);
    }

    @Override
    public AssemblyException getLastAssemblyException() {
        return this.lastException;
    }
}
