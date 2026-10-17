package org.valkyrienskies.clockwork.content.contraptions.phys.bearing;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 物理轴承方块实体。
 *
 * <p>对应旧版 {@code content/contraptions/phys/bearing/PhysBearingBlockEntity}（724 行）。
 * 装配 / 解体 / 约束 / 持久化全部在 {@link AbstractBearingBlockEntity}，这里只负责：
 * <ul>
 *   <li>旋转模式（跟随输入轴 / 锁定角度 / 自由铰链）与对应的 {@link BearingControl}；</li>
 *   <li>模式切换时的角度初始化（避免切到锁定模式时突然甩一下）。</li>
 * </ul>
 *
 * <p>与旧版的差异：
 * <ul>
 *   <li>旧版的 {@code FOLLOW_ANGLE} 模式（跟随另一艘船的相对角度）依赖 VS2 的固定关节插值，
 *       在 Sable 上需要一整套关节姿态插值，暂未移植；换成"锁定角度"模式。</li>
 *   <li>旧版扳手可以转动已装配的物理轴承（受配置开关限制）；这里扳手会先解体再旋转，更安全。</li>
 * </ul>
 */
public class PhysBearingBlockEntity extends AbstractBearingBlockEntity {

    private static final MutableComponent MODE_TITLE =
            Component.translatable("vs_clockwork.phys_bearing.rotation_mode");

    /** 旋转模式（对应旧版 {@code ContraptionController.LockedMode}）。 */
    public enum RotationMode implements INamedIconOptions {
        /** 跟随输入轴旋转（机械轴承手感，默认）。 */
        ROTATE(AllIcons.I_REFRESH, "vs_clockwork.phys_bearing.mode.rotate"),
        /** 锁定角度：被外部力矩推走会自己回到目标角。 */
        LOCK(AllIcons.I_CONFIG_LOCKED, "vs_clockwork.phys_bearing.mode.lock"),
        /** 自由铰链：只提供摩擦，和旧版未锁定状态一致。 */
        FREE(AllIcons.I_CONFIG_UNLOCKED, "vs_clockwork.phys_bearing.mode.free");

        private final AllIcons icon;
        private final String translationKey;

        RotationMode(final AllIcons icon, final String translationKey) {
            this.icon = icon;
            this.translationKey = translationKey;
        }

        @Override
        public AllIcons getIcon() {
            return this.icon;
        }

        @Override
        public String getTranslationKey() {
            return this.translationKey;
        }
    }

    private final BearingControl continuousControl = new BearingControls.Continuous();
    private final BearingControl lockedControl = new BearingControls.Locked();
    private final BearingControl freeControl = new BearingControls.Free();
    private ScrollOptionBehaviour<RotationMode> modeBehaviour;
    private RotationMode lastMode = RotationMode.ROTATE;

    /**
     * 四片翼面的开合动画（纯客户端状态机）。
     *
     * <p><b>单一真相源</b>：目标状态只来自服务端同步的 {@link #isAssembled()}。
     * 这里不再保存 {@code wingsOpened}/{@code wingsOpening} 这类"第二份真相"——
     * 上一版正是因为它们在解体后卡在 {@code wingsOpened=true && progress=0.95}，
     * 才导致"只闭合一大半、再次装配不播放打开动画"（推演见 {@link WingAnimation} 的类注释）。
     */
    private final WingAnimation wingAnimation = new WingAnimation();

    public PhysBearingBlockEntity(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    /**
     * 客户端每刻把服务端同步的装配状态喂给翼面状态机；服务端照旧走 {@link #onServerTick()}。
     *
     * <p>客户端 tick 确实会跑：{@code IBE#getTicker} 的默认实现无条件返回
     * {@code SmartBlockEntityTicker}，而它的 {@code tick} 直接调用 {@code SmartBlockEntity#tick()}
     * （两者都已用 {@code javap -c} 核实，没有 {@code isClientSide} 判断）；
     * {@code KineticBlockEntity#tick()} 在客户端也会先执行 {@code SmartBlockEntity#tick()} 再返回。
     *
     * <p>动画进度<b>只</b>在客户端推进（字段本身就是客户端内存状态，不写入 NBT）；
     * 服务端只负责 {@code isAssembled()} 这一份同步状态，因此不存在两端各算一份、互相覆盖的问题。
     */
    @Override
    public void tick() {
        super.tick();
        if (this.level != null && this.level.isClientSide) {
            this.wingAnimation.tick(this.isAssembled());
        }
    }

    /**
     * 翼面当前相对静止姿态的角度 [度]，供 {@code PhysBearingRenderer} 使用。
     *
     * <p>角度 = 进度 × {@link WingAnimation#OPEN_DEGREES}：完全张开 70°，完全闭合精确 0°。
     * 无参版本返回的是"本 tick 已推进到的值"，渲染时优先用带 partialTick 的重载。
     */
    public float getWingAngleDegrees() {
        return this.wingAnimation.getAngleDegrees();
    }

    /**
     * 渲染用角度：在上一 tick 与当前 tick 之间按 {@code partialTicks} 插值，避免固定步长带来的阶梯感。
     *
     * @param partialTicks 渲染帧在一 tick 内的插值系数 [0,1]
     */
    public float getWingAngleDegrees(final float partialTicks) {
        return this.wingAnimation.getAngleDegrees(partialTicks);
    }

    @Override
    public void addBehaviours(final List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        this.modeBehaviour = new ScrollOptionBehaviour<>(RotationMode.class, MODE_TITLE, this,
                new ModeValueBox());
        behaviours.add(this.modeBehaviour);
    }

    @Override
    protected Direction getFacing() {
        return this.getBlockState().getValue(PhysBearingBlock.FACING);
    }

    @Override
    protected BearingControl getControl() {
        final RotationMode mode = this.modeBehaviour == null ? RotationMode.ROTATE : this.modeBehaviour.get();
        return switch (mode) {
            case ROTATE -> this.continuousControl;
            case LOCK -> this.lockedControl;
            case FREE -> this.freeControl;
        };
    }

    /**
     * 当前旋转模式。
     *
     * <p>本方法由 CC:Tweaked 集成切片追加：外设需要读写模式（旧版对应的 Lua 方法是
     * {@code setFollowAngleMode} / {@code setUnlockedMode} / {@code isInFollowAngleMode}）。
     * 旧版的 FOLLOW_ANGLE 在 Sable 上未移植（见类注释），最接近的对应关系是
     * {@code ROTATE ↔ UNLOCKED}、{@code LOCK ↔ LOCKED}。
     */
    public RotationMode getRotationMode() {
        return this.modeBehaviour == null ? RotationMode.ROTATE : this.modeBehaviour.get();
    }

    /** 切换旋转模式（供 CC 外设调用）；值由 Create 的 {@code ScrollOptionBehaviour} 落盘。 */
    public void setRotationMode(final RotationMode mode) {
        if (this.modeBehaviour == null) {
            return;                            // behaviour 尚未就绪（理论上只会发生在构造/反序列化早期）
        }
        this.modeBehaviour.setValue(mode.ordinal());
        this.setChanged();
        this.sendData();
    }

    @Override
    protected void onServerTick() {
        final RotationMode mode = this.modeBehaviour == null ? RotationMode.ROTATE : this.modeBehaviour.get();
        if (mode != this.lastMode) {
            // 切进锁定模式时把目标角固定成当前角度，避免子结构被猛地拽回去
            if (mode == RotationMode.LOCK) {
                this.setTargetAngleDegrees(this.getTargetAngleDegrees());
            }
            this.lastMode = mode;
            this.sendData();
        }
    }

    /**
     * 模式选择框放在与输入轴垂直的侧面上（照抄 SwivelBearing 的 SelectionModeValueBox）。
     *
     * <p>模式值本身由 Create 的 {@code ScrollOptionBehaviour} 自动读写 NBT，这里不需要再存一份。
     */
    private static class ModeValueBox extends CenteredSideValueBoxTransform {

        ModeValueBox() {
            super((state, direction) -> direction.getAxis() != state.getValue(PhysBearingBlock.FACING).getAxis());
        }

        @Override
        protected net.minecraft.world.phys.Vec3 getSouthLocation() {
            return net.createmod.catnip.math.VecHelper.voxelSpace(8, 8, 15.75);
        }

        @Override
        public float getScale() {
            return 0.35f;
        }
    }
}
