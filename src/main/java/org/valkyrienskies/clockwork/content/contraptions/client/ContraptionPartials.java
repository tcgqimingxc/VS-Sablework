package org.valkyrienskies.clockwork.content.contraptions.client;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.valkyrienskies.clockwork.ClockworkMod;

/**
 * 物理机械（{@code content/contraptions/**}）里"只被方块实体渲染器引用"的局部模型。
 *
 * <p>对应旧版 {@code ClockworkPartials.kt} 中的
 * {@code GYRO_BASE}（:68）、{@code PHYS_NORTH/SOUTH/EAST/WEST_WING}（:72-75）、
 * {@code PHYS_SHAFT}（:76）、{@code PHYS_ATTACHER}（:77）。
 * 模型文件随资源一起迁移过来了（{@code models/block/gyro/base.json}、
 * {@code models/block/phys_bearing/*.json}），<b>不需要新增任何资源</b>。
 *
 * <h2>为什么必须集中一处并提前 init</h2>
 * <p>这里声明的模型<b>没有任何 blockstate / 物品模型引用它们</b>（已逐个核对
 * {@code assets/vs_clockwork/blockstates} 与 {@code models}），只被
 * {@code GyroRenderer} / {@code PhysBearingRenderer} / {@code PropellerBearingRenderer} /
 * {@code CopterBearingRenderer} / {@code GimbalBearingRenderer} / {@code PhysicsInfuserRenderer}
 * 在首次渲染时才用到。
 * 而 Flywheel 只在 {@code ModelEvent.RegisterAdditional} 时登记"那一刻已存在于
 * {@code PartialModel.ALL} 里"的模型，烘焙结束后 {@code PartialModel.populateOnInit} 变 true，
 * 之后再创建就直接查 {@code ModelManager}，查不到就是 {@code missingno}（黑紫方块）。
 * 所以必须由 {@code index/CWClientSetup#onClientSetup} 在 {@code FMLClientSetupEvent}
 * 里调用 {@link #init()} 触发类初始化（完整机理见 {@code CWClientSetup} 的类注释）。
 *
 * <p>本类是这些模型唯一的声明处：新增"只给 BER 用的方块局部模型"请加在这里，
 * 别散落到各方块包里，否则很容易漏登记而造成黑紫。
 */
public final class ContraptionPartials {

    /** 陀螺（{@code gyro}）的底板：旧版 {@code GyroBlockEntityRenderer} 里唯一不随轮子转的部件。 */
    public static final PartialModel GYRO_BASE = block("gyro/base");

    /** 物理轴承 / 陀螺共用的输入轴（旧版两处都引用同一个模型）。 */
    public static final PartialModel PHYS_SHAFT = block("phys_bearing/shaft");
    /** 物理轴承的"抓取爪"：装配时张开抓住子结构。 */
    public static final PartialModel PHYS_ATTACHER = block("phys_bearing/attacher");

    /** 物理轴承的四片可张开翼面（按局部坐标固定方向，不随 {@code facing} 旋转）。 */
    public static final PartialModel PHYS_NORTH_WING = block("phys_bearing/wing_north");
    public static final PartialModel PHYS_SOUTH_WING = block("phys_bearing/wing_south");
    public static final PartialModel PHYS_EAST_WING = block("phys_bearing/wing_east");
    public static final PartialModel PHYS_WEST_WING = block("phys_bearing/wing_west");

    /**
     * 螺旋桨轴承的顶盖（旧版 {@code ClockworkPartials.PROPELLER_TOP}）：随输入轴自转。
     *
     * <p>旧版实际借用的是 Create 的 {@code AllPartialModels.BEARING_TOP}（见 {@code BearingRenderer}），
     * 本移植改用工程自带的这块模型（贴图 {@code block/propbearingtop}），
     * 取舍见 {@code PropellerBearingRenderer} 类注释。
     */
    public static final PartialModel PROPELLER_TOP = block("propeller_bearing/top");

    /**
     * 直升机旋翼的顶盖 / 桨盘（斜盘）/ 四根操纵活塞
     * （旧版 {@code ClockworkPartials.SMART_PROP_TOP} / {@code SMART_PROP_WAFER} / {@code SMART_PROP_PISTON_*}）。
     */
    public static final PartialModel COPTER_TOP = block("copter_bearing/top");
    public static final PartialModel COPTER_WAFER = block("copter_bearing/wafer");
    public static final PartialModel COPTER_PISTON_NW = block("copter_bearing/piston_nw");
    public static final PartialModel COPTER_PISTON_NE = block("copter_bearing/piston_ne");
    public static final PartialModel COPTER_PISTON_SW = block("copter_bearing/piston_sw");
    public static final PartialModel COPTER_PISTON_SE = block("copter_bearing/piston_se");

    /**
     * 万向轴承的托板与四条连杆
     * （旧版 {@code ClockworkPartials.GIMBAL_PLATE} / {@code GIMBAL_ARM}）：
     * 托板跟着被举起的子空间走，连杆连接轴承底座与托板四角。
     */
    public static final PartialModel GIMBAL_PLATE = block("gimbal_bearing/plate");
    public static final PartialModel GIMBAL_ARM = block("gimbal_bearing/arm");

    /**
     * 物理灌注器轴心的三层水晶（旧版 {@code ClockworkPartials.CRYSTAL} /
     * {@code CRYSTAL_INNER} / {@code CRYSTAL_OUTER}）。
     *
     * <p>它们是<b>物品</b>模型（{@code item/wanderwand/crystal*}，与游荡法杖杖头共用同一批文件），
     * 旧版法杖渲染器与本灌注器渲染器引用的是同一份模型。这里单独声明一份而不是复用
     * {@code curiosities} 切片的 {@code WanderwandPartials}：{@code PartialModel.of} 内部是
     * {@code ALL.computeIfAbsent}，同一个 id 返回同一个实例，重复声明没有副作用，
     * 但可以让两个切片互不依赖。
     */
    public static final PartialModel CRYSTAL_INNER = item("wanderwand/crystal_inner");
    public static final PartialModel CRYSTAL = item("wanderwand/crystal");
    public static final PartialModel CRYSTAL_OUTER = item("wanderwand/crystal_outer");

    private ContraptionPartials() {
    }

    /**
     * {@code Direction.Axis} → 该轴的正方向。
     *
     * <p>用途：{@code SuperByteBuffer#rotateCentered(float, Direction)} 需要一个 {@code Direction}
     * 来表达"绕哪根轴、朝哪边转"，而方块状态里存的往往只有 {@code Axis}
     * （{@code Direction.Axis#getPositive()} 在本机可 javap 的产物里不存在，故写成显式映射）。
     */
    public static Direction positiveDirection(final Direction.Axis axis) {
        return switch (axis) {
            case X -> Direction.EAST;
            case Y -> Direction.UP;
            case Z -> Direction.SOUTH;
        };
    }

    /** 触发静态初始化（由 {@code CWClientSetup} 在客户端启动时调用）。重复调用安全：本方法无副作用。 */
    public static void init() {
    }

    private static PartialModel block(final String path) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, "block/" + path));
    }

    private static PartialModel item(final String path) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, "item/" + path));
    }
}
