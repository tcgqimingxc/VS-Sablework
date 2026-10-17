package org.valkyrienskies.clockwork.mixin.content.gas.boiler;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.bearing.WindmillBearingBlockEntity;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.steamEngine.PoweredShaftBlockEntity;
import com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlock;
import com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlockEntity;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.clockwork.content.logistics.gas.engine.GasEngineBlockEntity;

/**
 * 蒸汽机没接锅炉、但背面接的是燃气机时，用燃气机的效率驱动动力轴。
 *
 * <p>对应旧版 {@code MixinSteamEngineBlockEntity}（旧版在 {@code tick} 的 HEAD 注入、
 * 并额外注入 {@code Level#isLoaded} 调用点做 {@code ci.cancel()}）。
 *
 * <h2>为什么不能沿用「只替换 getEngineEfficiency 这一个输入」的写法</h2>
 * {@code javap -p -c -l} 核实 6.0.10 的 {@code SteamEngineBlockEntity#tick} 控制流：
 * <pre>
 *    4-5  : getTank()                       → 局部变量槽 1，LVT 名 tank
 *   14-26 : if (tank == null || shaft == null || !isValid()) → 走 29 的「无效分支」
 *   29-116: 无效分支（末尾 106-113 shaft.update(worldPosition, 0, 0)）后 return
 *  117-434: 有效分支；getEngineEfficiency 的唯一调用点在 234
 * </pre>
 * 也就是说：<b>唯一那个 {@code BoilerData.getEngineEfficiency(I)F} 调用点只可能
 * 在 {@code tank != null} 时到达</b>，而且 {@code isValid()}（javap：读背面方块是否为
 * {@code AllBlocks.FLUID_TANK}）也要求背面是储罐。所以「背面是燃气机 ⇒ tank == null」
 * 这条路径根本不经过该调用点 —— 在原调用点上做 {@code @ModifyExpressionValue} 是死代码，
 * 燃气机永远发不出动力。这正是旧版要整体接管 {@code tick} 的原因。
 *
 * <h2>本移植的写法</h2>
 * 仍然只替换「旧版真正改掉的那一段」：在 {@code tick} 的 HEAD 处，<b>仅当</b>
 * 背面不是储罐（{@code getTank() == null}）<b>且</b>背面是燃气机时，自己按 Create
 * 有效分支的逻辑驱动一次动力轴；其它情况一律 {@code return}，完全交给 Create。
 * 另加一处对 {@code Level#isLoaded} 调用点的 {@code ci.cancel()}，防止 Create 的
 * 无效分支紧接着用 {@code shaft.update(worldPosition, 0, 0)} 把刚写入的转速清零
 * （javap 核实 {@code tick} 里 {@code isLoaded} 只出现一次，即 100）。
 *
 * <p>下面 {@code vs_clockwork$driveShaftFromGasEngine} 的每一步都逐条对应
 * {@code SteamEngineBlockEntity#tick} 有效分支（字节码 117-434）：
 * {@code 119-176} 轴/朝向 → {@code 177-219} 方块与 facing → {@code 220-235} 效率与成就 →
 * {@code 258-317} conveyedSpeedLevel 与两个取反 → {@code 319-397} 反向自动翻转 →
 * {@code 397-406} shaft.update。唯一替换的输入是效率本身：
 * 原版 {@code tank.boiler.getEngineEfficiency(tank.getTotalTankSize())} → 燃气机的
 * {@link GasEngineBlockEntity#getEngineEfficiency()}。
 *
 * <p>与旧版的差异：旧版还会调用燃气机的 {@code spawnParticles(...)} 与 Create 的
 * 客户端粒子，本移植的 {@code GasEngineBlockEntity} 没有对应方法，因此<b>不发射粒子</b>
 * （纯表现层，不影响转速/应力）。
 */
@Mixin(SteamEngineBlockEntity.class)
public abstract class MixinSteamEngineBlockEntity {

    /** Create 自己 {@code tick} 有效分支里也会读写的同一个字段（javap 核实为 protected）。 */
    @Shadow
    protected ScrollOptionBehaviour<WindmillBearingBlockEntity.RotationDirection> movementDirection;

    @Inject(method = "tick", at = @At("HEAD"))
    private void vs_clockwork$gasEngineTick(final CallbackInfo ci) {
        final SteamEngineBlockEntity self = vs_clockwork$self();
        if (self.getTank() != null) {
            return; // 背面是储罐/锅炉：Create 的有效分支，原样不动
        }
        if (!AllBlocks.STEAM_ENGINE.has(self.getBlockState())) {
            return; // 不是蒸汽机方块（正常不会发生）：交回 Create
        }
        final GasEngineBlockEntity engine = vs_clockwork$findGasEngine();
        if (engine == null) {
            return; // 背面既不是储罐也不是燃气机：Create 的无效分支会照常把轴清零
        }
        vs_clockwork$driveShaftFromGasEngine(self, engine);
    }

    /**
     * Create 的无效分支在末尾会用 {@code 0, 0} 更新动力轴。背面是燃气机时我们已经
     * 在 HEAD 处正确驱动过它，这里必须拦下那次清零。{@code isLoaded} 在 {@code tick}
     * 里只出现一次（字节码 100，位于无效分支内），所以不需要 ordinal。
     */
    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;isLoaded(Lnet/minecraft/core/BlockPos;)Z"),
            cancellable = true)
    private void vs_clockwork$keepGasEngineShaft(final CallbackInfo ci) {
        if (vs_clockwork$findGasEngine() != null) {
            ci.cancel();
        }
    }

    /** 见类注释：逐条镜像 {@code SteamEngineBlockEntity#tick} 的有效分支，只换掉效率来源。 */
    @Unique
    private void vs_clockwork$driveShaftFromGasEngine(final SteamEngineBlockEntity self,
                                                      final GasEngineBlockEntity engine) {
        final PoweredShaftBlockEntity shaft = self.getShaft();
        if (shaft == null) {
            return;
        }

        final BlockState shaftState = shaft.getBlockState();
        Direction.Axis targetAxis = Direction.Axis.X;
        if (shaftState.getBlock() instanceof IRotate ir) {
            targetAxis = ir.getRotationAxis(shaftState);
        }
        final boolean verticalTarget = targetAxis == Direction.Axis.Y;

        final BlockState blockState = self.getBlockState();
        Direction facing = SteamEngineBlock.getFacing(blockState);
        if (facing.getAxis() == Direction.Axis.Y) {
            facing = blockState.getValue(SteamEngineBlock.FACING);
        }

        final float efficiency = Mth.clamp(engine.getEngineEfficiency(), 0, 1);
        if (efficiency > 0) {
            ((SmartBlockEntity) (Object) self).award(AllAdvancements.STEAM_ENGINE);
        }

        int conveyedSpeedLevel = efficiency == 0 ? 1
                : verticalTarget ? 1
                : (int) GeneratingKineticBlockEntity.convertToDirection(1, facing);
        if (targetAxis == Direction.Axis.Z) {
            conveyedSpeedLevel *= -1;
        }
        if (movementDirection.get() == WindmillBearingBlockEntity.RotationDirection.COUNTER_CLOCKWISE) {
            conveyedSpeedLevel *= -1;
        }

        final float shaftSpeed = shaft.getTheoreticalSpeed();
        if (shaft.hasSource() && shaftSpeed != 0 && conveyedSpeedLevel != 0
                && (shaftSpeed > 0) != (conveyedSpeedLevel > 0)) {
            movementDirection.setValue(1 - movementDirection.get()
                    .ordinal());
            conveyedSpeedLevel *= -1;
        }

        shaft.update(self.getBlockPos(), conveyedSpeedLevel, efficiency);
    }

    /** 背面（朝向的反方向）的燃气机方块实体；没有则返回 null。 */
    @Unique
    private GasEngineBlockEntity vs_clockwork$findGasEngine() {
        final SteamEngineBlockEntity self = vs_clockwork$self();
        final Level level = self.getLevel();
        if (level == null) {
            return null;
        }
        final Direction facing = SteamEngineBlock.getFacing(self.getBlockState());
        final BlockEntity be = level.getBlockEntity(self.getBlockPos()
                .relative(facing.getOpposite()));
        return be instanceof final GasEngineBlockEntity engine ? engine : null;
    }

    @Unique
    private SteamEngineBlockEntity vs_clockwork$self() {
        return (SteamEngineBlockEntity) (Object) this;
    }
}
