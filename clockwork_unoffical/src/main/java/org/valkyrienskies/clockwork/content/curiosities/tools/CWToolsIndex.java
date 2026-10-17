package org.valkyrienskies.clockwork.content.curiosities.tools;

import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.JukeboxPlayable;
import net.minecraft.world.item.EitherHolder;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tiers;
import org.valkyrienskies.clockwork.ClockworkMod;
import org.valkyrienskies.clockwork.ClockworkRegistrate;
import org.valkyrienskies.clockwork.content.curiosities.tools.drill.HandheldDrillItem;
import org.valkyrienskies.clockwork.content.curiosities.tools.gravitron.CreativeGravitronItem;
import org.valkyrienskies.clockwork.content.curiosities.tools.gravitron.GravitronItem;
import org.valkyrienskies.clockwork.content.curiosities.tools.saw.HandheldSawItem;
import org.valkyrienskies.clockwork.content.curiosities.tools.screwdriver.ScrewdriverItem;
import org.valkyrienskies.clockwork.content.curiosities.tools.wanderwand.WanderwandItem;
import org.valkyrienskies.clockwork.index.CWCreativeTabs;

/**
 * 工具 / 手持件切片注册入口。
 *
 * <p>对应旧版 {@code ClockworkItems.kt} 与 {@code curiosities/tools/**}。
 * 由 {@code CWCuriositiesIndex#register()} 调用。
 *
 * <p>资源沿用旧版 {@code assets/vs_clockwork/models/item/**}。
 * <b>注意</b>：{@code handheld_drill} / {@code handheld_saw} / {@code wanderwand} 的物品模型
 * <b>不是</b>完整的——钻头、锯片、齿轮、杖头水晶与法阵环都是独立模型，旧版由自定义 ItemRenderer
 * 叠上去（{@code AssetLookup.itemModelWithPartials()}）。本移植最初漏了这一步，导致这些物品
 * 少部件；现已由 {@link CWToolsClientSetup} + 各自的 {@code client} 渲染器补齐。
 *
 * <p>与旧版的注册差异：
 * <ul>
 *   <li>{@code music_disc_wanderlust}：1.21.1 已经没有 {@code MusicDiscItem}，改用原版
 *       {@link DataComponents#JUKEBOX_PLAYABLE} 数据组件 + 数据包里的
 *       {@code data/vs_clockwork/jukebox_song/wanderlust.json}（已随本次迁移新增）。</li>
 *   <li>{@code handheld_drill} / {@code handheld_saw}：1.21.1 的工具属性必须写进物品属性组件，
 *       这里用 {@link DiggerItem#createAttributes}（已 javap 核实签名）。</li>
 *   <li>{@code wanderlite_cube} / {@code wanderlite_matrix}：旧版靠自定义 ItemRenderer 渲染旋转立方体，
 *       本移植用现有的游荡水晶贴图补了两个立方体物品模型
 *       （{@code models/item/wanderlite_cube.json} / {@code wanderlite_matrix.json}），
 *       至少不再是缺失模型；旧版的旋转动画与自定义晶体着色器（{@code ClockworkRenderTypes.CRYSTAL}）
 *       依赖本移植尚未引入的 shader/mixin，没有一并移植（详见交付说明）。</li>
 *   <li>旧版 {@code AllTags.AllItemTags.WRENCH}（Create 的扳手标签）与
 *       {@code vs_clockwork:disable_swing_animation} 标签：
 *       前者需要 Create 的 TagKey，后者旧版靠 mixin；本移植用 {@code onEntitySwing} 钩子实现同样的
 *       "不播放挥动动画"，不额外注册标签。</li>
 * </ul>
 */
@SuppressWarnings("removal")
public final class CWToolsIndex {

    private static final ClockworkRegistrate REGISTRATE = ClockworkMod.REGISTRATE;

    /** {@code wanderlust} 唱片对应的原版唱片机曲目（数据包 JSON 见 resources/data/vs_clockwork/jukebox_song）。 */
    private static final ResourceKey<net.minecraft.world.item.JukeboxSong> WANDERLUST_SONG =
            ResourceKey.create(Registries.JUKEBOX_SONG,
                    ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, "wanderlust"));

    /** 游荡法杖。 */
    public static final ItemEntry<WanderwandItem> WANDERWAND = REGISTRATE
            .item("wanderwand", WanderwandItem::new)
            .properties(p -> p.stacksTo(1).rarity(Rarity.UNCOMMON))
            .tab(CWCreativeTabs.baseKey())
            .register();

    /** 半成品游荡法杖（序列装配的中间产物）。 */
    public static final ItemEntry<Item> INCOMPLETE_WANDERWAND = REGISTRATE
            .item("incomplete_wanderwand", Item::new)
            .properties(p -> p.stacksTo(1).rarity(Rarity.UNCOMMON))
            .tab(CWCreativeTabs.baseKey())
            .register();

    /** 重力枪。 */
    public static final ItemEntry<GravitronItem> GRAVITRON = REGISTRATE
            .item("gravitron", GravitronItem::new)
            .properties(p -> p.stacksTo(1).rarity(Rarity.UNCOMMON))
            .tab(CWCreativeTabs.baseKey())
            .register();

    /** 超充重力枪（创造模式道具）。 */
    public static final ItemEntry<CreativeGravitronItem> CREATIVE_GRAVITRON = REGISTRATE
            .item("creative_gravitron", CreativeGravitronItem::new)
            .properties(p -> p.stacksTo(1).rarity(Rarity.RARE))
            .tab(CWCreativeTabs.baseKey())
            .register();

    /** 螺丝刀。 */
    public static final ItemEntry<ScrewdriverItem> SCREWDRIVER = REGISTRATE
            .item("screwdriver", ScrewdriverItem::new)
            .properties(p -> p.stacksTo(1))
            .tab(CWCreativeTabs.baseKey())
            .register();

    /** 手持动力钻：下界合金级镐，3×3 断面挖掘。 */
    public static final ItemEntry<HandheldDrillItem> HANDHELD_DRILL = REGISTRATE
            .item("handheld_drill", p -> new HandheldDrillItem(Tiers.NETHERITE, p))
            .properties(p -> p.stacksTo(1)
                    .durability(2048)
                    .attributes(DiggerItem.createAttributes(Tiers.NETHERITE, 2.0f, -2.8f)))
            .tab(CWCreativeTabs.baseKey())
            .register();

    /** 手持切割锯：下界合金级斧，沿树干连续砍伐。 */
    public static final ItemEntry<HandheldSawItem> HANDHELD_SAW = REGISTRATE
            .item("handheld_saw", p -> new HandheldSawItem(Tiers.NETHERITE, p))
            .properties(p -> p.stacksTo(1)
                    .durability(2048)
                    .attributes(DiggerItem.createAttributes(Tiers.NETHERITE, 5.0f, -3.0f)))
            .tab(CWCreativeTabs.baseKey())
            .register();

    /** 游荡石立方（材料）。 */
    public static final ItemEntry<Item> WANDERLITE_CUBE = REGISTRATE
            .item("wanderlite_cube", Item::new)
            .tab(CWCreativeTabs.baseKey())
            .register();

    /** 游荡石基体（材料）。 */
    public static final ItemEntry<Item> WANDERLITE_MATRIX = REGISTRATE
            .item("wanderlite_matrix", Item::new)
            .tab(CWCreativeTabs.baseKey())
            .register();

    /** 修复过的音乐唱片：播放 {@code vs_clockwork:wanderlust}。 */
    public static final ItemEntry<Item> MUSIC_DISC_WANDERLUST = REGISTRATE
            .item("music_disc_wanderlust", Item::new)
            .properties(p -> p.stacksTo(1)
                    .rarity(Rarity.EPIC)
                    .component(DataComponents.JUKEBOX_PLAYABLE,
                            new JukeboxPlayable(new EitherHolder<>(WANDERLUST_SONG), true)))
            .tab(CWCreativeTabs.baseKey())
            .register();

    private CWToolsIndex() {
    }

    /** 触发类加载（由 {@code CWCuriositiesIndex} 调用）。 */
    public static void register() {
    }
}
