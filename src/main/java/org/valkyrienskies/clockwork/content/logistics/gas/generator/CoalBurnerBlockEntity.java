package org.valkyrienskies.clockwork.content.logistics.gas.generator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.clockwork.config.ClockworkConfig;
import org.valkyrienskies.clockwork.content.logistics.gas.GasNodeBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.GasPhysics;

/**
 * 煤炉方块实体：烧燃料 → 给节点加热。
 *
 * <p>对应旧版 {@code CoalBurnerBlockEntity}。每刻（有余燃时间时）：
 * <pre>
 * 升温能量 = min(总热容 · (目标温度 - 当前温度), 每刻最大焦耳)
 * </pre>
 * 其中"总热容"= 气体热容 + 管壁热容（旧版注释明确说要连管壁一起加热，否则热量会被管壁抽走）。
 * 目标温度 850 K、每刻上限 10000 J 都来自旧版（旧版为硬编码 850.0 / {@code MAX_JOULES_PER_TICK}）。
 *
 * <p>与旧版的差异：
 * <ul>
 *   <li>旧版用 Architectury 的 {@code FuelRegistry}；这里用原版 {@link AbstractFurnaceBlockEntity}
 *       的燃料表（NeoForge 平台上等价）。</li>
 *   <li>旧版实现 {@code ISyncableStorage} 自建物品栏同步包；这里改用原版 {@link Container} 接口，
 *       漏斗/机械臂可以直接投料与抽取。</li>
 * </ul>
 */
public class CoalBurnerBlockEntity extends GasNodeBlockEntity implements WorldlyContainer {

    private static final int SLOT_FUEL = 0;
    private static final int SLOT_REMAINDER = 1;

    /** 旧版硬编码的加热目标温度 [K]。 */
    private static final double TARGET_TEMPERATURE = 850.0;

    private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private int fuelTicks;

    public CoalBurnerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, 1.0);
    }

    /** 是否是有效燃料（用 NeoForge 的 {@code ItemStack#getBurnTime}，等价于旧版的燃料注册表）。 */
    public boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && stack.getBurnTime(RecipeType.SMELTING) > 0;
    }

    /** 取出燃料或残余各一个，供空手右键取回。 */
    public ItemStack extractOne() {
        for (int slot = 0; slot < items.size(); slot++) {
            if (!items.get(slot).isEmpty()) {
                ItemStack taken = items.get(slot).copyWithCount(1);
                items.get(slot).shrink(1);
                setChanged();
                return taken;
            }
        }
        return ItemStack.EMPTY;
    }

    /** 把玩家手上的燃料放进炉子（一次放一整叠）。 */
    public void insertFromHand(Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        ItemStack fuel = items.get(SLOT_FUEL);
        if (fuel.isEmpty()) {
            items.set(SLOT_FUEL, held.copy());
            if (!player.isCreative()) {
                player.setItemInHand(hand, ItemStack.EMPTY);
            }
        } else if (ItemStack.isSameItemSameComponents(fuel, held)) {
            int room = fuel.getMaxStackSize() - fuel.getCount();
            int moved = Math.min(room, held.getCount());
            fuel.grow(moved);
            if (!player.isCreative()) {
                held.shrink(moved);
            }
        } else {
            return;
        }
        setChanged();
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }

        if (fuelTicks > 0) {
            fuelTicks--;
            heatNetwork();
            setLit(true);
            setChanged();
            return;
        }

        ItemStack fuel = items.get(SLOT_FUEL);
        if (fuel.isEmpty()) {
            setLit(false);
            return;
        }
        fuelTicks = burnTime(fuel);
        if (fuel.hasCraftingRemainingItem()) {
            ItemStack remainder = fuel.getCraftingRemainingItem();
            ItemStack existing = items.get(SLOT_REMAINDER);
            if (existing.isEmpty()) {
                items.set(SLOT_REMAINDER, remainder);
            } else if (ItemStack.isSameItemSameComponents(existing, remainder)
                    && existing.getCount() < existing.getMaxStackSize()) {
                existing.grow(1);
            } else {
                // 残余槽满了就掉出来（旧版行为）
                net.minecraft.world.entity.item.ItemEntity dropped =
                        new net.minecraft.world.entity.item.ItemEntity(level,
                                getBlockPos().getX() + 0.5, getBlockPos().getY() + 1.0, getBlockPos().getZ() + 0.5,
                                remainder);
                dropped.setDefaultPickUpDelay();
                level.addFreshEntity(dropped);
            }
        }
        fuel.shrink(1);
        setChanged();
    }

    private void heatNetwork() {
        if (gasMix().isEmpty()) {
            return;
        }
        double heatCapacity = gasMix().heatCapacity() + GasPhysics.wallHeatCapacity();
        double energy = Math.min(
                heatCapacity * (TARGET_TEMPERATURE - gasMix().temperature()),
                ClockworkConfig.server().coalBurnerMaxJoulesPerTick.get());
        if (energy > 0) {
            gasMix().addHeat(energy);
            gasChanged();
        }
    }

    private void setLit(boolean lit) {
        if (level == null) {
            return;
        }
        BlockState state = getBlockState();
        if (state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT) == lit) {
            return;
        }
        level.setBlockAndUpdate(getBlockPos(),
                state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, lit));
    }

    private static int burnTime(ItemStack stack) {
        return stack.getBurnTime(RecipeType.SMELTING);
    }

    // ===================== 容器 =====================

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? new int[]{SLOT_REMAINDER} : new int[]{SLOT_FUEL};
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction direction) {
        return index == SLOT_FUEL && direction != Direction.DOWN && accepts(stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
        return index == SLOT_REMAINDER && direction == Direction.DOWN;
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return ContainerHelper.removeItem(items, slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    public void destroy() {
        if (level != null && !level.isClientSide) {
            for (ItemStack stack : items) {
                if (!stack.isEmpty()) {
                    net.minecraft.world.Containers.dropItemStack(level, getBlockPos().getX(), getBlockPos().getY(),
                            getBlockPos().getZ(), stack);
                }
            }
            items.clear();
        }
        super.destroy();
    }

    // ===================== NBT =====================

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        ContainerHelper.saveAllItems(tag, items, provider);
        tag.putInt("FuelTicks", fuelTicks);
        super.write(tag, provider, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        ContainerHelper.loadAllItems(tag, items, provider);
        fuelTicks = tag.getInt("FuelTicks");
    }
}
