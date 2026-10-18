# 热管（`heat_pipe`）连接模型：结构 / 几何 / UV / 自检

> 2026-10-18 第二次返工。对应实机反馈"单独放置的热管是一个满方块带十字/凹陷花纹"。
> 生成器：`tools/gen_heat_pipe_models.py`、`tools/gen_heat_pipe_blockstate.py`
> 断言：`tools/selftest_heat_pipe_model.py`（已挂进 `tools/run_gates.ps1 -SelfTest`）
> **连接判定与传热的语义见 `docs/HEAT-PIPE-CONNECTION.md`**（本文只谈几何/UV）。

## 0. 一句话结构

`blockstates/heat_pipe.json` = 6 档热度 × **13 条 multipart**（1 core + 6 臂 + 6 端环）= **78 条**；
模型目录 = **79 个** JSON（core / arm / cap 各 6 档 + 物品 `item_straight`）。

* 连接面 → `core` + `arm_<dir>`（内管 + 外壳 + 边界收口环）；
* 未连接面 → `core` + `cap_<dir>`（把同一收口环平移到 core 面外侧的**端环**）。

单放（0 连接）时命中 `core` + 六个 `cap_*`，不再只剩一个 6x6x6 的 core；四通/五通/六通也
逐面命中正确的臂/端环组合（`selftest_heat_pipe_model.py` 的 [HP2]/[HP12] 逐状态断言）。

## 1. 旧版到底错在哪（证据）

| # | 表现（实机） | 根因 |
|---|---|---|
| 1 | **单独放置是一个满方块 + 十字/凹陷花纹** | 旧 blockstate 只有 `core`（5..11 的 6x6x6 立方体，六个面都贴 `heatpipe_core`）。没有连接时没有任何臂/端环，看到的就是这个立方体的正面；`heatpipe_core` 正好是"带十字/凹陷的板" |
| 2 | 4 通管路缺两根臂 | 官方 30 条平面 multipart 每条只约束一个平面的 4 个属性，另外两个 = 任意值。64 个连接状态里有 **12 个**（4 通、缺的两臂互相垂直）只命中一个 2 臂模型 |
| 3 | 核心/臂同几何叠画（z-fighting、双倍填充率） | 同一设计副作用：64 个状态**全部**命中 2~3 个模型（每模型自带核心与臂） |
| 4 | 接缝处 z-fighting | 每个盒子都画 6 个面：管口端面与邻居的管口端面共面，靠核心一端与核心面共面 |
| 5 | 收口环偏厚、法兰比官方大一圈 | 手写常量 `RIM_IN/OUT=13.9/16.15`、截面 4.4~11.6；官方 Blockbench 是 `14.05~16.05`、`4.5~11.5` |

本次把 #1 作为首要回归项：**没有连接的面必须有端环**，否则 core 的六个面会裸露。

## 2. 方向约定（唯一权威）

`HeatPipeBlock` 的六个连接属性来自原版 `PipeBlock.PROPERTY_BY_DIRECTION`
（`north/east/south/west/up/down`），直接表示"这一面有邻居/有热接触"。blockstate 的 `when`
也只写这一件事：

```json
{"apply": {"model": ".../heat_pipe/core"},        "when": {"heat": "cool"}}
{"apply": {"model": ".../heat_pipe/arm_north"},   "when": {"heat": "cool", "north": "true"}}
{"apply": {"model": ".../heat_pipe/cap_north"},   "when": {"heat": "cool", "north": "false"}}
... 6 个方向 × (arm/cap) × 6 档 ...
```

* 一个状态 = `core` + 每个 true 方向一根**单臂** + 每个 false 方向一个**端环**；
* 臂的方向 = 属性为 true 的方向，不存在"平面/左右上下"这种二次映射（旧 `lu_x` 命名那套就是死在这里）；
* 64（6 位连接）× 6（热度）= **384 个状态**全部由同一套规则拼出，不存在"某些组合没模型"；
* 用 multipart 而不是 variants：384 条 variants 既冗长又容易重复整套模型列表；multipart 只有 78 条，
  且 `check_assets.py [12]` 能机械核对属性覆盖率。

## 3. 几何：官方 Blockbench + 端环平移

来源：`Clockwork-1.20.1-main/common/src/main/resources/assets/vs_clockwork/models/block/duct/block_old.json`
（"Made with Blockbench"，和 `heatpipe_core/connection/rim` 三张贴图同一批；`groups` 按方向分成
`core` / `connection.north..down` / `rim.north..down`）。每个方向 3 件（臂）：

| 件 | 尺寸 | 说明 |
|---|---|---|
| `inner` | 6.00 × 6.00 × 5 | 内管，从核心面（5/11）伸到方块边界（0/16） |
| `shell` | 6.15 × 6.15 × 5 | 外壳，只比内管大 0.075；UV 取"1px 描边"单元 → 管口一圈暗边 |
| `rim` | 7.00 × 7.00 × 2 | 边界收口环，压在边界上（沿轴 -0.05~1.95，故意探出 0.05，避免与邻居法兰共面 z-fight） |

**内管/外壳只给 4 个侧面**：两端的端面必然与别的东西共面（靠核心那头贴 core 的 6x6 面、
靠边界那头贴收口环），画出来就是同平面两份几何。官方模型也是这么做的。

**端环 `cap_<dir>`**：把同一方向的 `rim` 件沿轴向平移到 core 面外侧——

| 方向 | 边界 rim（原） | 端环（新） |
|---|---|---|
| north / west / down（负向） | -0.05 ~ 1.95 | 2.95 ~ 4.95 |
| south / east / up（正向） | 14.05 ~ 16.05 | 11.05 ~ 13.05 |

也就是厚 2px、离 core 面 0.05px，其余坐标/面/uv/rotation 逐字保留。这样：

* core 的六个面要么被臂的内管口盖住（连接面），要么被端环的 7x7 面盖住（未连接面），
  不会再露出"十字/凹陷"的 `heatpipe_core` 板；
* 端环与臂的收口环都不与 core 共面（0.05 间隙/探出），不会 z-fighting；
* 端环只在 `<dir> = false` 时出现，与臂互斥，不会重叠。

**取舍：为什么端环是实心法兰而不是"空心环"**：`heatpipe_rim.png` 的 (0,0)-(7,7)、
(0,9)-(7,16) 两个端面单元格是**全不透明**的（alpha 逐 texel 核对，见
`selftest_heat_pipe_model.py` 的贴图解码），所以收口环本来就是一块 7x7x2 的法兰盘，不是几何环。
热管是密封的导热件，端面封住是合理外观；如果以后要做"看得见管内"的空心端口，需要新增
4 根边框几何或改贴图 alpha，不在本次范围。

**取舍：为什么管-管相接是双法兰**：本方案不区分"邻居是不是热管"（blockstate 条件只能读自己的状态，
读不到邻居类型）。两根热管相接时会在接缝两侧各画一个收口环，形成 4px 厚的双法兰/联轴器。
Create 的 `shouldDrawRim` 会在邻居是管道时不画，但那是 `FluidPipeBlock` 在 BER/模型数据里按邻居
动态决定的；本工程为了"烘焙模型、零 BER"接受这个 0.05 重叠的双法兰（官方几何本来就是
"探出 0.05 + 0.1 重叠"，见 §1 #5 的官方数值）。

## 4. UV 规则（为什么这样取景）

三张贴图都是 16×16 的手绘展开图，**整张并不都有效**（alpha 只有几块）。有效单元格：

| 贴图 | 单元格 | 用途 |
|---|---|---|
| `heatpipe_core` | `(0,0)-(6,6)` | 核心 6 个面 |
| `heatpipe_connection` | `(0,10)-(5,16)`<br>`(10,11)-(16,16)` | 内管侧面 5x6（含镜像）<br>内管端面 6x5 |
| | `(5,10)-(10,16)`<br>`(10,6)-(16,11)` | 外壳侧面的 1px 描边<br>外壳端面的 1px 描边 |
| `heatpipe_rim` | `(0,0)-(7,7)`<br>`(0,7)-(7,9)`<br>`(0,9)-(7,16)` | 内端面（朝核心）<br>侧壁 7x2×2<br>外端面（朝外） |

规则：**每个面按 texel 对齐取上述单元格之一**（官方那套 uv 与 rotation 逐字保留，包括
`(0,7,7,0)` 这种 v 反写 = 镜像）。由此得到：

* texel 密度恒为 **1 px/单位**（外壳 6.15 宽对 6 texel 是唯一例外，0.976）→ 与
  `tools/check_assets.py [13]` 的口径一致，换模型不跳变；
* **核心 / 内管 / 收口环 / 端环的所有面 100% 不透明**；外壳的面只采到"1px 描边"单元
  （其余 texel 透明，但背后 0.075 处正好是内管，透不到背景）；
* 六档贴图的 alpha 布局完全相同（只换颜色），所以同一套 uv 对 6 档都成立。

## 5. 渲染方案：为什么是烘焙 multipart，不是 BER/PipeRenderer

**证据**：Create 1.21.1（6.0.10）的 `jar tf` 里没有 `PipeRenderer`/`PipeModel`/
`AllPartialModels.PIPE_*`；`javap FluidPipeBlock` 也没有 BER/`renderer`，普通管道完全由
`assets/create/blockstates/fluid_pipe.json`（30 条 multipart）+ 烘焙模型画。
`PipeAttachmentModel` 只给"被外壳包住/带支架"的管道用，不是普通管道。

**本工程的选择**：同样用烘焙 multipart（`core`/`arm`/`cap` 三者都是普通 block model），
不注册 BER、不需要 `PartialModel`、不需要在 `CWClientSetup` 提前 init。原因：

1. 端环/臂的形状完全由六个布尔决定，没有"按邻居类型动态画"的需求（双法兰是已接受的取舍）；
2. 384 个状态换成模型只发生一次，运行时零绘制调用；BER 每根热管每帧要画 6~12 个部件，
   热管成链/成排时开销明显；
3. 烘焙模型对 Flywheel/Sodium/光影最友好，也不会出现"BER 没注册 → 方块在、部件不画"的静默故障。

**如果以后要动态 `shouldDrawRim`（管-管不画法兰）或更多动态件**：可以照
`content/logistics/gas/duct/client/DuctRenderer` 的做法改成"base 模型 + BER"：把
`arm`/`cap` 做成 `PartialModel`（每档热度 2 个，共 12 个），在 `CWClientSetup` 里 `init()`，
用 `CachedBuffers.partialFacing(...)` 按面画。代价是 12 个 partial、一个渲染器类、
`check_render_coverage.py` 的新登记项，以及每帧绘制开销；本次按"结构简单、易维护"选了前者。
`DuctRenderer` 存在的原因是它的边类型（单向/过滤/智能）存在方块实体 NBT 里、blockstate
表达不了；热管的连接位就在 blockstate 里，不需要走那条路。

## 6. 自检（`tools/selftest_heat_pipe_model.py`）

2433 条断言，全部是静态几何，不启动游戏：

| 项 | 断言 |
|---|---|
| `[HP1]` | 78 条 multipart = 6 档 × (1 core + 6 臂 + 6 端环)；无重复 selector；档位与模型后缀对齐；每个方向恰好一条 true（arm）和一条 false（cap） |
| `[HP2]` | **64 个状态 × 6 档**逐一代入 multipart，把命中模型的**几何**换成臂集合/端环集合，必须 == 状态里的 true / false 方向（覆盖 0/1/2/3/4/5/6 连接） |
| `[HP3]` | 同一状态命中的元素签名不重复（同几何不叠画） |
| `[HP4]` | 六档几何逐字节相同，只有 `textures` 带档位后缀 |
| `[HP5]` | 一根臂三件套同侧同心；端环 7x7x2、离 core 面 0.05（轴向 2.95~4.95 / 11.05~13.05） |
| `[HP6]` | UV 不采透明区：core/内管/收口环/端环 = 100% 不透明；外壳 = 只许 1px 描边；uv 必须落在布局单元格里 |
| `[HP7]` | texel 密度 1.000 px/单位（±3%，外壳 0.976 属几何决定） |
| `[HP8]` | 生成物 == 生成器输出（两个生成器的 `--check`），目录里没有多余模型 |
| `[HP9]` | blockstate 的 `heat` 档位（含顺序）== `HeatPipeBlock.Heat` 枚举 |
| `[HP10]` | 与官方 `block_old.json` 逐件比对臂三件套；端环 == 官方 rim 平移（坐标/面/uv/rotation） |
| `[HP11]` | Java 架构：`extends PipeBlock`、`super(3 / 16f, ...)`、`PROPERTY_BY_DIRECTION`、getConnection/updateShape/updateConnections、无 `SHAPE_CACHE`、无 BER/partial |
| `[HP12]` | 不允许"满方块"退化：没有 0..16 的整块元素；core 是 5..11；单放（0 连接）必须命中 core + 6 个端环；每个状态六个面各恰好一条几何 |

```bash
python tools/gen_heat_pipe_models.py        # 79 个模型：core + 6 臂 + 6 端环（各 6 档）+ item_straight
python tools/gen_heat_pipe_blockstate.py    # 78 条 multipart
python tools/selftest_heat_pipe_model.py    # 断言
```

`gen_heat_pipe_models.py` 会删掉目录里不属于这套命名的旧模型（幂等），所以重跑不会留死资源。
`straight_duct` 从 2026-10-18 起用 `block/duct/straight`，本目录不再生成方块版 `straight`；
`models/item/heat_pipe.json` 仍 parent 到 `heat_pipe/item_straight`（core + 上下两臂）。

## 7. 需实机确认

1. **单放**（0 连接）：六个端环 + 6x6x6 core，不再是满方块/十字花纹；六个端环都应朝向各自的面。
2. **直通**（2 连接，对向）：两根管臂 + 两端边界收口环，另外四个面是端环；两管相接处是
   "双法兰背靠背"，不应有缝隙/闪烁。
3. **四通**（`up+south+east+west`）：这是旧版缺两根臂的 12 种状态之一，确认四根臂都在，
   未连接的两个面是端环。
4. **五通/六通**：法兰是否互相穿插得难看（官方几何就是 0.05 探出 + 0.1 重叠，属原设计）。
5. **六档热度**：cool→super_hot 只换贴图，几何应完全不动；温度在阈值附近不应抖动（每 20 tick 检查、
   跨档才写 blockstate）。
6. **与普通导管 / `straight_duct` 相接**：热管是 6px、普通导管是 10px，粗细差异属已记录观感项
   （见 `docs/GAS-DUCT-AUDIT.md`）；热管的边界收口环应贴在对方接口上，不应留 3px 空隙。
7. **端环与臂的接缝**：特别注意 4 通时两根相邻臂的收口环与另外两个端环在角部是否互相穿插
   （端环在 2.95~4.95、臂在 0~5，理论上只在 0.025 的角部有极小重叠；实机若看到闪烁再微调 0.05 间隙）。
