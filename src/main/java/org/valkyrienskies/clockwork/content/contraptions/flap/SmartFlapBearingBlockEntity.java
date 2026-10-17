package org.valkyrienskies.clockwork.content.contraptions.flap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.clockwork.config.ClockworkConfig;
import org.valkyrienskies.clockwork.content.contraptions.phys.bearing.BearingControl;

/**
 * 智能襟翼轴承方块实体。
 *
 * <p>对应旧版 {@code content/contraptions/flap/smart_flap/SmartFlapBearingBlockEntity}
 * （旧版靠 {@code DualLinkBehaviour} + 频率，由"联动控制器"远程设定摆角）。
 *
 * <p><b>与旧版差异（重要）</b>：联动控制器属于其它切片，本切片没有它的依赖，
 * 因此这里把"远程控制"换成<b>红石比例控制</b>：信号 0 级 = 不摆，
 * 15 级 = 配置里的完整振幅。这样方块在没有联动控制器的情况下依然是一个有明确用法的部件，
 * 而不是空壳；等联动控制器落地后，只需把 {@link #amplitudeScale()} 换成频率查表即可。
 */
public class SmartFlapBearingBlockEntity extends FlapBearingBlockEntity {

    public SmartFlapBearingBlockEntity(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    /** 本刻的摆幅比例（0..1），由红石信号给出。 */
    private double amplitudeScale() {
        if (this.level == null) {
            return 1.0;
        }
        return this.level.getBestNeighborSignal(this.getBlockPos()) / 15.0;
    }

    @Override
    protected BearingControl getControl() {
        // 用一个"按当前红石缩放振幅"的摆动策略包住父类的摆动策略，
        // 这样父类的相位推进逻辑（与输入轴转速绑定）完全复用。
        final BearingControl base = super.getControl();
        final double scale = this.amplitudeScale();
        final double amplitude = ClockworkConfig.server().flapAmplitudeDegrees.getF() * scale;
        return new BearingControl() {
            @Override
            public double advanceDegrees(final double targetDegrees, final double angularSpeedDegreesPerTick) {
                // 父策略算出来的角度按比例缩放（0 = 收到 0 级信号，完全不动）
                final double full = base.advanceDegrees(targetDegrees, angularSpeedDegreesPerTick);
                final double fullAmplitude = ClockworkConfig.server().flapAmplitudeDegrees.getF();
                if (fullAmplitude <= 0.0) {
                    return 0.0;
                }
                return full / fullAmplitude * amplitude;
            }

            @Override
            public double goalRadians(final BearingControl.Context ctx) {
                return net.createmod.catnip.math.AngleHelper.rad(ctx.interpolatedDegrees());
            }
        };
    }
}
