package org.valkyrienskies.clockwork.content.logistics.gas.compat.jei;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.TooltipFlag;
import org.valkyrienskies.clockwork.content.logistics.gas.GasPhysics;
import org.valkyrienskies.clockwork.content.logistics.gas.GasType;
import org.valkyrienskies.clockwork.content.logistics.gas.GasTypes;

import java.util.List;

/**
 * JEI 的"气体"原料类型。
 *
 * <p>对应旧版 {@code KelvinGasIngredient} + {@code GasIngredientType} + {@code GasIngredientRenderer}
 * （三者都在 Kelvin 库里，1.21.1 不存在）。移植差异：
 * <ul>
 *   <li>旧版气体原料是"气体种类 + 质量"，质量由 Kelvin 的 {@code GasTypeRegistry} 解析；
 *       这里直接持有 {@link GasType} 实例（本模组的气体表是静态的，不需要注册表查找）。</li>
 *   <li>旧版渲染器画的是 Kelvin 提供的 16×16 气体贴图；本移植改为<b>用气体自身颜色画色块</b>，
 *       这样不需要往资源包里加 9 张新贴图（{@link GasType#color()} 本来就是护目镜/粒子的配色）。</li>
 *   <li>气体条目的 tooltip 里额外给出<b>压力与温度</b>的读数（旧版只在管道统计里显示）。</li>
 * </ul>
 *
 * <p>本类只被 {@link ClockworkJeiPlugin} 触碰，而后者由 JEI 自己加载 —— 没装 JEI 时这两个类
 * 都不会被类加载，因此不会出现 NoClassDefFoundError（详见插件类注释）。
 *
 * <p><b>必须由 {@link ClockworkJeiPlugin#registerIngredients} 把 {@link #TYPE} 注册给 JEI</b>：
 * JEI 只认登记过的原料类型，否则往布局里 {@code addIngredient(TYPE, ...)} 时
 * {@code RegisteredIngredients} 会抛 {@code IllegalArgumentException: Unknown ingredient type}
 * （实机日志里 GasInfoJeiCategory 的 9 种气体各刷一条 ERROR）。
 */
public record GasIngredientJei(GasType gas, double massKg, double temperature) {

    /** JEI 原料类型；uid 只用于内部标识，与 mod 的 resource location 无关。 */
    public static final IIngredientType<GasIngredientJei> TYPE = new IIngredientType<>() {
        @Override
        public Class<? extends GasIngredientJei> getIngredientClass() {
            return GasIngredientJei.class;
        }

        @Override
        public String getUid() {
            return "vs_clockwork:gas";
        }
    };

    /** 气体原料的序列化 codec：JEI 注册原料类型时要一个（本类三个分量）。 */
    public static final Codec<GasIngredientJei> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("gas").forGetter(ingredient -> ingredient.gas().id()),
            Codec.DOUBLE.fieldOf("mass_kg").forGetter(GasIngredientJei::massKg),
            Codec.DOUBLE.fieldOf("temperature").forGetter(GasIngredientJei::temperature)
    ).apply(instance, GasIngredientJei::ofCodecFields));

    /** codec 三分量组装；气体 id 一定来自本模组的气体表，查不到说明数据被改坏了，直接抛错而不是造出 null 气体。 */
    private static GasIngredientJei ofCodecFields(final ResourceLocation gasId, final double massKg,
                                                 final double temperature) {
        final GasType gas = GasTypes.byId(gasId);
        if (gas == null) {
            throw new IllegalArgumentException("未知气体: " + gasId);
        }
        return new GasIngredientJei(gas, massKg, temperature);
    }

    /** 配方里的气体原料（温度未知，显示成环境温度）。 */
    public static GasIngredientJei of(final GasType gas, final double massKg) {
        return new GasIngredientJei(gas, massKg, GasPhysics.ambientTemperature());
    }

    /** 气体质量文本：小于 1 kg 用克（旧版 {@code GasAmount} 的 g/kg 两种单位）。 */
    public static String formatMass(final double massKg) {
        return Math.abs(massKg) < 1.0 ? String.format("%.1f g", massKg * 1000.0) : String.format("%.3f kg", massKg);
    }

    /** 压力文本：小于 1 kPa 用 Pa（旧版 {@code DuctUnits} 的等价简化，本移植不提供单位切换）。 */
    public static String formatPressure(final double pascal) {
        return pascal < 1000.0 ? String.format("%.1f Pa", pascal) : String.format("%.2f kPa", pascal / 1000.0);
    }

    /** 温度文本，摄氏度是玩家习惯的读数（旧版默认显示摄氏）。 */
    public static String formatTemperature(final double kelvin) {
        return String.format("%.1f K (%.1f °C)", kelvin, kelvin - 273.15);
    }

    /** 用气体颜色画一个带边框的色块，作为气体图标（渲染器与绘制工具共用）。 */
    static void drawGasIcon(final GuiGraphics graphics, final GasType gas, final int x, final int y) {
        graphics.fill(x, y, x + 16, y + 16, 0xFF000000 | gas.color());
        graphics.fill(x, y, x + 16, y + 1, 0xFF202020);
        graphics.fill(x, y + 15, x + 16, y + 16, 0xFF202020);
        graphics.fill(x, y + 1, x + 1, y + 15, 0xFF202020);
        graphics.fill(x + 15, y + 1, x + 16, y + 15, 0xFF202020);
        graphics.drawString(Minecraft.getInstance().font, gas.displayName().substring(0, 1), x + 5, y + 4,
                0xFFFFFFFF, true);
    }

    /** 气体条目的 tooltip：名称、编号、质量、压力、温度。 */
    static void addGasTooltip(final ITooltipBuilder tooltip, final GasIngredientJei ingredient) {
        final GasType gas = ingredient.gas();
        tooltip.add(Component.literal(gas.displayName()).withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.literal(gas.id().toString()).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("vs_clockwork.jei.gas.mass", formatMass(ingredient.massKg()))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("vs_clockwork.jei.gas.pressure",
                formatPressure(GasPhysics.ambientPressure())).withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("vs_clockwork.jei.gas.temperature",
                formatTemperature(ingredient.temperature())).withStyle(ChatFormatting.GOLD));
    }

    /** 往配方布局里加一个气体槽（背景 + 自定义渲染器 + tooltip）。 */
    public static IRecipeSlotBuilder addSlot(final IRecipeLayoutBuilder builder, final RecipeIngredientRole role,
                                             final int x, final int y, final GasIngredientJei ingredient,
                                             final IDrawable background) {
        return builder.addSlot(role, x, y)
                .setBackground(background, -1, -1)
                .setCustomRenderer(TYPE, new Renderer())
                .addIngredient(TYPE, ingredient)
                .addRichTooltipCallback((slotView, tooltip) -> addGasTooltip(tooltip, ingredient));
    }

    /**
     * JEI 原料助手：注册类型时必需，负责 uid / 名称 / 数量。
     *
     * <p>uid 只取气体 id（不含质量与温度）：这样 JEI 搜索里的"一种气体"能匹配到所有引用它的槽位
     * （同一气体的配方用量各不相同）。数量用克表示，{@code copyWithAmount} 按同一单位换算回质量。
     *
     * <p>{@code getUniqueId} 在 JEI 19.x 里被标记为"将来移除"（新写法是 {@code getUid}），
     * 但它目前仍是接口里的抽象方法，必须实现，所以这里抑制那条移除警告。
     */
    @SuppressWarnings("removal")
    public static final class Helper implements IIngredientHelper<GasIngredientJei> {

        @Override
        public IIngredientType<GasIngredientJei> getIngredientType() {
            return TYPE;
        }

        @Override
        public String getDisplayName(final GasIngredientJei ingredient) {
            return ingredient.gas().displayName();
        }

        @Override
        public String getUniqueId(final GasIngredientJei ingredient, final UidContext context) {
            return ingredient.gas().id().toString();
        }

        @Override
        public ResourceLocation getResourceLocation(final GasIngredientJei ingredient) {
            return ingredient.gas().id();
        }

        @Override
        public GasIngredientJei copyIngredient(final GasIngredientJei ingredient) {
            return ingredient;      // record 不可变，复制就是自身
        }

        @Override
        public String getErrorInfo(final GasIngredientJei ingredient) {
            return "gas=" + ingredient.gas().id() + ", massKg=" + ingredient.massKg();
        }

        @Override
        public long getAmount(final GasIngredientJei ingredient) {
            return Math.round(ingredient.massKg() * 1000.0);
        }

        @Override
        public GasIngredientJei copyWithAmount(final GasIngredientJei ingredient, final long amount) {
            return new GasIngredientJei(ingredient.gas(), amount / 1000.0, ingredient.temperature());
        }
    }

    /** 气体图标渲染器；tooltip 也在这里给一份，保证从 JEI 搜索结果看气体时同样有读数。 */
    public static final class Renderer implements IIngredientRenderer<GasIngredientJei> {

        @Override
        public void render(final GuiGraphics graphics, final GasIngredientJei ingredient) {
            drawGasIcon(graphics, ingredient.gas(), 0, 0);
        }

        @Override
        public void render(final GuiGraphics graphics, final GasIngredientJei ingredient, final int posX,
                           final int posY) {
            drawGasIcon(graphics, ingredient.gas(), posX, posY);
        }

        @Override
        public List<Component> getTooltip(final GasIngredientJei ingredient, final TooltipFlag tooltipFlag) {
            return List.of(
                    Component.literal(ingredient.gas().displayName()).withStyle(ChatFormatting.WHITE),
                    Component.translatable("vs_clockwork.jei.gas.mass", formatMass(ingredient.massKg()))
                            .withStyle(ChatFormatting.GRAY),
                    Component.translatable("vs_clockwork.jei.gas.temperature",
                            formatTemperature(ingredient.temperature())).withStyle(ChatFormatting.GOLD));
        }

        @Override
        public void getTooltip(final ITooltipBuilder tooltip, final GasIngredientJei ingredient,
                               final TooltipFlag tooltipFlag) {
            addGasTooltip(tooltip, ingredient);
        }

        @Override
        public int getWidth() {
            return 16;
        }

        @Override
        public int getHeight() {
            return 16;
        }
    }
}
