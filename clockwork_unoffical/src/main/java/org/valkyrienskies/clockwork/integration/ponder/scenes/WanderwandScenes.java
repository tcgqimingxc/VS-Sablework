package org.valkyrienskies.clockwork.integration.ponder.scenes;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.valkyrienskies.clockwork.content.contraptions.CWContraptionsIndex;
import org.valkyrienskies.clockwork.content.curiosities.tools.CWToolsIndex;
import org.valkyrienskies.clockwork.integration.ponder.ClockworkPonderScenes;

/**
 * 漫游之杖造船（{@code wanderwand}）的 Ponder 场景。
 *
 * <p>对应官方 {@code ponder/wanderwand.nbt} + 旧版 {@code content/ponders/PhysicsPonders.kt#createShip}。
 *
 * <p>移植差异：
 * <ul>
 *   <li>旧版最后几段用 {@code moveSection(section, Vec3(0, -0.1, 0), ticks)} 拼出"结构被重力拉下去、
 *       又被物理引擎托住"的抖动；Ponder 1.21.1 的 {@code moveSection} 签名完全一致，逐条照抄。</li>
 *   <li>旧版开头有一句 0 tick 的 {@code moveSection(..., Vec3.ZERO, 0)} 归位（实际是空操作），已删除。</li>
 *   <li>旧版用 {@code ClockworkItems.WANDERWAND / CREATIVE_GRAVITRON}，本移植对应
 *       {@link CWToolsIndex#WANDERWAND} / {@link CWToolsIndex#CREATIVE_GRAVITRON}。</li>
 * </ul>
 *
 * <p>文本键：{@code vs_clockwork.ponder.wanderwand.header / .text_1..3}。
 */
public final class WanderwandScenes {

    private WanderwandScenes() {
    }

    public static void wanderwand(final SceneBuilder builder, final SceneBuildingUtil util) {
        final CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("wanderwand", "Creating ships using the Wanderwand");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.setSceneOffsetY(-1.0F);
        scene.idle(15);

        final BlockPos infuserSlot = util.grid().at(0, 2, 0);
        final Selection ship = util.select().fromTo(0, 1, 1, 4, 3, 3);

        final ElementLink<WorldSectionElement> contraption =
                scene.world().showIndependentSection(ship, Direction.DOWN);
        scene.idle(15);

        // 用漫游之杖点两下，框选整艘船
        scene.overlay().showControls(util.vector().topOf(0, 2, 1), Pointing.UP, 40)
                .withItem(CWToolsIndex.WANDERWAND.asStack()).rightClick();
        scene.idle(6);
        scene.effects().indicateSuccess(util.grid().at(0, 2, 1));
        scene.idle(45);

        scene.overlay().showControls(
                        util.vector().blockSurface(util.grid().at(4, 3, 4), Direction.DOWN), Pointing.DOWN, 40)
                .withItem(CWToolsIndex.WANDERWAND.asStack()).rightClick();
        scene.idle(6);

        // 选区从第一个角开始长大 —— 与旧版用同一个 key 覆盖两次，第二次才是"长大"的框
        final Object boxKey = "wanderwand_selection";
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.BLUE, boxKey,
                new AABB(util.grid().at(0, 2, 1)), 1);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.BLUE, boxKey,
                new AABB(util.grid().at(0, 2, 1)).expandTowards(4.0, 1.0, 2.0), 70);
        scene.idle(70);

        // 放物理灌注器，再用杖把它激活
        scene.world().setBlock(util.grid().at(0, 1, 0),
                CWContraptionsIndex.PHYSICS_INFUSER.getDefaultState(), false);
        scene.world().showSection(util.select().position(0, 1, 0), Direction.NORTH);
        scene.idle(20);

        ClockworkPonderScenes.text(scene, 1, 40,
                        util.vector().blockSurface(infuserSlot, Direction.WEST))
                .attachKeyFrame().placeNearTarget();
        scene.overlay().showControls(util.vector().blockSurface(infuserSlot, Direction.DOWN), Pointing.DOWN, 40)
                .withItem(CWToolsIndex.WANDERWAND.asStack()).rightClick();
        scene.idle(50);

        ClockworkPonderScenes.text(scene, 2, 40,
                        util.vector().blockSurface(util.grid().at(2, 2, 2), Direction.WEST))
                .attachKeyFrame().placeNearTarget();
        scene.overlay().showControls(util.vector().blockSurface(util.grid().at(2, 2, 2), Direction.DOWN),
                        Pointing.DOWN, 40)
                .withItem(CWToolsIndex.CREATIVE_GRAVITRON.asStack()).rightClick();
        scene.idle(50);

        // 物理引擎接管：结构先被重力拉下去，再被托住，最后静止 —— 旧版的抖动序列逐条保留
        scene.world().moveSection(contraption, util.vector().of(0.0, -0.1, 0.0), 4);
        scene.idle(4);
        scene.world().moveSection(contraption, util.vector().of(0.0, -0.1, 0.0), 3);
        scene.idle(3);
        scene.world().moveSection(contraption, util.vector().of(0.0, -0.1, 0.0), 2);
        scene.idle(2);
        scene.world().moveSection(contraption, util.vector().of(0.0, -0.7, 0.0), 10);
        scene.idle(14);
        scene.world().moveSection(contraption, util.vector().of(0.0, 0.015, 0.0), 3);
        scene.idle(3);
        scene.world().moveSection(contraption, util.vector().of(0.0, -0.010, 0.0), 2);
        scene.idle(2);
        scene.world().moveSection(contraption, util.vector().of(0.0, -0.005, 0.0), 1);
        scene.idle(20);

        ClockworkPonderScenes.text(scene, 3, 40,
                        util.vector().blockSurface(util.grid().at(2, 0, 3), Direction.WEST))
                .attachKeyFrame().placeNearTarget();
        scene.idle(37 * 4);

        scene.markAsFinished();
    }
}
