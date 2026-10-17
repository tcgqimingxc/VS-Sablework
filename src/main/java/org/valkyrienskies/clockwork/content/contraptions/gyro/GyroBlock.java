package org.valkyrienskies.clockwork.content.contraptions.gyro;

import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.valkyrienskies.clockwork.content.contraptions.CWContraptionsIndex;

/**
 * 陀螺（旧版 {@code content/physicalities/gyro/GyroBlock}）。
 *
 * <p>放在结构上，靠旋转产生的"陀螺力矩"把结构的上方向拉回目标方向
 * （目标方向由四向红石信号倾斜，见 {@link GyroBlockEntity}）。
 *
 * <p>移植位置说明：旧版这个方块属于 {@code content/physicalities}，
 * 但按分工本次只允许写 {@code content/contraptions/**}，因此落在
 * {@code content/contraptions/gyro}；注册名保持 {@code gyro} 不变。
 */
public class GyroBlock extends KineticBlock implements IBE<GyroBlockEntity> {

    public GyroBlock(final Properties properties) {
        super(properties);
    }

    @Override
    public Class<GyroBlockEntity> getBlockEntityClass() {
        return GyroBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends GyroBlockEntity> getBlockEntityType() {
        return CWContraptionsIndex.GYRO_BE.get();
    }

    @Override
    public Direction.Axis getRotationAxis(final BlockState state) {
        return Direction.Axis.Y;
    }

    /**
     * 方块本体完全交给 {@code GyroRenderer} 绘制（旧版 {@code GyroBlock.getRenderShape} 同款）。
     *
     * <p>为什么必须这样：陀螺的"会转"体现在整个方块模型绕 Y 轴自转上，而这个模型
     * （{@code block/gyro/block} → {@code gyro.obj}）里既有底板也有转子。
     * 如果保留默认的 {@code MODEL}，静态的方块模型会先被区块渲染器画一遍，
     * {@code GyroRenderer} 再画一遍转动的，结果是"两个陀螺叠在一起、其中一个在转"。
     * 设成 {@code ENTITYBLOCK_ANIMATED} 后静态那份不再画，外观与旧版一致。
     *
     * <p>唯一的副作用：脱离 BER 的场合（如 Ponder 的静态预览工具）看不到方块模型，
     * 与旧版行为相同。物品栏图标走物品模型 {@code block/gyro/item}，不受影响。
     */
    @Override
    protected RenderShape getRenderShape(final BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public boolean hasShaftTowards(final LevelReader level, final BlockPos pos, final BlockState state,
                                   final Direction face) {
        return face == Direction.DOWN;
    }

    @Override
    protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos,
                                  final CollisionContext context) {
        return Shapes.block();
    }
}
