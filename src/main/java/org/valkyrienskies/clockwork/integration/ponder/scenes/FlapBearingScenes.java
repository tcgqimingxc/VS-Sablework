package org.valkyrienskies.clockwork.integration.ponder.scenes;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.redstone.analogLever.AnalogLeverBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.clockwork.content.contraptions.flap.FlapBearingBlockEntity;
import org.valkyrienskies.clockwork.integration.ponder.ClockworkPonderScenes;

/**
 * 摆转轴承（{@code flap_bearing}）的 Ponder 场景 —— 安山摆转轴承与智能摆转轴承共用。
 *
 * <p>对应官方 {@code ponder/flap_bearing.nbt} + 旧版 {@code content/ponders/PhysicsPonders.kt#flap}。
 *
 * <p>移植差异：
 * <ul>
 *   <li>旧版 {@code modifyBlockEntityNBT(..., FlapBearingBlockEntity::class, "TargetAngle" = ±22.5f)} 照写 ——
 *       本移植的 {@code AbstractBearingBlockEntity#write/read} 用的就是 {@code TargetAngle}（已核对字节码），
 *       不是某些注释里说的"键名不同"。</li>
 *   <li>被摆出来的那片结构在 Ponder 里是独立结构段，倾斜靠 {@code rotateSection} 表现；
 *       真实世界里这是 Sable 的物理关节（子空间真的被摆动），两者视觉一致。</li>
 *   <li>末段的模拟杆（analog lever）细节与旧版完全一致：先换杆子，再演示 3/15 与 7/15 两个档位。</li>
 * </ul>
 *
 * <p>文本键：{@code vs_clockwork.ponder.flap_bearing.header / .text_1..5}。
 */
public final class FlapBearingScenes {

    private FlapBearingScenes() {
    }

    public static void flapBearing(final SceneBuilder builder, final SceneBuildingUtil util) {
        final CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("flap_bearing", "Flap bearing");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.setSceneOffsetY(-1.0F);
        scene.idle(15);

        final BlockPos bearing = util.grid().at(2, 1, 2);
        final Vec3 bearingWest = util.vector().blockSurface(bearing, Direction.WEST);

        final Selection flap = util.select().position(2, 1, 1);
        final Selection shaft = util.select().fromTo(2, 1, 2, 2, 1, 5);
        final Selection bottomCog = util.select().position(2, 0, 5);
        final Selection left = util.select().fromTo(0, 1, 2, 1, 1, 2);
        final Selection right = util.select().fromTo(4, 1, 2, 3, 1, 2);

        // 被摆出来的那片：独立结构段，之后只靠 rotateSection 动
        final ElementLink<WorldSectionElement> contraption =
                scene.world().showIndependentSection(flap, Direction.DOWN);

        // 其余结构（红石两侧先藏起来，等讲到再出现）
        scene.world().showSection(
                util.select().layersFrom(1).substract(left).substract(right).substract(flap), Direction.DOWN);
        scene.world().showSection(shaft, Direction.DOWN);
        scene.world().showSection(bottomCog, Direction.DOWN);
        scene.idle(20);

        scene.world().setKineticSpeed(shaft, 16.0F);
        scene.world().setKineticSpeed(bottomCog, -16.0F);

        ClockworkPonderScenes.text(scene, 1, 60, bearingWest).placeNearTarget();
        scene.idle(80);
        ClockworkPonderScenes.text(scene, 2, 60, bearingWest).placeNearTarget();
        scene.idle(80);
        ClockworkPonderScenes.text(scene, 3, 60, bearingWest).placeNearTarget();
        scene.idle(80);

        scene.addKeyframe();
        scene.world().showSection(left, Direction.DOWN);
        scene.world().showSection(right, Direction.DOWN);
        scene.idle(15);

        ClockworkPonderScenes.text(scene, 4, 60, bearingWest).placeNearTarget();
        scene.idle(80);

        // 左侧红石 → +22.5°；右侧红石 → -22.5°；都断开 → 回正
        scene.world().toggleRedstonePower(left);
        scene.world().rotateSection(contraption, 0.0, 0.0, 25.0, 6);
        setTargetAngle(scene, util, bearing, 22.5F);
        scene.idle(35);

        scene.world().toggleRedstonePower(left);
        scene.world().toggleRedstonePower(right);
        setTargetAngle(scene, util, bearing, -22.5F);
        scene.world().rotateSection(contraption, 0.0, 0.0, -50.0, 12);
        scene.idle(35);

        scene.world().toggleRedstonePower(right);
        setTargetAngle(scene, util, bearing, 0.0F);
        scene.world().rotateSection(contraption, 0.0, 0.0, 25.0, 6);
        scene.idle(40);

        scene.addKeyframe();
        scene.idle(20);

        // 把两边的拉杆换成模拟杆：角度可以连续调
        scene.world().hideSection(left, Direction.UP);
        scene.world().hideSection(right, Direction.UP);
        scene.idle(20);
        scene.world().setBlock(util.grid().at(4, 1, 2), AllBlocks.ANALOG_LEVER.getDefaultState(), false);
        scene.world().setBlock(util.grid().at(0, 1, 2), AllBlocks.ANALOG_LEVER.getDefaultState(), false);
        scene.world().showSection(left, Direction.DOWN);
        scene.world().showSection(right, Direction.DOWN);
        scene.idle(15);

        final BlockPos lever = util.grid().at(0, 1, 2);
        final Vec3 leverVec = util.vector().centerOf(lever);

        ClockworkPonderScenes.text(scene, 5, 80, leverVec);
        scene.idle(100);

        // 3/15 → 4.5°
        scene.overlay().showControls(leverVec, Pointing.DOWN, 20).rightClick();
        scene.idle(10);
        setLeverState(scene, util, lever, 3);
        scene.idle(10);
        scene.world().modifyBlock(lever.east(),
                state -> state.setValue(RedStoneWireBlock.POWER, 3), false);
        scene.effects().indicateRedstone(lever.east());
        scene.world().rotateSection(contraption, 0.0, 0.0, 4.5, 1);
        setTargetAngle(scene, util, bearing, 4.5F);
        scene.idle(20);

        // 7/15 → 10.5°
        scene.overlay().showControls(leverVec, Pointing.DOWN, 20).rightClick();
        scene.idle(10);
        setLeverState(scene, util, lever, 7);
        scene.idle(10);
        scene.world().modifyBlock(lever.east(),
                state -> state.setValue(RedStoneWireBlock.POWER, 7), false);
        scene.effects().indicateRedstone(lever.east());
        scene.world().rotateSection(contraption, 0.0, 0.0, 10.5, 3);
        setTargetAngle(scene, util, bearing, 10.5F);
        scene.idle(20);

        scene.idle(20);
        scene.markAsFinished();
    }

    /** 改摆转轴承的目标摆角（旧版 {@code modifyBlockEntityNBT(..., "TargetAngle", ...)} 的同款写法）。 */
    private static void setTargetAngle(final CreateSceneBuilder scene, final SceneBuildingUtil util,
                                       final BlockPos bearing, final float degrees) {
        scene.world().modifyBlockEntityNBT(util.select().position(bearing), FlapBearingBlockEntity.class,
                nbt -> nbt.putFloat("TargetAngle", degrees));
    }

    private static void setLeverState(final CreateSceneBuilder scene, final SceneBuildingUtil util,
                                      final BlockPos lever, final int state) {
        scene.world().modifyBlockEntityNBT(util.select().position(lever), AnalogLeverBlockEntity.class,
                nbt -> nbt.putInt("State", state));
    }
}
