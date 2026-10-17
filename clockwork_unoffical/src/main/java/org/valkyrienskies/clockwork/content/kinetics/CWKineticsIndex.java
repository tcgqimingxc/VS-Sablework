package org.valkyrienskies.clockwork.content.kinetics;

import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.util.entry.EntityEntry;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Blocks;
import org.valkyrienskies.clockwork.ClockworkMod;
import org.valkyrienskies.clockwork.ClockworkRegistrate;
import org.valkyrienskies.clockwork.content.kinetics.resistor.RedstoneResistorBlock;
import org.valkyrienskies.clockwork.content.kinetics.resistor.RedstoneResistorBlockEntity;
import org.valkyrienskies.clockwork.content.kinetics.resistor.client.RedstoneResistorRenderer;
import org.valkyrienskies.clockwork.content.kinetics.sequenced_seat.CommandSeatBlock;
import org.valkyrienskies.clockwork.content.kinetics.sequenced_seat.CommandSeatBlockEntity;
import org.valkyrienskies.clockwork.content.kinetics.sequenced_seat.CommandSeatEntity;
import org.valkyrienskies.clockwork.content.kinetics.sequenced_seat.client.CommandSeatRenderer;
import org.valkyrienskies.clockwork.content.kinetics.universal_shaft.UniversalShaftBlock;
import org.valkyrienskies.clockwork.content.kinetics.universal_shaft.UniversalShaftBlockEntity;
import org.valkyrienskies.clockwork.content.kinetics.universal_shaft.UniversalShaftItem;
import org.valkyrienskies.clockwork.content.kinetics.universal_shaft.client.UniversalShaftRenderer;
import org.valkyrienskies.clockwork.content.physicalities.extendon.ExtendonBlock;
import org.valkyrienskies.clockwork.content.physicalities.extendon.ExtendonBlockEntity;
import org.valkyrienskies.clockwork.content.physicalities.extendon.ExtendonHoseItem;
import org.valkyrienskies.clockwork.content.physicalities.extendon.client.ExtendonRenderer;
import org.valkyrienskies.clockwork.content.physicalities.goo.GooBlock;
import org.valkyrienskies.clockwork.content.physicalities.goo.GooBlockEntity;
import org.valkyrienskies.clockwork.content.physicalities.slicker.SlickerBlock;
import org.valkyrienskies.clockwork.content.physicalities.slicker.SlickerBlockEntity;
import org.valkyrienskies.clockwork.content.physicalities.slicker.client.SlickerRenderer;
import org.valkyrienskies.clockwork.content.physicalities.spinoff_bearing.SpinoffBearingBlock;
import org.valkyrienskies.clockwork.content.physicalities.spinoff_bearing.SpinoffBearingBlockEntity;
import org.valkyrienskies.clockwork.content.physicalities.wing.DyedWingBlockItem;
import org.valkyrienskies.clockwork.content.physicalities.wing.FlapBlock;
import org.valkyrienskies.clockwork.content.physicalities.wing.WingBlock;
import org.valkyrienskies.clockwork.content.physicalities.wing.WingColorBlockEntity;
import org.valkyrienskies.clockwork.content.physicalities.wing.client.WingColorRenderer;
import org.valkyrienskies.clockwork.index.CWCreativeTabs;

/**
 * 通用动力部件（kinetics 切片）的注册入口。
 *
 * <p>全限定类名：{@code org.valkyrienskies.clockwork.content.kinetics.CWKineticsIndex}
 * ——Lead 在 {@code ClockworkMod} 里调用 {@link #register()} 即可。
 *
 * <p>本类同时负责 {@code content/kinetics/**} 与 {@code content/physicalities/**}
 * （slicker / goo / extendon / wing / flap / spinoff_bearing）以及 {@code command_seat} 的全部内容注册。
 * 仍未实现的是 {@code balloon_encased_shaft}（需要 EncasingRegistry 集成）。
 *
 * <p>约定：
 * <ul>
 *   <li>模型 / 方块状态 / 贴图直接复用 {@code assets/vs_clockwork/**}，所以一律不调 {@code .blockstate(...)}；</li>
 *   <li>应力值：这些方块都不产生也不消耗应力（Create 的默认值就是 0），因此不注册 {@code BlockStressValues}；</li>
 *   <li>创造标签：动力部件（轴 / 电阻）进 {@link CWCreativeTabs#baseKey()}，
 *       物理性方块（机翼 / 减摩 / 黏液 / 伸缩杆 / 轴承）进 {@link CWCreativeTabs#physicalitiesKey()}。</li>
 * </ul>
 */
@SuppressWarnings("removal") // Registrate 的 .renderer/.addLayer 在当前版本标记待删除，但生态内通行（Simulated/Offroad 同样使用）
public final class CWKineticsIndex {

    private static final ClockworkRegistrate REGISTRATE = ClockworkMod.REGISTRATE;

    // ============================== 通用轴（universal_shaft） ==============================

    public static final BlockEntry<UniversalShaftBlock> UNIVERSAL_SHAFT = REGISTRATE
            .block("universal_shaft", UniversalShaftBlock::new)
            .initialProperties(SharedProperties::stone)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutout)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .item()
            .tab(CWCreativeTabs.baseKey())
            .build()
            .register();

    /** 「可压缩轴」：通用轴的连接器，用一次消耗一个。 */
    public static final ItemEntry<UniversalShaftItem> UNIVERSAL_SHAFT_ITEM = REGISTRATE
            .item("universal_shaft_item", UniversalShaftItem::new)
            .tab(CWCreativeTabs.baseKey())
            .register();

    public static final BlockEntityEntry<UniversalShaftBlockEntity> UNIVERSAL_SHAFT_BE = REGISTRATE
            .blockEntity("universal_shaft", UniversalShaftBlockEntity::new)
            .validBlocks(UNIVERSAL_SHAFT)
            .renderer(() -> UniversalShaftRenderer::new)
            .register();

    // ============================== 红石电阻（redstone_resistor） ==============================

    public static final BlockEntry<RedstoneResistorBlock> REDSTONE_RESISTOR = REGISTRATE
            .block("redstone_resistor", RedstoneResistorBlock::new)
            .initialProperties(SharedProperties::stone)
            .properties(p -> p.noOcclusion())
            .tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .item()
            .tab(CWCreativeTabs.baseKey())
            .build()
            .register();

    public static final BlockEntityEntry<RedstoneResistorBlockEntity> REDSTONE_RESISTOR_BE = REGISTRATE
            .blockEntity("redstone_resistor", RedstoneResistorBlockEntity::new)
            .validBlocks(REDSTONE_RESISTOR)
            .renderer(() -> RedstoneResistorRenderer::new)
            .register();

    // ============================== 机翼 / 襟翼（wing / flap） ==============================

    public static final BlockEntry<WingBlock> WING = REGISTRATE
            .block("wing", WingBlock::new)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutoutMipped)
            .tag(BlockTags.MINEABLE_WITH_AXE)
            .item(DyedWingBlockItem::new)
            .tab(CWCreativeTabs.physicalitiesKey())
            .build()
            .register();

    public static final BlockEntry<FlapBlock> FLAP = REGISTRATE
            .block("flap", FlapBlock::new)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutoutMipped)
            .tag(BlockTags.MINEABLE_WITH_AXE)
            .item(DyedWingBlockItem::new)
            .tab(CWCreativeTabs.physicalitiesKey())
            .build()
            .register();

    /** 机翼与襟翼共用一个"颜色"方块实体（旧版也是共用 COLOR_BLOCK_ENTITY）。 */
    public static final BlockEntityEntry<WingColorBlockEntity> WING_COLOR_BE = REGISTRATE
            .blockEntity("wing_color", WingColorBlockEntity::new)
            .validBlocks(WING, FLAP)
            .renderer(() -> WingColorRenderer::new)
            .register();

    // ============================== 黏液块（goo_block） ==============================

    /** 属性直接继承蜂蜜块（旧版同样用 {@code Blocks.HONEY_BLOCK}）。 */
    public static final BlockEntry<GooBlock> GOO_BLOCK = REGISTRATE
            .block("goo_block", GooBlock::new)
            .initialProperties(() -> Blocks.HONEY_BLOCK)
            .addLayer(() -> RenderType::translucent)
            .item()
            .tab(CWCreativeTabs.physicalitiesKey())
            .build()
            .register();

    public static final BlockEntityEntry<GooBlockEntity> GOO_BLOCK_BE = REGISTRATE
            .blockEntity("goo_block", GooBlockEntity::new)
            .validBlocks(GOO_BLOCK)
            .register();

    // ============================== 减摩方块（slicker） ==============================

    public static final BlockEntry<SlickerBlock> SLICKER = REGISTRATE
            .block("slicker", SlickerBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::translucent)
            .item()
            .tab(CWCreativeTabs.physicalitiesKey())
            .build()
            .register();

    public static final BlockEntityEntry<SlickerBlockEntity> SLICKER_BE = REGISTRATE
            .blockEntity("slicker", SlickerBlockEntity::new)
            .validBlocks(SLICKER)
            .renderer(() -> SlickerRenderer::new)
            .register();

    // ============================== 可伸缩杆（extendon） ==============================

    public static final BlockEntry<ExtendonBlock> EXTENDON = REGISTRATE
            .block("extendon", ExtendonBlock::new)
            .initialProperties(SharedProperties::netheriteMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutout)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .item()
            .tab(CWCreativeTabs.gassesKey())
            .build()
            .register();

    /** 「软管卷」：伸缩杆的连接器，用一次消耗一个。 */
    public static final ItemEntry<ExtendonHoseItem> EXTENDON_HOSE = REGISTRATE
            .item("extendon_hose", ExtendonHoseItem::new)
            .tab(CWCreativeTabs.gassesKey())
            .register();

    public static final BlockEntityEntry<ExtendonBlockEntity> EXTENDON_BE = REGISTRATE
            .blockEntity("extendon", ExtendonBlockEntity::new)
            .validBlocks(EXTENDON)
            // 方块模型只有底座；卷轴支架 / 活塞筒 / 软管都由 BER 画
            //（对应官方 ExtendonRenderer，partial 见 ExtendonPartials）
            .renderer(() -> ExtendonRenderer::new)
            .register();

    // ============================== 对接口 / 惰性轴承（spinoff_bearing） ==============================

    /**
     * 对接口：两个面对面放置后形成自由铰链（转动照传、不施加力）。
     *
     * <p>旧版把它放在 {@code content/physicalities/spinoff_bearing}，按 PORT-CONVENTIONS
     * 由本注册入口（kinetics 切片）负责 {@code content/physicalities/**}。
     */
    public static final BlockEntry<SpinoffBearingBlock> SPINOFF_BEARING = REGISTRATE
            .block("spinoff_bearing", SpinoffBearingBlock::new)
            .initialProperties(SharedProperties::netheriteMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutoutMipped)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .item()
            .tab(CWCreativeTabs.physicalitiesKey())
            .build()
            .register();

    public static final BlockEntityEntry<SpinoffBearingBlockEntity> SPINOFF_BEARING_BE = REGISTRATE
            .blockEntity("spinoff_bearing", SpinoffBearingBlockEntity::new)
            .validBlocks(SPINOFF_BEARING)
            .register();

    // ============================== 命令座椅（command_seat） ==============================

    /** 命令座椅：坐在上面按方向键，就能按规则表分别驱动四个水平面的输出轴。 */
    public static final BlockEntry<CommandSeatBlock> COMMAND_SEAT = REGISTRATE
            .block("command_seat", CommandSeatBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .item()
            .tab(CWCreativeTabs.physicalitiesKey())
            .build()
            .register();

    public static final BlockEntityEntry<CommandSeatBlockEntity> COMMAND_SEAT_BE = REGISTRATE
            .blockEntity("command_seat", CommandSeatBlockEntity::new)
            .validBlocks(COMMAND_SEAT)
            // 旧版 ClockworkBlockEntities.kt 的 COMMAND_SEAT 登记了 SequencedSeatRenderer：
            // block 模型里没有任何轴，5 根 SHAFT_HALF 全靠这个 BER（见渲染器类注释）。
            .renderer(() -> CommandSeatRenderer::new)
            .register();

    /**
     * 座位实体（不可见，复用 Create 的 {@code SeatEntity}）。
     *
     * <p>id 沿用旧版的 {@code sequenced_seat}；跟踪范围 / 更新间隔 / 免火等属性照抄旧版
     * （旧版 {@code ForgeClockworkEntities}），朝向与体积由 {@code SeatEntity#build} 给出。
     */
    public static final EntityEntry<CommandSeatEntity> COMMAND_SEAT_ENTITY = REGISTRATE
            .entity("sequenced_seat", CommandSeatEntity::new, MobCategory.MISC)
            .properties(SeatEntity::build)
            .properties(b -> b.setTrackingRange(5)
                    .setUpdateInterval(Integer.MAX_VALUE)
                    .setShouldReceiveVelocityUpdates(false)
                    .fireImmune())
            .renderer(() -> SeatEntity.Render::new)
            .register();

    private CWKineticsIndex() {
    }

    /** 触发类加载，让上面的静态字段完成注册（由 {@link ClockworkMod} 调用）。 */
    public static void register() {
    }
}
