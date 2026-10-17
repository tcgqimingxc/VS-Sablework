package org.valkyrienskies.clockwork.index;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.valkyrienskies.clockwork.ClockworkMod;
import org.valkyrienskies.clockwork.content.contraptions.client.ContraptionPartials;
import org.valkyrienskies.clockwork.content.contraptions.flap.FlapBearingPartials;
import org.valkyrienskies.clockwork.content.contraptions.propeller.blades.BladePartials;
import org.valkyrienskies.clockwork.content.curiosities.altmeter.AltMeterPartials;
import org.valkyrienskies.clockwork.content.curiosities.clock.client.ClockPartialModels;
import org.valkyrienskies.clockwork.content.curiosities.tools.drill.client.DrillPartials;
import org.valkyrienskies.clockwork.content.curiosities.tools.gravitron.client.GravitronPartials;
import org.valkyrienskies.clockwork.content.curiosities.tools.saw.client.SawPartials;
import org.valkyrienskies.clockwork.content.curiosities.tools.wanderwand.client.WanderwandPartials;
import org.valkyrienskies.clockwork.content.kinetics.resistor.ResistorPartials;
import org.valkyrienskies.clockwork.content.logistics.gas.compressor.client.CompressorPartials;
import org.valkyrienskies.clockwork.content.logistics.gas.crafter.GasCrafterPartials;
import org.valkyrienskies.clockwork.content.logistics.gas.duct.DuctPartials;
import org.valkyrienskies.clockwork.content.logistics.gas.engine.GasEnginePartials;
import org.valkyrienskies.clockwork.content.logistics.gas.nozzle.GasNozzlePartials;
import org.valkyrienskies.clockwork.content.logistics.gas.tank.DuctTankPartials;
import org.valkyrienskies.clockwork.content.logistics.gas.valve.ValveDuctPartials;
import org.valkyrienskies.clockwork.content.logistics.solid.delivery.cannon.DeliveryCannonPartials;
import org.valkyrienskies.clockwork.content.physicalities.extendon.client.ExtendonPartials;
import org.valkyrienskies.clockwork.content.physicalities.slicker.SlickerPartials;
import org.valkyrienskies.clockwork.content.physicalities.wing.WingPartials;

/**
 * 客户端装配总入口：把所有"局部模型（partial model）"在<b>模型烘焙之前</b>登记进 Flywheel。
 *
 * <h2>为什么必须有一个提前初始化的入口</h2>
 * <p>Flywheel 1.0 的 {@code PartialModel} 是这样工作的（以下全部由
 * {@code javap -p -c} 反编译 {@code flywheel-neoforge-1.21.1-1.0.6.jar} 核实，不是推测）：
 * <ol>
 *   <li>{@code PartialModel.of(ResourceLocation)} = {@code ALL.computeIfAbsent(loc, PartialModel::new)}，
 *       即"创建即登记"，登记表是静态的 {@code PartialModel.ALL}；</li>
 *   <li>{@code PartialModelEventHandler.onRegisterAdditional} 在 {@code ModelEvent.RegisterAdditional}
 *       时遍历 {@code ALL.keySet()}，逐个 {@code event.register(ModelResourceLocation.standalone(loc))}，
 *       把它们排队去烘焙 —— <b>只登记调用那一刻已经在 {@code ALL} 里的模型</b>；</li>
 *   <li>{@code PartialModelEventHandler.onBakingCompleted} 在 {@code ModelEvent.BakingCompleted} 时
 *       把静态字段 {@code PartialModel.populateOnInit} 置为 {@code true}，
 *       并用烘焙结果填充 {@code ALL} 里已有条目的 {@code bakedModel}；</li>
 *   <li>此后（{@code populateOnInit == true}）再执行 {@code new PartialModel(loc)}，
 *       构造函数会直接 {@code FlwLibXplat.INSTANCE.getBakedModel(ModelManager, loc)} ——
 *       而本模组的这些 partial 模型<b>没有任何 blockstate / 物品模型引用它们</b>
 *       （已逐个核对 {@code assets/vs_clockwork}），没被排队烘焙过，
 *       于是 {@code ModelManager} 只能返回 {@code missingno}，实机看到的就是黑紫方块。</li>
 * </ol>
 *
 * <p>关键点：这些 partial 类只被各自的 BER / 物品渲染器引用，而渲染器（Registrate 的
 * {@code .renderer(() -> X::new)}）是懒加载的，首次实例化发生在进世界之后 —— 也就是烘焙之后。
 * 如果只依赖它们自己的静态初始化，第 2 步早就跑完了，第 4 步必然发生。
 *
 * <h2>写法依据（与上游一致，不是自创）</h2>
 * <p>Create 6.0.10 自己就是这么做的：{@code CreateClient.clientInit(FMLClientSetupEvent)} 里调
 * {@code AllPartialModels.init()}，而 {@code AllPartialModels.init()} 的字节码只有一个 {@code return}
 * —— 它存在的唯一意义就是触发 {@code <clinit>}（已 javap 核实）。
 * 本工程原先的 {@code WingClientSetup} / {@code GravitronClientSetup} 也是同一写法，故此处统一收口。
 *
 * <h2>时机</h2>
 * <p>{@code FMLClientSetupEvent} 由 {@code CommonModLoader.load()} 里
 * {@code ModLoader.dispatchParallelEvent("Sided setup", ..., FMLClientSetupEvent::new)} 派发，
 * 而它整段跑在 {@code ClientModLoader.onResourceReload} 这个资源重载监听器里，
 * 早于 {@code ModelManager} 的 {@code ModelBakery} 构造（{@code RegisterAdditional} 在那里派发）。
 * 该事件经 {@code ParallelDispatchEvent} → {@code ModLifecycleEvent} 实现 {@code IModBusEvent}
 * （javap 核实），所以下面用 {@code @EventBusSubscriber} 不写 {@code bus}：NeoForge 的
 * {@code AutomaticEventSubscriber.inject} 是按方法参数类型是否 {@code IModBusEvent} 分流的，
 * 根本不读 {@code bus} 属性（已反编译核实），而 {@code bus} 本身已被标记待删除。
 *
 * <p>本类只在客户端加载（{@code value = Dist.CLIENT}），专用服务器上 Flywheel 的
 * {@code PartialModel} 也不会被触碰。
 */
@EventBusSubscriber(modid = ClockworkMod.MOD_ID, value = Dist.CLIENT)
public final class CWClientSetup {

    private CWClientSetup() {
    }

    /**
     * 触发所有 partial 类的静态初始化。
     *
     * <p>每个 {@code init()} 都是空方法，调用它们是"幂等的"：真正的副作用来自 JVM 的类初始化
     * （{@code <clinit>} 里的 {@code PartialModel.of} → {@code ALL.computeIfAbsent}），
     * 每个类只会发生一次，重复调用不会再创建 PartialModel。
     *
     * <p>新增 partial 类时<b>必须同时加到这个列表里</b>，否则该类就只能等到首次渲染才初始化，
     * 结果是黑紫。这也是本类存在的意义：把"哪些 partial 需要提前登记"收敛成一个可核对的清单。
     */
    @SubscribeEvent
    public static void onClientSetup(final FMLClientSetupEvent event) {
        // 方块侧：只被 BER 引用的 partial（无任何 blockstate 引用）
        BladePartials.init();          // 叶片三段（窄叶 / 宽叶），被 BladeControllerRenderer 使用
        ContraptionPartials.init();    // 陀螺底板 + 物理轴承轴/爪/四翼，被 GyroRenderer / PhysBearingRenderer 使用
        ClockPartialModels.init();     // 落地钟三根指针，被 ClockRenderer 使用
        CompressorPartials.init();     // 空压机输入轴 / 风箱布 / 顶板，被 AirCompressorRenderer 使用
        ExtendonPartials.init();       // 伸缩杆卷轴支架 / 活塞筒，被 ExtendonRenderer 使用
        ResistorPartials.init();       // 红石电阻指示条，被 RedstoneResistorRenderer 使用
        AltMeterPartials.init();       // 高度计红石指示条，被 AltMeterRenderer 使用
        FlapBearingPartials.init();    // 襟翼轴承顶盖，被 FlapBearingRenderer 使用（两个襟翼轴承共用）
        DuctPartials.init();           // 气体导管接口圈 / 泄漏面 / 收口环，被 DuctRenderer 使用
        ValveDuctPartials.init();      // 气体阀门开度指针，被 ValveDuctRenderer 使用
        GasNozzlePartials.init();      // 气嘴表盘与叶轮轴，被 GasNozzleRenderer 使用
        DeliveryCannonPartials.init(); // 交付加农炮炮座/转台/炮管/天线，被 DeliveryCannonRenderer 使用
        GasCrafterPartials.init();     // 气体合成器四段，被 GasCrafterRenderer 使用
        GasEnginePartials.init();      // 燃气机内腔发光体，被 GasEngineRenderer 使用
        DuctTankPartials.init();       // 气体储罐罐内气体体积，被 DuctTankRenderer 使用
        SlickerPartials.init();        // 减摩方块黏液，被 SlickerRenderer 使用
        WingPartials.init();           // 机翼三块翼面，被 WingColorRenderer 使用
        // 物品侧：只被物品渲染器引用的 partial
        GravitronPartials.init();      // 重力枪爪子 / 表盘指针 / 过载特效，被 GravitronItemRenderer 使用
        DrillPartials.init();          // 手持动力钻的钻头与齿轮，被 HandheldDrillRenderer 使用
        SawPartials.init();            // 手持切割锯的锯片与齿轮，被 HandheldSawRenderer 使用
        WanderwandPartials.init();     // 游荡法杖的杖头水晶三层与法阵环，被 WanderwandItemRenderer 使用
    }
}
