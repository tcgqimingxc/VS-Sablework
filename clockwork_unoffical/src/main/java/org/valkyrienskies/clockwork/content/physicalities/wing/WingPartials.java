package org.valkyrienskies.clockwork.content.physicalities.wing;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 机翼 / 襟翼的局部模型（partial model）。
 *
 * <p>对应旧版 {@code ClockworkPartials} 里的 WING_MIDDLE / WING_SIDE / WING_SIDE_VERTICAL。
 * 机翼的连接面（"膜"）单独渲染，才能按玩家染的颜色上色；骨架仍由方块模型负责。
 *
 * <p><b>必须在模型烘焙前登记</b>：Flywheel 的 {@code PartialModel} 只在
 * {@code ModelEvent.RegisterAdditional} 时把当时已存在于 {@code PartialModel.ALL} 里的模型登记去烘焙
 * （javap 反编译 {@code flywheel-neoforge-1.21.1-1.0.6.jar} 的
 * {@code dev.engine_room.flywheel.lib.model.baked.PartialModelEventHandler#onRegisterAdditional}），
 * 而烘焙完成后它会把 {@code PartialModel.populateOnInit} 置为 true，之后创建的 PartialModel 直接从
 * {@code ModelManager} 取模型 —— 这三个模型<b>没有被任何 blockstate 引用</b>，
 * 于是晚登记一律拿到 {@code missingno}（黑紫方块）。所以由
 * {@code CWClientSetup} 在 {@code FMLClientSetupEvent} 里调 {@link #init()} 强制提前类初始化
 * （统一入口，见 {@code org.valkyrienskies.clockwork.index.CWClientSetup}）。
 */
public final class WingPartials {

    /** 朝向正面的翼面。 */
    public static final PartialModel WING_MIDDLE = of("block/wing/wing_middle");
    /** 水平相邻的翼面。 */
    public static final PartialModel WING_SIDE = of("block/wing/wing_side");
    /** 竖直相邻的翼面（FACING 为东西向时用）。 */
    public static final PartialModel WING_SIDE_VERTICAL = of("block/wing/wing_side_vertical");

    private WingPartials() {
    }

    /** 触发静态初始化（由 {@code CWClientSetup} 在客户端启动时调用）。重复调用安全：本方法无副作用。 */
    public static void init() {
    }

    private static PartialModel of(String path) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, path));
    }
}
