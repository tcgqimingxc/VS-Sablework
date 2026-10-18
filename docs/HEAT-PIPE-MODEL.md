# 热管（`heat_pipe`）连接模型：方向约定 / UV 规则 / 自检

> 2026-10-18 返工。对应实机反馈"热管连接模型异常"。
> 生成器：`tools/gen_heat_pipe_models.py`、`tools/gen_heat_pipe_blockstate.py`
> 断言：`tools/selftest_heat_pipe_model.py`（已挂进 `tools/run_gates.ps1 -SelfTest`）
> **连接判定与传热的语义见 `docs/HEAT-PIPE-CONNECTION.md`**（本文只谈几何/UV；"6 个连接布尔"
> 在那边被定义为"热接触"，与画臂一一对应）。

## 1. 异常的具体表现与根因（证据）

| # | 表现（实机） | 根因 | 证据 |
|---|---|---|---|
| 1 | **连接处只剩核心方块与一圈薄法兰，管子"断了"** | 生成器把 `heatpipe_connection.png` 当"内容区 (0,6)-(10,10)"居中取景。这片 5x4 区域里只有 2 个不透明 texel（x=10 那一列），`cutoutMipped` 下内管/外壳的所有面**全透明** | 逐 texel 采样：`uv=[0.09,6.09,5.91,9.93]` 内不透明数 = 0；旧模型 `lu_x` 等 222 个臂盒子全部如此 |
| 2 | 4 通管路**缺两根臂**（东西向直管 + 上方 + 南侧分支时，东西向断开） | 官方 30 条平面 multipart 每条只写一个平面的 4 个属性，另外两个属性 = 任意值。64 个连接状态里有 **12 个**（4 通、缺的两臂互相垂直）只命中一个 2 臂模型 | `_probe_tmp/probe_heatpipe_states.py` 全 64 状态枚举：12 个状态"臂 ≠ 连接"，例 `up+south+east+west` → 只画 south+up |
| 3 | 核心/臂有**同几何叠画**（z-fighting、双倍填充率） | 同一设计副作用：64 个状态**全部**命中 2~3 个模型（每模型自带核心与臂），最多 18 条 entry 同时命中（÷6 档 = 3 个模型） | 同上枚举：`up+south` 命中 `lu_x`+`u_y`+`u_z`，三个核心盒子完全重合 |
| 4 | 接缝处 z-fighting | 每个盒子都画 6 个面：管口端面与邻居的管口端面在同一平面（z=0/16），靠核心一端与核心面共面 | 旧模型元素 `faces` 全部 6 面 |
| 5 | 收口环偏厚、法兰比官方大一圈 | 手写常量 `RIM_IN/OUT=13.9/16.15`、截面 4.4~11.6；官方 Blockbench 是 `14.05~16.05`、`4.5~11.5` | 官方 `models/block/duct/block_old.json` |

## 2. 方向约定（唯一权威）

`HeatPipeBlock` 的 6 个连接布尔属性 `up/down/north/south/east/west` **直接表示"这一面有邻居"**
（`DIR_TO_CONNECTION`），blockstate 的 `when` 也只写这一件事。因此：

* 一个状态 = 1 个 `core` + 每根**朝各自方向伸出的单臂**（`arm_<dir>`）；
* 臂的方向 = 属性为 true 的方向，不存在"平面/左右上下"这种二次映射（旧 `lu_x` 命名那套就是死在这里）；
* 64（6 位连接）× 6（热度）= 384 个状态全部由同一套规则拼出，不存在"某些组合没模型"。

部件级 multipart（`blockstates/heat_pipe.json`，共 42 条）：

```json
{"apply": {"model": ".../heat_pipe/core"},       "when": {"heat": "cool"}}
{"apply": {"model": ".../heat_pipe/arm_north"}, "when": {"heat": "cool", "north": "true"}}
... 6 个方向 × 6 档 ...
```

不再沿用官方 30 条平面形状的原因见 §1 第 2/3 条：那套条件对 12 个状态缺臂、对全部状态叠画。

## 3. 几何：逐字取自官方 Blockbench

来源：`Clockwork-1.20.1-main/common/src/main/resources/assets/vs_clockwork/models/block/duct/block_old.json`
（"Made with Blockbench"，和 `heatpipe_core/connection/rim` 三张贴图同一批；`groups` 按方向分成
`core` / `connection.north..down` / `rim.north..down`）。每个方向 3 件：

| 件 | 尺寸 | 说明 |
|---|---|---|
| `inner` | 6.00 × 6.00 × 5 | 内管，从核心面（5/11）伸到方块边界（0/16） |
| `shell` | 6.15 × 6.15 × 5 | 外壳，只比内管大 0.075；UV 取"1px 描边"单元 → 管口一圈暗边 |
| `rim` | 7.00 × 7.00 × 2 | 收口环，压在边界上（沿轴 -0.05~1.95，故意探出 0.05，避免与邻居法兰共面 z-fight） |

**内管/外壳只给 4 个侧面**：两端的端面必然与别的东西共面（靠核心那头贴 core 的 6x6 面、
靠边界那头贴邻居的管口），画出来就是同平面两份几何。官方模型也是这么做的。

与官方的差异只有两处，都是**面的取舍/追加**，几何数值一个没动：
物品模型 `item_straight` = core + 上/下两臂 + 收口环 + 官方 `display` 姿态（`models/item/heat_pipe.json`
的 parent）。方块版 `straight` 已删除：`straight_duct` 从 2026-10-18 起改用自己的 `block/duct/straight`。

## 4. UV 规则（为什么这样取景）

三张贴图都是 16×16 的手绘展开图，**整张并不都有效**（alpha 只有几块）。有效单元格：

| 贴图 | 单元格 | 用途 |
|---|---|---|
| `heatpipe_core` | `(0,0)-(6,6)` | 核心 6 个面 |
| `heatpipe_connection` | `(0,10)-(5,16)`<br>`(10,11)-(16,16)` | 内管侧面 5x6（含镜像）<br>内管端面 6x5 |
| | `(5,10)-(10,16)`<br>`(10,6)-(16,11)` | 外壳侧面的 1px 描边<br>外壳端面的 1px 描边 |
| `heatpipe_rim` | `(0,0)-(7,7)`<br>`(0,7)-(7,9)`<br>`(0,9)-(7,16)` | 内端面（朝核心）<br>侧壁 7x2<br>外端面（朝外） |

规则：**每个面按 texel 对齐取上述单元格之一**（官方那套 uv 与 rotation 逐字保留，包括
`(0,7,7,0)` 这种 v 反写 = 镜像）。由此得到：

* texel 密度恒为 **1 px/单位**（外壳 6.15 宽对 6 texel 是唯一例外，0.976）→ 与
  `tools/check_assets.py [13]` 的口径一致，换模型不跳变；
* **核心 / 内管 / 收口环的所有面 100% 不透明**；外壳的面只采到"1px 描边"单元（其余 texel 是透明的，
  但背后 0.075 处正好是内管，透不到背景 —— `check_assets.py [16]` 单独确认过热管 0 问题）；
* 六档贴图的 alpha 布局完全相同（只换颜色），所以同一套 uv 对 6 档都成立。

## 5. 自检（`tools/selftest_heat_pipe_model.py`）

1238 条断言，全部是静态几何，不启动游戏：

| 项 | 断言 |
|---|---|
| `[HP1]` | 42 条 multipart = 6 档 × (1 core + 6 臂)；无重复 selector；档位与模型后缀对齐 |
| `[HP2]` | **64 个状态 × 6 档**逐一代入 multipart，把命中模型的**几何**换成臂集合，必须 == 状态里的连接方向（覆盖 0/1/2/3/4/5/6 连接） |
| `[HP3]` | 同一状态命中的元素签名不重复（同几何不叠画） |
| `[HP4]` | 六档几何逐字节相同，只有 `textures` 带档位后缀 |
| `[HP5]` | 一根臂三件套同侧同心：内管 5..11/11..16、外壳 ±0.075、法兰 4.5..11.5 且 14.05..16.05 |
| `[HP6]` | UV 不采透明区：core/内管/法兰 = 100% 不透明；外壳 = 只许 1px 描边（不透明 texel 只能在采样矩形最外圈）；uv 必须落在布局单元格里 |
| `[HP7]` | texel 密度 1.000 px/单位（±3%，外壳 0.976 属几何决定） |
| `[HP8]` | 生成物 == 生成器输出（两个生成器的 `--check`），目录里没有多余模型 |
| `[HP9]` | blockstate 的 `heat` 档位（含顺序）== `HeatPipeBlock.Heat` 枚举 |
| `[HP10]` | 与官方 `block_old.json` 逐件比对坐标/面集合/uv/rotation（找不到参考树时跳过） |

```bash
python tools/gen_heat_pipe_models.py        # 43 个模型：core + 6 臂（各 6 档）+ 物品 item_straight
python tools/gen_heat_pipe_blockstate.py    # 42 条 multipart
python tools/selftest_heat_pipe_model.py    # 断言
```

`gen_heat_pipe_models.py` 会删掉目录里不属于这套命名的旧模型（幂等），所以重跑不会留死资源。

## 6. 需实机确认

1. **单放**（0 连接）：只有一个核心方块，无臂无法兰。
2. **直通**（2 连接，对向）：两段内管 + 两端法兰对齐；两管相接处是"双法兰背靠背"，不应有缝隙/闪烁。
3. **四通**（`up+south+east+west`）：这是旧版缺两根臂的那 12 种状态之一，确认四根臂都在。
4. **五通/六通**：法兰是否互相穿插得难看（官方几何就是 0.05 探出 + 0.1 重叠，属原设计）。
5. **六档热度**：cool→super_hot 只换贴图，几何应完全不动；温度在阈值附近不应抖动（每 20 tick 检查、
   跨档才写 blockstate）。
6. 与普通导管 / `straight_duct`（现在用 `block/duct/straight`）相接时，法兰是否与对方接口对齐
   （普通导管是 10px 直径，热管是 6px，粗细差异属已记录观感项，见 `docs/GAS-DUCT-AUDIT.md`）。
