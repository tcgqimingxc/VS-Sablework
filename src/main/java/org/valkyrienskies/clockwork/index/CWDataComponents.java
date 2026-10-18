package org.valkyrienskies.clockwork.index;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 本模组的数据组件（1.21.1 的物品状态载体，对应旧版写在物品 NBT 里的那些键）。
 *
 * <p><b>为什么用 DataComponent 而不是继续写 NBT</b>：1.20.5 起原版移除了 {@code ItemStack} 的
 * 自由 NBT，任何"每件物品一个值"的状态都必须注册成 {@link DataComponentType}
 * （原版 {@code DataComponents} 与 Create 的 {@code AllDataComponents} 都是这么做的）。
 * 组件自带 codec 与网络 {@code StreamCodec}，因此：<b>存档持久化</b>、<b>客户端同步</b>、
 * <b>配方/掉落物复制</b>（{@code ItemStack#copy}）都由原版负责，不需要自己写包。
 *
 * <p>组件不写"默认值"字段：NeoForge 的组件没有默认值概念，<b>缺失即默认</b>。
 * 叶片展长的默认值（1）由 {@code BladeItems#getLength} 表达，这样"新合成 / 创造模式取出"的叶片
 * 不带任何组件、能和"长度为 1 的老叶片"堆叠成同一格（否则会因组件差异拆成两堆）。
 */
public final class CWDataComponents {

    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ClockworkMod.MOD_ID);

    /**
     * 叶片展长（旧版物品 NBT 键 {@code BladeLength}，旧版是 double，这里按需求改为整数格）。
     *
     * <p>{@code persistent(Codec.INT)} = 存档/数据包里的字段类型；
     * {@code networkSynchronized(VAR_INT)} = 同步给客户端时用的变长整数（渲染与 tooltip 都要读它）。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> BLADE_LENGTH =
            COMPONENTS.register("blade_length", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    private CWDataComponents() {
    }

    /** 由 {@link ClockworkMod} 在 mod 构造期调用（必须早于任何物品被创建）。 */
    public static void register(final IEventBus modBus) {
        COMPONENTS.register(modBus);
    }
}
