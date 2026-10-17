package org.valkyrienskies.clockwork.content.logistics.gas.engine;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 燃气机的局部模型。
 *
 * <p>旧版燃气机<b>没有任何渲染器</b>（它的视觉反馈来自上面那台 Create 蒸汽机）。
 * 本移植补一个"内腔发光"层，让玩家能直接看出这台机器在吃热气：
 * {@code models/block/gas_engine/glow.json} 是从 {@code gas_engine/block.json} 的内腔
 * 元素（索引 5）单独抽出来的一个对称立方体，因此<b>不需要按 axis 旋转</b>，纹理复用引擎自己的那张。
 *
 * <p><b>必须在模型烘焙前登记</b>：发光体没有任何 blockstate 引用，只被
 * {@code GasEngineRenderer} 在首次渲染时才用到；而 Flywheel 只在
 * {@code ModelEvent.RegisterAdditional} 时登记"当时已存在"的 partial，烘焙结束后
 * {@code PartialModel.populateOnInit} 变 true，之后再创建就直接查 {@code ModelManager}，
 * 查不到就是 {@code missingno}（黑紫）。所以由 {@code CWClientSetup} 在
 * {@code FMLClientSetupEvent} 里调 {@link #init()} 强制提前类初始化（机理详见 {@code CWClientSetup}）。
 */
public final class GasEnginePartials {

    /** 内腔发光体（对称立方体，绕任意轴旋转外观不变）。 */
    public static final PartialModel GLOW = PartialModel.of(
            ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, "block/gas_engine/glow"));

    private GasEnginePartials() {
    }

    /** 触发静态初始化（由 {@code CWClientSetup} 在客户端启动时调用）。重复调用安全：本方法无副作用。 */
    public static void init() {
    }
}
