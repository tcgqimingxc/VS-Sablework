package org.valkyrienskies.clockwork.integration.ponder.scenes;

import com.simibubi.create.content.redstone.nixieTube.NixieTubeBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.clockwork.integration.ponder.ClockworkPonderScenes;

/**
 * 高度计（{@code alt_meter}）的 Ponder 场景。
 *
 * <p>对应官方 {@code ponder/alt_meter.nbt} + 旧版 {@code content/ponders/PhysicsPonders.kt#altMeter}。
 *
 * <p>移植差异：
 * <ul>
 *   <li>旧版用 VS2 的 {@code moveSectionAsShip} 让"飞船"带着高度计升降；
 *       本移植用 {@link ClockworkPonderScenes#animateAsShip}（同一套惯性语义，纯 Ponder 指令实现）。</li>
 *   <li>旧版用 {@code sparklingPlane} 发射自绘粉尘粒子画出"目标高度平面"；
 *       本移植换 {@link ClockworkPonderScenes#chasePlane}（薄盒描边），平面照样可见、不依赖粒子。</li>
 *   <li>旧版把飞船"瞬移回原位"用了 0 tick 的 {@code moveSection}；1.21.1 里 0 tick 会做
 *       {@code delta / 0} 的除法，所以这里用 1 tick（视觉上仍是瞬移）。</li>
 *   <li>Nixie 管的 NBT 键 {@code RedstoneStrength} 已对着 Create 6.0.10 的
 *       {@code NixieTubeBlockEntity#write} 字节码核对，与旧版一致。</li>
 * </ul>
 *
 * <p>文本键：{@code vs_clockwork.ponder.alt_meter.header / .text_1..11}（11 条全部用到）。
 */
public final class AltMeterScenes {

    private AltMeterScenes() {
    }

    public static void altMeter(final SceneBuilder builder, final SceneBuildingUtil util) {
        final CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("alt_meter", "Altitude Meter");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.setSceneOffsetY(-1.0F);
        scene.idle(15);

        final Selection ship = util.select().fromTo(1, 1, 2, 3, 2, 2);
        final Selection lamp = util.select().position(3, 2, 2);
        final Selection nixie = util.select().position(1, 2, 2);
        final BlockPos meter = util.grid().at(2, 2, 2);
        final Vec3 south = util.vector().blockSurface(meter, Direction.SOUTH);

        // ---------- 1. 目标高度 ----------
        ElementLink<WorldSectionElement> contraption =
                scene.world().showIndependentSection(ship, Direction.DOWN);
        scene.idle(15);

        ClockworkPonderScenes.text(scene, 1, 60, south).placeNearTarget();
        scene.idle(80);

        // 飞船带着高度计一起上升：红石灯锁存 + Nixie 显示"到目标高度了"
        ClockworkPonderScenes.animateAsShip(scene, contraption, 20, new Vec3(0.0, 0.2, 0.0), Vec3.ZERO);
        scene.idle(10);
        scene.world().toggleRedstonePower(lamp);
        setNixie(scene, nixie, 15);
        scene.idle(20);

        ClockworkPonderScenes.chasePlane(scene, PonderPalette.GREEN,
                new AABB(-1.0, 4.0, -1.0, 5.0, 4.0, 5.0), Direction.UP, 60);
        ClockworkPonderScenes.text(scene, 2, 60, south).placeNearTarget();
        scene.idle(80);

        ClockworkPonderScenes.animateAsShip(scene, contraption, 20, new Vec3(0.0, 0.2, 0.0), Vec3.ZERO);
        scene.idle(10);
        ClockworkPonderScenes.chasePlane(scene, PonderPalette.GREEN,
                new AABB(-1.0, 4.0, -1.0, 5.0, 8.0, 5.0), Direction.UP, 60);
        ClockworkPonderScenes.text(scene, 3, 60, south).placeNearTarget();
        scene.idle(70);

        scene.world().hideIndependentSection(contraption, Direction.DOWN);
        scene.idle(20);
        scene.world().toggleRedstonePower(lamp);
        setNixie(scene, nixie, 0);

        // ---------- 2. 灵敏度 ----------
        scene.addKeyframe();
        contraption = scene.world().showIndependentSection(ship, Direction.DOWN);
        scene.idle(10);

        ClockworkPonderScenes.text(scene, 4, 120, south).placeNearTarget();
        scene.idle(140);

        ClockworkPonderScenes.animateAsShip(scene, contraption, 20, new Vec3(0.0, 0.1, 0.0), Vec3.ZERO);
        scene.idle(10);
        scene.world().toggleRedstonePower(lamp);
        setNixie(scene, nixie, 7);
        scene.idle(20);

        // 灵敏度 = 提前多少格开始给信号（旧版这块平面用 DOWN 压到顶面）
        ClockworkPonderScenes.chasePlane(scene, PonderPalette.GREEN,
                new AABB(0.0, 0.0, 0.0, 5.0, 4.0, 5.0), Direction.DOWN, 90 + 20 + 10 + 30);
        ClockworkPonderScenes.text(scene, 5, 90, south).placeNearTarget();
        scene.idle(110);

        ClockworkPonderScenes.animateAsShip(scene, contraption, 20, new Vec3(0.0, 0.1, 0.0), Vec3.ZERO);
        scene.idle(10);
        setNixie(scene, nixie, 15);
        scene.idle(20);

        scene.world().hideIndependentSection(contraption, Direction.DOWN);
        scene.idle(20);
        scene.world().toggleRedstonePower(lamp);
        setNixie(scene, nixie, 0);

        // ---------- 3. 方向设置 ----------
        scene.addKeyframe();
        contraption = scene.world().showIndependentSection(ship, Direction.DOWN);
        scene.idle(10);

        ClockworkPonderScenes.text(scene, 6, 90, south).placeNearTarget();
        scene.idle(110);
        ClockworkPonderScenes.text(scene, 7, 80, south).placeNearTarget();
        scene.idle(100);

        // ---------- 4. UP：只在下方向上接近目标时输出 ----------
        scene.addKeyframe();
        ClockworkPonderScenes.text(scene, 8, 90, south).placeNearTarget();
        scene.idle(110);

        ClockworkPonderScenes.chasePlane(scene, PonderPalette.GREEN,
                new AABB(0.0, 0.0, 0.0, 5.0, 4.0, 5.0), Direction.DOWN, 80);
        ClockworkPonderScenes.animateAsShip(scene, contraption, 20, new Vec3(0.0, 0.1, 0.0), Vec3.ZERO);
        scene.idle(10);
        scene.world().toggleRedstonePower(lamp);
        setNixie(scene, nixie, 7);
        scene.idle(20);

        ClockworkPonderScenes.animateAsShip(scene, contraption, 20, new Vec3(0.0, 0.1, 0.0), Vec3.ZERO);
        scene.idle(10);
        setNixie(scene, nixie, 15);
        scene.idle(20);

        scene.world().hideIndependentSection(contraption, Direction.UP);
        scene.idle(20);
        scene.world().toggleRedstonePower(lamp);
        setNixie(scene, nixie, 0);

        // ---------- 5. DOWN：只在上方向下接近目标时输出 ----------
        scene.addKeyframe();
        contraption = scene.world().showIndependentSectionImmediately(ship);
        // 先瞬移回原位（旧版 0 tick，这里 1 tick），再动画上升，方便看清"从上方接近"
        scene.world().moveSection(contraption, new Vec3(0.0, 3.0, 0.0), 1);
        scene.world().moveSection(contraption, new Vec3(0.0, 1.0, 0.0), 10);

        ClockworkPonderScenes.text(scene, 9, 80,
                        util.vector().blockSurface(util.grid().at(2, 6, 2), Direction.SOUTH))
                .placeNearTarget();
        scene.idle(100);

        ClockworkPonderScenes.chasePlane(scene, PonderPalette.GREEN,
                new AABB(0.0, 3.0, 0.0, 5.0, 6.0, 5.0), Direction.UP, 80);
        ClockworkPonderScenes.animateAsShip(scene, contraption, 20, new Vec3(0.0, -0.1, 0.0), Vec3.ZERO);
        scene.idle(10);
        scene.world().toggleRedstonePower(lamp);
        setNixie(scene, nixie, 7);
        scene.idle(20);

        ClockworkPonderScenes.animateAsShip(scene, contraption, 20, new Vec3(0.0, -0.1, 0.0), Vec3.ZERO);
        scene.idle(10);
        setNixie(scene, nixie, 15);
        scene.idle(20);

        scene.world().hideIndependentSection(contraption, Direction.DOWN);
        scene.idle(20);
        scene.world().toggleRedstonePower(lamp);
        setNixie(scene, nixie, 0);

        // ---------- 6. BOTH：上下都输出，正好在目标高度时满信号 ----------
        scene.addKeyframe();
        contraption = scene.world().showIndependentSection(ship, Direction.DOWN);
        scene.idle(10);

        ClockworkPonderScenes.text(scene, 10, 80, south).placeNearTarget();
        scene.idle(100);

        ClockworkPonderScenes.chasePlane(scene, PonderPalette.GREEN,
                new AABB(-1.0, 4.0, -1.0, 5.0, 8.0, 5.0), Direction.UP, 300);
        ClockworkPonderScenes.chasePlane(scene, PonderPalette.GREEN,
                new AABB(-1.0, 0.0, -1.0, 5.0, 4.0, 5.0), Direction.DOWN, 300);

        ClockworkPonderScenes.animateAsShip(scene, contraption, 20, new Vec3(0.0, 0.1, 0.0), Vec3.ZERO);
        scene.idle(10);
        scene.world().toggleRedstonePower(lamp);
        setNixie(scene, nixie, 7);
        scene.idle(20);

        ClockworkPonderScenes.animateAsShip(scene, contraption, 20, new Vec3(0.0, 0.1, 0.0), Vec3.ZERO);
        scene.idle(10);
        setNixie(scene, nixie, 15);
        scene.idle(20);

        ClockworkPonderScenes.text(scene, 11, 80, south).placeNearTarget();
        scene.idle(100);

        ClockworkPonderScenes.animateAsShip(scene, contraption, 20, new Vec3(0.0, 0.1, 0.0), Vec3.ZERO);
        scene.idle(10);
        setNixie(scene, nixie, 7);
        scene.idle(20);

        ClockworkPonderScenes.animateAsShip(scene, contraption, 20, new Vec3(0.0, 0.1, 0.0), Vec3.ZERO);
        scene.idle(10);
        scene.world().toggleRedstonePower(lamp);
        setNixie(scene, nixie, 0);
        scene.idle(20);

        scene.markAsFinished();
    }

    /** 直接把 Nixie 管显示的红石强度写进方块实体 NBT（旧版同款写法）。 */
    private static void setNixie(final CreateSceneBuilder scene, final Selection nixie, final int strength) {
        scene.world().modifyBlockEntityNBT(nixie, NixieTubeBlockEntity.class,
                nbt -> nbt.putInt("RedstoneStrength", strength));
    }
}
