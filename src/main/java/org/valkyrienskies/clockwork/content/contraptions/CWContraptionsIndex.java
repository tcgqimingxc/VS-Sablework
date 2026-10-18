package org.valkyrienskies.clockwork.content.contraptions;

import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import org.valkyrienskies.clockwork.ClockworkMod;
import org.valkyrienskies.clockwork.ClockworkRegistrate;
import org.valkyrienskies.clockwork.content.contraptions.flap.AndesiteFlapBearingBlock;
import org.valkyrienskies.clockwork.content.contraptions.flap.FlapBearingBlock;
import org.valkyrienskies.clockwork.content.contraptions.flap.FlapBearingBlockEntity;
import org.valkyrienskies.clockwork.content.contraptions.flap.SmartFlapBearingBlock;
import org.valkyrienskies.clockwork.content.contraptions.flap.SmartFlapBearingBlockEntity;
import org.valkyrienskies.clockwork.content.contraptions.flap.client.FlapBearingRenderer;
import org.valkyrienskies.clockwork.content.contraptions.gyro.GyroBlock;
import org.valkyrienskies.clockwork.content.contraptions.gyro.GyroBlockEntity;
import org.valkyrienskies.clockwork.content.contraptions.gyro.client.GyroRenderer;
import org.valkyrienskies.clockwork.content.contraptions.phys.ballooner.BalloonerBlock;
import org.valkyrienskies.clockwork.content.contraptions.phys.ballooner.BalloonerBlockEntity;
import org.valkyrienskies.clockwork.content.contraptions.phys.bearing.AbstractBearingBlockEntity;
import org.valkyrienskies.clockwork.content.contraptions.phys.bearing.PhysBearingBlock;
import org.valkyrienskies.clockwork.content.contraptions.phys.bearing.PhysBearingBlockEntity;
import org.valkyrienskies.clockwork.content.contraptions.phys.bearing.client.PhysBearingRenderer;
import org.valkyrienskies.clockwork.content.contraptions.phys.gimbal.GimbalBearingBlock;
import org.valkyrienskies.clockwork.content.contraptions.phys.gimbal.GimbalBearingBlockEntity;
import org.valkyrienskies.clockwork.content.contraptions.phys.gimbal.client.GimbalBearingRenderer;
import org.valkyrienskies.clockwork.content.contraptions.phys.infuser.PhysicsInfuserBlock;
import org.valkyrienskies.clockwork.content.contraptions.phys.infuser.PhysicsInfuserBlockEntity;
import org.valkyrienskies.clockwork.content.contraptions.phys.infuser.client.PhysicsInfuserRenderer;
import org.valkyrienskies.clockwork.content.contraptions.propeller.PropellerBearingBlock;
import org.valkyrienskies.clockwork.content.contraptions.propeller.PropellerBearingBlockEntity;
import org.valkyrienskies.clockwork.content.contraptions.propeller.PropellerKind;
import org.valkyrienskies.clockwork.content.contraptions.propeller.blades.BladeControllerBlock;
import org.valkyrienskies.clockwork.content.contraptions.propeller.blades.BladeControllerBlockEntity;
import org.valkyrienskies.clockwork.content.contraptions.propeller.blades.client.BladeControllerRenderer;
import org.valkyrienskies.clockwork.content.contraptions.propeller.blades.item.BladeItem;
import org.valkyrienskies.clockwork.content.contraptions.propeller.blades.item.BladeRecipes;
import org.valkyrienskies.clockwork.content.contraptions.propeller.client.PropellerBearingRenderer;
import org.valkyrienskies.clockwork.content.contraptions.propeller.copter.CopterBearingBlock;
import org.valkyrienskies.clockwork.content.contraptions.propeller.copter.CopterBearingBlockEntity;
import org.valkyrienskies.clockwork.content.contraptions.propeller.copter.client.CopterBearingRenderer;
import org.valkyrienskies.clockwork.content.contraptions.reactionwheel.ReactionWheelBlock;
import org.valkyrienskies.clockwork.content.contraptions.reactionwheel.ReactionWheelBlockEntity;
import org.valkyrienskies.clockwork.content.contraptions.reactionwheel.client.ReactionWheelRenderer;
import org.valkyrienskies.clockwork.content.contraptions.sugar_rocket.SugarRocketBlock;
import org.valkyrienskies.clockwork.content.contraptions.sugar_rocket.SugarRocketBlockEntity;
import org.valkyrienskies.clockwork.content.contraptions.sugar_rocket.client.SugarRocketRenderer;
import org.valkyrienskies.clockwork.index.CWCreativeTabs;

/**
 * 物理机械切片注册入口。
 *
 * <p>调用方式：由 Lead 在 {@code ClockworkMod} 构造器里按切片顺序调用 {@link #register()}。
 *
 * <p>资源（blockstate / 模型 / 贴图 / 语言）沿用旧版拷贝过来的
 * {@code src/main/resources/assets/vs_clockwork/**}，所以这里<b>不</b>调用
 * {@code .blockstate(...)}，避免覆盖手工资源；但也因此不能新增方块状态属性
 * （JSON 里的 variant 列表是按旧版属性写的）。
 */
@SuppressWarnings("removal") // Registrate 的 addLayer 在当前版本标记为待删除，但仍是生态内通行做法
public final class CWContraptionsIndex {

    private static final ClockworkRegistrate REGISTRATE = ClockworkMod.REGISTRATE;

    // ===================== P0：物理灌注器 =====================

    /** 物理灌注器：把选定区域装配成 Sable 子空间（旧版 {@code physics_infuser}）。 */
    public static final BlockEntry<PhysicsInfuserBlock> PHYSICS_INFUSER = REGISTRATE
            .block("physics_infuser", PhysicsInfuserBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutoutMipped)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .item()
            .tab(CWCreativeTabs.physicalitiesKey())
            .build()
            .register();

    public static final BlockEntityEntry<PhysicsInfuserBlockEntity> PHYSICS_INFUSER_BE = REGISTRATE
            .blockEntity("physics_infuser", PhysicsInfuserBlockEntity::new)
            .validBlocks(PHYSICS_INFUSER)
            .renderer(() -> PhysicsInfuserRenderer::new)
            .register();

    // ===================== P0：物理轴承 =====================

    /** 物理轴承（旧版 {@code phys_bearing}）。 */
    public static final BlockEntry<PhysBearingBlock> PHYS_BEARING = REGISTRATE
            .block("phys_bearing", PhysBearingBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutoutMipped)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .item()
            .tab(CWCreativeTabs.physicalitiesKey())
            .build()
            .register();

    public static final BlockEntityEntry<PhysBearingBlockEntity> PHYS_BEARING_BE = REGISTRATE
            .blockEntity("phys_bearing", PhysBearingBlockEntity::new)
            .validBlocks(PHYS_BEARING)
            .renderer(() -> PhysBearingRenderer::new)
            .register();

    // ===================== P0：螺旋桨轴承 =====================

    /** 黄铜螺旋桨轴承（旧版 {@code brass_propeller_bearing}）。 */
    public static final BlockEntry<PropellerBearingBlock> BRASS_PROPELLER_BEARING = REGISTRATE
            .block("brass_propeller_bearing", p -> new PropellerBearingBlock(p, PropellerKind.BRASS))
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutoutMipped)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .item()
            .tab(CWCreativeTabs.physicalitiesKey())
            .build()
            .register();

    /** 拼装螺旋桨轴承（旧版 {@code juryrigged_propeller_bearing}）。 */
    public static final BlockEntry<PropellerBearingBlock> JURYRIGGED_PROPELLER_BEARING = REGISTRATE
            .block("juryrigged_propeller_bearing", p -> new PropellerBearingBlock(p, PropellerKind.JURYRIGGED))
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutoutMipped)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .item()
            .tab(CWCreativeTabs.physicalitiesKey())
            .build()
            .register();

    /** 两种螺旋桨共用一个方块实体类型（规格由方块决定）。 */
    public static final BlockEntityEntry<PropellerBearingBlockEntity> PROPELLER_BEARING_BE = REGISTRATE
            .blockEntity("propeller_bearing", PropellerBearingBlockEntity::new)
            .validBlocks(BRASS_PROPELLER_BEARING, JURYRIGGED_PROPELLER_BEARING)
            .renderer(() -> PropellerBearingRenderer::new)
            .register();

    // ===================== P1：万向轴承 =====================

    /** 万向轴承（旧版 {@code gimbal_bearing}）。 */
    public static final BlockEntry<GimbalBearingBlock> GIMBAL_BEARING = REGISTRATE
            .block("gimbal_bearing", GimbalBearingBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutoutMipped)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .item()
            .tab(CWCreativeTabs.physicalitiesKey())
            .build()
            .register();

    public static final BlockEntityEntry<GimbalBearingBlockEntity> GIMBAL_BEARING_BE = REGISTRATE
            .blockEntity("gimbal_bearing", GimbalBearingBlockEntity::new)
            .validBlocks(GIMBAL_BEARING)
            .renderer(() -> GimbalBearingRenderer::new)
            .register();

    // ===================== P1：姿态控制 =====================

    /** 陀螺（旧版 {@code gyro}）。 */
    public static final BlockEntry<GyroBlock> GYRO = REGISTRATE
            .block("gyro", GyroBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutoutMipped)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .item()
            .tab(CWCreativeTabs.physicalitiesKey())
            .build()
            .register();

    public static final BlockEntityEntry<GyroBlockEntity> GYRO_BE = REGISTRATE
            .blockEntity("gyro", GyroBlockEntity::new)
            .validBlocks(GYRO)
            .renderer(() -> GyroRenderer::new)
            .register();

    /** 反作用轮（旧版 {@code reactionwheel}）。 */
    public static final BlockEntry<ReactionWheelBlock> REACTION_WHEEL = REGISTRATE
            .block("reactionwheel", ReactionWheelBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutoutMipped)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .item()
            .tab(CWCreativeTabs.physicalitiesKey())
            .build()
            .register();

    public static final BlockEntityEntry<ReactionWheelBlockEntity> REACTION_WHEEL_BE = REGISTRATE
            .blockEntity("reactionwheel", ReactionWheelBlockEntity::new)
            .validBlocks(REACTION_WHEEL)
            .renderer(() -> ReactionWheelRenderer::new)
            .register();

    // ===================== P2：襟翼轴承 =====================

    /** 安山襟翼轴承（旧版 {@code andesite_flap_bearing}）。 */
    public static final BlockEntry<AndesiteFlapBearingBlock> ANDESITE_FLAP_BEARING = REGISTRATE
            .block("andesite_flap_bearing", AndesiteFlapBearingBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutoutMipped)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .item()
            .tab(CWCreativeTabs.physicalitiesKey())
            .build()
            .register();

    /** 智能襟翼轴承（旧版 {@code smart_flap_bearing}）。 */
    public static final BlockEntry<SmartFlapBearingBlock> SMART_FLAP_BEARING = REGISTRATE
            .block("smart_flap_bearing", SmartFlapBearingBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutoutMipped)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .item()
            .tab(CWCreativeTabs.physicalitiesKey())
            .build()
            .register();

    public static final BlockEntityEntry<FlapBearingBlockEntity> FLAP_BEARING_BE = REGISTRATE
            .blockEntity("flap_bearing", FlapBearingBlockEntity::new)
            .validBlocks(ANDESITE_FLAP_BEARING)
            // 旧版 ClockworkBlockEntities.kt:269 的 .renderer { FlapBearingRenderer(...) }：
            // 顶盖（block/flap_bearing/top）+ 半根输入轴，二者都不在 blockstate 里
            .renderer(() -> FlapBearingRenderer::new)
            .register();

    public static final BlockEntityEntry<SmartFlapBearingBlockEntity> SMART_FLAP_BEARING_BE = REGISTRATE
            .blockEntity("smart_flap_bearing", SmartFlapBearingBlockEntity::new)
            .validBlocks(SMART_FLAP_BEARING)
            // 旧版 ClockworkBlockEntities.kt:294：与普通襟翼轴承共用同一个渲染器
            .renderer(() -> FlapBearingRenderer::new)
            .register();

    // ===================== P2：直升机旋翼 =====================

    /** 直升机旋翼轴承（旧版 {@code copter_bearing}）。 */
    public static final BlockEntry<CopterBearingBlock> COPTER_BEARING = REGISTRATE
            .block("copter_bearing", CopterBearingBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutoutMipped)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .item()
            .tab(CWCreativeTabs.physicalitiesKey())
            .build()
            .register();

    public static final BlockEntityEntry<CopterBearingBlockEntity> COPTER_BEARING_BE = REGISTRATE
            .blockEntity("copter_bearing", CopterBearingBlockEntity::new)
            .validBlocks(COPTER_BEARING)
            .renderer(() -> CopterBearingRenderer::new)
            .register();

    // ===================== P2：糖火箭 / 热气球 =====================

    /** 糖火箭（旧版 {@code sugar_rocket}）。 */
    public static final BlockEntry<SugarRocketBlock> SUGAR_ROCKET = REGISTRATE
            .block("sugar_rocket", SugarRocketBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutoutMipped)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .item()
            .tab(CWCreativeTabs.physicalitiesKey())
            .build()
            .register();

    public static final BlockEntityEntry<SugarRocketBlockEntity> SUGAR_ROCKET_BE = REGISTRATE
            .blockEntity("sugar_rocket", SugarRocketBlockEntity::new)
            .validBlocks(SUGAR_ROCKET)
            .renderer(() -> SugarRocketRenderer::new)
            .register();

    /** 热气球浮力方块（旧版 {@code ballooner}）。 */
    public static final BlockEntry<BalloonerBlock> BALLOONER = REGISTRATE
            .block("ballooner", BalloonerBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutoutMipped)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .item()
            .tab(CWCreativeTabs.physicalitiesKey())
            .build()
            .register();

    public static final BlockEntityEntry<BalloonerBlockEntity> BALLOONER_BE = REGISTRATE
            .blockEntity("ballooner", BalloonerBlockEntity::new)
            .validBlocks(BALLOONER)
            .register();

    // ===================== P0：叶片控制器 / 叶片（可变桨距） =====================

    /**
     * 叶片控制器（旧版 {@code blade_controller}）：螺旋桨的桨毂，装叶片并设定公共桨距角。
     *
     * <p>资源（blockstate / 模型 / 贴图）沿用旧版拷贝，属性只有 {@code facing}。
     */
    public static final BlockEntry<BladeControllerBlock> BLADE_CONTROLLER = REGISTRATE
            .block("blade_controller", BladeControllerBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutoutMipped)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .item()
            .tab(CWCreativeTabs.physicalitiesKey())
            .build()
            .register();

    /**
     * 叶片控制器方块实体。渲染器负责把槽位里的叶片画成实物
     * （旧版 {@code BladeControllerRenderer.kt}；见 {@link BladeControllerRenderer} 类注释的差异说明）。
     */
    public static final BlockEntityEntry<BladeControllerBlockEntity> BLADE_CONTROLLER_BE = REGISTRATE
            .blockEntity("blade_controller", BladeControllerBlockEntity::new)
            .validBlocks(BLADE_CONTROLLER)
            .renderer(() -> BladeControllerRenderer::new)
            .register();

    /** 窄叶片（旧版 {@code propeller_blade}：木剑 +2 伤害 / 耐久 100 / 默认展长 1 格）。 */
    public static final ItemEntry<BladeItem> PROPELLER_BLADE = REGISTRATE
            .item("propeller_blade", p -> new BladeItem(Tiers.WOOD, false, p))
            .properties(p -> p.durability(100).attributes(SwordItem.createAttributes(Tiers.WOOD, 2, 0.4f)))
            .tab(CWCreativeTabs.physicalitiesKey())
            .register();

    /** 宽叶片（旧版 {@code wide_propeller_blade}：木剑 +4 伤害 / 耐久 200 / 弦长更大）。 */
    public static final ItemEntry<BladeItem> WIDE_PROPELLER_BLADE = REGISTRATE
            .item("wide_propeller_blade", p -> new BladeItem(Tiers.WOOD, true, p))
            .properties(p -> p.durability(200).attributes(SwordItem.createAttributes(Tiers.WOOD, 4, 0.2f)))
            .tab(CWCreativeTabs.physicalitiesKey())
            .register();

    private CWContraptionsIndex() {
    }

    /** 触发类加载，保证上面的静态字段被注册（由 Lead 在 {@link ClockworkMod} 里调用）。 */
    public static void register() {
        // 轴承伺服必须在"每个物理子步"刷新（Sable 一游戏刻跑多个子步），
        // 注册顺序无关紧要：物理回调只在世界 tick 时才可能被触发。
        AbstractBearingBlockEntity.registerPhysicsHook();
        // 叶片加长 / 切割两个自定义配方类型（旧版 ClockworkRecipes.kt）。
        // 必须在这里注册：配方管理器在数据包加载时就会按 id 找它们，
        // 漏注册 = 实机 "Unknown registry key ... recipe_serializer"（_disabled_data 里记录的历史崩溃）。
        BladeRecipes.register();
    }
}
