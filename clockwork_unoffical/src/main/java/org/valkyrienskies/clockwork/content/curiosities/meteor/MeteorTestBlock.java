package org.valkyrienskies.clockwork.content.curiosities.meteor;

import com.mojang.serialization.MapCodec;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 调试用陨石生成器（Debug Meteor Spawner / {@code asteroid_block}）。
 *
 * <p>对应旧版 {@code curiosities/meteor/MeteorTestBlock.kt} + {@code MeteorStorm.kt}：
 * 放下方块时在它上方 30 格生成一颗陨石。
 *
 * <p>移植差异（VS2 → Sable）：旧版先用 {@code ShipAssembler} 在落点造一条"空船"，
 * 填完陨石方块后再把船设为动态（于是陨石会下落）。Sable 里对应的是
 * {@code SubLevelAssemblyHelper.assembleBlocks(level, anchor, blocks, bounds)}（已核实签名，
 * 且 {@code Simulated} 的 {@code SimAssemblyHelper} 就是这么用的）：
 * 先把方块写进世界，再整体装配成一个子空间，之后由 Sable 的物理接管 → 同样会下落。
 *
 * <p>注意：本方块的方块状态 JSON 缺失（{@code assets/vs_clockwork/blockstates/asteroid_block.json}
 * 不在本切片可改范围内），因此放下后在世界里可能不可见；它本来就是创造模式 OP 物品栏里的调试方块。
 */
public class MeteorTestBlock extends Block {

    public static final MapCodec<MeteorTestBlock> CODEC = simpleCodec(MeteorTestBlock::new);

    /** 旧版 {@code MeteorStorm.assembleMeteor} 用的参数。 */
    private static final int SPAWN_HEIGHT_OFFSET = 30;
    private static final double THRESHOLD = 0.035;
    private static final int MAX_DISTANCE = 10;
    private static final int META_BALLS = 3;

    public MeteorTestBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (level.isClientSide || isMoving) {
            return;
        }
        if (level instanceof ServerLevel serverLevel) {
            spawnMeteor(serverLevel, pos.offset(0, SPAWN_HEIGHT_OFFSET, 0));
        }
    }

    /** 生成并装配一颗陨石；装配后它会作为独立子空间自由下落。 */
    public static SubLevel spawnMeteor(ServerLevel level, BlockPos center) {
        List<BlockPos> filled = MeteorGenerator.generate(level, center, THRESHOLD, MAX_DISTANCE, META_BALLS);
        if (filled.isEmpty()) {
            return null;
        }
        BoundingBox3i bounds = BoundingBox3i.from(filled);
        // 与 Simulated 的 SimAssemblyHelper 一致：anchor 取集合里的第一个位置。
        return SubLevelAssemblyHelper.assembleBlocks(level, filled.get(0), filled, bounds);
    }
}
