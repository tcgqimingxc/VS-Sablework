package org.valkyrienskies.clockwork.mixin.content.gas.boiler;

import com.simibubi.create.content.fluids.tank.BoilerData;
import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.clockwork.content.logistics.gas.engine.GasEngineBlock;

/**
 * 让 Create 锅炉把「燃气机」也算作已连接的引擎。
 *
 * <p>对应旧版 {@code MixinBoilerData}（旧版是在 {@code BoilerData#evaluate} 里
 * {@code BlockPos.offset(III)} 调用点做 {@code @WrapOperation}，副作用式地累加
 * {@code attachedEngines}）。
 *
 * <h2>注入点为什么是 {@code needsHeatLevelUpdate} 的 PUTFIELD（而不是 {@code @At("RETURN")}）</h2>
 * {@code javap -p -c} 核实 6.0.10 的 {@code evaluate} 结构如下（字节码偏移）：
 * <pre>
 *   22-29 : attachedEngines = 0; attachedWhistles = 0;          // 先归零
 *   32-229: 三重循环扫描锅炉本体（width × height × width）      // 再重新计数
 *   232   : putfield BoilerData.needsHeatLevelUpdate:Z           ← 本 mixin 注入点
 *   237-252: 比较「扫描前保存的旧值」与「扫描后的新值」
 *   253-260: ireturn  old != new                                // 变化标志
 * </pre>
 * 把燃气机的计数放在 232（循环之后、比较之前），才能让 {@code evaluate} 的返回值也把
 * 燃气机算进「连接数是否变化」，从而让 {@code FluidTankBlockEntity#updateBoilerState}
 * 在燃气机被放上/拆掉时照常走 {@code notifyUpdate()} + {@code checkPipeOrganAdvancement}。
 * 旧版在循环内部累加，本来就满足这一点；{@code @At("RETURN")} 则会让返回值漏掉燃气机，
 * 属于移植引入的语义偏差。
 *
 * <p>为什么需要它：锅炉的 {@code getEngineEfficiency} 直接除以 {@code attachedEngines}
 * （javap：{@code BoilerData.getEngineEfficiency:(I)F}），燃气机不参与计数的话，
 * 蒸汽机拿到的效率会偏大。
 *
 * <p>计数判据与 Create 数蒸汽机的方式逐字对应（javap 核实 {@code evaluate} 的 143-171）：
 * <ul>
 *   <li>Create：{@code AllBlocks.STEAM_ENGINE.has(attachedState) && SteamEngineBlock.getFacing(attachedState) == d}</li>
 *   <li>本 mixin：{@code GasEngineBlock.isGasEngine(attachedState) && GasEngineBlock.getFacing(attachedState) == d}</li>
 * </ul>
 * 本体判据也一致：Create 用 {@code FluidTankBlock.isTank(state)}，而
 * {@code javap -c} 显示它<b>就是</b> {@code state.getBlock() instanceof FluidTankBlock}。
 */
@Mixin(BoilerData.class)
public abstract class MixinBoilerData {

    /** Create 在 {@code evaluate} 里自增的同一个字段（javap 核实为 {@code public int}）。 */
    @Shadow
    public int attachedEngines;

    /**
     * 注意 {@link CallbackInfoReturnable} 而不是 {@link org.spongepowered.asm.mixin.injection.callback.CallbackInfo}：
     * 目标方法 {@code evaluate(FluidTankBlockEntity)Z} 有返回值（boolean），
     * Mixin 会强制要求可返回型回调器，用 {@code CallbackInfo} 会在<b>类加载期</b>抛
     * {@code InvalidInjectionException: CallbackInfoReturnable is required}（实机崩溃过一次）。
     * 这里不 cancel，所以无需读 {@code cir}。
     */
    @Inject(
            method = "evaluate",
            at = @At(
                    value = "FIELD",
                    target = "Lcom/simibubi/create/content/fluids/tank/BoilerData;needsHeatLevelUpdate:Z",
                    opcode = Opcodes.PUTFIELD))
    private void vs_clockwork$countGasEngines(final FluidTankBlockEntity tank, final CallbackInfoReturnable<Boolean> cir) {
        final Level level = tank.getLevel();
        if (level == null) {
            return;
        }
        final FluidTankSizeAccessor size = (FluidTankSizeAccessor) tank;
        final BlockPos origin = tank.getBlockPos();
        // 与 Create 的扫描范围一致：width × height × width 的实心长方体就是锅炉本体
        for (int y = 0; y < size.vs_clockwork$getHeight(); y++) {
            for (int x = 0; x < size.vs_clockwork$getWidth(); x++) {
                for (int z = 0; z < size.vs_clockwork$getWidth(); z++) {
                    final BlockPos tankPos = origin.offset(x, y, z);
                    final BlockState tankState = level.getBlockState(tankPos);
                    if (!(tankState.getBlock() instanceof FluidTankBlock)) {
                        continue;
                    }
                    for (final Direction direction : Iterate.directions) {
                        final BlockPos attachedPos = tankPos.relative(direction);
                        final BlockState attachedState = level.getBlockState(attachedPos);
                        // 燃气机的轴必须指向锅炉（= 朝向那一面贴住锅炉），与 Create 判定蒸汽机的方式一致
                        if (GasEngineBlock.isGasEngine(attachedState)
                                && GasEngineBlock.getFacing(attachedState) == direction) {
                            attachedEngines++;
                        }
                    }
                }
            }
        }
    }
}
