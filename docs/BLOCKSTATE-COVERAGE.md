# blockstate 属性覆盖审计（2026-10-16 · 针对「物理轴承/陀螺仪方块状态纹理异常」的反馈）

> 用户原话：「物理轴承方块状态纹理异常，陀螺仪方块状态纹理异常，推测全部特殊形状方块的
> 方块状态纹理异常，请检查并修复。」
> 任务假设：`check_assets.py [4]` 只查「blockstate 里引用的模型文件在不在」，不查
> 「方块注册的属性取值是否被 blockstate 全覆盖」；没被覆盖的状态会渲染成 missing model（黑紫）。
> 铁律同 `ASSET-AUDIT.md`：每条结论都带证据（源码行 / 反编译 / 实验输出）。

## 0. 结论摘要

| 项 | 结果 | 证据 |
|---|---|---|
| 假设本身（未覆盖状态 → 黑紫） | **成立**，且 1.21.1 的机制已逐行核对 | `BlockStateModelLoader.loadBlockStateDefinitions`：状态没有命中任何变体时 `blockstate = this.missingModel` 并 `LOGGER.warn("... missing model for variant: '{}'", …)`；两个变体同时命中时也会换成 missing model（`prev.model != multipart`，variants 定义里 multipart 恒为 null） |
| 该机制在历史上真的发生过 | **是** | 老日志 `logs/_x/2026-10-11-*.log`：`vs_clockwork:solver` 6 个变体 + `asteroid_block` 1 个 → missing model（当时已修） |
| **物理轴承 / 陀螺仪当前是否存在未覆盖状态** | **否** | `phys_bearing` = `facing[north/east/south/west/up/down]` 6 态，JSON 6 个变体齐；`gyro` 无属性（1 态），JSON 是 `""` 通配。实例日志 `latest.log`（19:37 启动、19:54 仍在写）与 `debug-1.log.gz` 里 `missing model` / `Exception loading blockstate` **各 0 条** |
| 全库真正有问题的方块 | **2 个**（已修） | `coal_burner`（重复 flame selector，且 `facing=north` 的火焰缺失）、`heat_pipe`（8/64 个状态没有任何 selector 命中 → 方块看不见） |
| 额外发现（更贴合"特殊形状方块"的现象） | **1 类** | `heat_pipe` 的 **30 个生成模型里 24 个的臂朝向错了**（方向映射把 y/z 两组的平面记反、l/r 左右对调），见 §3 |
| 新增守门项 | `[12]` | 逐状态覆盖 + 条件合法性 + 解析期致命写法，见 §4 |

## 1. 属性真值从哪来（为什么不能靠读源码）

`blockstates/<id>.json` 要覆盖的状态集合 = `Block#getStateDefinition().getPossibleStates()`，
属性来自三处：

1. 方块自己的 `createBlockStateDefinition`（本仓库源码可见）；
2. **父类**：Create 的 `DirectionalKineticBlock`（加 `facing`）、`KineticBlock`、原版
   `SlabBlock`/`StairBlock`（`type`/`half`/`shape`/`waterlogged`）—— Create 只有 jar，读源码看不到；
3. Create/本仓库的自定义属性：`axis_along_first`、duct 的四态枚举（`true/forced/false/leak`）、
   `blaze`、`reference_axis`、`predictiveness`、`max_distance` …

所以 `tools/probe_blockstates.py` 直接**实例化方块类**取真值：1.21.1 的 `StateDefinition` 就是
`BlockBehaviour` 构造器里由 `createBlockStateDefinition` 建好的（`BlockBehaviour.<init>` →
`new StateDefinition.Builder<>(this)`），实例化即得属性与取值。实测 68/68 个方块全部探到。

三个必须踩过才知道的坑（都已写进脚本注释）：

* **`Property#getName(value)` 才是 JSON 里的字面量**，`String.valueOf(value)` 是枚举常量名。
  duct 的 `up` 是枚举 `SIDE/FORCED_OFF/NONE/LEAK`，序列化名却是 `true/false/forced/leak`；
  第一版分析脚本用 `String.valueOf` 得到 `"True"`，反而把 `coal_burner` 里正确的
  `"lit": true` 判成非法值 —— 方向刚好反了。
* **JSON 里的布尔要按 `JsonPrimitive#getAsString()` 归一**：`"lit": true` 在游戏里就是 `"true"`。
* 探针必须 stub `LoadingModList`（`FeatureFlags.<clinit>` → `FeatureFlagLoader.loadModdedFlags`
  会去问 FML）+ `Bootstrap.bootStrap()` 后把 `BuiltInRegistries.BLOCK` 的 `frozen` 反射置回 false
  （`Block.<init>` 要建 intrusive holder）。原版资源包缺失时 bootstrap 会在
  `CreativeModeTabs.validate()` 失败，此时注册表已经建好，探针容忍这一步失败继续跑。

## 2. 匹配语义（照 1.21.1 反编译源码逐条实现）

* `variants`：key 是逗号分隔的 `属性=值`，只写部分属性就只约束那部分，空 key（`""`）匹配全部。
  属性/取值不认识 → 该 key 抛异常被丢弃（只有它丢失，其余 key 照常）。
* `multipart`：所有状态先绑 multipart 模型，**不会** missing model；但 `when` 里属性/取值不认识
  会在求值时抛异常，该 selector 失效；一个 selector 都没命中的状态渲染成"空"。
  `when` 写成**空对象** `{}` 反而是解析期致命错误（`Selector` 抛 "No elements found in selector"）。
* 解析期致命（整个 blockstate 文件作废、该方块所有状态一起黑紫）：缺 `model`、`x`/`y` 不是
  0/90/180/270 的整数倍（`BlockModelRotation.by` 返回 null）、`weight < 1`、`variants` 值是空数组、
  `apply`/`variants`/`multipart` 类型写错、顶层既没有 variants 也没有 multipart。
* 同一 `when` 出现两次且**模型也相同** → 同一份几何画两遍（多半是 copy-paste 漏改方向）。
  只判"条件重复"会误报 `wing`/`flap`（它们故意用同一 `when` 叠加 `wing_frame` + `wing_bottom`），
  所以必须连模型一起比。

## 3. 修掉的东西

| # | 文件 | 改动 | 为什么 |
|---|---|---|---|
| B1 | `blockstates/coal_burner.json` | `multipart[4]` 的条件 `facing: "east"` → `"north"` | 原本 4 个火焰 selector 里 north 缺失、east 出现两次（`multipart[6]` 还带 `y:90`），即 `facing=east` 时同一份火焰几何被画两遍（一份没转、一份转了 90°），`facing=north` 时点亮反而没有火焰。文件与 1.20.1 逐字节相同，属**旧版遗留**，但按"四个朝向各一份"的明显意图修回 |
| B2 | `tools/gen_heat_pipe_models.py` 的方向映射 + 重新生成的 `models/block/heat_pipe/*.json`（230 个） | x 组 `l=south(+Z) r=north(-Z)`、y 组 `l=east(+X) r=west(-X) u=south(+Z) d=north(-Z)`、z 组 `l=east(+X) r=west(-X) u=up(+Y) d=down(-Y)` | `blockstates/heat_pipe.json` 是与 1.20.1 逐字节相同的原始文件、条件就是这个形状该有的连接方向，模型名→臂朝向被它唯一确定。上一版把 y/z 两组的平面记反（y 组按 XY 平面画、z 组按 XZ 平面画）又把 l/r 左右对调，**30 个形状里 24 个的臂指向了错误的邻居**（例如连东边却画出一根朝下的管）。这个错误不打任何日志，只能靠对照条件发现。230 = 38 形状 × 6 热度档（228）+ `straight` + `item_straight` |
| B3 | `blockstates/heat_pipe.json` | 追加 8 个 selector（`core` / `full` / `full_wo_{六方向}`） | 平面分组的写法只覆盖"某个平面恰好 2 个连接"的状态，于是 0 连接（单独放一个热管 = 默认状态！）、5 连接、6 连接共 8 个状态**一个 selector 都命不中**，方块直接看不见（`HeatPipeBlock#getConnectedState` 六个方向独立赋值，这些状态完全可达） |
| B4 | `models/block/heat_pipe/{core,full,full_wo_*}.json` | 新增（由 B2 的脚本一并生成，6 个温度档） | B3 需要"核心/六通/五通"形状，仓库里没有 |
| B5 | `blockstates/heat_pipe.json` + `HeatPipeBlock` / `HeatPipeBlockEntity` | 38 → **228** 条 multipart（38 形状 × 6 热度档）；新增 `heat` 枚举属性（cool/warm/hot/very_hot/molten/super_hot），`HeatPipeBlockEntity` 每 20 tick 按气体温度跨档才 `setBlockAndUpdate` | 2026-10-17 收口：另外 5 档共 190 个模型此前无人引用（RC6 提示）。模型 UV 也一并改成按贴图内容区取景（`heatpipe_connection` 的有效区在 (0,6)-(16,16)、`heatpipe_rim` 在 0..7×0..16），否则引用后会采到透明区域；`check_assets [13]` 由 27 条降到 2 条（仅剩 valve_duct 旧提示）。生成器：`tools/gen_heat_pipe_blockstate.py`（幂等） |
| — | `phys_bearing` / `gyro` 的所有资源 | **不动** | 覆盖检查与日志都干净（见 §0）。若实机仍看着不对，那是"渲染链"而不是"属性覆盖"的问题，见 §5 |

方向映射的核对方式：把每个 selector 的条件翻译成"应该伸出臂的方向"，再和模型里
每个元素中心相对方块中心的偏移（|Δ|>3.5 ⇒ 该元素是伸出去的臂）比对 —— 修完后 30/30 全对，
修之前 24/30 不一致。

## 4. 守门项 `[12]`（`tools/check_assets.py`）

* 数据源：`tools/blockstate_properties.json`（`tools/probe_blockstates.py` 生成，68 个方块的
  属性名 + 合法取值 + 状态数 + **编译产物指纹**）。属性只随方块类变化，所以快照带指纹：
  `build/classes/java/main/**/*.class` 的哈希与快照里记的不一致时，`[12]` 直接报
  「快照已过期，请重跑 python tools/probe_blockstates.py」——避免拿旧属性数据"检查通过"。
* 覆盖项：未覆盖状态（黑紫）、重叠命中（黑紫）、变体键被丢弃、multipart selector 被丢弃、
  multipart 重复 selector、整文件作废的写法（角度/类型/空数组/空 when/缺 model…）。
* 自检：`tools/selftest_assets.py` 新增 5 个用例（少写一个 `facing` 变体、属性名写错、
  重复 selector、`x=45`、multipart 取值非法），注入后必须由 `[12]` 报出、还原后归 0。

## 5. 仍需实机确认 / 已排除的相邻假设

1. **phys_bearing / gyro 的观感差异最可能来自"渲染链"而非"属性覆盖"**：
   旧版这两类方块有 BER —— `GyroBlockEntityRenderer`（`CachedBuffers.block(state)` 带旋转 +
   `GYRO_BASE` + `PHYS_SHAFT` + `RenderUtil.renderCubeMatrix` 的核心水晶）、
   `PhysBearingRenderer`（四片 `PHYS_*_WING` + `PHYS_ATTACHER` + `PHYS_SHAFT`）；
   旧 `GyroBlock.getRenderShape()` 还返回 `ENTITYBLOCK_ANIMATED`（方块状态模型交给 BER 画）。
   本移植没有这些 BER，方块只画静态 blockstate 模型 —— 看起来会"少东西/不转"，但贴图本身是对的。
   要不要补 BER 属于玩法/渲染范畴，本轮没动（改动面很大，且需要实机校验）。
2. `heat_pipe` 修好后需要实机看一眼：单独放一个热管应能看到核心（以前是**完全看不见**）；
   把它接成 6 通/5 通也应能看到对应形状。六档热度已接上气体温度（500/800/1300 K 与加热器
   三档对齐），**需确认**：升温/降温时外观是否按档切换、20 tick 去抖会不会让换档慢半拍、
   阈值附近是否稳定（不应每 tick 闪）。
3. 相邻假设（已被 `[7]/[8]/[9]` 与本次抽查排除）：贴图文件缺失/写成 missingno 占位、
   贴图键解析不出来、`texture_size` 字段、渲染层没注册、OBJ loader 残留 `forge:obj`。

## 6. 2026-10-18 command_seat 实机崩溃：同名 facing 的 4 向/6 向陷阱

> 实机日志（10:13:30）：
> `java.lang.IllegalArgumentException: Cannot get property DirectionProperty{name=facing,
> clazz=class net.minecraft.core.Direction, values=[north, east, south, west, up, down]}
> as it does not exist in Block{vs_clockwork:command_seat}`
> 堆栈指向 `CommandSeatBlock.useItemOn(CommandSeatBlock.java:79)`，但真正 `getValue` 的是
> `CreativeMotorBlock.hasShaftTowards(CreativeMotorBlock.java:44)`。

### 6.1 结论：不是 command_seat 漏注册 facing

* `command_seat` 的父类是 Create `HorizontalKineticBlock`，它注册的是 **4 向**
  `HORIZONTAL_FACING`（`facing` 的合法值只有 north/south/east/west），快照可证：
  `stateCount=4`；`blockstates/command_seat.json` 也正好 4 个变体。旧版
  `Clockwork-1.20.1-main/.../SequencedSeatBlock.kt:27` 同样是 `HorizontalKineticBlock`，
  所以**不能**按崩溃信息里的 6 向 `FACING` 去 `builder.add`：两个属性都叫 `facing`，
  取值集合不同，在同一个 `StateDefinition` 里无法共存。
* 真正的错误在 `CommandSeatBlock.useItemOn`：1.21 移植版写成
  `kinetic.hasShaftTowards(level, pos, state, hit.direction)`，把 **command_seat 自己的
  BlockState** 传给了手里那个方块的 `hasShaftTowards`。旧版 `SequencedSeatBlock.kt:48`
  没有限定接收者，Kotlin 解析到的是**座椅自己**的 `hasShaftTowards`（`face != UP`）；
  1.20.1 从来没有把座椅 state 交给别人。
* 于是：手里拿 Create 电机（6 向 `FACING`）右键 command_seat（4 向 `HORIZONTAL_FACING`）
  → 电机从座椅 state 里找 6 向 `facing` → 找不到 → 崩溃。换任何“按自己的属性读 state”的
  DirectionalKineticBlock 都会复现，属**同一模式**，不是 command_seat 独有。

### 6.2 修法

`CommandSeatBlock.useItemOn` 改回调用**本方块**的 `this.hasShaftTowards(...)`，恢复旧版
“座椅这一面有轴才让位给放置”的语义；不再把 `state` 传给 `kinetic`。详细理由写在
`useItemOn` 与类 Javadoc 里（含“为什么不能补 6 向 FACING”）。

### 6.3 新增守门项 `[14]` / `[15]`（`tools/check_assets.py`）

* `[14]`：扫描全部 `.getValue(...)` / `.setValue(...)`，把属性常量沿“本类字段 → 父类 →
  接口 → 已知 Create/原版基类”解析成 `(属性名, 合法取值)`，再和 `blockstate_properties.json`
  里**该方块**的属性比对。4 向 `HORIZONTAL_FACING` 与 6 向 `FACING` 按取值集合区分，
  不再被同一个 `facing` 名字骗过。
  - 全仓 166 处 `getValue` + 113 处 `setValue`；落到注册方块上的 146 处，属性解析成功
    **138 处**（其中 facing/axis **110 处**），其余 8 处是 `ValueSettings#getValue` 等
    非 BlockState 调用，另 1 处是 Mixin 读 Create 的 `SteamEngineBlock.FACING`（外部方块）。
  - 结果 0 问题；`command_seat` 的 `HORIZONTAL_FACING` 命中 4 向快照，不再误报。
* `[15]`：方块类里禁止 `A块.hasShaftTowards(..., state, ...)` / `A块.getRotationAxis(state)`
  这类“把本方块 state 交给另一个方块”的写法，命中即报。全仓 0 命中；`UniversalShaftBlock` /
  `ExtendonBlock` 的 `rotate.hasShaftTowards(..., neighbour, ...)` 传的是邻居自己的
  state，不误报。
* 自检：`tools/selftest_assets.py` 新增 2 个回归用例 —— 把 command_seat 的
  `HORIZONTAL_FACING` 改成 `FACING`（必须被 `[14]` 报出）、把 `this.hasShaftTowards`
  改回 `kinetic.hasShaftTowards`（必须被 `[15]` 报出）。

### 6.4 验收

* `gradlew --offline build` = BUILD SUCCESSFUL；
* `check_assets.py --jar` 的硬失败项 `[1]..[12]/[14]/[15]` 全 0（`[13]` 仍是 2 条历史 UV
  提示：`valve_duct` 的 `block_vertical`，非本轮引入，不计入合计）；
* **需实机确认**：手持 Create 电机 / 手摇曲柄右键 command_seat 的四个侧面不再崩，且仍应让位
  给放置逻辑（旧版行为：`hasShaftTowards` 对 UP 返回 false，所以只有顶面不接轴）。
  顶面右键应正常坐下/开界面；其余五个面在手里拿动力方块时让位给放置。
  如果实机觉得应该只在“手里方块真能接这一面”时让位，那需要另做“用对方方块自己的 state
  判断”的设计，不能再用本方块 state 调对方方法。
