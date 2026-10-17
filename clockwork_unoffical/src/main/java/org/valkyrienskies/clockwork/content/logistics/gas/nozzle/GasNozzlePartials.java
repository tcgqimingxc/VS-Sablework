package org.valkyrienskies.clockwork.content.logistics.gas.nozzle;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 气嘴的局部模型：表盘（含指针）与内部叶轮轴。
 *
 * <p>对应旧版 {@code ClockworkPartials.NOZZLE_DIAL} / {@code NOZZLE_AXIS}
 * （{@code content/logistics/gas/pockets/nozzle/GasNozzleRenderer.kt:43,65} 使用）。
 * 模型文件随资源迁移过来（{@code models/block/gas_nozzle/{dial,axis}.json}），无需新增。
 *
 * <p><b>必须在烘焙前登记</b>：{@code blockstates/gas_nozzle.json} 只引用 {@code gas_nozzle/block}
 * （喷嘴本体），这两个部件没有任何 blockstate / 物品模型引用，只被
 * {@code client/GasNozzleRenderer} 使用；烘焙后再创建即 missingno（黑紫）。
 */
public final class GasNozzlePartials {

    /** 表盘：旧版按转速转指针，本移植画静态（见渲染器类注释）。 */
    public static final PartialModel DIAL = of("dial");
    /** 内部叶轮轴：旧版按转速转，本移植按 FACING 摆正后静态绘制。 */
    public static final PartialModel AXIS = of("axis");

    private GasNozzlePartials() {
    }

    /** 触发静态初始化（由 {@code CWClientSetup} 在客户端启动时调用）。重复调用安全：本方法无副作用。 */
    public static void init() {
    }

    private static PartialModel of(String name) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID,
                "block/gas_nozzle/" + name));
    }
}
