package org.valkyrienskies.clockwork.content.curiosities.sensor.rotation;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import org.valkyrienskies.clockwork.content.curiosities.sensor.CWSensorsIndex;

/**
 * 透镜方块（Lodefocus）。
 *
 * <p>对应旧版 {@code sensor/rotation/LodefocusBlock.kt}：一块可以嵌入"绑定磁石的指南针"的透镜。
 * 相邻的陀螺传感器读取它以后，会把磁石方位当作比较基准；它同时属于 {@code #vs_clockwork:sensor_lens} 标签，
 * 所以也能当测距传感器的透镜用。
 *
 * <p>移植差异：旧版指南针的磁石标记在 NBT 里，1.21.1 改为数据组件
 * {@link DataComponents#LODESTONE_TRACKER}，因此"是否是绑定磁石的指南针"的判定方式随之改变。
 */
public class LodefocusBlock extends Block implements IBE<LodefocusBlockEntity> {

    public static final MapCodec<LodefocusBlock> CODEC = simpleCodec(LodefocusBlock::new);

    public LodefocusBlock(Properties properties) {
        super(properties.pushReaction(PushReaction.NORMAL));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public Class<LodefocusBlockEntity> getBlockEntityClass() {
        return LodefocusBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends LodefocusBlockEntity> getBlockEntityType() {
        return CWSensorsIndex.LODEFOCUS_BE.get();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            ItemStack held = player.getItemInHand(hand);
            if (held.isEmpty()) {
                withBlockEntityDo(level, pos, LodefocusBlockEntity::dropCompass);
                return ItemInteractionResult.SUCCESS;
            }
            if (held.is(Items.COMPASS) && held.has(DataComponents.LODESTONE_TRACKER)) {
                return onBlockEntityUseItemOn(level, pos, be -> {
                    if (!be.insertCompass(held)) {
                        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                    }
                    if (!player.isCreative()) {
                        held.shrink(1);
                    }
                    return ItemInteractionResult.SUCCESS;
                });
            }
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!isMoving) {
            withBlockEntityDo(level, pos, LodefocusBlockEntity::dropCompass);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
