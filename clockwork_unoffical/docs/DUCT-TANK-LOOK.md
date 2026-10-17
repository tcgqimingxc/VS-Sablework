# 气体储罐（duct_tank）外观：按用户在图集上的手写标注重裁（2026-10-17 第三轮）

> **2026-10-17 第四轮（物品图标）**：下文"1×1×1 单块不变：`block_single` 直接读官方图集 (0,3) 格"
> 已不再描述**世界模型**（世界 `block_single` 改成了 Create 几何 + 16×16 基础贴图，供 CT 使用）。
> 官方图集 `(0,3)` 格现在只被**物品图标**使用：新增 `models/block/duct_tank/block_single_item.json`
> 逐字节复制官方 `block_single`，`models/item/duct_tank.json` parent 指向它。
> 原因与本轮修法见根目录 [`../../docs/GAS-TANK.md`](../../docs/GAS-TANK.md)。

> **结论（第三轮）**
>
> * **顶面** = `duct_tank_top_connected.png` 右中部那块**双层边框大方块**，精确矩形
>   **x 16..62 / y 0..46（47×47）**，横跨 4×4 格的 **col 1..3 × row 0..2（3×3 格）**，
>   右/下各留 1 px 边距。它是**整块顶盖**（外框 → 凹槽 → 内框 → 带 4 颗铆钉的平板），
>   不是单格。`duct_tank_top.png` = 这块 47×47 **面积平均缩到 16×16**。
> * **侧面** = `duct_tank_connected.png` 中左部那块**横向加强筋面板**；里面唯一
>   "无竖框 + 水平无缝"的 16×16 子区是 **x 32..47 / y 11..26**（v0 = 加强筋亮线，
>   v15 = 筋下暗影，正好是图集的**一个 16 px 周期**）。
> * **1×1×1 单块不变**：`block_single` 继续直接读官方图集 `(0,3)` 格（整块带框盖板），
>   本轮一个像素没动。
>
> 上一轮（第二轮）把侧壁/顶面都取成"四边都连接"的 `(2,1)` 格；用户实机看过后指出
> **顶面/侧面的选格与实际设计不符**，并在 64×64 图集上手写了"顶面/侧面"标注。
> 本轮按标注重裁：顶面从"纯色平板 + 4 颗铆钉"换成"整块双层边框盖板的缩略"，
> 侧面从 `(2,1)` 的**相位**（筋在 v=11）换成**周期起点**（筋在 v=0）。

相关文档：[DUCT-TANK-UV.md](DUCT-TANK-UV.md)（`texture_size` 是死字段、uv 永远是 0..16、
`check_assets.py [13]` 的密度约束）。

---

## 1. 用户标注区域的像素判读（本轮的核心依据）

判读图（16 倍放大 + 4/16 px 网格 + 像素坐标刻度）：
`_probe_tmp/annotated_regions.png`（红框 = 顶面，青框 = 侧面）、
`_probe_tmp/zoom_duct_tank_top_connected.png`、`_probe_tmp/zoom_duct_tank_connected.png`、
`_probe_tmp/cells18_*.png`（逐格 18 倍）、`_probe_tmp/luma_*.txt`（逐像素亮度表）。

### 1.1 顶面：双层边框大方块 = x 16..62 / y 0..46（47×47）

沿行/列扫"长直亮线"（`_probe_tmp/analyze_frames.py` 输出 `_probe_tmp/frames.txt`）：

| 边 | 亮线位置 | 亮度 | 说明 |
|---|---|---|---|
| 左 | x = 16（y 1..46） | 106.7 | 外框左边 |
| 右 | x = 62（y 0..45） | 93.5 | 外框右边 |
| 上 | y = 0（x 17..62） | 95.1 | 外框上边 |
| 下 | y = 46（x 14..61） | 92.0 | 外框下边 |

→ 外接矩形 **x 16..62, y 0..46 = 47×47**，正好是 **col 1..3 × row 0..2** 这 3×3 格
（48×48）内缩 1 px 的整块盖板。结构（由外到内）：

| 层 | 像素范围 | 亮度 | 内容 |
|---|---|---|---|
| 外框（3 px 凸边） | x16..18 / x60..62 · y0..2 / y44..46 | 90..107 | 盖板最外圈包边 |
| 凹槽（3~4 px） | x19..23 / x55..59 · y3..7 / y39..43 | 59..73 | 凹下去的过渡槽 |
| 内框（3 px 凸边） | x24..26 / x52..54 · y8..10 / y36..38 | 88..94 | 第二道凸框（"双层"） |
| 平板 + 铆钉 | x27..51 / y11..35 | 79..101 | 中间平板；4 颗 2×2 铆钉在 (34,18)(44,18)(34,28)(44,28) |

判读依据（为什么这 3×3 格就是"顶盖"）：Create 的 `AllCTTypes.RECTANGLE` 在
`getTextureIndex` 里把 4×4 图集映射为 `x = 2(left&right)/1(only right)/3(only left)/0(neither)`、
`y = 1(up&down)/2(only up)/0(only down)/3(neither)`。**3×3 罐的顶面** 9 个格刚好是
`col 1..3 × row 0..2`（中心 (2,1)、四边 (1,1)(3,1)(2,0)(2,2)、四角 (1,0)(3,0)(1,2)(3,2)），
拼起来就是这块大盖板 —— 图集作者正是按"3×3 连通"画的。**1×1 罐的顶面**（顶面上方/四周
都没有邻居）是 `(0,3)` 格 = 16×16 的官方小盖板，`block_single` 用的就是它。

### 1.2 侧面：横向加强筋面板 = x 32..47 / y 11..26

图集侧壁是**横向加强筋**纹理，纵向周期 16 px：

| 特征 | 行 | 亮度 | 说明 |
|---|---|---|---|
| 加强筋亮线 | y = 11 / 27 / 43 / 59 | 98.7 | 横贯 x26..53 的亮筋 |
| 筋下暗影 | y = 10 / 26 / 42 / 58 | 49.3 | 整行暗影（全宽 64 px） |
| 面板体 | y 12..25 等 | 69..88 | 板面（带铆钉/竖向拉丝） |

横向：列 0 有 **x0..3 与 x13..15 两道竖框**（那是"1 宽罐整格面板"，见 §2），
x≥56 的右边缘一路收暗（45..73）；中间 **x19..55 没有竖框**。
所以"横向加强筋面板"= 中间那块板面，而**唯一"无竖框 + 水平包裹边差 1.29"** 的
16×16 子区是 **x 32..47**（对全部 49×49 个 16×16 窗口做过扫描：
`_probe_tmp/scan_wall_windows.py` → `_probe_tmp/wall_scan.txt`，只有 x=31..34 这几列
满足 `wrapH ≤ 1.3 且 竖框强度≈0`）。纵向取 **y 11..26** —— 正好是 16 px 周期本身
（v0 = 筋，v15 = 筋下暗影，下一行 y=27 就是下一根筋）。

> 用户标注的"中左部"= 面板体 x≈18..50 / y≈12..37 这块大板；x32..47 / y11..26 落在其中，
> 是它"可无缝取用"的那一块。

---

## 2. 为什么不用"1 宽罐整格" `(0,1)`（如实记录的取舍）

用户标注的位置如果按"格"读，最接近的是 `(0,1)`（x0..16 / y16..32，中左格）。它确实是
一块**完整面板**（左右各有 2~3 px 竖框 + 中间加强筋 + 铆钉），语义上正是 CT 在
"左右都不连接、上下都连接"时选的格 —— 也就是**1 宽多层罐的中段**。但：

* 它**带左右竖框**，`wrapH` 仍只有 2.23（数值能过），铺到 3×3 罐上就是每 16 px 一道
  竖缝 —— 正是第二轮用户抱怨过的"一格一格拼起来"的观感；
* 本任务是"**若区域大于 16×16，取其中无边框、可平铺的 16×16 子区；优先保证相邻块能接缝**"。

所以按任务要求取**无竖框子区**。代价（如实记录）：1 宽罐的侧壁不再有"左右两条竖框"
（官方 1 宽罐有），换来的是 3×3 罐侧壁连续、块缝不可见。两者不可兼得：静态贴图不知道
罐子的实际宽度，而 `block_top/bottom/middle` 每个面都只映射一张 16×16。

---

## 3. 两张基础贴图的最终裁切

生成脚本：`tools/gen_duct_tank_textures.py`（纯标准库，不依赖 Pillow；`--check` 逐字节核对）。

### 3.1 `duct_tank.png`（侧面，16×16，212 字节）

```
来源：duct_tank_connected.png
矩形：x 32..47, y 11..26   （16×16，原样裁出，无缩放/无改色）
含义：横向加强筋面板的一个完整 16 px 周期：v0 = 亮筋，v1..9 = 板面，
      v10 = 暗线，v11..14 = 板面，v15 = 筋下暗影
自检：左右两列不能是竖框（与内部亮度差 ≤ 6）、第 0 行是亮筋（≥88）、
      第 15 行是暗影（≤62）—— 脚本 check_wall_box() 每次 --check 都验
```

模型的 uv 用法（决定 v=0 该是什么）：

| 模型 | 面 | uv | 贴到哪 |
|---|---|---|---|
| `block_top` | Lid 的 4 个侧面 | `[0,0,16,4]` | 顶盖 12..16 那 4 px 的**盖沿** → v0..3 正好是"亮筋 + 板面"，盖沿上沿有一条高光 |
| `block_top` | 四面墙 | `[0,4,16,16]` | 0..12 的墙 → v4..15（板面 + 暗线 + 暗影） |
| `block_middle` | 四面墙 | `[0,0,16,16]` | 整 16 px → 每层一根筋，上下层之间自动接成"筋 / 暗影" |
| `block_bottom` | 四面墙 | `[0,0,16,12]` | 4..16 的墙；底板外沿 v12..15 = 板面 + 暗影 |

### 3.2 `duct_tank_top.png`（顶面，16×16，494 字节）

```
来源：duct_tank_top_connected.png
矩形：x 16..0 ... 精确写为 x 16..62 / y 0..46（47×47 整块顶盖）
处理：面积平均缩到 16×16（整数权重，权重分母 47；不是最近邻，避免 1 px 框线被抽掉）
自检：四条外框线必须落在 crop 边界、框外一格必须不亮、内圈亮线必须在（y0+8）——
      脚本 check_lid_box() 每次 --check 都验
```

缩放后 16×16 的结构（亮度实测）：外框 1 px（91..96）→ 凹槽 1 px（59..75）→
内框/板面过渡（77..87）→ 平板（74..88）。**双层边框在 16 px 下还看得出层次**（外框亮、
凹槽暗、第二道过渡亮），只是内框那 1 px 对比度变低；4 颗 2×2 铆钉缩成 1 px 低对比点
（47→16 时 2 px 只剩 0.68 px，这是面积平均的必然损失，不做"重画铆钉"，以免引入
官方没有的像素）。

### 3.3 1×1 与多层分别怎么用（观感取舍）

| 场景 | 用哪张 | 观感 |
|---|---|---|
| **1×1×1** 罐 | `block_single` → 官方图集 `(0,3)` 格 | **整块带双层边框的盖板**（16×16 原生、锐利、4 颗铆钉），与上一轮完全一致 |
| **1×1×N**（N≥2）顶段 | `block_top` → `duct_tank_top.png` | 用户标注的那块大盖板的 16×16 缩略：外框 + 凹槽 + 内框 + 平板，**单看一块就是一块盖板**；盖沿（v0..3）还有一条高光 |
| **N×N**（N≥2）顶面 | 9 个 `block_top` → 3×3 平铺 | 每 16 px 一道 1 px 浅框 + 暗槽，读作**盖板拼缝/检修口**，不是上一轮那种"9 个粗方框"；见 `_probe_tmp/top_final.png` 第 1 行 3×3 |
| **竖直多层** 侧壁 | `block_middle` → `duct_tank.png` | 每层一根加强筋 + 筋下暗影，层与层之间是"暗影 / 亮筋"的自然过渡（图集自己的过渡） |
| **N×N** 侧壁 | 3×3 平铺 | 横筋连续、**没有竖缝**（左右包裹边差 1.29） |

为什么不做成"1×1 用整块、多段用内部平板"：`block_top` 的 `up` 面 uv 固定 `[0,0,16,16]`，
1×1×N 与 N×N 共用同一张图，模型本轮不可改。若取内部平板（上一轮做法），1×1×N 的顶面
就退化成"一块灰板 + 4 颗铆钉"—— 用户正是因此标注。**缩略整盖是"两种尺寸都还能看"
的唯一折中**。若将来允许改模型/加 CT，正确做法是：1×1 顶面用 `(0,3)`，N×N 顶面用
`col1..3 × row0..2` 的 9 格 CT 选格（即真正的官方行为）。

### 3.4 为什么产物必须是 16×16

`block_top/bottom/middle` 的 uv 是 0..16 满铺一个面；16 像素 / 16 模型单位 = 1.000 px/单位。
`block_single` 从 64×64 图集取一格（4 uv 单位 = 16 像素覆盖 16 单位）也是 1.000。
若做成 64×64 满铺会变成 4.000 px/单位，`check_assets.py [13]` 会立刻报出来。

---

## 4. 可平铺度量（`python tools/gen_duct_tank_textures.py --check` 实测）

| 产物 | 左右包裹边差 | 上下包裹边差 | 内部相邻差 | 判定 |
|---|---|---|---|---|
| `duct_tank.png` | **1.29**（内部 3.77） | **75.25** | 9.67 | 左右 <= 4.0 ✔；上下 == 图集自然续接 75.25，是**设计本身的"暗影→亮筋"过渡**，不是平铺伪影 ✔ |
| `duct_tank_top.png` | **3.31**（内部 6.85） | **3.15** | 6.95 | 都 <= 4.0 ✔（缩略盖板外框左右亮度差 3 左右） |

"上下包裹边差 75.25"怎么理解：侧壁 crop 是**一个完整周期**，图集在 y=26 之后
本来就是 y=27 的亮筋（`design_seam_v()` 用图集第 26→27 行的像素差算出同一个 75.25）。
脚本的判据因此是"包裹边差 ≤ 图集自然续接边差 + 1.0"，而不是拍一个固定 2.0 ——
否则会把"每 16 px 一根筋"这种官方设计误判成"不能平铺"。
（顶面没有这种周期，走固定阈值 4.0。）

自检脚本还会核对标注区域本身：盖板四边外框在 crop 边界上、框外一格不亮、内圈亮线存在；
侧壁 crop 左右不是竖框、v0 是亮筋、v15 是暗影。

---

## 5. 采用的官方文件（逐字节，未改一个字节）

| 类别 | 官方路径 | 工程路径 | 说明 |
|---|---|---|---|
| 模型 | `models/block/duct_tank/block_single.json` | 同路径 | 单块：16³ 整方块，贴图取 64×64 图集 `(0,3)` 格 |
| 模型 | `models/block/duct_tank/block_top.json` | 同路径 | 顶段：4 px 顶盖 + 12 px 四面墙（墙厚 1 px） |
| 模型 | `models/block/duct_tank/block_bottom.json` | 同路径 | 底段：4 px 底板 + 12 px 四面墙（上下镜像） |
| 模型 | `models/block/duct_tank/block_middle.json` | 同路径 | 中段：只有四面 1 px 墙 |
| blockstate | `blockstates/duct_tank.json` | 同路径 | 官方 4 变体，键只含 `bottom`/`top` |
| 物品模型 | `models/item/duct_tank.json` | 同路径 | `parent: vs_clockwork:block/duct_tank/block_single` |

> `models/block/duct_tank/placeholder.json` 在第三轮期间**已不存在**（`tools/cleanup_assets.py`
> 把它列为"blockstate 四个变体都不引用、无 Java 引用"的清理项）。它不影响渲染，也不影响
> `check_assets.py [1]/[4]`（两者都是 0）；但 `tools/gen_duct_tank_assets.py --check`
> 仍在按"11 个产物"核对，所以它会报这一条缺失 —— 与本轮贴图改动无关，属于并行的资源清理。

模型里的贴图引用全部是 `vs_clockwork:block/...`：

* `duct_tank_connected.png`（64×64 图集）—— `block_single` 的四面；
* `duct_tank_top_connected.png`（64×64 图集）—— `block_single` 的顶/底；
* `duct_tank.png`（16×16，多段用）—— `block_top/bottom/middle` 的侧壁与盖沿；
* `duct_tank_top.png`（16×16，多段用）—— `block_top/bottom` 的顶/底外表面；
* `fluid_tank_inner.png`（16×16，官方树本来就有）—— 顶/底盖的**内**表面。

`models/block/duct_tank/*_window.json`（第一轮从 Create 复制的观察窗模型）仍然删除状态——
官方没有观察窗概念。

---

## 6. 复现与自动核对

```powershell
python tools/gen_duct_tank_textures.py            # 从官方图集派生两张 16×16
python tools/gen_duct_tank_textures.py --check    # 逐字节 + 标注区域 + 可平铺 + 模型引用
python tools/gen_duct_tank_assets.py              # 还原 5 个官方模型 + blockstate + 物品模型
python tools/gen_duct_tank_assets.py --check      # 11 个产物逐字节核对
python _probe_tmp/final_probe.py                  # 重新生成标注框/最终贴图/实机近似图
```

脚本里写死的参数（与本文一致）：

```python
WALL_BOX  = (32, 11)          # 侧面 16×16 左上角（x32..47, y11..26）
LID_BOX   = (16, 0, 47, 47)   # 顶面整块盖板（x16..62, y0..46）
SINGLE_CELL = (0, 3)          # block_single 的格（仅用于断言"没被改"）
FRAME_LUMA, SHADOW_LUMA = 88, 62   # 标注区域自检阈值
TILE_EPS_H, TILE_EPS_V = 4.0, 4.0  # 包裹边差阈值（侧壁上下另有"自然续接"判据）
```

---

## 7. 模型族：单块用图集格、多段用派生贴图

| 模型 | 贴图 | 尺寸 | 典型 uv | 密度 | 外观 |
|---|---|---|---|---|---|
| `block_single` | `duct_tank_connected` / `duct_tank_top_connected` | 64×64 | `[0,12,4,16]`（`(0,3)` 一格） | 1.000 | 1×1 单块：带框完整面板 + 双层边框盖板 |
| `block_top` | `duct_tank` / `duct_tank_top` / `fluid_tank_inner` | 16×16 ×3 | 盖沿 `[0,0,16,4]`、墙 `[0,4,16,16]` | 1.000 | 12 px 连续壁 + 4 px 盖沿 + 缩略整盖 |
| `block_bottom` | 同上 | 16×16 ×3 | 盖沿 `[0,12,16,16]`、墙 `[0,0,16,12]` | 1.000 | 上下镜像 |
| `block_middle` | `duct_tank` | 16×16 | `[0,0,16,16]` | 1.000 | 四面连续壁（每层一根筋） |

* **单块为什么用图集**：官方 CT 层会把 `duct_tank` 位移到 `_connected` 图集，再由
  `DuctTankCTBehaviour` 逐面选格；`block_single` 的 uv 是**静态写死的一格** `(0,3)`，
  所以在本移植里直接工作（不需要 CT），1×1 罐的外观就是官方设计。
* **多段为什么用派生贴图**：多段模型的 uv 是"满铺一张 16×16"，没有 CT 逐面选格；
  必须把"3×3 连通时该用的那一块"固化成独立贴图。
* **代价（如实记录）**：静态贴图不知道罐子的实际宽度。1 宽罐侧壁没有左右竖框；
  N×N 罐顶面是 N×N 个盖板缩略（拼缝），而不是官方那一整块大盖板；1×1×N 的顶段
  没有 `block_single` 那种独立凸起的舱盖（几何上盖板仍是 4 px 厚的一层）。

---

## 8. blockstate 与物品模型

### 8.1 官方 4 变体（原样照抄）

```json
"bottom=false,top=true"  -> block_top
"bottom=true,top=false"  -> block_bottom
"bottom=false,top=false" -> block_middle
"bottom=true,top=true"   -> block_single
```

代码里注册了 `top` / `bottom` / `large` 三个布尔属性，但官方 blockstate 的键**不写 `large`** ——
在 1.21.1 的 `Selector`/`KeyValueCondition` 语义里，没写的属性就是**部分匹配**：
`large=true/false` 都会落到同一个变体，8 个状态仍然**各命中一个**，不会 missing model。
`check_assets.py [12]` 会逐状态验证这一点。

`large` 属性仍由 `DuctTankBlockEntity` 正常维护（宽度 > 1 时置 true），只是**不影响外观**。

### 8.2 第一轮的 8 变体 / 观察窗映射：已废弃

| 第一轮 | 现状 |
|---|---|
| 8 变体（`bottom`/`large`/`top`） | 官方 4 变体（`bottom`/`top`，`large` 部分匹配） |
| `block_*_window` 4 个 Create 模型 | 已删除 |
| 物品模型 `parent: .../block_single_window` | 官方 `parent: .../block_single` |
| 贴图 `create:block/fluid_tank*` | 全部 `vs_clockwork:block/duct_tank*` / `fluid_tank_inner` |

---

## 9. 内容物 BER：保留，参数与官方内腔一致

`content/logistics/gas/tank/client/DuctTankRenderer.java` **原样保留**（画气面、颜色按气体、
压力决定不透明度、只在主方块画一次、渲染包围盒覆盖整罐）。它的几何常数与官方模型的空腔
完全一致，无需改动：

| BER 常数 | 值 | 官方模型里的来源 |
|---|---|---|
| `WALL`（墙厚） | 1/16 | `block_top/bottom/middle` 四面墙都是 `0..1` / `15..16` |
| `LID`（盖厚） | 0.25 | `block_bottom` 的 Lid `0..4`、`block_top` 的 Lid `12..16` |
| `LID_GAP`（满罐留 1 像素） | 1/16 | 与 Create 的 `height - 0.3125` 同口径 |
| `EDGE`（0.125 像素避让） | 1/128 | 避免与盖/壁内面共面闪烁 |

官方外壳是封闭的，所以正常视角下气面被不透明罐壁挡住、看不见；BER 仍然在画，不会造成
黑紫或渲染错误。本轮没有改 `content/**` 的任何 Java。

---

## 10. 验收（2026-10-17 第三轮）

环境：`JAVA_HOME=C:\Users\yql11\java\dragonwell-21.0.8.0.8+9-GA`，`--offline`。

| 检查 | 结果 |
|---|---|
| `.\gradlew.bat --offline build` | **BUILD SUCCESSFUL**（14s）。本轮构建后并行任务又在 20:34:40 重建过一次，最终产物 `build/libs/vs_clockwork-0.6.0-1.21.1.jar` = **8 164 415 字节**、SHA256 `738FA65B387A94662DC4151C428D14279A4687E2E109717A5093BF50B17C569C`；本轮两张贴图（212 / 494 字节）在 jar 内与源码树**逐字节一致** |
| `python tools/gen_duct_tank_textures.py --check` | 通过：2 张 16×16 逐字节一致；标注区域自检通过；包裹边差 1.29/75.25（= 自然续接）、3.31/3.15；单块仍 `(0,3)`；引用无 `create:` |
| `python tools/check_data_refs.py` | **合计问题 0 个** |
| `python tools/check_assets.py --jar` | **合计问题 0 个**（`[12]` 快照先因并行 Java 改动过期，按提示重跑 `python tools/probe_blockstates.py`（ok=68 fail=0，指纹 `1d91412f…`）后归 0；`[13]` 只剩 valve_duct 的 2 条既有提示，**duct_tank 族未新增**；`[11]` 产物 jar ↔ 源码树 0 问题） |
| `python tools/check_jar.py` | **合计问题 0 个**（1638 个条目，zip 结构 0 问题） |
| `python tools/check_render_coverage.py` | **合计问题 0 个**（RC1..RC7 全 0，提示 0 条） |
| `python tools/check_ponder.py` | **合计问题 0 个**（仅 1 条 [lang] 提示，与本轮无关） |

产物 jar 内实测：

* `textures/block/duct_tank.png` = **212 字节**、16×16 RGBA；`duct_tank_top.png` = **494 字节**、
  16×16 RGBA；两者与源码树逐字节相同；
* `block_top/bottom/middle` 的 `textures` 分别指向 `vs_clockwork:block/duct_tank`、
  `vs_clockwork:block/duct_tank_top`、`vs_clockwork:block/fluid_tank_inner`；
* 4 个模型内 **0 处 `create:`**。

---

## 11. 实机确认点

1. **1×1×1 单块**：四面仍是横向加强筋的完整面板（图集 `(0,3)` 格），顶面仍是双层边框 +
   四角铆钉的方形舱口 —— 与上一轮/官方一致，本轮没动。
2. **1×1×N（N≥2）**：顶段顶面现在是**带双层边框的盖板缩略**（不再是灰板 + 4 点），
   盖沿上沿还有一条高光；这是本轮顶面的主要变化，重点看这个。
3. **3×3×3 大罐（重点）**：侧壁横筋连续、**块与块之间没有竖缝**（`wrapH=1.29`）；
   顶面是 3×3 个盖板缩略、每 16 px 一道 1 px 浅框 —— 请判断这个"拼缝"观感是否可接受
   （若不能接受，可改回"内部平板"方案：把 `LID_BOX` 换成内平板 + 4 颗铆钉，
   见 §3.3 的说明）。
4. **竖直多层**：每层一根加强筋 + 筋下暗影；层缝处是"暗影 / 亮筋"过渡，不应对不齐。
5. **外圈边缘**：大罐四周/顶面外沿没有逐块的凸框（静态贴图的必然结果）。
6. **无黑紫 / 无 Z-fighting**：段缝、顶底盖与墙的交界不应闪烁。
7. **罐内气面**：正常视角看不到（官方封闭外壳）；若能看到气面/漏光，记录角度与坐标。
8. 破坏方块时的粒子用 `particle` 贴图（`duct_tank` 16×16 连续壁面）。

对照图：

* `_probe_tmp/annotated_regions.png` —— 标注矩形叠在两张图集上（红 = 顶面，青 = 侧面）；
* `_probe_tmp/final_16x.png` / `_probe_tmp/final_40x.png` —— 两张产物的 22x/40x + 3×3 + 1×1×3；
* `_probe_tmp/top_final.png` / `_probe_tmp/side_final3.png` / `_probe_tmp/top_downscale.png`
  —— 候选方案对比（含本轮落选方案，便于回头改）；
* `_probe_tmp/tank_mock.png` —— 3×3×3 与 1×1×1 的近似实机并排。

---

## 12. 装包状态（2026-10-17）

* 源 jar：`clockwork_unoffical/build/libs/vs_clockwork-0.6.0-1.21.1.jar`，**8 164 415 字节**
  （SHA256 `738FA65B…17C569C`；20:15 本轮构建时是 8 159 090 字节，20:34:40 并行任务重建后为现值，
  两张贴图在 jar 内与源码树逐字节一致）。
* 装包前置：`javaw` 进程 + `...\1.21.1-NeoForge_21.1.251\logs\latest.log` 的写入时间。
  本轮开始时游戏在跑（javaw 10344/3756，latest.log 1.8 分钟前才写过），因此**先等游戏退出**
  （无 javaw 且 latest.log > 2 分钟未写）再执行 `tools/install_jar.ps1`；脚本本身也做这两项
  前置检查，不满足就 `exit 2` 不动 jar。
* 落盘结果（2026-10-17 20:35:29，`tools/install_jar.ps1` exit 0，三个目标都通过 SHA256 校验，
  均为 8 164 415 字节 / `738FA65B…17C569C`）：
  1. `C:\Users\yql11\OneDrive\Desktop\.minecraft\versions\1.21.1-NeoForge_21.1.251\mods\`
  2. `C:\Users\yql11\AppData\Roaming\PrismLauncher\instances\CSN_The Song Of The Sky\minecraft\mods\`
  3. `C:\Users\yql11\OneDrive\Desktop\.minecraft\versions\CSN_The Song Of The Sky\mods\`
* 等待过程：本轮期间游戏被启动过两次（javaw 10344/3756 → 2756/9832 → 2756/4908），
  脚本一直等到"无 javaw 且 latest.log > 2 分钟未写"（实测 2.2 分钟）才动手；
  `_probe_tmp/wait_and_install.ps1` 就是这段等待 + 安装 + 三处校验的脚本。
