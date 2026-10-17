package org.valkyrienskies.clockwork.content.curiosities;

import org.valkyrienskies.clockwork.content.curiosities.aeronaut.CWAeronautIndex;
import org.valkyrienskies.clockwork.content.curiosities.food.CWFoodIndex;
import org.valkyrienskies.clockwork.content.curiosities.meteor.CWMeteorIndex;
import org.valkyrienskies.clockwork.content.curiosities.sensor.CWSensorsIndex;
import org.valkyrienskies.clockwork.content.curiosities.tools.CWToolsIndex;

/**
 * 奇物 / 工具 / 材料 / 陨石切片注册入口。
 *
 * <p>由 {@code ClockworkMod} 在内容注册阶段调用（与 {@code CWBlocks#register()}、{@code CWItems#register()} 同级）。
 * 本切片按子领域再分：
 * <ul>
 *   <li>{@link CWSensorsIndex} —— 传感器与透镜（distance / impact / gyroscopic sensor、lodefocus）；</li>
 *   <li>{@link CWInstrumentsIndex} —— 仪表与时钟（alt_meter、clock、solver）；</li>
 *   <li>{@link CWToolsIndex} —— 手持工具与材料物品（wanderwand / gravitron / screwdriver / drill / saw /
 *       wanderlite 立方与基体 / 音乐唱片）；</li>
 *   <li>{@link CWAeronautIndex} —— 飞行家护甲四件套；</li>
 *   <li>{@link CWFoodIndex} —— 食物与杂项（bluuguu / stratodonut / bluperglue / pastrymaker）；</li>
 *   <li>{@link CWMeteorIndex} —— 陨石与陨石生成（asteroid_block）。</li>
 * </ul>
 *
 * <p>移植约定：本切片的公共注册入口只有这一个，方便 Lead 在 {@code ClockworkMod} 里加一行调用。
 */
public final class CWCuriositiesIndex {

    private CWCuriositiesIndex() {
    }

    /** 触发本切片所有子索引的类加载与注册（调用顺序无关，只为可读性分组）。 */
    public static void register() {
        CWSensorsIndex.register();
        CWInstrumentsIndex.register();
        CWToolsIndex.register();
        CWAeronautIndex.register();
        CWFoodIndex.register();
        CWMeteorIndex.register();
    }
}
