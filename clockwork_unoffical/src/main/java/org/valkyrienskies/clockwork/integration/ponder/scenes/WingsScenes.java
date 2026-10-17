package org.valkyrienskies.clockwork.integration.ponder.scenes;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import org.valkyrienskies.clockwork.content.kinetics.CWKineticsIndex;
import org.valkyrienskies.clockwork.integration.ponder.ClockworkPonderScenes;

/**
 * 机翼基础（{@code wings}）的 Ponder 场景。
 *
 * <p>对应官方 {@code ponder/wings.nbt} + 旧版 {@code content/ponders/PhysicsPonders.kt#wings}。
 *
 * <p>移植差异：
 * <ul>
 *   <li>旧版讲的是 VS2 的气动模型（每个方块的形状都算升力/阻力）；本移植的升力由 Sable 的
 *       lift provider 提供（见 {@code WingBlock}），<b>概念相同</b>，因此文本与演示顺序照旧。</li>
 *   <li>演示手法与旧版一致：先把机翼位换成橡木台阶（"普通方块也能产生一点升力"），
 *       再换回 {@code vs_clockwork:flap} 并用 {@code restoreBlocks} 让连接纹理正确重画。</li>
 * </ul>
 *
 * <p>文本键：{@code vs_clockwork.ponder.wings.header / .text_1..6}。
 */
public final class WingsScenes {

    private WingsScenes() {
    }

    public static void wings(final SceneBuilder builder, final SceneBuildingUtil util) {
        final CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("wings", "Wings");
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);

        final Selection horizontalFlaps = horizontalWings(util);
        final Selection entirePlane = util.select().layer(2).add(util.select().layer(3));

        // 先全部当成"普通台阶"：说明形状本身就有升力
        scene.world().replaceBlocks(horizontalFlaps, Blocks.OAK_SLAB.defaultBlockState(), false);
        scene.world().showSection(entirePlane, Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(60).text(ClockworkPonderScenes.text(scene, 1));
        scene.idle(70);
        scene.overlay().showText(60).text(ClockworkPonderScenes.text(scene, 2)).attachKeyFrame();
        scene.idle(80);

        final BlockPos wingTip = util.grid().at(1, 2, 2);
        ClockworkPonderScenes.text(scene, 3, 60, util.vector().centerOf(wingTip));
        scene.idle(70);
        ClockworkPonderScenes.text(scene, 4, 60, util.vector().centerOf(wingTip));
        scene.idle(80);

        // 换成机翼方块：粒子（true）让"升力更强"有个视觉提示，再用 restoreBlocks 重画连接纹理
        scene.world().replaceBlocks(horizontalFlaps, CWKineticsIndex.FLAP.getDefaultState(), true);
        scene.world().restoreBlocks(horizontalFlaps);
        scene.idle(10);

        scene.addKeyframe();
        ClockworkPonderScenes.text(scene, 5, 60, util.vector().centerOf(wingTip));
        scene.idle(80);
        ClockworkPonderScenes.text(scene, 6, 60, util.vector().centerOf(wingTip));
        scene.idle(80);

        scene.markAsFinished();
    }

    /**
     * 选中整副机翼（左右主翼 + 尾翼，扣掉机身与切角）——
     * 与旧版 {@code PhysicsPonders.kt#wings} 里的 {@code horizontalFlaps} 完全一致。
     */
    static Selection horizontalWings(final SceneBuildingUtil util) {
        return util.select().fromTo(1, 2, 1, 3, 2, 5)
                .substract(util.select().position(1, 2, 1))
                .substract(util.select().position(1, 2, 5))
                .add(util.select().fromTo(5, 2, 2, 5, 2, 4))
                .substract(util.select().fromTo(1, 2, 3, 5, 2, 3));
    }
}
