package org.valkyrienskies.clockwork.content.curiosities.food;

import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import org.valkyrienskies.clockwork.ClockworkMod;
import org.valkyrienskies.clockwork.ClockworkRegistrate;
import org.valkyrienskies.clockwork.index.CWCreativeTabs;

/**
 * 食物 / 杂项物品切片注册入口。
 *
 * <p><b>与旧版的关系（重要）</b>：{@code bluperglue}、{@code bluuguu}、{@code pastrymaker}、
 * {@code stratodonut} 在旧版 1.20.1 的 Kotlin 代码里**完全不存在** —— 只有模型、
 * 贴图和 {@code lang/en_us.json} 里的名字（应该是更早版本的遗留资源）。
 * 因此本切片只能"按名字与模型给出最小可用定义"，这些数值都是新定的，不是照抄：
 * <ul>
 *   <li>{@code bluuguu}（Bluu Guu）：食物，饱食度 4 / 饱和度系数 0.4；</li>
 *   <li>{@code stratodonut}（Stratodonut）：食物，饱食度 6 / 饱和度系数 0.6；</li>
 *   <li>{@code bluperglue}（Bluperglue）：普通材料物品（旧版连"粘合"的代码都没有，
 *       这里不做任何假功能）；</li>
 *   <li>{@code pastrymaker}（Pastrymaker）：普通手持物品（stacksTo(1)）。它的模型带
 *       leftcannon / pressuretank / rightcannon 三个子模型，看起来是"糕点发射器"，
 *       但旧版没有任何对应代码，因此不实现任何行为。</li>
 * </ul>
 * 如果以后要给它俩补玩法，这几个字段就是改成对应物品类的入口。
 *
 * <p>{@code *_frosting*}（三种糖霜）在资源里是**流体**（{@code fluid.vs_clockwork.*_bucket}），
 * 需要流体切片（Registrate 的 fluid 注册 + 流体类型 + 桶）才能落地，本切片不涉及。
 */
public final class CWFoodIndex {

    private static final ClockworkRegistrate REGISTRATE = ClockworkMod.REGISTRATE;

    // ===================== 食物 =====================

    /** 蓝咕咕。 */
    public static final ItemEntry<Item> BLUUGUU = REGISTRATE
            .item("bluuguu", Item::new)
            .properties(p -> p.food(new FoodProperties.Builder()
                    .nutrition(4)
                    .saturationModifier(0.4f)
                    .build()))
            .tab(CWCreativeTabs.baseKey())
            .register();

    /** 层云甜甜圈。 */
    public static final ItemEntry<Item> STRATODONUT = REGISTRATE
            .item("stratodonut", Item::new)
            .properties(p -> p.food(new FoodProperties.Builder()
                    .nutrition(6)
                    .saturationModifier(0.6f)
                    .build()))
            .tab(CWCreativeTabs.baseKey())
            .register();

    // ===================== 杂项 =====================

    /** 蓝格露（材料）。 */
    public static final ItemEntry<Item> BLUPERGLUE = REGISTRATE
            .item("bluperglue", Item::new)
            .tab(CWCreativeTabs.baseKey())
            .register();

    /** 糕点制造机（手持件，旧版无行为）。 */
    public static final ItemEntry<Item> PASTRYMAKER = REGISTRATE
            .item("pastrymaker", Item::new)
            .properties(p -> p.stacksTo(1))
            .tab(CWCreativeTabs.baseKey())
            .register();

    private CWFoodIndex() {
    }

    /** 触发类加载（由 {@code CWCuriositiesIndex} 调用）。 */
    public static void register() {
    }
}
