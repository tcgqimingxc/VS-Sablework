package org.valkyrienskies.clockwork.content.curiosities.altmeter;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

import java.util.List;

/**
 * 高度计方块实体。
 *
 * <p>对应旧版 {@code curiosities/altmeter/AltMeterBlockEntity.kt}。每 tick 采样一次所在位置的高度，
 * 与目标高度 {@code triggerHeight} 比较，按 {@code triggerDirection} 生成 0~15 的红石强度写回方块的
 * {@code power} 属性。
 *
 * <p>移植差异：
 * <ul>
 *   <li>旧版写了 {@code level.getShipManagingPos(blockPos)?.transform...} 却**没有接收返回值**，
 *       于是距离算的是"子空间 plot 里的本地 Y"，在结构上根本不准（旧版实际 bug）。
 *       这里改为真正用 {@link SubLevel#logicalPose()} 把方块中心换算到世界坐标再取 Y —— 行为对玩家是修正。</li>
 *   <li>配置数据从 NBT 键 {@code ClockworkConstants.Nbt.*} 改为本类内的常量字符串（内容一致，键名不变形）。</li>
 *   <li>旧版的配置 GUI / 网络包改成方块交互（见 {@link AltMeterBlock} 的类注释）。</li>
 * </ul>
 */
public class AltMeterBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    private static final String NBT_HEIGHT = "TriggerHeight";
    private static final String NBT_SENSITIVITY = "TriggerSensitivity";
    private static final String NBT_DIRECTION = "TriggerDirection";

    /** 旧版 {@code AltMeterDirection}：信号随高度变化的三种方向。 */
    public enum TriggerDirection {
        /** 从下往上增强，到达并超过目标高度时最强。 */
        UP,
        /** 从上往下增强，到达并低于目标高度时最强。 */
        DOWN,
        /** 从两侧向目标高度增强，正好在目标高度最强。 */
        BOTH;

        public TriggerDirection next() {
            return switch (this) {
                case UP -> DOWN;
                case DOWN -> BOTH;
                case BOTH -> UP;
            };
        }
    }

    private int triggerHeight;
    private int triggerSensitivity = 1;
    private TriggerDirection triggerDirection = TriggerDirection.UP;
    private int signalStrength;

    public AltMeterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // 旧版同样为空实现。
    }

    public int getTriggerHeight() {
        return triggerHeight;
    }

    public void setTriggerHeight(int height) {
        this.triggerHeight = height;
        setChanged();
        sendData();
    }

    public int getTriggerSensitivity() {
        return triggerSensitivity;
    }

    public void setTriggerSensitivity(int sensitivity) {
        this.triggerSensitivity = Mth.clamp(sensitivity, 1, 16);
        setChanged();
        sendData();
    }

    public TriggerDirection getTriggerDirection() {
        return triggerDirection;
    }

    public void setTriggerDirection(TriggerDirection direction) {
        this.triggerDirection = direction;
        setChanged();
        sendData();
    }

    /** 当前世界高度（取方块中心并向下取整），供玩家显示与"设定目标"使用。 */
    public int getCurrentHeight() {
        return Mth.floor(currentCenterY());
    }

    /**
     * 当前红石输出强度 0..15。
     *
     * <p>本方法由 CC:Tweaked 集成切片追加（旧版 {@code AltMeterPeripheral.getOutput()} 直接读这个字段，
     * 这里的字段是私有的，需要一个公开读取口）。
     */
    public int getSignalStrength() {
        return signalStrength;
    }

    private double currentCenterY() {
        double localX = worldPosition.getX() + 0.5;
        double localY = worldPosition.getY() + 0.5;
        double localZ = worldPosition.getZ() + 0.5;
        if (level == null) {
            return localY;
        }
        SubLevel subLevel = Sable.HELPER.getContaining(level, worldPosition);
        if (subLevel == null) {
            return localY;
        }
        Vector3d world = subLevel.logicalPose()
                .transformPosition(new Vector3d(localX, localY, localZ), new Vector3d());
        return world.y;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }

        double distance = currentCenterY() - triggerHeight;
        int sensitivity = Math.max(1, triggerSensitivity);
        int raw = switch (triggerDirection) {
            case BOTH -> (int) (sensitivity - Math.abs(distance));
            case DOWN -> (int) (sensitivity - distance);
            case UP -> (int) (sensitivity + distance);
        };
        raw = Mth.clamp(raw, 0, sensitivity);
        // 旧版 Int.toRange(0..sensitivity, 0..15) 的等价写法。
        signalStrength = (int) ((float) raw / sensitivity * 15);

        if (getBlockState().getValue(AltMeterBlock.POWER) != signalStrength) {
            level.setBlock(worldPosition, getBlockState().setValue(AltMeterBlock.POWER, signalStrength), 3);
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt(NBT_HEIGHT, triggerHeight);
        tag.putInt(NBT_SENSITIVITY, triggerSensitivity);
        tag.putString(NBT_DIRECTION, triggerDirection.name());
        tag.putInt("SignalStrength", signalStrength);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        triggerHeight = tag.getInt(NBT_HEIGHT);
        triggerSensitivity = Math.max(1, tag.getInt(NBT_SENSITIVITY));
        // 旧版注释：从蓝图粘贴过来时这个字符串会是空的。
        String direction = tag.getString(NBT_DIRECTION);
        triggerDirection = direction.isEmpty() ? TriggerDirection.UP : parseDirection(direction);
        signalStrength = tag.getInt("SignalStrength");
    }

    private static TriggerDirection parseDirection(String name) {
        for (TriggerDirection candidate : TriggerDirection.values()) {
            if (candidate.name().equals(name)) {
                return candidate;
            }
        }
        return TriggerDirection.UP;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("Altimeter"));
        tooltip.add(Component.literal("  Current altitude: " + getCurrentHeight()));
        tooltip.add(Component.literal("  Target altitude: " + triggerHeight));
        tooltip.add(Component.literal("  Direction: " + triggerDirection));
        tooltip.add(Component.literal("  Sensitivity: " + triggerSensitivity));
        return true;
    }
}
