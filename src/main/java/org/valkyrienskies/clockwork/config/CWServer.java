package org.valkyrienskies.clockwork.config;

import net.createmod.catnip.config.ConfigBase;

/**
 * 服务端 / 世界规则相关配置（对应旧版 {@code ClockworkConfig.SERVER}）。
 *
 * <p>这里只保留移植后真正会被读取的项；新增玩法时按同样风格追加，
 * 注释写清楚单位与影响范围，方便后续维护者调参。
 */
public class CWServer extends ConfigBase {

    /** 气动物理：螺旋桨/机翼的施力量级（整体缩放，调试用）。 */
    public final ConfigGroup physics = group(0, "physics", Comments.physics);
    public final ConfigFloat propellerForceMultiplier =
            f(1.0f, 0.0f, 100.0f, "propellerForceMultiplier", Comments.propellerForceMultiplier);
    public final ConfigFloat propellerMaxForce =
            f(2000000.0f, 0.0f, 1.0E9f, "propellerMaxForce", Comments.propellerMaxForce);
    public final ConfigFloat propellerMaxTorque =
            f(2000000.0f, 0.0f, 1.0E9f, "propellerMaxTorque", Comments.propellerMaxTorque);
    /**
     * 单片叶片的最大展长 [格]（旧版 {@code ClockworkConfig.SERVER.maxBladeSize = 8.0}）。
     *
     * <p>工作台加长配方的上限判定、{@code BladeItems#setLength} 的夹取、以及叶片控制器渲染器的
     * 包围盒都读这一个值；改小后老存档里更长的叶片会在下一次写入时被夹到新上限，
     * 但<b>读取</b>时也会夹（见 {@code BladeItems#getLength}），所以不会出现超长叶片仍按旧值算推力。
     */
    public final ConfigInt maxBladeSize =
            i(8, 1, 64, "maxBladeSize", Comments.maxBladeSize);

    /** 浮力：热气球/浮空结构的受力倍率。 */
    public final ConfigGroup balloons = group(1, "balloons", Comments.balloons);
    public final ConfigFloat balloonForceMultiplier =
            f(1.0f, 0.0f, 100.0f, "balloonForceMultiplier", Comments.balloonForceMultiplier);
    public final ConfigInt balloonMaxScanVolume =
            i(20000, 64, 2000000, "balloonMaxScanVolume", Comments.balloonMaxScanVolume);
    public final ConfigInt balloonMaxScanSurface =
            i(4000, 64, 2000000, "balloonMaxScanSurface", Comments.balloonMaxScanSurface);
    public final ConfigInt balloonMaxRaycastDistance =
            i(16, 1, 128, "balloonMaxRaycastDistance", Comments.balloonMaxRaycastDistance);

    /** 气体系统：压力/温度的模拟尺度。 */
    public final ConfigGroup gas = group(2, "gas", Comments.gas);
    public final ConfigFloat gasPressureDecay =
            f(0.02f, 0.0f, 1.0f, "gasPressureDecay", Comments.gasPressureDecay);
    public final ConfigInt ductNodeCapacity =
            i(1000, 1, 100000, "ductNodeCapacity", Comments.ductNodeCapacity);

    // -------- 气体物流切片追加（仍在 gas 分组内；旧版这些参数分散在 Kelvin 分组与 ClockworkConfig 其他位置） --------

    public final ConfigFloat gasAmbientTemperature =
            f(293.0f, 1.0f, 5000.0f, "gasAmbientTemperature", Comments.gasAmbientTemperature);
    public final ConfigFloat gasAmbientPressure =
            f(101325.0f, 1.0f, 1.0E9f, "gasAmbientPressure", Comments.gasAmbientPressure);
    public final ConfigFloat gasAmbientHeatExchange =
            f(0.005f, 0.0f, 1.0f, "gasAmbientHeatExchange", Comments.gasAmbientHeatExchange);
    public final ConfigFloat gasFlowPerPascal =
            f(1.0E-6f, 0.0f, 1.0f, "gasFlowPerPascal", Comments.gasFlowPerPascal);
    /**
     * 普通金属连接的基础导热率 [1/tick]。
     *
     * <p>物理含义：一条连接的热导 G [W/K] 与两侧节点串联热容
     * {@code C_series = C₁C₂/(C₁+C₂)} 决定了温度差每刻衰减的比例
     * {@code k = G·Δt/C_series}；本实现把 Δt 与 C 合并成一个 0..1 的比例，
     * 所以 <b>k 就是“每刻消掉的两侧温差比例”</b>。k=0 表示完全绝热，k=1 表示一 tick 均温。
     *
     * <p>为什么需要这个基础值：质量搬运只带走“被搬走的那份气体”的热量，压力一均衡流动就停；
     * 没有导热时，不在流路上的死端（例如挂在支管末端的斯特林机）永远停在室温。
     * 旧版 {@code DuctNetworkUtils.configureMetalDuctEdge} 把
     * {@code ductThermalConductivityMultiplier}（默认 1000）写到所有金属边上，即旧版本来就有这条路径；
     * 本移植早期只给铜接头加了导热，普通导管恒为 0，属回归。
     *
     * <p>默认 0.1 的依据：比环境散热（{@code gasAmbientHeatExchange}=0.005/tick）快 20 倍，
     * 热量能沿十几格管道跑到机器，而不会被环境散热吃掉；同时 5～8 个节点的网络里
     * 煤炉仍能把自己维持在 800K 以上（k 再大就会把炉子本身拉离 850K 目标）。
     * 铜接头用 {@code copperDuctThermalConductivity} 覆盖，两者取较大值。
     */
    public final ConfigFloat gasThermalConductivity =
            f(0.1f, 0.0f, 1.0f, "gasThermalConductivity", Comments.gasThermalConductivity);

    // -------- 导管边件（单向 / 智能 / 铜）：语义取证见 docs/DUCT-EDGE-VARIANTS.md --------

    /** 铜接头：相同压差下比普通导管多搬多少气（旧版没有这个方块，属按材质语义补，见文档 §2.4）。 */
    public final ConfigFloat copperDuctFlowScale =
            f(1.5f, 0.1f, 10.0f, "copperDuctFlowScale", Comments.copperDuctFlowScale);
    /** 铜接头：每刻消掉的两侧温差比例（与基础导热率取较大者；铜比普通金属管更快）。 */
    public final ConfigFloat copperDuctThermalConductivity =
            f(0.15f, 0.0f, 1.0f, "copperDuctThermalConductivity", Comments.copperDuctThermalConductivity);
    /**
     * 智能边件：压差档阈值 [Pa]。
     *
     * <p>旧版这个数是 GUI 里的滚动输入（{@code SmartDuctScreen.kt:70-77}，范围 0..99999 kPa）。
     * 本移植没有那个界面（GUI 不在移植范围），改为服务端配置 + 方块状态里的"高/低于"方向，
     * 表达同一套语义；每个智能边件共享这一个阈值。
     */
    public final ConfigFloat smartDuctPressureThreshold =
            f(200000.0f, 0.0f, 1.0E9f, "smartDuctPressureThreshold", Comments.smartDuctPressureThreshold);
    /** 智能边件：温度档阈值 [K]（同上，旧版是 GUI 滚动输入，单位 K）。 */
    public final ConfigFloat smartDuctTemperatureThreshold =
            f(500.0f, 1.0f, 5000.0f, "smartDuctTemperatureThreshold", Comments.smartDuctTemperatureThreshold);

    public final ConfigFloat airCompressorSpeed =
            f(1.0E-4f, 0.0f, 10.0f, "airCompressorSpeed", Comments.airCompressorSpeed);
    public final ConfigFloat airCompressorMaxPressure =
            f(1000000.0f, 0.0f, 1.0E9f, "airCompressorMaxPressure", Comments.airCompressorMaxPressure);
    public final ConfigFloat airCompressorHeliumAirDensity =
            f(0.3f, -1.0f, 10.0f, "airCompressorHeliumAirDensity", Comments.airCompressorHeliumAirDensity);

    public final ConfigFloat creativeGasGeneratorPressure =
            f(1013250.0f, 0.0f, 1.0E9f, "creativeGasGeneratorPressure", Comments.creativeGasGeneratorPressure);
    public final ConfigFloat creativeGasGeneratorTemperature =
            f(293.0f, 1.0f, 5000.0f, "creativeGasGeneratorTemperature", Comments.creativeGasGeneratorTemperature);

    public final ConfigFloat coalBurnerMaxJoulesPerTick =
            f(10000.0f, 0.0f, 1.0E9f, "coalBurnerMaxJoulesPerTick", Comments.coalBurnerMaxJoulesPerTick);

    public final ConfigFloat steamGeneratorMaxMass =
            f(0.1f, 0.0f, 100.0f, "steamGeneratorMaxMass", Comments.steamGeneratorMaxMass);

    public final ConfigFloat sterlingEngineTemperatureIncrement =
            f(60.0f, 1.0f, 1000.0f, "sterlingEngineTemperatureIncrement", Comments.engineTemperatureIncrement);
    public final ConfigFloat sterlingEngineMaxHeatLoss =
            f(5000.0f, 0.0f, 1.0E9f, "sterlingEngineMaxHeatLoss", Comments.engineMaxHeatLoss);
    public final ConfigFloat sterlingEngineStressCapacity =
            f(16384.0f, 0.0f, 1.0E9f, "sterlingEngineStressCapacity", Comments.sterlingEngineStressCapacity);

    // -------- 燃气机（gas_engine）追加；旧版这些项在 ClockworkConfig.SERVER.gasEngine 分组内 --------

    public final ConfigFloat gasEngineFlowForFullEfficiency =
            f(5.0f, 0.0f, 1000.0f, "gasEngineFlowForFullEfficiency", Comments.gasEngineFlowForFullEfficiency);
    public final ConfigFloat gasEngineMinimumFlowRate =
            f(0.5f, 0.0f, 1000.0f, "gasEngineMinimumFlowRate", Comments.gasEngineMinimumFlowRate);
    public final ConfigFloat gasEngineFlowRateIncrement =
            f(0.25f, 0.0f, 1000.0f, "gasEngineFlowRateIncrement", Comments.gasEngineFlowRateIncrement);
    public final ConfigFloat gasEngineTemperatureIncrement =
            f(60.0f, 1.0f, 1000.0f, "gasEngineTemperatureIncrement", Comments.gasEngineTemperatureIncrement);
    public final ConfigFloat gasEngineMaxHeatLoss =
            f(5000.0f, 0.0f, 1.0E9f, "gasEngineMaxHeatLoss", Comments.gasEngineMaxHeatLoss);

    public final ConfigFloat gasThrusterForceMultiplier =
            f(200.0f, 0.0f, 1.0E6f, "gasThrusterForceMultiplier", Comments.gasThrusterForceMultiplier);

    public final ConfigFloat heaterSmoulderingTemp =
            f(500.0f, 1.0f, 10000.0f, "heaterSmoulderingTemp", Comments.heaterSmoulderingTemp);
    public final ConfigFloat heaterKindledTemp =
            f(800.0f, 1.0f, 10000.0f, "heaterKindledTemp", Comments.heaterKindledTemp);
    public final ConfigFloat heaterSeethingTemp =
            f(1300.0f, 1.0f, 10000.0f, "heaterSeethingTemp", Comments.heaterSeethingTemp);

    // ===================== 通用动力部件（kinetics 切片追加，勿动上面的字段） =====================

    /** 通用轴 / 伸缩杆等"万向节"类连接的参数。 */
    public final ConfigGroup kinetics = group(3, "kinetics", Comments.kinetics);
    public final ConfigFloat maxUniversalJointDistance =
            f(32.0f, 1.0f, 1024.0f, "maxUniversalJointDistance", Comments.maxUniversalJointDistance);

    // ===================== 物理机械（contraptions 切片追加，勿动上面的字段） =====================

    /**
     * 物理轴承家族（物理轴承 / 螺旋桨轴承 / 襟翼轴承 / 万向轴承）。
     *
     * <p>伺服系数的作用方式与 Sable 的 {@code RotaryConstraintHandle#setMotor} 一致：
     * 实际 stiffness/damping 会再乘以"绕该轴的等效转动惯量"，
     * 因此这里是"单位惯量的增益"，与结构大小无关。
     */
    public final ConfigGroup bearings = group(4, "bearings", Comments.bearings);
    public final ConfigFloat bearingStiffness =
            f(60.0f, 0.0f, 1.0E6f, "bearingStiffness", Comments.bearingStiffness);
    public final ConfigFloat bearingDamping =
            f(12.0f, 0.0f, 1.0E6f, "bearingDamping", Comments.bearingDamping);
    public final ConfigFloat bearingFriction =
            f(8.0f, 0.0f, 1.0E6f, "bearingFriction", Comments.bearingFriction);
    public final ConfigFloat bearingLockStiffness =
            f(400.0f, 0.0f, 1.0E6f, "bearingLockStiffness", Comments.bearingLockStiffness);
    public final ConfigFloat bearingLockDamping =
            f(60.0f, 0.0f, 1.0E6f, "bearingLockDamping", Comments.bearingLockDamping);
    public final ConfigFloat bearingMaxSpeed =
            f(128.0f, 1.0f, 1024.0f, "bearingMaxSpeed", Comments.bearingMaxSpeed);

    /** 襟翼轴承：摆动幅度与相位推进速度。 */
    public final ConfigGroup flap = group(5, "flap", Comments.flap);
    public final ConfigFloat flapAmplitudeDegrees =
            f(35.0f, 0.0f, 90.0f, "flapAmplitudeDegrees", Comments.flapAmplitudeDegrees);
    public final ConfigFloat flapPhasePerShaftDegree =
            f(0.35f, 0.0f, 10.0f, "flapPhasePerShaftDegree", Comments.flapPhasePerShaftDegree);

    /** 物理灌注器：装配节奏与规模上限。 */
    public final ConfigGroup infuser = group(6, "infuser", Comments.infuser);
    public final ConfigInt infuserAssemblyTicks =
            i(60, 1, 2400, "assemblyTicks", Comments.infuserAssemblyTicks);
    public final ConfigInt infuserMaxBlocks =
            i(8192, 1, 2000000, "maxBlocks", Comments.infuserMaxBlocks);

    /** 姿态控制：陀螺稳定与反作用轮。 */
    public final ConfigGroup attitude = group(7, "attitude", Comments.attitude);
    public final ConfigFloat gyroStrength =
            f(1.0f, 0.0f, 100.0f, "gyroStrength", Comments.gyroStrength);
    public final ConfigFloat gyroMaxTorque =
            f(2000000.0f, 0.0f, 1.0E9f, "gyroMaxTorque", Comments.gyroMaxTorque);
    /**
     * 陀螺合计允许给结构带来的最大角加速度 [rad/s²]（&lt;= 0 表示不限制，不推荐）。
     *
     * <p>为什么按角加速度而不是只按力矩封顶：陀螺算出的力矩本来就是
     * {@code τ = I·Δω/dt}，结构越大 I 越大、力矩同比例变大，用固定的 N·m 上限会让
     * 小船被弹飞、大船又不够用；按 α 封顶后大小结构手感一致（τ ∝ I 约掉）。
     * 这个上限同时是"失控时不被弹飞"的主保证：无上限时一次修正就可能给出巨大的角冲量。
     *
     * <p>多块陀螺时按块数摊薄，合计仍然是这一个值（见 GyroBlockEntity 类注释 §5）。
     */
    public final ConfigFloat gyroMaxAngularAcceleration =
            f(20.0f, 0.0f, 1000.0f, "gyroMaxAngularAcceleration", Comments.gyroMaxAngularAcceleration);
    public final ConfigFloat reactionWheelEffectiveness =
            f(1.0f, 0.0f, 100.0f, "reactionWheelEffectiveness", Comments.reactionWheelEffectiveness);

    /** 机翼 / 襟翼的气动参数（受力由 platform 侧 CWWingForces 计算，机翼方块调用它）。 */
    public final ConfigGroup wing = group(8, "wing", Comments.wing);
    public final ConfigFloat wingArea =
            f(1.0f, 0.01f, 100.0f, "area", Comments.wingArea);
    public final ConfigFloat wingForceMultiplier =
            f(1.0f, 0.0f, 100.0f, "forceMultiplier", Comments.wingForceMultiplier);
    public final ConfigFloat wingMaxForce =
            f(2000000.0f, 0.0f, 1.0E9f, "maxForce", Comments.wingMaxForce);
    /**
     * 计算升力/阻力时允许使用的最大来流速度 [m/s]。
     *
     * <p><b>这是"机翼导致物理结构消失"的第一道守卫</b>：升力 ∝ v²，而 Sable 的质量尺度是
     * 1 方块 ≈ 1 kg；不封顶的话，结构被任何外力加速到几百 m/s 之后气动力会正反馈爆炸，
     * 一两个子步就把结构推到 Y 坐标保护之外，被 Sable 直接删除
     * （{@code ServerSubLevel.java:228-232} "extreme Y coordinate range, removing"）。
     */
    public final ConfigFloat wingMaxAirspeed =
            f(60.0f, 1.0f, 400.0f, "maxAirspeed", Comments.wingMaxAirspeed);
    /** 空气密度计算允许使用的最大气压倍率（Sable 的 DimensionPhysics 曲线在稠密大气维度会 > 1）。 */
    public final ConfigFloat wingMaxPressure =
            f(2.0f, 0.0f, 100.0f, "maxPressure", Comments.wingMaxPressure);
    /**
     * 单块机翼允许给结构带来的最大加速度 [m/s²]（<= 0 表示不限制，不推荐）。
     *
     * <p>与 {@code maxForce} 的区别：这是<b>按结构质量缩放</b>的守卫。质量尺度是 1 方块 ≈ 1 kg，
     * 所以"力"封顶挡不住小结构被弹飞 —— 20 kg 的飞机挨上 2000 N 就是 100 m/s²。
     * 按加速度封顶后，轻结构和重结构的手感一致（都受同一个上限约束），重结构仍然拿到完整的力。
     */
    public final ConfigFloat wingMaxAcceleration =
            f(20.0f, 0.0f, 200.0f, "maxAcceleration", Comments.wingMaxAcceleration);

    /** 糖火箭。 */
    public final ConfigGroup sugarRocket = group(9, "sugar_rocket", Comments.sugarRocket);
    public final ConfigFloat sugarRocketThrust =
            f(2000.0f, 0.0f, 1.0E7f, "thrustPerPower", Comments.sugarRocketThrust);
    public final ConfigInt sugarRocketMaxPower =
            i(8, 1, 64, "maxPower", Comments.sugarRocketMaxPower);

    /** 热气球浮力（体积扫描模型）。 */
    public final ConfigGroup ballooner = group(10, "ballooner", Comments.ballooner);
    public final ConfigInt balloonerMaxScanVolume =
            i(4000, 8, 2000000, "maxScanVolume", Comments.balloonerMaxScanVolume);
    public final ConfigFloat balloonerLiftPerBlock =
            f(2.0f, 0.0f, 10000.0f, "liftPerBlock", Comments.balloonerLiftPerBlock);

    /** 对接口（惰性轴承）：自由铰链的粘性阻尼。 */
    public final ConfigGroup spinoffBearing = group(11, "spinoff_bearing", Comments.spinoffBearing);
    public final ConfigFloat spinoffBearingFriction =
            f(2.0f, 0.0f, 1.0E6f, "friction", Comments.spinoffBearingFriction);

    // ===================== 奇物工具（curiosities 切片追加，勿动上面的字段） =====================

    /**
     * 重力枪（gravitron）抓取牵引参数。
     *
     * <p>单位约定（重要，Sable 的 {@code RigidBodyHandle} 用的是 SI + 秒）：
     * 位置误差 [格(=m)]、速度 [m/s]、角速度 [rad/s]、力 [N]。
     * {@code positionGain} 是"每米位置误差换算成多少 m/s 的期望速度"，
     * 即一阶跟随的时间常数 τ = 1 / positionGain 秒；速度阻尼必须与它成对调，
     * 临界阻尼条件为 {@code velocityDamping ≈ 2 × √positionGain}（都按每秒计）。
     */
    public final ConfigGroup gravitron = group(12, "gravitron", Comments.gravitron);
    public final ConfigFloat gravitronReach =
            f(16.0f, 1.0f, 128.0f, "reach", Comments.gravitronReach);
    public final ConfigFloat gravitronHoldDistance =
            f(6.0f, 1.0f, 64.0f, "holdDistance", Comments.gravitronHoldDistance);
    public final ConfigFloat gravitronPositionGain =
            f(1.2f, 0.05f, 8.0f, "positionGain", Comments.gravitronPositionGain);
    public final ConfigFloat gravitronVelocityDamping =
            f(2.0f, 0.0f, 20.0f, "velocityDamping", Comments.gravitronVelocityDamping);
    public final ConfigFloat gravitronAngularDamping =
            f(0.25f, 0.0f, 1.0f, "angularDamping", Comments.gravitronAngularDamping);
    public final ConfigFloat gravitronMaxSpeed =
            f(24.0f, 1.0f, 200.0f, "maxSpeed", Comments.gravitronMaxSpeed);
    public final ConfigFloat gravitronMaxAcceleration =
            f(60.0f, 1.0f, 1000.0f, "maxAcceleration", Comments.gravitronMaxAcceleration);
    public final ConfigFloat gravitronMaxHoldForce =
            f(5.0E7f, 1.0E3f, 1.0E12f, "maxHoldForce", Comments.gravitronMaxHoldForce);
    public final ConfigInt gravitronMaxGrabMass =
            i(2000000, 1, 2000000000, "maxGrabMass", Comments.gravitronMaxGrabMass);

    // ===================== 解算器光束（curiosities 切片追加，勿动上面的字段） =====================

    /**
     * 解算器（{@code solver}）光束的观感参数。
     *
     * <p>放在 SERVER 而不是 CLIENT，是因为这是"方块表现"的一部分，与其它 gameplay 参数放在一起
     * 便于整包同步；NeoForge 在客户端也会加载 SERVER 配置，渲染器读到的就是这里（同 Create 的
     * {@code AllConfigs.server()} 用法）。改动后重载配置即可生效，渲染器每帧只读不缓存。
     */
    public final ConfigGroup solver = group(13, "solver", Comments.solver);
    /** 光束总宽度 [格]：0.01 很细，1.0 与旧版 {@code width=1f} 相当。 */
    public final ConfigFloat solverBeamWidth =
            f(0.35f, 0.01f, 2.0f, "beamWidth", Comments.solverBeamWidth);
    /** 光束 RGB 颜色（0xRRGGBB），与 beam.png 的紫白贴图相乘。 */
    public final ConfigInt solverBeamColor =
            i(0xFFFFFF, 0x000000, 0xFFFFFF, "beamColor", Comments.solverBeamColor);

    @Override
    public String getName() {
        return "server";
    }

    private static class Comments {
        static final String physics = "物理施力的全局倍率与上限";
        static final String propellerForceMultiplier = "螺旋桨推力倍率（1.0 = 默认手感）";
        static final String propellerMaxForce = "单个螺旋桨每物理刻允许施加的最大力 [N]";
        static final String propellerMaxTorque = "单个螺旋桨每物理刻允许施加的最大力矩 [N*m]";
        static final String maxBladeSize = "单片螺旋桨叶片的最大展长 [格]（旧版同名配置默认 8）："
                + "工作台加长上限、长度夹取、叶片渲染包围盒都读它";
        static final String balloons = "热气球 / 浮力相关参数";
        static final String balloonForceMultiplier = "浮力倍率（1.0 = 默认手感）";
        static final String balloonMaxScanVolume = "气球内部体积扫描上限（方块数），防止卡顿";
        static final String balloonMaxScanSurface = "气球外壳扫描上限（方块数），防止卡顿";
        static final String balloonMaxRaycastDistance = "热气球封口射线检测的最大距离 [格]";
        static final String gas = "气体网络参数";
        static final String gasPressureDecay = "每刻压力自然衰减比例（0 = 不漏气）";
        static final String ductNodeCapacity = "单个管道节点可容纳的气体量";
        static final String gasAmbientTemperature = "环境温度 [K]，气体与环境换热的目标温度（旧版按维度/海拔给出）";
        static final String gasAmbientPressure = "环境压力 [Pa]，1 atm = 101325";
        static final String gasAmbientHeatExchange = "每刻与环境交换的温度差比例（0 = 完全绝热）";
        static final String gasFlowPerPascal = "连接上的压差到质量流量的系数 [kg/(tick*Pa)]，越大气体流动越快";
        static final String gasThermalConductivity = "普通金属连接的基础导热率 [1/tick]：每刻消掉的两侧温差比例（0 = 完全绝热；"
                + "导热与流动无关，负责把热量送到不在流路上的死端，如支管末端的斯特林机）；铜接头取它和铜值的较大者";
        static final String copperDuctFlowScale = "铜接头的通流倍率（1.0 = 与普通导管相同；只在铜接头参与的连接上生效）";
        static final String copperDuctThermalConductivity = "铜接头每刻消掉的两侧温差比例（与 gasThermalConductivity 取较大者；铜比普通金属管更快）";
        static final String smartDuctPressureThreshold = "智能边件的压差阈值 [Pa]，配合方块状态里的高于/低于档位使用";
        static final String smartDuctTemperatureThreshold = "智能边件的温度阈值 [K]，配合方块状态里的高于/低于档位使用";
        static final String airCompressorSpeed = "空气压缩机每 RPM 每刻的产气质量 [kg/(tick*RPM)]（旧版同名配置默认值）";
        static final String airCompressorMaxPressure = "空气压缩机停机压力 [Pa]，超过后不再产气（旧版同名配置）";
        static final String airCompressorHeliumAirDensity = "外部空气密度低于该值时副产以太（旧版同名配置；0 或负数 = 禁用）";
        static final String creativeGasGeneratorPressure = "创造气源维持的目标压力 [Pa]";
        static final String creativeGasGeneratorTemperature = "创造气源维持的目标温度 [K]";
        static final String coalBurnerMaxJoulesPerTick = "煤炉每刻最多注入的热能 [J]（旧版硬编码 10000，目标温度 850K）";
        static final String steamGeneratorMaxMass = "蒸汽提取器每刻最大产汽质量 [kg/tick]（旧版硬编码 0.1）";
        static final String engineTemperatureIncrement = "热气机温度增量 [K]：每高出 290K 这么多算一档，20 档满效率（旧版 60）";
        static final String engineMaxHeatLoss = "热气机满效率时每刻消耗的热能 [J/tick]（旧版 5000）";
        static final String sterlingEngineStressCapacity = "斯特林机满效率时的应力容量（旧版 16384）";
        static final String gasEngineFlowForFullEfficiency = "燃气机满效率所需的质量流量 [kg/s]（0 = 只要通气就满效率）";
        static final String gasEngineMinimumFlowRate = "燃气机视为「无流量」的阈值 [kg/s]，低于此值不产动力";
        static final String gasEngineFlowRateIncrement = "燃气机流量分档步长 [kg/s]（0 = 不分档）";
        static final String gasEngineTemperatureIncrement = "燃气机温度增量 [K]：每高出 290K 这么多算一档，20 档满效率（旧版 60）";
        static final String gasEngineMaxHeatLoss = "燃气机满效率时每刻抽取的热能 [J/tick]（旧版 5000）";
        static final String gasThrusterForceMultiplier = "气体推进器推力倍率（旧版 gasThrusterForceMul = 200）";
        static final String heaterSmoulderingTemp = "气体加热器达到被动热源档的温度 [K]（旧版 500）";
        static final String heaterKindledTemp = "气体加热器达到加热档的温度 [K]（旧版 800）";
        static final String heaterSeethingTemp = "气体加热器达到超级加热档的温度 [K]（旧版 1300）";
        static final String kinetics = "通用动力部件（通用轴 / 万向节）参数";
        static final String maxUniversalJointDistance = "通用轴 / 伸缩杆允许的最大连接距离 [格]，超出后连接自动崩断";
        static final String bearings = "物理轴承家族的伺服增益（按单位转动惯量计）";
        static final String bearingStiffness = "伺服刚度：越大越硬，跟随目标角越紧";
        static final String bearingDamping = "伺服阻尼：抑制子结构相对母体的摆动";
        static final String bearingFriction = "自由铰链状态的被动摩擦（0 = 完全无阻力）";
        static final String bearingLockStiffness = "锁定角度模式的刚度（要抵抗外力，通常比普通模式大）";
        static final String bearingLockDamping = "锁定角度模式的阻尼";
        static final String bearingMaxSpeed = "轴承允许的最大输入轴转速 [RPM]";
        static final String flap = "襟翼轴承的摆动参数";
        static final String flapAmplitudeDegrees = "摆动幅度 [度]，0 = 不动";
        static final String flapPhasePerShaftDegree = "输入轴每转 1 度推进的摆动相位 [弧度/度]，越大摆得越快";
        static final String infuser = "物理灌注器";
        static final String infuserAssemblyTicks = "从开始到真正装配所需的刻数（装配中再次右键可跳过）";
        static final String infuserMaxBlocks = "单次装配允许的最大方块数（防止选中过大区域卡死）";
        static final String attitude = "姿态控制（陀螺 / 反作用轮）";
        static final String gyroStrength = "陀螺稳定强度倍率（1.0 = 旧版手感），单位 1/s：乘在 0..100 的转速曲线上，"
                + "高转速下每子步已能消掉全部误差，再调大基本无感";
        static final String gyroMaxTorque = "单个陀螺每物理刻允许施加的最大力矩 [N*m]（绝对上限，兜底用）";
        static final String gyroMaxAngularAcceleration =
                "陀螺合计允许给结构带来的最大角加速度 [rad/s²]（<= 0 表示不限制，不推荐）："
                        + "与结构大小无关，是「失控时不被弹飞」的主限幅（多块时按块数摊薄）";
        static final String reactionWheelEffectiveness = "反作用轮的等效转速倍率：越大转向越猛（角动量守恒由 Sable 处理）";
        static final String wing = "机翼 / 襟翼气动参数（platform/CWWingForces 读取）";
        static final String wingArea = "单块机翼的参考面积 [m²]（旧版按方块数累加）";
        static final String wingForceMultiplier = "机翼升力/阻力的全局倍率（1.0 = 默认手感）";
        static final String wingMaxForce = "单块机翼每物理刻允许施加的最大力 [N]";
        static final String wingMaxAirspeed = "气动计算使用的最大来流速度 [m/s]：升力 ∝ v²，不封顶会正反馈爆炸并把结构推出 Y 保护范围";
        static final String wingMaxPressure = "气动计算使用的最大气压倍率（防止稠密大气维度里密度失控）";
        static final String wingMaxAcceleration = "单块机翼允许给结构带来的最大加速度 [m/s²]（按结构质量缩放；<= 0 表示不限制）";
        static final String sugarRocket = "糖火箭";
        static final String sugarRocketThrust = "每档推力 [N]（每颗糖 +1 档）";
        static final String sugarRocketMaxPower = "最大推力档位（加糖上限）";
        static final String ballooner = "热气球浮力（体积扫描模型）";
        static final String balloonerMaxScanVolume = "封口检测的体积上限 [方块数]，超过即判定漏气";
        static final String balloonerLiftPerBlock = "每方块内部空气提供的浮力 [N]";
        static final String spinoffBearing = "对接口（惰性轴承）：自由铰链参数";
        static final String spinoffBearingFriction = "自由铰链的粘性阻尼（0 = 完全无阻力；越大转动能量损失越快）";
        static final String gravitron = "重力枪（gravitron）抓取牵引参数；力与速度都是 SI 单位（N、m/s）";
        static final String gravitronReach = "右键能抓到的最远距离 [格]（同时也是装配/解体的瞄准距离）";
        static final String gravitronHoldDistance = "抓住后结构被拖到视线前方的距离 [格]";
        static final String gravitronPositionGain =
                "位置牵引增益 [1/s]：每 1 格位置误差换算成的期望速度（m/s）。越大越跟手，"
                        + "一阶时间常数 = 1/该值 秒；稳定性上限约 4 - 2*velocityDamping（按 20Hz 控制周期）";
        static final String gravitronVelocityDamping =
                "速度阻尼 [1/s]：越大越粘、越不容易过头；临界阻尼取 2*sqrt(positionGain)";
        static final String gravitronAngularDamping =
                "角阻尼：每物理子步消掉该比例的角动量（0 = 抓住后仍会自旋，1 = 贴死朝向）";
        static final String gravitronMaxSpeed =
                "结构被牵引时的最大速度 [m/s]（1 格/tick = 20 m/s），防止一帧拽成炮弹";
        static final String gravitronMaxAcceleration =
                "结构被牵引时的最大加速度 [m/s²]（原版创造飞行约 20 m/s²），远距离抓取时限制得住的唯一旋钮";
        static final String gravitronMaxHoldForce =
                "单次牵引允许的最大力 [N]（500 万克方块约 5e6 kg，默认 5e7 N 对应 10 m/s²）";
        static final String gravitronMaxGrabMass =
                "允许抓取的最大结构质量 [kg]（默认 1 方块 ≈ 1 kg；超过此值的结构抓不动，避免力大到把物理炸掉）";
        static final String solver = "解算器（solver）光束的观感参数";
        static final String solverBeamWidth = "光束总宽度 [格]（旧版 BeamRenderer 用 1.0；本移植默认 0.35 更接近可见激光）";
        static final String solverBeamColor = "光束颜色 0xRRGGBB，与 beam.png 贴图相乘（默认白色保留贴图紫白渐变）";
    }
}
