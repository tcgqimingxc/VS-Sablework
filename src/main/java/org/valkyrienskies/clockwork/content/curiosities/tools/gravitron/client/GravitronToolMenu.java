package org.valkyrienskies.clockwork.content.curiosities.tools.gravitron.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.AllKeys;
import com.simibubi.create.foundation.gui.AllIcons;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.valkyrienskies.clockwork.ClockworkMod;
import org.valkyrienskies.clockwork.content.curiosities.tools.gravitron.GravitronTools;

/**
 * 重力枪的模式轮盘（旧版 {@code GravitronSelectionScreen.kt} 的 1.21.1 版）。
 *
 * <p><b>为什么不是"径向"圆环</b>：Create 6.0.10 里已经没有 {@code AbstractRadialMenu} 了
 * （javap 核实：{@code RadialWrenchMenu} 直接继承 {@code AbstractSimiScreen}，
 * 是个"扇区 + 滚轮"的普通 Screen），旧版 Clockwork 的工具菜单本来也是
 * "屏幕底部一排图标 + 按住键滚轮切换"的样式。因此这里照抄 {@code RadialWrenchMenu} 的做法：
 * 继承 {@link AbstractSimiScreen}，{@code mouseScrolled} 循环、松开 {@code AllKeys.TOOL_MENU} 确认、
 * {@code tick()} 里检查手持物。
 *
 * <p>交互：按住 <b>左 ALT</b>（即 Create 的 {@code AllKeys.TOOL_MENU}，用户给的截图就是这个键）
 * 打开 → 滚轮切换 → 松开按键（或左键）确认 → 选中项通过
 * {@link org.valkyrienskies.clockwork.content.curiosities.tools.gravitron.GravitronToolPacket}
 * 发给服务端并显示行动栏文字。右键直接取消。
 *
 * <p>面板背景沿用旧版的 {@code textures/gui/overlay.png}（16×16 拉伸），图标取
 * {@code textures/gui/icons.png} 第 0 行的 16×16 图标（新增的 LOCK 模式那张表里没有，
 * 改用 Create 的 {@code AllIcons.I_CONFIG_LOCKED}）；这里不画屏幕暗化，
 * 保证选模式时还能看见世界（径向菜单的常规做法）。
 */
public class GravitronToolMenu extends AbstractSimiScreen {

    private static final ResourceLocation ICONS =
            ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, "textures/gui/icons.png");
    private static final ResourceLocation PANEL =
            ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, "textures/gui/overlay.png");
    /** 图标边长与每个模式的横向间距（旧版 GravitronSelectionScreen 同值）。 */
    private static final int ICON_SIZE = 16;
    private static final int SLOT = 50;
    private static final int PANEL_HEIGHT = 30;
    private static final int LABEL_COLOR = 0xDDCCFF;
    private static final int DESCRIPTION_COLOR = 0xCCCCDD;
    /** 旧版把选中的图标向上抬 10 px。 */
    private static final int SELECTED_LIFT = 10;

    private final GravitronTools[] tools = GravitronTools.values();
    private GravitronTools selected;

    public GravitronToolMenu(final GravitronTools initial) {
        super(Component.empty());
        this.selected = initial;
        setWindowSize(Math.max(tools.length * SLOT + PANEL_HEIGHT, 220), PANEL_HEIGHT);
    }

    @Override
    public void tick() {
        super.tick();
        // 手上没有重力枪就自动关闭：与旧版 GravitronHandler.tick 的 active 判据一致
        if (!GravitronToolMenuHandler.holdingGravitron()) {
            Minecraft.getInstance().setScreen(null);
        }
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
        // 方向照抄 Create 的 RadialWrenchMenu：向下滚 = 后一个
        select(selected.cycle((int) Math.round(-Math.signum(scrollY))));
        return true;
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (button == 0) {
            onClose();                                                  // 左键 = 确认
            return true;
        }
        if (button == 1) {
            Minecraft.getInstance().setScreen(null);                    // 右键 = 取消
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyReleased(final int keyCode, final int scanCode, final int modifiers) {
        // 松开"打开轮盘的那个键"= 确认（Create 的 RadialWrenchMenu 就是这么收尾的）
        if (AllKeys.TOOL_MENU.getKeybind().isActiveAndMatches(InputConstants.getKey(keyCode, scanCode))) {
            onClose();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        GravitronToolMenuHandler.onMenuClosed();
        super.removed();
    }

    @Override
    protected void renderWindow(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTicks) {
        final int rowWidth = tools.length * SLOT;
        final int rowLeft = (width - rowWidth) / 2;
        final int rowTop = height - 75;

        // 面板：旧版把 16×16 的 overlay.png 直接拉伸成整条
        graphics.blit(PANEL, rowLeft - 15, rowTop, 0f, 0f, rowWidth + PANEL_HEIGHT, PANEL_HEIGHT, 16, 16);

        for (int i = 0; i < tools.length; i++) {
            final GravitronTools tool = tools[i];
            final boolean isSelected = tool == selected;
            final int iconX = rowLeft + i * SLOT + (SLOT - ICON_SIZE) / 2;
            final int iconY = rowTop + (PANEL_HEIGHT - ICON_SIZE) / 2 - (isSelected ? SELECTED_LIFT : 0);
            if (tool == GravitronTools.LOCK) {
                // icons.png 里没有锁形图标，直接用 Create 的（ScreenElement#render 画的就是 16×16）
                AllIcons.I_CONFIG_LOCKED.render(graphics, iconX, iconY);
            } else {
                graphics.blit(ICONS, iconX, iconY, 0, tool.iconU(), 0f, ICON_SIZE, ICON_SIZE, 256, 256);
            }
            if (isSelected) {
                graphics.drawCenteredString(font, tool.displayName(), iconX + ICON_SIZE / 2, iconY - 10, LABEL_COLOR);
            }
        }

        // 提示行：说明当前选中的模式干什么（旧版画 4 行，空行跳过）
        final Component hint = Component.translatable("vs_clockwork.gui.toolmenu.cycle");
        graphics.drawCenteredString(font, hint, width / 2, rowTop - 12, LABEL_COLOR);
        int textY = rowTop + PANEL_HEIGHT + 6;
        for (int line = 0; line < 4; line++) {
            final Component description = Component.translatable(selected.descriptionKey(line));
            if (description.getString().isEmpty()) {
                continue;
            }
            graphics.drawString(font, description, rowLeft, textY, DESCRIPTION_COLOR);
            textY += 10;
        }
    }

    @Override
    public void renderBackground(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTicks) {
        // 不画暗化/模糊底：轮盘要能看清世界（Create 的 RadialWrenchMenu 也是自己接管这一层）
    }

    @Override
    protected void renderMenuBackground(final GuiGraphics graphics) {
        // AbstractSimiScreen 会先调这里铺一层暗色菜单底，轮盘不需要
    }

    private void select(final GravitronTools tool) {
        if (tool == selected) {
            return;
        }
        selected = tool;
        GravitronToolMenuHandler.select(tool);      // 立刻同步给服务端 → 服务端回行动栏文字
    }
}
