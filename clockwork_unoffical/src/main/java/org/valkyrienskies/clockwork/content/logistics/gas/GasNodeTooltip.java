package org.valkyrienskies.clockwork.content.logistics.gas;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.valkyrienskies.clockwork.content.logistics.gas.engine.HeatEngineLogic;

import java.util.List;

/**
 * 气体节点的共用护目镜提示。
 *
 * <p>对应旧版 {@code IClockworkNodeBE.heatableGoggleTooltip}（含 {@code util/gui/DuctTextUtil} 的单位换算）。
 * 与旧版的差异：
 * <ul>
 *   <li>旧版有 kPa / kJ / 摄氏 等单位切换与简写（{@code DuctUnits} + 客户端配置）；
 *       本移植统一用 Pa / K / kg 直出，避免把单位换算工具类也搬过来。</li>
 *   <li>旧版能读到整个 Kelvin 网络（含总容积）；这里只显示本节点的数据。</li>
 * </ul>
 */
public final class GasNodeTooltip {

    private GasNodeTooltip() {
    }

    public static boolean add(GasNode node, List<Component> tooltip, boolean isPlayerSneaking) {
        GasMix mix = node.gasMix();
        tooltip.add(Component.translatable("vs_clockwork.gui.ductInfo.title").withStyle(ChatFormatting.GRAY));

        double pressure = mix.pressure(node.gasCapacityKg());
        tooltip.add(Component.literal("    ")
                .append(Component.literal("Pressure: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.format("%.0f Pa", pressure)).withStyle(ChatFormatting.BLUE)));

        if (!mix.isEmpty()) {
            tooltip.add(Component.literal("    ")
                    .append(Component.literal("Temperature: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.format("%.1f K", mix.temperature())).withStyle(ChatFormatting.GOLD)));
            tooltip.add(Component.literal("    ")
                    .append(Component.literal("Thermal Energy: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.format("%.0f J", mix.thermalEnergy())).withStyle(ChatFormatting.RED)));
        }

        if (isPlayerSneaking || !mix.isEmpty()) {
            double total = mix.totalMass();
            for (GasType type : GasTypes.all()) {
                double mass = mix.massOf(type);
                if (mass <= 1.0E-6) {
                    continue;
                }
                tooltip.add(Component.literal("    ")
                        .append(Component.literal(type.displayName() + ": ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(Component.literal(String.format("%.3f kg", mass)).withStyle(ChatFormatting.GRAY)));
            }
            if (total > 0) {
                tooltip.add(Component.literal("    ")
                        .append(Component.literal("Total: ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(Component.literal(String.format("%.3f kg", total)).withStyle(ChatFormatting.GRAY)));
            }
        }
        return true;
    }

    /**
     * 追加热气机（斯特林机）的效率信息。
     *
     * <p>对应旧版 {@code EngineGoggleTooltip}：把温度效率画成进度条 + "距离下一档还差多少 K"。
     * 这里用文字表达同一信息（不引入旧版的条形图工具）。
     */
    public static void addEfficiency(List<Component> tooltip, float efficiency, double temperature,
                                     double temperatureIncrement) {
        int bars = Math.round(efficiency * 20.0F);
        tooltip.add(Component.literal("    ")
                .append(Component.literal("Efficiency: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("|".repeat(bars) + ".".repeat(20 - bars)).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(String.format(" %.0f%%", efficiency * 100)).withStyle(ChatFormatting.GREEN)));
        double untilNext = HeatEngineLogic.temperatureUntilNextTier(temperature, temperatureIncrement);
        if (untilNext > 0) {
            tooltip.add(Component.literal("    ")
                    .append(Component.literal("Next tier: ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(String.format("+%.1f K", untilNext)).withStyle(ChatFormatting.GRAY)));
        }
    }

    /**
     * 追加"这个方块是干什么的"说明行（纯装饰，服务端也会调用，所以只依赖方块 id）。
     *
     * <p>对应旧版 {@code IHaveDuctStats.getAdditionalInfoLines()}（旧版每个方块自己写两条硬编码
     * 文本）；这里统一从语言文件取 {@code block.vs_clockwork.<id>.function}，没有该条目就什么都不加。
     */
    public static void addFunctionLine(net.minecraft.world.level.block.Block block, List<Component> tooltip) {
        final net.minecraft.resources.ResourceLocation id =
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
        if (id == null) {
            return;
        }
        final String key = "block." + id.getNamespace() + "." + id.getPath() + ".function";
        tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
