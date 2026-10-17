package org.valkyrienskies.clockwork.content.contraptions.phys.infuser;

import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.valkyrienskies.clockwork.content.contraptions.CWContraptionsIndex;

import java.util.Collection;

/**
 * 物理灌注器（旧版 {@code content/contraptions/phys/infuser/PhysicsInfuserBlock}）。
 *
 * <p>玩法：把一块结构"灌注"物理性 —— 也就是装配成 Sable 子空间。
 * 两种用法：
 * <ul>
 *   <li><b>空手右键</b>：装配贴着灌注器的那一块连通结构（再次右键可跳过动画）；</li>
 *   <li><b>手持实现了 {@link IAreaSelector} 的物品右键</b>：按选定区域装配
 *       （对应旧版的游荡权杖选点，自动按连通性拆成多个子空间）。</li>
 * </ul>
 * 潜行空手右键 = 把它创造出来的子空间放回世界。
 *
 * <p>与旧版差异：旧版灌满足迹是一段 500 tick 的多段音效 + 扫描波动画；
 * 这里保留"需要时间 + 可跳过"的节奏，音效用原版音效（本切片的 CWSounds 索引不在负责范围内），
 * 动画交由后续渲染切片处理。
 *
 * <p><b>交互返回值</b>：{@code useItemOn} 成功时返回 {@link ItemInteractionResult#CONSUME} 而不是 SUCCESS。
 * 两者都"消费"这次交互，但 SUCCESS 会让原版在两端各挥手一次，而配套使用的游荡法杖把挥动手臂当作
 * "左键清空选区"（{@code WanderwandItem#onEntitySwing}）—— 返回 SUCCESS 会让玩家一右键灌注器就把选区清空。
 */
public class PhysicsInfuserBlock extends Block implements IBE<PhysicsInfuserBlockEntity> {

    private static final VoxelShape SHAPE = makeShape();

    public PhysicsInfuserBlock(final Properties properties) {
        super(properties);
    }

    @Override
    protected ItemInteractionResult useItemOn(final ItemStack stack, final BlockState state, final Level level,
                                              final BlockPos pos, final Player player, final InteractionHand hand,
                                              final BlockHitResult hit) {
        if (!player.mayBuild()) {
            return ItemInteractionResult.FAIL;
        }
        // 这里一律用 CONSUME 而不是 SUCCESS：两者都"消费"了这次交互（consumesAction() 都为真），
        // 但 SUCCESS 会让原版顺手挥手 —— 客户端 Minecraft#startUseItem:1746、服务端
        // ServerGamePacketListenerImpl#handleUseItemOn:1134 都会按 shouldSwing() 调 player.swing(...)，
        // 而游荡法杖用 IItemExtension#onEntitySwing 实现"左键清空选区"。
        // 灌注器与法杖是配套使用的：返回 SUCCESS 会把玩家刚选好的选区在右键那一刻清掉，所以必须用 CONSUME。
        if (level.isClientSide) {
            return ItemInteractionResult.CONSUME;
        }
        if (player.isShiftKeyDown()) {
            this.withBlockEntityDo(level, pos, PhysicsInfuserBlockEntity::disassembleAll);
            return ItemInteractionResult.CONSUME;
        }
        if (stack.getItem() instanceof final IAreaSelector selector) {
            final Collection<BlockPos> selection = selector.cw$getSelectedBlocks(stack, level);
            if (selection != null && !selection.isEmpty()) {
                this.withBlockEntityDo(level, pos, be -> be.startAssembly(selection));
                return ItemInteractionResult.CONSUME;
            }
            // 手持选区工具但还没选任何东西：以前这里直接落到 PASS_TO_DEFAULT_BLOCK_INTERACTION，
            // 玩家看到的是"右键灌注器毫无反应"。明确提示一句，别让人以为灌注器/法杖坏了。
            player.displayClientMessage(
                    Component.translatable("vs_clockwork.physics_infuser.no_selection"), true);
            return ItemInteractionResult.CONSUME;
        }
        if (stack.isEmpty()) {
            this.withBlockEntityDo(level, pos, be -> be.startAssembly(null));
            return ItemInteractionResult.CONSUME;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos,
                                  final CollisionContext context) {
        return SHAPE;
    }

    @Override
    public Class<PhysicsInfuserBlockEntity> getBlockEntityClass() {
        return PhysicsInfuserBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends PhysicsInfuserBlockEntity> getBlockEntityType() {
        return CWContraptionsIndex.PHYSICS_INFUSER_BE.get();
    }

    /** 旧版外形：中心一个 15x15x15 的核 + 八个角柱（照抄旧版 makeShape）。 */
    private static VoxelShape makeShape() {
        VoxelShape shape = Shapes.empty();
        shape = Shapes.join(shape, box(0.0, 11.0, 0.0, 5.0, 16.0, 5.0), BooleanOp.OR);
        shape = Shapes.join(shape, box(0.5, 0.5, 0.5, 15.5, 15.5, 15.5), BooleanOp.OR);
        shape = Shapes.join(shape, box(0.0, 0.0, 0.0, 5.0, 5.0, 5.0), BooleanOp.OR);
        shape = Shapes.join(shape, box(11.0, 0.0, 0.0, 16.0, 5.0, 5.0), BooleanOp.OR);
        shape = Shapes.join(shape, box(11.0, 11.0, 0.0, 16.0, 16.0, 5.0), BooleanOp.OR);
        shape = Shapes.join(shape, box(11.0, 11.0, 11.0, 16.0, 16.0, 16.0), BooleanOp.OR);
        shape = Shapes.join(shape, box(11.0, 0.0, 11.0, 16.0, 5.0, 16.0), BooleanOp.OR);
        shape = Shapes.join(shape, box(0.0, 0.0, 11.0, 5.0, 5.0, 16.0), BooleanOp.OR);
        shape = Shapes.join(shape, box(0.0, 11.0, 11.0, 5.0, 16.0, 16.0), BooleanOp.OR);
        return shape.optimize();
    }
}
