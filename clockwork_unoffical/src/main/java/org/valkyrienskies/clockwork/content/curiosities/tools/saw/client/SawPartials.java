package org.valkyrienskies.clockwork.content.curiosities.tools.saw.client;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 手持切割锯的部件模型（旧版 {@code ClockworkPartials.HANDHELD_SAW_*}）。
 *
 * <p>同 {@code DrillPartials}：{@code models/item/handheld_saw.json} 的父模型
 * {@code item/handheld_saw/item} 只有 15 个元素（机身），<b>锯片与齿轮是独立模型</b>
 * （{@code saw.json} 1 个元素、{@code cog.json} 5 个元素，合计才是 {@code full.json} 的 21 个元素）。
 * 旧版靠 {@code HandheldSawRenderer} 叠上去；不还原就只能看到没有锯片的半截锯。
 *
 * <p>必须在模型烘焙前由 {@code CWClientSetup} 触发静态初始化（理由见 {@code GravitronPartials}）。
 */
public final class SawPartials {

    /** 圆锯片（旧版按住左键时绕 X 轴自转）。 */
    public static final PartialModel BUZZSAW = item("handheld_saw/saw");
    /** 传动齿轮（跟随 Create 的滚轮值自转）。 */
    public static final PartialModel COG = item("handheld_saw/cog");

    private SawPartials() {
    }

    private static PartialModel item(final String path) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, "item/" + path));
    }

    /** 触发静态初始化（由 {@code CWClientSetup} 调用）。重复调用无副作用。 */
    public static void init() {
    }
}
