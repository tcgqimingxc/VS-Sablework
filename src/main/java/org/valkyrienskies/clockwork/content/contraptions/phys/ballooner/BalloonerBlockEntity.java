package org.valkyrienskies.clockwork.content.contraptions.phys.ballooner;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.valkyrienskies.clockwork.config.ClockworkConfig;
import org.valkyrienskies.clockwork.platform.CWForces;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 热气球浮力方块实体。
 *
 * <p>对应旧版 {@code content/forces/BalloonController} + {@code content/forces/data/BalloonData}
 * （旧版是挂在飞船上的附件，靠射线穿外壳找"封口"，再用气体系统里的气体量与温度算浮力）。
 *
 * <p><b>移植后的模型</b>（因为气体物流属于另一个切片，这里不能依赖它）：
 * <ol>
 *   <li>每 {@value #SCAN_INTERVAL} 刻从自身相邻的空气格做一次有界洪水填充，
 *       只穿过空气；</li>
 *   <li>填充量超过 {@code ballooner.maxScanVolume} 就判定"没封口"（漏气），浮力归零 ——
 *       这与旧版射线找不到封口时的结果一致，代价是要限制扫描量；</li>
 *   <li>密封成功则记录体积与形心，浮力 = 体积 × {@code ballooner.liftPerBlock} × 倍率，
 *       沿世界 +Y，在形心处施加（等价于旧版 BalloonData 的"合力作用于浮心"）。</li>
 * </ol>
 *
 * <p><b>与旧版差异</b>：旧版浮力随气体温度/压力变化，能通过加热/充气调节；
 * 这里是与气体无关的固定浮力（等气体切片接入后，把 {@link #liftMagnitude()} 换成读气体即可）。
 */
public class BalloonerBlockEntity extends SmartBlockEntity implements BlockEntitySubLevelActor {

    /** 体积扫描间隔 [刻]：洪水填充不便宜，节流到每秒一次。 */
    private static final int SCAN_INTERVAL = 20;

    private int volume;
    private boolean sealed;
    private final Vector3d centroid = new Vector3d();
    private int scanTimer;

    public BalloonerBlockEntity(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(final List<BlockEntityBehaviour> behaviours) {
        // 无行为
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        if (++this.scanTimer < SCAN_INTERVAL) {
            return;
        }
        this.scanTimer = 0;
        this.scanBalloon();
    }

    /** 受配置上限约束的洪水填充。 */
    private void scanBalloon() {
        this.volume = 0;
        this.sealed = false;
        this.centroid.zero();

        final int maxVolume = ClockworkConfig.server().balloonerMaxScanVolume.get();
        final Deque<BlockPos> frontier = new ArrayDeque<>();
        final Set<BlockPos> visited = new HashSet<>();

        for (final Direction direction : Direction.values()) {
            final BlockPos start = this.getBlockPos().relative(direction);
            if (this.level.getBlockState(start).isAir()) {
                frontier.add(start);
            }
        }
        if (frontier.isEmpty()) {
            return;
        }

        long sumX = 0;
        long sumY = 0;
        long sumZ = 0;
        while (!frontier.isEmpty()) {
            final BlockPos pos = frontier.poll();
            if (visited.contains(pos)) {
                continue;
            }
            if (!this.level.getBlockState(pos).isAir()) {
                // 外壳：体积只统计内部空气，不把外壳算进去
                continue;
            }
            visited.add(pos);
            if (visited.size() > maxVolume) {
                // 漏气：填充跑出去了
                this.sealed = false;
                this.volume = 0;
                return;
            }
            sumX += pos.getX();
            sumY += pos.getY();
            sumZ += pos.getZ();
            for (final Direction direction : Direction.values()) {
                final BlockPos next = pos.relative(direction);
                if (!visited.contains(next)) {
                    frontier.add(next);
                }
            }
        }

        this.volume = visited.size();
        this.sealed = this.volume > 0;
        if (this.sealed) {
            this.centroid.set(sumX / (double) this.volume, sumY / (double) this.volume, sumZ / (double) this.volume);
        }
    }

    private double liftMagnitude() {
        return this.volume * ClockworkConfig.server().balloonerLiftPerBlock.get()
                * ClockworkConfig.server().balloonForceMultiplier.get();
    }

    @Override
    public void sable$physicsTick(final ServerSubLevel subLevel, final RigidBodyHandle handle, final double timeStep) {
        if (!this.sealed || this.volume <= 0) {
            return;
        }
        final double lift = this.liftMagnitude();
        if (lift <= 0.0) {
            return;
        }
        // 浮力沿世界 +Y；力分组用 local 系，所以把世界上方向逆变换到子空间 local 系
        final Vector3d localUp = subLevel.logicalPose().orientation()
                .transformInverse(new Vector3d(0.0, 1.0, 0.0), new Vector3d());
        localUp.mul(lift);
        CWForces.pointForce(subLevel, CWForces.balloonLift(), new Vector3d(this.centroid), localUp, timeStep);
    }

    @Override
    protected void write(final CompoundTag compound, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putInt("Volume", this.volume);
        compound.putBoolean("Sealed", this.sealed);
        compound.putDouble("CentroidX", this.centroid.x);
        compound.putDouble("CentroidY", this.centroid.y);
        compound.putDouble("CentroidZ", this.centroid.z);
    }

    @Override
    protected void read(final CompoundTag compound, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        this.volume = compound.getInt("Volume");
        this.sealed = compound.getBoolean("Sealed");
        this.centroid.set(compound.getDouble("CentroidX"), compound.getDouble("CentroidY"),
                compound.getDouble("CentroidZ"));
    }
}
