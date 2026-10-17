package org.valkyrienskies.clockwork.content.contraptions.propeller.copter;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.clockwork.content.contraptions.propeller.PropellerBearingBlockEntity;
import org.valkyrienskies.clockwork.content.contraptions.propeller.PropellerKind;

/**
 * 直升机旋翼方块实体。
 *
 * <p>对应旧版 {@code content/contraptions/propeller/copter/CopterBearingBlockEntity}（307 行）。
 * 旧版把"叶片长度/角度从物品上读、按叶片逐个建模"的整套逻辑写在 {@code PropellerController} 里；
 * 移植后叶片几何统一走 {@link PropellerKind#COPTER} 的当量参数，
 * 装配 / 约束 / 施力全部复用螺旋桨轴承（见 {@link PropellerBearingBlockEntity}）。
 *
 * <p>与旧版差异：旧版旋翼有独立的"多叶片倾斜盘"（每个叶片角度可被叶片控制器改），
 * 本切片暂未实现叶片控制器，所以旋翼桨距是固定的。
 */
public class CopterBearingBlockEntity extends PropellerBearingBlockEntity {

    public CopterBearingBlockEntity(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected PropellerKind getKind() {
        return PropellerKind.COPTER;
    }
}
