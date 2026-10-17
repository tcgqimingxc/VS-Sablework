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
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.clockwork.content.kinetics.CWKineticsIndex;
import org.valkyrienskies.clockwork.integration.ponder.ClockworkPonderScenes;

/**
 * 飞机小贴士（{@code plane_tips}）的 Ponder 场景。
 *
 * <p>对应官方 {@code ponder/plane_tips.nbt} + 旧版
 * {@code content/ponders/PhysicsPonders.kt#planeTips}。
 *
 * <p>移植差异：
 * <ul>
 *   <li>{@code moveSectionAsShip} → {@link ClockworkPonderScenes#animateAsShip}；
 *       0 tick 的瞬移 → 1 tick（避免 1.21.1 的 0 除）。</li>
 *   <li>垂直尾翼那段旧版就是 {@code WING} → {@code FLAP} 两次替换 + 镜头旋转 90°，
 *       本移植 1:1 照搬（旋转用 {@code RotateSceneInstruction}，签名不变）。</li>
 *   <li>旧版末尾留了个 TODO（"讲摆转轴承最佳实践"），本来就没做，这里也<b>不</b>补做 —— 保持与官方场景一致。</li>
 * </ul>
 *
 * <p>文本键：{@code vs_clockwork.ponder.plane_tips.header / .text_1..9}。
 */
public final class PlaneTipsScenes {

    private PlaneTipsScenes() {
    }

    public static void planeTips(final SceneBuilder builder, final SceneBuildingUtil util) {
        final CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("plane_tips", "Plane Tips");
        scene.configureBasePlate(0, 0, 7);

        final Selection horizontalFlaps = WingsScenes.horizontalWings(util);
        final Selection entirePlane = util.select().layer(2).add(util.select().layer(3));
        final BlockPos wingTip = util.grid().at(1, 2, 2);
        final BlockPos stabilizer = util.grid().at(5, 3, 3);

        ElementLink<WorldSectionElement> floor =
                scene.world().showIndependentSection(util.select().layer(0), Direction.DOWN);
        scene.idle(10);

        // 整架飞机（y=2 的翼面 + y=3 的垂尾位）一起淡入
        scene.world().showIndependentSection(entirePlane, Direction.DOWN);
        scene.idle(15);

        // ---------- Tip #1：大部分时候用普通机翼 ----------
        scene.overlay().showText(50).text(ClockworkPonderScenes.text(scene, 1));
        scene.idle(60);
        ClockworkPonderScenes.text(scene, 2, 60, util.vector().centerOf(wingTip));
        scene.idle(70);

        // 换成弯度翼看一眼"转过头"的效果，再收起来
        scene.world().replaceBlocks(horizontalFlaps, CWKineticsIndex.WING.getDefaultState(), true);
        final ElementLink<WorldSectionElement> cambered =
                scene.world().showIndependentSectionImmediately(util.select().layersFrom(4));
        scene.world().moveSection(cambered, new Vec3(0.0, -2.0, 0.0), 1);
        scene.world().replaceBlocks(horizontalFlaps, Blocks.AIR.defaultBlockState(), false);

        ClockworkPonderScenes.text(scene, 3, 60, util.vector().centerOf(wingTip));
        scene.idle(80);

        scene.world().hideIndependentSection(cambered, Direction.UP);
        scene.idle(15);
        scene.world().restoreBlocks(horizontalFlaps);

        // ---------- Tip #2：加一个垂直尾翼 ----------
        scene.addKeyframe();
        scene.overlay().showText(50).text(ClockworkPonderScenes.text(scene, 4));
        scene.idle(60);
        ClockworkPonderScenes.text(scene, 5, 60, util.vector().centerOf(stabilizer));
        scene.idle(70);
        ClockworkPonderScenes.text(scene, 6, 40, util.vector().centerOf(stabilizer));
        scene.idle(60);

        // 把尾翼转 90°，让它立起来
        scene.world().setBlock(stabilizer,
                CWKineticsIndex.WING.getDefaultState().rotate(Rotation.CLOCKWISE_90), true);
        scene.idle(10);

        ClockworkPonderScenes.text(scene, 7, 60, util.vector().centerOf(stabilizer));
        scene.idle(80);

        scene.addInstruction(new RotateSceneInstruction(-90.0F, 180.0F, false));
        scene.idle(35);
        scene.addKeyframe();

        // 从上方看立起来的尾翼就是一个 20° 攻角的平行四边形
        ClockworkPonderScenes.chaseParallelogram(scene, PonderPalette.GREEN,
                new Vec3(5.0, 4.0, 3.6), 1.0, 0.4, Math.toRadians(-20.0), Direction.DOWN, 60 + 10 + 60 + 10);

        ClockworkPonderScenes.text(scene, 8, 60, util.vector().centerOf(stabilizer));
        scene.idle(70);
        ClockworkPonderScenes.text(scene, 9, 60, util.vector().centerOf(stabilizer));
        scene.idle(70);

        // 地面往后滑：飞机在前进
        ClockworkPonderScenes.animateAsShip(scene, floor, 80,
                new Vec3(0.4, 0.0, 0.0), new Vec3(0.0, -0.5, 0.0));
        scene.idle(80);

        scene.world().hideIndependentSection(floor, Direction.DOWN);
        floor = scene.world().showIndependentSection(util.select().layer(0), Direction.UP);
        scene.addInstruction(new RotateSceneInstruction(-35.0F, 55.0F + 90.0F, false));
        // 换回襟翼：垂直尾翼用 flap 才不会被弯度带着转向
        scene.world().setBlock(stabilizer,
                CWKineticsIndex.FLAP.getDefaultState().rotate(Rotation.CLOCKWISE_90), true);
        scene.idle(40);

        scene.markAsFinished();
    }
}
