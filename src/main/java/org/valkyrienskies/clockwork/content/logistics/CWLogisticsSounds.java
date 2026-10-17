package org.valkyrienskies.clockwork.content.logistics;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 物流切片（气体 + 固体交付）用到的音效。
 *
 * <p>对应旧版 {@code ClockworkSounds} 里的同名条目（{@code gas_nozzle_start} / {@code gas_hiss} /
 * {@code thwoom}）；{@code sounds.json} 与 {@code .ogg} 资源已随旧版资源一起拷贝过来，
 * 这里只需要把事件注册进注册表。注册方式与 {@code CWKineticsSounds} 一致，
 * 因此不需要修改 Lead 维护的 {@code index/CWSounds}。
 */
public final class CWLogisticsSounds {

    /** 气嘴开始喷气（一口气）。 */
    public static final SoundEvent GAS_NOZZLE_START = create("gas_nozzle_start");
    /** 气嘴持续喷气的嘶声。 */
    public static final SoundEvent GAS_HISS = create("gas_hiss");
    /** 交付加农炮发射。 */
    public static final SoundEvent THWOOM = create("thwoom");

    private static final SoundEvent[] ALL = {GAS_NOZZLE_START, GAS_HISS, THWOOM};

    private CWLogisticsSounds() {
    }

    private static SoundEvent create(String name) {
        return SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, name));
    }

    /** 由 {@link CWLogisticsModBus} 在注册事件里调用。 */
    static void registerAll(RegisterEvent event) {
        for (SoundEvent sound : ALL) {
            event.register(Registries.SOUND_EVENT, sound.getLocation(), () -> sound);
        }
    }
}
