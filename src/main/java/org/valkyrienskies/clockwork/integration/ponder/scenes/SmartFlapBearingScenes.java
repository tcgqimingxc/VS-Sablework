package org.valkyrienskies.clockwork.integration.ponder.scenes;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.clockwork.integration.ponder.ClockworkPonderScenes;

/**
 * 智能摆转轴承（{@code smart_flap_bearing}）的 Ponder 场景。
 *
 * <p>对应官方 {@code ponder/smart_flap_bearing.nbt} + 旧版
 * {@code content/ponders/PhysicsPonders.kt#smart_flap}。
 *
 * <p>移植差异：无实质差异 —— 场景只用 Create 的 Filter 槽提示（{@code showFilterSlotInput}）
 * 说明"内置红石链接槽"，不依赖本移植未实现的东西。与 {@code flap_bearing} 场景共用同一套
 * 结构布局（官方两个 nbt 也只有轴承方块不同）。
 *
 * <p>文本键：{@code vs_clockwork.ponder.smart_flap_bearing.header / .text_1..3}。
 */
public final class SmartFlapBearingScenes {

    private SmartFlapBearingScenes() {
    }

    public static void smartFlapBearing(final SceneBuilder builder, final SceneBuildingUtil util) {
        final CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("smart_flap_bearing", "Smart flap bearing");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.setSceneOffsetY(-1.0F);
        scene.idle(15);

        final BlockPos bearing = util.grid().at(2, 1, 2);
        final Vec3 west = util.vector().blockSurface(bearing, Direction.WEST);

        final Selection flap = util.select().position(2, 1, 1);
        final Selection shaft = util.select().fromTo(2, 1, 2, 2, 1, 5);
        final Selection bottomCog = util.select().position(2, 0, 5);
        final Selection left = util.select().fromTo(0, 1, 2, 1, 1, 2);
        final Selection right = util.select().fromTo(4, 1, 2, 3, 1, 2);

        // 被摆出来的那片做成独立结构段（本场景只演示槽位，不需要再动它）
        scene.world().showIndependentSection(flap, Direction.DOWN);
        scene.world().showSection(
                util.select().layersFrom(1).substract(left).substract(right).substract(flap), Direction.DOWN);
        scene.world().showSection(shaft, Direction.DOWN);
        scene.world().showSection(bottomCog, Direction.DOWN);
        scene.idle(20);

        scene.world().setKineticSpeed(shaft, 16.0F);
        scene.world().setKineticSpeed(bottomCog, -16.0F);

        ClockworkPonderScenes.text(scene, 1, 60, west).placeNearTarget();
        scene.idle(80);

        ClockworkPonderScenes.text(scene, 2, 60, west).attachKeyFrame().placeNearTarget();

        // 两个内置频率槽：位置与旧版一样，从朝西的面往上下各偏 0.2、前后偏 0.1
        final Vec3 slot1 = west.add(0.0, 0.2, 0.1);
        final Vec3 slot2 = west.add(0.0, -0.2, 0.1);
        scene.overlay().showFilterSlotInput(slot1, Direction.WEST, 60);
        scene.overlay().showFilterSlotInput(slot2, Direction.WEST, 60);
        scene.idle(80);

        ClockworkPonderScenes.text(scene, 3, 60, west).placeNearTarget();
        scene.idle(80);

        scene.markAsFinished();
    }
}
