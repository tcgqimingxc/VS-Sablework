package org.valkyrienskies.clockwork.content.curiosities.clock;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.clockwork.ClockworkMod;

import java.util.List;

/**
 * 落地钟方块实体。
 *
 * <p>对应旧版 {@code curiosities/clock/ClockBlockEntity.kt}：按 {@code level.dayTime} 计算时/分/秒针角度，
 * 秒针每走过 12° 播放一次滴答声；另有一个"预告片动画"（指针快速转到 390° 并播放主题曲）。
 *
 * <p>移植差异：
 * <ul>
 *   <li>旧版只在服务端 {@code calcHandRotation()}，而渲染器在客户端读取同样的字段，
 *       客户端字段永远是 0 —— 也就是说旧版的指针实际不会走。这里改成两端都计算
 *       （{@code dayTime} 本来就是同步的），因此指针现在真的会动。</li>
 *   <li>旧版用 {@code ClockworkSounds.TICK / TOCK / CLOCK_SONG}（注册在 Lead 的 {@code index/CWSounds} 里，
 *       本切片不能新增）。这里用 {@link SoundEvent#createVariableRangeEvent} 直接引用
 *       {@code assets/vs_clockwork/sounds.json} 中已存在的同名音效，声音资源与玩家听感一致；
 *       Lead 注册 CWSounds 后可一行替换。</li>
 * </ul>
 */
public class ClockBlockEntity extends SmartBlockEntity {

    private static final SoundEvent TICK_SOUND = sound("tick");
    private static final SoundEvent TOCK_SOUND = sound("tock");
    private static final SoundEvent CLOCK_SONG = sound("clock_song");

    /** 1 小时 = 2000 tick（旧版常量）。 */
    private static final double TICKS_PER_HOUR = 2000.0;
    private static final double TICKS_PER_MINUTE = 2000.0 / 60.0;
    private static final double TICKS_PER_SECOND = 2000.0 / 3600.0;

    private float secondHandAngle;
    private float minuteHandAngle;
    private float hourHandAngle;

    private boolean tickTock;
    private boolean doingTrailerAnim;
    private float trailerAnimProgress = -1.0f;
    private boolean startedSong;

    /** 渲染器用的累计 tick（用于平滑插值），旧版没有，但渲染需要。 */
    public int tickCount;

    public ClockBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    private static SoundEvent sound(String path) {
        return SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, path));
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // 旧版同样为空实现。
    }

    public float getSecondHandAngle(float partialTicks) {
        return secondHandAngle;
    }

    public float getMinuteHandAngle(float partialTicks) {
        return minuteHandAngle;
    }

    public float getHourHandAngle(float partialTicks) {
        return hourHandAngle;
    }

    public boolean isDoingTrailerAnim() {
        return doingTrailerAnim;
    }

    /** 旧版 {@code triggerTrailerAnim}：让指针猛转一圈并播放主题曲（创造模式/彩蛋用）。 */
    public void triggerTrailerAnim() {
        doingTrailerAnim = true;
        trailerAnimProgress = 0.0f;
        startedSong = false;
    }

    @Override
    public void tick() {
        super.tick();
        tickCount++;

        if (level == null) {
            return;
        }

        if (doingTrailerAnim) {
            trailerAnimProgress = Math.min(1.0f, trailerAnimProgress + 0.01f);
            secondHandAngle = (float) Math.toRadians(trailerAnimProgress * 390.0);
            if (!level.isClientSide && !startedSong) {
                startedSong = true;
                level.playSound(null, worldPosition, CLOCK_SONG, SoundSource.MUSIC, 1.0f, 1.0f);
            }
            return;
        }

        long time = level.dayTime();
        double hours = time / TICKS_PER_HOUR;
        double minutes = time / TICKS_PER_MINUTE;
        double seconds = time / TICKS_PER_SECOND;

        secondHandAngle = (float) Math.toRadians(seconds % 60.0 * 6.0);
        minuteHandAngle = (float) Math.toRadians(minutes % 60.0 * 6.0);
        hourHandAngle = (float) Math.toRadians(hours % 12.0 * 30.0);

        if (!level.isClientSide && Math.abs(Math.toDegrees(secondHandAngle) % 12.0) < 0.6) {
            tickTock = !tickTock;
            level.playSound(null, worldPosition, tickTock ? TICK_SOUND : TOCK_SOUND,
                    SoundSource.BLOCKS, 0.5f, 1.0f);
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean("TickTock", tickTock);
        tag.putBoolean("DoingTrailerAnim", doingTrailerAnim);
        tag.putFloat("TrailerAnimProgress", trailerAnimProgress);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        tickTock = tag.getBoolean("TickTock");
        doingTrailerAnim = tag.getBoolean("DoingTrailerAnim");
        trailerAnimProgress = tag.getFloat("TrailerAnimProgress");
    }
}
