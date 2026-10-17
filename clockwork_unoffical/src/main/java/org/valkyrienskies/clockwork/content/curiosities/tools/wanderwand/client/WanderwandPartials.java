package org.valkyrienskies.clockwork.content.curiosities.tools.wanderwand.client;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 游荡法杖的部件模型（旧版 {@code ClockworkPartials.CRYSTAL*} 与 {@code item/wanderwand/ring}）。
 *
 * <p>为什么需要它们：{@code models/item/wanderwand.json} 的父模型 {@code item/wanderwand/item}
 * 只有 8 个元素——那是<b>杖身</b>（与 {@code pole.json} 逐元素相同）。杖头的悬浮水晶
 * （三层：{@code crystal_inner} / {@code crystal} / {@code crystal_outer}）与法阵环
 * （{@code ring}）是各自独立的模型，旧版由 {@code WanderwandItemRenderer} 叠上去。
 * 本移植原先只注册了静态模型，所以游戏里是一根"没有水晶、没有环"的光杆。
 *
 * <p>必须在模型烘焙前由 {@code CWClientSetup} 触发静态初始化（理由见 {@code GravitronPartials}）。
 */
public final class WanderwandPartials {

    /** 法阵环（模型自身在 y=14/16 处，正好在水晶上方）。 */
    public static final PartialModel RING = item("wanderwand/ring");
    /** 水晶主体（中层，旧版用自定 crystal shader 渲染）。 */
    public static final PartialModel CRYSTAL = item("wanderwand/crystal");
    /** 水晶外壳（最大一层，旧版用半透明紫色）。 */
    public static final PartialModel CRYSTAL_OUTER = item("wanderwand/crystal_outer");
    /** 水晶内核（最小一层）。 */
    public static final PartialModel CRYSTAL_INNER = item("wanderwand/crystal_inner");

    private WanderwandPartials() {
    }

    private static PartialModel item(final String path) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, "item/" + path));
    }

    /** 触发静态初始化（由 {@code CWClientSetup} 调用）。重复调用无副作用。 */
    public static void init() {
    }
}
