package org.valkyrienskies.clockwork.content.curiosities.tools.gravitron;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 重力枪的工作模式（旧版 {@code tool/ToolType.kt} 的 4 种工具，名字与图标一一对应）。
 *
 * <p>旧版用"按住 {@code AllKeys.TOOL_MENU} + 滚轮"的轮盘切换、并用网络包
 * {@code GravitronDialPacket / ToolType} 同步到服务端；本移植保留同一套模式与同一个按键，
 * 但菜单换成 1.21.1 的 Screen 实现（Create 6 里已经没有 {@code AbstractRadialMenu} 了，
 * 它的 {@code RadialWrenchMenu} 就是一个普通 {@code AbstractSimiScreen}），同步走
 * {@link GravitronToolPacket}。
 *
 * <p>图标来自 {@code textures/gui/icons.png} 第 0 行（与旧版 {@code ClockworkIcons} 的坐标一致）：
 * GRAB(0,0) / ASSEMBLE(16,0) / GRAB_AND_ASSEMBLE(32,0) / DESTROY(48,0)。
 *
 * <p>模式是"右键干什么"的选择，潜行右键始终是旧版的自动判方向（装配或解体），不受模式影响，
 * 因此默认模式 GRAB 下重力枪的行为与引入轮盘之前完全一样。
 */
public enum GravitronTools {

    /** 抓取 / 放开（默认）。 */
    GRAB("grab", 0),
    /** 只装配：把游荡法杖选区里包含目标方块的那一块并成子空间。 */
    ASSEMBLE("assemble", 1),
    /** 旧版 GrabssembleTool：先并块，再立刻抓住刚生成的结构。 */
    GRAB_AND_ASSEMBLE("grab_and_assemble", 2),
    /** 只解体：把目标所在的子空间原地放回世界。 */
    DESTROY("destroy", 3),
    /**
     * 锁定 / 解锁：把目标结构焊在世界坐标系上（再点一次解锁）。
     *
     * <p>对应 Aeronautics 物理手杖的 {@code LOCK} 动作；语义与实现见
     * {@link org.valkyrienskies.clockwork.platform.CWSubLevelLocks}。
     */
    LOCK("lock", 4);

    private static final GravitronTools[] VALUES = values();

    /**
     * 每个玩家当前选中的模式。
     *
     * <p>服务端是权威（收到 {@link GravitronToolPacket} 才改），客户端保存自己的镜像供菜单显示；
     * 换维度/重登由 {@link #clear(Player)} 清回默认，避免两端状态不一致。
     */
    private static final Map<UUID, GravitronTools> SELECTED = new ConcurrentHashMap<>();

    private final String id;
    private final int iconColumn;

    GravitronTools(String id, int iconColumn) {
        this.id = id;
        this.iconColumn = iconColumn;
    }

    /** 名称键（en_us / zh_cn 里已有旧版翻译）。 */
    public String langKey() {
        return "vs_clockwork.gravitron.tool." + id;
    }

    /** 第 {@code line} 行说明键（0..3，旧版也是 4 行）。 */
    public String descriptionKey(int line) {
        return langKey() + ".description." + line;
    }

    public Component displayName() {
        return Component.translatable(langKey());
    }

    /**
     * 图标在 {@code textures/gui/icons.png} 里的 U 坐标（16×16 一格）。
     *
     * <p>注意：{@code icons.png} 只有前 6 列有内容、里面没有锁形图标，所以 {@link #LOCK} 的图标
     * 由菜单改用 Create 的 {@code AllIcons.I_CONFIG_LOCKED} 画（见 {@code GravitronToolMenu}），
     * 这里的列号对它没有意义。
     */
    public int iconU() {
        return iconColumn * 16;
    }

    /** 滚轮循环：{@code steps} 为正表示向后一个模式（Create 的轮盘也是这个方向）。 */
    public GravitronTools cycle(int steps) {
        return VALUES[Math.floorMod(ordinal() + steps, VALUES.length)];
    }

    /** 网络包里的序号 → 模式；越界时退回默认，防止坏包把服务端状态写成 null。 */
    public static GravitronTools byIndex(int index) {
        return index >= 0 && index < VALUES.length ? VALUES[index] : GRAB;
    }

    public static GravitronTools get(@Nullable Player player) {
        if (player == null) {
            return GRAB;
        }
        return SELECTED.getOrDefault(player.getUUID(), GRAB);
    }

    public static void set(@Nullable Player player, GravitronTools tool) {
        if (player == null) {
            return;
        }
        if (tool == GRAB) {
            SELECTED.remove(player.getUUID());          // 默认值不入表，省一次清理
        } else {
            SELECTED.put(player.getUUID(), tool);
        }
    }

    public static void clear(@Nullable Player player) {
        if (player != null) {
            SELECTED.remove(player.getUUID());
        }
    }
}
