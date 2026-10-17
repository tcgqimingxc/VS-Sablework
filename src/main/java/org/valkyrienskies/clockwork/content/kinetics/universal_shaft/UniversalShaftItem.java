package org.valkyrienskies.clockwork.content.kinetics.universal_shaft;

import org.valkyrienskies.clockwork.content.kinetics.universal_joint.UniversalJointItem;

/**
 * 「可压缩轴」：通用轴专用的连接器。
 *
 * <p>对应旧版 {@code content.kinetics.universal_shaft.UniversalShaftItem}，
 * 行为完全由 {@link UniversalJointItem} 提供，这里只是给出一个独立物品用于区分外观与消耗。
 */
public class UniversalShaftItem extends UniversalJointItem {

    public UniversalShaftItem(Properties properties) {
        super(properties);
    }
}
