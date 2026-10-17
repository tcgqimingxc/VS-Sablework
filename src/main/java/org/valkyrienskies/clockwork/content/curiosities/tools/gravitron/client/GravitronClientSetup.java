package org.valkyrienskies.clockwork.content.curiosities.tools.gravitron.client;

import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.valkyrienskies.clockwork.ClockworkMod;
import org.valkyrienskies.clockwork.content.curiosities.tools.CWToolsIndex;

/**
 * 重力枪的客户端装配：注册自定义物品渲染器。
 *
 * <p>为什么不用 {@code Item#initializeClient}：那个钩子要写在物品类里，而本切片约定不改
 * {@code CWToolsIndex} 之外的东西；NeoForge 提供了等价入口
 * {@link RegisterClientExtensionsEvent#registerItem}（javap 核实签名），效果与
 * {@code initializeClient(consumer -> consumer.accept(SimpleCustomRenderer.create(...)))} 完全一致。
 *
 * <p>注意 {@link SimpleCustomRenderer#create} 必须对<b>每一个</b>物品各调一次：它内部除了返回扩展，
 * 还把物品登记进 Create 的 {@code CustomRenderedItems}，只有登记过的物品在烘焙时才会被包成
 * {@code CustomRenderedItemModel}，否则 {@code renderByItem} 里那次强制转换会直接抛异常。
 *
 * <p>{@link GravitronPartials} 的提前初始化已统一收口到
 * {@code org.valkyrienskies.clockwork.index.CWClientSetup}（同一个 {@code FMLClientSetupEvent}），
 * 本类不再重复调用。
 *
 * <p>不写 {@code bus = Bus.MOD}：NeoForge 21.1.228 的 {@code AutomaticEventSubscriber.inject}
 * 是按方法参数类型是否 {@code IModBusEvent} 分流到 mod 总线 / 游戏总线的，根本不读该属性
 * （已反编译核实），而该属性已被标记为待删除。
 */
@EventBusSubscriber(modid = ClockworkMod.MOD_ID, value = Dist.CLIENT)
public final class GravitronClientSetup {

    private GravitronClientSetup() {
    }

    @SubscribeEvent
    public static void onRegisterClientExtensions(final RegisterClientExtensionsEvent event) {
        event.registerItem(
                SimpleCustomRenderer.create(CWToolsIndex.GRAVITRON.get(), new GravitronItemRenderer()),
                CWToolsIndex.GRAVITRON.get());
        event.registerItem(
                SimpleCustomRenderer.create(CWToolsIndex.CREATIVE_GRAVITRON.get(), new GravitronItemRenderer()),
                CWToolsIndex.CREATIVE_GRAVITRON.get());
    }
}
