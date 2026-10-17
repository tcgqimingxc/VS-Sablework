package org.valkyrienskies.clockwork.content.logistics.solid.delivery.cannon;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.clockwork.content.logistics.CWLogisticsSounds;
import org.valkyrienskies.clockwork.content.logistics.solid.delivery.ActiveChutes;
import org.valkyrienskies.clockwork.content.logistics.solid.delivery.DeliveryInventories;
import org.valkyrienskies.clockwork.content.logistics.solid.delivery.DeliverySpace;
import org.valkyrienskies.clockwork.content.logistics.solid.delivery.chute.DeliveryChuteBlockEntity;

import java.util.List;

/**
 * 交付加农炮方块实体：从下方容器取物品，朝 {@code FACING} 抛到同频的交付滑道上。
 *
 * <p>对应旧版 {@code content/logistics/solid/delivery/cannon/DeliveryCannonBlockEntity}（Kotlin）。
 * 旧版的行为链是：从下方 {@code IItemHandler} 抽取 64 个 → 在 100 格内找同频滑道 →
 * 转头对准（{@code LerpedFloat}）→ 把物品挂到"在途"字段里，按距离推进一个假投掷物，
 * 到点直接塞进滑道的字段（没有真实的实体，抛物线只存在于渲染里）。
 *
 * <p>本移植按任务要求"可用原版 {@code ItemEntity} + 速度"改成<b>真实弹道</b>：
 * <ol>
 *   <li>从下方容器的物品能力里抽一份物品（最多 64 个）；</li>
 *   <li>在 {@link ActiveChutes} 里找 32 格内、频率匹配且装得下的滑道（取最近的）；</li>
 *   <li>解出"正好在 n 刻后落到目标顶面"的初速度，交给原版 {@link ItemEntity} 自己飞
 *       （vanilla 物品的重力 0.04 / 阻力 0.98，推导见 {@link #solveLaunch}）；</li>
 *   <li>滑道自己会收下落点上的掉落物（见 {@code DeliveryChuteBlockEntity#absorbDroppedItems}），
 *       因此本类不需要跟踪投掷物，被方块挡住/打偏时物品就掉在地上，不会凭空消失。</li>
 * </ol>
 *
 * <p>瞄准说明：投掷物<b>总是瞄准目标滑道</b>（旧版也是转过头去瞄，而不是死板地沿 {@code FACING} 直射），
 * {@code FACING} 只决定炮口位置与模型朝向。因此把炮口对着大致方向即可，不用精确对齐。
 * 坐标一律走世界坐标（{@link DeliverySpace}）：装在已装配的子空间上时，投掷物生成在船体的世界位置，
 * 玩家能看见它飞；目标是移动中的船时按"发射瞬间的位置"打，打偏就掉在原地（可捡回）。
 *
 * <p>与旧版的简化：没有转头动画（不做 BER）、没有火药加速与圆盘分布模式、频率从
 * {@code RedstoneLinkNetworkHandler.Frequency} 简化为"一个物品栈"。
 * 新增了红石上升沿立刻补射一次的能力（见 {@code DeliveryCannonBlock#neighborChanged}）。
 */
public class DeliveryCannonBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    /** 目标搜索半径 [格]（旧版 100 格；本移植发射真实投掷物，32 格更合理）。 */
    private static final double MAX_RANGE = 32.0;

    /** 两次发射的最小间隔 [tick]。 */
    private static final int SHOT_COOLDOWN = 10;

    /** 一次最多抽取的物品数量（旧版 {@code ItemHelper.extract(..., UPTO, 64, ...)}）。 */
    private static final int MAX_EXTRACT = 64;

    /** 投掷物的重力与每刻阻力：与 vanilla {@code ItemEntity} 完全一致。 */
    private static final double GRAVITY = 0.04;
    private static final double DRAG = 0.98;

    private ItemStack currentStack = ItemStack.EMPTY;
    private ItemStack frequency = ItemStack.EMPTY;
    private int cooldown;
    /** 红石上升沿请求的"立刻补射"。 */
    private boolean immediateShot;

    public DeliveryCannonBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
        }
        if (currentStack.isEmpty()) {
            currentStack = extractFromBelow();
            if (!currentStack.isEmpty()) {
                sendData();
            }
        }
        if (currentStack.isEmpty()) {
            immediateShot = false;
            return;
        }
        if (cooldown > 0 && !immediateShot) {
            return;
        }
        final DeliveryChuteBlockEntity target = findTarget();
        if (target == null) {
            immediateShot = false;
            return;
        }
        fire(target);
    }

    /** 红石上升沿调用：跳过冷却，下一次 tick 立刻尝试补射。 */
    public void requestImmediateShot() {
        immediateShot = true;
    }

    /** 从下方方块的物品能力里抽一份物品（旧版 {@code SolidDeliveryMethods.extractFrom}）。 */
    private ItemStack extractFromBelow() {
        final IItemHandler handler = DeliveryInventories.below(level, worldPosition);
        if (handler == null) {
            return ItemStack.EMPTY;
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            final ItemStack extracted = handler.extractItem(slot, MAX_EXTRACT, false);
            if (!extracted.isEmpty()) {
                return extracted;
            }
        }
        return ItemStack.EMPTY;
    }

    /** 最近的、同频且装得下的滑道；没有则返回 null。 */
    @Nullable
    private DeliveryChuteBlockEntity findTarget() {
        final Vec3 origin = DeliverySpace.center(level, worldPosition);
        for (final BlockPos pos : ActiveChutes.findSorted(level, origin, MAX_RANGE, frequency)) {
            if (level.getBlockEntity(pos) instanceof DeliveryChuteBlockEntity chute && chute.canAccept(currentStack)) {
                return chute;
            }
        }
        return null;
    }

    /** 发射：解出初速度并把物品作为真实投掷物丢出去。 */
    private void fire(DeliveryChuteBlockEntity target) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        final Direction facing = getBlockState().getValue(DeliveryCannonBlock.FACING);
        // 炮口 = 方块中心的世界坐标 + 世界方向上的偏移（子空间会把这套坐标整体旋转平移）
        final Vec3 muzzleOffset = DeliverySpace.direction(level, worldPosition, facing);
        final Vec3 from = DeliverySpace.center(level, worldPosition)
                .add(muzzleOffset.x * 0.7, muzzleOffset.y * 0.7 + 0.2, muzzleOffset.z * 0.7);
        final Vec3 to = DeliverySpace.center(level, target.getBlockPos()).add(0.0, 0.55, 0.0);
        final double horizontalDistance = new Vec3(to.x - from.x, 0.0, to.z - from.z).length();
        final Vec3 velocity = solveLaunch(from, to, flightTicksFor(horizontalDistance));
        if (velocity == null) {
            return;
        }

        final ItemEntity projectile = new ItemEntity(serverLevel, from.x, from.y, from.z, currentStack.copy(),
                velocity.x, velocity.y, velocity.z);
        // 与原版投掷物一致：10 刻内玩家吸不走（滑道的收取不看拾取延迟）
        projectile.setDefaultPickUpDelay();
        serverLevel.addFreshEntity(projectile);

        currentStack = ItemStack.EMPTY;
        cooldown = SHOT_COOLDOWN;
        immediateShot = false;
        level.playSound(null, worldPosition, CWLogisticsSounds.THWOOM, SoundSource.BLOCKS, 1.0F, 1.0F);
        sendData();
    }

    /** 飞得越远、飞得越久（拱越高，像迫击炮）；上限 40 刻避免投掷物在半空飘太久。 */
    private static int flightTicksFor(double horizontalDistance) {
        return Mth.clamp((int) Math.round(5.0 + horizontalDistance * 0.9), 10, 40);
    }

    /**
     * 解出发射速度，使投掷物正好在 {@code ticks} 刻后到达 {@code to}。
     *
     * <p>推导（严格按 vanilla {@code ItemEntity#tick} 的积分顺序：先加重力 → 位移 → 乘阻力）：
     * <pre>
     * 第 k 刻用于位移的速度 u_k = 0.98^(k-1)·v₀ - g·S(k)，  S(k) = (1 - 0.98^k) / 0.02
     * n 刻后的位移 D(n) = v₀·S(n) - g·T(n)，              T(n) = 50n - 49·S(n)
     * </pre>
     * 于是 v₀ = (D + g·T(n)) / S(n)，水平与垂直各解一次。物品的 g = 0.04、阻力系数 0.98
     * （{@code ItemEntity.getDefaultGravity()} 与 {@code tick} 里的 {@code f = 0.98F}）。
     *
     * @return 初速度；数值异常时返回 null（宁可这一炮不发，也不要扔出 NaN 实体）
     */
    @Nullable
    private static Vec3 solveLaunch(Vec3 from, Vec3 to, int ticks) {
        final double s = 50.0 * (1.0 - Math.pow(DRAG, ticks));
        final double t = 50.0 * ticks - 49.0 * s;
        if (s <= 1.0E-6) {
            return null;
        }
        final Vec3 velocity = new Vec3((to.x - from.x) / s, (to.y - from.y + GRAVITY * t) / s, (to.z - from.z) / s);
        if (Double.isNaN(velocity.x) || Double.isNaN(velocity.y) || Double.isNaN(velocity.z)
                || Math.abs(velocity.y) > 4.0) {
            return null;
        }
        return velocity;
    }

    // ===================== 频率 / 状态 =====================

    public void setFrequency(ItemStack stack, Player player) {
        frequency = stack.copyWithCount(1);
        setChanged();
        sendData();
        player.displayClientMessage(Component.literal("Delivery frequency: ")
                .append(frequency.getHoverName().copy().withStyle(ChatFormatting.AQUA)), true);
    }

    public void clearFrequency(Player player) {
        frequency = ItemStack.EMPTY;
        setChanged();
        sendData();
        player.displayClientMessage(Component.literal("Delivery frequency cleared (fires at any chute)."),
                true);
    }

    public Component statusMessage() {
        final String loaded = currentStack.isEmpty() ? "no item" : currentStack.getCount() + "x "
                + currentStack.getHoverName().getString();
        return Component.literal("Delivery Cannon: " + loaded
                + (frequency.isEmpty() ? " | no frequency" : " | " + frequency.getHoverName().getString()));
    }

    // ===================== 持久化 / 护目镜 =====================

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        tag.put("Current", currentStack.save(provider));
        if (!frequency.isEmpty()) {
            tag.put("Frequency", frequency.save(provider));
        }
        super.write(tag, provider, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        currentStack = tag.contains("Current")
                ? ItemStack.parseOptional(provider, tag.getCompound("Current"))
                : ItemStack.EMPTY;
        frequency = tag.contains("Frequency")
                ? ItemStack.parseOptional(provider, tag.getCompound("Frequency"))
                : ItemStack.EMPTY;
        super.read(tag, provider, clientPacket);
    }

    @Override
    public void remove() {
        if (level != null && !level.isClientSide && !currentStack.isEmpty()) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5, currentStack);
            currentStack = ItemStack.EMPTY;
        }
        super.remove();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ")
                .append(Component.literal("Loaded: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(currentStack.isEmpty() ? "empty"
                        : currentStack.getCount() + "x " + currentStack.getHoverName().getString())
                        .withStyle(ChatFormatting.AQUA)));
        if (!frequency.isEmpty()) {
            tooltip.add(Component.literal("    ")
                    .append(Component.literal("Frequency: ").withStyle(ChatFormatting.GRAY))
                    .append(frequency.getHoverName().copy().withStyle(ChatFormatting.AQUA)));
        } else {
            tooltip.add(Component.literal("    ")
                    .append(Component.literal("No frequency: any chute in range")
                            .withStyle(ChatFormatting.DARK_GRAY)));
        }
        return true;
    }
}
