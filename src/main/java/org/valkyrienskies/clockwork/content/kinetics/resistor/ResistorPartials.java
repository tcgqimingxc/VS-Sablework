package org.valkyrienskies.clockwork.content.kinetics.resistor;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 红石电阻用到的局部模型（partial model）。
 *
 * <p>对应旧版 {@code ClockworkPartials} 里的 {@code RESISTOR_INDICATOR} 一项。
 * 局部模型的好处是可以单独染色、单独旋转，方块本体仍走普通模型渲染。
 *
 * <p><b>必须在模型烘焙前登记</b>：指示条没有任何 blockstate 引用（方块本体走普通模型，
 * 指示条由 {@code RedstoneResistorRenderer} 单独画），而 Flywheel 只在
 * {@code ModelEvent.RegisterAdditional} 时登记"当时已存在"的 partial，烘焙结束后
 * {@code PartialModel.populateOnInit} 变 true，之后再创建就直接查 {@code ModelManager}，
 * 查不到就是 {@code missingno}（黑紫）。所以由 {@code CWClientSetup} 在
 * {@code FMLClientSetupEvent} 里调 {@link #init()} 强制提前类初始化（机理详见 {@code CWClientSetup}）。
 */
public final class ResistorPartials {

    /** 电阻上盖的红色指示条，颜色随红石强度变化。 */
    public static final PartialModel RESISTOR_INDICATOR = PartialModel.of(
            ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, "block/redstone_resistor/resistorindicator"));

    private ResistorPartials() {
    }

    /** 触发静态初始化（由 {@code CWClientSetup} 在客户端启动时调用）。重复调用安全：本方法无副作用。 */
    public static void init() {
    }
}
