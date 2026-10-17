package org.valkyrienskies.clockwork.integration.ponder.scenes;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.foundation.instruction.RotateSceneInstruction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.clockwork.content.kinetics.CWKineticsIndex;
import org.valkyrienskies.clockwork.integration.ponder.ClockworkPonderScenes;

/**
 * 弯度机翼（{@code cambered_wings}）的 Ponder 场景。
 *
 * <p>对应官方 {@code ponder/cambered_wings.nbt} + 旧版
 * {@code content/ponders/PhysicsPonders.kt#camberedWings}。
 *
 * <p>移植差异：
 * <ul>
 *   <li>{@code moveSectionAsShip} → {@link ClockworkPonderScenes#animateAsShip}（同语义：初速度 + 衰减）。</li>
 *   <li>旧版把"弯度翼层"瞬移到位用 0 tick 的 {@code moveSection}；1.21.1 会做 0 除，这里用 1 tick。</li>
 *   <li>攻角平行四边形用 {@link ClockworkPonderScenes#chaseParallelogram}（旧版 Kotlin 扩展的 1:1 移植）。</li>
 *   <li>镜头旋转仍用 {@code RotateSceneInstruction}（1.21.1 该类从 {@code com.simibubi.create}
 *       搬到了 {@code net.createmod.ponder.foundation.instruction}，构造签名不变）。</li>
 * </ul>
 *
 * <p>文本键：{@code vs_clockwork.ponder.cambered_wings.header / .text_1..6}。
 */
public final class CamberedWingsScenes {

    private CamberedWingsScenes() {
    }

    public static void camberedWings(final SceneBuilder builder, final SceneBuildingUtil util) {
        final CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("cambered_wings", "Cambered wings");
        scene.configureBasePlate(0, 0, 7);

        final Selection horizontalFlaps = WingsScenes.horizontalWings(util);
        final Selection entirePlane = util.select().layer(2).add(util.select().layer(3));
        final BlockPos wingTip = util.grid().at(1, 2, 2);

        ElementLink<WorldSectionElement> floor =
                scene.world().showIndependentSection(util.select().layer(0), Direction.DOWN);
        scene.idle(10);

        ElementLink<WorldSectionElement> contraption =
                scene.world().showIndependentSection(entirePlane, Direction.DOWN);
        scene.idle(15);

        // ---------- 普通机翼：攻角 0 ----------
        ClockworkPonderScenes.text(scene, 1, 60, util.vector().centerOf(wingTip));
        scene.idle(70);

        scene.addInstruction(new RotateSceneInstruction(0.0F, 180.0F, false));
        scene.idle(40);
        scene.addKeyframe();

        // 圈出迎风面（旧版两个绿色框：主翼前缘与尾翼前缘）
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, 1,
                new AABB(1.0, 2.25, 1.0, 4.0, 2.75, 1.0), 80);
        scene.overlay().chaseBoundingBoxOutline(PonderPalette.GREEN, 2,
                new AABB(5.0, 2.25, 1.0, 6.0, 2.75, 1.0), 80);

        ClockworkPonderScenes.text(scene, 2, 70, util.vector().centerOf(util.grid().at(1, 2, 1)));
        scene.idle(90);

        scene.addInstruction(new RotateSceneInstruction(-35.0F, 55.0F + 90.0F, false));
        scene.idle(35);
        scene.addKeyframe();

        ClockworkPonderScenes.text(scene, 3, 40, util.vector().centerOf(wingTip));
        scene.idle(60);

        // 滑翔：结构向前下方滑走，地面向后退
        ClockworkPonderScenes.animateAsShip(scene, contraption, 80,
                new Vec3(0.0, -0.01, 0.0), new Vec3(0.0, 0.0, 0.2));
        ClockworkPonderScenes.animateAsShip(scene, floor, 80, new Vec3(0.4, 0.0, 0.0), Vec3.ZERO);
        scene.idle(80);

        scene.world().hideIndependentSection(contraption, Direction.UP);
        scene.world().hideIndependentSection(floor, Direction.DOWN);
        scene.idle(15);

        floor = scene.world().showIndependentSection(util.select().layer(0), Direction.UP);
        scene.idle(15);
        contraption = scene.world().showIndependentSection(entirePlane, Direction.DOWN);
        scene.idle(15);

        // ---------- 弯度机翼：攻角 20° ----------
        scene.addKeyframe();
        scene.world().replaceBlocks(horizontalFlaps, CWKineticsIndex.WING.getDefaultState(), true);

        ElementLink<WorldSectionElement> cambered =
                scene.world().showIndependentSectionImmediately(util.select().layersFrom(4));
        // 弯度翼层本来放在 y=4，先瞬移下来贴到机翼位置（旧版 0 tick，这里 1 tick）
        scene.world().moveSection(cambered, new Vec3(0.0, -2.0, 0.0), 1);
        // 立刻清掉原地那层方块，避免 z-fighting
        scene.world().replaceBlocks(horizontalFlaps, Blocks.AIR.defaultBlockState(), false);

        ClockworkPonderScenes.text(scene, 4, 60, util.vector().centerOf(wingTip));
        scene.idle(70);

        scene.addInstruction(new RotateSceneInstruction(0.0F, 180.0F, false));
        scene.idle(40);
        scene.addKeyframe();

        // 四段 20° 攻角的平行四边形（从尾翼到翼根）
        ClockworkPonderScenes.chaseParallelogram(scene, PonderPalette.GREEN,
                new Vec3(5.0, 2.6, 1.0), 1.0, 0.4, Math.toRadians(-20.0), Direction.SOUTH, 80);
        ClockworkPonderScenes.chaseParallelogram(scene, PonderPalette.GREEN,
                new Vec3(3.0, 2.6, 1.0), 1.0, 0.4, Math.toRadians(-20.0), Direction.SOUTH, 80);
        ClockworkPonderScenes.chaseParallelogram(scene, PonderPalette.GREEN,
                new Vec3(2.0, 2.6, 1.0), 1.0, 0.4, Math.toRadians(-20.0), Direction.SOUTH, 80);
        ClockworkPonderScenes.chaseParallelogram(scene, PonderPalette.GREEN,
                new Vec3(1.0, 2.6, 1.0), 1.0, 0.4, Math.toRadians(-20.0), Direction.SOUTH, 80);

        ClockworkPonderScenes.text(scene, 5, 70, util.vector().centerOf(util.grid().at(1, 2, 1)));
        scene.idle(90);

        scene.addInstruction(new RotateSceneInstruction(-35.0F, 55.0F + 90.0F, false));
        scene.idle(40);
        scene.addKeyframe();

        ClockworkPonderScenes.text(scene, 6, 40, util.vector().centerOf(wingTip));
        scene.idle(60);

        // 升力更大：结构爬得更高（旧版同为 -0.2 的俯仰速度，这里方向相反）
        ClockworkPonderScenes.animateAsShip(scene, contraption, 80,
                new Vec3(0.0, -0.01, 0.0), new Vec3(0.0, 0.0, -0.2));
        ClockworkPonderScenes.animateAsShip(scene, cambered, 80,
                new Vec3(0.0, -0.01, 0.0), new Vec3(0.0, 0.0, -0.2));
        ClockworkPonderScenes.animateAsShip(scene, floor, 80, new Vec3(0.4, 0.0, 0.0), Vec3.ZERO);
        scene.idle(80);

        scene.world().hideIndependentSection(contraption, Direction.UP);
        scene.world().hideIndependentSection(cambered, Direction.UP);
        scene.world().hideIndependentSection(floor, Direction.DOWN);
        scene.idle(15);

        floor = scene.world().showIndependentSection(util.select().layer(0), Direction.UP);
        scene.idle(15);
        contraption = scene.world().showIndependentSection(entirePlane, Direction.DOWN);

        cambered = scene.world().showIndependentSectionImmediately(util.select().layersFrom(4));
        // 两段位移是为了跟淡入动画对齐（旧版注释原话："mostly"）
        scene.world().moveSection(cambered, new Vec3(0.0, -1.5, 0.0), 1);
        scene.world().moveSection(cambered, new Vec3(0.0, -0.5, 0.0), 15);
        scene.idle(15);

        scene.markAsFinished();
    }
}
