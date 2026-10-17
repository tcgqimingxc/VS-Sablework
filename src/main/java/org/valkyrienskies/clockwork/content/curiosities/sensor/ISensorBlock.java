package org.valkyrienskies.clockwork.content.curiosities.sensor;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * 传感器契约。
 *
 * <p>对应旧版 {@code content/curiosities/sensor/ISensorBlock.kt}：传感器不持有 BlockEntity，
 * 而是每隔 {@link #SAMPLE_INTERVAL} tick 采样一次，把结果写进自身的 {@code power} 属性并驱动红石输出。
 *
 * <p>移植差异：旧版采样方法带一个 {@code java.util.Random} 参数（旧签名遗留），这里换成 Minecraft 的
 * {@link RandomSource}；返回值语义保持一致（绝对值是信号强度，符号仅被陀螺传感器使用）。
 */
public interface ISensorBlock {

    /** 与旧版一致：复用原版 {@code power} 属性（资源文件里的方块状态直接对得上）。 */
    IntegerProperty POWER = BlockStateProperties.POWER;

    /** 采样周期（tick）。旧版三个传感器的 {@code scheduleTick(pos, this, 2)} 都是 2。 */
    int SAMPLE_INTERVAL = 2;

    /**
     * 采样一次，返回 -15~15 的有符号信号强度（负号仅陀螺传感器有意义）。
     *
     * @param random 服务端随机源（旧版 DistanceSensor 用它做占位，保留参数以对齐旧签名）
     */
    int updatePower(BlockState state, ServerLevel level, BlockPos pos, RandomSource random);
}
