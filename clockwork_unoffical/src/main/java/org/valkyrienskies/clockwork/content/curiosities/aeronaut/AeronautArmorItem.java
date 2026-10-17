package org.valkyrienskies.clockwork.content.curiosities.aeronaut;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;

/**
 * 飞行家护甲（四件共用一个类）。
 *
 * <p>对应旧版 {@code curiosities/aeronaut/AeronautGogglesItem.kt / AeronautJacketItem.kt /
 * AeronautJumpersItem.kt / AeronautBootsItem.kt} —— 四个类都只是
 * {@code ArmorItem(FLIGHT, Type.X, properties) + IAeronautEquipment}，没有任何各自的行为。
 * 移植时合并成一个类，通过 {@link ArmorItem.Type} 参数区分，避免四份复制粘贴。
 *
 * <p>移植差异：
 * <ul>
 *   <li>1.21.1 的 {@link ArmorMaterial} 从枚举改成了记录类，护甲材质经由
 *       {@code Registries.ARMOR_MATERIAL} 注册（见 {@link CWAeronautIndex}）。</li>
 *   <li>旧版有一个自定义护甲图层 {@code AeronautArmorLayer}（帽子 + 会随速度飘动的护目镜）；
 *       那需要 {@code ClockworkPartials} 里的帽子/护目镜模型资源与实体渲染层注册。
 *       本移植不做该图层：{@code assets/vs_clockwork/textures/models/armor/} 下也没有对应贴图，
 *       强行实现只会渲染成缺失贴图。护甲本体与穿脱逻辑正常。</li>
 * </ul>
 */
public class AeronautArmorItem extends ArmorItem implements IAeronautEquipment {

    public AeronautArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties);
    }
}
