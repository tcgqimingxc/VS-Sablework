package org.valkyrienskies.clockwork.content.logistics.gas.crafter;

import com.simibubi.create.api.boiler.BoilerHeater;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.clockwork.content.logistics.gas.GasNodeBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.GasNodeTooltip;

import java.util.ArrayList;
import java.util.List;

/**
 * 气体合成器方块实体：每 10 刻（lazyTick）在正下方的工作盆里找一个能做的气体合成配方。
 *
 * <p>对应旧版 {@code GasCrafterBlockEntity}。旧版用 Create 的 {@code DeferralBehaviour} +
 * {@code RecipeFinder} 缓存 + {@code GasCraftingRecipe.apply}；本移植改成"自己扫一遍配方表"，
 * 因为本模组的配方类型只处理气体（不属于 Create 的 {@code ProcessingRecipe} 体系）：
 * <ul>
 *   <li>配方来源：{@code level.getRecipeManager().getAllRecipesFor(TYPE)}（服务端配方表，
 *       数据包热重载后自动生效）；</li>
 *   <li>匹配：工作盆输入栏的物品 + 本节点气体的质量 + 气体温度三者都要满足；</li>
 *   <li>执行：消耗物品与气体、产出物品与气体、按 {@code energy} 吸入/放出热量，
 *       产物物品直接塞进工作盆（{@code acceptOutputs} 优先，塞不下就退回输入栏）。</li>
 * </ul>
 *
 * <p>与旧版的差异（面向玩家的行为）：
 * <ul>
 *   <li>含流体原料的配方（三个 vitriol 系列）在本移植里<b>会被跳过</b>，
 *       因为这里没有流体槽；其余 5 个配方全部可用。</li>
 *   <li>{@code heatRequirement} 用 Create 的 {@code BoilerHeater.findHeat(level, pos, state)}
 *       判断盆下方是否有热源（烈焰人燃烧室、本模组的 {@code gas_heater} 等都可以）。</li>
 *   <li>旧版有发光动画与渲染器；本移植补了等价的最小渲染
 *       （{@code crafter/client/GasCrafterRenderer}：滤网滑动 + 指示灯发光），
 *       为此 {@code processingTicks} 的状态翻转会 {@code sendData()} 给客户端。</li>
 * </ul>
 */
public class GasCrafterBlockEntity extends GasNodeBlockEntity {

    /** 每 10 刻扫一次配方（旧版 {@code lazyTick} 用 {@code DeferralBehaviour}，节奏相同）。 */
    private static final int CRAFT_INTERVAL = 10;

    /** 一次合成最短耗时 [tick]（旧版 {@code max(duration, 20)}）。 */
    private static final int MIN_PROCESSING_TICKS = 20;

    private int processingTicks;

    public GasCrafterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, 1.0);
    }

    /** 正下方的工作盆；不是工作盆则返回 null。 */
    @Nullable
    private BasinBlockEntity getBasin() {
        if (level == null) {
            return null;
        }
        return level.getBlockEntity(getBlockPos().below()) instanceof final BasinBlockEntity basin ? basin : null;
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (level == null || level.isClientSide) {
            return;
        }
        if (processingTicks > 0) {
            setProcessingTicks(Math.max(0, processingTicks - CRAFT_INTERVAL));
            return;
        }
        tryCraft();
    }

    /** 找一个可执行的配方并立刻完成一次合成（气体合成是瞬时的，{@code processing_time} 只影响节奏）。 */
    private void tryCraft() {
        final BasinBlockEntity basin = getBasin();
        if (basin == null || basin.isEmpty() || !basin.canContinueProcessing()) {
            return;
        }
        final RecipeInput input = new BasinInput(basin);
        final GasCraftingRecipe recipe = findRecipe(input, basin);
        if (recipe == null) {
            return;
        }

        recipe.apply(input, gasMix());
        gasChanged();
        setProcessingTicks(Math.max(MIN_PROCESSING_TICKS, recipe.processingTime()));
        deliverResults(basin, recipe);
    }

    /**
     * 更新合成计时；"在不在合成中"翻转时同步给客户端。
     *
     * <p>客户端有两处要读这个状态：护目镜的 crafting/idle 文案，以及
     * {@code crafter/client/GasCrafterRenderer} 的滤网滑动与指示灯发光。
     * 气体状态的常规同步只在气体数值变化时触发，盖不住"合成结束"这一刻。
     */
    private void setProcessingTicks(int ticks) {
        final boolean wasRunning = processingTicks > 0;
        processingTicks = ticks;
        if (wasRunning != (ticks > 0)) {
            sendData();
        }
    }

    /** 在配方表里找第一条满足物品 / 气体 / 温度 / 热源条件的配方。 */
    @Nullable
    private GasCraftingRecipe findRecipe(final RecipeInput input, final BasinBlockEntity basin) {
        if (level == null) {
            return null;
        }
        final boolean hasHeat = hasHeatSource(basin);
        final double temperature = gasMix().temperature();
        for (final RecipeHolder<GasCraftingRecipe> holder
                : level.getRecipeManager().getAllRecipesFor(GasCraftingRecipeSerializer.TYPE)) {
            final GasCraftingRecipe recipe = holder.value();
            if (recipe.heated() && !hasHeat) {
                continue;
            }
            if (recipe.superHeated() && !hasSuperHeat(basin)) {
                continue;
            }
            if (recipe.matches(input, gasMix(), temperature)) {
                return recipe;
            }
        }
        return null;
    }

    /** 盆下方有任意 Create 热源（{@code BoilerHeater}，本模组的 gas_heater 也注册在里面）。 */
    private boolean hasHeatSource(final BasinBlockEntity basin) {
        return heatBelow(basin) != BoilerHeater.NO_HEAT;
    }

    /** 盆下方是"超级加热"级别的热源（对齐 Create：热度 > 10 视为可superheat）。 */
    private boolean hasSuperHeat(final BasinBlockEntity basin) {
        return heatBelow(basin) > 10.0F;
    }

    private float heatBelow(final BasinBlockEntity basin) {
        if (level == null) {
            return BoilerHeater.NO_HEAT;
        }
        final BlockPos below = basin.getBlockPos().below();
        return BoilerHeater.findHeat(level, below, level.getBlockState(below));
    }

    /** 把产物物品塞进工作盆（接口允许就收进输出栏，否则退回输入栏，绝不凭空消失）。 */
    private void deliverResults(final BasinBlockEntity basin, final GasCraftingRecipe recipe) {
        final List<ItemStack> outputs = new ArrayList<>(recipe.results().size());
        for (final ItemStack result : recipe.results()) {
            if (!result.isEmpty()) {
                outputs.add(result.copy());
            }
        }
        if (outputs.isEmpty()) {
            return;
        }
        if (basin.acceptOutputs(outputs, List.of(), true)
                && basin.acceptOutputs(outputs, List.of(), false)) {
            return;
        }
        for (final ItemStack stack : outputs) {
            insertIntoBasin(basin, stack);
        }
    }

    private static void insertIntoBasin(final BasinBlockEntity basin, final ItemStack stack) {
        int remaining = stack.getCount();
        for (int slot = 0; slot < basin.getInputInventory().getSlots() && remaining > 0; slot++) {
            final ItemStack existing = basin.getInputInventory().getStackInSlot(slot);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)) {
                final int room = Math.min(existing.getMaxStackSize(), basin.getInputInventory().getSlotLimit(slot))
                        - existing.getCount();
                final int moved = Math.min(room, remaining);
                existing.grow(moved);
                remaining -= moved;
            }
        }
        for (int slot = 0; slot < basin.getInputInventory().getSlots() && remaining > 0; slot++) {
            if (basin.getInputInventory().getStackInSlot(slot).isEmpty()) {
                final int limit = Math.min(stack.getMaxStackSize(), basin.getInputInventory().getSlotLimit(slot));
                final int moved = Math.min(limit, remaining);
                basin.getInputInventory().setStackInSlot(slot, stack.copyWithCount(moved));
                remaining -= moved;
            }
        }
        if (remaining > 0) {
            // 实在塞不下：掉在盆上方（与 Create 的行为一致，不吞物品）
            net.minecraft.world.Containers.dropItemStack(basin.getLevel(),
                    basin.getBlockPos().getX() + 0.5, basin.getBlockPos().getY() + 1.5,
                    basin.getBlockPos().getZ() + 0.5, stack.copyWithCount(remaining));
        }
        basin.notifyChangeOfContents();
    }

    /** 本刻是否在合成中（护目镜显示用）。 */
    public boolean isRunning() {
        return processingTicks > 0;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ")
                .append(Component.literal("Gas Crafter: ").withStyle(net.minecraft.ChatFormatting.GRAY))
                .append(Component.literal(getBasin() == null ? "no basin below" : isRunning() ? "crafting" : "idle")
                        .withStyle(getBasin() == null ? net.minecraft.ChatFormatting.RED
                                : net.minecraft.ChatFormatting.GREEN)));
        return super.addToGoggleTooltip(tooltip, isPlayerSneaking);
    }

    // ===================== NBT =====================

    @Override
    protected void write(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider,
                         boolean clientPacket) {
        tag.putInt("ProcessingTicks", processingTicks);
        super.write(tag, provider, clientPacket);
    }

    @Override
    protected void read(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider,
                        boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        processingTicks = tag.getInt("ProcessingTicks");
    }

    /** 把工作盆的输入栏适配成原版 {@link RecipeInput}，让配方类不必依赖 Create 的类型。 */
    private record BasinInput(BasinBlockEntity basin) implements RecipeInput {

        @Override
        public ItemStack getItem(final int slot) {
            return basin.getInputInventory().getStackInSlot(slot);
        }

        @Override
        public int size() {
            return basin.getInputInventory().getSlots();
        }
    }
}
