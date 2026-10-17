package org.valkyrienskies.clockwork.content.physicalities.extendon.client;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 可伸缩杆（{@code extendon}）的局部模型。
 *
 * <p>对应官方（1.20.1）{@code ClockworkPartials} 的
 * {@code EXTENDON_AXIS0} / {@code EXTENDON_AXIS1}（:&nbsp;Extendon 段），
 * 模型文件已随官方资源迁到 {@code assets/vs_clockwork/models/block/extendon/}。
 * 官方的 {@code EXTENDON_HOSE}（{@code extendon/hose}）在渲染器里<b>没有用到</b>
 * （软管是程序化画的四边形带，见 {@link ExtendonRenderer}），所以这里也不声明它
 * —— 声明了没人用就是死代码。
 *
 * <h2>为什么这两块必须由 BER 画（实机"活塞/软管卷轴不显示"的根因）</h2>
 * <p>{@code blockstates/extendon.json} → {@code block/extendon/block}，只有 3 个 element（底座）。
 * 而 {@code models/block/extendon/item.json} 有 13 个 element：
 * Base 3（= block 的 3 个）+ Axis00 4（{@code axis0.json}）+ Hose 3（{@code hose.json}）
 * + Axis01 3（{@code axis1.json}）。也就是说世界里本该还有：
 * <ul>
 *   <li>{@code axis0}——两侧的<b>卷轴支架</b>（y=3..12 的四块侧板），</li>
 *   <li>{@code axis1}——中间的<b>活塞筒</b>（y=4..13 的三段圆柱），</li>
 *   <li>软管本身（由渲染器按两端连线程序化绘制）。</li>
 * </ul>
 * 工程里既没有 {@code .renderer(...)} 也没有 BER，所以世界里只剩一块底板。
 *
 * <p><b>必须在模型烘焙前登记</b>：这两块没有任何 blockstate / 物品模型引用
 * （物品走的是 {@code extendon/item.json} 那份独立资源），只被 {@code ExtendonRenderer}
 * 首次渲染时才用到；Flywheel 只在 {@code ModelEvent.RegisterAdditional} 时登记
 * "当时已存在"的 partial，烘焙后再创建就只能拿到 {@code missingno}（黑紫）。
 * 因此由 {@code index/CWClientSetup} 调 {@link #init()} 提前类初始化，
 * 机理详见 {@code CWClientSetup} 的类注释。
 */
public final class ExtendonPartials {

    /** 两侧的卷轴支架（4 个 element）。 */
    public static final PartialModel AXIS0 = block("extendon/axis0");
    /** 中间的活塞筒（3 个 element）。 */
    public static final PartialModel AXIS1 = block("extendon/axis1");

    private ExtendonPartials() {
    }

    /** 触发静态初始化（由 {@code CWClientSetup} 在客户端启动时调用）。重复调用安全：本方法无副作用。 */
    public static void init() {
    }

    private static PartialModel block(String path) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, "block/" + path));
    }
}
