package org.valkyrienskies.clockwork.integration.ponder.scenes;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.clockwork.content.curiosities.tools.CWToolsIndex;
import org.valkyrienskies.clockwork.content.logistics.gas.duct.DuctConnectionType;
import org.valkyrienskies.clockwork.content.logistics.gas.duct.DuctEdgeType;
import org.valkyrienskies.clockwork.content.logistics.gas.duct.GasDuctBlock;
import org.valkyrienskies.clockwork.content.logistics.gas.duct.GasDuctBlockEntity;
import org.valkyrienskies.clockwork.integration.ponder.ClockworkPonderScenes;

/**
 * 气体管道（{@code duct}）的 Ponder 场景。
 *
 * <p>对应官方 {@code ponder/duct.nbt} + 旧版 {@code content/ponders/KelvinPonders.kt#duct}。
 *
 * <p>旧版 text_4..8 演的是"螺丝刀循环切换某个面的边类型（One-way / Filtered / Smart）"与
 * "螺丝刀编辑该边"——本移植<b>回到了同一套机制</b>（{@code GasDuctBlockEntity} 保存面级边类型，
 * 见 {@code docs/DUCT-EDGE-VARIANTS.md}），所以：
 * <ul>
 *   <li>text_4..7 基本照旧版原文，只是 text_4 去掉了 "Shift"：本移植普通右键切类型、
 *       潜行右键调参数（旧版是反过来：普通右键开 GUI、潜行切类型）；</li>
 *   <li>text_8 讲"潜行右键编辑该边"，对应本移植的三个参数旋钮（见 {@code DuctEdgeInteraction}）。</li>
 * </ul>
 *
 * <p>为什么用 {@code modifyBlockEntity} 而不是 {@code setBlock}：边类型<b>不在方块状态里</b>
 * （6 个面 × 5 种类型做成状态会让变体数爆炸，见 {@code GasDuctBlockEntity} 类头），
 * 所以 Ponder 也必须改方块实体 —— 旧版 {@code KelvinPonders.kt} 同样是
 * {@code modifyBlockEntity(duct1, DuctBlockEntity::class){ be.DIR_TO_CONNECTION_TYPE[...] = ... }}。
 *
 * <p>其余照旧：扳手开关某一面的连接（text_3）、爆炸→泄漏（text_9）、扳手封堵（text_10）。
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
        // duct1 与 duct2 之间的接缝：边类型就画在这里
        final Vec3 edge = util.vector().centerOf(duct1).add(0.0, 0.0, 0.5);

        // 三条管一段链路一起落下来
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

        // ===== 边类型：螺丝刀点 duct1 的南面（对着 duct2 的那个接缝）=====
        ClockworkPonderScenes.text(scene, 4, 90, edge).attachKeyFrame().placeNearTarget();
        scene.overlay().showControls(edge, Pointing.DOWN, 10)
                .rightClick().withItem(CWToolsIndex.SCREWDRIVER.asStack());
        scene.idle(20);

        // 单向：气体只能从 duct1 经这一面流向 duct2（接缝上出现单向法兰）
        setEdge(scene, duct1, Direction.SOUTH, DuctEdgeType.ONEWAY_FORWARD);
        ClockworkPonderScenes.text(scene, 5, 80, edge).attachKeyFrame().placeNearTarget();
        scene.idle(70);

        // 过滤：再点一次切到过滤，再用潜行右键选一种气体
        setEdge(scene, duct1, Direction.SOUTH, DuctEdgeType.FILTERED);
        cyclePrimary(scene, duct1, Direction.SOUTH);
        ClockworkPonderScenes.text(scene, 6, 80, edge).attachKeyFrame().placeNearTarget();
        scene.idle(70);

        // 智能：切到智能（外观是旧版的铜接头），主参数默认从 NONE 切到按下差比较
        setEdge(scene, duct1, Direction.SOUTH, DuctEdgeType.SMART);
        cyclePrimary(scene, duct1, Direction.SOUTH);
        ClockworkPonderScenes.text(scene, 7, 80, edge).attachKeyFrame().placeNearTarget();
        scene.idle(70);

        // 潜行右键编辑该边：本移植用它循环主参数，空手右键调另外两级
        scene.overlay().showControls(edge, Pointing.DOWN, 12)
                .whileSneaking().rightClick().withItem(CWToolsIndex.SCREWDRIVER.asStack());
        ClockworkPonderScenes.text(scene, 8, 90, edge).placeNearTarget();
        scene.idle(80);

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

    /** 改某一面的边类型（旧版 {@code modifyBlockEntity(...){ be.DIR_TO_CONNECTION_TYPE[...] = type }}）。 */
    private static void setEdge(final CreateSceneBuilder scene, final BlockPos pos, final Direction side,
                                final DuctEdgeType type) {
        scene.world().modifyBlockEntity(pos, GasDuctBlockEntity.class, be -> be.setEdgeType(side, type));
    }

    /** 循环主参数（过滤 → 选气体；智能 → 切阈值指标），等价于玩家潜行右键一次。 */
    private static void cyclePrimary(final CreateSceneBuilder scene, final BlockPos pos, final Direction side) {
        scene.world().modifyBlockEntity(pos, GasDuctBlockEntity.class, be -> be.cyclePrimary(side));
    }
}
