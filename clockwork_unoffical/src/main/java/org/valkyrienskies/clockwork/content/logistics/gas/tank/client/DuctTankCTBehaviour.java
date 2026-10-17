package org.valkyrienskies.clockwork.content.logistics.gas.tank.client;

import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.HorizontalCTBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 气体储罐的 CT 行为：告诉 {@link com.simibubi.create.foundation.block.connected.CTModel}
 * "这个面该用哪条 sprite shift"，以及"邻居算不算连上"。
 *
 * <h2>对应 Create 的哪个文件</h2>
 * {@code com.simibubi.create.content.fluids.tank.FluidTankCTBehaviour}（Create 1.21.1 全 41 行），
 * 本类只差一处：Create 多一条 {@code innerShift}（内腔面），本工程没有（理由见
 * {@link DuctTankSpriteShifts} 类注释）。取格规则本身完全继承自
 * {@code HorizontalCTBehaviour#getShift}：
 * <ul>
 *   <li>水平轴的面（N/S/E/W，含盖子的 4 条"盖沿"）→ 侧壁映射 {@code DUCT_TANK}；</li>
 *   <li>竖直轴的面（UP/DOWN，含盖子的外顶/外底）→ {@code DUCT_TANK_TOP}。</li>
 * </ul>
 * 这也是 Create 储罐"侧壁一族贴图、顶底另一族贴图"的唯一来源 —— 与方块状态无关，所以
 * 成型/拆分时不会因为换了模型而换错族。
 *
 * <h2>为什么 buildContextForOccludedDirections 要返回 true</h2>
 * CT 的取格需要"邻居连不连"的上下文，而 {@code CTModel#createCTData} 默认只为<b>可见</b>的面建上下文
 * （不可见的面被 {@code Block.shouldRenderFace} 过滤掉）。多方块罐体里，相邻两块的侧面互相贴合、
 * 面被遮挡，但它们的 uv 仍需被正确地错开成"内部格"；若被过滤，这些面会拿到 {@code -1} 而保留
 * 基础图 uv，于是 3×3 的拼接处会出现一格"单体罐"的花纹。Create 的储罐同样打开这个开关
 * （{@code FluidTankCTBehaviour.java:32-34}）。
 *
 * <h2>为什么 connectsTo 要自己写（而不是用默认实现）</h2>
 * 默认实现只比较 {@code state.getBlock() == other.getBlock()}，也就是"同一种方块就算连上"。
 * 储罐不行：两个<b>各自独立</b>的 1×1 罐贴着放，贴图不该连成一片（它们不是同一个多方块）。
 * 所以这里再叠加 {@code ConnectivityHandler.isConnected} —— 只有同一个 controller 下的两段才算连。
 * 这正是 Create {@code FluidTankCTBehaviour.java:36-40} 与旧版
 * {@code DuctTankCTBehaviour.kt:24-29} 的语义，一字未改。
 */
public class DuctTankCTBehaviour extends HorizontalCTBehaviour {

    public DuctTankCTBehaviour(CTSpriteShiftEntry layerShift, CTSpriteShiftEntry topShift) {
        super(layerShift, topShift);
    }

    @Override
    public boolean buildContextForOccludedDirections() {
        return true;
    }

    @Override
    public boolean connectsTo(BlockState state, BlockState other, BlockAndTintGetter reader, BlockPos pos, BlockPos otherPos,
                              Direction face) {
        return state.getBlock() == other.getBlock() && ConnectivityHandler.isConnected(reader, pos, otherPos);
    }
}
