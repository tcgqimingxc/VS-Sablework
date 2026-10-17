package org.valkyrienskies.clockwork.content.logistics.gas.engine;

import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.clockwork.content.logistics.gas.CWGasIndex;
import org.valkyrienskies.clockwork.content.logistics.gas.IGasNodeBlock;

/**
 * 燃气机：把气体网络里的热能（温度 × 流量）变成动力，通过驱动贴着的 Create 蒸汽机输出。
 *
 * <p>对应旧版 {@code GasEngineBlock}（{@code RotatedPillarBlock} + Kelvin 节点）。
 * 移植差异：
 * <ul>
 *   <li>方块状态只有 {@code axis}，与 {@code assets/vs_clockwork/blockstates/gas_engine.json} 一致；
 *       这里改用 Create 的 {@link RotatedPillarKineticBlock} 以获得标准的朝向放置逻辑，
 *       它同样只用 {@code AXIS}，因此资源文件不用改。</li>
 *   <li>旧版在 {@code onPlace/onRemove} 里手工维护 {@code attachedEngines}；
 *       本移植由 Create 锅炉每刻重扫（见 {@code MixinBoilerData}），不需要这两个回调。</li>
 * </ul>
 *
 * <p>摆放方式（与旧版一致）：燃气机<b>夹在锅炉与蒸汽机之间</b>，
 * 其 {@code axis} 指向"锅炉 ↔ 蒸汽机"这条连线；蒸汽机贴在燃气机背离锅炉的那一面上。
 */
public class GasEngineBlock extends RotatedPillarKineticBlock
        implements IBE<GasEngineBlockEntity>, IGasNodeBlock {

    public GasEngineBlock(Properties properties) {
        super(properties);
    }

    /** 该方块状态是否是燃气机（mixin 里做类型判断用，避免到处 instanceof）。 */
    public static boolean isGasEngine(BlockState state) {
        return state.getBlock() instanceof GasEngineBlock;
    }

    /** 燃气机的轴向：{@code AXIS} 属性没有对应 Direction，这里用正方向代表（旧版同义）。 */
    public static Direction getFacing(BlockState state) {
        return Direction.fromAxisAndDirection(state.getValue(AXIS), Direction.AxisDirection.POSITIVE);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(AXIS);
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        // 动力从"背离锅炉的那一端"输出：两端都给轴，贴在哪一侧都能带蒸汽机
        return face.getAxis() == state.getValue(AXIS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 旧版用 context.nearestLookingDirection.axis；RotatedPillarKineticBlock 已实现同样行为，
        // 但这里显式写出以便与旧版对照（并且避免 Create 的"优先轴"逻辑改变手感）。
        return defaultBlockState().setValue(AXIS, context.getNearestLookingDirection().getAxis());
    }

    @Override
    public boolean gasConnectable(BlockState state, Direction dir) {
        // 旧版 canConnectTo：只有轴向两面算连通（一端接锅炉方向的气管，另一端接蒸汽机方向）
        return dir.getAxis() == state.getValue(AXIS);
    }

    @Override
    public Class<GasEngineBlockEntity> getBlockEntityClass() {
        return GasEngineBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends GasEngineBlockEntity> getBlockEntityType() {
        return CWGasIndex.GAS_ENGINE_BE.get();
    }
}
