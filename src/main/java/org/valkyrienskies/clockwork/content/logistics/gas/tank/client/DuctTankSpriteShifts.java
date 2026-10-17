package org.valkyrienskies.clockwork.content.logistics.gas.tank.client;

import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import net.minecraft.resources.ResourceLocation;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 气体储罐的 CT（connected texture）贴图映射表。
 *
 * <h2>对应 Create 的哪个文件</h2>
 * {@code com.simibubi.create.AllSpriteShifts} 第 74-76 行：
 * <pre>
 *   FLUID_TANK       = getCT(AllCTTypes.RECTANGLE, "fluid_tank")
 *   FLUID_TANK_TOP   = getCT(AllCTTypes.RECTANGLE, "fluid_tank_top")
 *   FLUID_TANK_INNER = getCT(AllCTTypes.RECTANGLE, "fluid_tank_inner")
 * </pre>
 * 即"每条 CT 映射 = 一张 16×16 基础贴图 + 一张 4×4 格的 64×64 图集"，两者用同名 {@code _connected}
 * 后缀配对。本类把这三条搬成 Clockwork 自己的贴图（{@code vs_clockwork:block/duct_tank*}）。
 *
 * <h2>为什么必须是"基础图 + 图集"两张（而不是只用图集）</h2>
 * CT 的取格发生在<b>烘焙之后</b>：模型的 uv 仍是 16×16 那张基础图的完整 0..16 坐标，
 * {@code CTModel} 把每个面的 uv 换算成"基础图内的 0..1 相对位置"，再加上 (列,行) 偏移重采样到图集上
 * （{@code CTSpriteShiftEntry#getTargetU/getTargetV}，用 javap 核实过的公式见
 * {@link DuctTankModel} 的类注释）。所以：
 * <ul>
 *   <li>模型里写的是 16×16 基础图 —— 决定 uv 坐标系；</li>
 *   <li>实际像素来自 64×64 图集的那一格 —— 决定外观。</li>
 * </ul>
 * 这也是为什么 {@code duct_tank.png} / {@code duct_tank_top.png} 这两张 16×16 静态图<b>仍然必须有</b>
 * （世界里的 4 个方块模型直接引用它们，CT 再把 uv 重采样到图集格），但它们的内容不再决定方块在世界里
 * 长什么样 —— 那由 {@code duct_tank_connected.png} 的图集格子决定。
 *
 * <p><b>物品图标不使用这两张基础图</b>：物品不经过 CT，且基础图是为了平铺从图集裁出来的一格墙，
 * 直接拿来烘 item 会得到没有边框/铆钉的灰盒子。物品模型 {@code models/item/duct_tank.json}
 * 指向 {@code block/duct_tank/block_single_item}（官方 1.20.1 的单块立方体，
 * 直接用下面这两张 64×64 图集的 (0,3) 格），与 Create 用普通 block 模型做 item 是同一思路。
 * 见 {@code docs/GAS-TANK.md}。
 *
 * <h2>与 Create 的差异</h2>
 * 没有 {@code DUCT_TANK_INNER}：内腔面用的是 {@code fluid_tank_inner}，而本工程只有它的 16×16 基础图、
 * 没有 {@code fluid_tank_inner_connected} 图集；且罐体密闭、内腔面在游戏里永不可见（见
 * {@link DuctTankCTBehaviour} 类注释）。Create 有这条映射，这里刻意不注册，避免为一张看不见的贴图
 * 多打包一张图集 —— 不是遗漏。
 */
public final class DuctTankSpriteShifts {

    /** 侧壁 / 盖沿（模型里的 {@code #1}）：{@code duct_tank} → {@code duct_tank_connected}。 */
    public static final CTSpriteShiftEntry DUCT_TANK = rectangle("duct_tank");

    /** 顶面 / 底面（模型里的 {@code #0}）：{@code duct_tank_top} → {@code duct_tank_top_connected}。 */
    public static final CTSpriteShiftEntry DUCT_TANK_TOP = rectangle("duct_tank_top");

    /**
     * 建一条 RECTANGLE 型 CT 映射。
     *
     * <p>为什么是 {@code RECTANGLE}：Create 的储罐用 {@code 4×4} 图集，取格只看"上下左右四个邻居连不连"，
     * 不画内角圆角，正是 {@code AllCTTypes.RECTANGLE} 的语义（{@code sheetSize = 4}，
     * {@code getTextureIndex} 给出 {@code 0..15}）。{@code OMNIROOF}/{@code CROSS} 那些型别会取错格。
     */
    private static CTSpriteShiftEntry rectangle(String base) {
        return CTSpriteShifter.getCT(AllCTTypes.RECTANGLE, id("block/" + base), id("block/" + base + "_connected"));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, path);
    }

    private DuctTankSpriteShifts() {
    }
}
