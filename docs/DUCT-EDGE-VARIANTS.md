# 导管边类型：单向 / 过滤 / 智能（旧版"螺丝刀切面类型"机制的移植记录）

> 范围：`content/logistics/gas/duct/**`、`content/logistics/gas/{GasNode,GasLink,GasFilter,GasNetwork,GasMix}.java`、
> `assets/vs_clockwork/{models/block/duct/custom,textures/block}/*`、`lang/{en_us,zh_cn}.json`、
> `integration/ponder/scenes/DuctScenes.java`、`tools/selftest_duct_variants.py`、`tools/duct_edge_lang.py`。
>
> **2026-10-18 重构**：上一轮把单向 / 智能 / 铜做成三个独立方块（`oneway_duct` / `smart_duct` / `copper_duct`），
> 本轮**改回旧版机制**：导管<b>每个面</b>有一个"边类型"，用螺丝刀循环切换；三个方块及其全部资源已删除。
> 结论在 §0，旧版取证在 §1，实现与取舍在 §2–§4，删除了什么在 §5，自检在 §6，实机清单在 §7。

---

## 0. 结论速查

| 项目 | 结论 |
|---|---|
| 数据模型 | `GasDuctBlockEntity` 持有 `EnumMap<Direction, DuctEdgeSettings>`，每个面一份（类型 + 参数） |
| 类型枚举 | `DuctEdgeType`：`NORMAL / ONEWAY_FORWARD / ONEWAY_BACKWARD / FILTERED / SMART`（= 旧版 `PIPE` 改名 + 后四个） |
| 切换顺序 | `NORMAL → ONEWAY_FORWARD → ONEWAY_BACKWARD → FILTERED → SMART → NORMAL`（逐字沿用旧版 `nextScrewdrivable()`） |
| 螺丝刀右键 | **循环被点击那一面的边类型**（服务端执行；行动栏 + 扳手音效） |
| 潜行 + 螺丝刀 | 循环该类型的**主参数**：过滤 → 气体种类；智能 → 阈值指标（NONE / PRESSURE / TEMPERATURE） |
| 空手右键 | **次参数**：过滤 → 白/黑名单；智能 → 阈值档位（10 档） |
| 潜行 + 空手 | **第三参数**：智能 → 大于 / 小于 |
| 单向语义 | 出口面 `Flow.OUT` / 入口面 `Flow.IN`；**不节流**（通径与通流倍率与普通管相同） |
| 过滤语义 | `GasFilter(gas, blacklist)`：白名单只搬该气体；黑名单只搬其余组分（被拦气体一滴不动） |
| 智能语义 | `|Δp|` 或气体温度与阈值比较，`moreThan` 决定大于/小于；NONE = 常开 |
| 铜 | 不再是独立方块/边类型；**智能边就是铜体**（旧版 SMART 边画的就是 `custom/copper`）：通流 ×1.5、导热 0.15 |
| 外观 | BER 在接缝处画 `duct/custom/{oneway_forward,oneway_backward,smart,copper}`；`FILTERED→smart`、`SMART→copper`（照抄旧版错位） |
| 同步 | `DuctEdges` 写进 NBT（只写非默认面）；`setChanged() + sendData()`，`getUpdateTag → write(clientPacket=true)` 自动带上 |
| 删除 | 8 个类（3 方块 + 3 方块实体 + 2 公共基类）、12 个资源/配方/战利品文件、18 个 lang 键、2 个工具脚本、Ponder/`CWGasIndex` 里的引用 |
| 自检 | `tools/selftest_duct_variants.py`：**286 条断言**，含 6 组网络语义场景 + 11 组静态检查 |

---

## 1. 旧版取证

### 1.1 边类型枚举与循环顺序

`Clockwork-1.20.1-main/common/src/main/kotlin/.../content/logistics/gas/duct/DuctEdgeType.kt`

```
16  enum class DuctEdgeType {
17      NONE,
18      PIPE,
19      ONEWAY_FORWARD,
20      ONEWAY_BACKWARD,
21      FILTERED,
22      SMART
23      ;
25      fun nextScrewdrivable(): DuctEdgeType {
26          return when (this) {
27              PIPE -> ONEWAY_FORWARD
28              ONEWAY_FORWARD -> ONEWAY_BACKWARD
29              ONEWAY_BACKWARD -> FILTERED
30              FILTERED -> SMART
31              SMART -> PIPE
32              else -> PIPE
33          }
34      }
41      fun createEdgeType(nodeA: DuctNodePos, nodeB: DuctNodePos, type: DuctEdgeType): DuctEdge {
43          PIPE -> createPipeEdge(nodeA, nodeB)
44          ONEWAY_FORWARD -> createOneWayEdge(nodeA, nodeB)
45          ONEWAY_BACKWARD -> createOneWayEdge(nodeB, nodeA)
46          FILTERED -> createFilteredEdge(nodeA, nodeB)
47          SMART -> createSmartEdge(nodeA, nodeB)
```

**移植结论**：枚举保留 5 个值。旧版的 `NONE` 表示"这一面没有边"，本移植用方块状态
`DuctConnectionType`（`NONE / SIDE / FORCED_OFF / LEAK`）表达，所以不需要重复的 `NONE`；
旧版 `PIPE` 改名为 **`NORMAL`**，避免与连接状态的 `NONE`/`SIDE` 语义混淆（同一份代码里
出现两个 `NONE` 是纯命名陷阱）。循环顺序用 `ordinal()+1` 取模，**与上表逐项一致**。

### 1.2 螺丝刀按面切换（旧版真正的入口）

`.../duct/DuctBlock.kt`

```
370  override fun onScrewdrived(state: BlockState, context: UseOnContext): InteractionResult {
380      if (leaking) {                       // 泄漏面：螺丝刀一敲就封好
381          playScrewSound(context.level, context.clickedPos)
389      if (!connected) return InteractionResult.SUCCESS   // 没连接：直接返回
398      foundEdge?.interact(context.player as ServerPlayer) // 有边：打开该边的 GUI
405  override fun onSneakScrewdrived(state: BlockState, context: UseOnContext): InteractionResult {
413      if (!connected) return InteractionResult.FAIL
423      playScrewSound(context.level, context.clickedPos)
425          val type = blockEntity.cycleEdgeType(changeDirection)
```

`.../duct/DuctBlockEntity.kt`（类型存在**方块实体**的 `EnumMap` 里，按面索引）

```
 24  val DIR_TO_CONNECTION_TYPE: EnumMap<Direction, DuctEdgeType> = EnumMap(Direction::class.java)
 44      val edgeType = DuctEdgeType.entries[tag.getInt("DuctEdgeType${dir.name}")]   // 读存档/更新包
 64      tag.putInt("DuctEdgeType${dir.name}", this.DIR_TO_CONNECTION_TYPE[dir]!!.ordinal)
 91  fun cycleEdgeType(dir: Direction): DuctEdgeType {
 94      val currentType = this.DIR_TO_CONNECTION_TYPE[dir]!!
 97      val otherDuctNodePos = (level!!.getBlockEntity(blockPos.relative(dir)) as? INodeBlockEntity)?...
 99      val nextType = currentType.nextScrewdrivable()
100      setEdgeType(dir, otherDuctNodePos, nextType, false)
105  fun setEdgeType(dir: Direction, otherDuctNodePos: DuctNodePos, edgeType: DuctEdgeType, clientPacket: Boolean, ...)
122          if (getDuctNodePosition().magnitudeSqr() > otherDuctNodePos.magnitudeSqr()) {   // 单向的 nodeA/nodeB
```

**关键结论**：边类型是**方块实体的面级数据**，不是方块状态、也不是独立方块。
本移植照此实现（§2.2），连"存在 BE、按方向索引"这一点都保持一致。

### 1.3 单向：只对调 nodeA/nodeB，**不节流**

`.../oneway/ClockworkOnewayDuct.kt:9-10`

```
 9  class ClockworkOnewayDuct(type: ConnectionType, nodeA: DuctNodePos, nodeB: DuctNodePos) :
10      OneWayDuctEdge(type, nodeA, nodeB, radius = 0.3125, length = 0.375)
```

`.../util/DuctNetworkUtils.kt:24-30` 里普通管是 `PipeDuctEdge(..., radius = 0.3125, length = 0.375)` ——
**逐字相同**。所以单向只改方向，不碰通径/流量系数。本移植：`withOnlyOut()` / `withOnlyIn()`，
`aperture` 与 `flowScale` 保持 1.0（自检 `[单向-不节流]` 有断言）。

**方向的定义（本移植与旧版不同，必须注意）**：旧版用 `magnitudeSqr()`（= x²+y²+z²）
比较两端坐标来定 `nodeA/nodeB`，对负坐标并不对称，且是"每条边一个方向"。本移植把方向钉在
**被点击的那一面**上：

* `ONEWAY_FORWARD` = 气体可以从本导管**经这一面流出**（这一面是出口）；
* `ONEWAY_BACKWARD` = 气体可以从对侧**经这一面流入**本导管（这一面是入口）。

这样同一个方块的不同面可以各自不同，且不需要先理解"nodeA 是谁"。只在一侧声明就足够：
`GasNetwork` 会读接缝两侧的 link，任意一侧的 `OUT` 都能挡住反向流（§2.4）。

### 1.4 过滤：气体种类集合 + 黑名单

`.../filter/edges/ClockworkFilteredDuctEdge.kt:19-28`

```
19  class ClockworkFilteredDuctEdge(
23      override var radius: Double = 0.3125, override var length: Double = 0.375, ...
24      override var thermalConductivityMultiplier: Double = 1.0,
25      override var filter: HashSet<GasType> = HashSet(),
26      override var blacklist: Boolean = false,
30      override fun interact(player: ServerPlayer): Boolean {
31          ClockworkPackets.sendTo(FilterScreenOpenPacket(nodeA, nodeB, filter, blacklist), player)
```

过滤维度**只有气体种类**（与压力/温度无关，那两个在下一条边里），带白/黑名单。

### 1.5 智能：FilterType + 比较值 + moreThan（GUI）

`.../smart/ClockworkSmartEdge.kt:14-20,29-31`

```
23      ClockworkPackets.sendTo(SmartScreenOpenPacket(nodeA, nodeB, filter, comparisonValue, moreThan), player)
29      tag.putInt("filterOrdinal", filter.ordinal)
30      tag.putDouble("comparisonValue", comparisonValue)
31      tag.putBoolean("moreThan", moreThan)
```

`.../smart/SmartDuctScreen.kt`

```
28              FilterType.NONE -> AllIcons.I_NONE
29              FilterType.PRESSURE -> AllIcons.I_PLAY
30              FilterType.TEMPERATURE -> AllIcons.I_CLEAR
36          val icon = when (moreThan) {              // 大于/小于切换按钮
73          scrollInput.withRange(0,99999)             // comparisonValue 的取值域
85          val symbol = when(filter) {
87              FilterType.PRESSURE -> " kPa"
88              FilterType.TEMPERATURE -> " K"
```

即：**指标**（不看 / 压差 / 温度）+ **数值**（0..99999，单位 kPa 或 K）+ **大于/小于**。
读数与输入全在那个界面里，方块本身没有状态属性。

### 1.6 渲染：BER 在接缝处画 `duct/custom/*`

`.../duct/DuctRenderer.kt:42-73`

```
42  for (dir in Direction.values()) {
45      if (... == DuctConnectionType.LEAK) {                              // 泄漏面
46          val dirLeak = CachedBuffers.partialFacing(leak, blockEntity.blockState, dir.opposite)
50      if (!...isConnected) continue                                     // 没连接：什么都不画
52      val dirConnection = CachedBuffers.partialFacing(connection, blockEntity.blockState, dir.opposite)
57      val edge = blockEntity.DIR_TO_CONNECTION_TYPE[dir] ?: continue
59      val partial = when (edge) {
60          DuctEdgeType.FILTERED -> ClockworkPartials.DUCT_SMART
61          DuctEdgeType.SMART ->  ClockworkPartials.DUCT_COPPER
63          DuctEdgeType.ONEWAY_FORWARD ->
64              if (dir.axisDirection.step == -1) ClockworkPartials.DUCT_ONEWAY_FORWARD else ClockworkPartials.DUCT_ONEWAY_BACKWARD
65          DuctEdgeType.ONEWAY_BACKWARD ->
66              if (dir.axisDirection.step == 1) ClockworkPartials.DUCT_ONEWAY_FORWARD else ClockworkPartials.DUCT_ONEWAY_BACKWARD
68          else -> if (dirBe?.blockState?.block is IDuct) null else ClockworkPartials.DUCT_RIM
72      CachedBuffers.partialFacing(partial, blockEntity.blockState, dir.opposite)
```

三个事实：
1. 只有**已连接**的面才画边类型件；普通边（`else` 分支）画的是收口环 `rim`（接机器时）；
2. `partialFacing(..., dir.opposite)` 把模型的 north 侧贴到 `dir` 那一面 —— 四个 `duct/custom/*`
   模型的几何都是 `z ∈ [0,3]` 的 3 px 法兰盘，所以这个调用正好把它放在**接缝**上；
3. `FILTERED` 用 **smart** 模型、`SMART` 用 **copper** 模型（命名错位，§1.7）。

### 1.7 铜：旧版**没有**语义，只有 SMART 边的外观

* `Clockwork-1.20.1-main/common/src/main/{java,kotlin}` 里搜 `copper_duct|CopperDuct|COPPER_DUCT`：
  只命中资源 `models/block/duct/custom/copper.json`，**没有任何类**；
* 旧版 `lang/en_us.json` 搜 `copper` 零命中（没有 `block.vs_clockwork.copper_duct`）；
* Ponder 文本 `vs_clockwork.ponder.duct.text_4..8` 只列 One-way / Filtered / Smart；
* `copper` 模型唯一的用途就是 `DuctRenderer.kt:61` 画 SMART 边。

所以"铜 = 更导热 / 更大流量"是**我们补的语义**，旧版只有一张贴图。本轮的取舍见 §2.5。

---

## 2. 实现：数据模型与网络语义

### 2.1 类型枚举

`duct/DuctEdgeType.java`（新建）

```java
public enum DuctEdgeType implements StringRepresentable {
    NORMAL("normal"), ONEWAY_FORWARD("oneway_forward"), ONEWAY_BACKWARD("oneway_backward"),
    FILTERED("filtered"), SMART("smart");
    public DuctEdgeType next() { return VALUES[(ordinal() + 1) % VALUES.length]; }
    public boolean isOneWay() { ... }
    public static DuctEdgeType byName(String name) { ... }   // 未知值回落 NORMAL，旧存档不炸
}
```

实现 `StringRepresentable` 是为了 NBT 里存**名字**而不是 ordinal：以后往枚举中间插值时，
老存档不会因为序号错位而变成另一种边类型（旧版存的正是 ordinal，属于旧版的隐患）。

### 2.2 面级数据为什么存在方块实体

`GasDuctBlockEntity` 增加：

```java
private final EnumMap<Direction, DuctEdgeSettings> edges = new EnumMap<>(Direction.class);
public DuctEdgeSettings edge(Direction dir)  // 永远非 null
public DuctEdgeType edgeType(Direction dir)  // 网络层 / BER 的查询入口
public void setEdgeType(Direction dir, DuctEdgeType type)
```

**不放进方块状态的理由**（这是本节唯一需要论证的地方）：

* 6 个面 × 5 种类型 = 5⁶ = 15625 种组合；现有导管状态已经有 6 个 `DuctConnectionType`
  属性（4 值）与水，再乘上去变体数会到**百万级**，blockstate JSON 与守门 `check_assets.py [12]`
  的逐状态覆盖检查都无法维护；
* 边类型是**配置**而不是**形状**：它不影响碰撞箱、不影响方块模型的选择（外观由 BER 按 BE 画）、
  不参与 `updateShape` —— 正好是旧版把 `DIR_TO_CONNECTION_TYPE` 放方块实体的理由；
* 方块状态更适合"渲染/碰撞必须知道"的东西，这里两者都不需要。

`DuctEdgeSettings`（新建）保存一份面配置：`type`、`gas`（`null` = 全部）、`blacklist`、
`filterType`、`threshold`（Pa 或 K）、`moreThan`，并提供三个循环方法与 `describe()`。
**切类型不重置参数**（旧版 `edgeData` 按接缝复用，等价行为）；只有 `filterType` 变化时阈值
会被换成该指标的默认值（把 200000 Pa 当温度用没有意义）。

### 2.3 NBT 与同步

```java
// write(tag, provider, clientPacket=true/false) —— 存档与更新包共用同一个方法
CompoundTag edgeTags = new CompoundTag();          // 只写非默认面
for (Direction dir : Direction.values())
    if (!edges.get(dir).isDefault()) edgeTags.put(dir.getSerializedName(), ...);
tag.put("DuctEdges", edgeTags);

// read(...)：先全部重置为 NORMAL 再逐面覆盖
```

`edgeDataChanged()` = `setChanged() + sendData()`。Create 的 `SyncedBlockEntity`：

* `getUpdateTag()` → `writeClient(new CompoundTag(), registries)` → `write(tag, ..., clientPacket = true)`；
* `sendData()` → `ServerLevel.blockChanged(pos)` → 把 `getUpdatePacket()`（内含上面那个 tag）发给跟踪该区块的玩家。

所以本移植**只实现 `write/read` 一处**，服务端存档、区块加载、联机更新包三份数据天然一致。
客户端 BER 与护目镜读到的就是同一份 `edges`。
`read` 用"换新对象"而不是就地改，保证"把某个面改回普通"之后客户端不会残留旧类型。

### 2.4 网络语义（`GasNetwork` 只改五处）

边类型 → `GasLink`（`GasDuctBlockEntity#gasLink`）：

| 类型 | `GasLink` | 效果 |
|---|---|---|
| `NORMAL` | `GasLink.PIPE` | 与改动前逐位相同 |
| `ONEWAY_FORWARD` | `PIPE.withOnlyOut()` | 只许从本节点经这一面流出 |
| `ONEWAY_BACKWARD` | `PIPE.withOnlyIn()` | 只许从对侧经这一面流入 |
| `FILTERED`（白名单） | `GasLink.filtered(gas)` | 只搬该气体 |
| `FILTERED`（黑名单） | `GasLink.blocking(gas)` | 除该气体外都搬 |
| `FILTERED`（未选气体） | `GasLink.PIPE` | 刚切过来不改变行为 |
| `SMART` | `PIPE.withFlowScale(copperDuctFlowScale).withConductivity(copperDuctThermalConductivity)` | 铜体（§2.5） |

`GasNetwork#transfer` 的四处改动：

1. **方向闸**（原有逻辑，未改）：`forbidAToB |= linkA.flow()==IN || linkB.flow()==OUT`，反向同理。
   两侧都看，所以"只在一侧配单向"也挡得住倒灌。
2. **阈值闸带方向**：`gasFlowGateOpen(Direction dir, double pressureDelta, double gasTemperature)` ——
   旧版每条边各有一套阈值，而 link 是在压差算出**之前**查的，所以只能在算出 `delta` 后回调节点；
   现在多传一个"本节点朝向对侧的那一面"，才能读到正确的面级配置：
   ```java
   Direction dirFrom = forward ? dir : dir.getOpposite();
   Direction dirTo = dirFrom.getOpposite();
   if (!from.gasFlowGateOpen(dirFrom, delta, T) || !to.gasFlowGateOpen(dirTo, -delta, T)) return;
   ```
3. **过滤支持黑名单**：`GasLink.filter` 的类型由 `GasType` 换成 `GasFilter(gas, blacklist)`。
   白名单路径与原来相同（只搬一种气体）；黑名单路径新增：
   ```java
   double available = from.gasMix().totalMass() - from.gasMix().massOf(filter.gas());
   parcel = from.gasMix().extract(Math.min(flow, available), filter.gas());  // 跳过被拦气体
   ```
   `GasMix#extract(kg, excluded)` 的分母是"**允许通过的那部分质量**"，所以请求 0.1 kg
   就真的搬 0.1 kg 其余气体，而不是先按全量抽样再丢掉被拦组分（后者会让黑名单边的实际流量
   随被拦气体占比变化，很难解释）。
4. **吞吐量记实际搬运量**：`recordThroughput(parcel.totalMass())` 而不是请求量 ——
   过滤/黑名单会让实际搬运量小于请求量，否则护目镜会虚报流量。

阈值判定的完整映射（`DuctEdgeSettings#gateOpen`）：

| `filterType` | `moreThan = true` | `moreThan = false` |
|---|---|---|
| `NONE` | 恒放行 | 恒放行 |
| `PRESSURE` | `|Δp| > threshold`（Pa） | `|Δp| < threshold` |
| `TEMPERATURE` | `T > threshold`（K） | `T < threshold` |

压差取绝对值：旧版 GUI 的"大于/小于"只显示 kPa 数值、没有方向符号，所以比较的是大小。

### 2.5 铜：保留语义，但作为 SMART 的材质（取舍与理由）

任务给了两个选项：把铜做成 SMART 的一个子模式，或者取消。**选择保留**，理由：

1. **旧版语义上最自然的落点**：SMART 边在旧版里画的**就是** copper 模型
   （`DuctRenderer.kt:61`）。"智能边 = 铜体智能接头"比"智能边恰好长成铜色"更能解释那张贴图；
2. **不引入新方块、不新增手势**：铜是 SMART 的固有材质，不需要第 4 个参数旋钮（本移植已经用了
   螺丝刀 / 潜行螺丝刀 / 空手 / 潜行空手四个手势，再加一个就不可用了）；
3. **两个配置项继续有意义**：`CWServer#copperDuctFlowScale`（默认 1.5）与
   `copperDuctThermalConductivity`（默认 0.15）仍然控制真实行为，不是死配置；
4. **保留了"铜接头"这个玩法**：把某个面切成 SMART、阈值指标留在 `NONE`，就是一个
   **通流 ×1.5、导热 0.15 的铜接头**（旧版独立铜方块的功能由这一种配置承担）；
   切到 PRESSURE / TEMPERATURE 后它同时是阈值闸。

代价（明确写出）：铜不再是"可独立放置的一段管子"，而是**导管某个面的属性**；
想要整段铜管就把接缝逐面切成 SMART。这与旧版"铜只是外观"的差距，小于"再造一个方块"的差距。

---

## 3. 配置 UX：旧版 GUI vs 本移植手势

### 3.1 手势对照表

| 手势 | 旧版（`DuctBlock.kt`） | 本移植（`DuctEdgeInteraction`） |
|---|---|---|
| 螺丝刀右键 | 打开该边的配置界面（`foundEdge?.interact`，行 398） | **循环该面的边类型**（`Gesture.CYCLE_TYPE`） |
| 潜行 + 螺丝刀 | 循环该面的边类型（行 405-425） | **循环主参数**（`Gesture.PRIMARY`） |
| 空手右键 | 同"螺丝刀右键"（也是开界面） | **次参数**（`Gesture.SECONDARY`） |
| 潜行 + 空手 | — | **第三参数**（`Gesture.TERTIARY`） |

**为什么把两个操作对调**：旧版的主操作是"打开 GUI"，而本移植没有 GUI。没有界面时，
最常用的操作（切类型）必须放在**不需要潜行**的右键上，否则玩家每切一次都要按住潜行。
这是"能玩"和"能查"的分界线，也是本移植唯一主动改变旧版手势的地方（任务 §1/§3 也指定了这个映射）。

### 3.2 每个类型的参数与旋钮

| 类型 | 主参数（潜行+螺丝刀） | 次参数（空手） | 第三参数（潜行+空手） |
|---|---|---|---|
| `NORMAL` | — | — | — |
| `ONEWAY_FORWARD` / `ONEWAY_BACKWARD` | —（方向就是类型本身） | — | — |
| `FILTERED` | 气体种类：全部 → 空气 → 废气 → 蒸汽 → 燃素 → 以太 → 星烷 → 沼汽 → 硫酸气 → 臭氧石气 → 全部 | 白名单 ↔ 黑名单 | — |
| `SMART` | 阈值指标：NONE → PRESSURE → TEMPERATURE → NONE | 阈值档位（10 档，见下） | 大于 ↔ 小于 |

阈值档位（`DuctEdgeSettings` 里的两张表）：

* 压差：1 / 2 / 5 / 10 / 20 / 50 / 100 / 200 / 500 / 1000 kPa
* 温度：250 / 300 / 350 / 400 / 500 / 600 / 800 / 1000 / 1500 / 2000 K

切换 `filterType` 时阈值取 `CWServer#smartDuctPressureThreshold`（默认 200000 Pa = 200 kPa）/
`smartDuctTemperatureThreshold`（默认 500 K）—— 这两个配置项从"全局阈值"变成"**新切到该指标时的
初始值**"，仍然真实生效。档位循环取"第一个严格大于当前值的档"，所以从任意默认值出发，
第一下也一定落到档位上，超出最大档回绕。

### 3.3 反馈与读数（没有 GUI 也能配置完整）

* **行动栏**：每次操作后显示"面名 + 完整配置摘要"，例如
  `South: Smart (copper body, dP > 200 kPa)` / `East: Filtered (only Steam)` / `Up: One-way (gas leaves through this face)`；
  语言键在 `lang/{en_us,zh_cn}.json`（`vs_clockwork.duct.edge.*`）；
* **音效**：切类型与调参数使用不同音高（1.0 / 1.4）的 `WRENCH_ROTATE`，闭眼也能分清动的是哪一层；
* **护目镜**：`GasDuctBlockEntity#addToGoggleTooltip` 列出**所有非普通面**的类型与参数；
  没有配置时显示 `Edges: Normal`。为什么列全部而不是"正看着的那一面"：
  `IHaveGoggleInformation` 拿不到玩家瞄准的面（没有 RayTrace 上下文），列全部比显示一个
  来路不明的面更可靠，也顺便让玩家一眼看到"哪一面还漏配"；
* **旧版的 GUI 读数**（`SmartDuctScreen` / `FilterScreen`）都能被上面三样覆盖，除了
  "自由输入任意数值"（§3.4）。

### 3.4 明确的功能降级（不假装等价）

1. **没有 GUI**：旧版 `FilterScreen` / `SmartScreen` 的多选气体集合、自由滚动阈值、搜索框都没有。
   本移植是单气体 + 10 档阈值 + 循环赋值。要恢复旧版手感只需补一个 Screen：把
   `DuctEdgeSettings` 暴露成 `ContainerData`，网络层完全不用改。
2. **过滤是单气体**（旧版是 `HashSet<GasType>`）：见 `GasFilter` 类注释，一条连接一次只搬
   一种气体，多选没有对应的搬运语义。
3. **阈值是离散档位**（旧版 0..99999 连续）：没有输入框时只能离散；档位按 ×2 取，覆盖
   1 kPa..1000 kPa / 250 K..2000 K。
4. **两侧都配置时只认 a 侧过滤条件**：一条连接只取第一个非空过滤（`GasNetwork#transfer`），
   所以建议只配接缝的一侧（§4.3 画了模型重叠的说明）。

---

## 4. 渲染

### 4.1 partial 与提前初始化

`DuctPartials` 新增 4 个 partial（旧版同名的 `ClockworkPartials.DUCT_*`）：

| 常量 | 模型 | 用途 |
|---|---|---|
| `EDGE_ONEWAY_FORWARD` | `block/duct/custom/oneway_forward` | 单向（箭头朝坐标轴负方向） |
| `EDGE_ONEWAY_BACKWARD` | `block/duct/custom/oneway_backward` | 单向（箭头朝正方向） |
| `EDGE_FILTERED` | `block/duct/custom/smart` | 过滤边（旧版 `FILTERED → DUCT_SMART`） |
| `EDGE_SMART` | `block/duct/custom/copper` | 智能边（旧版 `SMART → DUCT_COPPER`，即"铜"） |

这四个模型**没有任何 blockstate / 物品模型引用**，只被 BER 使用，所以必须在
`ModelEvent.RegisterAdditional` 之前创建 —— `CWClientSetup#onClientSetup` 里已有
`DuctPartials.init()` 一行（本轮之前就有，本轮只是让它多登记 4 个 partial）。
`tools/check_render_coverage.py [RC2]` 会静态校验"幂等 init + 提前调用 + 模型文件存在"。

### 4.2 单向模型的方向（推导，别猜）

两个单向模型几何完全相同，只有 east 面 UV 镜像 —— 差别就是**箭头朝向**。
"哪个模型朝哪边"没有文档，但从旧版渲染规则（§1.6 第 63-66 行）可以唯一反推：

```
ONEWAY_FORWARD  -> 方向为负轴（north/west/down）时画 oneway_forward，否则 oneway_backward
ONEWAY_BACKWARD -> 方向为正轴（south/east/up）时画 oneway_forward，否则 oneway_backward
```

只有"`oneway_forward` = 箭头朝坐标轴负方向"这一种解释能同时满足上面两条，并且让箭头
**永远指向气体实际流过的方向**（出口面朝外、入口面朝内）。所以：

```java
boolean negativeAxis = dir.getAxisDirection().getStep() < 0;
boolean flowNegative = outOfThisFace == negativeAxis;   // 出口面流向=外法线；入口面流向=-外法线
return flowNegative ? DuctPartials.EDGE_ONEWAY_FORWARD : DuctPartials.EDGE_ONEWAY_BACKWARD;
```

自检 `[单向-渲染]` 用**两个不同表达式**（本项目的 `flowNegative` 公式 vs 旧版的
`axisDirection.step` 判定）对 6 个方向逐项互证；游戏里模型抄反不会报错，只能靠这种互证抓住。

### 4.3 接缝处的模型与重叠

`DuctRenderer` 每面：

1. `LEAK` → 画 `leak`，跳过；
2. 未连接 → 跳过；
3. 已连接 → 画 `connection` 接口圈；然后按 `be.edgeType(dir)`：
   * `NORMAL` → 接的不是导管家族（机器/储罐）时画 `rim` 收口环；
   * 其余类型 → 画对应的 `duct/custom/*`（不再画 `rim`，与旧版 `else` 分支一致）。

位置与旧版逐像素一致：`CachedBuffers.partialFacing(partial, state, dir.getOpposite())`。

**两侧都配置时的重叠**：旧版把边类型镜像到接缝两侧、两侧各画一个同样的模型（重叠）。
本移植的边类型是**单面配置**，默认只画配置过的那一面；只有玩家把接缝两侧都配成非普通类型
才会出现两个模型叠在同一接缝 —— 与旧版的重叠效果相同。**建议只配一侧**（网络语义上
一侧就足够，§2.4）。

`isDuctFamily` 去掉了三个被删的方块类，其余不变（`IDuct` 的等价物）。

---

## 5. 删除了哪些冗余

上一轮"三个独立方块"的实现已全部移除（`git status` 应能看到这些删除）：

| 类别 | 文件 |
|---|---|
| 类（8 个） | `duct/edge/{OneWayDuctBlock,OneWayDuctBlockEntity,SmartDuctBlock,SmartDuctBlockEntity,CopperDuctBlock,CopperDuctBlockEntity,DuctFittingBlock,DuctFittingBlockEntity}.java`（`duct/edge/` 目录整个删除） |
| blockstate（3） | `blockstates/{oneway,smart,copper}_duct.json` |
| 物品模型（3） | `models/item/{oneway,smart,copper}_duct.json` |
| 配方（3） | `recipe/crafting/logistics/{oneway,smart,copper}_duct.json` |
| 战利品（3） | `loot_table/blocks/{oneway,smart,copper}_duct.json` |
| 语言（18 键） | 三个方块的方块名 / item 名 / `.function` / 状态串（`tools/duct_edge_lang.py` 的 `REMOVE` 列表） |
| 注册 | `CWGasIndex` 里 3 个 `BlockEntry` + 3 个 `BlockEntityEntry` 与相关 import |
| Ponder | `DuctScenes` 里 `setBlock(ONEWAY_DUCT/SMART_DUCT/COPPER_DUCT)` 三处；`ClockworkPonderScenes` 的 `forComponents(...)` 参数 |
| 工具 | `tools/gen_duct_edge_assets.py`（生成物已不存在）、`tools/add_duct_edge_lang.py`（被 `tools/duct_edge_lang.py` 取代） |

**保留**的贴图（不要删）：`textures/block/{oneway_duct,smart_duct,copper_duct}.png` 仍被
`models/block/duct/custom/*.json` 引用，删掉就是黑紫。

---

## 6. 自检

`python tools/selftest_duct_variants.py` → **286 条断言**，分组：

| 组 | 覆盖的失效模式 |
|---|---|
| `[规则]`（50+ 条源码片段） | Java 改了但自检没跟上：方向闸、通径 min/倍率 max、带方向的阈值闸、黑名单路径、NBT 键、手势映射…… |
| `[循环]` | 枚举声明顺序 ≠ `next()` 实际顺序（改枚举不重排 = 静默换语义） |
| `[档位]` | 档位不递增、默认值不在区间、循环不回绕 |
| `[单向-*]` | 正向 > 0 / 反向**正好 0** / 翻面后反向 0 / 串联 / 不节流 |
| `[过滤-白名单]` | 被挡气体正好 0，允许的 > 0，对照组不被误挡 |
| `[过滤-黑名单]` | 被拦气体正好 0（源节点也不减少），其余气体照常流 |
| `[智能-*]` | 压差/温度 × 大于/小于 四种组合该关时正好 0、该开时 > 0；NONE 恒开 |
| `[立即生效]` | 改完配置的下一 tick 就按新规则走（切指标、切 moreThan、切回 NORMAL） |
| `[铜体]` / `[导热]` | 流量比 = `copperDuctFlowScale`；铜体导热快于普通管 |
| `[单向-渲染]` | 两个独立表达式互证 6 方向 × 2 类型的模型选择 |
| `[partial]` | 4 个 `EDGE_*` 映射、模型文件存在、`CWClientSetup` 提前 init |
| `[NBT]` / `[同步]` | `save` 写的键 = `load` 读的键；`DuctEdges` + `setChanged/sendData` 都在 |
| `[语言]` | Java 里出现的每个 `vs_clockwork.*` 键在 en_us / zh_cn 都存在（含 6 个面名） |
| `[残留]` | 三个方块类名、4 类资源、lang 键、`CWGasIndex` 注册、Ponder 引用全部消失；`duct/edge` 目录不存在 |
| `[注册]` / `[规模]` | 三个 id 不在注册表；12 个相关类都 < 350 行 |

**为什么可信**：Python 版流量数学是**独立重写**的（不是调 Java），但运行前先断言 Java 源码里
的规则表达式还在（第一组），两边任何一侧漂移都会红；循环顺序、档位表、默认配置值全部
**从 Java 源码读**，不在脚本里抄常量。

---

## 7. 实机验证清单（自检覆盖不到的部分）

自检只验证纯逻辑与静态一致性，下面这些必须进游戏看：

### 7.1 切面循环
1. 放 3 个连成一排的导管，手持螺丝刀**右键中间那条的南面**：行动栏应依次出现
   `South: One-way (out…)` → `One-way (in…)` → `Filtered (any gas)` → `Smart (copper body, gate always open)` → `Normal`，
   每切一次接缝上的法兰模型应立刻变化（不是要等重新加载）。
2. 对着**北面**切一次，确认只有北面的模型变化、南面不受影响（面级，不是方块级）。
3. 对着**没有连接的那一面**点：行动栏提示 "That face is not connected"，方块状态不变。
4. 用扳手把某一面打成 `LEAK`，再用螺丝刀点那一面：应封好并提示 "Leak sealed"（旧版行为）。

### 7.2 单向（反向流量必须为 0）
1. 两个储罐 + `罐—导管—导管—罐`，把接缝两侧中**一侧**切成 `ONEWAY_FORWARD`；
   左边 2 atm、右边 1 atm：气只应向右流。
2. 把压力反过来（左 1、右 2）：**反向质量变化必须是 0**（不是"很慢"）；
   护目镜上的吞吐量应停在 0。
3. 把同一面切到 `ONEWAY_BACKWARD`：同一压差下应变成"不流通"（入口面不许出气）。
4. 观察接缝上的箭头：`ONEWAY_FORWARD` 时箭头应指向**气体流出的方向**（这一面的外侧）。

### 7.3 过滤
1. 网络里灌混合气（蒸汽 + 废气）。把接缝切成 `FILTERED`，潜行右键循环到 `Steam`：
   下游罐的废气质量**不得增长**，蒸汽照常通过。
2. 空手右键切到黑名单（`all except Steam`）：反过来 —— 废气能过、蒸汽不能过。
3. 潜行右键循环到"全部气体"（`Filtered (any gas)`）：两种气都恢复流动。

### 7.4 阈值
1. 切成 `SMART`，潜行右键一次 → `PRESSURE`（默认 200 kPa）：
   上游 < 200 kPa 时下游质量不动；加压到 300 kPa 应立刻开始流动。
2. 空手右键换档（200 → 500 kPa），确认门槛随之提高。
3. 潜行+空手切"小于"：同一压差下的通断应立刻反过来。
4. 潜行右键再切到 `TEMPERATURE`（默认 500 K）：用气体加热器把气烧到 600 K 应放行。

### 7.5 铜体
1. 同一对储罐、同一初始压差，分别用普通边与 SMART 边（指标 NONE）测 5 秒质量变化：
   SMART 那次应约为 **1.5 倍**。
2. 无压差、两侧 600 K / 300 K：SMART 边两侧温差应按 **15%/tick** 收敛；换普通边应约 10%/tick。

### 7.6 渲染 / 资源
1. 6 个方向各切一次单向：箭头方向都应符合 §4.2 的推导（这是唯一只能靠眼睛验收的点）。
2. 切到 `SMART` 时接缝上应是**铜色**模型、`FILTERED` 是带黑窗的 smart 模型（与旧版一致）。
3. 把接缝切成普通：边类型件消失，只剩接口圈（接机器时还有收口环）。
4. Ponder `duct` 场景：text_4..8 应逐条出现，且 text_4 演示的是**螺丝刀普通右键**（不是潜行）。

### 7.7 存档 / 联机
1. 配置好几个面后退出重进：类型与参数应完整恢复（`DuctEdges`）。
2. 联机时让另一名玩家看着接缝：A 切类型后 B 应立即看到模型变化（`sendData`）。
3. 把某个面切回普通后重进：不应残留旧的边类型件。
