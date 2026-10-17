package org.valkyrienskies.clockwork.config;

import net.createmod.catnip.config.ConfigBase;

/**
 * 客户端相关配置（对应旧版 {@code ClockworkConfig.CLIENT}）。
 */
public class CWClient extends ConfigBase {

    public final ConfigGroup debug = group(0, "debug", "调试显示开关，正式游玩时建议全部关闭");

    /** 旧版用于绘制气团/管道调试渲染，移植后保留同一个开关名，方便老玩家沿用配置。 */
    public final ConfigBool debugRender = b(false, "debugRender", "绘制气团与气体网络调试信息");

    @Override
    public String getName() {
        return "client";
    }
}
