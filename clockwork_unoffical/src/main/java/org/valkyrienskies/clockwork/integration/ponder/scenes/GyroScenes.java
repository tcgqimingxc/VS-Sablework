package org.valkyrienskies.clockwork.integration.ponder.scenes;

import com.simibubi.create.content.redstone.analogLever.AnalogLeverBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.clockwork.integration.ponder.ClockworkPonderScenes;

/**
 * 陀螺仪（{@code gyro}）的 Ponder 场景。
 *
 * <p>对应官方 {@code ponder/gyro.nbt} + 旧版 {@code content/ponders/PhysicsPonders.kt#gyro}。
 *
 * <p>移植差异：
 * <ul>
 *   <li>旧版靠 VS2 的 {@code ShipControl} 施加稳定力矩；本移植的陀螺仪走 Sable 的反作用轮
 *       （{@code BlockSubLevelReactionWheel}）。对外表现一致："四个方向的模拟杆给输入 → 结构被稳住"，
 *       所以四条杆的演示顺序原样保留。</li>
 *   <li>{@code moveSectionAsShip} 换 {@link ClockworkPonderScenes#animateAsShip}（同语义），
 *       旋转中心仍用 {@code configureCenterOfRotation(contraption, (2,3,2))}。</li>
 *   <li>旧版开头那句 0 tick、位移为 0 的 {@code moveSection} 是空操作，已删除。</li>
 * </ul>
 *
 * <p>文本键：{@code vs_clockwork.ponder.gyro.header / .text_1..2}（这两条不指向具体方块）。
 */
public final class GyroScenes {

    private GyroScenes() {
    }

    public static void gyro(final SceneBuilder builder, final SceneBuildingUtil util) {
        final CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("gyro", "Stabilize your ship");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.setSceneOffsetY(-1.0F);
        scene.idle(15);

        final BlockPos leverNorth = util.grid().at(2, 2, 3);
        final BlockPos leverEast = util.grid().at(3, 2, 2);
        final BlockPos leverWest = util.grid().at(1, 2, 2);

        final ElementLink<WorldSectionElement> contraption = scene.world()
                .showIndependentSection(util.select().fromTo(0, 1, 0, 4, 3, 4), Direction.DOWN);

        scene.overlay().showText(40).text(ClockworkPonderScenes.text(scene, 1)).attachKeyFrame();
        scene.idle(60);
        scene.overlay().showText(40).text(ClockworkPonderScenes.text(scene, 2)).attachKeyFrame();
        scene.idle(60);

        scene.world().configureCenterOfRotation(contraption, new Vec3(2.0, 3.0, 2.0));
        ClockworkPonderScenes.animateAsShip(scene, contraption, 20, new Vec3(0.0, 0.1, 0.0), Vec3.ZERO);
        scene.idle(25);

        // 北侧杆：先把结构往一边推，再反推回来
        setLever(scene, util, leverNorth, 5);
        ClockworkPonderScenes.animateAsShip(scene, contraption, 30, Vec3.ZERO, new Vec3(-2.5, 0.0, 0.0));
        scene.idle(40);

        setLever(scene, util, leverNorth, 0);
        ClockworkPonderScenes.animateAsShip(scene, contraption, 30, Vec3.ZERO, new Vec3(2.5, 0.0, 0.0));
        scene.idle(40);

        // 东侧杆：绕另一轴
        setLever(scene, util, leverEast, 5);
        ClockworkPonderScenes.animateAsShip(scene, contraption, 30, Vec3.ZERO, new Vec3(0.0, 0.0, 2.5));
        scene.idle(40);

        setLever(scene, util, leverEast, 0);
        ClockworkPonderScenes.animateAsShip(scene, contraption, 30, Vec3.ZERO, new Vec3(0.0, 0.0, -2.5));
        scene.idle(40);

        // 西侧杆：满信号，转得更快
        setLever(scene, util, leverWest, 10);
        ClockworkPonderScenes.animateAsShip(scene, contraption, 50, Vec3.ZERO, new Vec3(0.0, 0.0, -5.0));
        scene.idle(60);

        setLever(scene, util, leverWest, 0);
        ClockworkPonderScenes.animateAsShip(scene, contraption, 50, Vec3.ZERO, new Vec3(0.0, 0.0, 5.0));
        scene.idle(60);

        scene.idle(20);
        scene.markAsFinished();
    }

    /** 拨动模拟杆（旧版 {@code modifyBlockEntityNBT(..., AnalogLeverBlockEntity, "State")}）。 */
    private static void setLever(final CreateSceneBuilder scene, final SceneBuildingUtil util,
                                 final BlockPos pos, final int state) {
        scene.world().modifyBlockEntityNBT(util.select().position(pos), AnalogLeverBlockEntity.class,
                nbt -> nbt.putInt("State", state));
    }
}
