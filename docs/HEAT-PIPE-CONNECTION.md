# 热管（`heat_pipe`）连接判定与传热：语义、证据与修复

> 2026-10-18。对应实机反馈"检查一下热管判定"：热管紧贴一台气体设备（护目镜 Pressure 1.57 MPa /
> Temperature 408.2 K），热管朝它伸出一根臂，但设备/热管温度都不动。
> 断言：`tools/selftest_heat_pipe_connection.py`（已挂 `tools/run_gates.ps1 -SelfTest`）。
> 几何/UV 侧见 `docs/HEAT-PIPE-MODEL.md`；本文只谈**连接与传热**。

## 0. 结论（先给答案）

1. **气体设备应当连接，也应当传热。** 热管的语言文件写的是
   `Conducts heat between neighboring nodes without moving gas.`——设计意图是"在相邻**节点**之间
   导热、不搬气体"，不是"只有热管之间能导热"。
2. 因此把 `HeatNode` 扩大到**所有气体节点**（`GasNode extends HeatNode`），热管在**画了臂的面**上
   与邻居做完全混合换热。**臂 = 热接触**：画臂就传热，不画臂就不传热；用扳手关掉一面 = 断开这一面的
   热路（与资源里 6 个连接布尔逐面一致）。
3. 热管仍然**不搬气体**：`gasNodeActive=false`、`gasConnected=false`、`isGasPipe=false`，
   并且 `GasNetworks#nodeAt` 不再把未注册的 inactive 节点当气体节点，气体不会进热管、也不会穿过它。

## 1. 旧版语义与现状的差异清单

### 1.1 1.20.1 树里没有热管实现（先纠正前提）

| 证据 | 行号 | 说明 |
|---|---|---|
| `Clockwork-1.20.1-main/common/src/main/resources/assets/vs_clockwork/blockstates/heat_pipe.json` | 全 334 行；30 条 `"model"`（第 5、16、27… 行）；第 7 行起是 `when` | 有 6 个连接布尔的 multipart，**没有** `heat` 属性（`"heat":` 出现 0 次） |
| 同目录 `models/block/heat_pipe/` | **目录不存在** | blockstate 引用的 `block/heat_pipe/lu_x` 等 30 个模型在 1.20.1 树里根本不存在 |
| `.../textures/block/heatpipe_{core,connection,rim}*.png` | 18 张贴图 | 只有贴图，没有模型/方块实现 |
| `.../blockstates/straight_duct.json` | 4、9、12 行 | 曾引用 `block/heat_pipe/straight`，同样缺模型 |
| `.../content/logistics/gas/storage/heatsink/HeatsinkBlock.kt` | 1–4 行（空类） | 唯一与"热"沾边的"方块"是空壳 |
| `Clockwork-1.20.1-main/.gitmodules` | 1–3 行 | 气体/热逻辑在 Kelvin 子模块里；本仓库里该子模块目录为空，无法引用其行号 |

结论：**1.20.1 没有 `HeatPipeBlock`/`HeatNode`/`IHeatNode`/`HeatExchanger` 的 Java 实现**。
真正的参考实现是官方 **0.6.0（1.21.1）** 的 `vs_clockwork-0.6.0-1.21.1.jar`
（`_refs/baseline_vs_clockwork_0.6.0-1.21.1.jar`，反编译命令见附录 A）。

### 1.2 官方 0.6.0 的实现（反编译行号）

| 类/方法 | 行号 | 行为 | 问题 |
|---|---|---|---|
| `HeatPipeBlock#getConnection` | 56–62 | 相邻热管看对方连接位；否则**只要邻居是 `IGasNodeBlock` 就返回 `gasConnectable`** | 画臂只要求"是气体方块"，不要求"能换热" |
| `HeatPipeBlock#gasConnectable` | 98–100 | 返回自己的连接位 | 邻居（导管）据此画接口臂 |
| `HeatPipeBlockEntity#tick` | 21–47 | 每刻遍历 6 面 | — |
| 同上 | **28** | `if (!this.gasConnected(dir)) { …换热… }` | **反向跳过**：`gasConnected` 读的是自己的连接位（53–56），于是"画了臂的面"被跳过；两根热管接在一起 = 两面都跳过 = 0 换热 |
| `HeatPipeBlockEntity#gasNodeActive` | 49–51 | `false` | 自身 tick 不注册；但挡不住别人的 BFS |
| `HeatPipeBlockEntity#gasConnected` | 53–56 | 返回连接位 | 连接位是"结构/热接触"，不是"气体可过" |
| `HeatPipeBlockEntity#isGasPipe` | 58–60 | `false` | 正确（热管不算管道） |
| `HeatNode` | 7–27 | 温度/热容/位置/写温度 | 全 jar **只有 `HeatPipeBlockEntity` 一个实现**（扫描 jar：引用 `heatpipe/HeatNode` 的 class 只有 `HeatNode`、`HeatPipeBlockEntity`） |
| `assets/vs_clockwork/lang/en_us.json` | `block.vs_clockwork.heat_pipe.function` | "Conducts heat between neighboring nodes without moving gas." | 官方文案证明"邻居节点"才是设计意图 |

### 1.3 差异清单（逐条）

1. **连接对象**：官方"任何 `IGasNodeBlock`"；1.20.1 无实现。现状与官方同形，但官方机器都不实现
   `HeatNode`，所以"连得上"≠"换得了热"。
2. **传热路径**：官方只有热管↔热管，且因第 28 行的反向跳过，**连接位为 true 的热管之间反而不换热**；
   热管↔气体机器/导管/储罐完全没有路径。旧版 Kelvin 的边导热
   （`DuctNetworkUtils.configureMetalDuctEdge` → `thermalConductivityMultiplier`）只作用于气体网络内部的边，
   与热管无关；1.20.1 树里也没有 `HeatExchanger`/`IHeatNode` 可引用。
3. **热管是否参与气体网络**：官方声明 `gasNodeActive=false`、`isGasPipe=false`，但 `gasConnected` 返回
   连接位，且 `GasNetworks#nodeAt` 在注册表查不到时会回落到方块实体——于是热管会被 BFS 当成气体节点，
   气体能灌进热管、甚至形成"导管—热管—导管"的旁路。**这是语义冲突，不是"不搬气体"**。
4. **热源/热汇**：官方没有把气体加热器/煤炉/斯特林/燃气机接入热管。四台机器的热量只存在于各自
   `GasMix` 里，热管读不到。
5. **Create 加热器/煤炉/蒸汽机**：官方/1.20.1 都没有让热管与 Create 的 blaze burner / 蒸汽机直接耦合；
   热管只认识 `IGasNodeBlock`（本模组的气体方块）。本次维持这个边界：不碰 `content/contraptions/**`，
   也不把 Create 方块硬塞进气体网络。

## 2. 判定结论（截图场景）

- 截图里的"气体设备"若是**气体节点**（导管 / 储罐 / 气体加热器 / 煤炉 / 斯特林 / 燃气机 / 压缩机 / …），
  它现在**能换热**：`GasNode` 继承 `HeatNode`，热容 = 气体热容 + 管壁，温度 = `GasMix` 温度。
  所以**保留连接臂，并让它真正传热**（不是"仅结构连接"）。
- 若邻居**不是气体节点、也不是热节点**（石头、机器外壳等），`getConnection` 返回 false：**不画臂**。
  这种情况画臂只会重演"臂在、温度不动"。
- 若邻居是气体方块但方块实体不是 `HeatNode`（当前不存在，留给以后），同样不画臂——判据由
  `HeatNode.at` 兜底，不靠"方块类型看起来像"。

**为什么选"臂 = 热接触"而不是"画臂 + 护目镜写仅结构连接"**：

1. 热管的资源本来就是 6 个连接布尔逐面出臂（见 `HEAT-PIPE-MODEL.md` §2），臂是玩家唯一能读的连接反馈；
   用它表达"结构接触"、再在护目镜里补一句"不传热"，等于让最显眼的视觉信号说谎。
2. 气体设备的热量就在 `GasMix` 里，接上 `HeatNode` 的成本极低，没有理由让设备"连而不换"。
3. 统一判据后，**扳手**的语义也干净：关掉一面 = 同时断开几何与热路，不需要记两套规则。

**为什么保留端口方向（加热器/斯特林/燃气机只在各自轴向+端口画臂）**：
连接矩阵与官方逐面一致（现有存档里的臂不会突然多出来/消失），而"画了臂的面都传热"已经满足一致性；
把热管接到机器非端口的外壳上属于新增能力，容易让"为什么这里能连、那里不能连"再次变成玄学。若以后
需要"贴外壳也导热"，改 `getConnection` 一处 + 本文件矩阵即可。

## 3. 修法（代码位置）

| 文件 | 改动 |
|---|---|
| `content/logistics/gas/heatpipe/HeatNode.java` | 重写语义注释：谁实现、四个方法的口径、为什么位置必须是方块实体自己的坐标 |
| `content/logistics/gas/GasNode.java` | `GasNode extends HeatNode`，给出 4 个默认实现（温度=`GasMix`，热容=`nodeHeatCapacity()`，位置=`gasState().hostPos()`，写温度→`gasChanged()`） |
| `content/logistics/gas/GasNodeBlockEntity.java`、`GasKineticNodeBlockEntity.java` | 文档注明所有子类（22 个气体方块）因此都是热节点；无需逐个机器改 |
| `content/logistics/gas/heatpipe/HeatPipeBlock.java` | `getConnection`：热管互连看对方位；其余邻居必须 `HeatNode.at(...) != null`，气体方块还要 `gasConnectable(...)`；非气体热节点任意面。扳手"打开"用同一套判据（相邻热管除外，它会在 `neighborChanged` 跟进），"关掉"永远允许 |
| `content/logistics/gas/heatpipe/HeatPipeBlockEntity.java` | `tick` 只处理 `DIR_TO_CONNECTION` 为 true 的面；删除 `if (gasConnected(dir)) continue`；`gasConnected` 恒 false；清掉旧版本误灌进热管的气体；移除重复的 `HeatNode` 覆写；护目镜显示"几面在导热 + 当前档位" |
| `content/logistics/gas/GasNetworks.java` | `nodeAt` 的方块实体回落：控制器不存在且 `!gasNodeActive()` → null（热管/未注册成员不是网络节点） |

### 3.1 换热公式与守恒

对相邻两侧热容 C₁、C₂、温度 T₁、T₂：

```
Q = ΔT · C₁C₂ / (C₁ + C₂)          // 串联热容；ΔT = T₁ - T₂
T₁' = T₁ - Q/C₁ ,  T₂' = T₂ + Q/C₂
```

即两者到达按热容加权的平衡温度；`C₁·T₁ + C₂·T₂` 在一步内精确不变。它等价于
`GasNetwork#conduct` 取 `k = 1`。取 1 而不是普通金属边的 0.1 的理由：热管是专门的"高导热"部件，
自身热容只有管壁 44.9 J/K；k=0.1 时一条 10 节链的远端几乎收不到热，玩家感受不到"热管"。大热容邻居
（满罐气体）仍升温很慢，因为 Q 受 `C₁C₂/(C₁+C₂)` 限制，不会一 tick 跳到平衡。

### 3.2 为什么不会被气体网络"借道"

两道独立保证：

1. **节点资格**：热管 `gasNodeActive=false`，`GasNetworks#nodeAt` 对它返回 null，BFS 根本到不了它；
2. **边资格**：即使有人绕过 `nodeAt`，热管 `gasConnected` 恒 false，`GasNetwork` / `GasNetworks#collect`
   的两侧连通判定也不会成立。

热管方块自己的 `gasConnectable` 仍返回连接位——那是给**邻居导管画接口短管**用的（否则两根管子之间
会留 3px 空隙），与"气体能不能过"是两件事，已在 `HeatPipeBlock#gasConnectable` 注释中写明。

## 4. 连接矩阵（热管这一侧看出去）

| 邻居（`dir` 从热管指向它） | 连接/画臂 | 传热 |
|---|---|---|
| 热管，对方朝向本方的位 = true | 6 面 | 是 |
| 热管，对方朝向本方的位 = false | 不连 | 否 |
| 气体导管 / 直管 / 滤管 / 泵 / 阀 / 红石管 / 储罐 / 压缩机 / 进气口 / 排气口 / 喷嘴 / 推进器 / 软管口 / 背罐 | 各自 `gasConnectable`（多数 6 面） | 是 |
| 气体加热器 `FACING=north` | 自身端口 = north/south/down；**从热管看** = south/north/up | 是 |
| 斯特林机 `FACING=east` | east/west（轴向） | 是 |
| 燃气机 `AXIS=y` | up/down（轴向） | 是 |
| 煤炉 | 6 面（默认 `IGasNodeBlock`） | 是 |
| 非气体热节点（预留） | 6 面 | 是 |
| 石头 / 空气 / 非热节点 | 不连 | 否 |

注意加热器那一行的"反转"：`getConnection` 把 `dir.getOpposite()` 传给邻居的 `gasConnectable`，
加热器的端口在它自己的 `down` 面，所以热管在它**上方**才连得上。自检 `[C1]` 覆盖了这个镜像。

## 5. 自检（`tools/selftest_heat_pipe_connection.py`）

134 条断言，纯逻辑，不启动游戏：

| 项 | 内容 |
|---|---|
| `[C1]` | 11 类邻居 × 6 面连接矩阵（含端口方向镜像、非热节点否决、石头否决） |
| `[C2]` | 热管互连双向性：任一侧扳手关掉，另一侧重算后也断开；扳手"打开"只允许可导热的邻居（相邻热管除外，避免死锁） |
| `[C3]` | 8 节热管链：600 tick 后温差 < 0.01 K，`ΣC·T` 误差 < 1e-9 相对，不越出初始温度区间 |
| `[C4]` | 850 K 大热源 → 5 节热管 → 300 K 大热汇：第一刻方向正确、源降温、汇升温、2000 tick 全程守恒 |
| `[C5]` | 气体隔离：热管 `nodeAt=None` / `gasConnected=false` / `isGasPipe=false`；导管—热管—导管不搬气；同几何下热量仍能通过（对照：换回导管时气体能过） |
| `[C6]` | 源码规则：`HeatNode.at` 判据、`DIR_TO_CONNECTION` 门控、三个 false 开关、`GasNode extends HeatNode`、`nodeAt` 的 inactive 过滤、四台机器的继承、三种 `gasConnectable` 端口表达式 |

跑法：

```bash
python tools/selftest_heat_pipe_connection.py
# 或随全量守门：
powershell -File tools/run_gates.ps1 -SelfTest
```

## 6. 需实机确认

1. **热管 → 煤炉/加热器**：贴上去应出现臂；煤炉烧到 850 K 后热管在 1–2 秒内明显升温（外观档位从
   cool 逐档变红），护目镜看热管显示 `conducting N face(s), warm/hot/...`。
2. **热管 → 斯特林/燃气机**：把热管从煤炉引到机器，机器的 Temperature 应上升、效率应从 0% 起跳；
   这验证"机器是热汇"。
3. **热管链**：10 节以上仍能在一小段时间内把远端加热（不应出现"只有第一节热"）；用扳手关掉中间一面，
   远端应停止升温（臂与热路同时断开）。
4. **截图原场景**：那台 1.57 MPa / 408.2 K 的设备若是气体节点，臂应保留且设备温度开始向热管温度靠拢
   （不再"臂在、不动"）。若是石头/非气体方块，臂应消失。
5. **气体隔离**：热管串在两根导管之间时，两侧压力**不应**通过热管均衡（气不搬），但温度应能过去；
   把热管换成普通导管后压力才均衡（对照）。
6. **扳手**：对热管某一面用扳手，臂消失的同时该面停止换热；对可导热的邻居再扳回应恢复。
   对石头/加热器非端口面扳"开"应被拒绝（不出现"有臂但不传热"的死臂）；两根热管都关掉后，
   对任一侧扳"开"应能重新连上（另一侧自动跟进）。

## 附录 A：官方 0.6.0 反编译（可复现）

```powershell
$env:JAVA_HOME="C:\Users\yql11\java\dragonwell-21.0.8.0.8+9-GA"
# 1) 从 _refs/baseline_vs_clockwork_0.6.0-1.21.1.jar 抽出 heatpipe/*.class
# 2) 用 gradle 缓存里的 vineflower 反编译：
java -cp <vineflower-1.10.1.jar> org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler `
  -e=<clockwork_unoffical/_cw_cp.txt 的内容> <抽出的class目录> <输出目录>
```

本次输出在 `_probe_tmp/heatpipe_decomp/out/...`（临时目录，可随时删除；结论以 jar 内 class 为准）。
