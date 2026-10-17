# 资源/纹理审计报告（2026-10-11 · 针对"物品和方块纹理几乎全错"的实机反馈）

> 目标：定位「除了重力枪以外，其他物品和方块纹理几乎全部出错（矿石、楼梯这类标准形状方块正常）」
> 的系统性根因，修掉能证实的缺陷，并把检查补进守门脚本。
> 铁律同 `AUDIT-R23.md`：每条结论都带证据（日志行 / `文件:行号` / javap / 常量池扫描），
> 没有证据的一律不写；**排除掉的假设也记录在案**，避免下一轮重复走弯路。

## 0. 结论摘要

| 项 | 结果 | 证据 |
|---|---|---|
| 用户反馈的"几乎全部黑紫"能否在当前产物上复现 | **不能** | 三个实机日志（见 §1）模型/贴图报错分别为 0 / 9 / 0 条，9 条全是 `solver`+`asteroid_block`（20:34 已修） |
| 代码注册 id ↔ 资源完整性 | **0 缺口** | 68 方块 + 25 物品，`blockstates/` 与 `models/item/` 全齐（`tools/cw_ids.py`，旧脚本因把 `.item()`/`.blockEntity()` 当方块而误报 82 条） |
| 模型链（parent / 贴图键） | 修掉 8 处 | §3 表 |
| 贴图（跨命名空间） | 修掉 3 处 | §3 表 |
| 资源是否被正确打包 | 是 | jar 内 1099 条 = 源码树 1099 条，0 差（`check_assets.py --jar`） |
| 旧版→新版资源拷贝是否无损 | 是（1 处故意不带） | `tools/diff_old_assets.py`：896 vs 1099，缺的只有 `blockstates/OLD_flap_bearing.json`（大写路径，1.21 会忽略） |

## 1. 日志证据（本机 `logs/` 与实例 logs/）

| 日志 | 时间 | 进入世界 | 模型/贴图类报错 |
|---|---|---|---|
| `versions/1.21.1-NeoForge_21.1.251/logs/latest.log` | 21:01–21:16（当前 20:51 产物） | ✅ | **0 条**（只有 3 条 vs_clockwork 的 mip 级别 WARN） |
| 同上 `debug.log`（TRACE/DEBUG 全量） | 同上 | ✅ | **0 条** |
| `2026-10-11-6.log` / `debug-2.log` | 20:17–20:30 | ✅ | 9 条 = `blockstates/solver.json` 6 个变体 + `asteroid_block` 1 个 + `item/solver·item/wanderlite_cube·item/wanderlite_matrix` 3 个模型文件缺失（**20:34–20:57 已修**，见 §3） |
| `2026-10-11-5.log` | 20:01–20:07 | ✅ | 同上 9 条 |

**为什么日志"干净"不等于没问题**：1.21.1 对「模型里的贴图键解析不出来」是**完全静默**的。
证据（`tools/dump_class_strings.py` 扫 `client-1.21.1-…-srg.jar` 的常量池）：
`SpriteLoader` 里没有任何 missing/sprite 文案；`ModelBakery` 只有
`Unable to load model: '{}' referenced from: {}: {}` / `Unable to bake model` / `Error loading missing model`。
所以「黑紫但日志无痕」只能靠静态检查发现 —— 这正是新增 `[7]` 的理由。

## 2. 逐个排除的假设（都做了实验，不是猜）

| 假设 | 结论 | 证据 |
|---|---|---|
| OBJ 模型残留 `forge:obj` | 已修，当前 0 | `check_assets.py [6]`；`models/block/reactionwheel/block.json` 等 8 处 |
| 物品模型（`models/item/<id>.json`）缺失导致背包黑紫 | **否定** | 68+25 个 id 全有模型（`tools/cw_ids.py`）；且日志里没有任何 `Unable to load model: 'vs_clockwork:item/...'` |
| 贴图目录/大小写写错（`block` vs `blocks`） | **否定** | `[8]` 跨命名空间查 5260 张贴图索引，0 缺失（修完 §3 之后） |
| `.png.mcmeta` 动画帧指向不存在的 `_N.png` | **否定（脚本曾经误判）** | 1.21.1 的 `AnimationMetadataSectionSerializer` 常量池含 `frametime/frames/index/time/width/height/interpolate`，帧是**同一张 PNG 的条带**，`width`/`height` 声明帧尺寸；`impactotron/peepulon/spinotron_animated` 的 48x128 / 48x96 + `height:32` 完全自洽 |
| 渲染层（`ItemBlockRenderTypes`）没注册导致黑 | **否定** | javap `client-1.21.1…-srg.jar` 的 `ItemBlockRenderTypes.getRenderType(BlockState,boolean)`：只有 `RenderType.translucent()` 才走 translucent sheet，**其它一律返回 `Sheets.cutoutBlockSheet()`**（字节码 36–39）。即漏注册只会丢半透明，不会黑。另：Registrate `.addLayer` 确实会调 `ItemBlockRenderTypes.setRenderLayer`（javap `BlockBuilder.lambda$registerLayers$5`） |
| `texture_size` 字段在 1.21.1 被删导致 UV 错乱 | **否定** | `tools/scan_jar_bytes.py`：`texture_size` 在 **1.20.1 与 1.21.1 的 client jar 里都是 0 个 class**，即两个版本都忽略它（Blockbench 标注），模型 UV 本来就写在 0–16 空间 |
| 资源没进 jar / jar 字节坏 | **否定** | `check_assets.py --jar`：1099 = 1099，0 字节条目 0，逐条 CRC 读取 0 失败 |
| 旧版资源拷贝丢文件 | **否定** | `tools/diff_old_assets.py`：896 → 1099，唯一"缺"的是大写路径的 `OLD_flap_bearing.json` |

## 3. 本次修掉的缺陷（`tools/fix_asset_refs.py`，可重复执行）

| # | 文件 | 改动 | 为什么（证据） |
|---|---|---|---|
| R1 | `models/block/creative_gas_generator/item.json` | `block/creative_generator` → `block/creative_gas_generator` | 该贴图在所有 jar/原版资源里都不存在，而同目录 `block.json` 用的是 `creative_gas_generator.png`（磁盘上确有，2556 B）→ 创造气源的**物品形态一直是黑紫** |
| R2 | `models/block/valve_duct/pointer.json` | `particle` 的 `create:block/copper_plating` → `create:block/copper_casing` | `copper_plating` 在 `libs/create-1.21.1-6.0.10.jar` 里不存在（逐条列过 create 的 copper 贴图） |
| R3 | `compressor/{compressor,item}.json`、`brass_propeller_bearing/item.json`、`juryrigged_propeller_bearing/item.json`、`physics_infuser/zap.json` | `"texture": "#missing"` → 该模型里已有的语义最近贴图（共 13 个面） | Blockbench 未分配贴图的残留；1.21.1 会静默渲染成黑紫。其中拼装螺旋桨轴承的**整根中轴 6 个面**都受影响，物品图标能直接看到 |
| R4 | `textures/gui/gas_filter_OVERVIEW.png` → `gas_filter_overview.png` | 改名 | 路径含大写，1.21 的资源包校验会 `Invalid path in pack … ignoring`（同 `OLD_flap_bearing.json` 的报错形态） |
| R5 | 删除 `models/item/physics_infuser` | 删 0 字节无扩展名文件 | 会被原样打进 jar（`check_assets.py --jar` 的 0 字节条目检查就是抓这个） |
| R6 | `lang/{en_us,zh_cn}.json` | 补 `block/item.vs_clockwork.solver` | `SolverBlock` 是本轮新增方块，旧版 lang 里没有；缺了会在游戏里显示原始翻译键 |
| — | 旧版遗留 8 个未注册方块（`afterblazer`、`combustion_engine`、`propeller_bearing`…） | **不动** | 未注册 = 不会被加载，属 `[5]` 提示项；删资源反而丢参考 |

`tools/diff_old_assets.py` 另记录：`particles/phys_lightning.json` 旧版 fabric 模块 417 B、forge 模块 4 B，
本移植**拷的是 forge 那份（4 B）**——与我们要移植的目标一致，**不是回归**，故未改。

## 4. 守门脚本强化（`tools/check_assets.py`）

新增/重写后共 11 项，`--jar` 时含产物自检；`合计问题` 只统计硬失败项（[3]/[5] 为提示）：

| 项 | 内容 | 为什么加 |
|---|---|---|
| [1][2][3] | 注册 id ↔ blockstate / item 模型 / 语言 | **重写 id 提取**：旧正则把 `.item("x")` 与 `.blockEntity("x")` 都算方块，导致 82 条误报 + 40 多个机器方块漏报。现抽到 `tools/cw_ids.py`（按文件解析本地辅助方法，且排除 `REGISTRATE.simple(name, registry, …)` 这类非方块调用） |
| [4] | blockstate → 模型（含 parent 链） | 原语义保留 |
| [6] | OBJ：loader / .obj / mtllib / usemtl / map_Kd → 贴图键 → png | 修掉"非本模组命名空间一律跳过"的假阳性（`create:block/axis` 曾经被误报） |
| **[7]** | **贴图键 `#key` 能否沿 parent 链解析** | **新增**。对应"黑紫但日志无痕"这一唯一静默失败模式 |
| **[8]** | 具体贴图引用跨命名空间存在性 | **新增**。索引来自 libs/*.jar + 实例 mods/*.jar + 原版 `client-…-extra.jar`（原版贴图在 -extra.jar，不在 -srg.jar，也不在 `assets/indexes`） |
| **[9]** | png 合法性 + mcmeta 动画（按 `calculateFrameSize` 语义） | **新增**。前两版脚本分别按 `_N.png` 文件语义和"正方形帧"假设写，制造了 16 条 + 3 条假问题 |
| **[10]** | 非法资源路径 | **新增**。1.21 静默忽略这类文件（`OLD_flap_bearing.json` 的教训） |
| **[11]** | 产物 jar ↔ 源码资源树 + 0 字节条目 + 逐条 CRC | **新增**。对应"边跑边覆盖导致 invalid LOC header"那次事故 |

自检：`python tools/selftest_assets.py` —— 11 类坏数据全部被对应检查报出，恢复后回到 0
（含"造一个源码有、jar 里没有的资源"来验证 [11]）。

## 5. 仍然需要实机确认的点

1. 反馈里"几乎全部黑紫"在当前产物上无法复现（日志与静态检查都干净）。需要用户提供：
   **一张截图 / F3 界面 / 具体方块名**，或按 `F3+T` 重载资源后再看一次。
2. 用户的主整合包实例 `versions/CSN_The Song Of The Sky`（jar 为 18:16 的旧构建）日志里有
   `Unexpected error while registering entry vs_clockwork:distance_sensor` /
   `lodefind`… 即 **FACING 注册崩溃的旧版本**，该实例需要换新 jar 才能谈纹理问题。
3. 本次修的 13 个 `#missing` 面 + 创造气源物品贴图，装包后应在物品栏可见地变正常。
