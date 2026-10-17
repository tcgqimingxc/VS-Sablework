package org.valkyrienskies.clockwork.integration.ponder.scenes;

import com.simibubi.create.content.redstone.analogLever.AnalogLeverBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.RedStoneWireBlock;
import org.valkyrienskies.clockwork.integration.ponder.ClockworkPonderScenes;

/**
 * 红石电阻（{@code resistor}）的 Ponder 场景。
 *
 * <p>对应官方 {@code ponder/resistor.nbt} + 旧版 {@code content/ponders/KineticPonders.kt#redstoneResistor}。
 *
 * <p>移植差异：
 * <ul>
 *   <li>旧版把转速文本直接写成英文硬编码（"16 RPM" 等），但官方 lang 里已有
 *       {@code vs_clockwork.ponder.resistor.text_1..5} 且内容一字不差；
 *       这里改用 {@link ClockworkPonderScenes#text} 读 lang，中英文都能正常显示。</li>
 *   <li>{@code AnalogLeverBlockEntity} 的 NBT 键 {@code State} 已在 Create 6.0.10 的
 *       {@code write()} 里核对过，与旧版写法一致；红石线用 {@link RedStoneWireBlock#POWER}。</li>
 * </ul>
 *
 * <p>文本键：{@code vs_clockwork.ponder.resistor.header / .text_1..5}。
 */
public final class ResistorScenes {

    private ResistorScenes() {
    }

    public static void resistor(final SceneBuilder builder, final SceneBuildingUtil util) {
        final CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("resistor", "Using the Redstone Resistor");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.setSceneOffsetY(-1.0F);
        scene.idle(15);

        final Selection lever = util.select().position(2, 1, 1);
        final Selection resistor = util.select().position(2, 1, 3);
        final Selection redstone = util.select().position(2, 1, 2);
        final Selection output = util.select().fromTo(0, 1, 3, 1, 1, 3);
        final Selection input = util.select().fromTo(3, 1, 3, 4, 1, 3);

        // 先把动力链摆出来：右侧输入 → 电阻 → 左侧输出
        scene.world().showSection(input, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(resistor, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(output, Direction.DOWN);
        scene.idle(45);

        ClockworkPonderScenes.text(scene, 1, 50,
                        util.vector().blockSurface(util.grid().at(4, 1, 3), Direction.WEST))
                .attachKeyFrame().placeNearTarget();
        scene.idle(60);

        scene.world().showSection(lever, Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(redstone, Direction.DOWN);
        scene.idle(15);

        // 拉动模拟杆 —— 信号强度直接决定输出转速
        scene.overlay().showControls(
                        util.vector().centerOf(2, 1, 1).add(0.0, -0.25, 0.0), Pointing.DOWN, 40)
                .rightClick();
        scene.idle(45);

        setLeverAndWire(scene, util, lever, 5);
        // 5/15 的输入 → 16 * 2/3 ≈ 10.67 RPM
        scene.world().setKineticSpeed(output, -10.67F);
        ClockworkPonderScenes.text(scene, 2, 50,
                        util.vector().blockSurface(util.grid().at(2, 1, 2), Direction.WEST))
                .attachKeyFrame().placeNearTarget();
        scene.idle(60);

        ClockworkPonderScenes.text(scene, 3, 50,
                        util.vector().blockSurface(util.grid().at(0, 1, 3), Direction.WEST))
                .attachKeyFrame().placeNearTarget();
        scene.idle(60);

        setLeverAndWire(scene, util, lever, 10);
        // 10/15 → 16 * 1/3 ≈ 5.33 RPM
        scene.world().setKineticSpeed(output, -5.33F);
        ClockworkPonderScenes.text(scene, 4, 50,
                        util.vector().blockSurface(util.grid().at(0, 1, 3), Direction.WEST))
                .attachKeyFrame().placeNearTarget();
        scene.idle(45);

        setLeverAndWire(scene, util, lever, 15);
        // 满信号 → 完全停转
        scene.world().setKineticSpeed(output, 0.0F);
        ClockworkPonderScenes.text(scene, 5, 50,
                        util.vector().blockSurface(util.grid().at(0, 1, 3), Direction.WEST))
                .attachKeyFrame().placeNearTarget();
        scene.idle(45);

        scene.idle(37 * 4);
        scene.markAsFinished();
    }

    /** 把模拟杆拨到 {@code strength}，同时把旁边红石线的 POWER 改成同一个值（旧版的两步操作）。 */
    private static void setLeverAndWire(final CreateSceneBuilder scene, final SceneBuildingUtil util,
                                        final Selection lever, final int strength) {
        scene.world().modifyBlockEntityNBT(lever, AnalogLeverBlockEntity.class,
                nbt -> nbt.putInt("State", strength));
        scene.world().modifyBlock(util.grid().at(2, 1, 2),
                state -> state.setValue(RedStoneWireBlock.POWER, strength), false);
        scene.effects().indicateRedstone(util.grid().at(2, 1, 2));
    }
}
