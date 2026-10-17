package org.valkyrienskies.clockwork.content.curiosities.aeronaut;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/**
 * "飞行家装备"标记接口。
 *
 * <p>对应旧版 {@code curiosities/aeronaut/IAeronautEquipment.kt}：给四件护甲打标记，
 * 便于其它系统（飞行 / 空气动力学 / 护目镜 HUD）判断玩家穿了什么。
 *
 * <p>移植差异：旧版把 {@code isWearingAeronautSet / isWearingAnyAeronaut} 写成 Kotlin 的扩展函数
 * （companion object 里的静态方法）；这里改成接口的静态方法，调用方式一致、无需额外的伴生类。
 */
public interface IAeronautEquipment {

    /** 四个护甲槽都穿着飞行家装备。 */
    static boolean isWearingAeronautSet(LivingEntity entity) {
        return wearingAeronautInSlot(entity, EquipmentSlot.HEAD)
                && wearingAeronautInSlot(entity, EquipmentSlot.CHEST)
                && wearingAeronautInSlot(entity, EquipmentSlot.LEGS)
                && wearingAeronautInSlot(entity, EquipmentSlot.FEET);
    }

    /** 任意一个护甲槽穿着飞行家装备。 */
    static boolean isWearingAnyAeronaut(LivingEntity entity) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (wearingAeronautInSlot(entity, slot)) {
                return true;
            }
        }
        return false;
    }

    static boolean wearingAeronautInSlot(LivingEntity entity, EquipmentSlot slot) {
        return entity.getItemBySlot(slot).getItem() instanceof IAeronautEquipment;
    }
}
