package org.valkyrienskies.clockwork.content.curiosities.tools;

import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.valkyrienskies.clockwork.ClockworkMod;
import org.valkyrienskies.clockwork.content.curiosities.tools.drill.client.HandheldDrillRenderer;
import org.valkyrienskies.clockwork.content.curiosities.tools.saw.client.HandheldSawRenderer;
import org.valkyrienskies.clockwork.content.curiosities.tools.wanderwand.client.WanderwandItemRenderer;

/**
 * 工具的客户端装配：注册"静态模型 + partial 部件"类型的自定义物品渲染器。
 *
 * <p>旧版这些物品用 {@code AssetLookup.itemModelWithPartials()} + {@code customRenderedItem}，
 * 也就是物品模型只烘焙"机身"，钻头 / 锯片 / 法杖水晶这些部件由渲染器逐个叠上去。
 * 本移植最初漏掉了这一步，结果是物品在游戏里少了部件（详见 {@code docs/ASSET-AUDIT.md}）。
 *
 * <p>写法与 {@code GravitronClientSetup} 完全一致：{@code SimpleCustomRenderer.create} 必须对
 * <b>每一个</b>物品各调一次（它同时把物品登记进 Create 的 {@code CustomRenderedItems}，
 * 没登记的物品烘焙时不会被包成 {@code CustomRenderedItemModel}）。
 *
 * <p>对应 partial 类的提前初始化统一收口在 {@code index/CWClientSetup}，本类不重复调用。
 */
@EventBusSubscriber(modid = ClockworkMod.MOD_ID, value = Dist.CLIENT)
public final class CWToolsClientSetup {

    private CWToolsClientSetup() {
    }

    @SubscribeEvent
    public static void onRegisterClientExtensions(final RegisterClientExtensionsEvent event) {
        event.registerItem(
                SimpleCustomRenderer.create(CWToolsIndex.HANDHELD_DRILL.get(), new HandheldDrillRenderer()),
                CWToolsIndex.HANDHELD_DRILL.get());
        event.registerItem(
                SimpleCustomRenderer.create(CWToolsIndex.HANDHELD_SAW.get(), new HandheldSawRenderer()),
                CWToolsIndex.HANDHELD_SAW.get());
        event.registerItem(
                SimpleCustomRenderer.create(CWToolsIndex.WANDERWAND.get(), new WanderwandItemRenderer()),
                CWToolsIndex.WANDERWAND.get());
        event.registerItem(
                SimpleCustomRenderer.create(CWToolsIndex.INCOMPLETE_WANDERWAND.get(), new WanderwandItemRenderer()),
                CWToolsIndex.INCOMPLETE_WANDERWAND.get());
    }
}
