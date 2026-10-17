package org.valkyrienskies.clockwork.content.kinetics.sequenced_seat;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.EnumSet;
import java.util.Set;

/**
 * 命令座椅方块实体：一个"被按键序列控制的变速箱"。
 *
 * <p>对应旧版 {@code SequencedSeatBlockEntity.kt}（{@code SplitShaftBlockEntity} 子类）。
 * 座椅自身从下方（{@code sourceFacing == DOWN}）取动力，四个水平侧面各有一个"输出倍率"，
 * 倍率由"玩家在座椅上按了哪些键"决定（见 {@link SeatRuleList}）：于是坐在上面按 WASD
 * 就能分别驱动四个方向的轴，用来操控机械臂 / 起落架之类的序列动作。
 *
 * <p>与旧版差异：
 * <ul>
 *   <li>旧版的 ComputerCraft 外设事件（{@code command_seat_keys}）没有移植（本工程没有 CC 集成）。</li>
 *   <li>旧版只有在 {@code sourceFacing == DOWN} 时才允许四侧输出，这里保持一致；
 *       但源面本身的倍率固定为 1（不参与规则），否则动力会被自己的规则掐断。</li>
 *   <li>规则变更（网络包 / 界面）会 {@code detachKinetics + attachKinetics} 让网络重算应力，
 *       与旧版一致。</li>
 * </ul>
 */
public class CommandSeatBlockEntity extends SplitShaftBlockEntity {

    private SeatRuleList forwardRules = SeatRuleList.defaultList(Rotation.NONE);
    private SeatRuleList backwardRules = SeatRuleList.defaultList(Rotation.CLOCKWISE_180);
    private SeatRuleList leftRules = SeatRuleList.defaultList(Rotation.COUNTERCLOCKWISE_90);
    private SeatRuleList rightRules = SeatRuleList.defaultList(Rotation.CLOCKWISE_90);

    /** 每个水平侧面累计转过的角度（旧版 {@code degreesAwayFromBase}）。 */
    private final float[] degreesAwayFromBase = new float[4];
    private final float[] lastModifier = new float[4];
    private Set<InputKey> pressedKeys = EnumSet.noneOf(InputKey.class);

    public CommandSeatBlockEntity(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    // ==================== 规则 ====================

    public SeatRuleList getList(final Rotation rotation) {
        return switch (rotation) {
            case NONE -> this.forwardRules;
            case CLOCKWISE_90 -> this.rightRules;
            case CLOCKWISE_180 -> this.backwardRules;
            case COUNTERCLOCKWISE_90 -> this.leftRules;
        };
    }

    /** 侧面 → 规则表：以方块朝向为"前"，顺时针数出右 / 后 / 左（旧版 {@code MinecraftUtil.between}）。 */
    public SeatRuleList getList(final Direction face) {
        final Direction forward = this.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        if (face == forward) {
            return this.forwardRules;
        }
        if (face == forward.getClockWise()) {
            return this.rightRules;
        }
        if (face == forward.getOpposite()) {
            return this.backwardRules;
        }
        return this.leftRules;
    }

    public Set<InputKey> pressedKeys() {
        return this.pressedKeys;
    }

    /** 由网络包调用：玩家在座椅上按下的键变了。 */
    public void updateInput(final Set<InputKey> keys) {
        if (this.pressedKeys.equals(keys)) {
            return;
        }
        this.pressedKeys = keys;
    }

    /** 由界面网络包调用：整体替换四张规则表。 */
    public void updateRules(final SeatRuleList forward, final SeatRuleList backward,
                            final SeatRuleList left, final SeatRuleList right) {
        this.forwardRules = forward;
        this.backwardRules = backward;
        this.leftRules = left;
        this.rightRules = right;
        this.setChanged();
        this.sendData();
        this.detachKinetics();
        this.attachKinetics();
    }

    public float getDegreesAwayFromBase(final Direction face) {
        return this.degreesAwayFromBase[face.ordinal() - 2];
    }

    // ==================== 传动 ====================

    @Override
    public float getRotationSpeedModifier(final Direction face) {
        if (this.isVirtual() || !this.hasSource()) {
            return 1.0f;
        }
        if (this.getSourceFacing() != Direction.DOWN) {
            return 0.0f;   // 只有"从下方进动力"时才当变速箱用（旧版行为）
        }
        if (face == this.getSourceFacing()) {
            return 1.0f;
        }
        return this.getList(face).currentModifier(this, face);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        for (int i = 0; i < 4; i++) {
            final Direction face = Direction.values()[i + 2];
            final float modifier = this.getRotationSpeedModifier(face);
            this.degreesAwayFromBase[i] += KineticBlockEntity.convertToAngular(modifier * this.getSpeed());
            if (modifier != this.lastModifier[i]) {
                // 倍率变了必须让动力网络重算（旧版同样 detach/attach）
                this.detachKinetics();
                this.attachKinetics();
                this.lastModifier[i] = modifier;
            }
            if (this.degreesAwayFromBase[i] > 360.0f) {
                this.degreesAwayFromBase[i] -= 360.0f;
            } else if (this.degreesAwayFromBase[i] < 0.0f) {
                this.degreesAwayFromBase[i] += 360.0f;
            }
        }
    }

    // ==================== 持久化 ====================

    @Override
    protected void write(final CompoundTag tag, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("ForwardRules", this.forwardRules.serializeNBT());
        tag.put("BackwardRules", this.backwardRules.serializeNBT());
        tag.put("LeftRules", this.leftRules.serializeNBT());
        tag.put("RightRules", this.rightRules.serializeNBT());
    }

    @Override
    protected void read(final CompoundTag tag, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        readList(tag, "ForwardRules", this.forwardRules);
        readList(tag, "BackwardRules", this.backwardRules);
        readList(tag, "LeftRules", this.leftRules);
        readList(tag, "RightRules", this.rightRules);
    }

    private static void readList(final CompoundTag tag, final String key, final SeatRuleList target) {
        final ListTag list = tag.getList(key, CompoundTag.TAG_COMPOUND);
        if (!list.isEmpty()) {
            target.deserializeNBT(list);
        }
    }
}
