package org.valkyrienskies.clockwork.content.contraptions.phys.bearing;

import com.simibubi.create.foundation.block.IBE;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.valkyrienskies.clockwork.content.contraptions.CWContraptionsIndex;

/**
 * 物理轴承（旧版 {@code content/contraptions/phys/bearing/PhysBearingBlock}）。
 *
 * <p>玩法：贴在结构一侧，空手右键把那一侧的连通结构装配成子空间，
 * 再靠输入轴驱动子结构相对母体旋转 —— 母体可以是一艘飞船/飞艇，也可以是世界。
 *
 * <p>与旧版差异：旧版用 VS2 的 {@code VSRevoluteJoint}（{@code driveFreeSpin}）+ 一个
 * {@code BearingController} 附件；这里用一个 Sable 转动约束 + 伺服电机实现，
 * 并额外提供"旋转模式"（跟随输入轴 / 锁定角度 / 自由铰链）三档。
 */
public class PhysBearingBlock extends AbstractBearingBlock implements IBE<PhysBearingBlockEntity> {

    public PhysBearingBlock(final Properties properties) {
        super(properties);
    }

    @Override
    public Class<PhysBearingBlockEntity> getBlockEntityClass() {
        return PhysBearingBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends PhysBearingBlockEntity> getBlockEntityType() {
        return CWContraptionsIndex.PHYS_BEARING_BE.get();
    }
}
