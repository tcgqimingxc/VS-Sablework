package org.valkyrienskies.clockwork.content.kinetics;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 动力部件切片用到的音效。
 *
 * <p>对应旧版 {@code ClockworkSounds} 里的同名条目；这里只声明本切片真正会播放的那几个，
 * 避免为了几个音效就把整个 {@code index/CWSounds} 提前建出来（那个文件由 Lead 维护）。
 * {@code sounds.json} 与 {@code .ogg} 资源已随旧版资源一起拷贝过来，所以这里只需要注册事件。
 *
 * <p>注册走 {@link RegisterEvent} 而不是自己建 {@code DeferredRegister}，
 * 这样不必修改 {@code ClockworkMod} 去拿 mod 事件总线。
 */
public final class CWKineticsSounds {

    /** 通用轴 / 伸缩杆连接成功。 */
    public static final SoundEvent HOSE_ATTACH = create("hose_attach");
    /** 通用轴 / 伸缩杆断开连接。 */
    public static final SoundEvent HOSE_RELEASE = create("hose_release");
    /** 通用轴超出最大长度后自动崩断。 */
    public static final SoundEvent JOINT_BREAK = create("joint_break");
    /** 减摩方块吸附到结构上。 */
    public static final SoundEvent DOINK = create("doink");
    /** 减摩方块从结构上弹开。 */
    public static final SoundEvent BOING = create("boing");
    /** 惰性轴承对接成功。 */
    public static final SoundEvent GEAR_WHIRR = create("gear_whirr");

    private static final SoundEvent[] ALL =
            {HOSE_ATTACH, HOSE_RELEASE, JOINT_BREAK, DOINK, BOING, GEAR_WHIRR};

    private CWKineticsSounds() {
    }

    private static SoundEvent create(String name) {
        return SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, name));
    }

    /** 由 {@link CWKineticsModBus} 在注册事件里调用。 */
    static void registerAll(RegisterEvent event) {
        for (SoundEvent sound : ALL) {
            event.register(Registries.SOUND_EVENT, sound.getLocation(), () -> sound);
        }
    }
}
