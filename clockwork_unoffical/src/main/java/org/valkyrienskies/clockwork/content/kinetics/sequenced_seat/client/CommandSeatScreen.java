package org.valkyrienskies.clockwork.content.kinetics.sequenced_seat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.valkyrienskies.clockwork.ClockworkMod;
import org.valkyrienskies.clockwork.content.kinetics.sequenced_seat.CommandSeatBlockEntity;
import org.valkyrienskies.clockwork.content.kinetics.sequenced_seat.InputKey;
import org.valkyrienskies.clockwork.content.kinetics.sequenced_seat.SeatOperation;
import org.valkyrienskies.clockwork.content.kinetics.sequenced_seat.SeatRule;
import org.valkyrienskies.clockwork.content.kinetics.sequenced_seat.SeatRuleList;
import org.valkyrienskies.clockwork.content.kinetics.sequenced_seat.SeatRulesPacket;
import org.valkyrienskies.clockwork.content.kinetics.sequenced_seat.SeatValue;

/**
 * 命令座椅的规则编辑界面（旧版 {@code SequencedSeatScreen.kt}）。
 *
 * <p>用法：顶部四个标签切换要编辑的方向（前 / 右 / 后 / 左，以方块朝向为基准），
 * 下面 5 行是规则槽：每行可以勾选"按键组合"、选择操作、调整参数；关掉界面时把四张表发回服务端。
 *
 * <p>与旧版差异：旧版是 Create 的 {@code AbstractSimiScreen} + {@code ScrollInput} 等自制控件，
 * 这里按 Simulated 的 {@code NameplateScreen} 写法改成纯 {@code Screen} + 原版按钮，
 * 背景仍然用旧版的 {@code textures/gui/command_seat.png}（173×159）。
 * 旧版只给第 1~4 行画按键按钮（第 0 行固定为"没按键"的规则），这里保持一致；
 * 旧版会隐藏"没按键的行"，这里改为全部显示（更直观，行为不变）。
 */
public class CommandSeatScreen extends Screen {

    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, "textures/gui/command_seat.png");
    private static final int WINDOW_WIDTH = 173;
    private static final int WINDOW_HEIGHT = 159;

    /** 四个标签页的顺序与规则表对应关系（旧版用 {@code Rotation.values()}）。 */
    private static final Rotation[] TABS = {
            Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90};
    private static final String[] TAB_LABELS = {"\u25b2", "\u25b6", "\u25bc", "\u25c0"};

    private static final int ROW_TOP = 38;
    private static final int ROW_HEIGHT = 22;
    private static final int ROW_WIDGET_HEIGHT = 16;
    private static final int KEY_X = 6;
    private static final int KEY_STEP = 13;
    private static final int KEY_WIDTH = 12;
    private static final int OPERATION_X = 74;
    private static final int OPERATION_WIDTH = 46;
    private static final int MINUS_X = 122;
    private static final int VALUE_X = 136;
    private static final int VALUE_WIDTH = 20;
    private static final int PLUS_X = 158;
    private static final int STEP_WIDTH = 12;

    private final CommandSeatBlockEntity seat;
    private Rotation tab = Rotation.NONE;
    private int left;
    private int top;

    public CommandSeatScreen(final CommandSeatBlockEntity seat) {
        super(Component.translatable("block.vs_clockwork.command_seat"));
        this.seat = seat;
    }

    @Override
    protected void init() {
        this.left = (this.width - WINDOW_WIDTH) / 2;
        this.top = (this.height - WINDOW_HEIGHT) / 2;

        for (int i = 0; i < TABS.length; i++) {
            final Rotation candidate = TABS[i];
            this.addRenderableWidget(Button.builder(Component.literal(TAB_LABELS[i]), b -> {
                this.tab = candidate;
                this.scheduleRebuild();
            }).bounds(this.left + 6 + i * 41, this.top + 18, 40, 16).build());
        }

        final SeatRuleList list = this.seat.getList(this.tab);
        for (int row = 0; row < SeatRuleList.MAX_RULES; row++) {
            final int rowY = this.top + ROW_TOP + row * ROW_HEIGHT;
            this.addRowWidgets(list, row, rowY);
        }

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                .bounds(this.left + 126, this.top + 142, 41, 14).build());
    }

    private void addRowWidgets(final SeatRuleList list, final int row, final int rowY) {
        final SeatRule rule = list.getRule(row);
        // 第 0 行固定是"没按任何键"的规则，旧版也不给它按键按钮
        if (row > 0) {
            final InputKey[] keys = InputKey.values();
            for (int i = 0; i < keys.length; i++) {
                final InputKey key = keys[i];
                this.addRenderableWidget(Button.builder(Component.literal(keyLabel(key)), b -> {
                    if (rule.inputKeys().contains(key)) {
                        list.removeKey(row, key);
                    } else {
                        list.addKey(row, key);
                    }
                    this.scheduleRebuild();
                }).bounds(this.left + KEY_X + i * KEY_STEP, rowY, KEY_WIDTH, ROW_WIDGET_HEIGHT).build());
            }
        }

        this.addRenderableWidget(Button.builder(rule.operation().asComponent(), b -> {
            final SeatOperation[] operations = SeatOperation.values();
            list.setOperation(row, operations[(rule.operation().ordinal() + 1) % operations.length]);
            this.scheduleRebuild();
        }).bounds(this.left + OPERATION_X, rowY, OPERATION_WIDTH, ROW_WIDGET_HEIGHT).build());

        final SeatValue value = rule.value();
        if (value == null) {
            return;
        }
        this.addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            value.step(-1);
            this.scheduleRebuild();
        }).bounds(this.left + MINUS_X, rowY, STEP_WIDTH, ROW_WIDGET_HEIGHT).build());
        this.addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            value.step(1);
            this.scheduleRebuild();
        }).bounds(this.left + PLUS_X, rowY, STEP_WIDTH, ROW_WIDGET_HEIGHT).build());
    }

    /** 按钮回调里直接重建控件会改动正在被遍历的控件列表，推迟到当前帧结束再做（原版界面的惯例）。 */
    private void scheduleRebuild() {
        Minecraft.getInstance().execute(this::rebuildWidgets);
    }

    private static String keyLabel(final InputKey key) {
        return switch (key) {
            case FORWARD -> "W";
            case BACKWARD -> "S";
            case LEFT -> "A";
            case RIGHT -> "D";
            case JUMP -> "\u2423";   // ␣ 空格
        };
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(BACKGROUND, this.left, this.top, 0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);
        graphics.drawCenteredString(this.font, this.title, this.left + WINDOW_WIDTH / 2, this.top + 6, 0xFFFFFF);

        final SeatRuleList list = this.seat.getList(this.tab);
        for (int row = 0; row < SeatRuleList.MAX_RULES; row++) {
            final SeatRule rule = list.getRule(row);
            final int rowY = this.top + ROW_TOP + row * ROW_HEIGHT;
            final boolean current = row == this.currentRow(list);

            // 当前生效的那一行（第一条"有按键"的规则）加一个高亮框
            if (current) {
                graphics.fill(this.left + 4, rowY - 2, this.left + WINDOW_WIDTH - 4, rowY + ROW_WIDGET_HEIGHT + 1,
                        0x40FFFFFF);
            }

            // 选中的按键在按钮后面画一个底色（原版按钮没有"选中"状态）
            if (row > 0) {
                final InputKey[] keys = InputKey.values();
                for (int i = 0; i < keys.length; i++) {
                    if (rule.inputKeys().contains(keys[i])) {
                        graphics.fill(this.left + KEY_X + i * KEY_STEP, rowY, this.left + KEY_X + i * KEY_STEP + KEY_WIDTH,
                                rowY + ROW_WIDGET_HEIGHT, 0x8000FF00);
                    }
                }
            }

            final SeatValue value = rule.value();
            if (value != null) {
                graphics.drawCenteredString(this.font, value.asComponent(),
                        this.left + VALUE_X + VALUE_WIDTH / 2, rowY + 4, 0x404040);
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** 旧版用"第一条有按键的规则"表示当前编辑的语义；这里同样用它做高亮。 */
    private int currentRow(final SeatRuleList list) {
        for (int row = SeatRuleList.MAX_RULES - 1; row >= 0; row--) {
            if (!list.getRule(row).inputKeys().isEmpty()) {
                return row;
            }
        }
        return 0;
    }

    @Override
    public void onClose() {
        PacketDistributor.sendToServer(SeatRulesPacket.of(this.seat));
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
