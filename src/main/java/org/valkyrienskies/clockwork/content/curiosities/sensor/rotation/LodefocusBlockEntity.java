package org.valkyrienskies.clockwork.content.curiosities.sensor.rotation;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.valkyrienskies.clockwork.content.curiosities.sensor.SensorSubLevels;

import java.util.List;

/**
 * 透镜方块实体（Lodefocus）。
 *
 * <p>对应旧版 {@code sensor/rotation/LodefocusBlockEntity.kt}：一个只能装"绑定过磁石的指南针"的单格容器。
 * 相邻的陀螺传感器会读它，把"结构朝向"与"指向磁石的方位"作比较。
 *
 * <p>移植差异：
 * <ul>
 *   <li>旧版指南针的磁石信息存在 NBT 的 {@code LodestoneTracked / LodestonePos} 上；
 *       1.21.1 改成了数据组件 {@link DataComponents#LODESTONE_TRACKER}，读取方式随之更新。</li>
 *   <li>旧版实现 {@code WorldlyContainer}（1 格漏斗容器）。这里只保留对玩家交互必要的最小状态，
 *       不再对外暴露容器能力，避免与 Create 的物流系统产生意料之外的交互。</li>
 *   <li>掉落物位置会先经子空间姿态换算到世界坐标（旧版是 VS2 的 {@code toWorldCoordinates}）。</li>
 * </ul>
 */
public class LodefocusBlockEntity extends SmartBlockEntity {

    /** 旧版插入冷却（tick），防止一次右键连续插入。 */
    private static final int INSERTION_COOLDOWN = 10;

    private ItemStack compass = ItemStack.EMPTY;
    private int insertionCooldown;

    public LodefocusBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // 旧版同样是空实现：透镜没有可交互的 Create 行为。
    }

    @Override
    public void tick() {
        super.tick();
        if (insertionCooldown > 0) {
            insertionCooldown--;
        }
    }

    /** 磁石坐标（全局）。返回 null 表示没装指南针或指南针没绑定磁石。 */
    @Nullable
    public BlockPos getTargetPosition() {
        if (compass.isEmpty()) {
            return null;
        }
        LodestoneTracker tracker = compass.get(DataComponents.LODESTONE_TRACKER);
        if (tracker == null) {
            return null;
        }
        return tracker.target().map(GlobalPos::pos).orElse(null);
    }

    /**
     * 里面装的指南针（给客户端渲染器用，例如
     * {@code content/curiosities/sensor/rotation/client/LodefocusRenderer}）。
     *
     * <p>只读：写入必须走 {@link #insertCompass(ItemStack)} / {@link #dropCompass()}，
     * 这两个方法会负责 {@code setChanged()} 与 {@code sendData()}。
     */
    public ItemStack getCompass() {
        return compass;
    }

    public boolean canInsert() {
        return insertionCooldown == 0;
    }

    /** 装入一个"绑定过磁石"的指南针；返回是否真的装进去了。 */
    public boolean insertCompass(ItemStack stack) {
        if (!canInsert()) {
            return false;
        }
        LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
        if (tracker == null || tracker.target().isEmpty()) {
            return false;
        }
        dropCompass();
        compass = stack.copyWithCount(1);
        insertionCooldown = INSERTION_COOLDOWN;
        setChanged();
        sendData();
        return true;
    }

    /** 把里面的指南针丢出来（旧版带随机初速度，这里保留同样的手感）。 */
    public void dropCompass() {
        if (compass.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ItemStack dropped = compass;
        compass = ItemStack.EMPTY;
        setChanged();
        sendData();

        SubLevel subLevel = SensorSubLevels.containing(serverLevel, worldPosition);
        Vector3d spawn = SensorSubLevels.toGlobal(subLevel, Vec3.atCenterOf(worldPosition), new Vector3d());
        ItemEntity itemEntity = new ItemEntity(serverLevel, spawn.x, spawn.y, spawn.z, dropped);
        itemEntity.setDeltaMovement(
                (serverLevel.random.nextDouble() - 0.5) * 0.3,
                serverLevel.random.nextDouble() * 0.3 + 0.1,
                (serverLevel.random.nextDouble() - 0.5) * 0.3);
        serverLevel.addFreshEntity(itemEntity);
        serverLevel.playSound(null, spawn.x, spawn.y, spawn.z, SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                SoundSource.BLOCKS, 0.65f, 1.0f);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("InsertionCooldown", insertionCooldown);
        if (!compass.isEmpty()) {
            tag.put("Compass", compass.save(registries));
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        insertionCooldown = tag.getInt("InsertionCooldown");
        compass = tag.contains("Compass")
                ? ItemStack.parseOptional(registries, tag.getCompound("Compass"))
                : ItemStack.EMPTY;
    }
}
