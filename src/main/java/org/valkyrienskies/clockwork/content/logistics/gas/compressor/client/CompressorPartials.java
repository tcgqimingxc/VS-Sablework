package org.valkyrienskies.clockwork.content.logistics.gas.compressor.client;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 空气压缩机（{@code air_compressor}）的局部模型。
 *
 * <p>对应官方（1.20.1）{@code ClockworkPartials} 里的
 * {@code COMPRESSOR_AXIS} / {@code COMPRESSOR_FABRIC} / {@code COMPRESSOR_TOP}（:105-107），
 * 模型文件随官方资源一起迁到了 {@code assets/vs_clockwork/models/block/compressor/}。
 *
 * <h2>为什么这三块必须由 BER 画</h2>
 * <p>{@code blockstates/air_compressor.json} 只有一个变体，指向 {@code compressor/block}
 * ——那只是<b>底架</b>（11 个 element：四根立柱 + 底板 + 上围栏）。
 * 而 {@code models/block/compressor/item.json} 有 19 个 element，
 * 正好 = block(11) + {@code fabric.json}(2) + {@code top.json}(1) + {@code axis.json}(5)（逐个元素核对过）。
 * 缺的那 8 个 element 里：{@code fabric} 是风箱布、{@code top} 是顶板、{@code axis} 是输入轴扇叶，
 * 三者都要么有动画、要么比方块本体高（顶板在 y=25..28），只能由 BER 画。
 * 所以"只有灰色底板"的根因就是<b>没有 BER</b>，而不是模型/贴图缺失。
 *
 * <p><b>必须在模型烘焙前登记</b>：这三块没有任何 blockstate / 物品模型引用
 * （物品走的是 {@code compressor/item.json} 那份独立资源，不是 PartialModel），
 * 只被 {@code AirCompressorRenderer} 在首次渲染时用到；Flywheel 只在
 * {@code ModelEvent.RegisterAdditional} 时登记"当时已存在"的 partial，烘焙结束后再创建会拿到
 * {@code missingno}（黑紫）。所以由 {@code index/CWClientSetup} 调 {@link #init()} 提前类初始化，
 * 机理详见 {@code CWClientSetup} 的类注释。
 */
public final class CompressorPartials {

    /** 输入轴扇叶（5 个 element，绕方块 Y 轴自转）。 */
    public static final PartialModel AXIS = block("compressor/axis");
    /** 风箱布（2 个 element，随内部气压在 Y 方向伸缩）。 */
    public static final PartialModel FABRIC = block("compressor/fabric");
    /** 顶板（1 个 element，位于 y=25..28，跟着风箱上下平移）。 */
    public static final PartialModel TOP = block("compressor/top");

    private CompressorPartials() {
    }

    /** 触发静态初始化（由 {@code CWClientSetup} 在客户端启动时调用）。重复调用安全：本方法无副作用。 */
    public static void init() {
    }

    private static PartialModel block(String path) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, "block/" + path));
    }
}
