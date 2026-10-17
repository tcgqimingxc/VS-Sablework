package org.valkyrienskies.clockwork.content.contraptions.propeller.blades;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 叶片的三段局部模型（窄叶 / 宽叶各一套）。
 *
 * <p>对应旧版 {@code ClockworkPartials.kt:41-46} 的
 * {@code BLADE_BASE / BLADE_EXTENSION / BLADE_TIP} 与 {@code WIDEBLADE_*}。
 * 模型文件本来就随资源迁移过来了（{@code models/block/blade_controller/blade/*.json}），无需新增资源。
 *
 * <p><b>必须在模型烘焙前登记</b>：这六个模型没有任何 blockstate / 物品模型引用它们
 * （已逐个核对 {@code assets/vs_clockwork/blockstates} 与 {@code models}），只被
 * {@code BladeControllerRenderer} 在首次渲染时才用到；而 Flywheel 只在
 * {@code ModelEvent.RegisterAdditional} 时登记"当时已存在"的 partial，烘焙结束后
 * {@code PartialModel.populateOnInit} 变 true，之后再创建就直接查 {@code ModelManager}，
 * 查不到就是 {@code missingno}（黑紫）。所以由 {@code CWClientSetup} 在
 * {@code FMLClientSetupEvent} 里调 {@link #init()} 强制提前类初始化（机理详见 {@code CWClientSetup}）。
 */
public final class BladePartials {

    /** 靠近桨毂的一段。 */
    public static final PartialModel BLADE_BASE = of("blade_base");
    /** 中段：按展长沿叶片轴向拉伸。 */
    public static final PartialModel BLADE_EXTENSION = of("blade_extension");
    /** 叶尖。 */
    public static final PartialModel BLADE_TIP = of("blade_tip");
    /** 宽叶版的同一组三段。 */
    public static final PartialModel WIDEBLADE_BASE = of("wideblade_base");
    public static final PartialModel WIDEBLADE_EXTENSION = of("wideblade_extension");
    public static final PartialModel WIDEBLADE_TIP = of("wideblade_tip");

    private BladePartials() {
    }

    /** 触发静态初始化（由 {@code CWClientSetup} 在客户端启动时调用）。重复调用安全：本方法无副作用。 */
    public static void init() {
    }

    private static PartialModel of(String name) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID,
                "block/blade_controller/blade/" + name));
    }
}
