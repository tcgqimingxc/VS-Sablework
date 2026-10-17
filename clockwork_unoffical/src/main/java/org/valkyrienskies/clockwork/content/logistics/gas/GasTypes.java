package org.valkyrienskies.clockwork.content.logistics.gas;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * 气体类型注册表。
 *
 * <p>对应旧版 {@code ClockworkGasses} + Kelvin 的 {@code GasTypeRegistry}。旧版注册到 Kelvin 的全局
 * 注册表里；本移植改为模组内的静态注册表（没有跨模组注册需求，静态表更简单也更快）。
 *
 * <p>与旧版差异：
 * <ul>
 *   <li>新增 {@link #AIR}（旧版来自 Kelvin，命名空间 {@code kelvin:air}，这里沿用同一 id 以便识别）。</li>
 *   <li>删除了 {@code kelvin:methane} 等旧版由 Kelvin/其他模组提供的气体，只保留 Clockwork 自己用到的。</li>
 * </ul>
 */
public final class GasTypes {

    private static final Map<ResourceLocation, GasType> BY_ID = new HashMap<>();
    private static final List<GasType> ALL = new ArrayList<>();

    /** 空气：旧版 {@code kelvin:air}，空气压缩机的原料。密度/比热取干空气标准值。 */
    public static final GasType AIR =
            register("kelvin", "air", "Air", 1.225, 1005.0, 0xC8D8E8);

    // ===================== 以下 8 种来自旧版 ClockworkGasses.kt（数值原样搬运） =====================

    /** 废气：燃烧/发动机排出的气体。 */
    public static final GasType EXHAUST =
            register("vs_clockwork", "exhaust", "Exhaust", 1.98, 2.846, 0x5A5A5A);
    /** 蒸汽：蒸汽发生器产生，用于推动燃气机。 */
    public static final GasType STEAM =
            register("vs_clockwork", "steam", "Steam", 0.762, 2.2, 0xE0E8F0);
    /** 燃素（Phlogiston）：煤炉高温气体。 */
    public static final GasType PHLOGISTON =
            register("vs_clockwork", "phlogiston", "Phlogiston", 3.0, 14.30, 0xFF7B2A);
    /** 以太（Aether，旧版对应氦气）：高空空气压缩机的副产物，最轻的浮升气体。 */
    public static final GasType AETHER =
            register("vs_clockwork", "aether", "Aether", 0.166, 5.1832, 0x9BE8FF);
    /** 星烷（Stellane，旧版对应氢气）。 */
    public static final GasType STELLANE =
            register("vs_clockwork", "stellane", "Stellane", 0.08988, 14.30, 0xFFD9F0);
    /** 沼汽（Bog，旧版对应甲烷）。 */
    public static final GasType BOG =
            register("vs_clockwork", "bog", "Bog", 0.657, 2.2, 0x7FBF6A);
    /** 硫酸气（Vitriol）。 */
    public static final GasType VITRIOL =
            register("vs_clockwork", "vitriol", "Vitriol", 2.2, 8.0, 0xC8E04A);
    /** 臭氧石气（Ozonite）。 */
    public static final GasType OZONITE =
            register("vs_clockwork", "ozonite", "Ozonite", 1.15, 0.92, 0x7AB8FF);

    private GasTypes() {
    }

    private static GasType register(String namespace, String path, String displayName,
                                    double density, double specificHeat, int color) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
        GasType type = new GasType(ALL.size(), id, displayName, density, specificHeat, color);
        BY_ID.put(id, type);
        ALL.add(type);
        return type;
    }

    /** 气体种类数量，用于分配 {@link GasMix} 的数组长度。 */
    public static int count() {
        return ALL.size();
    }

    /** 按注册顺序返回全部气体（索引与 {@link GasType#index()} 一致）。 */
    public static List<GasType> all() {
        return Collections.unmodifiableList(ALL);
    }

    @Nullable
    public static GasType byId(ResourceLocation id) {
        return BY_ID.get(id);
    }

    @Nullable
    public static GasType byIndex(int index) {
        return index >= 0 && index < ALL.size() ? ALL.get(index) : null;
    }
}
