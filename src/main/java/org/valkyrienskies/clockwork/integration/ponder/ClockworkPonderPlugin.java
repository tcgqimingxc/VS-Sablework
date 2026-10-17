package org.valkyrienskies.clockwork.integration.ponder;

import com.simibubi.create.foundation.ponder.CreatePonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.api.registration.SharedTextRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * Clockwork 的 Ponder 插件。
 *
 * <p>对应旧版 {@code ClockworkPonderPlugin.kt}。差异：
 * <ul>
 *   <li>旧版直接实现 {@code PonderPlugin}；1.21.1 的生态惯例（也是 Simulated / Aeronautics 的写法）
 *       是继承 Create 的 {@link CreatePonderPlugin}，这样 Create 会帮忙处理场景里用到的
 *       共享文本等细节。</li>
 *   <li>标签从 {@code ClockworkPonderPlugin.CLOCKWORK_TAG} 挪到 {@link ClockworkPonderTags}，
 *       注册内容见该类（旧版把标签内容与场景注册分开写，本移植保持这个结构）。</li>
 *   <li>旧版注册 1 个标签 + 11 个场景；本移植同样注册 11 个场景（官方 nbt 11/11 就位），
 *       注册表集中在 {@link ClockworkPonderScenes}，一个场景一行。</li>
 * </ul>
 *
 * <p>注册时机：{@link ClockworkPonderClient#onClientSetup} 在 {@code FMLClientSetupEvent} 里调用
 * {@code PonderIndex.addPlugin(new ClockworkPonderPlugin())} —— 与 Create 自己
 * （{@code CreateClient.clientInit}）完全相同的时机，早于 Ponder 首次 {@code registerAll()}。
 */
public class ClockworkPonderPlugin extends CreatePonderPlugin {

    @Override
    public String getModId() {
        return ClockworkMod.MOD_ID;
    }

    @Override
    public void registerScenes(final PonderSceneRegistrationHelper<ResourceLocation> helper) {
        ClockworkPonderScenes.register(helper);
    }

    @Override
    public void registerTags(final PonderTagRegistrationHelper<ResourceLocation> helper) {
        ClockworkPonderTags.register(helper);
    }

    @Override
    public void registerSharedText(final SharedTextRegistrationHelper helper) {
        // 本模组的场景文本都走 lang 文件（vs_clockwork.ponder.*），没有需要共享的短句；
        // 这里覆盖成空实现是刻意的：父类会注册 Create 自己的一堆共享文本，与我们无关。
    }
}
