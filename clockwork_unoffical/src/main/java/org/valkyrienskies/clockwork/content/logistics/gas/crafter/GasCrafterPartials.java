package org.valkyrienskies.clockwork.content.logistics.gas.crafter;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 气体合成器的四段局部模型。
 *
 * <p>对应旧版 {@code ClockworkPartials.kt:97-100} 的
 * {@code GAS_CRAFTER_FRAME / _TUBE / _MESH / _GLOW}，模型文件沿用旧版资源
 * （{@code models/block/gas_crafter/*.json}），无需新增。
 *
 * <p><b>必须在模型烘焙前登记</b>：这四段没有任何 blockstate 引用，只被
 * {@code GasCrafterRenderer} 在首次渲染时才用到；而 Flywheel 只在
 * {@code ModelEvent.RegisterAdditional} 时登记"当时已存在"的 partial，烘焙结束后
 * {@code PartialModel.populateOnInit} 变 true，之后再创建就直接查 {@code ModelManager}，
 * 查不到就是 {@code missingno}（黑紫）。所以由 {@code CWClientSetup} 在
 * {@code FMLClientSetupEvent} 里调 {@link #init()} 强制提前类初始化（机理详见 {@code CWClientSetup}）。
 */
public final class GasCrafterPartials {

    /** 外壳框架（不参与动画）。 */
    public static final PartialModel FRAME = of("frame");
    /** 出气口内管（全亮度绘制）。 */
    public static final PartialModel TUBE = of("tube");
    /** 滤网：合成时沿朝向滑动。 */
    public static final PartialModel MESH = of("mesh");
    /** 指示灯：合成时发光。 */
    public static final PartialModel GLOW = of("glow");

    private GasCrafterPartials() {
    }

    /** 触发静态初始化（由 {@code CWClientSetup} 在客户端启动时调用）。重复调用安全：本方法无副作用。 */
    public static void init() {
    }

    private static PartialModel of(String name) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID,
                "block/gas_crafter/" + name));
    }
}
