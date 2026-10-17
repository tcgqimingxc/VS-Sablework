package org.valkyrienskies.clockwork.content.contraptions.flap;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 襟翼轴承的局部模型（只有一块：会摆动的顶盖）。
 *
 * <p>对应旧版 {@code ClockworkPartials.BEARING_TOP_FLAP}
 * （{@code content/contraptions/flap/FlapBearingRenderer.kt:47} 使用）。
 * 模型文件 {@code models/block/flap_bearing/top.json} 随资源迁移过来，无需新增。
 *
 * <p><b>必须在烘焙前登记</b>：{@code blockstates/andesite_flap_bearing.json} 与
 * {@code blockstates/smart_flap_bearing.json} 都只引用各自的 {@code .../block} 模型，
 * 顶盖没有任何 blockstate / 物品模型引用，只被
 * {@link org.valkyrienskies.clockwork.content.contraptions.flap.client.FlapBearingRenderer} 使用；
 * Flywheel 只在 {@code ModelEvent.RegisterAdditional} 时登记当时的 partial，烘焙后再创建即 missingno。
 */
public final class FlapBearingPartials {

    /** 襟翼轴承的顶盖（随输入轴摆动的"转盘"）。 */
    public static final PartialModel TOP = of("top");

    private FlapBearingPartials() {
    }

    /** 触发静态初始化（由 {@code CWClientSetup} 在客户端启动时调用）。重复调用安全：本方法无副作用。 */
    public static void init() {
    }

    private static PartialModel of(String name) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID,
                "block/flap_bearing/" + name));
    }
}
