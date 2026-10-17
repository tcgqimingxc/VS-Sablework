package org.valkyrienskies.clockwork.integration.cc;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.valkyrienskies.clockwork.content.contraptions.CWContraptionsIndex;
import org.valkyrienskies.clockwork.content.curiosities.CWInstrumentsIndex;
import org.valkyrienskies.clockwork.content.logistics.gas.CWGasIndex;

import java.util.function.Function;

/**
 * CC:Tweaked 外设注册入口。
 *
 * <p>对应旧版 {@code forge/CCTweakedPeripheralProvider.kt}（Forge 1.20.1 的
 * {@code registerPeripheralProvider} 写法）+ 各平台 {@code getPeripheralCommon} 的
 * "方块实体 → 外设"映射。移植差异：
 * <ul>
 *   <li>NeoForge 1.21.1 没有 Forge 的 IPeripheralProvider，改为在
 *       {@link RegisterCapabilitiesEvent} 里注册 {@link PeripheralCapability}（这是 CC:Tweaked
 *       1.21.1 自己的推荐做法，参考 {@code Simulated-Project-main} 的
 *       {@code NeoForgeSimPeripheralService.registerCapabilities}）。</li>
 *   <li>旧版给 8 类方块注册了外设（高度计 / 物理轴承 / 螺旋桨轴承 / 舵翼轴承 / 气体喷嘴 /
 *       阀门 / 燃气热源）。本移植按任务书只做最有价值的 3 个：
 *       {@code alt_meter}（高度计）、{@code phys_bearing}（物理轴承）、{@code valve_duct}（气体阀门）。</li>
 * </ul>
 *
 * <p><b>为什么必须"条件式惰性加载"</b>（改代码前请先读这段）：
 * <ol>
 *   <li>本类（以及同包的三个外设类）是全模组唯一引用 {@code dan200.computercraft.**} 的地方，
 *       而 CC 在构建脚本里是 {@code compileOnly}，发布包里<b>不含</b> CC 的类。</li>
 *   <li>因此本类<b>只能在 CC 存在时才被加载</b>：入口由 {@code ClockworkMod} 用
 *       {@code ModList.get().isLoaded("computercraft")} 守住（{@link #register}）。
 *       没装 CC 时该分支不执行，JVM 惰性解析 —— 本类及其 CC 类型引用都不会被解析。</li>
 *   <li><b>绝不能</b>给本类加 {@code @Mod("computercraft")}：那等于在我们的 jar 里为别的 mod
 *       声明入口点，FML 会在扫描阶段直接拒绝加载整个 Clockwork（实机崩溃：
 *       {@code contains mod entrypoint class ... for mod with id computercraft, which does not exist}）。
 *       jar 里只允许出现我们自己的 {@code @Mod(ClockworkMod.MOD_ID)}。</li>
 * </ol>
 */
public final class ClockworkComputerCraft {

    /**
     * 由 {@code ClockworkMod} 在确认 {@code computercraft} 已加载后调用，把
     * {@code @SubscribeEvent} 挂到我们自己的 mod 总线上（NeoForge 的
     * {@code IEventBus.register(Class)} 注册的是类里的静态监听方法）。
     */
    public static void register(final IEventBus modBus) {
        modBus.register(ClockworkComputerCraft.class);
    }

    private ClockworkComputerCraft() {
    }

    @SubscribeEvent
    public static void registerCapabilities(final RegisterCapabilitiesEvent event) {
        final var capability = PeripheralCapability.get();
        register(event, capability, CWInstrumentsIndex.ALT_METER_BE, AltMeterPeripheral::new);
        register(event, capability, CWContraptionsIndex.PHYS_BEARING_BE, PhysBearingPeripheral::new);
        register(event, capability, CWGasIndex.VALVE_DUCT_BE, ValveDuctPeripheral::new);
    }

    /**
     * 把一个方块实体类型接到外设上。
     *
     * <p>沿用 {@code Simulated} 的写法：忽略方向参数（这三个方块的外设 6 面都一样）。
     */
    private static <T extends BlockEntity> void register(final RegisterCapabilitiesEvent event,
                                                         final BlockCapability<IPeripheral, Direction> capability,
                                                         final DeferredHolder<BlockEntityType<?>, ? extends BlockEntityType<T>> type,
                                                         final Function<T, IPeripheral> factory) {
        event.registerBlockEntity(capability, type.get(), (blockEntity, direction) -> factory.apply(blockEntity));
    }
}
