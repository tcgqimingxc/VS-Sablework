package org.valkyrienskies.clockwork.content.contraptions.sugar_rocket;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.valkyrienskies.clockwork.config.ClockworkConfig;
import org.valkyrienskies.clockwork.platform.CWForces;

import java.util.List;

/**
 * 糖火箭方块实体。
 *
 * <p>对应旧版 {@code content/propulsion/sugar_rocket/SugarRocketBlockEntity} +
 * {@code content/forces/SugarRocketController}。
 *
 * <p><b>施力链路</b>：旧版把 {@code SugarRocketData} 注册到飞船上的
 * {@code SugarRocketController}，由控制器在 physTick 里施力；
 * 移植后实现 Sable 的 {@link BlockEntitySubLevelActor}，
 * 在 {@code sable$physicsTick} 里按"剩余燃烧时间 × 推力档"给
 * {@code ForceGroups.PROPULSION} 记一笔点力（这是 Sable 唯一合法的物理子步施力入口）。
 *
 * <p><b>渲染用的燃烧进度</b>：旧版客户端每刻都收到 {@code sendData()}（服务端 {@code burn()} 末尾），
 * 用 {@code LerpedFloat clientBurnProgress} 把模型由白渐变到黑。本移植<b>不再每刻发包</b>：
 * 点火时把总燃烧刻数 {@code burnTotal} 随方块实体数据同步一次，客户端据此自行累计
 * {@code clientBurnTicks}，渲染器读 {@link #getBurnProgress(float)} 得到 0..1 的进度。
 *
 * <p>与旧版差异：
 * <ul>
 *   <li>旧版颗粒/音效在客户端 tick 里；本切片只保留燃烧状态与渲染进度（颗粒未移植）。</li>
 *   <li>燃料上限、单档推力接配置（旧版是硬编码 {@code burnTime += 20 * amount}）。</li>
 * </ul>
 */
public class SugarRocketBlockEntity extends SmartBlockEntity implements BlockEntitySubLevelActor {

    /** 每次加糖增加的燃烧时间 [刻]（旧版 20）。 */
    private static final int TICKS_PER_SUGAR = 20;

    private int burnTime;
    private int burnPower;
    private boolean burning;
    /** 本次燃烧的总刻数（点火时定格，随方块实体数据同步给客户端，用于推算燃烧进度）。 */
    private int burnTotal;

    /** 客户端燃烧进度插值器（0 = 刚点火，1 = 烧完）；只在客户端 tick 里推进。 */
    private final LerpedFloat clientBurnProgress =
            LerpedFloat.linear().startWithValue(0.0).chase(0.0, 0.05, LerpedFloat.Chaser.LINEAR);
    /** 客户端已燃烧刻数（仅客户端维护，服务端恒为 0）。 */
    private int clientBurnTicks;

    public SugarRocketBlockEntity(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(final List<BlockEntityBehaviour> behaviours) {
        // 无行为（旧版 addBehaviours 同样为空）
    }

    /** 加糖：+1 秒燃烧、+1 推力档（受配置上限约束）。 */
    public void addSugar(final int amount) {
        if (this.burning) {
            return;
        }
        final int maxPower = ClockworkConfig.server().sugarRocketMaxPower.get();
        this.burnTime += TICKS_PER_SUGAR * amount;
        this.burnPower = Math.min(maxPower, this.burnPower + amount);
        this.sendData();
    }

    /** 红石点火（对标旧版 {@code induceIgnition}）。 */
    public void induceIgnition() {
        if (this.burning || this.burnTime <= 0) {
            return;
        }
        this.burning = true;
        // 定格总燃烧时长：客户端靠它把"已烧刻数"换算成 0..1 的进度，不必每刻发包
        this.burnTotal = this.burnTime;
        this.sendData();
    }

    public boolean isBurning() {
        return this.burning;
    }

    public int getBurnPower() {
        return this.burnPower;
    }

    /** 燃烧进度 0..1（供渲染器把方块模型由白渐变到黑；服务端调用返回 0）。 */
    public float getBurnProgress(final float partialTicks) {
        return this.clientBurnProgress.getValue(partialTicks);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level == null) {
            return;
        }
        if (this.level.isClientSide) {
            this.tickClientProgress();
            return;
        }
        if (!this.burning) {
            return;
        }
        if (--this.burnTime <= 0) {
            this.burning = false;
            this.burnTime = 0;
            this.burnPower = 0;
            this.burnTotal = 0;
            this.sendData();
        }
    }

    /** 客户端：按"已烧刻数 / 总刻数"推进插值器；熄火后回到 0（旧版由服务端每刻发包驱动）。 */
    private void tickClientProgress() {
        this.clientBurnProgress.tickChaser();
        if (!this.burning) {
            if (this.clientBurnTicks != 0) {
                this.clientBurnTicks = 0;
                this.clientBurnProgress.updateChaseTarget(0.0f);
            }
            return;
        }
        this.clientBurnTicks++;
        if (this.burnTotal > 0) {
            this.clientBurnProgress.updateChaseTarget(
                    Mth.clamp(this.clientBurnTicks / (float) this.burnTotal, 0.0f, 1.0f));
        }
    }

    @Override
    public void sable$physicsTick(final ServerSubLevel subLevel, final RigidBodyHandle handle, final double timeStep) {
        if (!this.burning || this.burnPower <= 0) {
            return;
        }
        // 旧版：realDirection = FACING.opposite
        final Direction direction = this.getBlockState().getValue(SugarRocketBlock.FACING).getOpposite();
        final Vector3d thrust = new Vector3d(direction.getStepX(), direction.getStepY(), direction.getStepZ())
                .mul(this.burnPower * ClockworkConfig.server().sugarRocketThrust.get());
        final Vector3d position = new Vector3d(this.getBlockPos().getX() + 0.5,
                this.getBlockPos().getY() + 0.5, this.getBlockPos().getZ() + 0.5);
        CWForces.pointForce(subLevel, CWForces.propulsion(), position, thrust, timeStep);
    }

    @Override
    protected void write(final CompoundTag compound, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putInt("BurnTime", this.burnTime);
        compound.putInt("BurnPower", this.burnPower);
        compound.putInt("BurnTotal", this.burnTotal);
        compound.putBoolean("Burning", this.burning);
    }

    @Override
    protected void read(final CompoundTag compound, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        final boolean wasBurning = this.burning;
        this.burnTime = compound.getInt("BurnTime");
        this.burnPower = compound.getInt("BurnPower");
        this.burnTotal = compound.getInt("BurnTotal");
        this.burning = compound.getBoolean("Burning");
        if (clientPacket && this.burning && !wasBurning) {
            // 新一次点火：进度从 0 重新开始（旧版这里是 updateChaseTarget(0) + 一发 FLASH 颗粒）
            this.clientBurnTicks = 0;
            this.clientBurnProgress.setValue(0.0);
        }
    }
}
