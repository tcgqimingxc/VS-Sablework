package org.valkyrienskies.clockwork.content.logistics.solid.delivery.cannon;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 交付加农炮的四段可动模型（炮座 / 底座 / 炮管 / 天线）。
 *
 * <p>对应旧版 {@code ClockworkPartials.CANNON_MOUNT / CANNON_BASE / CANNON_BARREL / CANNON_ANTENNA}
 * （{@code content/logistics/solid/delivery/cannon/DeliveryCannonRenderer.kt:41-44} 使用）。
 * 模型文件随资源迁移过来（{@code models/block/delivery_cannon/*.json}），无需新增。
 *
 * <p><b>必须在烘焙前登记</b>：{@code blockstates/delivery_cannon.json} 只引用
 * {@code delivery_cannon/block}（三段底座），这四个部件没有任何 blockstate / 物品模型引用，
 * 只被 {@code client/DeliveryCannonRenderer} 使用；烘焙后再创建即 missingno（黑紫）。
 */
public final class DeliveryCannonPartials {

    /** 炮座（y 4..17）。 */
    public static final PartialModel MOUNT = of("mount");
    /** 转台底座（y 9..22）。 */
    public static final PartialModel BASE = of("cannon_base");
    /** 炮管（z -12..2，朝 -Z 伸出）。 */
    public static final PartialModel BARREL = of("cannon_barrel");
    /** 天线（顶部 y 21..27）。 */
    public static final PartialModel ANTENNA = of("antenna");

    private DeliveryCannonPartials() {
    }

    /** 触发静态初始化（由 {@code CWClientSetup} 在客户端启动时调用）。重复调用安全：本方法无副作用。 */
    public static void init() {
    }

    private static PartialModel of(String name) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID,
                "block/delivery_cannon/" + name));
    }
}
