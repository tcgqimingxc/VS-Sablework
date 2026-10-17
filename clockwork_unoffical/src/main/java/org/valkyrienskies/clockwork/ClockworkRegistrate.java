package org.valkyrienskies.clockwork;

import com.simibubi.create.foundation.data.CreateRegistrate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;

/**
 * Clockwork 的注册器。
 *
 * <p>沿用 Create 生态的做法：使用 {@link CreateRegistrate} 统一处理方块 / 物品 / 方块实体注册与数据生成，
 * 这样可以直接复用 Create 的模型、应力、tooltip 等基础设施，避免手写大量样板代码。
 */
public class ClockworkRegistrate extends CreateRegistrate {

    protected ClockworkRegistrate(String modId) {
        super(modId);
    }

    /**
     * 创建注册器，并关掉 Registrate 的"隐式默认标签页"。
     *
     * <p><b>为什么必须置空：</b>{@code AbstractRegistrate} 的构造器把 {@code defaultCreativeModeTab}
     * 默认设成 {@code CreativeModeTabs.SEARCH}，而 {@code AbstractRegistrate#item} 会据此给<b>每个</b>物品
     * 额外追加一次 {@code ItemBuilder#tab(SEARCH)}（{@code BlockBuilder#item()} 也走同一条
     * {@code AbstractRegistrate#item} 路径）。这个隐式插入用的可见性是
     * {@code TabVisibility.PARENT_AND_SEARCH_TABS}（{@code CreativeModeTab.Output#accept(ItemLike)}
     * 的默认可见性），而各切片里显式的 {@code .tab(clockwork_*)} 同样会把物品写进 search 集合；
     * 于是 {@code minecraft:search} 标签构建时会拿同一个 ItemStack 插入两次，被 NeoForge 的去重断言拦下：
     * {@code IllegalArgumentException: Itemstack 1 vs_clockwork:wanderlite_deepslate_ore already exists in the tab's list}
     * （抛点：{@code BuildCreativeModeTabContentsEvent.accept} 第 94 行的父标签断言）。
     *
     * <p>置空之后，物品归属完全由各切片显式的 {@code .tab(...)} 决定，每个物品只插入一次。
     * 同技术栈的 Create: Simulated 也是这么做的（{@code Simulated.java:31} 的
     * {@code .defaultCreativeTab((ResourceKey) null)}）。
     *
     * <p><b>不要</b>改用 {@code CreateRegistrate#setCreativeTab} 来"顺手入页"：按字节码，
     * {@code setCreativeTab} 只写 {@code CreateRegistrate.TAB_LOOKUP}，而该表仅被 Create 自己的
     * {@code AllCreativeModeTabs$RegistrateDisplayItemsGenerator}（经 {@code isInCreativeTab}）读取，
     * 它不会往 {@code BuildCreativeModeTabContentsEvent} 里插入任何物品——用了只会让标签页空掉。
     */
    public static ClockworkRegistrate create(String modId) {
        ClockworkRegistrate registrate = new ClockworkRegistrate(modId);
        registrate.defaultCreativeTab((ResourceKey<CreativeModeTab>) null);
        return registrate;
    }
}
