package org.valkyrienskies.clockwork.content.curiosities;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.util.entry.RegistryEntry;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.tags.BlockTags;
import org.valkyrienskies.clockwork.ClockworkMod;
import org.valkyrienskies.clockwork.ClockworkRegistrate;
import org.valkyrienskies.clockwork.content.curiosities.altmeter.AltMeterBlock;
import org.valkyrienskies.clockwork.content.curiosities.altmeter.AltMeterBlockEntity;
import org.valkyrienskies.clockwork.content.curiosities.altmeter.AltMeterDisplaySource;
import org.valkyrienskies.clockwork.content.curiosities.altmeter.client.AltMeterRenderer;
import org.valkyrienskies.clockwork.content.curiosities.clock.ClockBlock;
import org.valkyrienskies.clockwork.content.curiosities.clock.ClockBlockEntity;
import org.valkyrienskies.clockwork.content.curiosities.solver.SolverBlock;
import org.valkyrienskies.clockwork.content.curiosities.solver.SolverBlockEntity;
import org.valkyrienskies.clockwork.index.CWCreativeTabs;

/**
 * 仪表 / 时钟 / 解算器切片注册入口。
 *
 * <p>对应旧版 {@code ClockworkBlocks.kt} 中 {@code ALT_METER}、{@code CLOCK}、{@code SOLVER} 三项
 * （以及 {@code ClockworkBlockEntities} 里对应的三个方块实体、{@code ClockworkDisplaySources.ALT_METER}）。
 * 由 {@code CWCuriositiesIndex#register()} 调用。
 *
 * <p>资源沿用旧版 {@code assets/vs_clockwork/**}：{@code alt_meter} 与 {@code clock} 的方块状态、
 * {@code alt_meter} 的 DisplaySource 配置界面都直接对接。
 *
 * <p>创造标签差异：旧版把 clock / solver 的 {@code .tab(...)} 注释掉了（只能 {@code /give} 获得），
 * 本移植把它们放进主标签，方便测试；alt_meter 保持在物理机械标签（与旧版一致）。
 */
@SuppressWarnings("removal")
public final class CWInstrumentsIndex {

    private static final ClockworkRegistrate REGISTRATE = ClockworkMod.REGISTRATE;

    /** 高度计的信息面板数据源。必须在使用它的方块之前初始化。 */
    public static final RegistryEntry<DisplaySource, AltMeterDisplaySource> ALT_METER_SOURCE =
            REGISTRATE.simple("alt_meter", CreateRegistries.DISPLAY_SOURCE, AltMeterDisplaySource::new);

    /** 高度计。 */
    public static final BlockEntry<AltMeterBlock> ALT_METER = REGISTRATE
            .block("alt_meter", AltMeterBlock::new)
            .initialProperties(SharedProperties::stone)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutout)
            .transform(DisplaySource.displaySource(ALT_METER_SOURCE))
            .tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .item()
            .tab(CWCreativeTabs.physicalitiesKey())
            .build()
            .register();

    public static final BlockEntityEntry<AltMeterBlockEntity> ALT_METER_BE = REGISTRATE
            .blockEntity("alt_meter", AltMeterBlockEntity::new)
            .validBlocks(ALT_METER)
            // 旧版 ClockworkBlockEntities.kt:314 的 .renderer { AltMeterRenderer(...) }：
            // 画表盘上的红石指示条（blockstate 只引用表壳，指示条只能由 BER 画）
            .renderer(() -> AltMeterRenderer::new)
            .register();

    /** 落地钟。 */
    public static final BlockEntry<ClockBlock> CLOCK = REGISTRATE
            .block("clock", ClockBlock::new)
            .initialProperties(SharedProperties::wooden)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutoutMipped)
            .tag(BlockTags.MINEABLE_WITH_AXE)
            .item()
            .tab(CWCreativeTabs.baseKey())
            .build()
            .register();

    public static final BlockEntityEntry<ClockBlockEntity> CLOCK_BE = REGISTRATE
            .blockEntity("clock", ClockBlockEntity::new)
            .validBlocks(CLOCK)
            // 渲染器类只在客户端被实例化（Registrate 的 renderer 供应商是懒加载的）。
            .renderer(() -> org.valkyrienskies.clockwork.content.curiosities.clock.client.ClockRenderer::new)
            .register();

    /** 解算器。 */
    public static final BlockEntry<SolverBlock> SOLVER = REGISTRATE
            .block("solver", SolverBlock::new)
            .initialProperties(SharedProperties::netheriteMetal)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutout)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .item()
            .tab(CWCreativeTabs.baseKey())
            .build()
            .register();

    public static final BlockEntityEntry<SolverBlockEntity> SOLVER_BE = REGISTRATE
            .blockEntity("solver", SolverBlockEntity::new)
            .validBlocks(SOLVER)
            // 旧版 ClockworkBlockEntities.kt 同样登记了 SolverRenderer；渲染器类只在客户端实例化。
            .renderer(() -> org.valkyrienskies.clockwork.content.curiosities.solver.client.SolverRenderer::new)
            .register();

    private CWInstrumentsIndex() {
    }

    /** 触发类加载（由 {@code CWCuriositiesIndex} 调用）。 */
    public static void register() {
    }
}
