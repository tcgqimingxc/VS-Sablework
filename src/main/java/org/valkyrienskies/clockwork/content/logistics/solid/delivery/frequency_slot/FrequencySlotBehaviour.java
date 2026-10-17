package org.valkyrienskies.clockwork.content.logistics.solid.delivery.frequency_slot;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.equipment.clipboard.ClipboardCloneable;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * 交付滑道的"频率槽"行为：保存一个频率物品，并把它的 ValueBox 槽位交给渲染器。
 *
 * <p>对应旧版 {@code content/logistics/solid/delivery/frequency_slot/FrequencySlotBehaviour.kt}。
 * 旧版继承 Create 的 {@link BlockEntityBehaviour} 并实现 {@link ClipboardCloneable}，
 * 频率用 Create 的 {@code RedstoneLinkNetworkHandler.Frequency} 包装。
 * 本移植的频率语义在交付切片里已经统一成"一个物品栈"（见 {@code DeliveryChuteBlockEntity} 与
 * {@code ActiveChutes}），所以这里只存 {@link ItemStack}，比较方式仍是
 * {@link ItemStack#isSameItemSameComponents} —— 与交付加农炮的 {@code hasFrequency} 完全一致。
 *
 * <h2>为什么单独做一个 behaviour（而不是继续用方块实体字段）</h2>
 * <ul>
 *   <li>旧版的槽位渲染器 {@code FrequencySlotRenderer} 通过 {@code be.getBehaviour(TYPE)} 找槽位变换，
 *       再由 {@code ValueBoxRenderer} 把频率物品画进槽里；不移植 behaviour 就没有槽位矩阵，
 *       只能像之前那样"频率只是一个隐藏字段"。</li>
 *   <li>NBT 由 {@link SmartBlockEntity#write} / {@link SmartBlockEntity#read} 统一遍历 behaviours，
 *       与旧版一样写在方块实体的根 tag 里（键 {@code "Frequency"}），旧存档可直接读取。</li>
 *   <li>剪贴板复制（旧版 {@code FrequencySlotGlobals} 的 {@code DeliveryFrequencies} 键）也由
 *       {@link ClipboardCloneable} 承担，后续接剪贴板时不用再改方块实体。</li>
 * </ul>
 *
 * <h2>槽位位置</h2>
 * <p>{@link ChuteSlot} 把频率物品放在滑道顶部开口的中心、顶板上表面（13/16）之上一点
 * （y = 15/16），并绕 X 轴转 90° 让它平躺。旧版 {@code DeliveryChuteBlockEntity.FrequencySlot}
 * 把物品放在 y = 10.5/16、朝北偏移 5/16 的位置；那个位置在 11..13 的顶板下面，实机是看不见的
 * （旧版槽位还因为 {@code isSideActive} 把 UP 排除而永远不渲染）。本移植按"能看见、能点中"
 * 重新取值，交互判定半径仍是 {@link ValueBoxTransform#testHit} 的 {@code scale / 2 = 0.25} 格。
 */
public class FrequencySlotBehaviour extends BlockEntityBehaviour implements ClipboardCloneable {

    /** 旧版 {@code FrequencySlotBehaviour.Companion.TYPE}；按 BehaviourType 取实例。 */
    public static final BehaviourType<FrequencySlotBehaviour> TYPE = new BehaviourType<>();

    /** 旧版写入的 NBT / 剪贴板键逐字相同，保证旧存档兼容。 */
    private static final String NBT_FREQUENCY = "Frequency";
    private static final String CLIPBOARD_KEY = "DeliveryFrequencies";

    private final ValueBoxTransform slot;
    private ItemStack frequency = ItemStack.EMPTY;

    public FrequencySlotBehaviour(SmartBlockEntity blockEntity, ValueBoxTransform slot) {
        super(blockEntity);
        this.slot = slot;
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPE;
    }

    public ValueBoxTransform getSlot() {
        return this.slot;
    }

    /** 只读访问：调用方不得修改返回的栈（渲染/比较都是只读）。 */
    public ItemStack getFrequency() {
        return this.frequency;
    }

    /** 设置频率（复制一份并固定数量 1，与旧版 {@code setFrequency} 一致）。 */
    public void setFrequency(ItemStack stack) {
        this.frequency = stack.copyWithCount(1);
        this.blockEntity.setChanged();
        this.blockEntity.sendData();
    }

    public void clearFrequency() {
        this.frequency = ItemStack.EMPTY;
        this.blockEntity.setChanged();
        this.blockEntity.sendData();
    }

    /** 槽位是否应该渲染（方块未移除、槽位偏移有效）。 */
    public boolean shouldRender() {
        final Level level = this.blockEntity.getLevel();
        return level != null && this.slot.shouldRender(level, this.blockEntity.getBlockPos(),
                this.blockEntity.getBlockState());
    }

    /** 命中测试；{@code localHit} 是相对方块原点的坐标（0..1）。 */
    public boolean testHit(Vec3 localHit) {
        final Level level = this.blockEntity.getLevel();
        if (level == null) {
            return false;
        }
        return this.slot.testHit(level, this.blockEntity.getBlockPos(), this.blockEntity.getBlockState(), localHit);
    }

    // ===================== 持久化 / 剪贴板 =====================

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put(NBT_FREQUENCY, this.frequency.save(registries));
    }

    @Override
    public void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.frequency = tag.contains(NBT_FREQUENCY)
                ? ItemStack.parseOptional(registries, tag.getCompound(NBT_FREQUENCY))
                : ItemStack.EMPTY;
    }

    @Override
    public String getClipboardKey() {
        return CLIPBOARD_KEY;
    }

    @Override
    public boolean writeToClipboard(HolderLookup.Provider registries, CompoundTag tag, Direction side) {
        tag.put(NBT_FREQUENCY, this.frequency.save(registries));
        return true;
    }

    @Override
    public boolean readFromClipboard(HolderLookup.Provider registries, CompoundTag tag, Player player,
                                     Direction side, boolean simulate) {
        if (!tag.contains(NBT_FREQUENCY)) {
            return false;
        }
        if (simulate) {
            return true;
        }
        final ItemStack parsed = ItemStack.parseOptional(registries, tag.getCompound(NBT_FREQUENCY));
        if (parsed.isEmpty()) {
            this.clearFrequency();
        } else {
            this.setFrequency(parsed);
        }
        return true;
    }

    /**
     * 滑道顶面的槽位：平放在顶部开口中心、比顶板上表面略高，便于看见与点击。
     *
     * <p>固定 X 轴 90° 让物品"躺下"，与 Create 的 {@code CenteredSideValueBoxTransform}
     * 处理顶面槽位的做法一致；这里不复用它的原因见类注释（旧版位置不可见）。
     */
    public static final class ChuteSlot extends ValueBoxTransform {

        /** 槽位中心（方块本地坐标）：顶板在 11..13，这里取 15/16，物品躺在顶板上方。 */
        private static final Vec3 LOCAL_OFFSET = new Vec3(0.5, 15.0 / 16.0, 0.5);
        /** 平躺旋转；复用同一个四元数，避免每次渲染新建对象。 */
        private static final Quaternionf FLAT = new Quaternionf().rotationX((float) Math.PI * 0.5f);

        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            return LOCAL_OFFSET;
        }

        @Override
        public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
            ms.mulPose(FLAT);
        }
    }
}
