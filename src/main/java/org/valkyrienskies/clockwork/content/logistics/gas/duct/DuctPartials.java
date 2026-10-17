package org.valkyrienskies.clockwork.content.logistics.gas.duct;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 气体导管的局部模型：三个"接口件"，全部按面朝向渲染。
 *
 * <p>对应旧版 {@code ClockworkPartials} 的 {@code DUCT_CONN} / {@code DUCT_LEAK} / {@code DUCT_RIM}
 * （{@code content/logistics/gas/duct/DuctRenderer.kt:41-86} 使用）。
 * 模型文件随资源迁移过来（{@code models/block/duct/{connection,leak,rim}.json}），无需新增。
 *
 * <p><b>必须在烘焙前登记</b>：{@code blockstates/duct.json} 只引用 {@code duct/core}
 * 与 {@code duct/core_straight}（管身），这三个接口件没有任何 blockstate / 物品模型引用，
 * 只被 {@code client/DuctRenderer} 使用；Flywheel 只在 {@code ModelEvent.RegisterAdditional}
 * 时登记当时的 partial，烘焙后再创建即 missingno（黑紫）。
 */
public final class DuctPartials {

    /** 已连通的一面的接口圈。 */
    public static final PartialModel CONNECTION = of("connection");
    /** 破损泄漏面（不导气，滴气）。 */
    public static final PartialModel LEAK = of("leak");
    /** 接到非导管方块（机器）时的收口环。 */
    public static final PartialModel RIM = of("rim");

    private DuctPartials() {
    }

    /** 触发静态初始化（由 {@code CWClientSetup} 在客户端启动时调用）。重复调用安全：本方法无副作用。 */
    public static void init() {
    }

    private static PartialModel of(String name) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID,
                "block/duct/" + name));
    }
}
