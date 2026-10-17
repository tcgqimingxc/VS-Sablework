package org.valkyrienskies.clockwork.content.kinetics.resistor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.utility.CreateLang;
import java.util.List;
import java.util.Locale;

import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.TickPriority;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 红石电阻的方块实体：按红石强度缩放输出转速，并让玩家用滚轮选择"衰减方向"。
 *
 * <p>对应旧版 {@code content.kinetics.resistor.RedstoneResistorBlockEntity}。
 *
 * <p>玩法（与旧版一致）：16 级红石分成 10% 一档。{@code NONE_TO_MAX} 时信号越强输出越慢，
 * {@code MAX_TO_NONE} 时反过来，倍率会在 0.0 / 0.1 … 1.0 之间取整到一位小数。
 *
 * <p>与旧版的差异：
 * <ul>
 *   <li>1.21 的存档读写多了 {@code HolderLookup.Provider} 参数（Create 6 的签名变化）。</li>
 *   <li>滚轮选项的旋转原来用 Flywheel 的 {@code TransformStack}，这里直接用 JOML 的
 *       {@code mulPose} 实现同样的 90°/180° 补偿，少一层依赖。</li>
 *   <li>护目镜提示除了红石强度，还会带上父类（转速 / 应力）的信息，信息量比旧版多。</li>
 * </ul>
 */
public class RedstoneResistorBlockEntity extends SplitShaftBlockEntity {

    /** 当前红石信号强度 0..15。 */
    private int signal;
    /** 上一刻的强度，用于判断"需要重新接入动力学网络"。 */
    private int lastSignal;
    /** 滚轮行为：衰减方向。在 {@link #addBehaviours} 里创建。 */
    private ScrollOptionBehaviour<ResistDirection> resistDirection;

    public RedstoneResistorBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type,
                                       BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public int getSignal() {
        return signal;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        resistDirection = new ScrollOptionBehaviour<>(ResistDirection.class,
                Component.translatable(ClockworkMod.MOD_ID + ".contraptions.resistor.resist_direction"),
                this, new ResistorValueBox());
        behaviours.add(resistDirection);
    }

    @Override
    public void tick() {
        super.tick();
        lastSignal = signal;
        signal = readRedstonePower();
        if (signal != lastSignal) {
            detachKinetics();
        }
    }

    /**
     * 断开网络并排队在下个调度刻重新接入。
     *
     * <p>强度一变就必须重新传播一次，否则下游方块会继续沿用旧倍率算出来的转速。
     */
    @Override
    public void detachKinetics() {
        super.detachKinetics();
        if (level != null) {
            level.scheduleTick(worldPosition, getBlockState().getBlock(), 0, TickPriority.EXTREMELY_HIGH);
        }
    }

    /** 取六个方向（以及向上信号）里的最大红石强度，与旧版一致。 */
    private int readRedstonePower() {
        Level level = getLevel();
        if (level == null) {
            return 0;
        }
        int power = 0;
        for (Direction direction : Iterate.directions) {
            power = Math.max(power, level.getSignal(worldPosition.relative(direction), direction));
        }
        for (Direction direction : Iterate.directions) {
            power = Math.max(power, level.getSignal(worldPosition.relative(direction), Direction.UP));
        }
        return power;
    }

    @Override
    public float getRotationSpeedModifier(Direction face) {
        if (hasSource() && face != getSourceFacing() && resistDirection != null) {
            double ratio = resistDirection.get() == ResistDirection.MAX_TO_NONE
                    ? Math.abs(signal - 15) / 15.0
                    : Math.abs(signal) / 15.0;
            // 档位取整到 0.1，避免红石强度 1 级一档导致转速抖动
            return (float) (Math.round(ratio * 10) / 10.0);
        }
        return 1f;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        tooltip.add(CreateLang.translateDirect("tooltip.analogStrength", signal));
        return true;
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        compound.putInt("RedstoneLevel", signal);
        compound.putInt("LastRedstoneLevel", lastSignal);
        super.write(compound, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        signal = compound.getInt("RedstoneLevel");
        lastSignal = compound.getInt("LastRedstoneLevel");
        super.read(compound, registries, clientPacket);
    }

    /** 衰减方向：MAX_TO_NONE = 信号越强越慢，NONE_TO_MAX = 信号越强越快。 */
    public enum ResistDirection implements INamedIconOptions {
        MAX_TO_NONE(AllIcons.I_REFRESH),
        NONE_TO_MAX(AllIcons.I_ROTATE_CCW);

        private final AllIcons icon;
        private final String translationKey;

        ResistDirection(AllIcons icon) {
            this.icon = icon;
            this.translationKey = "resistor.resist_direction." + name().toLowerCase(Locale.ROOT);
        }

        @Override
        public AllIcons getIcon() {
            return icon;
        }

        @Override
        public String getTranslationKey() {
            return translationKey;
        }
    }

    /**
     * 滚轮选项的显示位置：贴在轴侧面（不含轴自身那两个面）的中央。
     *
     * <p>轴向为 X/Z 时需要额外把面板转正，与旧版 {@code ResistorValueBox} 的补偿一致。
     */
    public static class ResistorValueBox extends ValueBoxTransform.Sided {

        @Override
        protected Vec3 getSouthLocation() {
            return new Vec3(0.5, 0.5, 1.0);
        }

        @Override
        public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
            super.rotate(level, pos, state, ms);
            Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
            if (axis == Direction.Axis.Y || getSide() != Direction.UP) {
                return;
            }
            float horizontalAngle = axis == Direction.Axis.X ? 90f : 0f;
            ms.mulPose(new Quaternionf(new AxisAngle4f((float) Math.toRadians(-horizontalAngle + 180f), 0f, 0f, 1f)));
        }

        @Override
        protected boolean isSideActive(BlockState state, Direction direction) {
            return direction.getAxis() != state.getValue(BlockStateProperties.AXIS);
        }
    }
}
