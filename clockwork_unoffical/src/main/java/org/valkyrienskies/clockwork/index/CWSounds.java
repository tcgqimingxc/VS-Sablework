package org.valkyrienskies.clockwork.index;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 声音事件注册表。
 *
 * <p>为什么需要它：1.21 起 {@code data/<ns>/jukebox_song/*.json} 里的 {@code sound_event}
 * 必须是**注册表里真实存在**的声音事件；只写 {@code assets/<ns>/sounds.json} 是不够的
 * （sounds.json 只负责"这个事件播哪些 ogg"）。
 * 移植初期唱片 {@code music_disc_wanderlust} 就因为只写了 sounds.json 而让
 * {@code jukebox_song/wanderlust.json} 解析失败，进而在创建世界时直接崩
 * （{@code Failed to load registries}）。
 *
 * <p>因此这里把数据文件引用到的声音事件集中注册；新增唱片/方块音效时，
 * 在 sounds.json 里加条目之后，也来这里登记同名的 {@link SoundEvent}。
 */
public final class CWSounds {

    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, ClockworkMod.MOD_ID);

    /** 唱片 "wanderlust" 的曲目（sounds.json 里有同名条目指向 ogg 文件）。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> WANDERLUST = register("wanderlust");

    private CWSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, path);
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(id));
    }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
