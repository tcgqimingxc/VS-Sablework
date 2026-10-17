# 全方块 / 全物品渲染覆盖审计（RENDER-AUDIT）

> 起因：实机反馈「空气压缩机模型异常、时钟指针错位、气泵无齿轮、气动活塞模型异常、软管卷轴不显示」，
> 并要求「检查所有方块的渲染」。那五项由另一任务负责；本文件覆盖**其余全部注册方块与物品**。
>
> 判据（每条都能静态复现，脚本 `tools/check_render_coverage.py`）：
> 1. **旧版 BER**：唯一权威是 `Clockwork-1.20.1-main/.../ClockworkBlockEntities.kt` 的 33 条 `.renderer` 调用，
>    按 `.validBlocks(...)` 反查到方块 id（不按类名，因为类名有改名）。
> 2. **partial**：`PartialModel.of()` 只在 `ModelEvent.RegisterAdditional` 时已存在才会被烘焙；
>    烘焙之后才创建就是 missingno（黑紫）。所以每个 `*Partials` 类必须有幂等 `init()` 且被
>    `CWClientSetup#onClientSetup` 调用。
> 3. **RenderShape.ENTITYBLOCK_ANIMATED**：旧版某些方块整块由 BER 画，漏设就是「静态一份 + 动态一份」。
> 4. **物品模型**：`models/item/<id>.json` 必须存在，且 parent / `#贴图键` 链可解析（1.21 对解析失败静默）。
> 5. **孤立子模型**：`models/block/<block>/*.json` 既不被 blockstate/parent 引用、也不被 Java 引用 →
>    该部件没有消费者，通常等于「BER 没写」。

## 0. 统计

- 注册方块 **68** 个（其中 **6** 个由另一任务负责：air_compressor, clock, duct_tank, extendon, hose_port, pump_duct）。
- 本轮审计范围：**62** 个方块 + **25** 个物品。
- 结论分布：✅ **61** / ⚠️ **1** / ❌ **0** / —（另一任务）**6**。
- 检查项合计问题 **0** 个（分项见 §3）。
- 上游遗留（官方自己也没引用，仅记录，不计入问题）**32** 个模型。

## 1. 全量矩阵（68 个注册方块）

列说明：**旧版 BER** = 1.20.1 由谁画；**工程 BER** = 1.21.1 现在由谁画；
**Shape** = RenderShape（`ANIM` 即 ENTITYBLOCK_ANIMATED）；**物品模型** = `models/item/<id>.json`
是否存在且 parent/贴图链可解析；结论 ✅ 正常 / ⚠️ 存疑（附理由，含"旧版有 BER 但本工程按设计没有"）/
❌ 缺失（缺 BER / 缺 RenderShape / 孤立子模型 / 物品模型坏）。

| 方块 | 类 | 旧版 BER | 工程 BER | Shape | 物品模型 | 结论 |
|---|---|---|---|---|---|---|
| `air_compressor` | AirCompressorBlock | AirCompressorRenderer | AirCompressorRenderer | model | ✅ | —（另一任务） |
| `alt_meter` | AltMeterBlock | AltMeterRenderer | AltMeterRenderer | model | ✅ | ✅ |
| `andesite_flap_bearing` | AndesiteFlapBearingBlock | FlapBearingRenderer | FlapBearingRenderer | model | ✅ | ✅ |
| `asteroid_block` | MeteorTestBlock | — | — | model | ✅ | ✅ |
| `balloon_casing` | Block | — | — | model | ✅ | ✅ |
| `ballooner` | BalloonerBlock | — | — | model | ✅ | ✅ |
| `blade_controller` | BladeControllerBlock | BladeControllerRenderer | BladeControllerRenderer | model | ✅ | ✅ |
| `brass_propeller_bearing` | — | BearingRenderer | PropellerBearingRenderer | model | ✅ | ✅ |
| `charged_nyx` | Block | — | — | model | ✅ | ✅ |
| `chiseled_wanderlite` | Block | — | — | model | ✅ | ✅ |
| `clock` | ClockBlock | ClockRenderer | ClockRenderer | model | ✅ | —（另一任务） |
| `coal_burner` | CoalBurnerBlock | — | — | model | ✅ | ✅ |
| `cobbled_nyx` | Block | — | — | model | ✅ | ✅ |
| `command_seat` | CommandSeatBlock | SequencedSeatRenderer | CommandSeatRenderer | model | ✅ | ✅ |
| `copter_bearing` | CopterBearingBlock | CopterBearingRenderer | CopterBearingRenderer | model | ✅ | ✅ |
| `creative_gas_generator` | CreativeGasGeneratorBlock | — | — | model | ✅ | ✅ |
| `delivery_cannon` | DeliveryCannonBlock | DeliveryCannonRenderer | DeliveryCannonRenderer | model | ✅ | ✅ |
| `delivery_chute` | DeliveryChuteBlock | FrequencySlotRenderer | FrequencySlotRenderer | model | ✅ | ✅ |
| `distance_sensor` | DistanceSensorBlock | — | — | model | ✅ | ✅ |
| `duct` | GasDuctBlock | DuctRenderer | DuctRenderer | model | ✅ | ✅ |
| `duct_tank` | DuctTankBlock | — | DuctTankRenderer | model | ✅ | —（另一任务） |
| `exhaust` | ExhaustBlock | — | — | model | ✅ | ✅ |
| `extendon` | ExtendonBlock | ExtendonRenderer | ExtendonRenderer | model | ✅ | —（另一任务） |
| `filter_duct` | FilterDuctBlock | — | — | model | ✅ | ✅ |
| `flap` | FlapBlock | WingBlockEntityRenderer | WingColorRenderer | model | ✅ | ✅ |
| `gas_backtank` | GasBacktankBlock | — | — | model | ✅ | ✅ |
| `gas_crafter` | GasCrafterBlock | GasCrafterBlockEntityRenderer | GasCrafterRenderer | model | ✅ | ✅ |
| `gas_engine` | GasEngineBlock | — | GasEngineRenderer | model | ✅ | ✅ |
| `gas_heater` | GasHeaterBlock | — | — | model | ✅ | ✅ |
| `gas_nozzle` | GasNozzleBlock | GasNozzleRenderer | GasNozzleRenderer | model | ✅ | ✅ |
| `gas_thruster` | GasThrusterBlock | — | — | model | ✅ | ✅ |
| `gimbal_bearing` | GimbalBearingBlock | — | GimbalBearingRenderer | model | ✅ | ✅ |
| `goo_block` | GooBlock | GooBlockEntityRenderer | — | model | ✅ | ⚠️ 旧版 GooBlockEntityRenderer.renderSafe 只有 super 调用（空实现），无动态部件 |
| `gyro` | GyroBlock | GyroBlockEntityRenderer | GyroRenderer | ANIM | ✅ | ✅ |
| `gyroscopic_sensor` | GyroscopicSensorBlock | — | — | model | ✅ | ✅ |
| `heat_pipe` | HeatPipeBlock | — | — | model | ✅ | ✅ |
| `hose_port` | HosePortBlock | — | — | model | ✅ | —（另一任务） |
| `impact_sensor` | ImpactSensorBlock | — | — | model | ✅ | ✅ |
| `intake` | IntakeBlock | — | — | model | ✅ | ✅ |
| `juryrigged_propeller_bearing` | — | BearingRenderer | PropellerBearingRenderer | model | ✅ | ✅ |
| `lodefocus` | LodefocusBlock | LodefocusRenderer | LodefocusRenderer | model | ✅ | ✅ |
| `nyx` | Block | — | — | model | ✅ | ✅ |
| `phys_bearing` | PhysBearingBlock | PhysBearingRenderer | PhysBearingRenderer | model | ✅ | ✅ |
| `physics_infuser` | PhysicsInfuserBlock | PhysicsInfuserRenderer | PhysicsInfuserRenderer | model | ✅ | ✅ |
| `pump_duct` | PumpDuctBlock | PumpDuctRenderer | PumpDuctRenderer | model | ✅ | —（另一任务） |
| `reactionwheel` | ReactionWheelBlock | ReactionWheelRenderer | ReactionWheelRenderer | ANIM | ✅ | ✅ |
| `redstone_duct` | RedstoneDuctBlock | — | — | model | ✅ | ✅ |
| `redstone_resistor` | RedstoneResistorBlock | RedstoneResistorRenderer | RedstoneResistorRenderer | model | ✅ | ✅ |
| `slicker` | SlickerBlock | SlickerBlockEntityRenderer | SlickerRenderer | model | ✅ | ✅ |
| `smart_flap_bearing` | SmartFlapBearingBlock | FlapBearingRenderer | FlapBearingRenderer | model | ✅ | ✅ |
| `smooth_wanderlite` | Block | — | — | model | ✅ | ✅ |
| `smooth_wanderlite_slab` | SlabBlock | — | — | model | ✅ | ✅ |
| `smooth_wanderlite_stairs` | — | — | — | model | ✅ | ✅ |
| `solver` | SolverBlock | SolverRenderer | SolverRenderer | model | ✅ | ✅ |
| `spinoff_bearing` | SpinoffBearingBlock | — | — | model | ✅ | ✅ |
| `steam_generator` | SteamGeneratorBlock | — | — | model | ✅ | ✅ |
| `sterling_engine` | SterlingEngineBlock | SterlingEngineRenderer | SterlingEngineRenderer | model | ✅ | ✅ |
| `straight_duct` | StraightDuctBlock | — | — | model | ✅ | ✅ |
| `sugar_rocket` | SugarRocketBlock | SugarRocketRenderer | SugarRocketRenderer | ANIM | ✅ | ✅ |
| `universal_shaft` | UniversalShaftBlock | UniversalShaftRenderer | UniversalShaftRenderer | ANIM | ✅ | ✅ |
| `valve_duct` | ValveDuctBlock | ValveDuctRenderer | ValveDuctRenderer | model | ✅ | ✅ |
| `wanderglass` | Block | — | — | model | ✅ | ✅ |
| `wanderlite_block` | Block | — | — | model | ✅ | ✅ |
| `wanderlite_bricks` | Block | — | — | model | ✅ | ✅ |
| `wanderlite_deepslate_ore` | WanderliteOreBlock | — | — | model | ✅ | ✅ |
| `wanderlite_end_ore` | WanderliteOreBlock | — | — | model | ✅ | ✅ |
| `wanderlite_nyx_ore` | WanderliteOreBlock | — | — | model | ✅ | ✅ |
| `wing` | WingBlock | WingBlockEntityRenderer | WingColorRenderer | model | ✅ | ✅ |

## 2. 物品（25 个注册物品）

| 物品 | 物品模型 | 旧版自定义渲染器 | 工程做法 | 结论 |
|---|---|---|---|---|
| `aeronaut_boots` | ✅ | — | 烘焙模型 | ✅ |
| `aeronaut_goggles` | ✅ | 旧版 AeronautGogglesRenderer 画护目镜三层；移植改用静态 item 模型 | 旧版 AeronautGogglesRenderer 画护目镜三层；移植改用静态 item 模型 | ✅ |
| `aeronaut_jacket` | ✅ | — | 烘焙模型 | ✅ |
| `aeronaut_jumpers` | ✅ | — | 烘焙模型 | ✅ |
| `bluperglue` | ✅ | — | 烘焙模型 | ✅ |
| `bluuguu` | ✅ | — | 烘焙模型 | ✅ |
| `creative_gravitron` | ✅ | — | 烘焙模型 | ✅ |
| `extendon_hose` | ✅ | — | 烘焙模型 | ✅ |
| `gravitron` | ✅ | — | 烘焙模型 | ✅ |
| `handheld_drill` | ✅ | — | 烘焙模型 | ✅ |
| `handheld_saw` | ✅ | — | 烘焙模型 | ✅ |
| `incomplete_hose_spool` | ✅ | — | 烘焙模型 | ✅ |
| `incomplete_telescoping_mechanism` | ✅ | — | 烘焙模型 | ✅ |
| `incomplete_wanderwand` | ✅ | — | 烘焙模型 | ✅ |
| `music_disc_wanderlust` | ✅ | — | 烘焙模型 | ✅ |
| `pastrymaker` | ✅ | — | 烘焙模型 | ✅ |
| `propeller_blade` | ✅ | 旧版 BladeItemRenderer 按 NBT BladeLength 拼三段；本工程 blade 长度尚未写入 NBT，静态模型见 models/item/propeller_blade.json | 旧版 BladeItemRenderer 按 NBT BladeLength 拼三段；本工程 blade 长度尚未写入 NBT，静态模型见 models/item/propeller_blade.json | ✅ |
| `screwdriver` | ✅ | — | 烘焙模型 | ✅ |
| `stratodonut` | ✅ | — | 烘焙模型 | ✅ |
| `universal_shaft_item` | ✅ | — | 烘焙模型 | ✅ |
| `wanderlite_crystal` | ✅ | — | 烘焙模型 | ✅ |
| `wanderlite_cube` | ✅ | 旧版 WanderliteCubeItemRenderer 用自研 render type 画多层立方体；移植改用静态立方体模型（models/item/wanderlite_cube.json 顶部 credit 已注明） | 旧版 WanderliteCubeItemRenderer 用自研 render type 画多层立方体；移植改用静态立方体模型（models/item/wanderlite_cube.json 顶部 credit 已注明） | ✅ |
| `wanderlite_matrix` | ✅ | 同上（静态多一层外壳的立方体模型） | 同上（静态多一层外壳的立方体模型） | ✅ |
| `wanderwand` | ✅ | — | 烘焙模型 | ✅ |
| `wide_propeller_blade` | ✅ | 同上（宽叶版） | 同上（宽叶版） | ✅ |

## 3. 检查项输出

### [RC1] BER 覆盖（旧版 .renderer{} ↔ 工程 .renderer()）：0 个
- 无
### [RC2] partial 登记 / 提前 init / 模型文件：0 个
- 无
### [RC3] RenderShape（ENTITYBLOCK_ANIMATED）对照：0 个
- 无
### [RC4] 物品模型与 parent / 贴图链：0 个
- 无
### [RC5] 自定义物品渲染器对照：0 个
- 无
### [RC6] 孤立子模型（无人引用）：0 个
- 无
### [RC7] kinetic 渲染器依赖 super.renderSafe：0 个
- 无

## 4. 修复记录

本轮（2026-10-17）按「全方块/全物品穷举」修掉 12 项，全部有静态证据（旧版类名 + 行号）：

| # | 方块 / 项 | 原因（证据） | 修法 |
|---|---|---|---|
| F1 | `sterling_engine` | 旧版 `ClockworkBlockEntities.kt:665` 有 `SterlingEngineRenderer`（飞轮 = Create `MECHANICAL_PUMP_COG`），工程没登记 → 无齿轮 | 新增 `engine/client/SterlingEngineRenderer`（`getRotatedModel` 与旧版逐字一致）+ 登记 `.renderer()` |
| F2 | `alt_meter` | `:314` `AltMeterRenderer` 缺失；`block/alt_meter/redstone` 只可能由 BER 画 | 新增 `altmeter/client/AltMeterRenderer` + `AltMeterPartials`（提前 init），颜色公式照抄 |
| F3 | `andesite_flap_bearing` / `smart_flap_bearing` | `:269` / `:294` `FlapBearingRenderer` 缺失；顶盖 `block/flap_bearing/top` 与半根轴无人画 | 新增 `flap/client/FlapBearingRenderer` + `FlapBearingPartials`，两个方块实体共用 |
| F4 | `duct` | `:462` `DuctRenderer` 缺失；`duct/connection`、`duct/leak`、`duct/rim` 三个接口件无人画（blockstate 只有管身） | 新增 `duct/client/DuctRenderer` + `DuctPartials`，逐面按 `DuctConnectionType` 画 |
| F5 | `valve_duct` | `:523` `ValveDuctRenderer` 缺失；`valve_duct/pointer` 无人画 → 看不出开度 | 新增 `valve/client/ValveDuctRenderer` + `ValveDuctPartials`，指针按 `openness()` 转 0→-90° |
| F6 | `gas_nozzle` | `:605` `GasNozzleRenderer` 缺失；`gas_nozzle/dial`、`/axis` 无人画 | 新增 `nozzle/client/GasNozzleRenderer` + `GasNozzlePartials`；本移植喷嘴非动力方块，两件按姿态静态绘制（见 §5） |
| F7 | `delivery_cannon` | `:784` `DeliveryCannonRenderer` 缺失；blockstate 只引用 3 元素底座，炮座/转台/炮管/天线（共 11 元素）无人画 → 世界里只是一座空底座 | 新增 `cannon/client/DeliveryCannonRenderer` + `DeliveryCannonPartials`，按静止姿态绘制并复刻 blockstate 的 y 旋转 |
| F8 | `lodefocus` | `:333` `LodefocusRenderer` 缺失；装进去的指南针无处显示 | 新增 `sensor/rotation/client/LodefocusRenderer`；给 `LodefocusBlockEntity` 加只读 `getCompass()` |
| F9 | `sugar_rocket` | 旧版 `SugarRocketBlock.kt:40` 返回 `ENTITYBLOCK_ANIMATED`，工程漏设；而 `SugarRocketRenderer` 会整块重画 → 静态一份 + BER 一份叠着，燃烧染色只染一份 | `SugarRocketBlock` 补 `getRenderShape() = ENTITYBLOCK_ANIMATED` |
| F10 | `reactionwheel` | 依赖 `super.renderSafe` 画轴：父类在 Flywheel 可视化开启时直接 return（本工程无 visual）→ 轴永不绘制；且未覆写 `getRenderedBlockState`，Flywheel 关闭时会把轮子叠画一遍 | 改显式 `renderRotatingBuffer`，并补 `getRenderedBlockState() = shaft(axis)`（旧版原本就是这么写的） |
| F11 | `brass_propeller_bearing` / `juryrigged_propeller_bearing`、`phys_bearing`、`redstone_resistor` | 同上：`super.renderSafe` 在本工程等于"不画"，其中前两者是输入轴/整体，后两者会把方块叠一层 | 统一改成显式 `renderRotatingBuffer` / 去掉无用的父类调用（类注释写明原因） |
| F12 | `CWClientSetup` | 新增 6 个 partial 类必须提前登记，否则烘焙后创建 = missingno 黑紫 | 追加 `AltMeterPartials/FlapBearingPartials/DuctPartials/ValveDuctPartials/GasNozzlePartials/DeliveryCannonPartials.init()`，并在各 partial 类头写明"为什么必须提前 init" |

### 2026-10-17 收口轮（F13–F16）

| # | 方块 / 项 | 原因（证据） | 修法 |
|---|---|---|---|
| F13 | `solver` 光束 | 旧版 `SolverRenderer.kt:69` 用自研 `ClockworkRenderTypes.BEAM`（`POSITION_COLOR_TEX_LIGHTMAP` + 自注册 shader）+ `util/beam/BeamRenderer` 画 64 格滚动贴图；本移植一条都没搬，实机完全看不到光束 | 新增 `solver/client/SolverRenderer`：改用原版 `RenderType.entityTranslucentEmissive(beam.png)`（javap 核实 `NO_CULL + COLOR_WRITE + emissive`），4 条 45° 交叉带子 + 亮芯；**不用** `RenderType.lightning()`（javap 核实其绑定 `WEATHER_TARGET`）。服务端新增 `beamDistance` 随 `sendData()` 同步（旧版每帧客户端 `clip`），宽度/颜色进 `CWServer.solverBeamWidth/solverBeamColor` |
| F14 | `delivery_chute` 频率槽 | 旧版 `DeliveryChuteBlockEntities` 登记 `FrequencySlotRenderer<DeliveryChuteBlockEntity>`，槽位来自 `FrequencySlotBehaviour.kt`；本移植既无 behaviour 也无渲染器，频率只是隐藏字段 | 新增 `frequency_slot/FrequencySlotBehaviour`（NBT 键沿用 `Frequency`、剪贴板 `DeliveryFrequencies`、`setFrequency` 只存 1 个）+ `FrequencySlotRenderer`（`ValueBoxRenderer` 画在滑道顶部开口中心 y=15/16）。交互：潜行右键 / 直击槽位设频率，空手潜行清空；`hasFrequency` 与加农炮匹配逻辑不变 |
| F15 | `command_seat` 轴半块 | 旧版 `SequencedSeatRenderer.kt:33` 画 1 根朝下 + 4 根水平 `AllPartialModels.SHAFT_HALF`；审计曾误记"轴已做进 block 模型"，实际 `models/block/command_seat/block.json` 15 个 element 全是座体（坐标 6..10 的轴截面一个都没有） | 新增 `sequenced_seat/client/CommandSeatRenderer`：朝下输入轴沿用旧版 `renderRotatingBuffer`（随输入转速转），4 根水平输出轴静态绘制（旧版输出轴旋转代码本就注释掉）；`CWKineticsIndex` 补 `.renderer()`。`buttonone/buttontwo/joystick` 三个子模型保持不用（旧版 blockstate/渲染器同样没引用，属上游遗留） |
| F16 | `heat_pipe` 六档热度 | 200 个 `*_warm/_hot/_very_hot/_molten/_super_hot` 模型无人引用；blockstate 只按六面连接选常温模型。模型还是按 `[0,0,6,6]` 硬编码 UV 生成的，而 `heatpipe_connection` 内容区在 (0,6)-(16,16)、`heatpipe_rim` 在 0..7×0..16，直接引用会采到透明区域 | `HeatPipeBlock.Heat`（cool/warm/hot/very_hot/molten/super_hot，阈值 373.15/500/800/1300/2000 K，对齐加热器三段温度）+ `HEAT` 属性；`HeatPipeBlockEntity` 每 20 tick 检查、按 `worldPosition` 哈希错相位、**跨档才** `setBlockAndUpdate`。`tools/gen_heat_pipe_blockstate.py` 从现有条件幂等展开 38×6=228 条 multipart；`tools/gen_heat_pipe_models.py` 改为按贴图内容区 + 统一 `UV_SCALE=0.97` 取景，删掉 10 个无消费者的 straight/item_straight 热度变体。`[13]` 提示 27→2（仅剩 valve_duct 旧提示） |
| — | `goo_block` | 旧版 `GooBlockEntityRenderer.renderSafe` 只有 `super`（空实现） | 本轮确认**不做**，保持 `KNOWN_EQUIVALENT` |

## 5. 已判定但本轮未修（在案，不计入问题数）

- 无

## 6. 上游遗留模型（官方自己也没引用，仅记录）

`block/flap_bearing_top`, `block/peepotron`, `block/vstop`, `block/blade_controller/blade_controller`, `block/blade_controller/blade/blade`, `block/blade_controller/blade/wideblade`, `block/combustion_engine/main`, `block/combustion_engine/single_piston`, `block/command_seat/buttonone`, `block/command_seat/buttontwo`, `block/command_seat/joystick`, `block/creative_gas_generator/item`, `block/duct/block`, `block/flap/flap`, `block/flap_bearing/block`, `block/flap_bearing/block_horizontal`, `block/flap_bearing/block_vertical`, `block/gyro/gyro`, `block/juryrigged_infuser/item`, `block/physics_infuser/core` …

## 7. 仍需实机确认 / 无法静态判定

1. **襟翼轴承顶盖的角度来源**：旧版用 `BearingBlockEntity#getInterpolatedAngle`（伺服摆角）。
   本移植的 `AbstractBearingBlockEntity` 每刻推进 `targetAngleDegrees` 但<b>不</b> `sendData()`，
   客户端拿不到伺服角，因此改用输入轴角（`getAngleForBe`）。**需实机确认**：顶盖"随输入轴转"
   是否可接受，若要还原成"随襟翼摆"，需要在 BE 里每刻同步目标角（非渲染改动，本轮未做）。
2. **气嘴表盘/叶轮轴**：本移植的 `gas_nozzle` 不是动力方块（类注释：没有输入轴），
   没有转速或开度可驱动指针，因此按静态绘制（指针固定 180°）。**需实机确认**观感；
   若后续把"喷气量/气压"接到表盘，只需把 `GasNozzleRenderer.NEEDLE_DEGREES` 换成动态值。
3. **交付加农炮**：只恢复静止姿态（BE 明确不做转头动画，也没有 `xRot/yRot` 状态）。
   炮身完整了，但**不会瞄准**。若要做动画，须先把旧版 `xRot/yRot/clientShotProgress` 搬回 BE。
4. **`solver` 光束是"近似还原"**：没有照搬旧版自研 shader，改用原版
   `entityTranslucentEmissive(beam.png)` + 4 条 45° 交叉带子；颜色/宽度在
   `CWServer.solver(solverBeamColor / solverBeamWidth)`。**需实机确认**：贴图滚动方向/亮度是否可接受；
   旧版的命中点迸射（`renderImpactBurst`）没有画，若需要可在 `SolverRenderer` 末尾按
   `start + facing × distance` 再画一组带子。性能上每帧不建对象，但 4 条 shell + 4 条 core
   共 8 个 quad、64 格长度，多个解算器同屏时建议看一眼 FPS。
5. **`delivery_chute` 频率槽**：槽位在滑道顶部开口中心 y=15/16（旧版位置在顶板下方，实机不可见），
   物品用 `ValueBoxRenderer` 的 FIXED 姿态平躺。**需实机确认**：物品是否浮得太高/穿模；
   若位置不合适，只改 `FrequencySlotBehaviour.ChuteSlot.LOCAL_OFFSET` 一个常量。
   交互：潜行右键或直接点中槽位设置频率；空手潜行右键清空；方块物品仍走放置。
6. **`command_seat` 的 5 根轴**：朝下输入轴沿用旧版 `renderRotatingBuffer`（随输入转速转，
   `getSpeed` 由 Create 同步），四根水平输出轴静态绘制（旧版输出轴旋转代码本就注释掉）。
   **需实机确认**：轴是否与相邻 Create 轴对齐（`hasShaftTowards` 除 UP 外全 true）；
   `buttonone/buttontwo/joystick` 仍是上游遗留、未引用。
7. **`heat_pipe` 六档外观**：阈值是纯观感常量（373.15/500/800/1300/2000 K），与加热器的
   500/800/1300 K 对齐；每 20 tick 检查一次、跨档才写 blockstate。**需实机确认**：
   温度在阈值附近时外观是否稳定（不应每 tick 闪）、20 tick 延迟是否可接受；
   若觉得换档太慢/太快，改 `HeatPipeBlockEntity.HEAT_REFRESH_TICKS` 一个常量即可。
8. **`duct/custom/*` 四个边件**（单向/过滤/智能）同样无人引用：`DuctConnectionType` 只有
   4 个值，边类型的玩法入口未移植（该类注释已说明）。
9. **无法静态判定**：Flywheel 的 `supportsVisualization` 在目标客户端是否为真（取决于后端设置）、
   BER 实际可见性/朝向是否与截图一致 —— 本轮只做到"代码路径正确 + 资源齐全"，最终观感需实机截图确认。
10. 本轮未改：`air_compressor`、`clock`、`pump_duct`、`extendon`、`hose_port`、`duct_tank`
    （另一任务范围内）；`content/logistics/gas/tank/**` 与 Ponder 集成同样未动。
