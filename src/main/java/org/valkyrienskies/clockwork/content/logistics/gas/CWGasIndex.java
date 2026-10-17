package org.valkyrienskies.clockwork.content.logistics.gas;

import com.simibubi.create.AllTags;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.valkyrienskies.clockwork.ClockworkMod;
import org.valkyrienskies.clockwork.ClockworkRegistrate;
import org.valkyrienskies.clockwork.content.logistics.gas.compressor.AirCompressorBlock;
import org.valkyrienskies.clockwork.content.logistics.gas.compressor.AirCompressorBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.compressor.client.AirCompressorRenderer;
import org.valkyrienskies.clockwork.content.logistics.gas.backtank.GasBacktankBlock;
import org.valkyrienskies.clockwork.content.logistics.gas.backtank.GasBacktankBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.backtank.GasBacktankItem;
import org.valkyrienskies.clockwork.content.logistics.gas.crafter.GasCrafterBlock;
import org.valkyrienskies.clockwork.content.logistics.gas.crafter.GasCrafterBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.crafter.GasCraftingRecipe;
import org.valkyrienskies.clockwork.content.logistics.gas.crafter.GasCraftingRecipeSerializer;
import org.valkyrienskies.clockwork.content.logistics.gas.crafter.client.GasCrafterRenderer;
import org.valkyrienskies.clockwork.content.logistics.gas.duct.GasDuctBlock;
import org.valkyrienskies.clockwork.content.logistics.gas.duct.GasDuctBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.duct.StraightDuctBlock;
import org.valkyrienskies.clockwork.content.logistics.gas.duct.StraightDuctBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.duct.client.DuctRenderer;
import org.valkyrienskies.clockwork.content.logistics.gas.filter.FilterDuctBlock;
import org.valkyrienskies.clockwork.content.logistics.gas.filter.FilterDuctBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.heatpipe.HeatPipeBlock;
import org.valkyrienskies.clockwork.content.logistics.gas.heatpipe.HeatPipeBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.intake.IntakeBlock;
import org.valkyrienskies.clockwork.content.logistics.gas.intake.IntakeBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.engine.GasEngineBlock;
import org.valkyrienskies.clockwork.content.logistics.gas.engine.GasEngineBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.engine.SterlingEngineBlock;
import org.valkyrienskies.clockwork.content.logistics.gas.engine.SterlingEngineBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.engine.client.GasEngineRenderer;
import org.valkyrienskies.clockwork.content.logistics.gas.engine.client.SterlingEngineRenderer;
import org.valkyrienskies.clockwork.content.logistics.gas.exhaust.ExhaustBlock;
import org.valkyrienskies.clockwork.content.logistics.gas.exhaust.ExhaustBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.hoseport.HosePortBlock;
import org.valkyrienskies.clockwork.content.logistics.gas.hoseport.HosePortBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.nozzle.GasNozzleBlock;
import org.valkyrienskies.clockwork.content.logistics.gas.nozzle.GasNozzleBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.nozzle.client.GasNozzleRenderer;
import org.valkyrienskies.clockwork.content.logistics.solid.delivery.cannon.DeliveryCannonBlock;
import org.valkyrienskies.clockwork.content.logistics.solid.delivery.cannon.DeliveryCannonBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.solid.delivery.cannon.client.DeliveryCannonRenderer;
import org.valkyrienskies.clockwork.content.logistics.solid.delivery.chute.DeliveryChuteBlock;
import org.valkyrienskies.clockwork.content.logistics.solid.delivery.chute.DeliveryChuteBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.solid.delivery.frequency_slot.FrequencySlotRenderer;
import org.valkyrienskies.clockwork.content.logistics.gas.generator.CoalBurnerBlock;
import org.valkyrienskies.clockwork.content.logistics.gas.generator.CoalBurnerBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.generator.CreativeGasGeneratorBlock;
import org.valkyrienskies.clockwork.content.logistics.gas.generator.CreativeGasGeneratorBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.generator.SteamGeneratorBlock;
import org.valkyrienskies.clockwork.content.logistics.gas.generator.SteamGeneratorBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.heater.GasHeaterBlock;
import org.valkyrienskies.clockwork.content.logistics.gas.heater.GasHeaterBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.pump.PumpDuctBlock;
import org.valkyrienskies.clockwork.content.logistics.gas.pump.PumpDuctBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.pump.PumpDuctRenderer;
import org.valkyrienskies.clockwork.content.logistics.gas.redstone.RedstoneDuctBlock;
import org.valkyrienskies.clockwork.content.logistics.gas.redstone.RedstoneDuctBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.tank.DuctTankBlock;
import org.valkyrienskies.clockwork.content.logistics.gas.tank.DuctTankBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.tank.client.DuctTankModel;
import org.valkyrienskies.clockwork.content.logistics.gas.tank.client.DuctTankRenderer;
import org.valkyrienskies.clockwork.content.logistics.gas.thruster.GasThrusterBlock;
import org.valkyrienskies.clockwork.content.logistics.gas.thruster.GasThrusterBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.valve.ValveDuctBlock;
import org.valkyrienskies.clockwork.content.logistics.gas.valve.ValveDuctBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.valve.client.ValveDuctRenderer;
import org.valkyrienskies.clockwork.index.CWCreativeTabs;

/**
 * 气体物流切片的注册入口。
 *
 * <p>对应旧版 {@code ClockworkBlocks} / {@code ClockworkBlockEntities} 里 gas 相关的条目，
 * 按约定集中在本包内，由 {@code ClockworkMod} 在初始化时调用 {@link #register()}。
 *
 * <p>资源（blockstates / models / textures / lang）沿用旧版拷贝到 {@code assets/vs_clockwork} 的文件，
 * 因此这里<b>不调用</b> Registrate 的 {@code blockstate(...)}，只声明方块状态与方块实体。
 * 创造标签统一用 {@link CWCreativeTabs#gassesKey()}。
 *
 * <p>应力影响对应旧版 {@code ClockworkStress.setImpact(4.0)}：Create 6.0.10 把它放在
 * {@link BlockStressValues#IMPACTS} 注册表里，所以在对应方块注册时用 {@code onRegister} 登记。
 */
@SuppressWarnings("removal") // Registrate 的 addLayer 在当前版本被标记为待删除，但生态内通行（CWBlocks 同样用法）
public final class CWGasIndex {

    private static final ClockworkRegistrate REGISTRATE = ClockworkMod.REGISTRATE;

    /** 旧版 {@code ClockworkStress.setImpact(4.0)}：泵 / 阀门 / 压缩机的应力影响。 */
    private static final double KINETIC_STRESS_IMPACT = 4.0;

    private CWGasIndex() {
    }

    // ===================== 方块（必须先于方块实体声明：方块实体条目要引用它们） =====================

    /** 气体导管：网络骨架。 */
    public static final BlockEntry<GasDuctBlock> DUCT = REGISTRATE
            .block("duct", GasDuctBlock::new)
            .initialProperties(SharedProperties::netheriteMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutoutMipped)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, AllTags.AllBlockTags.SAFE_NBT.tag)
            .item()
            .tab(CWCreativeTabs.gassesKey())
            .build()
            .register();

    /** 气体泵：建立压差。 */
    public static final BlockEntry<PumpDuctBlock> PUMP_DUCT = REGISTRATE
            .block("pump_duct", PumpDuctBlock::new)
            .initialProperties(SharedProperties::netheriteMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutoutMipped)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, AllTags.AllBlockTags.SAFE_NBT.tag)
            .onRegister(block -> BlockStressValues.IMPACTS.register(block, () -> KINETIC_STRESS_IMPACT))
            .item()
            .tab(CWCreativeTabs.gassesKey())
            .build()
            .register();

    /** 气体阀门：按转速节流。 */
    public static final BlockEntry<ValveDuctBlock> VALVE_DUCT = REGISTRATE
            .block("valve_duct", ValveDuctBlock::new)
            .initialProperties(SharedProperties::netheriteMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutout)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, AllTags.AllBlockTags.SAFE_NBT.tag)
            .onRegister(block -> BlockStressValues.IMPACTS.register(block, () -> KINETIC_STRESS_IMPACT))
            .item()
            .tab(CWCreativeTabs.gassesKey())
            .build()
            .register();

    /** 气体储罐：可垂直堆叠的多方块。 */
    public static final BlockEntry<DuctTankBlock> DUCT_TANK = REGISTRATE
            .block("duct_tank", DuctTankBlock::new)
            .initialProperties(SharedProperties::netheriteMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutout)
            // 连接纹理（CT）：与 Create 的流体储罐同一套接法 —— AllBlocks.java:990
            // `.onRegister(CreateRegistrate.blockModel(() -> FluidTankModel::standard))`。
            // 走 Create 自己的 ModelSwapper：它在 ModelEvent.ModifyBakingResult 时把本方块**全部**
            // blockstate 变体对应的已烘焙模型包一层 DuctTankModel，于是 uv 可以按邻居连接状态重采样。
            // 用 Create 的入口而不是自己再写一份监听器，是为了不重复实现"枚举方块的每个
            // ModelResourceLocation"这段容易写错的逻辑。
            // 服务端安全：CreateRegistrate.blockModel 走 CatnipServices.PLATFORM.executeOnClientOnly，
            // 专用服务器上 DuctTankModel 这个类根本不会被加载（已 javap 核实 executeOnClientOnly）。
            .onRegister(CreateRegistrate.blockModel(() -> DuctTankModel::of))
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, AllTags.AllBlockTags.SAFE_NBT.tag)
            .item()
            .tab(CWCreativeTabs.gassesKey())
            .build()
            .register();

    /** 空气压缩机：动力产气。 */
    public static final BlockEntry<AirCompressorBlock> AIR_COMPRESSOR = REGISTRATE
            .block("air_compressor", AirCompressorBlock::new)
            .initialProperties(SharedProperties::netheriteMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutout)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, AllTags.AllBlockTags.SAFE_NBT.tag)
            .onRegister(block -> BlockStressValues.IMPACTS.register(block, () -> KINETIC_STRESS_IMPACT))
            .item()
            .tab(CWCreativeTabs.gassesKey())
            .build()
            .register();

    /** 创造气源：无限供气（右键切换气体种类）。 */
    public static final BlockEntry<CreativeGasGeneratorBlock> CREATIVE_GENERATOR = REGISTRATE
            .block("creative_gas_generator", CreativeGasGeneratorBlock::new)
            .initialProperties(SharedProperties::netheriteMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutout)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, AllTags.AllBlockTags.SAFE_NBT.tag)
            .item()
            .tab(CWCreativeTabs.gassesKey())
            .build()
            .register();

    /** 煤炉：烧燃料产热。 */
    public static final BlockEntry<CoalBurnerBlock> COAL_BURNER = REGISTRATE
            .block("coal_burner", CoalBurnerBlock::new)
            .initialProperties(SharedProperties::netheriteMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutout)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, AllTags.AllBlockTags.SAFE_NBT.tag)
            .item()
            .tab(CWCreativeTabs.gassesKey())
            .build()
            .register();

    /** 蒸汽提取器：贴在锅炉上产蒸汽。 */
    public static final BlockEntry<SteamGeneratorBlock> STEAM_GENERATOR = REGISTRATE
            .block("steam_generator", SteamGeneratorBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutout)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, AllTags.AllBlockTags.SAFE_NBT.tag)
            .item()
            .tab(CWCreativeTabs.gassesKey())
            .build()
            .register();

    /** 斯特林机：热气 → 旋转动力。 */
    public static final BlockEntry<SterlingEngineBlock> STERLING_ENGINE = REGISTRATE
            .block("sterling_engine", SterlingEngineBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutout)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, AllTags.AllBlockTags.SAFE_NBT.tag)
            .onRegister(BlockStressValues.setGeneratorSpeed(64, true))
            .item()
            .tab(CWCreativeTabs.gassesKey())
            .build()
            .register();

    /** 气体加热器：热气体 → Create 热源。 */
    public static final BlockEntry<GasHeaterBlock> GAS_HEATER = REGISTRATE
            .block("gas_heater", GasHeaterBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutout)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, AllTags.AllBlockTags.SAFE_NBT.tag)
            .item()
            .tab(CWCreativeTabs.gassesKey())
            .build()
            .register();

    /** 排气口：把气体排到环境。 */
    public static final BlockEntry<ExhaustBlock> EXHAUST = REGISTRATE
            .block("exhaust", ExhaustBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutout)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, AllTags.AllBlockTags.SAFE_NBT.tag)
            .item()
            .tab(CWCreativeTabs.gassesKey())
            .build()
            .register();

    /** 红石导管：压力 → 红石信号。 */
    public static final BlockEntry<RedstoneDuctBlock> REDSTONE_DUCT = REGISTRATE
            .block("redstone_duct", RedstoneDuctBlock::new)
            .initialProperties(SharedProperties::netheriteMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutoutMipped)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, AllTags.AllBlockTags.SAFE_NBT.tag)
            .item()
            .tab(CWCreativeTabs.gassesKey())
            .build()
            .register();

    /** 燃气机：气体热能 → 蒸汽机效率（blockstate 只有 axis，见资源 JSON）。 */
    public static final BlockEntry<GasEngineBlock> GAS_ENGINE = REGISTRATE
            .block("gas_engine", GasEngineBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutoutMipped)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, AllTags.AllBlockTags.SAFE_NBT.tag)
            .item()
            .tab(CWCreativeTabs.gassesKey())
            .build()
            .register();

    /** 气体合成器：贴在工作盆上方执行气体合成配方。 */
    public static final BlockEntry<GasCrafterBlock> GAS_CRAFTER = REGISTRATE
            .block("gas_crafter", GasCrafterBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutoutMipped)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, AllTags.AllBlockTags.SAFE_NBT.tag)
            .item()
            .tab(CWCreativeTabs.gassesKey())
            .build()
            .register();

    /** 气体背罐：可穿戴的小型储气罐（破坏时保留内部气体）。 */
    public static final BlockEntry<GasBacktankBlock> GAS_BACKTANK = REGISTRATE
            .block("gas_backtank", GasBacktankBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutoutMipped)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, AllTags.AllBlockTags.SAFE_NBT.tag)
            .item(GasBacktankItem::new)
            .tab(CWCreativeTabs.gassesKey())
            .build()
            .register();

    /** 气体推进器：喷气产生推力（对所在子空间施力）。 */
    public static final BlockEntry<GasThrusterBlock> GAS_THRUSTER = REGISTRATE
            .block("gas_thruster", GasThrusterBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutoutMipped)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, AllTags.AllBlockTags.SAFE_NBT.tag)
            .item()
            .tab(CWCreativeTabs.gassesKey())
            .build()
            .register();

    /** 气嘴：定向喷气推进（旧版依赖 VS2 气囊，本实现为重新设计，见方块实体类头）。 */
    public static final BlockEntry<GasNozzleBlock> GAS_NOZZLE = REGISTRATE
            .block("gas_nozzle", GasNozzleBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutoutMipped)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, AllTags.AllBlockTags.SAFE_NBT.tag)
            .item()
            .tab(CWCreativeTabs.gassesKey())
            .build()
            .register();

    /** 软管接口：两口配对后气体网络远程连通（旧版依赖 VS2 距离关节，本实现为重新设计）。 */
    public static final BlockEntry<HosePortBlock> HOSE_PORT = REGISTRATE
            .block("hose_port", HosePortBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutoutMipped)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, AllTags.AllBlockTags.SAFE_NBT.tag)
            .item()
            .tab(CWCreativeTabs.gassesKey())
            .build()
            .register();

    // -------- 固体交付（旧版 content/logistics/solid/delivery；物流切片目前只有 CWGasIndex 一个注册入口，
    //          因此这两个方块按"物流"归到这里，仅追加条目，不改动 Lead 维护的文件 --------

    /** 交付加农炮：把下方容器里的物品抛到同频的交付滑道上。 */
    public static final BlockEntry<DeliveryCannonBlock> DELIVERY_CANNON = REGISTRATE
            .block("delivery_cannon", DeliveryCannonBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutoutMipped)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, AllTags.AllBlockTags.SAFE_NBT.tag)
            .item()
            .tab(CWCreativeTabs.gassesKey())
            .build()
            .register();

    /** 交付滑道：收下落到身上的物品，并从下方输出。 */
    public static final BlockEntry<DeliveryChuteBlock> DELIVERY_CHUTE = REGISTRATE
            .block("delivery_chute", DeliveryChuteBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutoutMipped)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, AllTags.AllBlockTags.SAFE_NBT.tag)
            .item()
            .tab(CWCreativeTabs.gassesKey())
            .build()
            .register();

    // -------- 以下 4 个方块两仓库都只有残留资源、没有实现源码，按导管/热管/滤管/进气口的常识自行设计 --------

    /** 直管：只有轴向两端连通的导管。 */
    public static final BlockEntry<StraightDuctBlock> STRAIGHT_DUCT = REGISTRATE
            .block("straight_duct", StraightDuctBlock::new)
            .initialProperties(SharedProperties::netheriteMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutoutMipped)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, AllTags.AllBlockTags.SAFE_NBT.tag)
            .item()
            .tab(CWCreativeTabs.gassesKey())
            .build()
            .register();

    /** 滤管：只允许指定气体通过。 */
    public static final BlockEntry<FilterDuctBlock> FILTER_DUCT = REGISTRATE
            .block("filter_duct", FilterDuctBlock::new)
            .initialProperties(SharedProperties::netheriteMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutoutMipped)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, AllTags.AllBlockTags.SAFE_NBT.tag)
            .item()
            .tab(CWCreativeTabs.gassesKey())
            .build()
            .register();

    /** 热管：只在相邻节点之间导热，不搬气体。 */
    public static final BlockEntry<HeatPipeBlock> HEAT_PIPE = REGISTRATE
            .block("heat_pipe", HeatPipeBlock::new)
            .initialProperties(SharedProperties::netheriteMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutoutMipped)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, AllTags.AllBlockTags.SAFE_NBT.tag)
            .item()
            .tab(CWCreativeTabs.gassesKey())
            .build()
            .register();

    /** 进气口：动力驱动的环境空气入口。 */
    public static final BlockEntry<IntakeBlock> INTAKE = REGISTRATE
            .block("intake", IntakeBlock::new)
            .initialProperties(SharedProperties::netheriteMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutoutMipped)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, AllTags.AllBlockTags.SAFE_NBT.tag)
            .onRegister(block -> BlockStressValues.IMPACTS.register(block, () -> KINETIC_STRESS_IMPACT))
            .item()
            .tab(CWCreativeTabs.gassesKey())
            .build()
            .register();

    // ===================== 方块实体 =====================

    public static final BlockEntityEntry<GasDuctBlockEntity> DUCT_BE = REGISTRATE
            .blockEntity("duct", GasDuctBlockEntity::new)
            .validBlocks(DUCT)
            // 旧版 ClockworkBlockEntities.kt:462 的 .renderer { DuctRenderer(...) }：
            // 每面的接口圈 / 泄漏面 / 收口环都不在 blockstate 里，只能由 BER 画
            .renderer(() -> DuctRenderer::new)
            .register();

    public static final BlockEntityEntry<PumpDuctBlockEntity> PUMP_DUCT_BE = REGISTRATE
            .blockEntity("pump_duct", PumpDuctBlockEntity::new)
            .validBlocks(PUMP_DUCT)
            // 齿轮不走 blockstate（block/pump/block 只有管体），必须由 BER 按转速转着画，
            // 否则世界里只剩一个管壳 —— 对应官方 PumpDuctRenderer
            .renderer(() -> PumpDuctRenderer::new)
            .register();

    public static final BlockEntityEntry<ValveDuctBlockEntity> VALVE_DUCT_BE = REGISTRATE
            .blockEntity("valve_duct", ValveDuctBlockEntity::new)
            .validBlocks(VALVE_DUCT)
            // 旧版 ClockworkBlockEntities.kt:523 的 .renderer { ValveDuctRenderer(...) }：
            // 开度指针（block/valve_duct/pointer）+ 输入轴，二者都不在 blockstate 里
            .renderer(() -> ValveDuctRenderer::new)
            .register();

    public static final BlockEntityEntry<DuctTankBlockEntity> DUCT_TANK_BE = REGISTRATE
            .blockEntity("duct_tank", DuctTankBlockEntity::new)
            .validBlocks(DUCT_TANK)
            // 罐内的气体体积（"气面"高度 + 颜色）：Create 的流体储罐同样用 BER 画内容物
            // （FluidTankRenderer），罐体本身仍然只是普通烘焙模型
            .renderer(() -> DuctTankRenderer::new)
            .register();

    public static final BlockEntityEntry<AirCompressorBlockEntity> AIR_COMPRESSOR_BE = REGISTRATE
            .blockEntity("air_compressor", AirCompressorBlockEntity::new)
            .validBlocks(AIR_COMPRESSOR)
            // 方块模型只是底架；输入轴扇叶 / 风箱布 / 顶板三块由 BER 画
            // （对应官方 AirCompressorRenderer，partial 见 CompressorPartials）
            .renderer(() -> AirCompressorRenderer::new)
            .register();

    public static final BlockEntityEntry<CreativeGasGeneratorBlockEntity> CREATIVE_GENERATOR_BE = REGISTRATE
            .blockEntity("creative_gas_generator", CreativeGasGeneratorBlockEntity::new)
            .validBlocks(CREATIVE_GENERATOR)
            .register();

    public static final BlockEntityEntry<CoalBurnerBlockEntity> COAL_BURNER_BE = REGISTRATE
            .blockEntity("coal_burner", CoalBurnerBlockEntity::new)
            .validBlocks(COAL_BURNER)
            .register();

    public static final BlockEntityEntry<SteamGeneratorBlockEntity> STEAM_GENERATOR_BE = REGISTRATE
            .blockEntity("steam_generator", SteamGeneratorBlockEntity::new)
            .validBlocks(STEAM_GENERATOR)
            .register();

    public static final BlockEntityEntry<SterlingEngineBlockEntity> STERLING_ENGINE_BE = REGISTRATE
            .blockEntity("sterling_engine", SterlingEngineBlockEntity::new)
            .validBlocks(STERLING_ENGINE)
            // 旧版 ClockworkBlockEntities.kt:665 的 .renderer { SterlingEngineRenderer(...) }：
            // 机身模型里没有飞轮，齿轮是 Create 的 MECHANICAL_PUMP_COG partial，只能由 BER 画
            .renderer(() -> SterlingEngineRenderer::new)
            .register();

    public static final BlockEntityEntry<GasHeaterBlockEntity> GAS_HEATER_BE = REGISTRATE
            .blockEntity("gas_heater", GasHeaterBlockEntity::new)
            .validBlocks(GAS_HEATER)
            .register();

    public static final BlockEntityEntry<ExhaustBlockEntity> EXHAUST_BE = REGISTRATE
            .blockEntity("exhaust", ExhaustBlockEntity::new)
            .validBlocks(EXHAUST)
            .register();

    public static final BlockEntityEntry<RedstoneDuctBlockEntity> REDSTONE_DUCT_BE = REGISTRATE
            .blockEntity("redstone_duct", RedstoneDuctBlockEntity::new)
            .validBlocks(REDSTONE_DUCT)
            .register();

    public static final BlockEntityEntry<GasEngineBlockEntity> GAS_ENGINE_BE = REGISTRATE
            .blockEntity("gas_engine", GasEngineBlockEntity::new)
            .validBlocks(GAS_ENGINE)
            .renderer(() -> GasEngineRenderer::new)
            .register();

    public static final BlockEntityEntry<GasCrafterBlockEntity> GAS_CRAFTER_BE = REGISTRATE
            .blockEntity("gas_crafter", GasCrafterBlockEntity::new)
            .validBlocks(GAS_CRAFTER)
            .renderer(() -> GasCrafterRenderer::new)
            .register();

    public static final BlockEntityEntry<GasBacktankBlockEntity> GAS_BACKTANK_BE = REGISTRATE
            .blockEntity("gas_backtank", GasBacktankBlockEntity::new)
            .validBlocks(GAS_BACKTANK)
            .register();

    public static final BlockEntityEntry<GasThrusterBlockEntity> GAS_THRUSTER_BE = REGISTRATE
            .blockEntity("gas_thruster", GasThrusterBlockEntity::new)
            .validBlocks(GAS_THRUSTER)
            .register();

    public static final BlockEntityEntry<GasNozzleBlockEntity> GAS_NOZZLE_BE = REGISTRATE
            .blockEntity("gas_nozzle", GasNozzleBlockEntity::new)
            .validBlocks(GAS_NOZZLE)
            // 旧版 ClockworkBlockEntities.kt:605 的 .renderer { GasNozzleRenderer(...) }：
            // 表盘与叶轮轴是独立子模型，blockstate 只引用喷嘴本体
            .renderer(() -> GasNozzleRenderer::new)
            .register();

    public static final BlockEntityEntry<HosePortBlockEntity> HOSE_PORT_BE = REGISTRATE
            .blockEntity("hose_port", HosePortBlockEntity::new)
            .validBlocks(HOSE_PORT)
            .register();

    public static final BlockEntityEntry<DeliveryCannonBlockEntity> DELIVERY_CANNON_BE = REGISTRATE
            .blockEntity("delivery_cannon", DeliveryCannonBlockEntity::new)
            .validBlocks(DELIVERY_CANNON)
            // 旧版 ClockworkBlockEntities.kt:784 的 .renderer { DeliveryCannonRenderer(...) }：
            // blockstate 只引用底座，炮座/转台/炮管/天线四段全靠 BER（静止姿态，见渲染器类注释）
            .renderer(() -> DeliveryCannonRenderer::new)
            .register();

    public static final BlockEntityEntry<DeliveryChuteBlockEntity> DELIVERY_CHUTE_BE = REGISTRATE
            .blockEntity("delivery_chute", DeliveryChuteBlockEntity::new)
            .validBlocks(DELIVERY_CHUTE)
            // 旧版 ClockworkBlockEntities.kt 的 .renderer { FrequencySlotRenderer<DeliveryChuteBlockEntity>(...) }：
            // 频率物品画在 FrequencySlotBehaviour 的槽位里（槽位为空时不画）。
            .renderer(() -> FrequencySlotRenderer::new)
            .register();

    public static final BlockEntityEntry<StraightDuctBlockEntity> STRAIGHT_DUCT_BE = REGISTRATE
            .blockEntity("straight_duct", StraightDuctBlockEntity::new)
            .validBlocks(STRAIGHT_DUCT)
            .register();

    public static final BlockEntityEntry<FilterDuctBlockEntity> FILTER_DUCT_BE = REGISTRATE
            .blockEntity("filter_duct", FilterDuctBlockEntity::new)
            .validBlocks(FILTER_DUCT)
            .register();

    public static final BlockEntityEntry<HeatPipeBlockEntity> HEAT_PIPE_BE = REGISTRATE
            .blockEntity("heat_pipe", HeatPipeBlockEntity::new)
            .validBlocks(HEAT_PIPE)
            .register();

    public static final BlockEntityEntry<IntakeBlockEntity> INTAKE_BE = REGISTRATE
            .blockEntity("intake", IntakeBlockEntity::new)
            .validBlocks(INTAKE)
            .register();

    /**
     * 触发类加载，完成本切片的所有注册（由 {@code ClockworkMod} 调用）。
     *
     * <p>方块 / 物品 / 方块实体都在上面的静态字段里注册；应力影响随方块创建时一并登记
     * （见各条目的 {@code onRegister}）。这里额外注册气体合成配方类型与序列化器
     * （配方不是 Registrate 的注册类型，用 NeoForge 的 {@link DeferredRegister}）。
     */
    public static void register() {
        final DeferredRegister<RecipeType<?>> recipeTypes =
                DeferredRegister.create(Registries.RECIPE_TYPE, ClockworkMod.MOD_ID);
        recipeTypes.register("gas_crafting", () -> GasCraftingRecipeSerializer.TYPE);
        recipeTypes.register(REGISTRATE.getModEventBus());

        final DeferredRegister<RecipeSerializer<?>> recipeSerializers =
                DeferredRegister.create(Registries.RECIPE_SERIALIZER, ClockworkMod.MOD_ID);
        recipeSerializers.register("gas_crafting", () -> GasCraftingRecipeSerializer.INSTANCE);
        recipeSerializers.register(REGISTRATE.getModEventBus());
    }
}
