package org.valkyrienskies.clockwork.content.physicalities.wing;

import net.minecraft.world.level.block.state.BlockState;

/**
 * 襟翼：与机翼同为平面翼面，但没有弯度，因此只有迎面气流攻角产生的升力。
 *
 * <p>对应旧版 {@code content.physicalities.wing.FlapBlock}。旧版它与机翼的唯一差别就是
 * {@code getWing()} 里的攻角偏置（0 而不是 10°），所以这里只保留这个常量，其余全部继承。
 *
 * <p>注：旧版还有"被襟翼轴承带动偏转的襟翼"（角度由轴承给出）；
 * 那个角度属于 {@code andesite_flap_bearing} / {@code smart_flap_bearing} 的玩法，
 * 由轴承侧驱动方块朝向变化，这里仍按固定 0 攻角参与气动。
 */
public class FlapBlock extends ConnectedWingBlock {

    /** 襟翼没有弯度（旧版 {@code getWing()} 传 0）。 */
    public static final double CAMBER_ATTACKING_BIAS = 0.0;

    public FlapBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected double getAngleOfAttackRadians(BlockState state) {
        return CAMBER_ATTACKING_BIAS;
    }
}
