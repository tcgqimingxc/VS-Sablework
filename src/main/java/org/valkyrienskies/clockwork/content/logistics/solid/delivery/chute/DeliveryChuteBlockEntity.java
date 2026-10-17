package org.valkyrienskies.clockwork.content.logistics.solid.delivery.chute;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.valkyrienskies.clockwork.content.logistics.solid.delivery.ActiveChutes;
import org.valkyrienskies.clockwork.content.logistics.solid.delivery.DeliveryInventories;
import org.valkyrienskies.clockwork.content.logistics.solid.delivery.DeliverySpace;
import org.valkyrienskies.clockwork.content.logistics.solid.delivery.frequency_slot.FrequencySlotBehaviour;

import java.util.List;

/**
 * 交付滑道方块实体：收下落到自己身上的物品，再从下方（置物台 / 传送带 / 滑槽）推出去。
 *
 * <p>对应旧版 {@code content/logistics/solid/delivery/chute/DeliveryChuteBlockEntity}（Kotlin）。
 * 保持一致的语义：单格缓存（同物品可叠加到 {@code maxStackSize}）、频率物品、堵塞时护目镜报警、
 * 往下方的物品容器（Forge/NeoForge 的 {@code IItemHandler}）推送。
 *
 * <p>与旧版的差异：
 * <ul>
 *   <li>旧版只能由<b>加农炮</b>投递（它把在途物品直接塞进滑道的字段）；本移植的加农炮发射的是真实的
 *       {@code ItemEntity}，所以滑道每刻会收下"落在自己方块内或正上方"的掉落物 —— 这样加农炮、
 *       漏斗、玩家手抛都能用同一条路径投递，也让"打偏的炮弹"掉在地上而不是凭空消失。
 *       收取范围同时按<b>本地坐标</b>与<b>世界坐标</b>（{@link DeliverySpace}）各查一次：
 *       装在已装配的子空间上时，投掷物是在世界坐标里飞的。</li>
 *   <li>旧版频率用 {@code RedstoneLinkNetworkHandler.Frequency}；本移植简化为"一个物品栈"，
 *       两边的物品（含组件）相同即视为同频（见 {@link #hasFrequency}）。
 *       频率的存储、剪贴板与槽位渲染都收口到 {@link FrequencySlotBehaviour}（旧版同款），
 *       本类不再自己保存 {@code frequency} 字段。</li>
 * </ul>
 */
public class DeliveryChuteBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    /** 收取掉落物时的放宽量 [格]：自身方块 + 正上方一格。 */
    private static final double ABSORB_INFLATE = 0.3;

    private ItemStack stored = ItemStack.EMPTY;
    /** 频率槽：NBT / 槽位矩阵 / 频率物品都在里面（旧版同款 behaviour）。 */
    private FrequencySlotBehaviour frequencySlot;
    /** 下方容器满了（推送失败）。 */
    private boolean stuck;

    public DeliveryChuteBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // 旧版 DeliveryChuteBlockEntity.addBehaviours 同样只做这一件事。
        this.frequencySlot = new FrequencySlotBehaviour(this, new FrequencySlotBehaviour.ChuteSlot());
        behaviours.add(this.frequencySlot);
    }

    /** 供方块交互 / 渲染器读取；{@code addBehaviours} 之前为 null。 */
    public FrequencySlotBehaviour getFrequencySlot() {
        return this.frequencySlot;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }
        ActiveChutes.register(level, worldPosition);
        absorbDroppedItems();
        pushDown();
    }

    // ===================== 收 / 发 =====================

    /** 收下落在滑道上方与内部一格的掉落物（加农炮的炮弹就是这么进来的）。 */
    private void absorbDroppedItems() {
        // 两个范围都收：本地坐标（未装配时它本来就是世界坐标）与世界坐标
        // （装在已装配的子空间上时，投掷物是在世界坐标里飞的，见 DeliverySpace）
        absorbWithin(new AABB(worldPosition).expandTowards(0.0, 1.0, 0.0).inflate(ABSORB_INFLATE));
        final Vec3 center = DeliverySpace.center(level, worldPosition);
        absorbWithin(new AABB(center.x - 0.5, center.y - 0.5, center.z - 0.5,
                center.x + 0.5, center.y + 1.5, center.z + 0.5).inflate(ABSORB_INFLATE));
    }

    /** 收下 {@code area} 内装得下的掉落物（会就地扣减实体的物品数量）。 */
    private void absorbWithin(AABB area) {
        for (final ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, area)) {
            final ItemStack incoming = entity.getItem();
            if (incoming.isEmpty() || !canAccept(incoming)) {
                continue;
            }
            receiveItem(incoming);
            if (incoming.isEmpty()) {
                entity.discard();
            } else {
                // 只收了一部分：把剩余的写回实体，保证客户端看到数量变化
                entity.setItem(incoming);
            }
        }
    }

    /** 把缓存推给下方的物品容器（旧版 {@code SolidDeliveryMethods.pushTo} 的等价实现）。 */
    private void pushDown() {
        if (stored.isEmpty()) {
            stuck = false;
            return;
        }
        final IItemHandler handler = DeliveryInventories.below(level, worldPosition);
        if (handler == null) {
            stuck = true;
            return;
        }
        final ItemStack remainder = ItemHandlerHelper.insertItemStacked(handler, stored, false);
        if (remainder.getCount() != stored.getCount()) {
            stored = remainder;
            setChanged();
            sendData();
        }
        stuck = !stored.isEmpty();
    }

    /**
     * 尝试接收一份物品（<b>会就地修改 {@code incoming}</b>：把收下的部分从它里面扣掉）。
     *
     * @return 是否全部收下
     */
    public boolean receiveItem(ItemStack incoming) {
        if (incoming.isEmpty()) {
            return false;
        }
        if (stored.isEmpty()) {
            stored = incoming.copy();
            setChanged();
            sendData();
            incoming.setCount(0);
            return true;
        }
        if (!ItemStack.isSameItemSameComponents(stored, incoming)) {
            return false;
        }
        final int space = stored.getMaxStackSize() - stored.getCount();
        if (space <= 0) {
            return false;
        }
        final int moved = Math.min(space, incoming.getCount());
        stored.grow(moved);
        incoming.shrink(moved);
        setChanged();
        sendData();
        return incoming.isEmpty();
    }

    /** 是否能整份收下（供加农炮选目标时"预演"，不产生副作用）。 */
    public boolean canAccept(ItemStack incoming) {
        if (incoming.isEmpty()) {
            return false;
        }
        if (stored.isEmpty()) {
            return true;
        }
        if (!ItemStack.isSameItemSameComponents(stored, incoming)) {
            return false;
        }
        return stored.getCount() + incoming.getCount() <= stored.getMaxStackSize();
    }

    // ===================== 频率（FrequencySlotBehaviour） =====================

    /** 频率是否与本滑道一致（物品与组件都相同）；语义与 {@code ActiveChutes} 的匹配完全一致。 */
    public boolean hasFrequency(ItemStack other) {
        final ItemStack frequency = frequency();
        return !frequency.isEmpty() && ItemStack.isSameItemSameComponents(frequency, other);
    }

    /** 只读的频率物品；behaviour 还没创建（读 NBT 之前）时返回空栈。 */
    public ItemStack frequency() {
        return this.frequencySlot == null ? ItemStack.EMPTY : this.frequencySlot.getFrequency();
    }

    /** 方块交互调用：设置频率并给玩家一条快捷栏提示。 */
    public void setFrequency(ItemStack stack, Player player) {
        if (this.frequencySlot == null) {
            return;
        }
        this.frequencySlot.setFrequency(stack);
        player.displayClientMessage(Component.literal("Delivery frequency: ")
                .append(stack.getHoverName().copy().withStyle(ChatFormatting.AQUA)), true);
    }

    /** 方块交互调用：清空频率（空手潜行右键）。 */
    public void clearFrequency(Player player) {
        if (this.frequencySlot == null) {
            return;
        }
        this.frequencySlot.clearFrequency();
        player.displayClientMessage(Component.literal("Delivery frequency cleared (accepts any cannon)."),
                true);
    }

    public Component statusMessage() {
        final String content = stored.isEmpty()
                ? "empty"
                : stored.getCount() + "x " + stored.getHoverName().getString();
        final ItemStack frequency = frequency();
        return Component.literal("Delivery Chute: " + content
                + (stuck ? " (output blocked)" : "")
                + (frequency.isEmpty() ? " | no frequency" : " | " + frequency.getHoverName().getString()));
    }

    // ===================== 持久化 / 护目镜 =====================

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        tag.put("Stored", stored.save(provider));
        tag.putBoolean("Stuck", stuck);
        // 频率键由 FrequencySlotBehaviour#write 负责（旧版键名同样是 "Frequency"）。
        super.write(tag, provider, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        stored = tag.contains("Stored")
                ? ItemStack.parseOptional(provider, tag.getCompound("Stored"))
                : ItemStack.EMPTY;
        stuck = tag.getBoolean("Stuck");
        super.read(tag, provider, clientPacket);
    }

    @Override
    public void remove() {
        if (level != null) {
            ActiveChutes.unregister(level, worldPosition);
            if (!level.isClientSide && !stored.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX() + 0.5,
                        worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, stored);
                stored = ItemStack.EMPTY;
            }
        }
        super.remove();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ")
                .append(Component.literal("Contents: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(stored.isEmpty() ? "empty"
                        : stored.getCount() + "x " + stored.getHoverName().getString())
                        .withStyle(ChatFormatting.AQUA)));
        final ItemStack frequency = frequency();
        if (!frequency.isEmpty()) {
            tooltip.add(Component.literal("    ")
                    .append(Component.literal("Frequency: ").withStyle(ChatFormatting.GRAY))
                    .append(frequency.getHoverName().copy().withStyle(ChatFormatting.AQUA)));
        }
        if (stuck) {
            tooltip.add(Component.translatable("vs_clockwork.gui.delivery_chute.info.obstructed.title")
                    .withStyle(ChatFormatting.RED));
            tooltip.add(Component.translatable("vs_clockwork.gui.delivery_chute.info.obstructed")
                    .withStyle(ChatFormatting.GRAY));
        }
        return true;
    }
}
