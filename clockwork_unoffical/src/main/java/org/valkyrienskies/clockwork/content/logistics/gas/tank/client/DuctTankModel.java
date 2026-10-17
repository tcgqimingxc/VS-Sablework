package org.valkyrienskies.clockwork.content.logistics.gas.tank.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.foundation.block.connected.CTModel;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;

import net.createmod.catnip.data.Iterate;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelData.Builder;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/**
 * 气体储罐的烘焙模型包装：在 Create 的 {@link CTModel}（连接纹理）之上，再加"相邻罐段之间不画内部面"。
 *
 * <h2>对应 Create 的哪个文件</h2>
 * {@code com.simibubi.create.content.fluids.tank.FluidTankModel}（Create 1.21.1 全 94 行），
 * 结构逐行对应：
 * <ul>
 *   <li>{@code standard(BakedModel)} → 本类 {@link #of(BakedModel)}，同样喂
 *       {@code (侧壁, 顶底)} 两条 {@code CTSpriteShiftEntry}；</li>
 *   <li>{@code FluidTankCTBehaviour} → {@link DuctTankCTBehaviour}；</li>
 *   <li>私有内部类 {@code CullData} + {@code CULL_PROPERTY} → 本类同名内部类，逻辑一字未改。</li>
 * </ul>
 *
 * <h2>CT 的取格到底怎么发生（为什么不需要在 blockstate/模型里挑格子）</h2>
 * 模型每个面都引用 16×16 的基础图（{@code duct_tank} / {@code duct_tank_top}），uv 是完整的 0..16。
 * 真正"挑哪一格"发生在 {@code CTModel#getQuads}：
 * <ol>
 *   <li>{@code CTModel#createCTData} 为每个面算出 {@code index}（{@code AllCTTypes.RECTANGLE}
 *       按上下左右四个邻居给 0..15）；</li>
 *   <li>{@code getQuads} 里对每个面调 {@code spriteShift.getTargetU(u, index)} /
 *       {@code getTargetV(v, index)} 重写 uv。</li>
 * </ol>
 * {@code CTSpriteShiftEntry} 的公式（已 javap {@code SpriteShiftEntry} 核实）：
 * <pre>
 *   相对位置 = (uv - 基础图.u0) / (基础图.u1 - 基础图.u0)        // 0..1，基础图内
 *   新uv     = 图集.getU((相对位置 + 列号) / 4)                  // 图集 4×4 格
 * </pre>
 * 因为图集是 64×64、基础图是 16×16，图集的 uv 跨度正好是基础图的 4 倍，所以
 * {@code (相对位置 + 列号)/4} 恰好落在"第 (列,行) 格"的同一相对位置上。于是每格 16 像素仍铺满
 * 一个 16 单位的面 = 1 px/单位，与多层模型一致，成型/拆分时贴图不跳变。
 *
 * <h2>为什么要剔除水平面（CullData）</h2>
 * 罐壁是 1 像素厚的薄片、而且贴在方块边界上，于是相邻两段的壁在交界处**背靠背共面**
 * （A 的东外面在 {@code x=1.0} 朝东，B 的西外面也在 {@code x=1.0} 朝西）。
 * 这两张面在密闭罐里永远看不见（各自只朝自己那一侧，而那一侧被对方挡死），
 * 但它们确实会被提交：3×3 的罐里，这种"埋在墙里"的侧壁面正好占全部侧壁面的一半。
 * Create 的储罐也是整批丢掉（{@code FluidTankModel} 的 {@code CullData} + {@code CULL_PROPERTY}），
 * 本类照抄：只要该方向上 {@code ConnectivityHandler.isConnected} 为真，就跳过那一侧的所有面，
 * 只留最外圈那一层。顺带消掉一个隐患 —— 两张共面且深度相等的面一旦同时被光栅化
 * （同一份模型被重复绘制的场合，例如被移动装置再画一次），交界处会出现闪烁。
 * 竖直方向不剔除：上下两端是盖子，本来就只有最上一段/最下一段才有。
 */
public class DuctTankModel extends CTModel {

    /** 四面水平邻居是否与自己同属一个多方块 —— 缓存进 {@code ModelData}，避免每个面都去查方块实体。 */
    protected static final ModelProperty<CullData> CULL_PROPERTY = new ModelProperty<>();

    /** 由 {@code CreateRegistrate.blockModel(() -> DuctTankModel::of)} 调用（见 {@code CWGasIndex.DUCT_TANK}）。 */
    public static DuctTankModel of(BakedModel originalModel) {
        return new DuctTankModel(originalModel,
                new DuctTankCTBehaviour(DuctTankSpriteShifts.DUCT_TANK, DuctTankSpriteShifts.DUCT_TANK_TOP));
    }

    private DuctTankModel(BakedModel originalModel, ConnectedTextureBehaviour behaviour) {
        super(originalModel, behaviour);
    }

    @Override
    protected ModelData.Builder gatherModelData(Builder builder, BlockAndTintGetter world, BlockPos pos, BlockState state,
                                                ModelData blockEntityData) {
        super.gatherModelData(builder, world, pos, state, blockEntityData);
        CullData cullData = new CullData();
        for (Direction d : Iterate.horizontalDirections) {
            cullData.setCulled(d, ConnectivityHandler.isConnected(world, pos, pos.relative(d)));
        }
        return builder.with(CULL_PROPERTY, cullData);
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData extraData,
                                    RenderType renderType) {
        // 渲染器会分别用"6 个方向 + null"来要面；这里只在 null 那次统一吐出（与 Create 一致），
        // 否则同一张面会被要求两次，剔除逻辑还得各写一遍。
        if (side != null) {
            return Collections.emptyList();
        }

        List<BakedQuad> quads = new ArrayList<>();
        for (Direction d : Iterate.directions) {
            if (extraData.has(CULL_PROPERTY) && extraData.get(CULL_PROPERTY).isCulled(d)) {
                continue;
            }
            quads.addAll(super.getQuads(state, d, rand, extraData, renderType));
        }
        // null = 没有 cullface 的面（例如盖子的内表面），永远不剔除
        quads.addAll(super.getQuads(state, null, rand, extraData, renderType));
        return quads;
    }

    private static final class CullData {
        private final boolean[] culledFaces;

        private CullData() {
            culledFaces = new boolean[4];
            Arrays.fill(culledFaces, false);
        }

        private void setCulled(Direction face, boolean cull) {
            if (face.getAxis().isVertical()) {
                return;
            }
            culledFaces[face.get2DDataValue()] = cull;
        }

        private boolean isCulled(Direction face) {
            if (face.getAxis().isVertical()) {
                return false;
            }
            return culledFaces[face.get2DDataValue()];
        }
    }
}
