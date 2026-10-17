package org.valkyrienskies.clockwork.content.logistics.gas.valve;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 气体阀门的局部模型（只有一块：开度指针）。
 *
 * <p>对应旧版 {@code ClockworkPartials.VALVE_DUCT_POINTER}
 * （{@code content/logistics/gas/valve/ValveDuctRenderer.kt:37} 使用）。
 * 模型文件 {@code models/block/valve_duct/pointer.json} 随资源迁移过来，无需新增。
 *
 * <p><b>必须在烘焙前登记</b>：{@code blockstates/valve_duct.json} 只引用
 * {@code valve_duct/block_horizontal} 与 {@code block_vertical}（阀体），指针没有任何
 * blockstate / 物品模型引用，只被 {@code client/ValveDuctRenderer} 使用；烘焙后再创建即 missingno。
 */
public final class ValveDuctPartials {

    /** 阀顶的开度指针（随 {@code openness} 从 0° 转到 -90°）。 */
    public static final PartialModel POINTER = of("pointer");

    private ValveDuctPartials() {
    }

    /** 触发静态初始化（由 {@code CWClientSetup} 在客户端启动时调用）。重复调用安全：本方法无副作用。 */
    public static void init() {
    }

    private static PartialModel of(String name) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID,
                "block/valve_duct/" + name));
    }
}
