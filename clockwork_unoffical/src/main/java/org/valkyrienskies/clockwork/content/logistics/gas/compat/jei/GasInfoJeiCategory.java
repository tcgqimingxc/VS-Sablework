package org.valkyrienskies.clockwork.content.logistics.gas.compat.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.valkyrienskies.clockwork.content.logistics.gas.GasPhysics;
import org.valkyrienskies.clockwork.content.logistics.gas.GasType;

/**
 * JEI 类别：气体资料（每种气体一页，给出压力 / 温度 / 密度等物性）。
 *
 * <p>旧版没有这个类别（旧版把气体数据放在管道统计 {@code DuctStatsCategory} 与 Kelvin 的气体
 * tooltip 里）。本移植的气体表在 {@link org.valkyrienskies.clockwork.content.logistics.gas.GasTypes}，
 * 玩家没有别的途径看到这些数值，因此单开一页；<b>压力与温度</b>两项是任务要求的显示内容。
 *
 * <p>每条"配方"就是 {@link GasType} 本身。
 */
public final class GasInfoJeiCategory implements IRecipeCategory<GasInfoJeiCategory.GasInfo> {

    public static final mezz.jei.api.recipe.RecipeType<GasInfo> TYPE =
            mezz.jei.api.recipe.RecipeType.create("vs_clockwork", "gas_info", GasInfo.class);

    private final IDrawable icon;
    private final IDrawable slotBackground;

    /** 一条气体资料。 */
    public record GasInfo(GasType gas) {
    }

    public GasInfoJeiCategory(final IGuiHelper guiHelper, final IDrawable icon) {
        this.icon = icon;
        this.slotBackground = guiHelper.getSlotDrawable();
    }

    @Override
    public mezz.jei.api.recipe.RecipeType<GasInfo> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("vs_clockwork.jei.category.gas_info");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return 160;
    }

    @Override
    public int getHeight() {
        return 50;
    }

    @Override
    public void setRecipe(final IRecipeLayoutBuilder builder, final GasInfo recipe, final IFocusGroup focuses) {
        GasIngredientJei.addSlot(builder, RecipeIngredientRole.OUTPUT, 4, 4,
                new GasIngredientJei(recipe.gas(), 1.0, GasPhysics.ambientTemperature()), slotBackground);
    }

    @Override
    public void draw(final GasInfo recipe, final IRecipeSlotsView slots, final GuiGraphics graphics,
                     final double mouseX, final double mouseY) {
        final GasType gas = recipe.gas();
        final Minecraft minecraft = Minecraft.getInstance();
        int lineY = 4;
        graphics.drawString(minecraft.font, Component.literal(gas.displayName()).withStyle(ChatFormatting.WHITE),
                24, lineY, 0xFFFFFFFF, false);
        graphics.drawString(minecraft.font, Component.literal(gas.id().toString()).withStyle(ChatFormatting.DARK_GRAY),
                24, lineY + 9, 0xFFFFFFFF, false);
        lineY += 22;
        graphics.drawString(minecraft.font, Component.translatable("vs_clockwork.jei.gas.pressure",
                        GasIngredientJei.formatPressure(GasPhysics.ambientPressure())).withStyle(ChatFormatting.BLUE),
                4, lineY, 0xFFFFFFFF, false);
        graphics.drawString(minecraft.font, Component.translatable("vs_clockwork.jei.gas.temperature",
                        GasIngredientJei.formatTemperature(GasPhysics.ambientTemperature()))
                .withStyle(ChatFormatting.GOLD), 4, lineY + 10, 0xFFFFFFFF, false);
        graphics.drawString(minecraft.font, Component.translatable("vs_clockwork.jei.gas.density",
                        String.format("%.3f kg/m³", gas.density())).withStyle(ChatFormatting.GRAY),
                4, lineY + 20, 0xFFFFFFFF, false);
        graphics.drawString(minecraft.font, Component.translatable("vs_clockwork.jei.gas.specific_heat",
                        String.format("%.1f J/(kg·K)", gas.specificHeat())).withStyle(ChatFormatting.GRAY),
                4, lineY + 30, 0xFFFFFFFF, false);
    }
}
