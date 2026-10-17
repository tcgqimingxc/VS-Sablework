package org.valkyrienskies.clockwork.content.curiosities.aeronaut;

import com.simibubi.create.content.equipment.goggles.GogglesItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import com.tterrag.registrate.util.entry.RegistryEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import org.valkyrienskies.clockwork.ClockworkMod;
import org.valkyrienskies.clockwork.ClockworkRegistrate;
import org.valkyrienskies.clockwork.index.CWCreativeTabs;
import org.valkyrienskies.clockwork.index.CWItems;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 飞行家护甲切片注册入口。
 *
 * <p>对应旧版 {@code ClockworkArmorMaterials.FLIGHT} + {@code ClockworkItems.kt} 里的四件护甲
 * （{@code aeronaut_goggles / jacket / jumpers / boots}）以及
 * {@code ClockworkItems.register()} 里给 Create 护目镜加的佩戴判定。
 *
 * <p>与旧版的差异：
 * <ul>
 *   <li>旧版 {@code ArmorMaterial} 是 1.20 的枚举（{@code getDefenseForType / getDurabilityForType}）；
 *       1.21.1 改成记录类 + {@code Registries.ARMOR_MATERIAL} 注册。数值完全照抄旧版：
 *       防御 [头盔 3, 胸甲 6, 护腿 8, 靴子 3]、耐久系数 33、附魔 15、韧性 2.0、击退抗性 0.1、
 *       修复材料 = 游荡水晶。</li>
 *   <li>装备音效：旧版是 {@code ClockworkSounds.FLIGHTSUIT_EQUIP}（Lead 的 {@code index/CWSounds} 负责注册），
 *       这里先用原版皮革护甲音效占位，Lead 注册后替换即可。</li>
 *   <li>护甲贴图：图层标识用 {@code vs_clockwork:flight}，对应旧版迁移过来的
 *       {@code assets/vs_clockwork/textures/models/armor/flight_layer_1.png}（外层：头盔/胸甲/靴子）
 *       与 {@code flight_layer_2.png}（内层：护腿）。1.21.1 由
 *       {@code ArmorMaterial.Layer#resolveTexture} 拼成
 *       {@code <namespace>:textures/models/armor/<path>_layer_<1|2>.png}，<b>命名空间会保留</b>，
 *       所以不需要写任何自定义 {@code HumanoidArmorLayer}。</li>
 *   <li>旧版额外挂了一个 {@code AeronautArmorLayer} 画 3D 飞帽（{@code HAT_BASE / HAT_FLAP_* / HAT_GOGGLES}），
 *       那部分依赖 1.20.1 的 {@code LivingEntityFeatureRendererRegistrationCallback}（1.21.1 已移除），
 *       属于独立的客户端切片，本切片不做。</li>
 * </ul>
 */
@SuppressWarnings("removal")
public final class CWAeronautIndex {

    private static final ClockworkRegistrate REGISTRATE = ClockworkMod.REGISTRATE;

    /** 护甲图层贴图标识（见类注释：解析到 {@code vs_clockwork:textures/models/armor/flight_layer_*.png}）。 */
    private static final ResourceLocation FLIGHT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, "flight");

    /** 飞行家护甲材质。 */
    public static final RegistryEntry<ArmorMaterial, ArmorMaterial> FLIGHT_MATERIAL = REGISTRATE
            .simple("flight", Registries.ARMOR_MATERIAL, CWAeronautIndex::createFlightMaterial);

    public static final ItemEntry<AeronautArmorItem> AERONAUT_GOGGLES = armor("aeronaut_goggles", ArmorItem.Type.HELMET);
    public static final ItemEntry<AeronautArmorItem> AERONAUT_JACKET = armor("aeronaut_jacket", ArmorItem.Type.CHESTPLATE);
    public static final ItemEntry<AeronautArmorItem> AERONAUT_JUMPERS = armor("aeronaut_jumpers", ArmorItem.Type.LEGGINGS);
    public static final ItemEntry<AeronautArmorItem> AERONAUT_BOOTS = armor("aeronaut_boots", ArmorItem.Type.BOOTS);

    private CWAeronautIndex() {
    }

    private static ArmorMaterial createFlightMaterial() {
        Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        // 旧版 damageReductionAmountArray = [3, 6, 8, 3]，顺序与 ArmorItem.Type 的枚举顺序一致。
        defense.put(ArmorItem.Type.HELMET, 3);
        defense.put(ArmorItem.Type.CHESTPLATE, 6);
        defense.put(ArmorItem.Type.LEGGINGS, 8);
        defense.put(ArmorItem.Type.BOOTS, 3);
        return new ArmorMaterial(
                defense,
                15,
                SoundEvents.ARMOR_EQUIP_LEATHER,
                // 旧版修复材料是游荡水晶，由 Lead 的 index/CWItems 注册。
                () -> Ingredient.of(CWItems.WANDERLITE_CRYSTAL.get()),
                List.of(new ArmorMaterial.Layer(FLIGHT_TEXTURE)),
                2.0f,
                0.1f);
    }

    private static ItemEntry<AeronautArmorItem> armor(String name, ArmorItem.Type type) {
        return REGISTRATE
                .item(name, p -> new AeronautArmorItem(FLIGHT_MATERIAL, type, p))
                // 旧版 MAX_DAMAGE_ARRAY = [11, 16, 15, 13] * maxDamageFactor(33)。
                .properties(p -> p.durability(type.getDurability(33)))
                .tab(CWCreativeTabs.baseKey())
                .register();
    }

    /** 触发类加载（由 {@code CWCuriositiesIndex} 调用）。 */
    public static void register() {
        // 旧版在 ClockworkItems.register() 里做的同一件事：戴着飞行家护目镜时启用 Create 的护目镜信息显示。
        GogglesItem.addIsWearingPredicate(
                player -> player.getItemBySlot(EquipmentSlot.HEAD).is(AERONAUT_GOGGLES.get()));
    }
}
