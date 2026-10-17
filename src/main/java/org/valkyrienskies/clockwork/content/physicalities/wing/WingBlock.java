package org.valkyrienskies.clockwork.content.physicalities.wing;

import net.minecraft.world.level.block.state.BlockState;

/**
 * 机翼：带弯度（camber）的翼面，比襟翼多一个固定攻角偏置，因此同样迎面气流下升力更大。
 *
 * <p>对应旧版 {@code content.physicalities.wing.WingBlock}。
 *
 * <p>受力由 {@link ConnectedWingBlock} 实现的 Sable 升力提供者完成，这里只提供气动常量
 * （{@code getWing()} 里其余的公共数值 power 150 / drag 150 / breakingForce 10 机翼与襟翼相同，
 * 放在 {@link ConnectedWingBlock} 里共享）。
 */
public class WingBlock extends ConnectedWingBlock {

    /** 攻角偏置（弧度）：旧版 {@code getWing()} 里写死的 10°，是机翼"有弯度"的来源。 */
    public static final double CAMBER_ATTACKING_BIAS = Math.toRadians(10.0);

    public WingBlock(Properties properties) {
        super(properties);
    }

    /** 机翼攻角 = 固定弯度偏置（旧版机翼没有任何可变结构）。 */
    @Override
    protected double getAngleOfAttackRadians(BlockState state) {
        return CAMBER_ATTACKING_BIAS;
    }
}
