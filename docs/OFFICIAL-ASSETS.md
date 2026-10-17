# 官方解包资源的差分整合（2026-10-16，duct_tank 部分 2026-10-17 更新）

> **2026-10-17 第四轮（物品图标 / 连通性）**：`models/item/duct_tank.json` **不再** parent 世界模型
> `block_single`，而是 parent 新增的 `models/block/duct_tank/block_single_item.json`
> （官方 `block_single` 的逐字节副本，64×64 图集 `(0,3)` 格）。世界 `block_single` 保持
> "Create 几何 + 16×16 基础贴图"以配合 CT。本文 §1 中"物品模型同官方 parent `block_single`"的
> 表述已作废；最新说明见根目录 [`../../docs/GAS-TANK.md`](../../docs/GAS-TANK.md)。

> 本文是**官方资源树 vs 工程资源树**的权威对照与整合结论。
> 讨论对象：
>
> * **官方树**（权威版本，从 Clockwork 官方发布包解出）：`assets/vs_clockwork/**`
> * **工程树**（1.21.1 / NeoForge 目标）：`clockwork_unoffical/src/main/resources/assets/vs_clockwork/**`
>
> 在此之前，工程只能"从 `Clockwork-1.20.1-main` 源码拷 + 程序生成补齐"；
> 官方树是第一次拿到的**完整原始资源**。相关文档：
> [`DUCT-TANK-LOOK.md`](DUCT-TANK-LOOK.md)（储罐外观方案）、
> [`DUCT-TANK-UV.md`](DUCT-TANK-UV.md)（UV 缩放一致性）。

---

## 0. 结论速览

| 项 | 结果 |
|---|---|
| 官方树文件数 | 895 |
| 工程树文件数 | 1160 |
| ① 工程缺失、官方有 | **4**（全部是死资源 / 0 字节占位 / 大小写重复，见 §2.1） |
| ② 两边字节一致 | **867** |
| ③ 两边不同（需逐条判断） | **24**（剩余条目全部判定为"保留工程版"，见 §2.3） |
| ④ 工程独有 | **269**（1.21.1 新增资源 + 本轮补的模型） |

> 计数按 2026-10-17 复测（`python tools/diff_official_assets.py`）：duct_tank 的
> 5 个模型、blockstate、物品模型已改为**逐字节等于官方**（其中 4 个模型 + blockstate +
> 物品模型原属 ③），从 ③ 移入 ②；上一轮新增的 4 个 `*_window` 删除、新增 2 张裁切贴图，
> 工程文件数 1162 -> 1160、④ 271 -> 269。

**一句话原则**：① 类照收（但要先判死资源）；③ 类**一律不能无脑用官方覆盖** ——
官方那份是 1.20.1 的，工程版里有大量 1.21.1 修正（`neoforge:obj`、`mtllib` 路径、
缺失模型补齐、大小写、火焰 selector 方向），覆盖会直接让移植回归。

复现命令：

```powershell
python tools/diff_official_assets.py             # 三类清单 + _probe_tmp/official_diff.json
python tools/diff_official_assets.py --list A    # 只看某一类
python tools/classify_official_conflicts.py      # 把 ③ 类再细分（换行/JSON 语义/文本/二进制）
python tools/json_field_diff.py                  # ③ 类逐字段打印"官方 -> 工程"改了什么
```

---

## 1. 储罐（duct_tank）：外观改为**官方模型**（2026-10-17 更新）

> **本节旧结论（"保留工程版 Create 模型、不采用官方模型"）已被用户反馈推翻。**
> 现行方案：外观以官方 Clockwork 模型为准，Create 6.0.10 版**已废弃**。
> 完整取舍、贴图裁切细节与实机确认点见 [DUCT-TANK-LOOK.md](DUCT-TANK-LOOK.md)。

### 1.1 最终采用（逐字节）

| 文件 | 官方（解包树） | 工程（现在） | 判定 |
|---|---|---|---|
| `blockstates/duct_tank.json` | 4 变体，键只含 `bottom`/`top` | **同官方，逐字节** | 采用官方 |
| `models/item/duct_tank.json` | `parent: .../block_single` | **同官方** | 采用官方 |
| `models/block/duct_tank/block_single.json` | 1 element，uv `[0,12,4,16]` | **同官方** | 采用官方 |
| `models/block/duct_tank/block_top.json` | 5 element，uv 16x4 / 16x12 / 16x16 | **同官方** | 采用官方 |
| `models/block/duct_tank/block_bottom.json` | 5 element，上下镜像 | **同官方** | 采用官方 |
| `models/block/duct_tank/block_middle.json` | 4 element，uv 16x16 | **同官方** | 采用官方 |
| `models/block/duct_tank/placeholder.json` | 占位模型 | **同官方** | 采用官方 |
| `textures/block/duct_tank.png`<br>`textures/block/duct_tank_top.png` | **不存在** | **16x16，由官方 64x64 CT 图集的 (0,3) 格裁出** | 补缺（见 §1.3） |
| `models/block/duct_tank/*_window.json` | 官方没有 | **已删除**（上一轮从 Create 复制的 4 个） | 废弃 |
| `textures/block/duct_tank_connected.png`<br>`textures/block/duct_tank_top_connected.png` | 64x64 CT 图集 | 同左（`block_single` 直接引用） | 采用官方 |
| `textures/block/fluid_tank_inner.png` | 16x16 | 同左（`block_top/bottom` 内表面引用） | 采用官方 |

工程里已**没有任何** `create:block/fluid_tank*` 引用（jar 内实测 0 处）。

### 1.2 为什么官方模型现在能直接用

上一版列过三条"不能用官方模型"的理由，现在逐条消解：

1. **缺 `duct_tank.png`/`duct_tank_top.png`** → 已从官方图集 (0,3) 格精确裁出 16x16，
   并与 `block_single` 的 uv 指向逐像素一致（§1.3）。
2. **uv 与贴图尺寸"不自洽"** → 自洽：`block_single` 是"64x64 图集取一格"= 1 px/单位，
   `block_top/bottom/middle` 是"16x16 满铺一面"= 1 px/单位；把缺图做成 16x16 后全族同为
   1.000 px/单位，`check_assets.py [13]` 对 duct_tank 零提示。
   （"64x64 满铺 = 4 px/单位"只发生在把 64x64 图**当作**基础贴图时，本轮不这么做。）
3. **没有观察窗** → 官方本来就没有。`large` 仍然是代码里正常维护的属性，但官方 blockstate
   的键不写它（部分匹配），8 个状态各命中一个变体、不会黑紫；`large` 不再影响外观。
   代价是官方外壳封闭、从外面看不到罐内气面（见 DUCT-TANK-LOOK.md §5）。

**属性覆盖**：官方 4 个键把 `bottom`/`top` 的组合各命中一次，`large` 的 2 个取值由部分匹配
覆盖；这正是 1.21.1 `Selector` 的语义，`check_assets.py [12]` 逐状态验证为 0。

### 1.3 两张缺失贴图怎么来的

`block_single.json` 声明 `texture_size: [64,64]`，六面 uv 都是 `[0,12,4,16]`。
uv 是 0..16 归一化坐标（`texture_size` 游戏不读），换算成 64x64 像素就是
**x 0..16、y 48..64 = 第 0 列第 3 行（左下角）那一格 16x16 面板**。

* 侧壁：`duct_tank_connected.png` 的 (0,3) 格 → `duct_tank.png`
* 顶/底：`duct_tank_top_connected.png` 的 (0,3) 格 → `duct_tank_top.png`

选这一格是因为 `block_top` 的盖沿 uv `[0,0,16,4]` + 壁 uv `[0,4,16,16]`
（`block_bottom` 为 `[0,12,16,16]` + `[0,0,16,12]`）正好把同一张面板上下拼回来，
`block_middle` 整面就是这一格 ⇒ 单块/顶段/底段/中段的画完全一致。

复现：`python tools/gen_duct_tank_textures.py`（自带标准库 PNG 读写，不依赖 Pillow；
`--check` 逐字节核对）。工程内的图集副本会先与官方树比对，防止从被改过的图派生。

### 1.4 两个生成脚本的处置

| 脚本 | 现状 |
|---|---|
| `tools/gen_duct_tank_textures.py` | **重写**为"从官方 CT 图集裁 16x16"（之前是"绝不覆盖"的诊断/兜底脚本，已不符合当前需求），支持 `--check` |
| `tools/gen_duct_tank_assets.py` | **重写**为官方资源还原管线：5 个官方模型 + 官方 blockstate + 官方物品模型逐字节拷贝、删除 4 个 Create 窗模型、调用上面的裁切；`--check` 额外检查"不许残留 `create:`" |

两个脚本都只用标准库；`gen_duct_tank_assets.py --check` 会核对 11 个产物。

---

## 2. 三类差分清单与逐条处置

### 2.1 ① 工程缺失、官方有（4 条）—— 全部不整合，理由如下

| 文件 | 官方大小 | 处置 | 理由 |
|---|---|---|---|
| `blockstates/OLD_flap_bearing.json` | 643 B | **不收** | 官方树里名字带 `OLD_` 的死资源，旧版自己都不引用（工程树另有一个活的 `flap_bearing`）。收进来只会让 `check_assets.py [5]`"有资源但代码未注册"多一条噪声 |
| `models/item/physics_infuser` | **0 B** | **不收** | 0 字节**目录占位文件**，不是模型（真正的物品模型是 `physics_infuser.json`） |
| `textures/gui/jei-widgets.png` | **0 B** | **不收** | 0 字节占位。工程里已有**同内容的真图** `textures/gui/jei_widgets.png`（9391 B）—— 官方的连字符命名在 1.21 资源路径规范里也不合法 |
| `textures/gui/gas_filter_OVERVIEW.png` | 2789 B | **不收（已等价拥有）** | 官方用**大写**路径 `_OVERVIEW`，1.21 会静默忽略（`check_assets.py [10]`：非法资源路径）。工程的 `gas_filter_overview.png` 与它**大小/内容完全相同**，是小写化修正版 |

> 注：`textures/gui/gas_filter_overview.png` 与 `jei_widgets.png` 目前都**没有代码引用**
> （全仓库 grep 无命中），属于旧版遗留资源；但它们**不是 0 字节**、且在 1.21 上路径合法，
> 保留成本为零，故维持现状。

**本轮实际整合的 ① 类**：`particles/phys_lightning.json`（见 §2.4）。

### 2.2 ② 两边字节一致（867 条）—— 无需动

覆盖 `blockstates`(70)、`models`(397)、`textures`(311)、`sounds`(61)、`ponder`(11)、
`lang`(8)、`particles`(4)、`font`/`flywheel`/图标等。**其中 `ponder/**` 11 个 nbt 全部一致**，
见 §3.3。（10-16 基线为 861 条；10-17 起 duct_tank 的 5 个模型 + blockstate + 物品模型
也进入本类。）

### 2.3 ③ 两边不同（2026-10-16 基线 30 条）—— 逐条判定，全部"保留工程版"

> **2026-10-17 更新**：duct_tank 的 6 个文件（4 个有差异的模型 + blockstate + 物品模型；
> `placeholder.json` 本来就与官方一致）已改为逐字节等于官方，不再属于 ③；当前 ③ 为 **24 条**
> （下表是 10-16 的基线分类，除 duct_tank 外的条目未变）。

用 `tools/json_field_diff.py` 逐字段看过后，10-16 基线 30 条可归为 6 类**全部是 1.21.1 侧的修正**
（其中 duct_tank 的 blockstate 已在 10-17 改采官方，表中已从"生成变体"类移走）：

| 类别 | 条数 | 代表文件 | 官方 -> 工程的改动 | 判定理由 |
|---|---|---|---|---|
| **`porting_lib` -> `neoforge` loader** | 2 | `models/block/gyro/block.json`、`models/block/reactionwheel/block.json` | `"loader": "porting_lib:obj"` -> `"neoforge:obj"` | 1.21.1 没有 porting_lib，官方那份**加载即失败**；`check_assets.py [6]` 专查这项 |
| **OBJ `mtllib` 路径修正** | 2 | `models/block/reactionwheel/old/{wheeltop,wheelbottom}.obj` | `mtllib wheel.mtl` -> `mtllib alsoold/wheel.mtl` | 材质库实际在 `alsoold/` 子目录，官方路径解析不到 |
| **OBJ 重导出（Blockbench 4.11.2 -> 5.1.4）** | 1 | `models/block/reactionwheel/reactionwheel_shaftless.obj` | 法线/顶点浮点重算，2237 vs 2284 行 | 官方那份是旧版导出；工程版是重导出结果，几何等价、精度更规范 |
| **生成的 blockstate（补变体）** | 1 | `blockstates/heat_pipe.json`（30 -> 38 条 multipart；2026-10-17 收口轮再按 `heat` 属性展开为 38×6=228 条） | 补 `heat_pipe` 的生成模型与六档热度模型 | 1.21.1 侧的重做；官方 `heat_pipe` 变体不足，会缺模型（黑紫）。六档展开见 `tools/gen_heat_pipe_blockstate.py`（幂等）。（`duct_tank` 已改用官方 4 变体，见 §1） |
| **火焰 selector 方向修正** | 1 | `blockstates/coal_burner.json` | `multipart[4].when.AND[0].facing`: `east` -> `north` | 官方把朝北的点燃模型挂在了 `east` 分支上（同一分支里其余条件是 north），**朝北点火看不到火焰**；工程版是修好的 |
| **语言文件扩张 / 单字节格式差异** | 2 | `lang/{en_us,zh_cn}.json`（+91 / +65 键） | 官方缺 `filter_duct`/`heat_pipe`/`solver`/`straight_duct`/`gimbal_bearing`/`sterling_engine` 等条目 | 工程版包含本轮移植新增方块的条目，**覆盖会掉语言** |
| **单字节差异（格式/精度）** | 20 | `models/block/{compressor,clock,brass_propeller_bearing,...}`、`models/block/valve_duct/pointer.json`、`sounds.json` 等 | 1~30 字节的等价微调 | 见下 |

`sounds.json` 单独说明（3 个键的取舍）：

| 键 | 官方 | 工程 | 判定 |
|---|---|---|---|
| `doink` vs `doing` | 官方键名 `doing`，指向 `vs_clockwork:doink` | 工程键名 **`doink`** | **保留工程版**。代码里 `CWKineticsSounds.DOINK = create("doink")`，查的是 `doink`；官方 `doing` 与音频文件名 `doink.ogg` 不符，是官方笔误 |
| `supersonic` | 官方有，指向 `vs_clockwork:egg_reverie` | 工程没有 | **不收**。`egg_reverie.ogg` **在官方树的 61 个 ogg 里根本不存在**（全仓库 grep 也无命中），且 1.20.1 与 1.21.1 代码都不引用 `supersonic`；收进来会让 `check_data_refs.py` 的"sounds.json 指向不存在的 ogg"从 0 变 1 |
| `gravitron_lock` / `gravitron_unlock` | 官方没有 | 工程有（指向实存的 `gravitron_freeze` / `gravitron_fling`.ogg） | 工程为 gravitron 切片新增，代码 `GravitronSounds.LOCK/UNLOCK` 在用 → 保留 |

**③ 类总判定（10-16 基线 30 条）：全部"保留工程版"，没有一条采用官方版。**
这不是偷懒 —— 官方那份是 1.20.1 的，而工程版恰好是"为 1.21.1 修过"的那一份；
逐条理由已用 `tools/json_field_diff.py` 固化，任何人可以复核。
（10-17 唯一例外是 duct_tank：用户明确要求官方外观，且缺失贴图已能精确还原，故 6 个文件
改采官方、退出 ③；当前 ③ = 24 条。）

### 2.4 ④ 工程独有（271 -> 269 条）—— 1.21.1 新增 / 本轮补齐

含 `models/block/**` 里 1.21.1 新增的模型、`blockstates` 4 个、`textures` **8** 个
（`duct_tank_gas.png` + 从官方图集裁切的 `duct_tank.png` / `duct_tank_top.png`），以及本轮补的
`wanderlite_cube` / `wanderlite_matrix`（见 §3.2）。
10-17 删除上一轮的 4 个 Create `*_window` 模型、补 2 张裁切贴图，故 271 -> 269。

**唯一实质内容丢失修复**：`particles/phys_lightning.json`
工程里是 **4 字节的空 `{}`**（`{}` + 换行），官方是 417 字节的完整 15 帧 zap 贴图列表。
`textures/particle/zap_0..14.png` 在**两边树里都在**（工程 15 张齐全），只是粒子定义被写空了
→ 该粒子即使被引用也只会渲染成空白。**本轮已用官方文件覆盖**（字节一致），
`check_data_refs.py` 的 `textures` 引用校验通过。

---

## 3. 三个已知缺口：官方资源能不能解决

### 3.1 `solver` 贴图 —— ❌ 官方树里根本没有

官方树全树 grep `solver` **零命中**（没有 blockstates / models / textures）。
工程现有 `blockstates/solver.json` + `models/block/solver.json` + `models/item/solver.json`，
模型用 `create:block/industrial_iron_block`（`..._top`）占位。
**结论：官方资源无法解决，继续用 Create 工业铁。** 原因很可能是 1.20.1 的 solver
本来就是借用工业铁（Create 前置），并没有自带贴图。

### 3.2 `wanderlite_cube` / `wanderlite_matrix` —— ❌ 官方没有，工程自补

官方树有 `wanderlite_block` 系列（方块模型 + 物品模型，与工程字节一致），
但**没有** `models/item/wanderlite_cube.json`、`models/item/wanderlite_matrix.json`；
工程在 2026-10-11 自行补齐（1200 B / 1526 B，`wanderlite_cube/matrix` 是工具类物品）。
**结论：官方资源无法解决，保留工程自补版本。**

顺带修掉的一条：`models/item/wanderlite_block.json` 曾经缺失（`check_assets.py [2]`
报"物品形态会是 missing"），官方树里**有**且与工程现有文件字节一致 ——
即该项已由官方资源对齐（工程在 10-16 20:58 补齐，hash 相同）。

### 3.3 Ponder `ponder/*.nbt` —— ✅ 官方有，且工程已全部就位

官方 11 个场景 nbt（`alt_meter`、`cambered_wings`、`duct`、`flap_bearing`、`gyro`、
`plane_tips`、`resistor`、`smart_flap_bearing`、`solid_delivery`、`wanderwand`、`wings`）
与工程树 **11/11 字节一致**。也就是说 Ponder 集成所需的 nbt **已经全部在工程里**，
只差"注册场景"这一步。

> ✅ **2026-10-17 已注册**：这 11 个场景全部接进 `PonderSceneRegistrationHelper`
> （注册表 `integration/ponder/ClockworkPonderScenes.java`，一个场景一行；
> 逐场景差异见 `docs/PORT-STATUS.md` §7）。静态校验用 `tools/check_ponder.py`
> （nbt 存在性 / nbt 里的方块是否注册 / lang 键 / title 与 nbt 同名）。
> 原先"只盘点、不注册"的任务边界已经解除。

---

## 4. 最终整合动作清单（可追溯）

| 动作 | 文件 | 理由 |
|---|---|---|
| 覆盖（采用官方） | `particles/phys_lightning.json` | 工程版是 4 字节空 `{}`，官方 417 B 完整定义；zap 贴图两边都在 |
| 覆盖（采用官方，10-17） | `models/block/duct_tank/*.json`（5 个）、`blockstates/duct_tank.json`、`models/item/duct_tank.json` | 外观以官方为准；逐字节复制，贴图引用全为 `vs_clockwork:block/...` |
| 删除（10-17） | `models/block/duct_tank/{block_single,block_top,block_bottom,block_middle}_window.json` | 上一轮从 Create 复制的观察窗模型，官方没有；已无引用 |
| 新增（10-17） | `textures/block/duct_tank.png`、`duct_tank_top.png` | 官方缺失；从官方 CT 图集 (0,3) 格裁 16x16（见 §1.3） |
| 重写 | `tools/gen_duct_tank_textures.py` | 改为"从官方图集裁切"（含标准库 PNG 读写、`--check`） |
| 重写 | `tools/gen_duct_tank_assets.py` | 官方资源还原管线：拷贝官方模型/blockstate/物品模型 + 删窗模型 + 调用裁切；`--check` 检查残留 `create:` |
| 新增工具 | `tools/diff_official_assets.py`、`tools/classify_official_conflicts.py`、`tools/json_field_diff.py` | 让"三类清单 + 逐条判定"可复现，而不是只存在于汇报里 |
| 不动 | ② 类 867 条；③ 类 24 条（全部保留工程版） | 见 §2.2 / §2.3 |

---

## 5. 每类文件怎么处理（速查表）

| 类别 | 处理规则 | 理由 |
|---|---|---|
| `textures/**`（两边一致） | 无需动 | 官方即权威 |
| `textures/**`（工程缺） | 先判"0 字节占位 / 大写路径 / 是否被引用"，再决定 | 官方树里有 0 字节占位与非法大小写路径 |
| `textures/**`（两边不同） | **保留工程版** | 工程版通常是 1.21 路径修正（小写化） |
| `models/**/*.json`（loader 字段） | **保留工程版**（`neoforge:obj`） | 1.21.1 无 porting_lib |
| `models/**.obj` | **保留工程版**（`mtllib` 路径 + 重导出） | 官方路径解析不到材质库 |
| `models/block/duct_tank/**` | **采用官方版（逐字节）**，贴图引用全为 `vs_clockwork:block/...` | §1；Create 版已废弃 |
| `textures/block/duct_tank{,_top}.png` | 官方缺失，**由官方 CT 图集 (0,3) 格裁 16x16**（脚本产出） | §1.3；不要手绘/覆盖 |
| `blockstates/*.json` | 逐条看：变体数不足就用工程版 | 官方 1.20.1 的变体表在 1.21.1 上会缺模型 |
| `lang/*.json` | **保留工程版** | 工程含新增方块条目，覆盖会掉语言 |
| `sounds.json` | 逐键判断（键名 + ogg 是否存在） | 官方有笔误键 `doing` 与悬空引用 `supersonic` |
| `sounds/*.ogg` | 无需动（61/61 一致） | — |
| `particles/*.json` | 逐条判断，空 `{}` 的一律用官方补 | §2.4 |
| `ponder/*.nbt` | 无需动（11/11 一致） | §3.3 |
| `font/`、`flywheel/`、`icon*.png`、`palettes/` | 无需动 | — |

---

## 6. 守门与验收

```powershell
cd clockwork_unoffical
$env:JAVA_HOME="C:\Users\yql11\java\dragonwell-21.0.8.0.8+9-GA"
.\gradlew.bat --offline build                    # BUILD SUCCESSFUL
cd ..
python tools/check_data_refs.py                  # 合计问题 0 个
python tools/check_assets.py --jar               # 合计问题 0 个
python tools/check_jar.py clockwork_unoffical/build/libs/vs_clockwork-0.6.0-1.21.1.jar   # 0
python tools/gen_duct_tank_assets.py --check     # 与官方树/裁切结果一致（11 个文件）
python tools/gen_duct_tank_textures.py --check   # 2 张 16x16 与官方图集 (0,3) 格一致
python tools/diff_official_assets.py             # A=4 B=867 C=24 D=269
```

* **[12] 属性覆盖**：本轮**没有改任何 Java、没有改方块状态属性名**。
  但执行期间有**并行任务在改 `content/contraptions/**` 的 Java**，编译产物一变，
  `[12]` 就按设计报"属性快照已过期（`1863c069…` -> `df78f400…`）"。
  按脚本提示重跑 `python tools/probe_blockstates.py`（68 个方块、fail=0）后 `[12]` 归 0。
  **没有**改任何资源或守门逻辑来绕过。此后若又有 Java 编译，需再次重跑该探针。
* **[13] UV 缩放一致性**：官方模型全族 1.000 px/单位（`block_single` 从 64x64 图集取一格；
  多层段用 16x16 基础贴图），因此不回归；`[13]` 剩余提示仍为既有 7 条，只在
  `heat_pipe` / `straight_duct` / `valve_duct`；duct_tank 不在其中。
  **2026-10-17 收口轮更新**：`heat_pipe` 六档模型改为按贴图内容区 + 统一 `UV_SCALE=0.97`
  重新生成后，`[13]` 只剩 2 条（都在 `valve_duct`，属既有提示），见 `BLOCKSTATE-COVERAGE.md` §3 B5。

### 6.1 本轮实测结果（2026-10-17）

| 检查 | 结果 |
|---|---|
| `.\gradlew.bat --offline build` | **BUILD SUCCESSFUL**（20s；`processResources` + `jar` 执行） |
| `python tools/check_data_refs.py` | **合计问题 0 个** |
| `python tools/check_assets.py --jar` | **合计问题 0 个**（`[1]~[12]` 全 0；提示 `[5]` 8 条、`[13]` 7 条，均为既有项） |
| `python tools/check_jar.py build/libs/…jar` | **合计问题 0 个**（1680 个条目） |
| `python tools/gen_duct_tank_assets.py --check` | 一致（11 个文件） |
| `python tools/gen_duct_tank_textures.py --check` | 一致（2 张 16x16） |
| `python tools/diff_official_assets.py` | A=4 B=867 C=24 D=269 |
| jar 内 `duct_tank` 模型含 `create:` | **0 处**（全部为 `vs_clockwork:block/...`） |
| jar 内 `duct_tank.png` / `duct_tank_top.png` | 存在，16x16 RGBA、PNG 头合法 |
| jar 内 4 个 `*_window` 模型 | **不存在** |

> 上一版记录的"构建缓存 `Could not get file mode`"是 10-16 并行构建偶发问题；
> 10-17 本轮 `build` 一次通过。装包状态见 `DUCT-TANK-LOOK.md` 或本轮汇报。

