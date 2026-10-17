package org.valkyrienskies.clockwork.content.logistics.gas.tank;

import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.clockwork.content.logistics.gas.GasMix;
import org.valkyrienskies.clockwork.content.logistics.gas.GasNodeBlockEntity;

import java.util.List;
import java.util.Objects;

/**
 * 气体储罐方块实体（多方块，复用 Create 的 {@code ConnectivityHandler}）。
 *
 * <p>对应旧版 {@code DuctTankBlockEntity}：同样是 {@code IMultiBlockEntityContainer}，
 * 最大高度 5、最大宽度 3。
 *
 * <p>整罐一份气体：质量和温度都存在<b>主方块</b>（最下面那块）的 {@link GasMix} 里，容量按
 * "每块 4 倍导管容量 × 高 × 宽²"放大（{@link #gasCapacityKg()}），其余段落只是同一个节点的延伸：
 * 它们的 {@link #gasMix()}/{@link #gasCapacityKg()} 都委托给主方块，因此从哪一块接管、看护目镜
 * 得到的都是整罐的数值。
 *
 * <p>与旧版的差异：
 * <ul>
 *   <li>旧版储罐节点的容积与单根导管相同（0.4 m³，不随多方块尺寸变化，看起来是旧版遗留问题）；
 *       本移植按体积放大容量，让它真正起到储气作用。</li>
 *   <li>旧版靠 Kelvin 的 {@code IConnectable} 把导管连到"主方块坐标"上，所以罐子任意一块都能接管；
 *       本移植照搬同一语义：<b>整罐只有主方块一个节点</b>，但每一段都"可接管"，
 *       接管入口统一折算到主方块坐标（见 {@link #gasPos()} / {@link #gasNodeActive()}）。</li>
 * </ul>
 *
 * <p><b>为什么整罐必须只注册一个节点</b>（曾经踩过的坑，改这里前先读完）：
 * 上一轮曾把"每一段都注册成节点"当作"任意一块都能接管"的实现方式，那是错的——如果段与段之间
 * 也建边，9 段储罐会让同一份气体在 9 个节点间被反复搬运/重复换热；如果不建边，气体网络就变成
 * 9 个<b>互不连通</b>的孤立节点，导管只有正好贴住主方块那一段时才接通，贴住别的段就是一口死罐
 * （2×2 的四段里主方块恰好较常被碰到，所以小罐"看起来正常"，3×3 的九段里主方块只占九分之一，
 * 于是"3×3 完全失效"）。现在改成"一个节点 + 节点坐标恒为主方块坐标"，与旧版
 * {@code DuctTankBlock.nodePlace}（只 addNode 一次、key 用 {@code getDuctNodePosition()} = 主方块坐标）
 * 完全一致，段数再多也只是同一个节点的不同接入口，几何规模不再影响功能。
 *
 * <p><b>同步契约（改动这里前请先读）</b>：客户端的外观只看方块状态、客户端的数据只看方块实体的字段，
 * 两条都是服务端单向推送，而且时序不保证，所以只有三个必须同步的位置：
 * <ol>
 *   <li>{@link #notifyMultiUpdated()}：结构成型/拆分后立刻写方块状态（UPDATE_CLIENTS）+ 发数据包
 *       + 让主块 {@code syncNow()} 立即同步气压（容量变了）；</li>
 *   <li>{@link #removeController(boolean)}：落回"单块"外观（Create 同款），否则残留段会停在
 *       上下全开口的"中间段"模型上；</li>
 *   <li>{@link #read(CompoundTag, HolderLookup.Provider, boolean)}：客户端拿到数据包后先清空再读
 *       {@code Controller}（否则"成员变主块"时旧 controller 永远留着），并在结构字段变化时
 *       强制客户端重算渲染网格。</li>
 * </ol>
 *
 * <p><b>客户端绝不能自己"纠正"结构字段</b>（曾经踩过的坑，删代码前先读）：
 * 某一版曾在客户端每 10 刻按方块状态 {@code BOTTOM} 重新推导 controller，理由是"数据包可能
 * 晚于方块状态到达"。这个推导是错的：{@code BOTTOM} 的语义是"本段位于整罐的最下一层"
 * （Create 的 {@code notifyMultiUpdated} 就是按 Y 坐标整层写 true），宽 3 的罐底有 <b>9 块</b>
 * 同时 {@code BOTTOM=true}，其中只有 1 块是 controller。按它推导会把另外 8 块的 controller
 * 清空、height/width 改成 1，于是客户端把它们当成 9 个独立单块：
 * <ul>
 *   <li>{@code ConnectivityHandler.isConnected} 比较的是 controller，CT 与面剔除立刻认为
 *       罐底与罐身断开 → 罐子看起来"自己分离"（且只有区块网格重建时才看得出来，所以是"有时候"）；</li>
 *   <li>护目镜从这些段读到的是各自那份空数据（0 Pa、1 block）。</li>
 * </ul>
 * Create 的客户端从不重新推导结构：{@code Controller/Height/Width} 由 {@code sendData} 权威下发，
 * {@code read} 里先清空再读；{@code controllerBE()} 每次调用都会重新 {@code level.getBlockEntity}，
 * 主块方块实体晚到时下一次调用自然解析成功。所以这里不再做任何客户端自愈，字段只在
 * {@link #read} / {@link #setController} / {@link #removeController} 三处改。
 */
public class DuctTankBlockEntity extends GasNodeBlockEntity implements IMultiBlockEntityContainer {

    /** 每块储罐相对单根导管的容量倍率。 */
    private static final double CAPACITY_PER_BLOCK = 4.0;

    private static final int MAX_HEIGHT = 5;
    private static final int MAX_WIDTH = 3;

    private int height = 1;
    private int width = 1;
    private BlockPos controller;
    private final BlockPos lastKnownPos;
    private boolean updateConnectivity;

    public DuctTankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, CAPACITY_PER_BLOCK);
        this.lastKnownPos = pos;
    }

    /** 排到下一 tick 再成型：拆罐/换主块时用（Create 的 {@code removeController} 同款）。 */
    public void queueConnectivityUpdate() {
        updateConnectivity = true;
    }

    /**
     * 立即成型（若本段是 controller）。对应 Create {@code FluidTankBlockEntity#updateConnectivity}。
     *
     * <p>两条调用路径与 Create 完全一致：{@link DuctTankBlock#onPlace} 放置时<b>同步</b>调用；
     * {@link #tick()} 消费 {@link #queueConnectivityUpdate()} 排下的任务（拆罐/存档恢复）。
     * 这里再判一次 {@code isController()}：只有主块能当 {@code formMulti} 的搜索原点；
     * 成员调进来是空操作（Create 同款）。
     */
    public void updateConnectivity() {
        updateConnectivity = false;
        if (level == null || level.isClientSide) {
            return;
        }
        if (!isController()) {
            return;
        }
        ConnectivityHandler.formMulti(this);
    }

    // ===================== 气体节点 =====================

    /**
     * 本段所属整罐的"身份坐标"= 主方块位置（旧版 {@code getDuctNodePosition()} 的语义）。
     *
     * <p>只用于判断"邻居是否属于同一罐体"（见 {@link #gasConnected(Direction)}），
     * 节点对外暴露的坐标是 {@link #gasPos()}。
     */
    private BlockPos tankRoot() {
        return isController() ? getBlockPos() : controller;
    }

    /**
     * 本段所属的主方块（整罐的"数据块"）；自身即主方块、或 controller 还没确定时返回自己。
     *
     * <p>客户端同样可用：{@code Controller}/{@code Height}/{@code Width} 随 {@code sendData} 一起同步。
     */
    private DuctTankBlockEntity controllerBE() {
        if (isController()) {
            return this;
        }
        if (controller == null || level == null) {
            return null;
        }
        return level.getBlockEntity(controller) instanceof DuctTankBlockEntity tank ? tank : null;
    }

    /**
     * 整罐只有一个气体节点，就在主方块身上；其余段落<b>不是</b>节点。
     *
     * <p>为什么用它而不是"每段一个节点"：见类注释。这里返回 false 的段落到
     * {@link org.valkyrienskies.clockwork.content.logistics.gas.GasNodeState#serverTick()} 里会被直接跳过
     * （不注册、不参与模拟），因此同一个 {@code nodes} 表里不会出现 9 个指向同一份气体的条目。
     *
     * <p>未成型的段（{@code controller == null}）按 {@link #isController()} 的定义算主方块，
     * 于是每段先各自是一个 1×1 节点、成型后被 {@code GasNetworks#prune} 收敛成主方块那一个节点，
     * 不需要额外的"重算归属"回调。
     */
    @Override
    public boolean gasNodeActive() {
        return isController();
    }

    /**
     * 节点坐标恒为<b>主方块坐标</b>，对应旧版 {@code DuctTankBlockEntity.getDuctNodePosition()}
     * （旧版注释：{@code if (isController) blockPos else controller}，即恒为主方块）。
     *
     * <p>为什么节点坐标不能是"本段自己的坐标"（曾经踩过的坑）：气体网络按方块坐标找节点
     * （{@code GasNetworks.nodeAt} 先查注册表、再回落到方块坐标上的方块实体），
     * 若节点坐标 = 段自己的坐标，那么贴着第 N 段的导管找到的就是"第 N 段那个节点"，
     * 而不是整罐；再叠上"段与段之间不建边"，除主方块外的段就全都接不通（见类注释）。
     * 恒返回主方块坐标后，任意一段被导管问到，拿到的都是同一个整罐节点，接哪一块都等价。
     *
     * <p>客户端也走这一条：客户端不注册节点、不跑气体模拟，护目镜只读数据，恒等映射没有副作用。
     */
    @Override
    public BlockPos gasPos() {
        DuctTankBlockEntity whole = controllerBE();
        return whole == null ? getBlockPos() : whole.getBlockPos();
    }

    /** 本段所在整罐的容量 [kg] = 单块容量 × 高 × 宽²（口径与 Create 液体储罐 width*width*height 一致）。 */
    private double ownCapacityKg() {
        return super.gasCapacityKg() * Math.max(1, height) * Math.max(1, width) * Math.max(1, width);
    }

    /**
     * 本段所在整罐由几块组成，<b>仅供护目镜显示</b>。
     *
     * <p>口径与 Create 一致：Create 用 {@code width * width * height} 作为"多少块"和整罐容积
     * （{@code ConnectivityHandler.tryToFormNewMulti} 第 121/131 行就是这么算的），其中 {@code width}
     * 是多方块底面正方形的<b>边长</b>、{@code height} 是层数，因此"宽"不是可自由长宽的矩形。
     * 底面被摆成 L 形/缺角时这里会高估块数（Create 的液体储罐也有同样的口径），
     * 因为它描述的是"这个多方块占的方块体积"，而不是"实际放了几块"。
     *
     * <p>不再用它做换热分摊：整罐只注册一个节点（见 {@link #gasNodeActive()}），
     * 换热天然只发生一次，不需要按块数把权重切碎。
     */
    private int tankBlocks() {
        DuctTankBlockEntity whole = controllerBE();
        int tankHeight = whole == null ? 1 : Math.max(1, whole.height);
        int tankWidth = whole == null ? 1 : Math.max(1, whole.width);
        return tankHeight * tankWidth * tankWidth;
    }

    /**
     * 气体状态委托给主方块：整罐只有主方块存气，其余段落自己那份永远是空的。
     *
     * <p>旧版护目镜/统计是"任意一段都去读主方块坐标上的 Kelvin 节点"（{@code getDuctNodePosition()}
     * 恒返回 controller 坐标），所以从罐子任意一块看过去都能读到整罐的数据；本移植照此委托，
     * 否则站在上层罐段看护目镜只会得到 {@code 0 Pa}。
     *
     * <p>安全性：委托出去的只是个"共享的 GasMix 对象"，不是复制；无论从哪一段进出气体，
     * 改的都是同一份质量和温度，因此不会出现"整罐的气被算成好几份"。
     */
    @Override
    public GasMix gasMix() {
        DuctTankBlockEntity controllerBE = controllerBE();
        return controllerBE == null ? super.gasMix() : controllerBE.gasState().mix();
    }

    @Override
    public double gasCapacityKg() {
        DuctTankBlockEntity controllerBE = controllerBE();
        return controllerBE == null ? ownCapacityKg() : controllerBE.ownCapacityKg();
    }

    /**
     * 护目镜：气体读数已经是整罐的（见 {@link #gasMix()}），再补一行"合并了几块、总容量多少"。
     *
     * <p>旧版护目镜有 {@code gui.ductInfo.volume} 一行显示整罐容积；本移植的通用节点提示没有这一行，
     * 而"相邻罐体是否真的合并成一个容积"只能从容量看出来，所以在这里单独显示。
     */
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        // 只读客户端已经同步下来的字段（Controller/Height/Width + GasMix），不在这里推导结构：
        // 原因见类注释"客户端绝不能自己纠正结构字段"。主块方块实体晚到时 controllerBE() 返回
        // null，本次显示退化为本段的空数据，下一帧/下一次发包后自然恢复，不会污染结构字段。
        boolean shown = super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        DuctTankBlockEntity whole = controllerBE();
        int tankWidth = whole == null ? 1 : Math.max(1, whole.width);
        int tankHeight = whole == null ? 1 : Math.max(1, whole.height);
        tooltip.add(Component.literal("    ")
                .append(Component.literal("Tank: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.format("%d blocks (%dW x %dH), %.2f kg",
                                tankBlocks(), tankWidth, tankHeight, gasCapacityKg()))
                        .withStyle(ChatFormatting.AQUA)));
        return shown;
    }

    /**
     * 连通规则：除"同一罐体的其他段落"以外，一律放行。
     *
     * <p>为什么每一段都要能连：旧版 {@code IConnectable.updateConnection} 建边时取的是
     * "邻居方块实体的 {@code getDuctNodePosition()}"，而对储罐来说这个坐标恒为主方块坐标，
     * 于是<b>导管接在罐子的任意一块上都能连到那一个整罐节点</b>。本移植的 {@link #gasPos()}
     * 同样恒返回主方块坐标，所以导管贴住任意一段都能找到同一个整罐节点（{@code GasNetworks.nodeAt}
     * 先查注册表，查不到就回落到该坐标上的方块实体，因此"段不是注册节点"也不影响被发现）。
     *
     * <p>同一罐体内部两段之间不连：是<b>故意</b>的。若建边，{@code GasNetwork.transfer} 会把同一份
     * {@link GasMix} 在段与段之间反复搬运（两个节点读的是同一个对象，压差恒为 0 但会白跑一遍换热与
     * 脏标记），而它们本来就是同一份气体、没有任何需要搬运的东西。段间不建边也不影响"任意一段能接管"：
     * 导管是按坐标找节点的，不需要沿着罐体一段段走过去。
     */
    @Override
    public boolean gasConnected(Direction dir) {
        if (level == null) {
            return true;
        }
        BlockEntity neighbor = level.getBlockEntity(getBlockPos().relative(dir));
        if (neighbor instanceof DuctTankBlockEntity other) {
            return !tankRoot().equals(other.tankRoot());
        }
        return true;
    }

    @Override
    public void gasChanged() {
        // 气体存在主方块里，脏标记必须落在主方块上，否则整罐的气不会被存档/同步
        DuctTankBlockEntity controllerBE = controllerBE();
        if (controllerBE == null) {
            super.gasChanged();
            setChanged();
            return;
        }
        controllerBE.gasState().markDirty();
        controllerBE.setChanged();
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }
        // 只消费"结构事件"排下的 flag；气体/热量的周期同步走 GasNodeState.syncIfChanged()，
        // 只发数据包、不写方块状态（见类注释的同步契约）。这里绝不 setBlock。
        if (updateConnectivity) {
            updateConnectivity();
        }
    }

    // ===================== 多方块 =====================

    @Override
    public void notifyMultiUpdated() {
        BlockState state = getBlockState();
        if (level != null && state.getBlock() instanceof DuctTankBlock) {
            BlockPos controllerPos = getController();
            state = state.setValue(DuctTankBlock.BOTTOM, controllerPos != null && controllerPos.getY() == getBlockPos().getY());
            state = state.setValue(DuctTankBlock.TOP,
                    controllerPos != null && controllerPos.getY() + height - 1 == getBlockPos().getY());
            // 为什么必须在这里立刻写方块状态并带 UPDATE_CLIENTS：
            // 客户端的"长什么样"完全由方块状态决定（罐体没有自定义模型/方块实体渲染器），
            // 而 Create 的 ConnectivityHandler 只在服务端跑（formMulti 由服务端驱动），
            // 所以"结构变了 ⇒ 每段的 top/bottom 变了"这件事只能由服务端主动 setBlock 告诉客户端。
            // flag 与 Create FluidTankBlockEntity.notifyMultiUpdated 逐字相同（2|4）：
            //   2 = UPDATE_CLIENTS：ServerLevel.sendBlockUpdated → 本刻末广播新方块状态；
            //   4 = UPDATE_INVISIBLE：不参与"是否可见"的判定；**不是**"跳过邻居更新"。
            // 真正保证不触发邻居重算的是：1（UPDATE_NEIGHBORS）不在 flag 里，Level.setBlock 不会
            // 调 blockUpdated/neighborChanged；本方块也没有重写 updateShape/updateIndirectNeighbourShapes。
            // 另外 DuctTankBlock.onPlace 对"同方块换状态"直接 return，所以这里 setBlock 不会再排
            // 一次 formMulti（javap 核实过 LevelChunk.setBlockState 对任何状态变化都会回调 onPlace）。
            level.setBlock(getBlockPos(), state, Block.UPDATE_CLIENTS | Block.UPDATE_INVISIBLE);
        }
        setChanged();
        sendData();
        // 为什么结构变化还要额外 syncNow：成型/拆分同时改变了整罐容量与块数，压力会瞬间跳变，
        // 而 GasNodeState 的常规同步是"10 刻 + 阈值"，跳变后的差值可能正落在阈值内，
        // 于是护目镜会停在旧结构的读数上。整罐的气体只在主块里，所以只让主块立即发包。
        DuctTankBlockEntity whole = controllerBE();
        if (whole != null) {
            whole.gasState().syncNow();
        }
    }

    /**
     * 拆解时把本段恢复成"单块罐"的外观（{@code top=true, bottom=true}）。
     *
     * <p><b>为什么必须单独写一次</b>：{@link #removeController(boolean)} 把 height/width 置 1 后，
     * 下一 tick 的 {@code formMulti} 对"1×1"会命中
     * {@code ConnectivityHandler.tryToFormNewMulti} 的快速返回
     * （{@code beWidth == 1 && 1*1*height == amount}，Create 6.0.10 第 128-131 行），
     * <b>不会</b>再调 {@link #notifyMultiUpdated()}。若这里不写 top/bottom，残留段会停在原来的
     * {@code block_middle}/{@code block_bottom} 外观（顶段没有盖子、底段没有底板）——
     * 这正是实机看到的"多方块结构纹理异常"。
     *
     * <p>为什么不用 {@code notifyMultiUpdated} 代替：后者语义是"结构已经成型，按
     * controller/height 重算每一段"，还附带 {@code sendData}/{@code syncNow}/容量等结构副作用；
     * 这里只是"结构已拆掉，先把本段落成单块外观"，之后存活段会重新 formMulti 覆盖成正确段落。
     * Create 的 {@code FluidTankBlockEntity.removeController}（1.21.1 第 249-255 行）也是显式写
     * {@code BOTTOM=true, TOP=true}，本类照做。
     *
     * <p>注意 {@link #getController()} 在 {@code controller == null} 时会回退到自身，所以
     * 即使调用 {@code notifyMultiUpdated} 也会算出 (true,true)；旧注释说它算出
     * (false,false) 是不准确的，真正的理由是上面的快速返回 + 副作用不同。
     */
    private void applySingleShape() {
        BlockState state = getBlockState();
        if (level == null || !(state.getBlock() instanceof DuctTankBlock)) {
            return;
        }
        state = state.setValue(DuctTankBlock.BOTTOM, true);
        state = state.setValue(DuctTankBlock.TOP, true);
        // flag 22 = 2|4|16，与 Create removeController 完全一致（16=UPDATE_KNOWN_SHAPE：拆解中不再扩散形状更新）
        level.setBlock(getBlockPos(), state, 22);
    }

    @Override
    public void removeController(boolean keepContents) {
        if (level == null || level.isClientSide) {
            return;
        }
        controller = null;
        height = 1;
        width = 1;
        queueConnectivityUpdate();
        // 先把外观落到"单块"，再让上层的 splitMulti/re-form 覆盖成正确的段落外观（见 applySingleShape 注释）
        applySingleShape();
        setChanged();
        sendData();
    }

    @Override
    public BlockPos getController() {
        return isController() ? getBlockPos() : controller;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity & IMultiBlockEntityContainer> T getControllerBE() {
        if (isController()) {
            return (T) this;
        }
        BlockEntity be = controller == null || level == null ? null : level.getBlockEntity(controller);
        return be instanceof IMultiBlockEntityContainer ? (T) be : null;
    }

    @Override
    public boolean isController() {
        return controller == null || controller.equals(getBlockPos());
    }

    @Override
    public void setController(BlockPos pos) {
        // 与 Create FluidTankBlockEntity.setController 一致：只在服务端改，且值没变就不发包。
        // 客户端不写 controller：结构归属是服务端权威，客户端只通过 sendData/read 接收；
        // 若客户端也能本地改，CT/护目镜会在一帧内看到"半新半旧"的结构（上一版的
        // reconcileClientState 按 BOTTOM 把 3×3 罐底的 8 块改坏，见类注释）。
        if (level == null || level.isClientSide) {
            return;
        }
        if (Objects.equals(pos, controller)) {
            return;
        }
        controller = pos;
        setChanged();
        sendData();
    }

    @Override
    public BlockPos getLastKnownPos() {
        return lastKnownPos;
    }

    @Override
    public void preventConnectivityUpdate() {
        updateConnectivity = false;
    }

    @Override
    public Direction.Axis getMainConnectionAxis() {
        return Direction.Axis.Y;
    }

    @Override
    public int getMaxLength(Direction.Axis longAxis, int width) {
        return longAxis == Direction.Axis.Y ? MAX_HEIGHT : MAX_WIDTH;
    }

    @Override
    public int getMaxWidth() {
        return MAX_WIDTH;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public void setHeight(int height) {
        this.height = height;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public void setWidth(int width) {
        this.width = width;
    }

    // ===================== NBT =====================

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        // 与 Create FluidTankBlockEntity.write 一致：把"本段还需要重新成型"落盘。
        // 为什么这一位必须进 NBT：拆罐 / 换主块的瞬间 removeController 会 queueConnectivityUpdate()，
        // 若此时退出存档（或区块被保存），下次进入世界时这一位会让本段自己再 formMulti 一次，
        // 把 top/bottom 与结构归属重新算对；不写它的话，结构在"半成型"状态下存盘后，
        // 重进也不会自愈（贴图/读数会一直停在错的形状上）。
        if (updateConnectivity) {
            tag.putBoolean("Uninitialized", true);
        }
        tag.putInt("Height", height);
        tag.putInt("Width", width);
        if (controller != null) {
            tag.put("Controller", NbtUtils.writeBlockPos(controller));
        }
        super.write(tag, provider, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        BlockPos controllerBefore = controller;
        int widthBefore = width;
        int heightBefore = height;

        super.read(tag, provider, clientPacket);

        // 为什么必须先清空再读（Create FluidTankBlockEntity.read 同款）：
        // 上面 write 只在 controller != null 时写 "Controller"，也就是**主块自己不带这个字段**。
        // 若这里不清空，客户端会把旧 controller 一直留着：本段从"成员"变成"主块"时
        // （拆掉下面那段、或拆分后本段成为新罐的底块），isController() 恒为 false，
        // getController()/controllerBE() 指向已经不存在、或已经换了主人的那一块，
        // 护目镜 gasMix()/gasCapacityKg() 就会读到别的罐（或空罐、1 block）。
        // 服务端状态一直是对的，所以只有退出存档重进（客户端方块实体重新构造、controller 从 null 开始）
        // 才恢复 —— 这正是"只有退出存档重进才能恢复"的直接原因。
        controller = null;
        if (tag.contains("Controller")) {
            controller = NbtUtils.readBlockPos(tag, "Controller").orElse(null);
        }
        height = Math.max(1, tag.getInt("Height"));
        width = Math.max(1, tag.getInt("Width"));
        updateConnectivity = tag.contains("Uninitialized");

        // 为什么客户端必须在这里强制重算渲染（Create FluidTankBlockEntity.read 同款，见其 439-451 行）：
        // 数据包只更新方块实体字段，客户端已经烘好的区块网格不会因此重算；而方块状态广播
        // （服务端 setBlock → ChunkHolder 下一 tick 广播）与数据包是两条独立路径，时序不保证。
        // level.sendBlockUpdated 在客户端会走 ClientLevel.sendBlockUpdated → LevelRenderer.blockChanged
        // → 把 3×3×3 个 section 标脏并重算渲染包围盒，保证"结构一成型/一拆分，外观立刻跟着变"，
        // 而不是继续显示成型前的旧模型（单块外观）直到重进存档重建整块网格。
        if (clientPacket && (!Objects.equals(controllerBefore, controller)
                || widthBefore != width || heightBefore != height)) {
            if (level != null) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 16);
            }
            invalidateRenderBoundingBox();
        }
    }
}
