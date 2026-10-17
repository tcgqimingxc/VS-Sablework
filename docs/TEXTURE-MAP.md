# 贴图 ↔ 方块/物品 对应表（vs_clockwork）

> 本文件由 `tools/map_textures.py` **自动生成**。手工结论写在脚本的 `MANUAL_NOTES` 里，重跑不会丢；要改结论请改脚本后重跑，不要手改本文。

## 0. 生成方式与数据来源

```powershell
python tools/map_textures.py            # 生成/刷新 docs/TEXTURE-MAP.md
python tools/map_textures.py --check    # 只校验（不写文件）：工程树/jar 里有运行时可见的缺图或缺失模型 → 退出码 1；只在官方树旧版里的坏引用只报警告；另外校验本次收口删除项确实不在工程树/jar、保留的 WIP blockstate 还在
python tools/map_textures.py -v         # 额外打印孤儿 / 缺图清单
```

| 来源 | 路径 | 用途 |
|---|---|---|
| 官方资源树（权威） | `assets/vs_clockwork/` | 贴图清点基准（166 个文件）；块/物品模型、blockstates |
| 工程资源树 | `clockwork_unoffical/src/main/resources/assets/vs_clockwork/` | 移植后的实际资源，**运行时优先**；与官方树有差异 |
| 产物 jar | `clockwork_unoffical/build/libs/vs_clockwork-0.6.0-1.21.1.jar`（7.8 MB） | 打包结果（应与工程树一致） |
| 依赖 jar（14 个） | `clockwork_unoffical/libs/*.jar` | 跨命名空间解析（create: / sable: / simulated: …） |
| 原版 jar | `C:\Users\yql11\OneDrive\Desktop\.minecraft\libraries\net\minecraft\client\1.21.1-20240808.144430\client-1.21.1-20240808.144430-extra.jar` | `minecraft:` 命名空间的模型/贴图存在性 |
| 注册快照 | `tools/blockstate_properties.json`（68 个方块，1.21.1 / NeoForge 21.1.251） | 判定「方块是否真的注册了」 |
| Java 引用 | `clockwork_unoffical/src/main/java/`（301 个文件） | PartialModel 字面量 / 动态前缀、renderer 里直接绑定的 PNG |

## 1. 判定规则（先看这里，再看表）

1. **引用** = 模型 `textures` 字段里的具体贴图 id（`#变量` 沿 `parent` 链解析；OBJ 模型按 `mtllib` → `map_Kd #键` 取用）。`"."` / `""` 是占位键，不算引用。
2. **可达** = 能被 `blockstates/<id>.json`、`models/item/<id>.json`（仅第一层）、或 Java 里的 PartialModel 引用（静态字面量 + `"block/x/" + name` 动态前缀）烘焙到。`models/block/**` 之间互相 parent **不算**可达 —— 一组死模型互相引用仍然是死的。
3. 分类：
   - **已用**：可达模型的**面 / particle / OBJ 材质**真正取用了它；
   - **已用(Java)**：没有模型引用，但 Java 渲染器直接绑定 `textures/block/x.png`（只看模型会把它误判成孤儿）；
   - **已烘焙未上面**：可达模型声明了它，但没有任何面/particle/材质取用（多写的键或 OBJ 备用键）；
   - **仅声明**：只有不可达的模型声明它 → **功能没接**（贴图+模型都在，游戏里烘焙不到）；
   - **孤儿**：任何来源的任何模型都没声明过它；
   - **已清理**：已从工程树 / 产物 jar 删除、仅官方树还留着的旧资源（本次资源卫生收口，见附录 D）。
4. **运行时存在性**按 工程树 → 产物 jar 查；官方树只作对照（不参与构建）。跨命名空间引用（`create:` 等）在依赖 jar / 原版 jar 里查。**工程树优先于官方树**用于解析模型链（运行时用的是工程树 / 产物 jar）。
5. `textures/block/` 下 `.mcmeta` 是贴图的动画定义（附属件），`.aseprite` / `.bbmodel` / `.gpl` / `.obj` / `.mtl` 是制作期资源，均**不计入贴图数**。
6. 「方块是否注册」来自运行时快照 `tools/blockstate_properties.json`；只有 blockstate、没有注册点的方块 → blockstate 是死的（贴图也不会显示）。

## 2. 统计摘要

| 项 | 数量 | 说明 |
|---|---|---|
| `textures/block/` 文件总数 | 166 | 含子目录、含附属件 |
| 其中 PNG 贴图 | 155 | 主表一行一个 |
| 其中 `.png.mcmeta` | 10 | 动画定义，挂在对应 PNG 行下 |
| 其中其它（制作期资源） | 1 | `.aseprite` 等，见 §10 |
| **已用** | 115 | 含 Java 直接引用 3 张 |
| **已清理**（本次删除，仅官方树保留） | 26 | 附录 D.1；不参与构建，不算孤儿/仅声明 |
| **孤儿**（无任何模型声明、仍发布） | 7 | §5；其中保留待接的已逐条标注 |
| **仅声明**（模型有、烘焙不到、仍发布） | 3 | §6，功能未接的主战场 |
| 仅官方树旧版模型引用 | 4 | §4.3（工程树已改，不算缺口） |
| 已烘焙未上面 | 0 | 可达模型里多写的键 |
| **缺图引用**（黑紫） | 2 | §7；全部只在官方树旧版模型里，运行时已无黑紫 |
| 官方树独有 / 工程树新增 | 26 / 6 | §4；官方树独有的 26 张全部是本次收口删除（附录 D.1），不是意外缺失 |
| 贴图挂在**未注册方块**上 | 4 | §9.1（整套功能没移植） |

- 官方树 PNG：155 张；工程树同目录 PNG：135 张（差集 = 工程新增 6 张 / 工程缺失 26 张；缺失的 26 张全部是本次收口删除，见附录 D.1）。
- 产物 jar 内 `textures/block/**`：135 张。
- 模型 629 个（三来源去重），能被根烘焙到的 550 个；**工程树 / 产物 jar 实际发布**：模型 607 个、blockstates 71 个、物品模型 100 个；三来源 blockstates 77 个、物品模型 101 个、Java PartialModel 引用 7 个（另 65 个靠动态前缀解析到）。
- 已注册方块 68 个；工程树死 blockstate 3 个（§9.1；收口前 9 个：5 删 / 3 保留 / 1 仅官方树）。

## 3. 主表：`textures/block/**` 每个贴图 → 谁在用

列说明：`官方/工程/jar` = 该 PNG 在这三个来源里是否存在；`方块/Java 模型` = 真正取用它的方块 id 或 Java partial 模型路径；`物品` = 只列物品独有的 id（与方块同名时归到方块列）；`注册` = 该贴图服务的方块是否真的注册了（**✗ 未注册** = 资源整套在、方块没注册）。状态为 **已清理** 的行只在官方树留档，工程树 / jar 里已经没有这张图。

| # | 贴图 | 尺寸 | 动画 | 官方 | 工程 | jar | 方块 / Java 模型 | 物品 | Java | 注册 | 状态 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | `block/afterblazer.png` | 128×128 |  | ✓ | ✓ | ✓ | `afterblazer` | — | — | **✗ 未注册** | 已用 |
| 2 | `block/alt_meter.png` | 32×32 |  | ✓ | ✓ | ✓ | `gyro` | — | — | ✓ | 已用 |
| 3 | `block/alt_meter_on.png` | 32×32 |  | ✓ | ✓ | ✓ | — | — | — | — | 孤儿 |
| 4 | `block/altimeter.png` | 32×32 |  | ✓ | ✓ | ✓ | `alt_meter` | — | — | ✓ | 已用 |
| 5 | `block/altimeter_redstone.png` | 32×32 |  | ✓ | ✓ | ✓ | `alt_meter` | — | — | ✓ | 已用 |
| 6 | `block/andesite_flap_bearing.png` | 48×48 |  | ✓ | ✓ | ✓ | `andesite_flap_bearing`, `old_andesite_flap_bearing` | — | — | ✓ | 已用 |
| 7 | `block/angry.png` | 32×32 |  | ✓ | ✓ | ✓ | — | — | — | — | 孤儿 |
| 8 | `block/asteroid_block.png` | 16×16 |  | ✓ | ✓ | ✓ | `asteroid_block` | — | — | ✓ | 已用 |
| 9 | `block/balloon_casing.png` | 16×16 |  | ✓ | ✓ | ✓ | `balloon_casing`, `balloon_encased_shaft` | — | — | ✓ | 已用 |
| 10 | `block/balloon_casing_connected.png` | 128×128 |  | ✓ | ✓ | ✓ | — | — | — | — | 孤儿 |
| 11 | `block/balloon_gearbox.png` | 16×16 |  | ✓ | ✓ | ✓ | `balloon_encased_shaft` | — | — | **✗ 未注册** | 已用 |
| 12 | `block/blade.png` | 32×32 |  | ✓ | ✓ | ✓ | `blade_controller` | `propeller_blade` | — | ✓ | 已用 |
| 13 | `block/blade_controller.png` | 64×64 |  | ✓ | ✓ | ✓ | `blade_controller` | — | — | ✓ | 已用 |
| 14 | `block/blaze_fury.png` | 16×16 |  | ✓ | ✓ | ✓ | — | — | — | — | 孤儿 |
| 15 | `block/blue_hue.png` | 16×16 |  | ✓ | ✓ | ✓ | — | — | — | — | 孤儿 |
| 16 | `block/brass_propeller.png` | 32×32 |  | ✓ | ✓ | ✓ | `brass_propeller_bearing` | — | — | ✓ | 已用 |
| 17 | `block/brass_redstoneresistor.png` | 64×64 |  | ✓ | ✓ | ✓ | — | `brass_redstone_resistor` | — | — | 已用 |
| 18 | `block/burner.png` | 64×64 |  | ✓ | ✓ | ✓ | `ballooner` | — | — | ✓ | 已用 |
| 19 | `block/cannon_barrel.png` | 32×32 |  | ✓ | — | — | — | — | — | — | 已清理 |
| 20 | `block/cannon_barrel_largecheckers.png` | 32×32 |  | ✓ | ✓ | ✓ | `delivery_cannon`, `delivery_chute` | — | — | ✓ | 已用 |
| 21 | `block/cannon_base.png` | 32×32 |  | ✓ | ✓ | ✓ | `delivery_cannon` | — | — | ✓ | 已用 |
| 22 | `block/charged_nyx.png` | 16×16 |  | ✓ | ✓ | ✓ | `charged_nyx` | — | — | ✓ | 已用 |
| 23 | `block/chiselled_wanderlite.png` | 16×16 |  | ✓ | ✓ | ✓ | `chiseled_wanderlite` | — | — | ✓ | 已用 |
| 24 | `block/chiselled_wanderlite_side.png` | 16×16 |  | ✓ | ✓ | ✓ | `chiseled_wanderlite` | — | — | ✓ | 已用 |
| 25 | `block/clock.png` | 64×64 |  | ✓ | ✓ | ✓ | `clock` | — | — | ✓ | 已用 |
| 26 | `block/coal_burner.png` | 64×64 |  | ✓ | ✓ | ✓ | `coal_burner` | — | — | ✓ | 已用 |
| 27 | `block/coal_burner_flame.png` | 16×320 | 动画 20f | ✓ | ✓ | ✓ | `coal_burner` | — | — | ✓ | 已用 |
| 28 | `block/cobbled_nyx.png` | 16×16 |  | ✓ | ✓ | ✓ | `cobbled_nyx` | — | — | ✓ | 已用 |
| 29 | `block/combustion_engine.png` | 64×64 |  | ✓ | ✓ | ✓ | `combustion_engine` | — | — | **✗ 未注册** | 已用 |
| 30 | `block/commandseat.png` | 128×128 |  | ✓ | ✓ | ✓ | `command_seat` | — | — | ✓ | 已用 |
| 31 | `block/compressible_axis.png` | 4×14 |  | ✓ | ✓ | ✓ | — | — | ✓ | — | 已用(Java) |
| 32 | `block/compressor.png` | 32×32 |  | ✓ | ✓ | ✓ | `air_compressor` | — | — | ✓ | 已用 |
| 33 | `block/compressor_fabric.png` | 32×32 |  | ✓ | ✓ | ✓ | `air_compressor` | — | — | ✓ | 已用 |
| 34 | `block/compressor_old.png` | 32×32 |  | ✓ | — | — | — | — | — | — | 已清理 |
| 35 | `block/copper_duct.png` | 16×16 |  | ✓ | ✓ | ✓ | — | — | — | — | 仅声明 |
| 36 | `block/core.png` | 32×32 |  | ✓ | ✓ | ✓ | — | `physics_infuser` | — | — | 已用 |
| 37 | `block/creative_gas_generator.png` | 64×64 |  | ✓ | ✓ | ✓ | `creative_gas_generator` | — | — | ✓ | 已用 |
| 38 | `block/deepslate_wanderlite_ore.png` | 16×160 | 动画 10f | ✓ | ✓ | ✓ | `wanderlite_deepslate_ore` | — | — | ✓ | 已用 |
| 39 | `block/deepslate_wanderlite_ore_top.png` | 16×160 | 动画 10f | ✓ | ✓ | ✓ | `wanderlite_deepslate_ore` | — | — | ✓ | 已用 |
| 40 | `block/delivery_chute.png` | 64×64 |  | ✓ | ✓ | ✓ | `delivery_chute` | — | — | ✓ | 已用 |
| 41 | `block/doink.png` | 32×32 |  | ✓ | — | — | — | — | — | — | 已清理 |
| 42 | `block/doinkborder.png` | 32×32 |  | ✓ | — | — | — | — | — | — | 已清理 |
| 43 | `block/duct.png` | 32×32 |  | ✓ | ✓ | ✓ | `duct`, `exhaust`, `gas_engine`, `redstone_duct` +2 | — | — | ✓ | 已用 |
| 44 | `block/duct_old.png` | 32×32 |  | ✓ | — | — | — | — | — | — | 已清理 |
| 45 | `block/duct_pump.png` | 32×32 |  | ✓ | ✓ | ✓ | `pump_duct` | — | — | ✓ | 已用 |
| 46 | `block/duct_tank_connected.png` | 64×64 |  | ✓ | ✓ | ✓ | — | — | — | — | 仅官方树 |
| 47 | `block/duct_tank_placeholder.png` | 32×32 |  | ✓ | — | — | — | — | — | — | 已清理 |
| 48 | `block/duct_tank_top_connected.png` | 64×64 |  | ✓ | ✓ | ✓ | — | — | — | — | 仅官方树 |
| 49 | `block/electricity.png` | 32×480 | 动画 15f | ✓ | — | — | — | — | — | — | 已清理 |
| 50 | `block/empty.png` | 16×16 |  | ✓ | — | — | — | — | — | — | 已清理 |
| 51 | `block/end_wanderlite_ore.png` | 16×160 | 动画 10f | ✓ | ✓ | ✓ | `wanderlite_end_ore` | — | — | ✓ | 已用 |
| 52 | `block/extendon.png` | 64×64 |  | ✓ | ✓ | ✓ | `extendon`, `hose_port` | — | — | ✓ | 已用 |
| 53 | `block/flap.png` | 64×64 |  | ✓ | ✓ | ✓ | `flap` | — | — | ✓ | 已用 |
| 54 | `block/flap_bearing.png` | 64×64 |  | ✓ | ✓ | ✓ | `OLD_flap_bearing`, `block/flap_bearing/top` | `flap_bearing` | — | **✗ 未注册** | 已用 |
| 55 | `block/fluid_tank.png` | 16×16 |  | ✓ | ✓ | ✓ | — | — | — | — | 仅官方树 |
| 56 | `block/fluid_tank_inner.png` | 16×16 |  | ✓ | ✓ | ✓ | `duct_tank` | — | — | ✓ | 已用 |
| 57 | `block/fluid_tank_top.png` | 16×16 |  | ✓ | ✓ | ✓ | — | — | — | — | 仅官方树 |
| 58 | `block/fuming.png` | 32×32 |  | ✓ | ✓ | ✓ | — | — | — | — | 孤儿 |
| 59 | `block/gas_backtank.png` | 32×32 |  | ✓ | ✓ | ✓ | `gas_backtank` | — | — | ✓ | 已用 |
| 60 | `block/gas_crafter.png` | 32×32 |  | ✓ | — | — | — | — | — | — | 已清理 |
| 61 | `block/gas_engine_front.png` | 16×16 |  | ✓ | — | — | — | — | — | — | 已清理 |
| 62 | `block/gas_engine_side.png` | 16×16 |  | ✓ | — | — | — | — | — | — | 已清理 |
| 63 | `block/gas_heater.png` | 32×32 |  | ✓ | ✓ | ✓ | `gas_heater` | — | — | ✓ | 已用 |
| 64 | `block/gas_pump.png` | 40×40 |  | ✓ | — | — | — | — | — | — | 已清理 |
| 65 | `block/gas_thruster.png` | 64×64 |  | ✓ | ✓ | ✓ | `gas_thruster` | — | — | ✓ | 已用 |
| 66 | `block/goo.png` | 16×16 |  | ✓ | ✓ | ✓ | `goo_block` | — | — | ✓ | 已用 |
| 67 | `block/gooborder.png` | 16×16 |  | ✓ | ✓ | ✓ | `goo_block` | — | — | ✓ | 已用 |
| 68 | `block/gyro.png` | 32×32 |  | ✓ | ✓ | ✓ | `gyro` | — | — | ✓ | 已用 |
| 69 | `block/heat_valve.png` | 32×32 |  | ✓ | ✓ | ✓ | `valve_duct` | — | — | ✓ | 已用 |
| 70 | `block/heatpipe_connection.png` | 16×16 |  | ✓ | ✓ | ✓ | `filter_duct`, `heat_pipe`, `straight_duct` | — | — | ✓ | 已用 |
| 71 | `block/heatpipe_connection_hot.png` | 16×16 |  | ✓ | ✓ | ✓ | `heat_pipe` | — | — | ✓ | 已用 |
| 72 | `block/heatpipe_connection_molten.png` | 16×16 |  | ✓ | ✓ | ✓ | `heat_pipe` | — | — | ✓ | 已用 |
| 73 | `block/heatpipe_connection_super_hot.png` | 16×16 |  | ✓ | ✓ | ✓ | `heat_pipe` | — | — | ✓ | 已用 |
| 74 | `block/heatpipe_connection_very_hot.png` | 16×16 |  | ✓ | ✓ | ✓ | `heat_pipe` | — | — | ✓ | 已用 |
| 75 | `block/heatpipe_connection_warm.png` | 16×16 |  | ✓ | ✓ | ✓ | `heat_pipe` | — | — | ✓ | 已用 |
| 76 | `block/heatpipe_core.png` | 16×16 |  | ✓ | ✓ | ✓ | `filter_duct`, `heat_pipe`, `straight_duct` | — | — | ✓ | 已用 |
| 77 | `block/heatpipe_core_hot.png` | 16×16 |  | ✓ | ✓ | ✓ | `heat_pipe` | — | — | ✓ | 已用 |
| 78 | `block/heatpipe_core_molten.png` | 16×16 |  | ✓ | ✓ | ✓ | `heat_pipe` | — | — | ✓ | 已用 |
| 79 | `block/heatpipe_core_super_hot.png` | 16×16 |  | ✓ | ✓ | ✓ | `heat_pipe` | — | — | ✓ | 已用 |
| 80 | `block/heatpipe_core_very_hot.png` | 16×16 |  | ✓ | ✓ | ✓ | `heat_pipe` | — | — | ✓ | 已用 |
| 81 | `block/heatpipe_core_warm.png` | 16×16 |  | ✓ | ✓ | ✓ | `heat_pipe` | — | — | ✓ | 已用 |
| 82 | `block/heatpipe_rim.png` | 16×16 |  | ✓ | ✓ | ✓ | `filter_duct`, `heat_pipe`, `straight_duct` | — | — | ✓ | 已用 |
| 83 | `block/heatpipe_rim_hot.png` | 16×16 |  | ✓ | ✓ | ✓ | `heat_pipe` | — | — | ✓ | 已用 |
| 84 | `block/heatpipe_rim_molten.png` | 16×16 |  | ✓ | ✓ | ✓ | `heat_pipe` | — | — | ✓ | 已用 |
| 85 | `block/heatpipe_rim_super_hot.png` | 16×16 |  | ✓ | ✓ | ✓ | `heat_pipe` | — | — | ✓ | 已用 |
| 86 | `block/heatpipe_rim_very_hot.png` | 16×16 |  | ✓ | ✓ | ✓ | `heat_pipe` | — | — | ✓ | 已用 |
| 87 | `block/heatpipe_rim_warm.png` | 16×16 |  | ✓ | ✓ | ✓ | `heat_pipe` | — | — | ✓ | 已用 |
| 88 | `block/hose.png` | 9×15 |  | ✓ | ✓ | ✓ | — | — | ✓ | — | 已用(Java) |
| 89 | `block/impactotron_1.png` | 48×32 |  | ✓ | ✓ | ✓ | `impact_sensor` | — | — | ✓ | 已用 |
| 90 | `block/impactotron_2.png` | 48×32 |  | ✓ | ✓ | ✓ | `impact_sensor` | — | — | ✓ | 已用 |
| 91 | `block/impactotron_3.png` | 48×32 |  | ✓ | ✓ | ✓ | `impact_sensor` | — | — | ✓ | 已用 |
| 92 | `block/impactotron_4.png` | 48×32 |  | ✓ | ✓ | ✓ | `impact_sensor` | — | — | ✓ | 已用 |
| 93 | `block/impactotron_animated.png` | 48×128 | 动画 4f | ✓ | ✓ | ✓ | — | `impact_sensor` | — | — | 已用 |
| 94 | `block/infuriated.png` | 32×32 |  | ✓ | ✓ | ✓ | — | — | — | — | 孤儿 |
| 95 | `block/infuserblock.png` | 128×128 |  | ✓ | ✓ | ✓ | `physics_infuser` | — | — | ✓ | 已用 |
| 96 | `block/infuservortex.png` | 64×64 |  | ✓ | — | — | — | — | — | — | 已清理 |
| 97 | `block/intake.png` | 128×128 |  | ✓ | ✓ | ✓ | `intake` | — | — | ✓ | 已用 |
| 98 | `block/junk_gears.png` | 16×16 |  | ✓ | — | — | — | — | — | — | 已清理 |
| 99 | `block/juryrigged_infuser.png` | 64×64 |  | ✓ | — | — | — | — | — | — | 已清理 |
| 100 | `block/juryrigged_propeller.png` | 64×64 |  | ✓ | ✓ | ✓ | `juryrigged_propeller_bearing` | — | — | ✓ | 已用 |
| 101 | `block/lodefocus.png` | 16×16 |  | ✓ | ✓ | ✓ | `lodefocus` | — | — | ✓ | 已用 |
| 102 | `block/mysteriousliquidtransparent.png` | 32×32 |  | ✓ | ✓ | ✓ | — | `physics_infuser` | — | — | 已用 |
| 103 | `block/nozzle.png` | 64×64 |  | ✓ | ✓ | ✓ | `gas_nozzle` | — | — | ✓ | 已用 |
| 104 | `block/nyx.png` | 16×16 |  | ✓ | ✓ | ✓ | `charged_nyx`, `nyx` | — | — | ✓ | 已用 |
| 105 | `block/nyx_wanderlite_ore.png` | 16×160 | 动画 10f | ✓ | ✓ | ✓ | `wanderlite_nyx_ore` | — | — | ✓ | 已用 |
| 106 | `block/nyx_wanderlite_ore_top.png` | 16×160 | 动画 10f | ✓ | ✓ | ✓ | `wanderlite_nyx_ore` | — | — | ✓ | 已用 |
| 107 | `block/oneway_duct.png` | 16×16 |  | ✓ | ✓ | ✓ | — | — | — | — | 仅声明 |
| 108 | `block/peepulon.png` | 48×32 |  | ✓ | — | — | — | — | — | — | 已清理 |
| 109 | `block/peepulon_1.png` | 48×32 |  | ✓ | ✓ | ✓ | `distance_sensor` | — | — | ✓ | 已用 |
| 110 | `block/peepulon_2.png` | 48×32 |  | ✓ | ✓ | ✓ | `distance_sensor` | — | — | ✓ | 已用 |
| 111 | `block/peepulon_3.png` | 48×32 |  | ✓ | ✓ | ✓ | `distance_sensor` | — | — | ✓ | 已用 |
| 112 | `block/peepulon_4.png` | 48×32 |  | ✓ | ✓ | ✓ | `distance_sensor` | — | — | ✓ | 已用 |
| 113 | `block/peepulon_animated.png` | 48×128 | 动画 4f | ✓ | ✓ | ✓ | — | `distance_sensor` | — | — | 已用 |
| 114 | `block/peepulon_redstone.png` | 48×32 |  | ✓ | ✓ | ✓ | `distance_sensor`, `gyroscopic_sensor`, `impact_sensor` | — | — | ✓ | 已用 |
| 115 | `block/phys_bearing.png` | 64×64 |  | ✓ | ✓ | ✓ | `phys_bearing` | `gimbal_bearing` | — | ✓ | 已用 |
| 116 | `block/phys_bearing_bigflap.png` | 64×64 |  | ✓ | — | — | — | — | — | — | 已清理 |
| 117 | `block/pipes.png` | 32×32 |  | ✓ | — | — | — | — | — | — | 已清理 |
| 118 | `block/pipes_connected.png` | 32×32 |  | ✓ | — | — | — | — | — | — | 已清理 |
| 119 | `block/pneumocrafter.png` | 64×64 |  | ✓ | ✓ | ✓ | `gas_crafter` | — | — | ✓ | 已用 |
| 120 | `block/propbearingbase.png` | 64×64 |  | ✓ | — | — | — | — | — | — | 已清理 |
| 121 | `block/propbearingpistons.png` | 32×32 |  | ✓ | — | — | — | — | — | — | 已清理 |
| 122 | `block/propbearingtop.png` | 64×64 |  | ✓ | ✓ | ✓ | `block/propeller_bearing/top` | `propeller_bearing` | — | — | 已用 |
| 123 | `block/purple_hue.png` | 16×16 |  | ✓ | ✓ | ✓ | — | — | ✓ | — | 已用(Java) |
| 124 | `block/reactionwheel.png` | 128×128 |  | ✓ | — | — | — | — | — | — | 已清理 |
| 125 | `block/reactionwheelmid.png` | 32×32 |  | ✓ | — | — | — | — | — | — | 已清理 |
| 126 | `block/reactionwheels.png` | 64×64 |  | ✓ | ✓ | ✓ | `reactionwheel` | — | — | ✓ | 已用 |
| 127 | `block/reactionwheelspokes.png` | 32×32 |  | ✓ | — | — | — | — | — | — | 已清理 |
| 128 | `block/reactionwheelstitched.png` | 512×512 |  | ✓ | — | — | — | — | — | — | 已清理 |
| 129 | `block/redstone_duct.png` | 32×32 |  | ✓ | ✓ | ✓ | `redstone_duct` | — | — | ✓ | 已用 |
| 130 | `block/redstoneresistor.png` | 64×64 |  | ✓ | ✓ | ✓ | `redstone_resistor` | — | — | ✓ | 已用 |
| 131 | `block/slicker.png` | 64×64 |  | ✓ | ✓ | ✓ | `slicker` | — | — | ✓ | 已用 |
| 132 | `block/slicker_activated.png` | 64×64 |  | ✓ | ✓ | ✓ | `slicker` | — | — | ✓ | 已用 |
| 133 | `block/smart_duct.png` | 16×16 |  | ✓ | ✓ | ✓ | — | — | — | — | 仅声明 |
| 134 | `block/smart_flap_bearing.png` | 48×48 |  | ✓ | ✓ | ✓ | `smart_flap_bearing` | — | — | ✓ | 已用 |
| 135 | `block/smart_propeller_bearing.png` | 64×64 |  | ✓ | ✓ | ✓ | `copter_bearing` | — | — | ✓ | 已用 |
| 136 | `block/smooth_wanderlite.png` | 16×16 |  | ✓ | ✓ | ✓ | `smooth_wanderlite`, `smooth_wanderlite_slab`, `smooth_wanderlite_stairs` | — | — | ✓ | 已用 |
| 137 | `block/smooth_wanderlite_side.png` | 16×16 |  | ✓ | ✓ | ✓ | `smooth_wanderlite`, `smooth_wanderlite_slab`, `smooth_wanderlite_stairs` | — | — | ✓ | 已用 |
| 138 | `block/smooth_wanderlite_step.png` | 16×16 |  | ✓ | ✓ | ✓ | `smooth_wanderlite_stairs` | — | — | ✓ | 已用 |
| 139 | `block/spinoff_bearing.png` | 32×32 |  | ✓ | ✓ | ✓ | `spinoff_bearing` | — | — | ✓ | 已用 |
| 140 | `block/spinotron_animated.png` | 48×96 | 动画 3f | ✓ | ✓ | ✓ | — | `gyroscopic_sensor` | — | — | 已用 |
| 141 | `block/spinotron_x.png` | 48×32 |  | ✓ | ✓ | ✓ | `gyroscopic_sensor` | — | — | ✓ | 已用 |
| 142 | `block/spinotron_y.png` | 48×32 |  | ✓ | ✓ | ✓ | `gyroscopic_sensor` | — | — | ✓ | 已用 |
| 143 | `block/spinotron_z.png` | 48×32 |  | ✓ | ✓ | ✓ | `gyroscopic_sensor` | — | — | ✓ | 已用 |
| 144 | `block/sugar_rocket_bottom.png` | 16×16 |  | ✓ | ✓ | ✓ | `sugar_rocket` | — | — | ✓ | 已用 |
| 145 | `block/sugar_rocket_side.png` | 16×16 |  | ✓ | ✓ | ✓ | `sugar_rocket` | — | — | ✓ | 已用 |
| 146 | `block/sugar_rocket_top.png` | 16×16 |  | ✓ | ✓ | ✓ | `sugar_rocket` | — | — | ✓ | 已用 |
| 147 | `block/universal_shaft.png` | 16×16 |  | ✓ | ✓ | ✓ | `universal_shaft` | — | — | ✓ | 已用 |
| 148 | `block/vsified_top.png` | 32×32 |  | ✓ | — | — | — | — | — | — | 已清理 |
| 149 | `block/wanderglass.png` | 16×16 |  | ✓ | ✓ | ✓ | `wanderglass` | — | — | ✓ | 已用 |
| 150 | `block/wanderlite_block.png` | 16×16 |  | ✓ | ✓ | ✓ | `wanderlite_block` | — | — | ✓ | 已用 |
| 151 | `block/wanderlite_bricks.png` | 16×16 |  | ✓ | ✓ | ✓ | `wanderlite_bricks` | — | — | ✓ | 已用 |
| 152 | `block/wideblade.png` | 32×32 |  | ✓ | ✓ | ✓ | `blade_controller` | `wide_propeller_blade` | — | ✓ | 已用 |
| 153 | `block/wing.png` | 64×64 |  | ✓ | ✓ | ✓ | `flap`, `wing` | — | — | ✓ | 已用 |
| 154 | `block/wingextension.png` | 32×32 |  | ✓ | ✓ | ✓ | `wing` | — | — | ✓ | 已用 |
| 155 | `block/winghorizontalspokes.png` | 32×32 |  | ✓ | ✓ | ✓ | `flap`, `wing` | — | — | ✓ | 已用 |

## 4. 官方树 vs 工程树

### 4.1 工程树新增（官方树没有，共 6 张）

| 贴图 | 尺寸 | 状态 | 说明 |
|---|---|---|---|
| `textures/block/duct_tank.png` | 16×16 | 已用 | 移植期新增（官方 1.20.1 没有） |
| `textures/block/duct_tank_gas.png` | 16×16 | 已用 | 移植期新增（官方 1.20.1 没有） |
| `textures/block/duct_tank_top.png` | 16×16 | 已用 | 移植期新增（官方 1.20.1 没有） |
| `textures/block/gimbal_bearing.png` | 64×64 | 已用 | 移植期新增（官方 1.20.1 没有） |
| `textures/block/sterling_engine.png` | 64×64 | 已用 | 移植期新增（官方 1.20.1 没有） |
| `textures/block/vs_gas_flow_engine.png` | 64×64 | 已用 | 移植期新增（官方 1.20.1 没有） |

### 4.2 官方树有、工程树没有（共 26 张）

- **本次收口删除 26 张**（仅官方树留档，详见附录 D.1）。
- 意外缺失 0 张（无）。

> 也就是说，工程树不再发布这些图是**有意为之**，不是打包漏文件；官方树仍保留同名副本，需要时可回拷。

### 4.3 官方树旧版模型还在引用、工程树已改掉的贴图（4 张）

| 贴图 | 官方树里引用它的模型 | 说明 |
|---|---|---|
| `block/duct_tank_connected.png` | `block/duct_tank/block_single` | 工程树模型已换别的贴图，运行时用不到这张（不是缺口） |
| `block/duct_tank_top_connected.png` | `block/duct_tank/block_single` | 工程树模型已换别的贴图，运行时用不到这张（不是缺口） |
| `block/fluid_tank.png` | `block/gas_engine/block` | 工程树模型已换别的贴图，运行时用不到这张（不是缺口） |
| `block/fluid_tank_top.png` | `block/gas_engine/block` | 工程树模型已换别的贴图，运行时用不到这张（不是缺口） |

### 4.4 两边同名但内容不同的模型：15 个

工程树是 1.21.1 移植版：OBJ loader 从 `forge:obj` / `porting_lib:obj` 改成 `neoforge:obj`、`models/` 前缀修正、`#missing` 补上、贴图改名等，所以两边不必逐字节一致。逐来源的模型告警见 §8.3。

## 5. 孤儿贴图（仍发布、任何模型都不引用）

共 **7** 张（收口前 20 张：12 张确认死资源已删除、1 张 `nyx_wanderlite_ore_top` 已接进模型）。留下的 7 张都是**有意保留**的未移植功能 / 旧版状态图，逐条在「判定依据」里写明；Java 直接引用的 3 张不算孤儿（§13 已核对）。详见附录 D。

### 未接的变体面（2 张）

> `alt_meter_on`（红石点亮）与 `balloon_casing_connected`（CT 连接）两张仍待接；`nyx_wanderlite_ore_top` 已在本次收口接到 `cube_bottom_top`（附录 D.5）

| 贴图 | 尺寸 | 关联方块 | 方块注册 | 判定依据 |
|---|---|---|---|---|
| `block/alt_meter_on.png` | 32×32 | `alt_meter` | ✓ | 旧版「红石点亮」状态图：`alt_meter/block.bbmodel` 里有第二层贴图，但 blockstate 没有 power 变体；等接点亮状态再上 |
| `block/balloon_casing_connected.png` | 128×128 | `balloon_casing` | ✓ | 128×128 CT 连接图集（4×4 格）：`balloon_casing` 仍是 `cube_all`；等接 Create 式 CT / EncasingRegistry 再上 |

### 表现层 / 情绪贴图（5 张）

> afterblazer 的表情/羽流图（1.20.1 `ClockworkPartials.kt:15-18` 有声明，方块/渲染器未移植）＋ physics_infuser 的蓝色色相图；均保留待补，**不是死资源**

| 贴图 | 尺寸 | 关联方块 | 方块注册 | 判定依据 |
|---|---|---|---|---|
| `block/angry.png` | 32×32 | — | — | afterblazer 表情/羽流贴图：1.20.1 `ClockworkPartials.kt:15-18` 有声明，方块 / 渲染器没移植；等 afterblazer 一起补 |
| `block/blaze_fury.png` | 16×16 | — | — | 同上（afterblazer） |
| `block/blue_hue.png` | 16×16 | `physics_infuser` | ✓ | physics_infuser 蓝色色相图：渲染器只接了 `purple_hue`（`PhysicsInfuserRenderer.java:67`）；等蓝色光晕功能再上 |
| `block/fuming.png` | 32×32 | — | — | 同上（afterblazer 的 PLUME_FUMING） |
| `block/infuriated.png` | 32×32 | — | — | 同上（afterblazer 的 BLAZE_INFURIATED / PLUME_INFURIATED） |


## 6. 仅声明贴图（模型声明了，但没有任何根能烘焙到）

共 **3** 张（收口前 15 张：12 张死局部模型用图已随模型一起删除）。剩下的 3 张是**唯一整套没接**的导管 custom 边件，贴图和模型都画好了，但模型没有 blockstate / 物品模型 / Java partial 去引用，游戏里烘焙不到。按功能分组：

### 导管边件（duct/custom/*）（3 张）

> 三个边件模型（oneway_forward / oneway_backward / smart / copper）都在，但 `blockstates/duct.json` 只引用 duct/core 与 duct/core_straight，没有任何 blockstate 能烘焙到它们；DuctRenderer.java:41 原话：「`duct/custom/*` 四个边件暂无人使用」。这是本次最像「整套功能没接」的一组贴图

| 贴图 | 尺寸 | 关联方块 | 方块注册 | 声明它的模型 |
|---|---|---|---|---|
| `block/copper_duct.png` | 16×16 | `duct` | ✓ | `block/duct/custom/copper`, `block/duct/custom/oneway_backward`, `block/duct/custom/oneway_forward`；**保留待补**：导管 custom 铜边件：同上，等待铜导管外观接线 |
| `block/oneway_duct.png` | 16×16 | `duct` | ✓ | `block/duct/custom/oneway_backward`, `block/duct/custom/oneway_forward`；**保留待补**：导管 custom 单向边件：没有任何 blockstate 能选到；`DuctRenderer.java:41` 原话「`duct/custom/*` 四个边件暂无人使用」→ 等待功能移植 |
| `block/smart_duct.png` | 16×16 | `duct` | ✓ | `block/duct/custom/smart`；**保留待补**：导管 custom 智能边件：同上，等待 `GasDuctBlock` 加智能属性 |


## 7. 缺图引用（模型引用了、但所有来源都没有 → 黑紫）

| 贴图 | 引用它的模型 | 模型所在来源 | 运行时影响 |
|---|---|---|---|
| `create:block/copper_plating` | `block/valve_duct/pointer` | 官方树 | 仅官方树旧版（工程树已改，不影响运行时） |
| `vs_clockwork:block/creative_generator` | `block/creative_gas_generator/item` | 官方树 | 仅官方树旧版（工程树已改，不影响运行时） |

> 判断依据：引用它的模型若可达（有 blockstate / 物品模型 / Java partial 引用），实机就会黑紫；只在死模型里则当前不显示，但仍是必须修的坏引用。

## 8. 模型侧问题（与贴图接线直接相关）

### 8.1 blockstate / 物品 / Java 引用了不存在的模型

（无）

### 8.2 没有任何根能烘焙到的模型（57 个，按目录分组）

这些模型自身不报错，但只会出现在「死链」里；§6 的 `仅声明` 大多由它们造成。（`*_old` / `old/` 子目录属于旧版遗留，已在 §5 归到「旧版外观遗留」。）本次已从工程树删除 22 个死模型（附录 D.2），下表只剩仍发布的。

- `block/blade_controller`：3 个 —— `block/blade_controller/blade/blade`, `block/blade_controller/blade/wideblade`, `block/blade_controller/blade_controller`
- `block/combustion_engine`：2 个 —— `block/combustion_engine/main`, `block/combustion_engine/single_piston`
- `block/command_seat`：3 个 —— `block/command_seat/buttonone`, `block/command_seat/buttontwo`, `block/command_seat/joystick`
- `block/compressor`：1 个 —— `block/compressor/compressor`
- `block/creative_gas_generator`：1 个 —— `block/creative_gas_generator/item`
- `block/duct`：28 个 —— `block/duct/block`, `block/duct/block_old`, `block/duct/connection_old/cool`, `block/duct/connection_old/hot` +24
- `block/extendon`：1 个 —— `block/extendon/hose`
- `block/flap`：1 个 —— `block/flap/flap`
- `block/flap_bearing`：2 个 —— `block/flap_bearing/block_horizontal`, `block/flap_bearing/block_vertical`
- `block/flap_bearing_top`：1 个 —— `block/flap_bearing_top`
- `block/phys_bearing_old`：10 个 —— `block/phys_bearing_old/block`, `block/phys_bearing_old/cornerne`, `block/phys_bearing_old/cornernw`, `block/phys_bearing_old/cornerse` +6
- `block/redstone_resistor`：2 个 —— `block/redstone_resistor/block_brass`, `block/redstone_resistor/resistorindicator_brass`
- `block/sterling_engine`：1 个 —— `block/sterling_engine/old`
- `block/wing`：1 个 —— `block/wing/wing`

### 8.3 逐来源的模型告警（同一个模型两棵树可能不一样）

| 来源 | 数量 | 告警 |
|---|---|---|
| 官方树 | 14 | `block/brass_propeller_bearing/item`: 面引用 #missing 无法解析（会渲染成黑紫）；`block/compressor/compressor`: 面引用 #missing 无法解析（会渲染成黑紫）；`block/compressor/item`: 面引用 #missing 无法解析（会渲染成黑紫）；`block/gas_engine/block`: 面引用 #missing 无法解析（会渲染成黑紫）；`block/juryrigged_propeller_bearing/item`: 面引用 #missing 无法解析（会渲染成黑紫）；`block/physics_infuser/zap`: 面引用 #missing 无法解析（会渲染成黑紫） |

> 官方树里的告警如果工程树已修，说明是移植期修掉的（例如 `#missing`）；两边都报的才是真待修。

## 9. 注册侧与资源侧的缺口

### 9.1 有整套资源、但方块没注册（工程树仍发布 3 个）

收口前共 9 个：**3 个保留待补、5 个确认旧版/流体误留已删除、1 个只在官方树**。保留下来的 id 有 blockstate + 模型 + 贴图，但 `tools/blockstate_properties.json` 里没有注册点（也就没有物品/方块实体），属于**未移植的 WIP** —— 对应贴图虽然「已用」，但游戏里依然看不到；删除的 5 个已在附录 D.3 记录依据。

| id | 模型数 | 涉及的贴图 |
|---|---|---|
| `afterblazer` | 1 | `block/afterblazer` |
| `balloon_encased_shaft` | 1 | `block/balloon_casing`, `block/balloon_gearbox` |
| `combustion_engine` | 1 | `block/combustion_engine` |

9 个 id 的逐条判定（保留 / 删除 / 仅官方树）：

| id | 位置 | 处理 | 依据 |
|---|---|---|---|
| `OLD_flap_bearing` | 仅官方树 | **不处理** | 官方树有、工程树没有；官方树只读，不参与构建（官方 1.20.1 也从未注册这个 id） |
| `afterblazer` | 工程树 | **保留** | 1.20.1 `ClockworkPartials.kt:15-18` 有 BLAZE_INFURIATED / PLUME_ANGRY / PLUME_FUMING / PLUME_INFURIATED，`ClockworkShapes.kt:14` 有 AFTERBLAZER 碰撞箱，但两版都没有方块注册点 / 渲染器 → 未移植的 WIP，保留待补 |
| `balloon_encased_shaft` | 工程树 | **保留** | 1.20.1 `ClockworkBlocks.kt:1020` 注册过；工程树 `CWBlocks.java:93` 与 `CWKineticsIndex.java:52` 明确写「需要与 balloon_encased_shaft 成对实现 / 需要 EncasingRegistry 集成」→ 未移植，保留待补 |
| `chocolate_frosting` | 工程树 | **删除** | 1.20.1 `FabricClockworkFluids.java:24-25` 的 fluid 注册整段被注释；流体不该有 blockstate，属误留 |
| `combustion_engine` | 工程树 | **保留** | 1.20.1 `ClockworkPartials.kt:21-22` 有 ENGINE / SINGLE_ENGINE_PISTON，block/item 模型齐全，但两版都没有方块注册 → 未移植的 WIP，保留待补 |
| `old_andesite_flap_bearing` | 工程树 | **删除** | 注册方块是 `andesite_flap_bearing`，复用 `andesite_flap_bearing/block`；无 lang / 物品 / 代码注册点，属旧版别名 |
| `propeller_bearing` | 工程树 | **删除** | 1.20.1 与 1.21 都没有 plain `propeller_bearing` 方块注册；现役是 `brass_propeller_bearing` / `juryrigged_propeller_bearing`（`CWContraptionsIndex.java:112/124`）；旧模型链一并清理 |
| `strawberry_frosting` | 工程树 | **删除** | 同 chocolate_frosting |
| `vanilla_frosting` | 工程树 | **删除** | 同 chocolate_frosting |

> 保留的 3 个（`afterblazer` / `balloon_encased_shaft` / `combustion_engine`）在游戏里看不到，不是 bug，是功能没接；详见附录 D.4。

| 项 | 数量 | 清单 |
|---|---|---|
| 死 blockstate（无注册方块） | 3 | `afterblazer`, `balloon_encased_shaft`, `combustion_engine` |
| 注册了方块但没有 blockstate | 0 | — |
| 注册了物品但没有 `models/item/<id>.json` | 0 | — |
| 方块（BlockItem）没有 `models/item/<id>.json` | 0 | — |

> 注册清单来自 `tools/blockstate_properties.json`（运行时实例化方块的快照）与 `tools/cw_ids.py`（Java 里 `.block()` / `.item()` 的解析）。BlockItem 由 Registrate 随方块自动注册，所以「物品注册」= 方块 id ∪ cw_ids 的物品集合。

## 10. `textures/block/` 下的非贴图文件

| 文件 | 类型 | 说明 |
|---|---|---|
| `block/coal_burner_flame.png.mcmeta` | 动画定义（附属件） | 不算贴图；动画帧数与对应 PNG 同行 |
| `block/deepslate_wanderlite_ore.png.mcmeta` | 动画定义（附属件） | 不算贴图；动画帧数与对应 PNG 同行 |
| `block/deepslate_wanderlite_ore_top.png.mcmeta` | 动画定义（附属件） | 不算贴图；动画帧数与对应 PNG 同行 |
| `block/electricity.png.mcmeta` | 动画定义（附属件） | 不算贴图；对应 PNG 已在本次收口删除，**只官方树留档**（附录 D.1） |
| `block/end_wanderlite_ore.png.mcmeta` | 动画定义（附属件） | 不算贴图；动画帧数与对应 PNG 同行 |
| `block/hosespool.aseprite` | 制作期资源 | Blockbench/aseprite 源文件，不参与烘焙 |
| `block/impactotron_animated.png.mcmeta` | 动画定义（附属件） | 不算贴图；动画帧数与对应 PNG 同行 |
| `block/nyx_wanderlite_ore.png.mcmeta` | 动画定义（附属件） | 不算贴图；动画帧数与对应 PNG 同行 |
| `block/nyx_wanderlite_ore_top.png.mcmeta` | 动画定义（附属件） | 不算贴图；动画帧数与对应 PNG 同行 |
| `block/peepulon_animated.png.mcmeta` | 动画定义（附属件） | 不算贴图；动画帧数与对应 PNG 同行 |
| `block/spinotron_animated.png.mcmeta` | 动画定义（附属件） | 不算贴图；动画帧数与对应 PNG 同行 |

全树其它非游戏资源（`models/**` 下的 `.bbmodel` / `.obj` / `.mtl`、`textures/**` 下的 `.aseprite`、根目录 `.gpl`）：

- **官方树**：`.aseprite` 2、`.bbmodel` 12、`.frag` 1、`.gif` 1、`.gpl` 2、`.mtl` 9、`.obj` 12
  - `.aseprite`：`textures/block/hosespool.aseprite`, `textures/gui/iconspalette.aseprite`
  - `.bbmodel`：`models/block/alt_meter/block.bbmodel`, `models/block/blade_controller/blade controller demo.bbmodel`, `models/block/coal_burner/coal_burner_compiled.bbmodel` +9
  - `.frag`：`flywheel/water_mask.frag`
  - `.gif`：`icon.gif`
  - `.gpl`：`textures/palettes/brassnt.gpl`, `textures/palettes/wanderlite.gpl`
  - `.mtl`：`models/block/clock/clock_ring.mtl`, `models/block/clock/item.mtl`, `models/block/gyro/gyro.mtl` +6
  - `.obj`：`models/block/clock/clock_ring.obj`, `models/block/clock/item.obj`, `models/block/gyro/gyro.obj` +9
- **工程树**：`.aseprite` 2、`.bbmodel` 12、`.frag` 1、`.gif` 1、`.gpl` 2、`.mtl` 6、`.obj` 6
  - `.aseprite`：`textures/block/hosespool.aseprite`, `textures/gui/iconspalette.aseprite`
  - `.bbmodel`：`models/block/alt_meter/block.bbmodel`, `models/block/blade_controller/blade controller demo.bbmodel`, `models/block/coal_burner/coal_burner_compiled.bbmodel` +9
  - `.frag`：`flywheel/water_mask.frag`
  - `.gif`：`icon.gif`
  - `.gpl`：`textures/palettes/brassnt.gpl`, `textures/palettes/wanderlite.gpl`
  - `.mtl`：`models/block/clock/clock_ring.mtl`, `models/block/clock/item.mtl`, `models/block/gyro/gyro.mtl` +3
  - `.obj`：`models/block/clock/clock_ring.obj`, `models/block/clock/item.obj`, `models/block/gyro/gyro.obj` +3

## 11. 其它 `textures/` 子目录（不在本表统计口径内）

| 目录 | PNG 数 | 被 Java 直接引用 | 引用方 |
|---|---|---|---|
| `effects` | 3 | Java 直接 1 张 | — |
| `entity` | 2 | Java 直接 0 张 | 实体渲染 |
| `fluid` | 8 | Java 直接 0 张 | 流体 |
| `font` | 1 | Java 直接 0 张 | — |
| `gui` | 16 | Java 直接 3 张 | Screen / ToolMenu |
| `icons` | 10 | Java 直接 0 张 | — |
| `item` | 34 | Java 直接 0 张 | 物品图标 |
| `models` | 3 | Java 直接 0 张 | — |
| `palettes` | 0 | Java 直接 0 张 | — |
| `particle` | 59 | Java 直接 0 张 | particles/*.json |

> 这些目录的贴图由 Java（GUI/实体/字体）、`particles/*.json` 或流体渲染引用，不参与「模型 → 方块」回溯，所以不计入已用/孤儿统计；§13 已人工确认它们不是「放错目录的方块贴图」。

## 12. 附录 A：按方块汇总（贴图视角）

| 方块 | 已注册 | 有 blockstate | 取用贴图数 | 贴图（去 `block/` 前缀） |
|---|---|---|---|---|
| `afterblazer` | — | ✓ | 1 | `afterblazer` |
| `air_compressor` | ✓ | ✓ | 2 | `compressor`, `compressor_fabric` |
| `alt_meter` | ✓ | ✓ | 2 | `altimeter`, `altimeter_redstone` |
| `andesite_flap_bearing` | ✓ | ✓ | 1 | `andesite_flap_bearing` |
| `asteroid_block` | ✓ | ✓ | 1 | `asteroid_block` |
| `balloon_casing` | ✓ | ✓ | 1 | `balloon_casing` |
| `balloon_encased_shaft` | — | ✓ | 2 | `balloon_casing`, `balloon_gearbox` |
| `ballooner` | ✓ | ✓ | 1 | `burner` |
| `blade_controller` | ✓ | ✓ | 3 | `blade`, `blade_controller`, `wideblade` |
| `brass_propeller_bearing` | ✓ | ✓ | 1 | `brass_propeller` |
| `charged_nyx` | ✓ | ✓ | 2 | `charged_nyx`, `nyx` |
| `chiseled_wanderlite` | ✓ | ✓ | 2 | `chiselled_wanderlite`, `chiselled_wanderlite_side` |
| `clock` | ✓ | ✓ | 1 | `clock` |
| `coal_burner` | ✓ | ✓ | 2 | `coal_burner`, `coal_burner_flame` |
| `cobbled_nyx` | ✓ | ✓ | 1 | `cobbled_nyx` |
| `combustion_engine` | — | ✓ | 1 | `combustion_engine` |
| `command_seat` | ✓ | ✓ | 1 | `commandseat` |
| `copter_bearing` | ✓ | ✓ | 1 | `smart_propeller_bearing` |
| `creative_gas_generator` | ✓ | ✓ | 1 | `creative_gas_generator` |
| `delivery_cannon` | ✓ | ✓ | 2 | `cannon_barrel_largecheckers`, `cannon_base` |
| `delivery_chute` | ✓ | ✓ | 2 | `cannon_barrel_largecheckers`, `delivery_chute` |
| `distance_sensor` | ✓ | ✓ | 5 | `peepulon_1`, `peepulon_2`, `peepulon_3`, `peepulon_4`, `peepulon_redstone` |
| `duct` | ✓ | ✓ | 1 | `duct` |
| `duct_tank` | ✓ | ✓ | 4 | `duct_tank`, `duct_tank_gas`, `duct_tank_top`, `fluid_tank_inner` |
| `exhaust` | ✓ | ✓ | 1 | `duct` |
| `extendon` | ✓ | ✓ | 1 | `extendon` |
| `filter_duct` | ✓ | ✓ | 3 | `heatpipe_connection`, `heatpipe_core`, `heatpipe_rim` |
| `flap` | ✓ | ✓ | 3 | `flap`, `wing`, `winghorizontalspokes` |
| `gas_backtank` | ✓ | ✓ | 1 | `gas_backtank` |
| `gas_crafter` | ✓ | ✓ | 1 | `pneumocrafter` |
| `gas_engine` | ✓ | ✓ | 2 | `duct`, `vs_gas_flow_engine` |
| `gas_heater` | ✓ | ✓ | 1 | `gas_heater` |
| `gas_nozzle` | ✓ | ✓ | 1 | `nozzle` |
| `gas_thruster` | ✓ | ✓ | 1 | `gas_thruster` |
| `gimbal_bearing` | ✓ | ✓ | 1 | `gimbal_bearing` |
| `goo_block` | ✓ | ✓ | 2 | `goo`, `gooborder` |
| `gyro` | ✓ | ✓ | 2 | `alt_meter`, `gyro` |
| `gyroscopic_sensor` | ✓ | ✓ | 4 | `peepulon_redstone`, `spinotron_x`, `spinotron_y`, `spinotron_z` |
| `heat_pipe` | ✓ | ✓ | 18 | `heatpipe_connection`, `heatpipe_connection_hot`, `heatpipe_connection_molten`, `heatpipe_connection_super_hot`, `heatpipe_connection_very_hot`, `heatpipe_connection_warm` +12 |
| `hose_port` | ✓ | ✓ | 1 | `extendon` |
| `impact_sensor` | ✓ | ✓ | 5 | `impactotron_1`, `impactotron_2`, `impactotron_3`, `impactotron_4`, `peepulon_redstone` |
| `intake` | ✓ | ✓ | 1 | `intake` |
| `juryrigged_propeller_bearing` | ✓ | ✓ | 1 | `juryrigged_propeller` |
| `lodefocus` | ✓ | ✓ | 1 | `lodefocus` |
| `nyx` | ✓ | ✓ | 1 | `nyx` |
| `phys_bearing` | ✓ | ✓ | 1 | `phys_bearing` |
| `physics_infuser` | ✓ | ✓ | 1 | `infuserblock` |
| `pump_duct` | ✓ | ✓ | 1 | `duct_pump` |
| `reactionwheel` | ✓ | ✓ | 1 | `reactionwheels` |
| `redstone_duct` | ✓ | ✓ | 2 | `duct`, `redstone_duct` |
| `redstone_resistor` | ✓ | ✓ | 1 | `redstoneresistor` |
| `slicker` | ✓ | ✓ | 2 | `slicker`, `slicker_activated` |
| `smart_flap_bearing` | ✓ | ✓ | 1 | `smart_flap_bearing` |
| `smooth_wanderlite` | ✓ | ✓ | 2 | `smooth_wanderlite`, `smooth_wanderlite_side` |
| `smooth_wanderlite_slab` | ✓ | ✓ | 2 | `smooth_wanderlite`, `smooth_wanderlite_side` |
| `smooth_wanderlite_stairs` | ✓ | ✓ | 3 | `smooth_wanderlite`, `smooth_wanderlite_side`, `smooth_wanderlite_step` |
| `solver` | ✓ | ✓ | 0 | — |
| `spinoff_bearing` | ✓ | ✓ | 1 | `spinoff_bearing` |
| `steam_generator` | ✓ | ✓ | 1 | `duct` |
| `sterling_engine` | ✓ | ✓ | 1 | `sterling_engine` |
| `straight_duct` | ✓ | ✓ | 3 | `heatpipe_connection`, `heatpipe_core`, `heatpipe_rim` |
| `sugar_rocket` | ✓ | ✓ | 3 | `sugar_rocket_bottom`, `sugar_rocket_side`, `sugar_rocket_top` |
| `universal_shaft` | ✓ | ✓ | 1 | `universal_shaft` |
| `valve_duct` | ✓ | ✓ | 2 | `duct`, `heat_valve` |
| `wanderglass` | ✓ | ✓ | 1 | `wanderglass` |
| `wanderlite_block` | ✓ | ✓ | 1 | `wanderlite_block` |
| `wanderlite_bricks` | ✓ | ✓ | 1 | `wanderlite_bricks` |
| `wanderlite_deepslate_ore` | ✓ | ✓ | 2 | `deepslate_wanderlite_ore`, `deepslate_wanderlite_ore_top` |
| `wanderlite_end_ore` | ✓ | ✓ | 1 | `end_wanderlite_ore` |
| `wanderlite_nyx_ore` | ✓ | ✓ | 2 | `nyx_wanderlite_ore`, `nyx_wanderlite_ore_top` |
| `wing` | ✓ | ✓ | 3 | `wing`, `wingextension`, `winghorizontalspokes` |

## 13. 附录 B：人工核对记录（脚本判定之外）

下列 33 条逐条读过模型 / Java 注释后确认，专门覆盖最容易误判的几类：CT 图集、动画 `.mcmeta`、OBJ 贴图键、Java 直接绑定、跨命名空间陷阱、「贴图在但方块没注册」。脚本只校验这些贴图**仍然存在**（`--check` 会报），结论本身由人工维护。

| 贴图 | 结论 | 依据 |
|---|---|---|
| `block/duct_tank_connected` | CT 图集，**已正确接线**（不是孤儿） | `duct_tank/block_single.json` 用 uv 子区间从 4×4 格里取 1 格；`block_bottom/top/middle` 整张铺满，所以同一族内 texel 密度差 4 倍（成型/拆分时贴图会跳变）—— 这是 uv 问题，与「有没有人引用」无关 |
| `block/duct_tank_top_connected` | CT 图集，**已用** | `duct_tank/block_top.json` / `block_single.json` 引用；与上面同一族 |
| `block/balloon_casing_connected` | **保留待接**：旧版 CT 连接变体贴图（128×128，4×4 格） | `balloon_casing.json` 只有 `all` 一个键指向 `block/balloon_casing`（cube_all），没有 CT 结构；等接 Create 式 CT / EncasingRegistry 再上（本次不删） |
| `block/alt_meter` | **已用**：gyro 物品模型的 OBJ 材质键 1 | `gyro/gyro_item.mtl` 有 `map_Kd #1`（物品模型，`gyro/gyro.json` 声明 `"1": block/alt_meter`）；方块版 `gyro.mtl` 只有 `#0`。只看方块模型会误判成「仅声明」 |
| `block/altimeter` | **已用**：alt_meter 方块的旧命名族 | `alt_meter/block.json` 用 `block/altimeter`，`alt_meter/redstone.json` 用 `block/altimeter_redstone`；方块 id 是 `alt_meter`，贴图名却是 `altimeter`，两套命名并存 |
| `block/alt_meter_on` | **保留待接**：旧版「红石点亮」变量图 | `alt_meter/block.bbmodel` 里它作为第二层贴图存在，但 blockstate 没有 `power` 变体、没有任何 JSON 模型引用它；等接点亮状态再上（本次不删） |
| `block/nyx_wanderlite_ore_top` | **已接入**：`wanderlite/nyx_ore.json` 已改 `cube_bottom_top`，top 用此图 | 官方树与 1.20.1 源码都还是 `cube_all`（上游自己没接），工程树按本次任务目标接线；见附录 D.5，回拷官方模型会退回 cube_all |
| `block/heatpipe_core` | 常温档，**已用** | `heat_pipe` 常温档的连接件模型（l/r/u/d/lu/... 各方向）＋ `straight_duct` 引用的 `heat_pipe/straight` 都用它 |
| `block/heatpipe_core_hot` | 高温档，**已用**（不是孤儿！） | `heat_pipe` 方块带 `heat` 属性（cool/warm/hot/very_hot/molten/super_hot），工程树的 `blockstates/heat_pipe.json` 有 228 条 multipart（38 个连接形态 × 6 档）覆盖全部 6 档（官方树只有 30 条常温变体）——「六档热度整套没接」这个怀疑在**工程树里不成立**，只在官方树成立 |
| `block/heatpipe_rim_molten` | 熔融档，**已用** | 同上；`heat_pipe/*_molten` 模型由 `heat=molten` 的条件选中 |
| `block/oneway_duct` | **保留待补**：单向阀导管边件没接 | `duct/custom/oneway_forward.json` / `oneway_backward.json` 引用它，但这两个模型不被任何 blockstate 引用；`DuctRenderer.java:41` 原话：「`duct/custom/*` 四个边件暂无人使用」。要接必须先给 `GasDuctBlock` 加单向/智能属性（本次不删） |
| `block/smart_duct` | **保留待补**：智能导管边件没接 | `duct/custom/smart.json` 引用；同样没有 blockstate 能选到它（DuctRenderer.java:41，本次不删） |
| `block/copper_duct` | **保留待补**：铜导管外观没接 | `duct/custom/copper.json` 引用；同样没有 blockstate 能选到它（DuctRenderer.java:41，本次不删） |
| `block/redstone_duct` | **已用**（不是孤儿）：它属于独立方块 `redstone_duct`，不是 duct 的变体 | `redstone_duct/block.json` 引用；`blockstates/redstone_duct.json` 是 axis=x/y/z 三变体，`RedstoneDuctBlockEntity` 单独注册（CWGasIndex:245/481） |
| `block/hose` | **已用（Java 直接引用，非模型）**：别当孤儿删 | `ExtendonRenderer` 的 `HOSE_TEXTURE` 常量直接 `ResourceLocation(..., "textures/block/hose.png")`；模型里确实没人引用它 —— 这就是「只查模型会误判」的典型 |
| `block/compressible_axis` | **已用（Java 直接引用，非模型）** | `UniversalShaftRenderer.java:40` 直接引用 `textures/block/compressible_axis.png`；尺寸 4×14（拉伸用） |
| `block/purple_hue` | **已用（Java 直接引用，非模型）** | `PhysicsInfuserRenderer.java:67` 直接引用 `textures/block/purple_hue.png`（外层紫色辉光壳） |
| `block/blue_hue` | **保留待补**：purple_hue 的姊妹图，只接了紫色一侧 | 全工程 Java / JSON 都没有 `blue_hue`；`PhysicsInfuserRenderer` 只用 `purple_hue`。等蓝色光晕功能再上（本次不删） |
| `block/angry` | **保留待补**：afterblazer 表情/羽流贴图，整套没接 | `angry` / `infuriated` / `blaze_fury` / `fuming` 四张在 Java 与 JSON 里都搜不到，但 1.20.1 `ClockworkPartials.kt:15-18` 有 afterblazer 的 PLUME_ANGRY / PLUME_FUMING / PLUME_INFURIATED / BLAZE_INFURIATED 声明 → 等 afterblazer 功能移植（本次不删） |
| `block/blaze_fury` | **保留待补**：afterblazer 表情贴图 | 同上；对应 1.20.1 BLAZE_INFURIATED（`ClockworkPartials.kt:15`） |
| `block/fuming` | **保留待补**：afterblazer 羽流贴图 | 同上；对应 PLUME_FUMING（`ClockworkPartials.kt:17`） |
| `block/infuriated` | **保留待补**：afterblazer 羽流贴图 | 同上；对应 PLUME_INFURIATED（`ClockworkPartials.kt:18`） |
| `block/coal_burner_flame` | **已用**：动画贴图（20 帧，16×320） | `coal_burner/flame.json` 引用；`.mcmeta` 只有 frametime，按 min(w,h)=16 正方形切帧 |
| `block/spinotron_animated` | **已用**：物品图标用动画版，方块用三轴静态图 | `gyroscopic_sensor/item.json` 用 `spinotron_animated`，`block.json` 用 `spinotron_x` |
| `block/impactotron_animated` | **已用**：物品图标用动画版 | `impact_sensor/item.json` 引用；方块侧用 `impactotron_1..4` |
| `block/peepulon_animated` | **已用**：物品图标用动画版 | `distance_sensor/item.json` 引用；方块侧用 `peepulon_1..4` |
| `block/clock` | **已用**：OBJ 材质键 `0` + `particle` | `clock/clock_ring.json`、`clock/item.json` 都是 `neoforge:obj`，`textures` 只有 `0` 与 `particle`；真正取用点是 `clock_ring.mtl` 的 `map_Kd #0`，不看 mtl 会以为没引用 |
| `block/reactionwheels` | **已用**：OBJ 材质键 `0`（另 `1`/`2` 是 Create 的轴贴图） | `reactionwheel/reactionwheel.json` 声明 `0/1/2`，`reactionwheel_shaftless.mtl` 三个 `map_Kd` 全用上；`1`/`2` 指向 `create:block/axis(_top)`，属跨命名空间 |
| `block/gyro` | **已用**：OBJ 材质键 `0` | `gyro/block.json` → parent `gyro/gyro`（声明 `0`/`1`），`gyro.mtl` 只用 `#0`，`gyro_item.mtl` 用 `#0`+`#1`（`#1` = `block/alt_meter`） |
| `block/afterblazer` | **保留待补**：afterblazer 未移植（方块/渲染器都没有注册点） | 1.20.1 `ClockworkPartials.kt:15-18` 有 BLAZE_INFURIATED / PLUME_ANGRY / PLUME_FUMING / PLUME_INFURIATED 声明，`ClockworkShapes.kt:14` 有 AFTERBLAZER 碰撞箱；但两版都没有 blockstate 之外的注册，工程树 `afterblazer/block.json` 是按贴图补的可烘焙模型。等 afterblazer 功能一起补 |
| `block/combustion_engine` | **保留待补**：combustion_engine 未移植（方块/渲染器都没有注册点） | 1.20.1 `ClockworkPartials.kt:21-22` 有 ENGINE / SINGLE_ENGINE_PISTON 声明，block/item 模型齐全，但两版都没有注册点；属 WIP，保留待补 |
| `block/fluid_tank` | **仅官方树**：官方 `gas_engine/block.json` 用它，工程树改成 `vs_gas_flow_engine` | 按工程树（运行时）解析时没有任何可达模型引用它；这不是缺口，是移植期主动换图 |
| `block/creative_gas_generator` | **缺图引用**：模型里写成了 `block/creative_generator`（少 `_gas_`） | `block/creative_gas_generator/item.json`（官方树，死模型）引用 `vs_clockwork:block/creative_generator`，三棵树 + jar 都没有这张图；工程树的同名模型已改为 `creative_gas_generator`，所以运行时不黑紫，但官方树这份坏引用仍在 |

## 14. 附录 C：后续修复清单（本任务只列，不动手）

1. **官方树上游 bug（已确认，不修）**：2 张缺图引用**全部只出现在官方树**（工程树已修，运行时不黑紫），官方树仅作参考、不参与构建，所以**不要修官方树**；回拷资源时注意别把这两处带回来：`create:block/copper_plating`、`vs_clockwork:block/creative_generator`。`create:block/copper_plating` 是 Create 6 已删除的贴图（工程树已换 `copper_casing`）；`block/creative_generator` 是少写 `_gas_` 的笔误（工程树已改 `creative_gas_generator`）。
2. **接未移植功能（本次已确认保留，见附录 D.4）**：3 张导管 custom 边件（`oneway_duct` / `smart_duct` / `copper_duct`）要接，先给 `GasDuctBlock` 加单向/智能属性，再让 `DuctRenderer` 用上 `duct/custom/*`；`afterblazer`（含 `angry` / `infuriated` / `blaze_fury` / `fuming`）、`combustion_engine`、`balloon_encased_shaft` 三套 WIP 资源等功能移植；`alt_meter_on` / `balloon_casing_connected` 等状态/CT 变体等接线；`blue_hue` 等蓝色光晕功能再上。
3. **仍未注册的 3 个 WIP blockstate**：`afterblazer`、`balloon_encased_shaft`、`combustion_engine` 属未移植功能，**有意保留**，不是死资源（依据见 §9.1）。
4. **本次已收口（2026-10-17）**：删除贴图 26 张（含 12 张孤儿 + 12 张死模型用图 + 2 张旧轴承链）、死模型 22 个 + `reactionwheel/old/**`、死 blockstate 5 个；剩余孤儿 7 张 / 仅声明 3 张均为有意保留；完整清单与依据见附录 D。

## 15. 附录 D：资源卫生收口记录（2026-10-17）

> 本次只动 `clockwork_unoffical/src/main/resources/assets/vs_clockwork/**`、`docs/**`、`tools/**`，不改 Java，也不动只读的官方树。所有删除件在官方树都有同名副本，可随时回拷；引用核查由 `tools/cleanup_assets.py` 完成（默认 dry-run，删除前排除待删文件后全树搜 0 引用才 `--apply`）。

### D.1 删除的贴图（26 张）

| 贴图 | 分类 | 为什么确认无引用 |
|---|---|---|
| `textures/block/cannon_barrel.png` | 被替换 | `delivery_cannon/*` 与 `delivery_chute/block.json` 统一用 `block/cannon_barrel_largecheckers` |
| `textures/block/gas_crafter.png` | 被替换 | `gas_crafter/{block,frame,glow,mesh,tube}.json` 的 `0`/`particle` 全部指向 `block/pneumocrafter` |
| `textures/block/gas_engine_front.png` | 被替换 | 工程树 `gas_engine/block.json` 用 `vs_gas_flow_engine` + `duct` |
| `textures/block/gas_engine_side.png` | 被替换 | 同上；官方树也没接这两张 |
| `textures/block/doink.png` | 被替换 | `slicker/doink.json` 的 `0`/`particle` 指向 `block/doinkborder`；该模型也随本次删除 |
| `textures/block/compressor_old.png` | 旧版外观 | `compressor/compressor.json` 用 `block/compressor` + `block/compressor_fabric`，没有 `*_old` 链 |
| `textures/block/duct_old.png` | 旧版外观 | `duct/block_old.json` / `item_old.json` 已改用 `heatpipe_*` |
| `textures/block/phys_bearing_bigflap.png` | 旧版外观 | `phys_bearing/block.json` 只用 `block/phys_bearing`；`phys_bearing_old/block` 也不用它 |
| `textures/block/pipes.png` | 旧版外观 | `ballooner` 的 `particle` 是 **create:block/pipes**（Create 的图），本工程无引用 |
| `textures/block/pipes_connected.png` | 旧版外观 | 无任何模型引用 `vs_clockwork:block/pipes_connected` |
| `textures/block/reactionwheelstitched.png` | 旧版外观 | 现用 `reactionwheel/reactionwheels.png`（OBJ 的 `0/1/2` 材质键） |
| `textures/block/empty.png` | 占位 | 16×16 全透明占位图，任何模型 / Java 都没引用 |
| `textures/block/doinkborder.png` | 死局部模型用图 | 只被 `slicker/doink.json` 引用；该模型无 blockstate / Java 根 |
| `textures/block/duct_tank_placeholder.png` | 死局部模型用图 | 只被 `duct_tank/placeholder.json` 引用；`blockstates/duct_tank.json` 的四个变体都不引用 placeholder |
| `textures/block/electricity.png` | 死局部模型用图 | 只被 `physics_infuser/zap.json` 引用；`PhysicsInfuserRenderer` 注释写明 ZAP partial 全工程无引用 |
| `textures/block/gas_pump.png` | 死局部模型用图 | 只被 `pump/cog.json` 引用；`PumpDuctRenderer` 明确用 Create 的 `MECHANICAL_PUMP_COG`，不用本工程 `pump/cog` |
| `textures/block/infuservortex.png` | 死局部模型用图 | 只被 `physics_infuser/vortex.json` 引用 |
| `textures/block/junk_gears.png` | 死局部模型用图 | 只被 `juryrigged_infuser/item.json` 引用 |
| `textures/block/juryrigged_infuser.png` | 死局部模型用图 | 同上 |
| `textures/block/peepulon.png` | 死局部模型用图 | 只被 `peepotron.json` 引用；距离传感器用 `peepulon_1..4` + `peepulon_animated` |
| `textures/block/reactionwheel.png` | 死局部模型用图 | 只被 `reactionwheel/old/alsoold/{wheelbottom,wheeltop}.json` 引用 |
| `textures/block/reactionwheelmid.png` | 死局部模型用图 | 同上 |
| `textures/block/reactionwheelspokes.png` | 死局部模型用图 | 同上 |
| `textures/block/vsified_top.png` | 死局部模型用图 | 只被 `vstop.json` 引用 |
| `textures/block/propbearingbase.png` | 旧版轴承链 | 只被 `propeller_bearing/{block,item}.json` 引用；新轴承用 `brass_propeller` / `juryrigged_propeller` |
| `textures/block/propbearingpistons.png` | 旧版轴承链 | 只被 `propeller_bearing/piston*.json` 引用；`propeller_bearing/top.json` + `propbearingtop` 是 Java partial，**保留** |

### D.2 删除的模型（22 个 JSON + `reactionwheel/old/**` 的 9 个 .obj/.mtl）

| 模型 | 依据 |
|---|---|
| `models/block/physics_infuser/core.json` | `PhysicsInfuserRenderer` 类注释：旧版 PHYSICS_CORE partial 全工程无引用 |
| `models/block/physics_infuser/liquid.json` | 同上（STRANGE_FLUID） |
| `models/block/physics_infuser/vortex.json` | 同上（VORTEX） |
| `models/block/physics_infuser/zap.json` | 同上（ZAP） |
| `models/block/pump/cog.json` | `PumpDuctRenderer` 用 Create 的 `MECHANICAL_PUMP_COG`；本模型无 blockstate / Java / 模型引用 |
| `models/block/vstop.json` | 无 blockstate / 物品模型 / Java partial 引用 |
| `models/block/peepotron.json` | 无 blockstate / 物品模型 / Java partial 引用 |
| `models/block/reactionwheel/old/alsoold/wheelbottom.json` | 旧版 OBJ 链，无 blockstate / Java 引用 |
| `models/block/reactionwheel/old/alsoold/wheeltop.json` | 同上 |
| `models/block/slicker/doink.json` | slicker 的 blockstate 走 `slicker/block.json`（`block/slicker`），doink 链无人引用 |
| `models/block/juryrigged_infuser/item.json` | 没有 `models/item/juryrigged_infuser.json`，无 blockstate / Java 引用 |
| `models/block/duct_tank/placeholder.json` | `blockstates/duct_tank.json` 四个变体都不引用它，无 Java 引用 |
| `models/block/chocolate_frosting.json` | 流体误留 blockstate 的伴生模型（只有 `particle` 键） |
| `models/block/strawberry_frosting.json` | 同上 |
| `models/block/vanilla_frosting.json` | 同上 |
| `models/block/propeller_bearing/block.json` | 旧版 `propeller_bearing` blockstate 的模型；现役是 brass / juryrigged |
| `models/block/propeller_bearing/item.json` | 旧版物品模型链；该物品未注册（`cw_ids` 的 items 里没有） |
| `models/block/propeller_bearing/pistonbotl.json` | 无任何 blockstate / 模型 / Java 引用 |
| `models/block/propeller_bearing/pistonbotr.json` | 同上 |
| `models/block/propeller_bearing/pistontopl.json` | 同上 |
| `models/block/propeller_bearing/pistontopr.json` | 同上 |
| `models/item/propeller_bearing.json` | 未注册物品的 item 模型（`cw_ids` items 无此 id、lang 无键） |

`models/block/reactionwheel/old/` 里的 `.obj` / `.mtl`（9 个文件）只被同目录两个 JSON 引用，随 JSON 一起删；工程树其余模型 / Java / blockstate 均无引用。

### D.3 删除的 blockstate（5 个，9 个死 blockstate 的逐条判定见 §9.1）

| blockstate | 分类 | 依据 |
|---|---|---|
| `blockstates/chocolate_frosting.json` | 删除 | 1.20.1 `FabricClockworkFluids.java:24-25` 的 fluid 注册整段被注释；流体不该有 blockstate，属误留 |
| `blockstates/old_andesite_flap_bearing.json` | 删除 | 注册方块是 `andesite_flap_bearing`，复用 `andesite_flap_bearing/block`；无 lang / 物品 / 代码注册点，属旧版别名 |
| `blockstates/propeller_bearing.json` | 删除 | 1.20.1 与 1.21 都没有 plain `propeller_bearing` 方块注册；现役是 `brass_propeller_bearing` / `juryrigged_propeller_bearing`（`CWContraptionsIndex.java:112/124`）；旧模型链一并清理 |
| `blockstates/strawberry_frosting.json` | 删除 | 同 chocolate_frosting |
| `blockstates/vanilla_frosting.json` | 删除 | 同 chocolate_frosting |

> 另有 `OLD_flap_bearing` 只在官方树、工程树没有；官方树只读，不处理。保留的 3 个见 D.4。

### D.4 保留并标注（未移植功能 / Java 直绑）

| 资源 | 分组 | 结论 / 依据 |
|---|---|---|
| `textures/block/alt_meter_on.png` | 未移植 / 待接功能 | 旧版「红石点亮」状态图：`alt_meter/block.bbmodel` 里有第二层贴图，但 blockstate 没有 power 变体；等接点亮状态再上 |
| `textures/block/balloon_casing_connected.png` | 未移植 / 待接功能 | 128×128 CT 连接图集（4×4 格）：`balloon_casing` 仍是 `cube_all`；等接 Create 式 CT / EncasingRegistry 再上 |
| `textures/block/nyx_wanderlite_ore_top.png` | 已接入（本次收口） | 本次已接：`wanderlite/nyx_ore.json` 改 `cube_bottom_top`，top 用此图（官方树也仍是 cube_all，见附录 D.5） |
| `textures/block/angry.png` | 未移植 / 待接功能 | afterblazer 表情/羽流贴图：1.20.1 `ClockworkPartials.kt:15-18` 有声明，方块 / 渲染器没移植；等 afterblazer 一起补 |
| `textures/block/blaze_fury.png` | 未移植 / 待接功能 | 同上（afterblazer） |
| `textures/block/fuming.png` | 未移植 / 待接功能 | 同上（afterblazer 的 PLUME_FUMING） |
| `textures/block/infuriated.png` | 未移植 / 待接功能 | 同上（afterblazer 的 BLAZE_INFURIATED / PLUME_INFURIATED） |
| `textures/block/blue_hue.png` | 未移植 / 待接功能 | physics_infuser 蓝色色相图：渲染器只接了 `purple_hue`（`PhysicsInfuserRenderer.java:67`）；等蓝色光晕功能再上 |
| `textures/block/oneway_duct.png` | 未移植 / 待接功能 | 导管 custom 单向边件：没有任何 blockstate 能选到；`DuctRenderer.java:41` 原话「`duct/custom/*` 四个边件暂无人使用」→ 等待功能移植 |
| `textures/block/smart_duct.png` | 未移植 / 待接功能 | 导管 custom 智能边件：同上，等待 `GasDuctBlock` 加智能属性 |
| `textures/block/copper_duct.png` | 未移植 / 待接功能 | 导管 custom 铜边件：同上，等待铜导管外观接线 |
| `textures/block/hose.png` | Java 直绑，**勿删** | `ExtendonRenderer` 的 `HOSE_TEXTURE` 常量直接 `ResourceLocation(..., "textures/block/hose.png")` |
| `textures/block/compressible_axis.png` | Java 直绑，**勿删** | `UniversalShaftRenderer.java:40` 直接引用；4×14 拉伸用 |
| `textures/block/purple_hue.png` | Java 直绑，**勿删** | `PhysicsInfuserRenderer.java:67` 外层紫色辉光壳 |
| `blockstates/afterblazer.json` | 未移植，保留待补 | 1.20.1 `ClockworkPartials.kt:15-18` 有 BLAZE_INFURIATED / PLUME_ANGRY / PLUME_FUMING / PLUME_INFURIATED，`ClockworkShapes.kt:14` 有 AFTERBLAZER 碰撞箱，但两版都没有方块注册点 / 渲染器 → 未移植的 WIP，保留待补 |
| `blockstates/balloon_encased_shaft.json` | 未移植，保留待补 | 1.20.1 `ClockworkBlocks.kt:1020` 注册过；工程树 `CWBlocks.java:93` 与 `CWKineticsIndex.java:52` 明确写「需要与 balloon_encased_shaft 成对实现 / 需要 EncasingRegistry 集成」→ 未移植，保留待补 |
| `blockstates/combustion_engine.json` | 未移植，保留待补 | 1.20.1 `ClockworkPartials.kt:21-22` 有 ENGINE / SINGLE_ENGINE_PISTON，block/item 模型齐全，但两版都没有方块注册 → 未移植的 WIP，保留待补 |

按功能分组的保留清单：**afterblazer**（`angry` / `infuriated` / `blaze_fury` / `fuming` + `block/afterblazer` 模型）、**combustion_engine**（block/item/4 个模型）、**balloon_encased_shaft**（encased_shaft/block_balloon）、**导管 custom 边件**（`oneway_duct` / `smart_duct` / `copper_duct`）、**旧版状态变体**（`alt_meter_on` / `balloon_casing_connected`）、**physics_infuser 蓝色色相**（`blue_hue`）。

### D.5 `nyx_wanderlite_ore_top` 模型修正（本次唯一改写的模型）

先查官方树：`assets/vs_clockwork/models/block/wanderlite/nyx_ore.json` 是 `minecraft:block/cube_all`，只声明 `all`；1.20.1 源码 `Clockwork-1.20.1-main/common/src/main/resources/assets/vs_clockwork/models/block/wanderlite/nyx_ore.json` 同样是 `cube_all`。也就是说**官方树自己也没接这张顶面图**，没有 `cube_bottom_top` 先例可照抄（深板岩矿用的是 `cube_column` + `end`，末地矿也是 `cube_all`）。

因此这里按本次任务的功能目标接线，而不是照抄官方：

```diff
-  "parent": "minecraft:block/cube_all",
-  "textures": { "all": "vs_clockwork:block/nyx_wanderlite_ore", "particle": ... }
+  "parent": "minecraft:block/cube_bottom_top",
+  "textures": {
+    "top": "vs_clockwork:block/nyx_wanderlite_ore_top",
+    "bottom": "vs_clockwork:block/nyx_wanderlite_ore",
+    "side": "vs_clockwork:block/nyx_wanderlite_ore",
+    "particle": "vs_clockwork:block/nyx_wanderlite_ore"
+  }
```

> 风险提示：**若日后从官方树回拷这个模型，会退回 `cube_all`，顶面图又变成孤儿**；回拷时要保留工程树这一版。

### D.6 官方树 2 处坏引用：只记录、不修

| 贴图 | 官方树里的模型 | 说明 |
|---|---|---|
| `create:block/copper_plating` | `block/valve_duct/pointer` | Create 6 已删除这张贴图；工程树已换成 `copper_casing`。官方树仅作参考、不参与构建，**不要修**；若日后从官方树回拷资源，会把黑紫带回来 |
| `vs_clockwork:block/creative_generator` | `block/creative_gas_generator/item` | 少写 `_gas_` 的笔误；工程树同名模型已改成 `creative_gas_generator`。同上，官方树不修、回拷有风险 |

> 官方树只作参考、不参与构建；在官方树上修完，下次从官方树回拷资源时问题仍会回来。**以工程树为准**：工程树这 2 处已经分别改成 `copper_casing` 与 `creative_gas_generator`。

### D.7 验收（本次）

| 指标 | 收口前 | 收口后 |
|---|---|---|
| `textures/block` 工程树 PNG | 161 | 135 |
| 产物 jar 内 PNG | 161 | 135 |
| 已用 | 118 | 115 |
| 已清理（仅官方树留档） | 0 | 26 |
| 孤儿（仍发布） | 20 | 7 |
| 仅声明（仍发布） | 15 | 3 |
| 工程树模型 | 629 | 607 |
| 工程树 blockstates | 76 | 71 |
| 工程树物品模型 | 101 | 100 |

五关 + 贴图映射校验：`gradlew build` = BUILD SUCCESSFUL；`check_data_refs.py` = 0；`check_assets.py --jar` = 0（[12] 快照已重跑、[13] 仍为 2 条既有提示、未新增）；`check_jar.py` = 0；`check_render_coverage.py` = 0；`check_ponder.py` = 0；`map_textures.py --check` = 0。

> 需实机确认的点：① 深板岩 / 末地 / nyx 三种矿的顶面动画是否一致（nyx 顶面 10 帧）；② 旧螺旋桨轴承删掉后，现役黄铜 / 拼装轴承的 `propeller_bearing/top` 顶盖仍在（Java partial 保留）；③ afterblazer / combustion_engine / balloon_encased_shaft 仍无注册点，属预期「看不到」，不是本次删除造成的。

