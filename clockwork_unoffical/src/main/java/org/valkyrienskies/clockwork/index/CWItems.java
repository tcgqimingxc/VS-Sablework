package org.valkyrienskies.clockwork.index;

import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.world.item.Item;
import org.valkyrienskies.clockwork.ClockworkMod;
import org.valkyrienskies.clockwork.ClockworkRegistrate;

/**
 * 物品注册总表。
 *
 * <p>目前只包含材料类物品；工具（游荡法杖 / 重力枪 / 螺丝刀 / 手持锯 / 手持钻）与
 * 护甲（飞行家套装）在各自的内容切片里注册，避免这里变成一个巨型文件。
 */
public final class CWItems {

    private static final ClockworkRegistrate REGISTRATE = ClockworkMod.REGISTRATE;

    /** 游荡水晶：游荡石矿的产物，也是多数 Clockwork 机器的合成材料。 */
    public static final ItemEntry<Item> WANDERLITE_CRYSTAL = REGISTRATE
            .item("wanderlite_crystal", Item::new)
            .tab(CWCreativeTabs.baseKey())
            .register();

    // ===== 序列装配（create:sequenced_assembly）的中间产物 =====
    // 这些物品是配方链的一环，必须注册：Create 解析序列装配时会用它们构造步骤的
    // ingredient 列表，物品不存在会让列表为空并抛 IndexOutOfBoundsException（实机崩过一次）。

    /** 软管卷（未完成）：extendon_hose 配方链的中间产物。 */
    public static final ItemEntry<Item> INCOMPLETE_HOSE_SPOOL = REGISTRATE
            .item("incomplete_hose_spool", Item::new)
            .tab(CWCreativeTabs.gassesKey())
            .register();

    /** 伸缩机构（未完成）：universal_shaft_item 配方链的中间产物。 */
    public static final ItemEntry<Item> INCOMPLETE_TELESCOPING_MECHANISM = REGISTRATE
            .item("incomplete_telescoping_mechanism", Item::new)
            .tab(CWCreativeTabs.physicalitiesKey())
            .register();

    private CWItems() {
    }

    /** 触发类加载（由 {@link ClockworkMod} 调用）。 */
    public static void register() {
    }
}
