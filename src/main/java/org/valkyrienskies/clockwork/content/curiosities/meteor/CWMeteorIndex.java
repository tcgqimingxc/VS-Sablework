package org.valkyrienskies.clockwork.content.curiosities.meteor;

import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.CreativeModeTabs;
import org.valkyrienskies.clockwork.ClockworkMod;
import org.valkyrienskies.clockwork.ClockworkRegistrate;

/**
 * 陨石切片注册入口。
 *
 * <p>对应旧版 {@code ClockworkBlocks.ASTEROID}（{@code asteroid_block} / {@code MeteorTestBlock}），
 * 与 {@code curiosities/meteor/**} 的生成逻辑。由 {@code CWCuriositiesIndex#register()} 调用。
 *
 * <p>创造标签与旧版一致：{@link CreativeModeTabs#OP_BLOCKS}（调试方块）。
 */
@SuppressWarnings("removal")
public final class CWMeteorIndex {

    private static final ClockworkRegistrate REGISTRATE = ClockworkMod.REGISTRATE;

    /** 调试用陨石生成器：放下后在上方 30 格生成一颗会下落的陨石。 */
    public static final BlockEntry<MeteorTestBlock> ASTEROID_BLOCK = REGISTRATE
            .block("asteroid_block", MeteorTestBlock::new)
            .initialProperties(SharedProperties::wooden)
            .properties(p -> p.noOcclusion())
            .addLayer(() -> RenderType::cutout)
            .tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .item()
            .tab(CreativeModeTabs.OP_BLOCKS)
            .build()
            .register();

    private CWMeteorIndex() {
    }

    /** 触发类加载（由 {@code CWCuriositiesIndex} 调用）。 */
    public static void register() {
    }
}
