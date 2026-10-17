package org.valkyrienskies.clockwork.mixin.content.gas.boiler;

import com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.clockwork.content.logistics.gas.engine.GasEngineBlock;

/**
 * 允许蒸汽机贴在燃气机上（而不是只允许贴在锅炉/储罐上）。
 *
 * <p>对应旧版 {@code MixinSteamEngineBlock}。旧版因为 Forge/Fabric 映射差异同时注入
 * {@code m_53196_} 与 {@code canAttach}；1.21.1 的 NeoForge 编译产物只有
 * {@code canAttach(LevelReader, BlockPos, Direction)}（javap 已核实），所以只注入一次。
 *
 * <p>判断与旧版一致：看向的方向上（{@code pDirection} 指向被贴住的方块）是燃气机即可。
 */
@Mixin(SteamEngineBlock.class)
public class MixinSteamEngineBlock {

    @Inject(method = "canAttach", at = @At("RETURN"), cancellable = true)
    private static void vs_clockwork$canAttachGasEngine(final LevelReader reader, final BlockPos pos,
                                                        final Direction direction,
                                                        final CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            return;
        }
        if (GasEngineBlock.isGasEngine(reader.getBlockState(pos.relative(direction)))) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }
}
