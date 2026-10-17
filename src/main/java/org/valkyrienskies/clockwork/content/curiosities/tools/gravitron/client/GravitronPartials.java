package org.valkyrienskies.clockwork.content.curiosities.tools.gravitron.client;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 重力枪的部件模型（旧版 {@code ClockworkPartials} 的 GRAV_* 部分）。
 *
 * <p>为什么需要它们：物品模型 {@code models/item/gravitron.json} 的父模型
 * {@code gravitronbase} 只有 29 个元素（壳体），爪子和表盘指针分别放在
 * {@code models/item/gravitron/prong*.json} / {@code dialhand.json} 里，
 * 旧版靠 {@code GravitronItemRenderer} 逐个画上去。没有渲染器时它们根本不会出现，
 * 于是游戏里只能看到半截枪身——这就是"模型显示不全"的原因。
 *
 * <p>模型注册交给 Flywheel：{@code PartialModel.of} 会把模型塞进 {@code PartialModel.ALL}，
 * Flywheel 自己的 {@code PartialModelEventHandler} 在 {@code ModelEvent.RegisterAdditional} 时
 * 把它们加进烘焙队列。因此必须在模型烘焙之前触发本类的静态初始化
 * （统一由 {@code org.valkyrienskies.clockwork.index.CWClientSetup} 在
 * {@code FMLClientSetupEvent} 里调用）。
 */
public final class GravitronPartials {

    public static final PartialModel DIAL_HAND = item("gravitron/dialhand");
    public static final PartialModel PRONG_TOP_ONE = item("gravitron/prongtopone");
    public static final PartialModel PRONG_TOP_TWO = item("gravitron/prongtoptwo");
    public static final PartialModel PRONG_TOP_THREE = item("gravitron/prongtopthree");
    public static final PartialModel PRONG_LEFT_ONE = item("gravitron/prongleftone");
    public static final PartialModel PRONG_LEFT_TWO = item("gravitron/pronglefttwo");
    public static final PartialModel PRONG_LEFT_THREE = item("gravitron/prongleftthree");
    public static final PartialModel PRONG_RIGHT_ONE = item("gravitron/prongrightone");
    public static final PartialModel PRONG_RIGHT_TWO = item("gravitron/prongrighttwo");
    public static final PartialModel PRONG_RIGHT_THREE = item("gravitron/prongrightthree");
    /** 超充重力枪的充能光效（只有创造模式那把会画）。 */
    public static final PartialModel OVERLOAD_FX = item("gravitron/overload_fx");

    private GravitronPartials() {
    }

    private static PartialModel item(final String path) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, "item/" + path));
    }

    /** 触发静态初始化（由 {@code CWClientSetup} 在客户端启动时调用）。重复调用安全：本方法无副作用。 */
    public static void init() {
    }
}
