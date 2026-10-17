package org.valkyrienskies.clockwork.content.curiosities.altmeter;

import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.NumericSingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 高度计的信息面板数据源。
 *
 * <p>对应旧版 {@code curiosities/altmeter/AltMeterDisplaySource.kt}：让信息面板（Display Link）显示
 * 高度计的当前高度。
 *
 * <p>移植差异：旧版继承 {@code NumericSingleLineDisplaySource} 并在
 * {@code initConfigurationWidgets} 里加了一个"当前高度 / 目标高度 / 触发方向"的下拉选择（客户端 GUI）。
 * 本移植只输出**当前高度**这一项：该下拉控件属于客户端界面切片，且需要在其它切片注册
 * 翻译键与配置项；保留数值行格式，玩家依然可以把它接到显示屏 / 翻牌屏上。
 */
public class AltMeterDisplaySource extends NumericSingleLineDisplaySource {

    @Override
    protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
        if (!(context.getSourceBlockEntity() instanceof AltMeterBlockEntity meter)) {
            return ZERO.copy();
        }
        return Component.literal(Integer.toString(meter.getCurrentHeight()));
    }

    @Override
    protected boolean allowsLabeling(DisplayLinkContext context) {
        return true;
    }

    @Override
    public Component getName() {
        // 旧版同样复用方块名，避免新增翻译键。
        return Component.translatable("block.vs_clockwork.alt_meter");
    }
}
