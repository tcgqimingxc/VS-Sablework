package org.valkyrienskies.clockwork.content.curiosities.clock.client;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 落地钟的局部模型。
 *
 * <p>对应旧版 {@code ClockRenderer.kt} 里直接 {@code new PartialModel(...)} 的三根指针。
 * 1.21.1（Flywheel 1.0）改用 {@link PartialModel#of(ResourceLocation)} 的静态工厂。
 * 模型文件本来就在 {@code assets/vs_clockwork/models/block/clock/} 下。
 *
 * <p><b>必须在模型烘焙前登记</b>：这三根指针没有任何 blockstate / 物品模型引用
 * （钟面与钟体走普通方块模型，指针是 BER 单独画的），只被 {@code ClockRenderer} 在首次渲染时才用到；
 * 而 Flywheel 只在 {@code ModelEvent.RegisterAdditional} 时登记"当时已存在"的 partial，
 * 烘焙结束后 {@code PartialModel.populateOnInit} 变 true，之后再创建就直接查 {@code ModelManager}，
 * 查不到就是 {@code missingno}（黑紫）。所以由 {@code CWClientSetup} 在
 * {@code FMLClientSetupEvent} 里调 {@link #init()} 强制提前类初始化（机理详见 {@code CWClientSetup}）。
 */
public final class ClockPartialModels {

    public static final PartialModel HOUR_HAND = block("clock/hand_hour");
    public static final PartialModel MINUTE_HAND = block("clock/hand_minute");
    public static final PartialModel SECOND_HAND = block("clock/hand_second");

    private ClockPartialModels() {
    }

    /** 触发静态初始化（由 {@code CWClientSetup} 在客户端启动时调用）。重复调用安全：本方法无副作用。 */
    public static void init() {
    }

    private static PartialModel block(String path) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, "block/" + path));
    }
}
