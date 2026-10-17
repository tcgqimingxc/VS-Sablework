package org.valkyrienskies.clockwork.integration.ponder.scenes;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.clockwork.content.logistics.gas.duct.DuctConnectionType;
import org.valkyrienskies.clockwork.content.logistics.gas.duct.GasDuctBlock;
import org.valkyrienskies.clockwork.integration.ponder.ClockworkPonderScenes;

/**
 * 气体管道（{@code duct}）的 Ponder 场景。
 *
 * <p>对应官方 {@code ponder/duct.nbt} + 旧版 {@code content/ponders/KelvinPonders.kt#duct}。
 *
 * <p>移植差异（<b>只演旧版里仍然成立的玩法</b>）：
 * <ul>
 *   <li>旧版 text_4..8 讲的是"扳手改边类型（One-way / Filtered / Smart）"与"手拧编辑"。
 *       本移植的气体网络<b>没有边类型</b>（连接关系只存在方块状态的
 *       {@link DuctConnectionType#SIDE}/{@code FORCED_OFF}/{@code NONE}/{@code LEAK} 里，
 *       见 {@code GasDuctBlockEntity} 类注释），所以这 5 条文本<b>不上台</b>，
 *       免得演示一个不存在的功能；将来补回边类型时再把它们接上。</li>
 *   <li>其余照旧：扳手开关某一面的连接（text_3）、爆炸→泄漏（text_9）、扳手封堵（text_10）。</li>
 *   <li>旧版用 {@code DuctBlockEntity.DIR_TO_CONNECTION_TYPE} 改边类型，本移植没有这个字段，
 *       因此那几段 {@code modifyBlockEntity} 调用整体删除（不留空转调用）。</li>
 * </ul>
 *
 * <p>文本键：{@code vs_clockwork.ponder.duct.header / .text_1..3, .text_9..10}
 * （{@code .text_4..8} 保留在 lang 里未使用，等边类型移植回来）。
 */
public final class DuctScenes {

    private DuctScenes() {
    }

    public static void gasDuct(final SceneBuilder builder, final SceneBuildingUtil util) {
        final CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("duct", "Managing Gas Networks");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        final BlockPos duct1 = util.grid().at(2, 1, 2);
        final BlockPos duct2 = util.grid().at(2, 1, 3);
        final BlockPos duct3 = util.grid().at(2, 1, 4);
        final Vec3 edge = util.vector().centerOf(duct1).add(0.0, 0.0, 0.5);

        // 三条管 + 一端排气口 + 另一端造气机：整条气体链路一起落下来
        scene.world().showSection(util.select().fromTo(1, 1, 2, 3, 1, 4), Direction.DOWN);
        scene.idle(20);

        ClockworkPonderScenes.text(scene, 1, 60, util.vector().topOf(duct2))
                .attachKeyFrame().placeNearTarget();
        scene.idle(70);

        ClockworkPonderScenes.text(scene, 2, 80, util.vector().topOf(duct2)).placeNearTarget();
        scene.idle(90);

        // 扳手点接缝 → 这一面断开；再点一次 → 接回来
        scene.overlay().showControls(edge, Pointing.DOWN, 6)
                .rightClick().withItem(AllItems.WRENCH.asStack());
        scene.idle(10);

        ClockworkPonderScenes.text(scene, 3, 70, edge).attachKeyFrame().placeNearTarget();
        setConnection(scene, duct1, Direction.SOUTH, DuctConnectionType.FORCED_OFF);
        setConnection(scene, duct2, Direction.NORTH, DuctConnectionType.FORCED_OFF);
        scene.idle(40);
        setConnection(scene, duct1, Direction.SOUTH, DuctConnectionType.SIDE);
        setConnection(scene, duct2, Direction.NORTH, DuctConnectionType.SIDE);
        scene.idle(40);

        // 泄漏：爆炸把最外一段打成 LEAK，气体从那一面喷出去；扳手一敲就封好
        setConnection(scene, duct3, Direction.UP, DuctConnectionType.LEAK);
        ClockworkPonderScenes.text(scene, 9, 70, util.vector().topOf(duct3)).attachKeyFrame();
        scene.idle(80);

        scene.overlay().showControls(util.vector().topOf(duct3), Pointing.DOWN, 60)
                .rightClick().withItem(AllItems.WRENCH.asStack());
        scene.idle(10);

        ClockworkPonderScenes.text(scene, 10, 60, util.vector().topOf(duct3)).placeNearTarget();
        scene.idle(40);

        setConnection(scene, duct3, Direction.UP, DuctConnectionType.NONE);
        scene.idle(70);

        scene.markAsFinished();
    }

    /** 改某根管子某一面的连接状态（旧版也是直接改方块状态，不走方块实体）。 */
    private static void setConnection(final CreateSceneBuilder scene, final BlockPos pos, final Direction side,
                                      final DuctConnectionType type) {
        scene.world().modifyBlock(pos,
                state -> state.setValue(GasDuctBlock.DIR_TO_CONNECTION.get(side), type), true);
    }
}
