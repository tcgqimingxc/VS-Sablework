package org.valkyrienskies.clockwork.content.physicalities.slicker;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 减摩方块用到的局部模型。
 *
 * <p>对应旧版 {@code ClockworkPartials.GOO}：伸出时向前推出去的那团"黏液"。
 * 旧版还有一个 {@code DOINK} 模型，但它挂在 {@code shouldRenderDoink} 分支上而那个分支是空的，
 * 属于死代码，本移植不再引入。
 *
 * <p><b>必须在模型烘焙前登记</b>：黏液没有任何 blockstate 引用，只被
 * {@code SlickerRenderer} 在首次渲染时才用到；而 Flywheel 只在
 * {@code ModelEvent.RegisterAdditional} 时登记"当时已存在"的 partial，烘焙结束后
 * {@code PartialModel.populateOnInit} 变 true，之后再创建就直接查 {@code ModelManager}，
 * 查不到就是 {@code missingno}（黑紫）。所以由 {@code CWClientSetup} 在
 * {@code FMLClientSetupEvent} 里调 {@link #init()} 强制提前类初始化（机理详见 {@code CWClientSetup}）。
 */
public final class SlickerPartials {

    public static final PartialModel GOO = PartialModel.of(
            ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, "block/slicker/goo"));

    private SlickerPartials() {
    }

    /** 触发静态初始化（由 {@code CWClientSetup} 在客户端启动时调用）。重复调用安全：本方法无副作用。 */
    public static void init() {
    }
}
