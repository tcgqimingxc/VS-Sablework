package org.valkyrienskies.clockwork.content.curiosities.altmeter;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 高度计的局部模型（只有一块：表盘上的红石指示条）。
 *
 * <p>对应旧版 {@code ClockworkPartials.ALTIMETER_REDSTONE}
 * （{@code content/curiosities/altmeter/AltMeterRenderer.kt:26} 使用）。
 * 模型文件 {@code models/block/alt_meter/redstone.json} 随资源迁移过来，无需新增。
 *
 * <p><b>必须在烘焙前登记</b>：该模型没有任何 blockstate / 物品模型引用它
 * （{@code blockstates/alt_meter.json} 只引用 {@code block/alt_meter/block}），
 * 只被 {@link org.valkyrienskies.clockwork.content.curiosities.altmeter.client.AltMeterRenderer} 使用；
 * 而 Flywheel 只在 {@code ModelEvent.RegisterAdditional} 时登记"当时已存在"的 partial，烘焙后再创建就是
 * missingno（黑紫）。所以由 {@code index/CWClientSetup} 调 {@link #init()} 强制提前类初始化。
 */
public final class AltMeterPartials {

    /** 表盘指示条：颜色随红石强度在暗灰 → 白之间插值。 */
    public static final PartialModel REDSTONE = of("redstone");

    private AltMeterPartials() {
    }

    /** 触发静态初始化（由 {@code CWClientSetup} 在客户端启动时调用）。重复调用安全：本方法无副作用。 */
    public static void init() {
    }

    private static PartialModel of(String name) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID,
                "block/alt_meter/" + name));
    }
}
