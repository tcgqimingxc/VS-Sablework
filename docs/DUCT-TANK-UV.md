# duct_tank 多方块储罐：UV 缩放一致性修复（2026-10-16）

> **后续（2026-10-17）**：外观已整体还原为**官方 Clockwork 模型**（Create 6.0.10 版已废弃），
> 缺失的 `duct_tank.png`/`duct_tank_top.png` 改由官方 64×64 CT 图集的 (0,3) 格裁成 **16×16**，
> 不再使用 64×64 噪声图。本文保留下来的有效结论全部成立：`texture_size` 是死字段、
> uv 永远是 0..16 归一化坐标、64×64 图集里 `[0,12,4,16]` = 一格 16×16 面板、
> 以及 `tools/check_assets.py [13]` 这条防回归提示项。当前取舍与实测见
> [DUCT-TANK-LOOK.md](DUCT-TANK-LOOK.md)。
>
> **再后续（2026-10-17 20:40）：CT 层已按 Create 原版恢复** —— 不再"静态取一格"，
> 取格改由 `DuctTankModel` + `DuctTankCTBehaviour` 在烘焙后完成，`block_single.json`
> 也不再引用 64×64 图集。**本文第 29-30 行、第 69-74 行、第 88 行关于 `block_single`
> "从 64x64 图集取一格"的表述已作废**，最新分工见文末「CT 恢复」一节。


## 症状

气体储罐由 1 格变为多层（或拆回 1 格）时会换模型，**贴图颗粒度肉眼可见地跳变**
（用户反馈"气体储罐多方块结构纹理异常"）。

## 先核实：`texture_size` 是**无效字段**

接手时的结论是"`block_single.json` 声明了 `texture_size: [64,64]`，其余模型没声明，
所以 uv 被当成整张贴图 → 差 16 倍"。核实后**机制不成立**：

* `client-1.21.1-*-extra.jar` **全部 class 的常量池里没有 `texture_size` 字符串**；
  正对照 `cullface`/`ambientocclusion`/`gui_light`/`tintindex` 都在（`cullface` 5 个类、
  `ambientocclusion` 3 个类）。Create 的 class 里也没有。
  ⇒ Java 版模型格式里 **uv 永远是 0..16 归一化坐标**，`texture_size` 只是 Blockbench
  写出的导出元数据，游戏根本不读。
* 全仓库 1164 个声明了 `texture_size` 的模型里，99% 的 uv 仍然落在 0..16（`[24,16]`/`[40,40]`/
  `[128,128]` 的贴图也一样）—— 如果它按 `uv/texture_size` 生效，这些模型会全部只显示贴图一角。

所以 `block_single` 的 `uv [0,12,4,16]` 的真实含义是：**64x64 图集里 (u 0..16px, v 48..64px)
的那一格 16x16 面板**，不是"4x4 像素"。

## 各模型实际数值（修复前）

| 模型 | texture_size | uv 跨度 | 贴图（实测像素） | 有效密度 |
|---|---|---|---|---|
| block_single | `[64,64]` | 4x4（=16x16px） | duct_tank_connected / _top_connected **64x64** | 1.00 px/单位 |
| block_bottom | 无 | 16x16 / 16x12 / 16x4 | duct_tank / duct_tank_top **64x64**，fluid_tank_inner 16x16 | **4.00** px/单位 |
| block_top | 无 | 16x4 / 16x12 / 16x16 | 同上 | **4.00** px/单位 |
| block_middle | 无 | 16x16 | duct_tank **64x64** | **4.00** px/单位 |

⇒ 单块与多层差了 **4 倍**（不是 16 倍），换模型时颗粒度突变。

## 根因

`*_connected.png` 是 Create 风格的 **CT 图集**：64x64 = 4x4 个 16x16 完整面贴图
（每格是一张完整面板，格子之间有明显接缝；`Clockwork-1.20.1-main` 里
`DuctTankCTBehaviour` + `DuctTankModel` 就是靠 Create 的 CT 位移在这些格子里选一格，
**但上游的 CT 注册是注释掉的**，所以 `block_single` 的那一格是静态取用的）。

这四张贴图/模型本来是 **Create 流体储罐的逐字复制**：Create 的
`fluid_tank.png` / `fluid_tank_top.png` 是 **16x16**，模型 uv 0..16 = 整张贴图 = 1 px/单位。
而本仓库的 `duct_tank.png` / `duct_tank_top.png` 是**缺失后由 `tools/gen_duct_tank_textures.py`
现生成的 64x64**（脚本注释写着"同尺寸（64x64）"，是为了跟 `_connected` 一个色系/尺寸），
于是这三个模型把整张 64x64 铺满一个面 → 4 px/单位。

上游 1.20.1 的这四个模型与本仓库**逐字节相同**（`Compare-Object` 无差异），也就是说
上游自己也缺这两张 png（`Clockwork-1.20.1-main` 里根本没有 `duct_tank.png`/`duct_tank_top.png`），
多层罐体在上游是黑紫；本仓库先补了贴图，但补错了尺寸。

## 取舍与改法

两条路：

1. **改模型**：把绑定在 **64x64 贴图**上的面 uv 按 1/4 缩放，
   即"每个面只取图集里的一格 16x16"；绑定 16x16 的 `fluid_tank_inner` 的面保持 0..16 不动。
   比例不变、无拉伸，只是把整张 64x64 换成它左上角的 16x16 区域。
2. **改贴图**：把 `duct_tank.png`/`duct_tank_top.png` 重新生成为 16x16，模型一个字都不用改。

**2026-10-17 的最终结论：走 2，而且不需要给 uv 做任何 1/4 缩放。**
现在用的是**官方模型**：`block_single` 本来就是"64x64 图集取一格"（1 px/单位），
`block_top/bottom/middle` 本来就是"16x16 满铺"（1 px/单位）；只要把缺失的两张图
按官方图集 (0,3) 格裁成 16x16，两条路同时满足、不存在冲突。
（10-16 晚那轮曾把模型换成 Create 的 16x16 贴图来"绕开"缺图；10-17 已整体退回官方模型，
详见 [DUCT-TANK-LOOK.md](DUCT-TANK-LOOK.md)。）

> 下面"选 1 的理由 / 修复后"是 10-16 那轮的历史记录，保留以便追溯；
> 当前实际做法是"官方模型 + 16x16 裁切贴图"（全族同样 1.000 px/单位）。


选 1 的理由：任务范围限定在 `models/block/duct_tank/**`，而且与"贴图由并行任务维护"这一不确定性
解耦 —— 改完模型后，无论这两张 png 是 64x64 还是被重新生成，**只要还是 64x64 就自洽**；
若将来真的把贴图改成 16x16，`tools/check_assets.py [13]` 会立刻报出来（见下），不会静默错。
取舍写在这里而不是模型文件里：模型 JSON 不允许注释。

## 修复后（当前验收数据，2026-10-17）

官方模型族共 **42 个面**（`block_single` 6 + `block_top` 14 + `block_bottom` 14 + `block_middle` 8），
全部是 **1.000 x 1.000 px/单位**：`block_single` 从 64x64 图集取一格（16px / 16 单位），
多层段用 16x16 基础贴图（16px / 16 单位），`fluid_tank_inner` 也是 16x16。
没有一面需要缩放 uv。`check_assets.py [13]` 里 duct_tank **不出现**。

（10-16 历史记录：当时改成"模型 uv 1/4 缩放"，族内 32 个面同为 1.000，也满足 [13]；
该方案已随 Create 模型一起废弃。）

## 防回归

`tools/check_assets.py` 新增 **[13] 变体模型间 UV 缩放一致性**（提示项，不计入合计）：
按 blockstate 把"运行时会互相切换"的模型聚成一族，用**贴图实际像素**（动画贴图按 mcmeta 的帧尺寸）
算每个面的 texel 密度，报 (a) 同贴图被不同变体以不同缩放使用、(b) 族内跨贴图密度差 >1.5 倍。
`tools/selftest_assets.py` 增加了对应自检用例（把 `block_middle` 的 uv 放回 16，
即精确复现本次的原始毛病，必须被 [13] 报出）。

修复后 [13] 仍提示 7 条，都在 `heat_pipe` / `straight_duct` / `valve_duct`
（heatpipe_rim/heatpipe_connection 这类"环/接口细节"贴图故意用了转置或局部 uv，倍率 1.2~2.7），
不属于本次范围，留作清单。

---

# CT 恢复（2026-10-17 20:40）：取格改回 Create 原版做法

## 为什么改

前面几轮的所有取舍都建立在一个前提上：**"本移植没有 CT 层，所以只能静态取一格"**。
这个前提来自上游 `Clockwork-1.20.1-main/ClockworkBlocks.kt:497` —— 那一行
`.onRegister(connectedTextures { DuctTankCTBehaviour(...) })` 被注释掉了。
但同一个文件第 498 行的 `blockModel { DuctTankModel(it) }` **是启用的**，而
`DuctTankModel extends CTModel`，也就是说上游的取格其实一直在跑，被注释掉的
`connectedTextures` 只是 Import 侧的另一条注册路径。本移植把 `blockModel` 这条也漏了，
于是退化成"静态取一格"。

现在按 Create 6.0.10 补回整条链路（对照文件写在各新类的类注释里）：

| 本工程 | 对应 Create 6.0.10 |
|---|---|
| `tank/client/DuctTankSpriteShifts.java` | `AllSpriteShifts.java:74-76`（`FLUID_TANK` / `FLUID_TANK_TOP` / `FLUID_TANK_INNER`） |
| `tank/client/DuctTankCTBehaviour.java` | `content/fluids/tank/FluidTankCTBehaviour.java` |
| `tank/client/DuctTankModel.java` | `content/fluids/tank/FluidTankModel.java`（`CTModel` + 水平面剔除） |
| `CWGasIndex.DUCT_TANK` 的 `.onRegister(...)` | `AllBlocks.java:990` `.onRegister(CreateRegistrate.blockModel(() -> FluidTankModel::standard))` |

## 改完之后的"谁决定什么"

* **模型**：4 个结构模型（`block_single/top/bottom/middle`）一律只引用 **16×16 基础图**
  `duct_tank` / `duct_tank_top`，uv 全部落在 0..16。**不再有任何 64×64 图集的 uv**
  （`block_single.json` 已按 Create 原版重写：`texture_size` 字段与 `[0,12,4,16]` 一起删除）。
* **取格**：烘焙后由 `CTModel#getQuads` 按邻居连接状态把 uv 重采样到
  `<base>_connected` 图集的第 `index` 格（`CTSpriteShiftEntry#getTargetU/getTargetV`
  公式：`(基础图内相对位置 + 列号) / 4`，图集必须正好是基础图的 4 倍边长）。
* **基础图仍然必须有，但内容不再决定世界外观**：`CTSpriteShifter.getCT(RECTANGLE, base)`
  用 `base` 当"原图"、`base + "_connected"` 当"图集"，两边靠**文件名后缀配对**。
  `base` 的作用只剩三个：(1) 提供 uv 坐标系；(2) `CTModel` 用
  `quad.getSprite() == shift.getOriginal()` 判断该不该位移；(3) 物品栏图标 + `particle`。
  所以 `tools/gen_duct_tank_textures.py` 生成的两张 16×16 **继续保留、继续需要**，
  只是它们从"世界外观的唯一来源"降级成"uv 锚点 + 物品图标"。
* **命名约束**：`duct_tank` ↔ `duct_tank_connected`、`duct_tank_top` ↔ `duct_tank_top_connected`
  必须严格成对，改任一张的名字/换成别的图集都要同时改 `DuctTankSpriteShifts`。

## 与 Create 的已知差异（有意为之）

1. **没有 `SHAPE` / 窗户**：Create 的储罐有 6 态 `SHAPE` + `setWindows`
   （`FluidTankBlockEntity.java:297-329`），那是给 `*_window*` 那 20 个模型用的；
   官方 Clockwork 1.20.1 与本工程都**没有**窗户模型/贴图（`duct_tank_window` 不存在），
   硬加会让 `check_assets.py [12]` 立刻报"状态没有变体覆盖"。方块状态因此只有
   `top`/`bottom`（`large` 这个从上游继承来的死属性已删除）。
2. **没有 `DUCT_TANK_INNER`**：内腔面用 `fluid_tank_inner` 的 16×16 基础图，
   但本工程没有 `fluid_tank_inner_connected` 图集；而且罐体密闭，内腔面在游戏里永不可见
   （`block_top` 的 `down` / `block_bottom` 的 `up`），不值得为它多打包一张图集。
3. **内腔气体由 BER 画**（`DuctTankRenderer`），Create 用 `renderFluidBox`；这一层不变。

## 数据

* [13] 仍然全部 **1.000 x 1.000 px/单位**（CT 只把 16px 的面搬到图集的 16px 格，
  一格仍铺满一个 16 单位面），`check_assets.py [13]` 里 duct_tank **仍不出现**。
* [12] 属性快照（`tools/blockstate_properties.json`）已重跑，`duct_tank` 现为 2 属性 / 4 状态，
  与 `blockstates/duct_tank.json` 的 4 个变体一一对应。
