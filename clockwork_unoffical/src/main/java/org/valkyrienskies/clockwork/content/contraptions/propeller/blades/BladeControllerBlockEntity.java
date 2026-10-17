package org.valkyrienskies.clockwork.content.contraptions.propeller.blades;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 叶片控制器方块实体：存放最多 8 片叶片，并提供螺旋桨的公共桨距角。
 *
 * <p>对应旧版 {@code blades/BladeControllerBlockEntity.kt}（361 行）+
 * {@code AngleScrollValueBehaviour.kt} + {@code BladeControllerMovementBehaviour.kt}。
 *
 * <p><b>与旧版的差异</b>
 * <ul>
 *   <li>不实现 {@code Container}：旧版实现 {@code ISyncableStorage}（自研容器 + 自定义同步包），
 *       新架构下装叶片只走右键交互，用 {@link #insertBlade}/{@link #removeBlade} 加
 *       {@code SyncedBlockEntity#sendData()} 即可，不需要漏斗/管道接口（旧版那套接口也没人用）。</li>
 *   <li>旧版 {@code BladeControllerMovementBehaviour} 有两个职责：① 装配后（Create contraption 里）
 *       按"旋转增量"磨损叶片；② 在 contraption 渲染里画叶片。新架构下螺旋桨结构是 Sable 子空间，
 *       方块实体照常 tick、渲染由子空间自己负责，所以这个 MovementBehaviour 整体废弃：
 *       ② 直接消失；① 旧版默认就是关闭的（{@code bladeControllerUsesDurability = false}），
 *       本移植不实现（详见交付说明）。</li>
 *   <li>实现 {@link BlockEntitySubLevelActor} 不是为了物理 tick，而是让 {@code LevelPlot}
 *       把本实体登记进"块实体演员表"——螺旋桨轴承正是通过
 *       {@code LevelPlot#getBlockEntityActors()} 找到子空间里的所有叶片控制器
 *       （旧版是遍历 contraption 的方块表找 {@code BLADE_CONTROLLER}），这条绑定链路见
 *       {@code PropellerBearingBlockEntity#refreshBlades()}。</li>
 * </ul>
 */
public class BladeControllerBlockEntity extends SmartBlockEntity implements BlockEntitySubLevelActor {

    /** 叶片槽位数（旧版 {@code NonNullList.withSize(8)}）。 */
    public static final int MAX_BLADES = 8;
    /** 插/拔叶片的冷却（旧版硬编码 10 刻），防止一次右键连续触发。 */
    private static final int BLADE_COOLDOWN_TICKS = 10;

    private final NonNullList<ItemStack> blades = NonNullList.withSize(MAX_BLADES, ItemStack.EMPTY);
    private BladeAngleBehaviour angleBehaviour;
    private int cooldown;

    public BladeControllerBlockEntity(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    /**
     * 注意：本方法由 {@link SmartBlockEntity} 的<b>构造器</b>调用，
     * 此时子类字段尚未初始化，因此只能赋值、不能读已有字段。
     */
    @Override
    public void addBehaviours(final List<BlockEntityBehaviour> behaviours) {
        behaviours.add(this.angleBehaviour = new BladeAngleBehaviour(this));
    }

    // ==================== 桨距 ====================

    /** 公共桨距角 [度]，范围 [-90, 90]（旧版 {@code bladeAngle}）。 */
    public double pitchDegrees() {
        return this.angleBehaviour == null ? 0.0 : this.angleBehaviour.getValue();
    }

    /**
     * 外部改桨距（螺旋桨轴承的红石总距，对应旧版
     * {@code PropellerBearingBlockEntity#setNewBladeAngle}）。
     *
     * <p>走 behaviour 而不是自己存字段：滚轮选项自己负责 NBT 持久化与客户端同步，
     * 两边共用一份值才不会出现"扳手面板显示的角度和实际用的角度不一样"。
     */
    public void setPitchDegrees(final double degrees) {
        if (this.angleBehaviour == null) {
            return;
        }
        final int next = Mth.clamp((int) Math.round(degrees), -BladeAngleBehaviour.MAX_ANGLE, BladeAngleBehaviour.MAX_ANGLE);
        if (next == this.angleBehaviour.getValue()) {
            return;
        }
        // ScrollValueBehaviour#setValue 自己会 setChanged + sendData（值没变则直接返回）
        this.angleBehaviour.setValue(next);
    }

    // ==================== 叶片 ====================

    /** 当前叶片的当量气动参数；角度取当前桨距，因此不需要缓存失效逻辑。 */
    public List<BladeData> bladeData() {
        return BladeData.of(this.blades, this.pitchDegrees());
    }

    public int bladeCount() {
        int count = 0;
        for (final ItemStack stack : this.blades) {
            if (!stack.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    public boolean isOnCooldown() {
        return this.cooldown > 0;
    }

    /** 装入一片叶片（旧版 {@code insertBlade}）。 */
    public boolean insertBlade(final ItemStack blade) {
        if (this.cooldown > 0) {
            return false;
        }
        for (int i = 0; i < MAX_BLADES; i++) {
            if (this.blades.get(i).isEmpty()) {
                this.blades.set(i, blade);
                this.cooldown = BLADE_COOLDOWN_TICKS;
                this.setChanged();
                this.sendData();
                return true;
            }
        }
        return false;
    }

    /** 取出"编号最大的那片"叶片（旧版 {@code removeBlade}）；空则返回 {@link ItemStack#EMPTY}。 */
    public ItemStack removeBlade() {
        if (this.cooldown > 0) {
            return ItemStack.EMPTY;
        }
        for (int i = MAX_BLADES - 1; i >= 0; i--) {
            final ItemStack blade = this.blades.get(i);
            if (!blade.isEmpty()) {
                this.blades.set(i, ItemStack.EMPTY);
                this.cooldown = BLADE_COOLDOWN_TICKS;
                this.setChanged();
                this.sendData();
                return blade;
            }
        }
        return ItemStack.EMPTY;
    }

    // ==================== 生命周期 / 持久化 ====================

    @Override
    public void tick() {
        super.tick();
        if (this.cooldown > 0) {
            this.cooldown--;
        }
    }

    /** 控制器被破坏时把里面的叶片掉出来（旧版 {@code destroy() -> dropBlades()}）。 */
    @Override
    public void destroy() {
        if (this.level != null && !this.level.isClientSide) {
            for (final ItemStack blade : this.blades) {
                if (!blade.isEmpty()) {
                    Block.popResource(this.level, this.worldPosition, blade);
                }
            }
        }
        this.blades.clear();
        super.destroy();
    }

    @Override
    protected void write(final CompoundTag tag, final HolderLookup.Provider registries, final boolean clientPacket) {
        final CompoundTag bladesTag = new CompoundTag();
        ContainerHelper.saveAllItems(bladesTag, this.blades, registries);
        // 与旧版的 NBT 布局保持一致（"Blades" + "BladeCount"），旧存档/蓝图可直接沿用
        tag.put("Blades", bladesTag);
        tag.putInt("BladeCount", this.bladeCount());
        super.write(tag, registries, clientPacket);
    }

    @Override
    protected void read(final CompoundTag tag, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.blades.clear();
        ContainerHelper.loadAllItems(tag.getCompound("Blades"), this.blades, registries);
        this.cooldown = 0;
    }

    // ==================== 桨距滚轮 ====================

    /**
     * 桨距角滚轮选项（旧版 {@code AngleScrollValueBehaviour}）。
     *
     * <p>面板两行是 ⟳ / ⟲（负 / 正舵向），与旧版一致；{@code between(-90, 90)} 限制范围。
     */
    public static class BladeAngleBehaviour extends ScrollValueBehaviour {

        /** 桨距角上限 [度]（旧版 {@code between(-90, 90)}）。 */
        public static final int MAX_ANGLE = 90;

        private static final List<Component> ROWS = List.of(
                Component.literal("\u27f3").withStyle(ChatFormatting.BOLD),
                Component.literal("\u27f2").withStyle(ChatFormatting.BOLD));

        public BladeAngleBehaviour(final SmartBlockEntity blockEntity) {
            super(Component.translatable("vs_clockwork.blade_controller.angle"), blockEntity, new AngleValueBox());
            this.between(-MAX_ANGLE, MAX_ANGLE);
        }

        @Override
        public ValueSettingsBoard createBoard(final Player player, final BlockHitResult hitResult) {
            return new ValueSettingsBoard(this.label, MAX_ANGLE, 10, ROWS,
                    new ValueSettingsFormatter(this::formatSettings));
        }

        @Override
        public void setValueSettings(final Player player, final ValueSettings settings, final boolean ctrlHeld) {
            final int value = Math.max(0, settings.value());
            if (!settings.equals(this.getValueSettings())) {
                this.playFeedbackSound(this);
            }
            this.setValue(settings.row() == 0 ? -value : value);
        }

        @Override
        public ValueSettings getValueSettings() {
            return new ValueSettings(this.getValue() < 0 ? 0 : 1, Math.abs(this.getValue()));
        }

        @Override
        public String getClipboardKey() {
            return "BladeAngle";
        }

        private MutableComponent formatSettings(final ValueSettings settings) {
            return Component.literal(Math.max(1, Math.abs(settings.value())) + "\u00b0")
                    .append(Component.literal(settings.row() == 0 ? " \u27f3" : " \u27f2")
                            .withStyle(ChatFormatting.BOLD));
        }

        /** 值盒位置：贴在朝向那一面（旧版 {@code AngleControllerValueBoxTransform}）。 */
        private static class AngleValueBox extends ValueBoxTransform.Sided {

            @Override
            protected Vec3 getSouthLocation() {
                return VecHelper.voxelSpace(8.0, 8.0, 18.5);
            }

            @Override
            public Vec3 getLocalOffset(final LevelAccessor level, final BlockPos pos, final BlockState state) {
                return super.getLocalOffset(level, pos, state)
                        .add(Vec3.atLowerCornerOf(state.getValue(BladeControllerBlock.FACING).getNormal())
                                .scale(-2.0 / 16.0));
            }

            @Override
            public void rotate(final LevelAccessor level, final BlockPos pos, final BlockState state, final PoseStack ms) {
                if (!this.getSide().getAxis().isHorizontal()) {
                    ms.mulPose(Axis.YP.rotationDegrees(
                            AngleHelper.horizontalAngle(state.getValue(BladeControllerBlock.FACING)) + 180.0f));
                }
                super.rotate(level, pos, state, ms);
            }

            @Override
            protected boolean isSideActive(final BlockState state, final Direction direction) {
                return direction == state.getValue(BladeControllerBlock.FACING);
            }
        }
    }
}
