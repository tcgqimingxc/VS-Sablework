package org.valkyrienskies.clockwork.content.logistics.gas.duct;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 气体导管的局部模型：三个"接口件" + 四个"边类型件"，全部按面朝向渲染。
 *
 * <p>对应旧版 {@code ClockworkPartials} 的 {@code DUCT_CONN / DUCT_LEAK / DUCT_RIM /
 * DUCT_ONEWAY_FORWARD / DUCT_ONEWAY_BACKWARD / DUCT_SMART / DUCT_COPPER}
 * （{@code ClockworkPartials.kt:88-91}，使用方 {@code content/logistics/gas/duct/DuctRenderer.kt:33-73}）。
 * 模型文件随资源迁移过来（{@code models/block/duct/{connection,leak,rim}.json} 与
 * {@code models/block/duct/custom/*.json}），无需新增。
 *
 * <p><b>注意旧版的一处命名错位（照抄，不"修"）</b>：旧版把 {@code FILTERED} 边画成
 * {@code smart} 模型、把 {@code SMART} 边画成 {@code copper} 模型
 * （{@code DuctRenderer.kt:60-61}）。本移植按"边类型 → 旧版同一张模型"照抄：
 * {@link #EDGE_FILTERED} 指向 {@code custom/smart}，{@link #EDGE_SMART} 指向 {@code custom/copper}。
 * 这样从旧版存档/截图过来的人看到的外观完全一致；"铜 = 智能边"也正是旧版对铜的<b>唯一</b>用法
 * （旧版没有任何 copper 边类型或 copper 方块，见 {@code docs/DUCT-EDGE-VARIANTS.md} §1.5）。
 *
 * <p><b>必须在烘焙前登记</b>：这些模型没有被任何 blockstate / 物品模型引用，
 * 只被 {@code client/DuctRenderer} 使用；Flywheel 只在 {@code ModelEvent.RegisterAdditional}
 * 时登记当时的 partial，烘焙后再创建即 missingno（黑紫）。见 {@code CWClientSetup}。
 */
public final class DuctPartials {

    /** 已连通的一面的接口圈。 */
    public static final PartialModel CONNECTION = of("connection");
    /** 破损泄漏面（不导气，滴气）。 */
    public static final PartialModel LEAK = of("leak");
    /** 接到非导管方块（机器）时的收口环。 */
    public static final PartialModel RIM = of("rim");

    /** 单向外形·箭头朝坐标轴负方向（旧版 {@code DUCT_ONEWAY_FORWARD}）。 */
    public static final PartialModel EDGE_ONEWAY_FORWARD = custom("oneway_forward");
    /** 单向外形·箭头朝坐标轴正方向（旧版 {@code DUCT_ONEWAY_BACKWARD}）。 */
    public static final PartialModel EDGE_ONEWAY_BACKWARD = custom("oneway_backward");
    /** 过滤外形（旧版 {@code DUCT_SMART}，文件就是 {@code custom/smart}）。 */
    public static final PartialModel EDGE_FILTERED = custom("smart");
    /** 智能外形（旧版 {@code DUCT_COPPER}，文件就是 {@code custom/copper}）。 */
    public static final PartialModel EDGE_SMART = custom("copper");

    private DuctPartials() {
    }

    /** 触发静态初始化（由 {@code CWClientSetup} 在客户端启动时调用）。重复调用安全：本方法无副作用。 */
    public static void init() {
    }

    private static PartialModel of(String name) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID,
                "block/duct/" + name));
    }

    private static PartialModel custom(String name) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID,
                "block/duct/custom/" + name));
    }
}
