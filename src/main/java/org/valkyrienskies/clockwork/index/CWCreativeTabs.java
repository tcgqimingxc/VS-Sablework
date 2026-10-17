package org.valkyrienskies.clockwork.index;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 创造模式标签页（与旧版一致：主标签 / 物理机械 / 气体物流）。
 *
 * <p>物品归属完全由 Registrate 的 {@code item().tab(key)} 决定（注册器已在
 * {@link org.valkyrienskies.clockwork.ClockworkRegistrate#create(String)} 里关掉隐式默认标签页，
 * 避免同一物品被插入 search 标签两次），因此这里对外只暴露 {@link ResourceKey}；
 * {@link DeferredHolder} 只是注册返回值，同时也是取 key 与图标物品的载体。
 * <p>注意：{@code CreateRegistrate#setCreativeTab} 不参与本模组的入页，它只写 Create 自己的
 * {@code TAB_LOOKUP} 表供 Create 标签生成器过滤用。
 */
public final class CWCreativeTabs {

    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(BuiltInRegistries.CREATIVE_MODE_TAB, ClockworkMod.MOD_ID);

    // TODO(移植)：图标暂用已存在的材料方块，等 wanderwand / physics_infuser / air_compressor 实现后换回旧版图标
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BASE = TABS.register("clockwork_main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + ClockworkMod.MOD_ID))
                    .icon(() -> new ItemStack(CWBlocks.WANDERLITE_BLOCK.get()))
                    .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> PHYSICALITIES = TABS.register("clockwork_physicalities",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + ClockworkMod.MOD_ID + ".physicalities"))
                    .icon(() -> new ItemStack(CWBlocks.BALLOON_CASING.get()))
                    .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> GASSES = TABS.register("clockwork_gasses",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + ClockworkMod.MOD_ID + ".gasses"))
                    .icon(() -> new ItemStack(CWBlocks.WANDERGLASS.get()))
                    .build());

    private CWCreativeTabs() {
    }

    public static ResourceKey<CreativeModeTab> baseKey() {
        return BASE.getKey();
    }

    public static ResourceKey<CreativeModeTab> physicalitiesKey() {
        return PHYSICALITIES.getKey();
    }

    public static ResourceKey<CreativeModeTab> gassesKey() {
        return GASSES.getKey();
    }

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}
