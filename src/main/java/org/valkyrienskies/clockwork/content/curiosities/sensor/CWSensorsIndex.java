package org.valkyrienskies.clockwork.content.curiosities.sensor;

import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import org.valkyrienskies.clockwork.ClockworkMod;
import org.valkyrienskies.clockwork.ClockworkRegistrate;
import org.valkyrienskies.clockwork.content.curiosities.sensor.distance.DistanceSensorBlock;
import org.valkyrienskies.clockwork.content.curiosities.sensor.impact.ImpactSensorBlock;
import org.valkyrienskies.clockwork.content.curiosities.sensor.rotation.GyroscopicSensorBlock;
import org.valkyrienskies.clockwork.content.curiosities.sensor.rotation.LodefocusBlock;
import org.valkyrienskies.clockwork.content.curiosities.sensor.rotation.LodefocusBlockEntity;
import org.valkyrienskies.clockwork.content.curiosities.sensor.rotation.client.LodefocusRenderer;
import org.valkyrienskies.clockwork.index.CWCreativeTabs;

/**
 * 传感器 / 仪器切片注册入口。
 *
 * <p>对应旧版 {@code curiosities/sensor/**} + {@code ClockworkBlocks.kt} 里 ALT_METER / DISTANCE_SENSOR /
 * GYROSCOPIC_SENSOR / LODEFOCUS / IMPACT_SENSOR 五项的注册。由 {@code CWCuriositiesIndex#register()} 调用。
 *
 * <p>资源（模型 / 方块状态 / 贴图 / 语言）沿用旧版拷贝过来的 {@code assets/vs_clockwork/**}，因此这里不写
 * {@code .blockstate(...)}，避免生成物覆盖手工资源。方块状态属性名与资源文件严格对齐：
 * {@code max_distance}（distance_sensor）、{@code predictiveness}（impact_sensor）、
 * {@code reference_axis}（gyroscopic_sensor）。
 */
@SuppressWarnings("removal") // Registrate 的 addLayer 在当前版本被标记待删除，但仍是生态通行做法
public final class CWSensorsIndex {

    private static final ClockworkRegistrate REGISTRATE = ClockworkMod.REGISTRATE;

    /** 透镜标签：定义了可以被传感器"穿过去继续探测"的透明方块（数据包文件已存在）。 */
    public static final TagKey<Block> SENSOR_LENS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, "sensor_lens"));

    /** 测距传感器（Peepotron）。 */
    public static final BlockEntry<DistanceSensorBlock> DISTANCE_SENSOR = REGISTRATE
            .block("distance_sensor", DistanceSensorBlock::new)
            .initialProperties(SharedProperties::stone)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutout)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .item()
            .tab(CWCreativeTabs.baseKey())
            .build()
            .register();

    /** 撞击传感器（Impactotron）。 */
    public static final BlockEntry<ImpactSensorBlock> IMPACT_SENSOR = REGISTRATE
            .block("impact_sensor", ImpactSensorBlock::new)
            .initialProperties(SharedProperties::stone)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutout)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .item()
            .tab(CWCreativeTabs.baseKey())
            .build()
            .register();

    /** 陀螺传感器（Spinotron）。 */
    public static final BlockEntry<GyroscopicSensorBlock> GYROSCOPIC_SENSOR = REGISTRATE
            .block("gyroscopic_sensor", GyroscopicSensorBlock::new)
            .initialProperties(SharedProperties::stone)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutout)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .item()
            .tab(CWCreativeTabs.baseKey())
            .build()
            .register();

    /** 透镜（Lodefocus）：透明、微光、不遮挡视线。 */
    public static final BlockEntry<LodefocusBlock> LODEFOCUS = REGISTRATE
            .block("lodefocus", LodefocusBlock::new)
            .initialProperties(SharedProperties::stone)
            .properties(p -> p.noOcclusion().lightLevel(s -> 1).isViewBlocking((s, l, pos) -> false))
            .addLayer(() -> RenderType::cutout)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .item()
            .tab(CWCreativeTabs.baseKey())
            .build()
            .register();

    public static final BlockEntityEntry<LodefocusBlockEntity> LODEFOCUS_BE = REGISTRATE
            .blockEntity("lodefocus", LodefocusBlockEntity::new)
            .validBlocks(LODEFOCUS)
            // 旧版 ClockworkBlockEntities.kt:333 的 .renderer { LodefocusRenderer(...) }：
            // 把装进去的指南针悬浮显示出来（方块模型里没有指南针）
            .renderer(() -> LodefocusRenderer::new)
            .register();

    private CWSensorsIndex() {
    }

    /** 触发类加载（由 {@code CWCuriositiesIndex} 调用）。 */
    public static void register() {
    }
}
