package org.valkyrienskies.clockwork.mixin.content.gas.boiler;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 读取锅炉多方块的尺寸。
 *
 * <p>Create 把 {@code FluidTankBlockEntity.width / height} 声明为 <b>protected</b>（javap 已核实），
 * 而 {@code BoilerData#evaluate} 的扫描循环正是用它们遍历锅炉本体。
 * {@code MixinBoilerData} 也要做同样的遍历，因此用 accessor 取得这两个字段 —— 这比在
 * mixin 里 {@code @Shadow} 一个非本方块的字段安全（不依赖 mixin 的继承层级）。
 */
@Mixin(FluidTankBlockEntity.class)
public interface FluidTankSizeAccessor {

    @Accessor("width")
    int vs_clockwork$getWidth();

    @Accessor("height")
    int vs_clockwork$getHeight();
}
