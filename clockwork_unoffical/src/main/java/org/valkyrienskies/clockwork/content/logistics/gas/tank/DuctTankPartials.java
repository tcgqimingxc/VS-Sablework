package org.valkyrienskies.clockwork.content.logistics.gas.tank;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 气体储罐的局部模型（"罐内的气体体积"）。
 *
 * <p>对应关系：Create 的流体储罐用 {@code FluidTankRenderer}（BER）把液体画成一个随液面高度
 * 变化的盒子（{@code catnip} 的 {@code FluidRenderHelper.renderFluidBox}，见
 * {@code com.simibubi.create.content.fluids.tank.FluidTankRenderer#renderSafe}）。
 * 本模组的"气体"不是流体，没有对应的 {@code Fluid}，所以这里改用一个
 * <b>可染色 + 可缩放的立方体局部模型</b>来表达同一件事（细节与差异见
 * {@link org.valkyrienskies.clockwork.content.logistics.gas.tank.client.DuctTankRenderer}）。
 *
 * <p>{@code models/block/duct_tank/gas_volume.json} 与贴图 {@code block/duct_tank_gas.png}
 * 由 {@code tools/gen_duct_tank_assets.py} 生成（贴图是纯白 16x16，颜色完全由渲染器给，
 * 因此新增气体种类不需要新贴图）。
 *
 * <p><b>必须在模型烘焙前登记</b>：本模型没有任何 blockstate / 物品模型引用它，只被
 * {@code DuctTankRenderer} 在首次渲染时才用到；而 Flywheel 只在 {@code ModelEvent.RegisterAdditional}
 * 时登记"当时已存在"的 partial，烘焙结束后 {@code PartialModel.populateOnInit} 变 true，
 * 之后再创建就直接查 {@code ModelManager}，查不到就是 {@code missingno}（黑紫）。
 * 所以由 {@link org.valkyrienskies.clockwork.index.CWClientSetup} 在 {@code FMLClientSetupEvent}
 * 里调 {@link #init()} 强制提前类初始化（机理详见 {@code CWClientSetup} 的类注释）。
 */
public final class DuctTankPartials {

    /** 罐内气体体积：以 (0,0,0)-(16,16,16) 为单位的立方体，渲染时按罐体尺寸缩放并染色。 */
    public static final PartialModel GAS_VOLUME = PartialModel.of(
            ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, "block/duct_tank/gas_volume"));

    private DuctTankPartials() {
    }

    /** 触发静态初始化（由 {@code CWClientSetup} 在客户端启动时调用）。重复调用安全：本方法无副作用。 */
    public static void init() {
    }
}
