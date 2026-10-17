package org.valkyrienskies.clockwork.platform;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Sable 子空间（SubLevel）查询的薄封装。
 *
 * <p>对应旧版 VS2 的 {@code level.getShipManagingPos(pos)} / {@code getShipObjectManagingPos(pos)} 一族；
 * 旧版那些 API 在 1.21.1 + Sable 下全部不存在，这里统一收敛到一个类，避免各处直接散落 Sable 调用。
 *
 * <p>所有方法在"没有子空间 / 不是服务端"时都返回 {@code null} 或安全默认值，
 * 调用方不需要自己判空 {@code level instanceof ServerLevel}。
 */
public final class CWSubLevels {

    private CWSubLevels() {
    }

    /** 该方块所在（或世界）的子空间；不在任何子空间里则返回 null。 */
    public static @Nullable SubLevel containing(final Level level, final BlockPos pos) {
        return Sable.HELPER.getContaining(level, pos);
    }

    /** 同 {@link #containing}，但只在服务端返回 {@link ServerSubLevel}。 */
    public static @Nullable ServerSubLevel containingServer(final Level level, final BlockPos pos) {
        return containing(level, pos) instanceof final ServerSubLevel server ? server : null;
    }

    /** 按 UUID 取子空间（装配后持久化下来的是 UUID 而不是对象）。 */
    public static @Nullable SubLevel byId(final Level level, final @Nullable UUID id) {
        if (id == null) {
            return null;
        }
        return SubLevelContainer.getContainer(level).getSubLevel(id);
    }

    /** 服务端物理系统；客户端 / 未初始化时返回 null。 */
    public static @Nullable SubLevelPhysicsSystem physics(final Level level) {
        return SubLevelPhysicsSystem.get(level);
    }

    /**
     * 取物理管线（施加约束 / 唤醒刚体都走它）。
     *
     * <p>容器 / 物理系统还没装好时 {@code physicsSystem()} 会 NPE（它内部只有 {@code assert}，
     * 生产环境是关掉的）—— 这里统一吞成 {@code null}：拿不到管线 = 这一帧什么都不做，
     * 绝不让异常冒到调用方的方块实体 tick 里（2026-10-16 的崩溃就是这么进服务端 tick 的）。
     *
     * @return 非服务端或管线不可用时返回 null
     */
    public static @Nullable PhysicsPipeline pipeline(final Level level) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return null;
        }
        try {
            final ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
            return container == null ? null : container.physicsSystem().getPipeline();
        } catch (final RuntimeException e) {
            Sable.LOGGER.debug("[CWSubLevels] 取物理管线失败：{}", e.toString());
            return null;
        }
    }

    /**
     * 唤醒子空间刚体：施加约束/速度前必须先唤醒，否则休眠中的刚体不会响应。
     *
     * <p>调用方交给这里的可能是"本刻刚被标记移除、还没从容器里摘掉"的子空间：
     * {@code Sable.HELPER.getContaining} 不过滤 {@code isRemoved}
     * （{@code ActiveSableCompanion.java:67-79} 只按 plot 查），而
     * {@code RapierPhysicsPipeline#wakeUp} 的 {@code assertBodyValid} 会抛
     * {@code "Body has been removed"}。唤醒一个已经没了的刚体没有任何意义，吞掉即可；
     * 这条路径每刻都会被轴承（{@code advanceTargetAngle}）调到，所以只记 debug。
     */
    public static void wakeUp(final Level level, final @Nullable SubLevel subLevel) {
        if (!(subLevel instanceof final ServerSubLevel server) || server.isRemoved()) {
            return;
        }
        final PhysicsPipeline pipeline = pipeline(level);
        if (pipeline != null) {
            try {
                pipeline.wakeUp(server);
            } catch (final RuntimeException e) {
                Sable.LOGGER.debug("[CWSubLevels] 唤醒刚体失败：{}", e.toString());
            }
        }
    }

    /** 物理插值系数（用于伺服目标角平滑）；拿不到物理系统时返回 0。 */
    public static double partialPhysicsTick(final Level level) {
        final SubLevelPhysicsSystem physics = physics(level);
        return physics == null ? 0.0 : physics.getPartialPhysicsTick();
    }

    /** 物理是否被暂停（暂停时不应推进轴承目标角，对标 SwivelBearingBlockEntity#tick）。 */
    public static boolean paused(final Level level) {
        final SubLevelPhysicsSystem physics = physics(level);
        return physics != null && physics.getPaused();
    }
}
