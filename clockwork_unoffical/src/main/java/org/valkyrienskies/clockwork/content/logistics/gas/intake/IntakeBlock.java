package org.valkyrienskies.clockwork.content.logistics.gas.intake;

import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.clockwork.content.logistics.gas.CWGasIndex;
import org.valkyrienskies.clockwork.content.logistics.gas.IGasNodeBlock;

/**
 * 进气口：带动力的环境空气入口。
 *
 * <p><b>旧版与新仓库都没有这个方块的实现源码</b>（只有 {@code blockstates/intake.json}
 * 的 6 向 {@code facing} 与 {@code models/block/intake/*}）。本实现是<b>重新设计</b>，
 * 定位为"空气压缩机的轻量替代"：
 *
 * <ul>
 *   <li>动力接口在<b>背面</b>（{@code facing} 的反方向），只有背面有轴。</li>
 *   <li>只有背面连通气体网络（口径朝外吸气），因此必须用管道从背面接出去。</li>
 *   <li>每刻按转速吸入环境空气（产量按海拔密度缩放）；不做副产物、不需要上方开阔。</li>
 * </ul>
 *
 * <p>与已实现的 {@code air_compressor} 的区别：压缩机是"高压产气 + 以太副产物 + 会堵塞"，
 * 进气口是"低压补气"，用来给负压管网补空气或给燃机/气室供气。
 *
 * <p>方块状态：用 {@link DirectionalKineticBlock}（自带 {@code facing} + {@code powered}），
 * 与资源 JSON 声明的 6 向 {@code facing} 完全一致。
 */
public class IntakeBlock extends DirectionalKineticBlock implements IBE<IntakeBlockEntity>, IGasNodeBlock {

    public IntakeBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        // 动力从背面输入
        return face == state.getValue(FACING).getOpposite();
    }

    @Override
    public boolean gasConnectable(BlockState state, Direction dir) {
        return dir == state.getValue(FACING).getOpposite();
    }

    @Override
    public Class<IntakeBlockEntity> getBlockEntityClass() {
        return IntakeBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends IntakeBlockEntity> getBlockEntityType() {
        return CWGasIndex.INTAKE_BE.get();
    }
}
