# 气体管道（Duct）系统专项审计

> 审计对象：`clockwork_unoffical/`（Clockwork 移植到 1.21.1 / NeoForge 21.1.251，Create 6.0.10 + Sable 2.0.6）
> 审计时间：2026-10-18 17:00 前后
> 源码快照：`src/main/java/.../logistics/gas/**`（并行任务 ①/② 正在改的文件已标注，本报告不修改）
> 存档快照：`...\.minecraft\versions\1.21.1-NeoForge_21.1.251\saves\新的世界`（最后一次退出写入 17:03:43；16:07 的自动保存与截图读数一致）
> 运行实例：13:05 构建的旧 jar 在 14:43 启动；17:02 并行任务把 `mods/` 换成当前源码构建，但游戏进程直到 17:03 退出前没有重载它。因此存档里的温度/质量是旧运行时写下的，不能用它反推“当前源码一定还会这样”。

## 0. 结论速查

| # | 问题 | 判定 | 关键证据 |
|---|---|---|---|
| 1 | 相邻气体管道 327.3 K / 338.8 kg，紧邻蒸汽提取器 1023.0 K / 108.7 kg | **不是连通性问题；是网络层的局部温度/热量传播问题**（并行任务 ② 的范围），叠加护目镜客户端同步阈值造成的质量读数轻微滞后 | 18 个节点 BFS 为 **1 个连通域**；两边压力都约 **38.3 MPa**；`m*T` 都在 **1.11×10^5** 量级（理想气体关系）；见 §1 |
| 2 | 连接判定 | 发现 **3 个真 bug**：红石导管缺 `gasConnectable`、排气口接错面、蒸汽提取器接错面；均已修。另有 3 个报告项：扳手可制造“假 SIDE”、热管可被 BFS 当成气体节点、进气口与动力轴抢同一面 | 见 §2、§7 |
| 3 | 渲染 | 通用导管的 6 面连接/泄漏/收口由 BER 覆盖，模型/贴图/语言齐全；直管/滤管/红石管的模型两端是固定接口，空气端也会“看起来接着”；边件/阀门的 UV 缩放有提示项 | 见 §4、§6 |
| 4 | 工具提示 | 读的是**当前方块自己的 `GasMix`**（储罐例外，读主方块），不是“网络代表节点”；压力会均衡，温度和当地质量不会自动变成全网一个值。缺少“本地节点/容量”标注，英文标签硬编码 | 见 §5 |
| 5 | 资源与注册 | `check_assets.py --jar` **0 个硬问题**；`map_textures.py --check` 通过；五类导管方块/物品/语言/贴图齐全 | 见 §6 |

---

## 1. 相邻方块读数不同：明确判定

### 1.1 先把截图里的两个方块定位出来

用 `tools/nbt_dump.py` 的 Reader 直接解析 `r.-1.0.mca`（区块 -2,0 / -1,0）里的 `block_entities`，再解码同一区块的 `block_states`：

* 截图中的气体管道：`(-16, -54, 13)`，`vs_clockwork:duct`，16:07 存档为 **327.203 K / 338.689 kg**。
* 截图中的蒸汽提取器：`(-17, -55, 14)`，`vs_clockwork:steam_generator`，16:07 存档为 **1023.017 K / 109.603 kg**。
* 两者**不是面相邻**：坐标差为 `(-1, -1, +1)`，是 3×3×2 堆叠体的一个**对角**。真正与管道面相邻的是 `(-16,-55,13)` 的提取器；真正与提取器面相邻的是 `(-17,-54,14)` 的管道。两块都在同一个 3×3 提取器 + 3×3 管道簇里。

现场结构（同一存档、同一区块）：

* `y=-57..-56`：3×3 的 `create:fluid_tank`（锅炉）。
* `y=-55`：3×3 的 `vs_clockwork:steam_generator`，全部 `face=floor`、`below=fluid_tank`。
* `y=-54`：3×3 的 `vs_clockwork:duct`；每个管道 `down=true`，并且水平相邻的管道 `north/south/east/west` 相互为 `true`。
* 另有一条 `x=-16, z=10..12` 的管道支路从下方 `y=-60` 的网络接上来，与 3×3 簇在 `(-16,-54,12)/(-16,-54,13)` 处相接。

### 1.2 判定：连通域成立

把存档里的方块状态按当前 `GasDuctBlockEntity.gasConnected`、`SteamGeneratorBlock.gasConnectable` 的规则做一次 BFS（脚本：`_probe_tmp/dump_scene.py` + 手工 BFS）：

* 3×3 管道 + 3×3 提取器 = **18 个节点，1 个连通域**（9 根管道 + 9 台提取器全部互相可达）。
* 每个 `duct.down=true` 都对应下方 `steam_generator` 的 `UP` 面；`getFacing(face=floor)=UP`，当前实现允许 `UP`（旧实现是 `dir != getFacing().getOpposite()` = 除 `DOWN` 外全通；本次已按旧版收紧到 `getFacing`，见 §7）。
* 水平管道之间双侧 `SIDE` 一致；没有对角、没有隔空连接。
* 把 §1.4 的 `z=12` 支路也算上，仍然是同一个连通域（多出支路节点）；它压力低是流量瓶颈，不是断连。

压力核对（`P = P0·(m/m0)·(T/T0)`，`P0=101325 Pa`，`T0=293 K`，管道/提取器容量均为 1.0 kg）：

| 方块 | 坐标 | 温度 K | 质量 kg | 压力 MPa |
|---|---|---:|---:|---:|
| duct | (-16,-54,13) | 327.20 | 338.69 | **38.30** |
| duct | (-17,-54,14) | 332.13 | 335.06 | **38.41** |
| duct | (-18,-54,13) | 331.00 | 335.58 | **38.42** |
| steam_generator | (-16,-55,13) | 1019.37 | 108.60 | **38.26** |
| steam_generator | (-17,-55,14) | 1023.02 | 109.60 | **38.28** |
| steam_generator | (-16,-55,14) | 1019.37 | 108.97 | **38.39** |

9 根管道 + 9 台提取器的 18 个节点，压力范围 **38.26–38.65 MPa**，最大相对差约 **1%**；`m*T` 范围约 **1.107×10^5–1.117×10^5**，相对差约 **0.6%**。这是“同一个连通域、压力已均衡”的典型特征。

反证：如果管道簇与提取器不相连，管道簇里 38 MPa 的高压从哪来？空气压缩机配置上限 `airCompressorMaxPressure=1,000,000 Pa`（约 1 MPa），泵满速也只有 `MAX_PUMP_PRESSURE=1,023,440 Pa`（约 1 MPa）；38 MPa 只能来自蒸汽提取器（其压力上限 `MAX_PRESSURE_PER_LEVEL · activeHeat`，activeHeat=18 时约 38.25 MPa）。因此 38 MPa 的管道气必然来自这 9 台提取器，连通性成立。

### 1.3 为什么温度/质量不同：网络层的局部温度，不是连接

* `GasMix` 是**每个节点一份**：`GasNodeState` 持有一个 `GasMix`（`GasNodeState.java:31`），`GasNodeTooltip` 读 `node.gasMix()`（`GasNodeTooltip.java:26-41`）。网络不是“一个共享气罐”，而是“一堆节点 + 边”。
* 质量搬运只带走“被搬走的那份气体”的焓；压力一旦均衡，质量流动停止，静止的管段之间不会因为“同处一个网络”自动变成同一温度。普通导管的 `GasLink.PIPE` 的 `conductivity` 是 **0**（`GasLink.java:46`）；`GasNetwork.handleConnection` 取两侧 link 的 `max`（`GasNetwork.java:109-120`），所以在旧行为下普通铁管没有额外管壁导热。
* 当前源码（并行任务 ②，16:58 之后）已经补了 `GasPhysics.thermalConductivity()` 和 `GasMix.nodeHeatCapacity()`，`GasNetwork.conduct` 里用 `max(gasThermalConductivity, linkConductivity)`（`GasNetwork.java:223-247`），默认 `gasThermalConductivity=0.1`（`CWServer.java:78-79`）。这是“每 tick 消掉 10% 温差”的强导热；如果它实际生效，327 K 的管道和 1023 K 的提取器不可能长期相差 700 K。旧实例（13:05 jar）的 `conduct` 字节码里虽然有这个方法，但实际生效值/调用路径不足，存档特征就是“压力均衡、温度不均衡”。
* 因此，**用户截图的现象首先应归因于“温度传播/管壁导热”这一层，而不是管道连接面**；这正是并行任务 ② 正在修的内容。用 17:02/17:13 之后的 jar 重启后，同一对节点的温度应明显趋同（见 §9 实机确认）。
* 另外，截图里的 `108.7 kg` 与服务器存档的 `108.9–109.6 kg` 差不到 1%，在客户端同步阈值内：`GasNodeState.syncIfChanged` 只有压力变化 >500 Pa、温度 >1 K、质量 >5% 才发包（`GasNodeState.java:142-161`），所以护目镜上的质量可能是上一次同步的旧值；温度 1023.0 也可能是 1015.6–1023.0 之间的某一帧。这个“客户端滞后”会放大“两个读数完全不同”的观感，但不能解释 700 K 的温差。

### 1.4 额外发现：`(-16,-54,12)` 的 6.94 MPa 是“流动瓶颈”，也不是断连

同一存档里 `(-16,-54,12)` 的管道是 **65.20 kg / 307.8 K / 6.94 MPa**，而它面相邻的 `(-16,-54,13)` 是 **338.83 kg / 326.9 K / 38.30 MPa**。两者 `north/south` 双侧都是 `true`，BFS 也在同一连通域。

原因不是连接，而是 `GasNetwork.transfer` 的单边流量上限：

```java
// GasNetwork.java:32-33
private static final double MAX_FLOW_RATIO = 0.25;
...
double limit = MAX_FLOW_RATIO * Math.min(a.gasCapacityKg(), b.gasCapacityKg()); // 1 kg 节点 => 0.25 kg/tick
```

`(-16,-54,13)` 是 38 MPa 的 3×3 簇，`(-16,-54,12)` 是下方 6.9 MPa 的支路/消费端；中间只有**一条**普通管道边，最大 0.25 kg/tick。高压侧 9 台提取器合计 0.9 kg/tick 的产气量远大于这一条边的出口能力，于是高压侧被提取器的压力上限顶住，低压侧由下游消费/泵维持，形成稳定的 31 MPa 压差。这是**简化流动模型的瓶颈**，不是“方块没连上”。如果用户下次把“相邻两块压力不同”当成连接 bug，需要先看这个压差是不是跨在一条高流量边上。

---

## 2. 连接判定逐项

### 2.1 判定链

1. `GasDuctBlock.getConnection` 逐面计算方块状态（`GasDuctBlock.java:127-142`）：
   * 当前面是 `LEAK/FORCED_OFF` → 保持（粘性）；
   * 邻居是普通导管 → 看邻居反面的 `SIDE/FORCED_OFF/LEAK`；
   * 邻居是 `IGasNodeBlock` → 调 `neighbor.gasConnectable(neighborState, dir.getOpposite())`。
2. `GasDuctBlockEntity.gasConnected(dir)` 只认自己这一面的 `SIDE`（`GasDuctBlockEntity.java:31-34`）。
3. BFS 两侧都问：`current.gasConnectedAt(endpoint, dir)` 与 `next.gasConnectedAt(nextPos, dir.getOpposite())`（`GasNetworks.java:283-294`）。
4. 至少一侧是管道才建边：`if (!a.isGasPipe() && !b.isGasPipe()) continue;`（`GasNetworks.java:296-299`），对应旧版 ponder 的“机器之间必须夹一段管道”。

方向语义统一为：**`dir` 永远是从当前节点指向邻居**；邻居查询时用 `dir.getOpposite()`。`GasDuctBlock.java:139`、`GasNetworks.java:293`、`GasNetwork.java:85` 都是这个约定。

### 2.2 每种邻居的结论

| 邻居 | 判定实现 | 允许的面 | 结论 |
|---|---|---|---|
| 普通导管 `duct` | `GasDuctBlockEntity.gasConnected` 读 6 个 blockstate | 逐面 `SIDE` | OK；`FORCED_OFF/LEAK` 不导通 |
| 直管 `straight_duct` | `StraightDuctBlock.gasConnectable` 57-59；实体 21-24 | 仅 `AXIS` 两面 | OK |
| 储罐 `duct_tank` | `DuctTankBlockEntity.gasConnectedAt` 236-245 / 331-333；端点 204-232 | 除同一罐体段外全通 | OK（多方块任意段可接）；见 §3 的端点残留风险 |
| 泵 `pump_duct` | `PumpDuctBlock.gasConnectable` 59-62；实体 35-40 / `gasLink` 43-57 | 仅 `FACING` 轴两面 | OK；两侧 bias 方向正确 |
| 加热器 `gas_heater` | `GasHeaterBlock.gasConnectable` 59-62 | `FACING` 轴两面 + `DOWN` | 与旧版一致；OK |
| 燃煤炉 `coal_burner` | 默认 `IGasNodeBlock` 全通 | 6 面 | 与旧版一致（旧版未覆盖 `canConnectTo`）；OK |
| 热管 `heat_pipe` | `HeatPipeBlock.gasConnectable` 200-204；实体 103-107 | 由 6 个连接布尔决定 | **报告项（P1/P2）**：`gasNodeActive=false` 只让 `GasNodeState` 跳过，但 `GasNetworks.nodeAt` 的 BE 回退仍能返回它，BFS 会把它加入组并可能把气体搬进去/穿过；见 §3.3 |
| 斯特林 `sterling_engine` | `SterlingEngineBlock.gasConnectable` 39-42；实体 90-94 | `FACING` 轴两面 | 与旧版一致；OK |
| 燃气机 `gas_engine` | `GasEngineBlock.gasConnectable` 65-69 | `AXIS` 两面 | 与旧版一致；OK |
| 阀门 `valve_duct` | `ValveDuctBlock.gasConnectable` 31-34；实体 60-64 / 66-73 | `getDuctAxis` 两面 | OK；开度只影响 `aperture` |
| 滤管 `filter_duct` | `FilterDuctBlock.gasConnectable` 64-67；实体 95-99 | `AXIS` 两面 | 连接 OK；过滤语义见 §3.4 |
| 红石导管 `redstone_duct` | **本次新增** `RedstoneDuctBlock.gasConnectable` 47-50；实体 48-50 | `AXIS` 两面 | **已修 P1**：原来缺 block 覆盖，邻居按默认“全连通”画出假连接 |
| 背罐 `gas_backtank` | `GasBacktankBlock.gasConnectable` 79-83 | 仅 `Y` 轴 | 与旧版一致；OK |
| 进气口 `intake` | `IntakeBlock.gasConnectable` 49-52；轴接口 43-47 | 仅背面 | **报告项（P1 可用性）**：背面同时是动力轴面，无法既供能又接管道 |
| 排气口 `exhaust` | **本次修正** `ExhaustBlock.gasConnectable` 90-92 | 仅 `FACING` 反面 | **已修 P1**：原 `dir != FACING` 会多接 4 个侧面 |
| 蒸汽提取器 `steam_generator` | **本次修正** `SteamGeneratorBlock.gasConnectable` 88-94 | 仅 `getFacing(state)`（floor=UP） | **已修 P1**：原 `dir != getFacing().getOpposite()` 会多接 4 个侧面 |
| 喷嘴 `gas_nozzle` | `GasNozzleBlock.gasConnectable` 52-56 | 仅背面 | 新设计，OK（旧版全通；本移植明确限定背面） |
| 软管接口 `hose_port` | `HosePortBlock.gasConnectable` 64-66 | 除软管面外 5 面 | 新设计，OK |
| 推进器 `gas_thruster` | `GasThrusterBlock.gasConnectable` 58-61 | `FACING` 轴两面 | 旧版全通；新设计收紧到轴，但**两面都通**（含喷口面），与类注释“进气管在背面”不完全一致；报告项 P2 |
| 边件 `oneway/smart/copper_duct` | `DuctFittingBlock.gasConnectable` 80-84；实体 41-44 | `FACING` 轴两面 | OK；流体方向闸见 §3.5 |
| 空压机/气动合成/创造气源 | 默认全通 | 6 面 | 与旧版一致；OK |

### 2.3 对角与“相邻但没接触”

* `GasNetworks.collect` 和 `GasNetwork.simulate` 都只遍历 `Direction.values()`（`GasNetworks.java:283`、`GasNetwork.java:68`），没有对角/斜线遍历。
* `GasDuctBlock.getConnection` 只读 `pos.relative(dir)` 的**直接邻居**（`GasDuctBlock.java:147-150`），隔一格空气不会连。
* 因此“对角线看起来相邻”不会误连；截图里的两块正是对角，实际通过面相邻的中间块连通。

### 2.4 “看起来连着、实际没连”的几种情况

1. **扳手强制 SIDE**：`GasDuctBlock.onWrenched` 只检查邻居是不是 `IGasNodeBlock`，不检查对方的 `gasConnectable`（`GasDuctBlock.java:194-213`）。例如对着蒸汽提取器的**侧面**扳手，会把管道这一面写成 `SIDE`，渲染出接口圈，但提取器侧面 `gasConnectable=false`，BFS 永远过不去。这个文件属于并行任务 ① 的范围，本次**只报告不改**。建议：扳手在设置为 `SIDE` 前调用 `nodeBlock.gasConnectable(neighborState, dir.getOpposite())`，不允许就 `FAIL`/保持 `NONE`。
2. **红石导管（已修）**：见 §7.1。
3. **直管/滤管/红石管的固定端模型**：它们的 blockstate 模型两端自带接口，旁边是空气时仍然“看起来接着”。这是观感问题，不是逻辑连接。
4. **`LEAK` 面**：`LEAK` 不导气但会画泄漏面；不要把它当成“已连接”。

---

## 3. 连通域与均衡

### 3.1 BFS 与端点表

* `GasNetworks.nodeAt`（78-96）：先查端点表，再查身份表，最后回退到方块实体。
* `GasNetworks.endpointsOf`（98-108）：按 `gasPos` 缓存 `gasEndpoints()`；储罐整罐一个身份、多个物理端点。
* `GasNetworks.register`（110-120）：把每个端点写进 `endpoints`；`unregister` 按对象身份删（129-133）。
* `GasNetworks.tick`（215-241）：按 `getGameTime()` 去重，`prune` 后对每个未访问节点 `collect`，再 `GasNetwork.simulate`。
* `GasNetworks.collect`（274-305）：两侧都问、按 `gasPos` 去重、至少一侧是管道；`MAX_NETWORK_NODES=1024` 防爆。
* `GasNetwork.simulate`（44-102）：按**节点身份**（`gasPos`）去重边，处理 `bIdentity > aIdentity` 的一侧，避免同一对边处理两次；多方块储罐的多个端点不会重复搬运。

结论：单方块管道/提取器的 BFS 没有断点；存档中的 3×3 簇确实是 1 个连通域。

### 3.2 多方块储罐的端点残留风险（报告项 P2）

`GasNetworks.structureChanged`（148-188）只清理**新 `scope` 范围内**的 `endpoints`，然后重新注册新端点。`prune`（254-272）只删除“节点已不在 `nodes` 里”的端点。若储罐从 3×3 缩成 1×1，旧主方块仍在 `nodes` 里，旧段落的 `endpoints` 映射不会被清掉；这些位置之后可能被 `nodeAt` 误认为仍是该罐的端点。正常拆除会触发相邻方块的 `neighborChanged`，所以多数情况下不会立刻出问题；但这是结构收缩/重建时的潜在幽灵连接。建议在 `structureChanged` 里用“旧端点列表 − 新端点列表”做差集清理，或在 `register` 前先移除该 `gasPos` 的所有旧端点。

### 3.3 热管：`gasNodeActive=false` 并没有让 BFS 忽略它（报告项 P1/P2）

* `HeatPipeBlockEntity` 注释说“`gasNodeActive()=false` 让气体模拟忽略它”（27-29 行），`GasNodeState.serverTick` 也确实在 85-90 行提前返回。
* 但 `GasNetworks.nodeAt` 在注册表找不到时，会回退到 `getBlockEntity(pos) instanceof GasNode`（90-95 行），因此热管仍会被 BFS 当作节点发现；`GasNetworks.collect` 没有检查 `gasNodeActive()`，于是“管道—热管”这一对会被加入 `out`，`GasNetwork.simulate` 会把气体搬进热管；热管另一侧如果也是管道，还会形成“管道—热管—管道”的旁路。
* 另外，热管的 `HeatNode.neighbor` 只把**其他热管**当热节点（`HeatNode.at` 只认 `HeatNode` 实现；全仓库只有 `HeatPipeBlockEntity implements HeatNode`），所以热管目前既不能把热传给相邻管道/机器，又可能被当成气体节点；这与类注释“不参与气体流动、用于连通不通风体的邻居”不一致。
* 建议（不在本次改动内）：在 `GasNetworks.collect`/`GasNetwork.simulate` 里显式跳过 `!gasNodeActive()` 的节点（网络层，属于并行任务 ② 的范围），或让 `HeatPipeBlockEntity` 不实现 `GasNode` 而只实现 `HeatNode`；同时让 `GasNodeBlockEntity` 实现 `HeatNode` 或给热管增加真正的端点换热。

### 3.4 滤管/智能边件的过滤语义（报告项 P2）

* `FilterDuctBlockEntity` 黑名单模式在**节点为空**时返回 `GasLink.PIPE`（82-93 行）：如果滤管里还没有任何气体，被禁止的气体可以先流进来，之后才按“第一种未禁止且已有的气体”放行；黑名单对“刚进入的第一股被禁气体”不生效。
* `GasNetwork.transfer` 的过滤选择是 `linkA.filter` → `linkB.filter` → `a.gasFilter` → `b.gasFilter`（172-175 行），两侧都有过滤时只取一侧，可能放行另一侧禁止的气体。
* 这两个都是简化模型的边界，建议在 `docs` 里写成已知限制，或改成“对每种气体分别做闸门”。

### 3.5 边件/泵/阀的方向闸

* `OneWayDuctBlockEntity.gasLink`（31-38 行）：`FORWARD` 时 `FACING` 面 `OUT`、背面 `IN`，`BACKWARD` 相反；`GasNetwork.transfer` 的 `forbidAToB/forbidBToA`（135-140 行）与 `GasLink.Flow` 的语义对得上。
* 泵的 `gasLink`（43-57 行）两侧 bias 相反，`transfer` 里 `biasB = -linkB.bias()`（131 行）把它翻到 a→b 坐标系；方向闸与泵压方向一致。
* 阀门的 `openness - 0.3125` 会被 `GasLink` 构造器钳到 0..1（`GasLink.java:51-55`），全关时 `aperture=0`，`handleConnection` 的 `if (aperture > 0)` 会跳过搬运；符合预期。

---

## 4. 渲染

### 4.1 通用导管：6 面覆盖

* `DuctRenderer`（`duct/client/DuctRenderer.java:60-96`）遍历 `Direction.values()`：
  * `LEAK` → `DuctPartials.LEAK`；
  * `SIDE` → `DuctPartials.CONNECTION`；
  * 邻居不是导管家族 → 再画 `DuctPartials.RIM`。
* `isDuctFamily`（98-110 行）覆盖普通/直管/滤管/泵/红石/阀/单向/智能/铜，所以“导管接导管”不画收口环，接机器/储罐才画。
* 模型/partial 注册：`DuctPartials`（19-39 行）只登记 `block/duct/{connection,leak,rim}`；`CWClientSetup` 在客户端启动时调用 `DuctPartials.init()`；`CWGasIndex.DUCT_BE` 注册 `DuctRenderer`（465-470 行）。顺序正确，不会 missingno。
* `GasDuctBlock.getShape`（244-263 行）按 6 位 mask 缓存碰撞箱，只有 `SIDE` 加手臂；`LEAK/FORCED_OFF` 只有核心，和渲染一致。
* `blockstates/duct.json` 只引用 `duct/core` 与 `duct/core_straight`，6 个状态的 core 覆盖在 `check_assets` 里为 0 问题；直管状态用 `core_straight` 避免 core+straight 重叠。

### 4.2 观感上的“假连接”

* **扳手 SIDE**（§2.4）和**红石导管缺 `gasConnectable`**（§7.1）都会让相邻管道画出 `CONNECTION`，但逻辑不通。
* 直管/滤管/红石管的 blockstate 模型两端固定有接口；旁边是空气时仍像接着。`straight_duct` 还直接复用 `block/heat_pipe/straight` 与 6 px 的 `heatpipe_*` 贴图，而普通导管是 10 px 的 `duct` 贴图，视觉粗细不一致。这些是观感项，不是缺模型。
* `duct` 的 `core` 是 10×10×10 的方块，接口/收口由 BER 补；如果 BER 没注册，会只剩“裸核心”。当前 `DUCT_BE` 注册了渲染器，正常。

### 4.3 编码问题（P3）

`DuctRenderer.java` 的类注释/方法注释显示为 `姘斾綋...` 这类乱码（UTF-8 被按 GBK 重编码的痕迹），但文件里没有中文用户字符串，**不影响编译/运行**；属于源码卫生问题。`HosePortBlock.java`、`IntakeBlockEntity.java` 等也有同类现象。建议后续统一用 UTF-8 重写注释；本次未动这些文件。

---

## 5. 工具提示（护目镜）

* 入口：`GasNodeBlockEntity.addToGoggleTooltip`（86-88 行）/`GasKineticNodeBlockEntity`（84-89 行）→ `GasNodeTooltip.add`。
* 读数来源：`GasNodeTooltip.add` 直接读 `node.gasMix()` 和 `node.gasCapacityKg()`（25-41 行）：
  * 压力：`mix.pressure(node.gasCapacityKg())`；
  * 温度：`mix.temperature()`；
  * 质量：`mix.totalMass()` 与各组分。
* **不是网络代表节点**：普通管道/机器读自己的 `GasMix`；只有 `DuctTankBlockEntity.gasMix()`/`gasCapacityKg()` 会委托给主方块（282-291 行），所以储罐任意一段显示整罐。护目镜标题是“Duct Info”，没有“local node”字样，用户很容易把“这个方块的气体”误读成“整张网络的气体”。
* 压力会均衡、温度不会自动均衡：这正是截图里“压力同为 38 MPa、温度差 700 K、质量差 3 倍”的原因。质量差不是“少了一部分气”，而是同压高温气体密度低：`m ∝ P/T`。
* 客户端同步：`GasNodeState.syncIfChanged` 只在压力 >500 Pa、温度 >1 K、质量 >5% 时 `sendData()`（142-161 行），`lazyTick` 每 10 tick 跑一次（`GasNodeBlockEntity` 35-36 行）。因此护目镜可能显示上次同步的值；本次截图里提取器 108.7 kg 与服务器 108.9–109.6 kg 的差就在 5% 内。
* 本地化/信息缺口：`Pressure/Temperature/Thermal Energy/Total` 都是硬编码英文；`vs_clockwork.gui.ductInfo.*`（en_us 463-471、zh_cn 439-447）已有翻译键却没被使用；旧版的 `volume` 行也没有移植，用户看不到“节点容量 1 kg”，更容易把质量差误解为连接问题。
* 建议：① 标题增加“Local node”或“Node (not network average)”；② 增加一行 `Capacity: 1.00 kg`/储罐总容量；③ 改用已有 lang 键；④ 需要“全网平均”的话单独加一行（并明确是平均还是代表节点）。

---

## 6. 资源与注册

### 6.1 五类导管资源

| 方块 | blockstate | 模型 | 物品模型 | 语言（en/zh） | 备注 |
|---|---|---|---|---|---|
| `duct` | `blockstates/duct.json`（multipart，core/core_straight） | `block/duct/*` + BER partial | `models/item/duct.json` | 有 | 6 面连接由 BER 补 |
| `straight_duct` | `blockstates/straight_duct.json`（axis 三变体） | `block/heat_pipe/straight` | `block/heat_pipe/item_straight` | 有 | 复用热管模型/6px 贴图，观感偏细 |
| `redstone_duct` | `blockstates/redstone_duct.json`（axis，power 省略） | `block/redstone_duct/block` | 有 | 有 | 1.21 变体省略属性=任意值；check_assets 通过 |
| `filter_duct` | `blockstates/filter_duct.json`（axis 三变体） | `block/filter/block` | 有 | 有 | 固定两端接口 |
| `valve_duct` | `blockstates/valve_duct.json`（12 变体） | `block/valve_duct/block_{horizontal,vertical}` | 有 | 有 | `DirectionalAxisKineticBlock` 的 facing×axis |

`check_assets.py --jar` 结果：

* [1] 缺 blockstate：0；[2] 缺 models/item：0；[3] 缺语言：0；[4] blockstate→模型链断裂：0；[7] 贴图键无法解析：0；[8] 贴图引用不存在：0；[9] png/mcmeta：0；[10] 非法资源路径：0；[11] 产物 jar 与源码资源树/结构：0；[12] blockstate 属性覆盖/条件合法性：0；[14]/[15] 属性读写/跨方块 state：0。
* 提示项（不计失败）：3 个旧版遗留未注册 blockstate（`afterblazer`/`balloon_encased_shaft`/`combustion_engine`）；5 条 UV 缩放提示，集中在 `copper_duct`/`oneway_duct`/`smart_duct`/`valve_duct` 的变体模型（并行任务 ① 的边件资源），切换模型时贴图可能跳变，属观感提示。
* `map_textures.py --check`：通过；工程树/产物 jar 没有缺图引用、没有缺失模型；官方树里的 `create:block/copper_plating`、`vs_clockwork:block/creative_generator` 缺图是旧版模型遗留，工程树已修，检查器明确标注“不计入失败”。

### 6.2 注册

* 方块/方块实体：`CWGasIndex` 里 `DUCT_BE`/`PUMP_DUCT_BE`/`VALVE_DUCT_BE`/`REDSTONE_DUCT_BE`/`STRAIGHT_DUCT_BE`/`FILTER_DUCT_BE` 等均有 BE 类型；`duct` 有 BER，泵/阀有各自 BER，直管/滤管/红石管用静态模型（无 BER 需求）。
* 物品：`models/item/*duct*.json` 均指向存在的父模型。
* 语言：en_us/zh_cn 均有 `block.vs_clockwork.*` 条目；`check_assets` [3] 为 0。

---

## 7. 实际改动

### 7.1 `redstone/RedstoneDuctBlock.java`：补 `gasConnectable`（P1，已修）

**为什么是 bug**：`RedstoneDuctBlockEntity.gasConnected` 只沿 `AXIS` 两面返回 true（48-50 行），但方块类没有覆盖 `IGasNodeBlock.gasConnectable`，默认实现对所有方向返回 true（`IGasNodeBlock.java:21-23`）。相邻普通导管的 `GasDuctBlock.getConnection` 调 `neighbor.gasConnectable(...)`（139 行）时就会把**侧面**写成 `SIDE`，渲染出接口圈；而 BFS 走到红石导管这一侧时 `gasConnected` 只认轴向，实际永远不通。这就是“看起来连着、实际没连”。

**改动**：在 `RedstoneDuctBlock` 增加：

```java
@Override
public boolean gasConnectable(BlockState state, Direction dir) {
    return dir.getAxis() == state.getValue(BlockStateProperties.AXIS);
}
```

文件：`src/main/java/org/valkyrienskies/clockwork/content/logistics/gas/redstone/RedstoneDuctBlock.java:47-50`。改动前 mtime 2026-10-11，非并行任务文件。

### 7.2 `exhaust/ExhaustBlock.java`：排气口只接背面（P1，已修）

**旧版证据**：`Clockwork-1.20.1-main/.../gas/exhaust/ExhaustBlock.kt:93-95` 的 `canConnectTo` 是 `if (direction != FACING.opposite) return false`。当前实现原来写成 `return dir != state.getValue(FACING);`，会把上/下/左/右四个侧面也接进网络；排气口从侧面“漏接”不仅不符合旧版，也会让相邻管道画出错误接口圈。

**改动**：

```java
@Override
public boolean gasConnectable(BlockState state, Direction dir) {
    return dir == state.getValue(FACING).getOpposite();
}
```

文件：`.../gas/exhaust/ExhaustBlock.java:90-92`。改动前 mtime 2026-10-11，非并行任务文件。

### 7.3 `generator/SteamGeneratorBlock.java`：提取器只接“背离锅炉”的一面（P1，已修）

**旧版证据**：`Clockwork-1.20.1-main/.../gas/generation/steam_generator/SteamGeneratorBlock.kt:65-68` 的 `canConnectTo` 是 `if (direction != getFacing(state)) return false`。`getFacing` 对 `FACE=FLOOR` 返回 `UP`，对墙挂/吊顶返回背离锅炉的那一面。当前实现原来写成 `return dir != getFacing(state).getOpposite();`，只排除了贴锅炉的一面，会多接通 4 个侧面。

**改动**：

```java
@Override
public boolean gasConnectable(BlockState state, Direction dir) {
    return dir == getFacing(state);
}
```

文件：`.../gas/generator/SteamGeneratorBlock.java:88-94`。改动前 mtime 2026-10-11，非并行任务文件。

### 7.4 没有改的东西

* **没有改** `content/logistics/gas/duct/**`（并行任务 ①，最近 mtime 16:14–16:36）、`GasNetwork.java`/`GasLink.java`/`GasMix.java`/`CWServer.java`（并行任务 ②，16:58–17:02）、`DuctRenderer.java`（16:14）。
* 没有改 `HeatPipeBlockEntity`（只报告）、`IntakeBlock`（只报告）、`FilterDuctBlockEntity`（只报告）、`GasDuctBlock.onWrenched`（只报告；文件在并行任务 ① 范围）。
* 没有改任何 blockstate/model/texture/lang；资源检查是只读。

### 7.5 构建

* `.\gradlew.bat compileJava`：BUILD SUCCESSFUL（48s）。
* `.\gradlew.bat build`：BUILD SUCCESSFUL（12s），产物 `clockwork_unoffical/build/libs/vs_clockwork-0.6.0-1.21.1.jar`，2026-10-18 17:13:09，8,216,066 字节。
* 安装：`latest.log` 最后写入 17:03，`javaw` 无进程，满足安装前置条件。`tools/install_jar.ps1` 检测到 `mods/` 已是同一份（SHA256 与产物一致），随后对同一文件的冗余覆盖被系统/OneDrive 文件句柄拒绝（脚本在 `File.Replace` 失败后的 `Remove-Item` 分支退出 1）。**内容本身已经是正确的**：`mods/vs_clockwork-0.6.0-1.21.1.jar` 大小为 8,216,066、mtime 17:13:09，SHA256 `11CB3A5F...F46D58` 与 `build/libs` 一致；`javap` 已核实三个修复都在安装 jar 里，`check_jar.py` 对该 jar 报 1675 条目 0 问题。没有在游戏运行时覆盖 jar，也没有再次强行删除被锁文件。

---

## 8. 守门（五关）

本次实际执行结果（2026-10-18 17:13–17:20）：

| 关 | 命令 | 结果 |
|---|---|---|
| [1/5] | `tools/check_data_refs.py` | **0 问题**（103 个注册 id、3 个配方类型、19 个音效事件、worldgen 全部对得上） |
| [2/5] | `tools/check_assets.py --jar` | 独立运行 **0 个硬问题**（见 §6；聚合脚本里的同一条命令因并行任务同时开了 4–5 个 `check_assets` 进程、CPU 竞争严重，到本报告截稿时仍未返回；不是失败项） |
| [3/5] | `tools/check_jar.py` | **0 问题**（1675 个条目，zip 结构 0 问题） |
| [4/5] | `tools/check_ponder.py` | **0 问题**（11 个场景 / 11 条注册，含 `duct` 场景；四类导管 id 均能解析） |
| [5/5] | `tools/check_render_coverage.py` | **0 问题**（BER/partial/RenderShape/物品模型/孤立子模型/kinetic 渲染器 7 项全 0） |
| 附加 | `tools/selftest_gas_heat.py` | **42 条断言，失败 0 条**（基础导热 0.1/tick、环境散热 0.005/tick） |
| 附加 | `tools/selftest_duct_variants.py` | **224 条断言，失败 0 条**（单向/智能/铜的流动、过滤、倍率、导热、旋转矩阵） |

结论：本次 3 个面判定改动不涉及数据/资源/渲染注册，相关关卡均为 0 问题；聚合脚本 `run_gates.ps1` 的 [2/5] 属于并行任务资源竞争导致的“未完成”，不是失败。若需要完整汇总，等并行任务结束后重跑一次 `tools/run_gates.ps1` 即可。

---

## 9. 需要实机确认的点

1. **用 17:13 之后的新 jar 重启**（旧 13:05 实例没有加载新代码）。看同一对 `(-16,-54,13)` / `(-17,-55,14)`：
   * 压力应仍然接近（约 38 MPa）；
   * 温度应在若干秒内明显趋同（并行任务 ② 的 `gasThermalConductivity=0.1` 生效的话）；如果仍然差 700 K，先检查 `config/vs_clockwork-server.toml` 是否生成了 `gasThermalConductivity=0.1`，再查 `GasNetwork.conduct` 是否真的被调用。
2. **红石导管**：把它横放，旁边贴一根普通管道，看管道侧面是否还画接口圈；修后应只在红石导管轴向画。
3. **排气口**：把管道贴在排气口的侧面，应不能再连接；贴背面才连。
4. **蒸汽提取器**：把管道贴在提取器侧面，应不能再连接；贴顶面（floor 放置）才连；3×3 现有结构的 `down=true` 不应受影响。
5. **热管**：在“管道—热管—管道”之间看气体是否被搬进热管（当前 BFS 可能搬）；同时确认热管是否能给相邻机器/管道传热（当前 `HeatNode` 只认热管，可能不传）。
6. **进气口**：确认动力轴与气体管道是否抢同一面；若确实无法同时供能/接管道，需要改成“除背面外可接”或调整轴面（报告项，未改）。
7. **渲染**：对普通导管逐面扳手开/关，检查 `CONNECTION`/`RIM`/`LEAK` 是否随状态变化；直管/滤管/红石管旁边留空气，确认“固定端模型”的观感是否可接受。
8. **多方块储罐**：搭 3×3→拆成 1×1→再接管道，确认没有幽灵端点/幽灵连接（§3.2）。

---

## 附录 A：证据文件/脚本

* 存档解析：`_probe_tmp/scan_gas_nbt.py`（列出所有 `vs_clockwork` 气体 BE 的 `GasMass/GasTemperature`）、`_probe_tmp/dump_scene.py`（解码 block_states，输出 3×3 场景）。
* 资源检查日志：`_probe_tmp/gas_check_assets.log`、`_probe_tmp/gas_map_textures.log`、`_probe_tmp/gas_gates.log`。
* 构建日志：`_probe_tmp/gas_compile.log`、`_probe_tmp/gas_build.log`。
* 旧版对照：`Clockwork-1.20.1-main/common/src/main/kotlin/org/valkyrienskies/clockwork/content/logistics/gas/**`。
