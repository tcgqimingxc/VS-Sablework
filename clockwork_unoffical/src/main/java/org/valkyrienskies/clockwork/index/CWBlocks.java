package org.valkyrienskies.clockwork.index;

import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.valkyrienskies.clockwork.ClockworkMod;
import org.valkyrienskies.clockwork.ClockworkRegistrate;
import org.valkyrienskies.clockwork.content.curiosities.WanderliteOreBlock;

/**
 * 方块注册总表。
 *
 * <p>移植说明：
 * <ul>
 *   <li>模型 / 方块状态 / 贴图直接沿用旧版资源（已拷贝到 src/main/resources/assets/vs_clockwork），
 *       所以这里不再调用 Registrate 的 {@code blockstate(...)}，避免与手工资源冲突。</li>
 *   <li>旧版 {@code GenericWanderliteBlock} 的"右键自组装成浮空结构"行为依赖 VS2 的 ShipAssembler，
 *       移植后改用 Sable 的浮空方块机制，放到浮空结构切片里实现。</li>
 * </ul>
 */
@SuppressWarnings("removal") // Registrate 的 addLayer 在当前版本标记为待删除，但仍是生态内通行做法（Simulated/Offroad 同样使用）
public final class CWBlocks {

    private static final ClockworkRegistrate REGISTRATE = ClockworkMod.REGISTRATE;

    // ===================== 资源材料：陨星岩（Nyx）与游荡石（Wanderlite） =====================

    /** 游荡石深板岩矿。 */
    public static final BlockEntry<WanderliteOreBlock> WANDERLITE_DEEPSLATE_ORE = ore("wanderlite_deepslate_ore", false);
    /** 游荡石末地矿。 */
    public static final BlockEntry<WanderliteOreBlock> WANDERLITE_END_ORE = ore("wanderlite_end_ore", false);
    /** 游荡石陨星岩矿（生成在陨石里）。 */
    public static final BlockEntry<WanderliteOreBlock> WANDERLITE_NYX_ORE = ore("wanderlite_nyx_ore", true);

    /** 陨星岩：陨石主体。 */
    public static final BlockEntry<Block> NYX = simple("nyx", true);
    /** 碎陨星岩。 */
    public static final BlockEntry<Block> COBBLED_NYX = simple("cobbled_nyx", true);
    /** 充能陨星岩。 */
    public static final BlockEntry<Block> CHARGED_NYX = simple("charged_nyx", true);

    /** 游荡石块。 */
    public static final BlockEntry<Block> WANDERLITE_BLOCK = simple("wanderlite_block", false);
    /** 雕纹游荡石。 */
    public static final BlockEntry<Block> CHISELED_WANDERLITE = simple("chiseled_wanderlite", false);
    /** 平滑游荡石。 */
    public static final BlockEntry<Block> SMOOTH_WANDERLITE = simple("smooth_wanderlite", false);
    /** 游荡石砖。 */
    public static final BlockEntry<Block> WANDERLITE_BRICKS = simple("wanderlite_bricks", false);

    /** 平滑游荡石台阶（构造时需要基准方块状态，声明顺序不能调换）。 */
    public static final BlockEntry<StairBlock> SMOOTH_WANDERLITE_STAIRS = REGISTRATE
            .block("smooth_wanderlite_stairs", p -> new StairBlock(SMOOTH_WANDERLITE.getDefaultState(), p))
            .initialProperties(SharedProperties::netheriteMetal)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .item()
            .tab(CWCreativeTabs.baseKey())
            .build()
            .register();

    /** 平滑游荡石台阶板。 */
    public static final BlockEntry<SlabBlock> SMOOTH_WANDERLITE_SLAB = REGISTRATE
            .block("smooth_wanderlite_slab", SlabBlock::new)
            .initialProperties(SharedProperties::netheriteMetal)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .item()
            .tab(CWCreativeTabs.baseKey())
            .build()
            .register();

    /** 游荡玻璃：微光、透明、不遮挡视线。 */
    public static final BlockEntry<Block> WANDERGLASS = REGISTRATE
            .block("wanderglass", Block::new)
            .initialProperties(SharedProperties::netheriteMetal)
            .properties(p -> p.noOcclusion().lightLevel(s -> 1).isViewBlocking((s, l, pos) -> false))
            .addLayer(() -> RenderType::cutout)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .item()
            .tab(CWCreativeTabs.baseKey())
            .build()
            .register();

    /**
     * 热气球外壳。
     *
     * <p>TODO(移植)：旧版是 Create 的 {@code CasingBlock} 并注册进 {@code EncasingRegistry}，
     * 需要与 balloon_encased_shaft 成对实现，放到"封装方块"切片里。
     */
    public static final BlockEntry<Block> BALLOON_CASING = simple("balloon_casing", false);

    private CWBlocks() {
    }

    /**
     * 统一的石质方块注册：陨星岩系手感（硬、抗爆）+ 镐子可挖。
     *
     * @param fireResistantItem 该方块的物品形态是否防火（陨星岩系为 true，因为它来自下界/陨石）
     */
    private static BlockEntry<Block> simple(String name, boolean fireResistantItem) {
        var item = REGISTRATE.block(name, Block::new)
                .initialProperties(SharedProperties::netheriteMetal)
                .tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .item();
        if (fireResistantItem) {
            item.properties(Item.Properties::fireResistant);
        }
        return item.tab(CWCreativeTabs.baseKey()).build().register();
    }

    /**
     * 矿石注册：使用 curiosities 切片实现的 {@link WanderliteOreBlock}（旧版行为：挖矿掉经验、右键"游荡"）。
     *
     * @param fireResistantItem 物品形态是否防火（陨星岩系为 true）
     */
    private static BlockEntry<WanderliteOreBlock> ore(String name, boolean fireResistantItem) {
        var item = REGISTRATE.block(name, org.valkyrienskies.clockwork.content.curiosities.WanderliteOreBlock::new)
                .initialProperties(SharedProperties::netheriteMetal)
                .tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .item();
        if (fireResistantItem) {
            item.properties(Item.Properties::fireResistant);
        }
        return item.tab(CWCreativeTabs.baseKey()).build().register();
    }

    /** 触发类加载，保证上面的静态字段被注册（由 {@link ClockworkMod} 调用）。 */
    public static void register() {
    }
}
