package org.valkyrienskies.clockwork.content.physicalities.extendon;

import net.minecraft.world.level.block.entity.BlockEntity;
import org.valkyrienskies.clockwork.content.kinetics.universal_joint.UniversalJoint;
import org.valkyrienskies.clockwork.content.kinetics.universal_joint.UniversalJointItem;

/**
 * 「软管卷」：伸缩杆专用的连接器。
 *
 * <p>对应旧版 {@code content.physicalities.extendon.ExtendonHoseItem}。
 *
 * <p>与旧版的差异：旧版允许它同时连接伸缩杆与「软管接口」（{@code HosePortBlockEntity}，
 * 属于气体物流切片）。那个方块在本移植里还不存在，所以这里先只接受伸缩杆；
 * 等气体切片落地后把 {@code isJoint} 放宽即可（旧版也是这个结构）。
 */
public class ExtendonHoseItem extends UniversalJointItem {

    public ExtendonHoseItem(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean isJoint(BlockEntity be) {
        return be instanceof UniversalJoint;
    }
}
