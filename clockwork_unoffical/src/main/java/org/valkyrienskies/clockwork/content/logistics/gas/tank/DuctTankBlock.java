package org.valkyrienskies.clockwork.content.logistics.gas.tank;

import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.valkyrienskies.clockwork.content.logistics.gas.CWGasIndex;
import org.valkyrienskies.clockwork.content.logistics.gas.IGasNodeBlock;

/**
 * 气体储罐：可竖直堆叠成多层（沿用 Create 的 {@code ConnectivityHandler} 多方块机制）。
 *
 * <p>对应 Create 的 {@code com.simibubi.create.content.fluids.tank.FluidTankBlock}。方块状态只保留
 * {@code top}/{@code bottom} 两个布尔量（Create 同款），它们唯一的作用是<b>选模型</b>：
 * <pre>
 *   bottom=true,  top=true   → block_single   （1×1×1）
 *   bottom=true,  top=false  → block_bottom   （罐底）
 *   bottom=false, top=true   → block_top      （罐顶）
 *   bottom=false, top=false  → block_middle   （中间段，四面薄壁、上下面全开）
 * </pre>
 * 这 4 个模型都照抄 Create 的 {@code models/block/fluid_tank/block_*}（含其 uv 分区：盖沿 4 像素、
 * 壁面 12 像素），由 {@code DuctTankBlockEntity#notifyMultiUpdated} 在结构变化时写进方块状态。
 *
 * <p><b>贴图不靠方块状态挑</b>：侧壁/盖沿用哪一格贴图由连接纹理决定，见
 * {@link org.valkyrienskies.clockwork.content.logistics.gas.tank.client.DuctTankCTBehaviour}。
 *
 * <p><b>物品图标不走这 4 个世界模型</b>：它们被 {@code DuctTankModel}（CT）接管，且侧壁贴图
 * {@code duct_tank.png} 是从 4×4 图集裁出的"可平铺的一格墙"，只适合在世界里按邻居重采样；
 * 直接把它烘成 item 会得到一个没有边框/铆钉的灰盒子（实机"物品栏贴图错误"）。
 * 物品模型改为 {@code models/item/duct_tank.json → block/duct_tank/block_single_item}：
 * 官方 1.20.1 的单块立方体 + 64×64 图集 (0,3) 格，和 Create 用
 * {@code fluid_tank.json → block/duct_tank/block_single_window} 同一个思路
 * （item 指向"不被 CT 包装、自带完整面板贴图"的普通模型）。见 {@code docs/GAS-TANK.md}。
 *
 * <p>与 Create 的差异：<b>没有 {@code SHAPE} 属性、没有窗户</b>。Create 的储罐还有
 * {@code PLAIN/WINDOW/WINDOW_NW/WINDOW_SW/WINDOW_NE/WINDOW_SE} 六态 + {@code setWindows}
 * （{@code FluidTankBlockEntity.java:297-329}：宽 1 全窗、宽 2 每块一个角窗、宽 3 只有
 * {@code |x|-|z|=±1} 的 4 块有窗），本工程不复刻，理由是本罐体<b>没有任何窗户模型/贴图</b>：
 * 官方 Clockwork 1.20.1 的 {@code models/block/duct_tank/} 只有这 4 个模型、贴图目录里也没有
 * {@code *_window*}。硬加 {@code SHAPE} 只会得到 20 个不存在的模型引用（{@code check_assets.py}
 * 的 [12] 属性覆盖会立刻报出来，实机是黑紫）。这是"必要子集"，不是漏做。
 *
 * <p>拆罐必须走 {@link ConnectivityHandler#splitMulti}（见 {@link #onRemove}），这一点与 Create 的
 * {@code FluidTankBlock}、旧版 {@code DuctTankBlock.onRemove} 完全一致，不能省。
 */
public class DuctTankBlock extends Block implements IBE<DuctTankBlockEntity>, IGasNodeBlock {

    public static final BooleanProperty TOP = BooleanProperty.create("top");
    public static final BooleanProperty BOTTOM = BooleanProperty.create("bottom");

    public DuctTankBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(TOP, true)
                .setValue(BOTTOM, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TOP, BOTTOM);
    }

    @Override
    public Class<DuctTankBlockEntity> getBlockEntityClass() {
        return DuctTankBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends DuctTankBlockEntity> getBlockEntityType() {
        return CWGasIndex.DUCT_TANK_BE.get();
    }

    /**
     * 放置/替换方块时立即重建多方块，与 Create 的 {@code FluidTankBlock.onPlace}（1.21.1）逐条对齐。
     *
     * <p><b>为什么先判 {@code oldState.is(state.getBlock())}</b>：{@link DuctTankBlockEntity#notifyMultiUpdated()}
     * 会 {@code level.setBlock} 写 {@code top}/{@code bottom}，而 {@code LevelChunk.setBlockState} 对
     * <b>任何</b>状态变化都会回调 {@code onPlace}（已用 javap 核实 1.21.1 字节码：非客户端分支里
     * {@code newState.onPlace(...)} 在 block entity 创建之前无条件执行，没有"换块"判断）。
     * 若这里不返回，一次结构成型就会对每一段再排一次 {@code formMulti}。现在
     * {@code ConnectivityHandler} 的 width/height 快速返回让它幂等，所以不会立刻出事；
     * 但"外观同步"和"结构重算"被绑在一起，以后只要改动 formMulti 的快速返回条件，
     * 写一次 top/bottom 就会把整罐拆开——这正是"罐子自己分离"最难查的一类来源。
     * Create 的写法就是靠这个 guard 把两条路径分开。
     *
     * <p><b>为什么 {@code isMoving} 直接返回</b>：活塞/移动装置搬方块时不该在这里重新搜多方块，
     * {@code SmartBlockEntity} 的 onPositionChanged 会负责清理（Create 同款）。
     *
     * <p><b>为什么成型是同步的、而不是排到下一 tick</b>：Create 在 onPlace 里直接调
     * {@code updateConnectivity}。放置的这一刻就要把本格的 top/bottom 算对，紧接着下面的
     * markAndNotifyBlock 才能把"成型后的"方块状态发给客户端。
     */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (oldState.is(state.getBlock())) {
            return;
        }
        if (isMoving) {
            return;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof DuctTankBlockEntity tank) {
            tank.updateConnectivity();
        }
        // Create 同款兜底（FluidTankBlock.onPlace 第 101-106 行）：updateConnectivity 可能在
        // onPlace 期间就改掉了本格的方块状态，而 NeoForge 在放块之后仍会用"放之前"的 state
        // 做一次 markAndNotifyBlock。不再标一次的话，客户端会停在默认的 block_single 外观，
        // 直到下一次区块重建才恢复（实机上就是"放下 3 格罐，只有最下面一格像罐底"）。
        BlockState newState = level.getBlockState(pos);
        if (state != newState && newState.is(this)) {
            level.markAndNotifyBlock(pos, level.getChunkAt(pos), oldState, newState,
                    Block.UPDATE_ALL_IMMEDIATE, 512);
        }
    }

    /**
     * 拆掉任意一段罐体后，必须让 {@link ConnectivityHandler#splitMulti} 把整罐"作废重算"。
     *
     * <p>为什么不能只通知邻居：{@code DuctTankBlockEntity.tick()} 里调用 {@code formMulti} 的前提是
     * {@code isController()}（与 Create 的 {@code FluidTankBlockEntity.updateConnectivity} 一致，
     * 已用 javap 核实）。拆掉主方块（最下面那块）后，剩余罐段的 controller 仍指向那个已经不存在的坐标，
     * {@code isController()} 恒为 false → 再也没人调用 {@code formMulti}，整个罐子退化成一堆死方块
     * （护目镜读数归零、不再参与气体网络、罐内气体随主方块实体一起消失）。
     * splitMulti 会把每个存活罐段的 controller 清空（{@code removeController(true)}），它们各自在下一刻
     * 重新 formMulti，于是罐子被正确拆成"下半段 + 上半段"。
     * 旧版 {@code DuctTankBlock.onRemove} 与 Create {@code FluidTankBlock.onRemove} 都是这个写法。
     *
     * <p>另外：整罐的气体存在主方块的方块实体里，而 Create 的液体储罐在拆掉控制器时会先把内容物分给
     * 存活段落（{@code ConnectivityHandler.splitMultiAndInvalidate} 里的 Fluid 分支）。这里照做——
     * 拆主方块前把气体并到正上方那块，否则"拆一段 = 气全没了"。
     */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.hasBlockEntity() && (!state.is(newState.getBlock()) || !newState.hasBlockEntity())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof DuctTankBlockEntity tank) {
                // 拆的是主方块（最下面那块）时，整罐的气体都存在它身上：先交给正上方那块
                // （拆完它会成为新的主方块），否则气体会随着被销毁的方块实体一起凭空消失。
                // Create 液体储罐的 ConnectivityHandler.splitMulti 也是这么做的（把控制器里的液体分给存活段）。
                if (tank.isController() && tank.getHeight() > 1
                        && level.getBlockEntity(pos.above()) instanceof DuctTankBlockEntity above
                        && tank.getBlockPos().equals(above.getController())) {
                    above.gasState().mix().absorb(tank.gasState().mix());
                }
                // 与 Create 一致：先摘掉方块实体（IBE.onRemove = SmartBlockEntity.destroy + removeBlockEntity），
                // 再拆罐——splitMulti 会自己跳过已经被拆掉的那一段。
                IBE.onRemove(state, level, pos, newState);
                ConnectivityHandler.splitMulti(tank);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /**
     * 比较器输出：按罐内压力给信号（旧版没有这个功能，旧版只有护目镜显示）。
     * 1 atm 以下为 0，达到 15 atm 时满信号，便于用红石控制压缩机。
     */
    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof DuctTankBlockEntity tank)) {
            return 0;
        }
        // 1 atm 以下为 0，达到或超过 15 atm 时满信号
        double atmospheres = tank.gasMix().pressure(tank.gasCapacityKg()) / 101325.0;
        return net.minecraft.util.Mth.clamp((int) atmospheres, 0, 15);
    }
}
