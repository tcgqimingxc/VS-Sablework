package org.valkyrienskies.clockwork.content.curiosities.tools.drill.client;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 手持动力钻的部件模型（旧版 {@code ClockworkPartials.HANDHELD_DRILL_*}）。
 *
 * <p>为什么需要它们：{@code models/item/handheld_drill.json} 的父模型
 * {@code item/handheld_drill/item} 只有 12 个元素（机身），<b>钻头与齿轮是各自独立的模型</b>
 * （{@code head.json} 9 个元素、{@code cog.json} 5 个元素，加上机身才是 {@code full.json} 的 26 个元素）。
 * 旧版靠 {@code HandheldDrillRenderer} 把它们叠上去；本移植原先只注册了静态模型，
 * 结果是"只有机身、没有钻头也没有齿轮"。
 *
 * <p>登记时机同 {@code GravitronPartials}：必须由 {@code index/CWClientSetup}
 * 在 {@code FMLClientSetupEvent} 里提前触发静态初始化，否则 Flywheel 烘焙时它们还不在
 * {@code PartialModel.ALL} 里，只会拿到 missingno（黑紫）。
 */
public final class DrillPartials {

    /** 钻头（旧版按住左键时自转）。 */
    public static final PartialModel BIT = item("handheld_drill/head");
    /** 传动齿轮（跟随 Create 的滚轮值自转）。 */
    public static final PartialModel COG = item("handheld_drill/cog");

    private DrillPartials() {
    }

    private static PartialModel item(final String path) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, "item/" + path));
    }

    /** 触发静态初始化（由 {@code CWClientSetup} 调用）。重复调用无副作用。 */
    public static void init() {
    }
}
