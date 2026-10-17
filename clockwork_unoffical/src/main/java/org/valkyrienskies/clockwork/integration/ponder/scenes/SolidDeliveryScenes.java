package org.valkyrienskies.clockwork.integration.ponder.scenes;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.clockwork.integration.ponder.ClockworkPonderScenes;

/**
 * 固体运输（{@code solid_delivery}）的 Ponder 场景：输物炮与输物槽。
 *
 * <p>对应官方 {@code ponder/solid_delivery.nbt} + 旧版
 * {@code content/ponders/OtherPonders.kt#solid_delivery}。
 *
 * <p>移植差异：
 * <ul>
 *   <li>无实质差异：场景里除输物炮/槽外全是 Create 方块（Depot / Chute / Belt / 配重弹出器），
 *       {@code AllBlocks.WEIGHTED_EJECTOR} 等常量在 Create 6.0.10 里都还在。</li>
 *   <li>本移植的输物炮确实是抛物线反解发射（见 {@code DeliveryCannonBlockEntity}），
 *       "能跨越飞船投送"这条（text_6）成立。</li>
 * </ul>
 *
 * <p>文本键：{@code vs_clockwork.ponder.solid_delivery.header / .text_1..9}。
 */
public final class SolidDeliveryScenes {

    private SolidDeliveryScenes() {
    }

    public static void solidDelivery(final SceneBuilder builder, final SceneBuildingUtil util) {
        final CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("solid_delivery", "Solid delivery");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.setSceneOffsetY(-1.0F);
        scene.idle(15);

        final Selection depotLine = util.select().fromTo(0, 1, 2, 4, 2, 2);
        final Selection chuteLine = util.select().fromTo(0, 1, 3, 4, 2, 3);
        final Selection beltLine = util.select().fromTo(0, 1, 4, 4, 2, 4);

        final BlockPos cannonPos = util.grid().at(0, 2, 2);
        final BlockPos chutePos = util.grid().at(4, 2, 2);
        final Vec3 cannonSlot = util.vector().blockSurface(cannonPos, Direction.NORTH).add(0.0, -0.35, 0.0);
        final Vec3 chuteSlot = util.vector().blockSurface(chutePos, Direction.NORTH).add(0.0, -0.35, 0.0);

        scene.world().showSection(depotLine, Direction.DOWN);
        ClockworkPonderScenes.text(scene, 1, 80,
                        util.vector().blockSurface(cannonPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(100);

        // 频率槽：炮和槽同频才会投递
        scene.overlay().showFilterSlotInput(cannonSlot, Direction.NORTH, 60);
        scene.overlay().showFilterSlotInput(chuteSlot, Direction.NORTH, 60);

        ClockworkPonderScenes.text(scene, 2, 80, cannonSlot).attachKeyFrame().placeNearTarget();
        scene.idle(100);
        ClockworkPonderScenes.text(scene, 3, 60, chuteSlot).placeNearTarget();
        scene.idle(80);

        ClockworkPonderScenes.text(scene, 4, 80,
                        util.vector().blockSurface(cannonPos.below(), Direction.WEST))
                .attachKeyFrame().placeNearTarget();
        scene.idle(100);

        // 临时把炮/槽换成配重弹出器，对比"只能在本船内投"的限制
        scene.world().hideSection(depotLine, Direction.UP);
        scene.idle(15);
        scene.world().setBlock(cannonPos, Blocks.AIR.defaultBlockState(), false);
        scene.world().setBlock(chutePos, Blocks.AIR.defaultBlockState(), false);
        scene.world().setBlock(cannonPos.below(),
                AllBlocks.WEIGHTED_EJECTOR.getDefaultState().rotate(Rotation.CLOCKWISE_90), false);
        scene.world().showSection(depotLine, Direction.DOWN);
        scene.idle(15);

        ClockworkPonderScenes.text(scene, 5, 80,
                        util.vector().blockSurface(cannonPos.below(), Direction.WEST))
                .attachKeyFrame().placeNearTarget();
        scene.idle(100);

        // 换回输物炮 + 输物槽
        scene.world().hideSection(depotLine, Direction.UP);
        scene.idle(15);
        scene.world().restoreBlocks(depotLine);
        scene.idle(5);
        scene.world().showSection(depotLine, Direction.DOWN);
        scene.idle(15);

        ClockworkPonderScenes.text(scene, 6, 80,
                        util.vector().blockSurface(cannonPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(100);

        // 能放在 Depot / Chute / 传送带上
        scene.addKeyframe();
        scene.idle(20);

        ClockworkPonderScenes.text(scene, 7, 40,
                        util.vector().blockSurface(chutePos.below(), Direction.WEST))
                .placeNearTarget();
        scene.idle(60);

        scene.world().hideSection(depotLine, Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(chuteLine, Direction.DOWN);
        ClockworkPonderScenes.text(scene, 8, 40,
                        util.vector().blockSurface(util.grid().at(4, 1, 3), Direction.WEST))
                .placeNearTarget();
        scene.idle(60);

        scene.world().hideSection(chuteLine, Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(beltLine, Direction.DOWN);
        ClockworkPonderScenes.text(scene, 9, 40,
                        util.vector().blockSurface(util.grid().at(4, 1, 4), Direction.WEST))
                .placeNearTarget();
        scene.idle(60);

        scene.markAsFinished();
    }
}
