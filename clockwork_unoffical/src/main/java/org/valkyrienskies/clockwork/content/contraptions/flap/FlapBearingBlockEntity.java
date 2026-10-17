package org.valkyrienskies.clockwork.content.contraptions.flap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.clockwork.content.contraptions.phys.bearing.AbstractBearingBlockEntity;
import org.valkyrienskies.clockwork.content.contraptions.phys.bearing.BearingControl;
import org.valkyrienskies.clockwork.content.contraptions.phys.bearing.BearingControls;

/**
 * 襟翼轴承方块实体。
 *
 * <p>对应旧版 {@code content/contraptions/flap/FlapBearingBlockEntity}（200 行）。
 * 装配 / 解体 / 约束 / 持久化直接复用 {@link AbstractBearingBlockEntity}，
 * 这里只提供"摆动"这一条旋转控制策略。
 *
 * <p>与旧版差异：
 * <ul>
 *   <li>旧版摆动由 {@code FlapContraption} + 每刻改方块实体角度实现（装置是 Create 的受控装置）；
 *       移植后摆动直接作用在子空间的伺服目标角上，因此子结构是真正的刚体摆动。</li>
 *   <li>旧版摆动相位与输入轴转速的关系是硬编码；这里接到
 *       {@code flap.phasePerShaftDegree} / {@code flap.amplitudeDegrees} 两个配置上。</li>
 * </ul>
 */
public class FlapBearingBlockEntity extends AbstractBearingBlockEntity {

    private final BearingControls.Oscillating control = new BearingControls.Oscillating();

    public FlapBearingBlockEntity(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected Direction getFacing() {
        return this.getBlockState().getValue(FlapBearingBlock.FACING);
    }

    @Override
    protected BearingControl getControl() {
        return this.control;
    }

    /** 当前摆角（度），供机翼/襟翼方块读取以计算升力。 */
    public double getCurrentFlapAngleDegrees() {
        return this.getTargetAngleDegrees();
    }

    @Override
    public void disassemble() {
        super.disassemble();
        this.control.reset();
    }
}
