package org.valkyrienskies.clockwork.content.curiosities.tools.gravitron;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 重力枪自己的音效事件。
 *
 * <p>为什么必须注册：{@code level.playSound} 会把 {@link SoundEvent} 包成 {@code Holder} 走网络包，
 * 只有注册表里存在的 id 才能被 {@code /playsound} 与字幕查到；未注册的虽然能靠
 * {@code DIRECT_STREAM_CODEC} 兜底发声，但不属于"正式注册的 id"，数据包/命令都引用不到。
 *
 * <p>注册写法与 {@code CWKineticsSounds} 一致（切片不改 Lead 的 {@code index/CWSounds}），
 * 由 {@link GravitronModBus} 在 {@link RegisterEvent} 里调用。
 *
 * <p>只注册本移植真正会播放的 6 个：旧版 {@code gravitron_fling}（左键发射）与
 * {@code gravitron_freeze}（超充枪左键冻结）在本移植里没有对应的旧动作，所以**不注册这两个事件**，
 * 但它们的 ogg 没有被浪费 —— LOCK / UNLOCK 的采样就是这两个文件（见下）。
 * 这样"未使用的重力枪音效"变成了"锁定/解锁的采样"，不再有死资源。
 *
 * <p><b>锁定 / 解锁为什么不复用 GRAB / RELEASE</b>（实机反馈："锁定/解锁音效错误运用了抓取/释放音效"）：
 * 这两件事是完全不同的动作 —— 抓取是"拽着结构走"（3.2 s 的拉起长音），锁定是"把结构焊死在世界坐标系"
 * （一瞬间的刚性约束合上）。所以 {@link #LOCK} / {@link #UNLOCK} 各自是一条独立事件。
 *
 * <p><b>采样一律取自本模组自己的资源</b>（{@code assets/vs_clockwork/sounds/}，见
 * {@code docs/GRAVITRON-SOUNDS.md}）：LOCK 用 {@code gravitron_freeze}、UNLOCK 用 {@code gravitron_fling}。
 * 明确<b>不使用跨模组采样</b>（例如 Aeronautics / Simulated 的 {@code item/physics_staff/*}）：
 * 上游改版本、挪文件时那种引用会静默失效（字幕还在，日志里只剩"找不到采样"），
 * 而这两个 ogg 随本模组一起打包，永远存在。
 */
public final class GravitronSounds {

    /** 拿起重力枪（旧版 {@code GravitronHandler.tick} 的装备音）。 */
    public static final SoundEvent START = create("gravitron_start");
    /** 抓住结构成功（旧版 {@code GrabTool.tryGrabShip}）。 */
    public static final SoundEvent GRAB = create("gravitron_grab");
    /** 放开结构（旧版 {@code GrabTool.tryGrabShip} 的 dropShip 分支）。 */
    public static final SoundEvent RELEASE = create("gravitron_release");
    /** 收起重力枪（旧版 {@code GravitronHandler.tick}），同时用于"解体"。 */
    public static final SoundEvent SHUTDOWN = create("gravitron_shutdown");
    /**
     * 锁定成功：把目标结构焊在世界坐标系上（{@code CWSubLevelLocks.toggle} 装上固定约束）。
     *
     * <p>采样 = 本模组自己的 {@code vs_clockwork:gravitron_freeze}（旧版超充重力枪左键把
     * {@code ship.isStatic} 取反的"冻结"专用音；本移植没有那个左键动作，文件此前一直闲置）。
     * 语义正好是"把结构冻在原地"，且实测听感与前四个音都分得开：4.08 s、起音 27 ms，
     * 开头是一记明亮的"咔"（前 100 ms 频谱质心 1163 Hz，低频只占 20%），随后向下扫到
     * 110~140 Hz 的闷响（0.5~1.0 s 是它最响的一段，−2.6 dB）再快速衰减，
     * 1.5 s 后 −33 dB、3.3 s 后基本无声 —— 像刚性约束"合上"再稳住。
     * 抓取是 3.23 s 的慢起音长音（峰值在 643 ms），放开是 3.38 s 的低频收力，都不撞。
     *
     * <p>实机播放参数 = {@code GravitronItem#LOCK_SOUND_VOLUME} / {@code #LOCK_SOUND_PITCH}
     * （旧版实测值 {@code 1f} / {@code 1f}，见该常量注释）：<b>不人为移调</b>。
     * 本条与 {@link #UNLOCK} 的区别来自采样本身（这条是亮"咔"、UNLOCK 是闷"砰"），
     * 不再靠 pitch 拉半音 —— 这里曾用过 pitch 1.1，实机反馈"音调偏高"后按旧版还原。
     */
    public static final SoundEvent LOCK = create("gravitron_lock");
    /**
     * 解锁成功：把那条固定约束拆掉（结构重新受重力/推力支配）。
     *
     * <p>采样 = 本模组自己的 {@code vs_clockwork:gravitron_fling}（旧版左键"发射/甩出"的专用音）。
     * 解锁就是"约束松开、能量重新作用在结构上"，取它的甩出语义；实测 5.01 s、起音 32 ms、
     * 低频占比 62.7%（另有约 36% 落在 200~1000 Hz，是一条有中频"力感"的长音），
     * 且衰减明显比 LOCK 慢（2.5~4.2 s 段仍有 −36~−46 dB 的尾巴，LOCK 同期已经 −47~−66 dB 基本无声），
     * 听感是"一股力被放出去"。
     *
     * <p>实机播放参数与 {@link #LOCK} 相同（{@code GravitronItem#LOCK_SOUND_VOLUME} /
     * {@code #LOCK_SOUND_PITCH} = 旧版 {@code 1f} / {@code 1f}）：<b>不移调</b>（曾用 0.9 把它压低，
     * 已随 LOCK 一起按旧版还原），因此时长仍是采样的 5.01 s，与 {@link #LOCK}（4.08 s）
     * 在音色、时长与包络上都不重叠，也与 {@link #GRAB}（3.23 s、质心 472 Hz 的慢起音）/
     * {@link #RELEASE}（3.38 s、质心 409 Hz）分得开。
     */
    public static final SoundEvent UNLOCK = create("gravitron_unlock");

    private static final SoundEvent[] ALL = {START, GRAB, RELEASE, SHUTDOWN, LOCK, UNLOCK};

    private GravitronSounds() {
    }

    private static SoundEvent create(String name) {
        return SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, name));
    }

    /** 由 {@link GravitronModBus} 在注册事件里调用。 */
    static void registerAll(RegisterEvent event) {
        for (SoundEvent sound : ALL) {
            event.register(Registries.SOUND_EVENT, sound.getLocation(), () -> sound);
        }
    }
}
