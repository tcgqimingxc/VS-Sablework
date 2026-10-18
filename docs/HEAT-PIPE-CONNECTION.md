# 热管（`heat_pipe`）连接判定与传热：语义、证据与修复

> 2026-10-18 第二次返工。对应实机反馈"单独放置的热管是一个满方块带十字/凹陷花纹，
> 不像 Create 的管道"与"检查一下热管判定"。
> 断言：`tools/selftest_heat_pipe_connection.py`（已挂 `tools/run_gates.ps1 -SelfTest`）。
> 几何/外观侧见 `docs/HEAT-PIPE-MODEL.md`；本文只谈**连接与传热**。

## 0. 结论（先给答案）

1. **连接架构照 Create 1.21.1 的 `FluidPipeBlock`**：`HeatPipeBlock extends
   net.minecraft.world.level.block.PipeBlock`，六个连接属性直接复用
   `PipeBlock.PROPERTY_BY_DIRECTION`（`north/east/south/west/up/down`），碰撞箱缓存也由
   `PipeBlock` 按 `super(3 / 16f, properties)` 生成（6px 管径）。判断邻居用
   `getConnection(...)`，写回状态用 `updateShape(...)`/`neighborChanged(...)`，
   放置初值用 `getConnectedState(...)`（= Create 的 `updateBlockState(...)` 的对应物）。
2. **判据统一为"臂 = 热接触"**：邻居必须是 `HeatNode`（所有气体节点经 `GasNode` 继承它），
   并且（若邻居是气体方块）自报这一面可连。画臂、传热、扳手"打开"共用同一谓词。
3. **修掉一个真实死锁**：两根新放的热管初始都是 `false`，而互连规则是"读对方朝向本方的位"，
   于是谁都等对方先变 `true`，永远连不上。现在放置时用 `forPlacement=true` 先把自己这侧置位，
   对方收到 `updateShape` 后跟着置位；稳态仍读对方的位，所以任一侧扳手关掉，两侧一起断开。
4. **扳手闭锁**：`updateShape`/`neighborChanged` 只重算**变化的那一面**（Create 的
   `updateBlockState` 重算所有面，因为它没有单面关断）。这样扳手关掉的面不会被无关的邻居更新
   重新打开；只有这一面的邻居再次变化时才重新判定。另外 `onPlace` 只在 `oldState` 不是热管
   （真正"从别的方块变成热管"）时做放置配对；HEAT 换档/扳手/连接位更新也会触发 `onPlace`，
   此时必须直接返回，否则每 20 tick 换档就会把闭锁冲掉。
5. **不伪造对端**：Create 的管道在只有一个连接时会自动补出对面试为 `true`（让单放的管道看起来
   像一段直管）。热管的臂同时是热接触，补假对端会画出一条不传热的臂。因此这里保留
   "属性 = 真实邻居"；单放时六个面都由 `cap_*` 端环封住（见 `HEAT-PIPE-MODEL.md`），
   外观不再是满方块。
6. 热管仍然**不搬气体**：`gasNodeActive=false`、`gasConnected=false`、`isGasPipe=false`，
   并且 `GasNetworks#nodeAt` 不把未注册的 inactive 节点当气体节点。

## 1. 证据：Create 1.21.1 的管道到底怎么写的

### 1.1 `FluidPipeBlock` 的继承与方法（`javap` 核实）

```powershell
$env:JAVA_HOME="C:\Users\yql11\java\dragonwell-21.0.8.0.8+9-GA"
& "$env:JAVA_HOME\bin\javap.exe" -p -classpath clockwork_unoffical\libs\create-1.21.1-6.0.10.jar `
  com.simibubi.create.content.fluids.pipes.FluidPipeBlock
```

关键输出：

```text
public class com.simibubi.create.content.fluids.pipes.FluidPipeBlock
        extends net.minecraft.world.level.block.PipeBlock
        implements ... IBE<FluidPipeBlockEntity>, ... {
  public static boolean canConnectTo(BlockAndTintGetter, BlockPos, BlockState, Direction);
  public static boolean shouldDrawRim(BlockAndTintGetter, BlockPos, BlockState, Direction);
  public static boolean isOpenAt(BlockState, Direction);
  public BlockState updateBlockState(BlockState, Direction, Direction, BlockAndTintGetter, BlockPos);
  public BlockState updateShape(BlockState, Direction, BlockState, LevelAccessor, BlockPos, BlockPos);
  public void neighborChanged(BlockState, Level, BlockPos, Block, BlockPos, boolean);
  protected void createBlockStateDefinition(StateDefinition$Builder<Block, BlockState>);
  ...
}
```

要点：

* **六个布尔属性来自原版 `PipeBlock`**，`createBlockStateDefinition` 里由子类
  `builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN, ...)` 声明；`PipeBlock` 的构造函数
  `PipeBlock(float apothem, Properties)` 只生成 64 个 AABB 形状缓存。
* 连接判定入口是 `canConnectTo(...)`；状态写回是 `updateBlockState(state, preferredDirection,
  ignore, world, pos)`。它**重算所有面**，并在"只有一个连接"时把对面试为 `true`
  （单放管道因此是一段直管）。
* `shouldDrawRim(...)` 决定什么时候在管口画收口环：邻居不是管道（或不可连）时画。
* **没有 `PipeRenderer`/`PipeModel`/`AllPartialModels.PIPE_*`**。`jar tf` 里 fluid pipe 只有
  `FluidPipeBlockEntity`、`FluidPipeBlockRotation`、`TransparentStraightPipeRenderer`（直管玻璃），
  没有管道 BER；渲染完全由 `assets/create/blockstates/fluid_pipe.json`（30 条 multipart）+
  `assets/create/models/block/fluid_pipe/{core_*,u_*,lu_*,...}.json` 完成。
  `PipeAttachmentModel` 只给"被外壳包住的管道/支架"用，不是普通管道。

### 1.2 官方 0.6.0 的 `HeatPipeBlock`（基线）

`_refs/baseline_vs_clockwork_0.6.0-1.21.1.jar` 里的 `HeatPipeBlock` 是
`extends Block`（不是 `PipeBlock`），自己声明六个 `BooleanProperty`、自己维护
`CORE/ARMS/SHAPE_CACHE`，`getConnection` 对相邻热管读对方朝向本方的位。它没有
`FluidPipeBlock` 的 `forPlacement` 概念，所以两根新热管会卡在双 `false`——这就是本移植修掉的死锁。
它的 `blockstates/heat_pipe.json` 是 30 条平面形状（`lu_x/ru_x/...`），对 12 个 4 通状态缺臂，
且同一个状态会命中 2~3 个模型（核心/臂叠画）；`HEAT-PIPE-MODEL.md` §1 有逐状态枚举证据。

官方 `HeatPipeBlockEntity#tick` 还有一个反向跳过：`if (gasConnected(dir)) continue`，
而 `gasConnected` 读的就是连接位，于是"画了臂的面反而不换热"。本移植在
`HeatPipeBlockEntity#tick` 里改成"只在连接位为 true 的面换热"，并让所有气体节点实现
`HeatNode`（`GasNode extends HeatNode`），保证"臂在、温度也会动"。

## 2. 连接矩阵（热管这一侧看出去）

判据（`HeatPipeBlock#getConnection`）：

1. 相邻热管：稳态读对方朝向本方的连接位；放置时（`forPlacement=true`）先按"可连"算。
2. 其余邻居：`HeatNode.at(level, neighbourPos) != null`，否则不连。
3. 若邻居是 `IGasNodeBlock`：还要 `gasConnectable(neighbourState, dir.getOpposite())`；
   非气体的热节点则任意面都算。

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

注意加热器那一行的"反转"：`getConnection` 把 `dir.getOpposite()` 传给邻居的
`gasConnectable`，加热器的端口在它自己的 `down` 面，所以热管在它**上方**才连得上。
自检 `[C1]` 覆盖了这个镜像。

## 3. 修法（代码位置）

| 文件 | 改动 |
|---|---|
| `content/logistics/gas/heatpipe/HeatPipeBlock.java` | 改为 `extends PipeBlock`，复用 `PROPERTY_BY_DIRECTION`/形状缓存；`getConnection`/`canConnect`/`getConnectedState`/`updateConnections`/`updateShape`/`neighborChanged`/`onPlace` 按 Create 的入口重写；放置配对（`forPlacement`）修死锁；`updateShape`/`neighborChanged` 只重算变化面（扳手闭锁）；`onPlace` 只在 `oldState` 不是热管时配对（HEAT 换档/扳手更新不重算）；`rotate`/`mirror` 重排六位；扳手开断与 `canWrenchOn` 保留并复用同一谓词 |
| `content/logistics/gas/heatpipe/HeatNode.java` | `at(...)` 参数从 `Level` 放宽到 `BlockGetter`：`updateShape(LevelAccessor)` 里可以直接判定邻居（`LevelAccessor` 不是 `Level`） |
| `content/logistics/gas/heatpipe/HeatPipeBlockEntity.java` | 换热逻辑不变（只在连接位 true 的面做完全混合）；仍 `gasConnected/gasNodeActive/isGasPipe = false` |
| `content/logistics/gas/GasNetworks.java` | 之前已修：`nodeAt` 的方块实体回落只在 `gasNodeActive()` 时返回节点 |
| `blockstates/heat_pipe.json`、`models/block/heat_pipe/**`、`tools/gen_heat_pipe_*.py` | 78 条 multipart、79 个模型；未连接面加 `cap_*` 端环，见 `HEAT-PIPE-MODEL.md` |

### 3.1 放置配对（修双 `false` 死锁）

```text
A、B 相邻，初始都 false。
B 的 getStateForPlacement/onPlace 用 forPlacement=true：
    B 朝向 A 的面 = true（不读 A 的 false）
设置 B 的状态触发 A 的 updateShape/neighborChanged：
    A 朝向 B 的面 = B 的对应位 = true
设置 A 的状态触发 B 的 updateShape：
    B 朝向 A 的面 = A 的对应位 = true
稳态：两侧都读对方的位；任一侧扳手关掉 → 对方 updateShape 跟着置 false。
```

### 3.2 换热公式与守恒

对相邻两侧热容 C₁、C₂、温度 T₁、T₂：

```
Q = ΔT · C₁C₂ / (C₁ + C₂)          // 串联热容；ΔT = T₁ - T₂
T₁' = T₁ - Q/C₁ ,  T₂' = T₂ + Q/C₂
```

即两者到达按热容加权的平衡温度；`C₁·T₁ + C₂·T₂` 在一步内精确不变。它等价于
`GasNetwork#conduct` 取 `k = 1`。取 1 而不是普通金属边的 0.1 的理由：热管是专门的"高导热"部件，
自身热容只有管壁 44.9 J/K；k=0.1 时一条 10 节链的远端几乎收不到热，"热管"名不副实。
大热容邻居（满罐气体）升温仍很慢，因为 Q 受串联热容 C₁C₂/(C₁+C₂) 限制，不会一 tick 跳到平衡。

**两根热管之间还要"两侧都同意"**：`HeatPipeBlockEntity#tick` 在对方是 `HeatPipeBlockEntity`
时会读对方朝向本方的连接位，false 就跳过。正常放置/扳手会让两侧同步；这个检查用来防
"区块半加载、命令改状态"等瞬态下的单边灌热——不能出现"这一面已经关掉，却还在被对面加热"。

### 3.3 为什么不会被气体网络"借道"

两道独立保证：

1. **节点资格**：热管 `gasNodeActive=false`，`GasNetworks#nodeAt` 对它返回 null，BFS 根本到不了它；
2. **边资格**：即使有人绕过 `nodeAt`，热管 `gasConnected` 恒 false，`GasNetwork` / `GasNetworks#collect`
   的两侧连通判定也不会成立。

热管方块自己的 `gasConnectable` 仍返回连接位——那是给**邻居导管画接口短管**用的（否则两根管子之间
会留 3px 空隙），与"气体能不能过"是两件事，已在 `HeatPipeBlock#gasConnectable` 注释中写明。

## 4. 自检（`tools/selftest_heat_pipe_connection.py`）

156 条断言，纯逻辑，不启动游戏：

| 项 | 内容 |
|---|---|
| `[C1]` | 11 类邻居 × 6 面连接矩阵（含端口方向镜像、非热节点否决、石头否决） |
| `[C2]` | 热管互连：放置配对（含"没有配对就会双 false"的对照）、任一侧关断双向、扳手闭锁（只重算变化面；对照：重算所有面会冲掉闭锁）、扳手打开准入、两侧都同意才换热（防单边灌热） |
| `[C3]` | 8 节热管链：600 tick 后温差 < 0.01 K，`ΣC·T` 误差 < 1e-9 相对，不越出初始温度区间 |
| `[C4]` | 850 K 大热源 → 5 节热管 → 300 K 大热汇：第一刻方向正确、源降温、汇升温、2000 tick 全程守恒 |
| `[C5]` | 气体隔离：热管 `nodeAt=None` / `gasConnected=false` / `isGasPipe=false`；导管—热管—导管不搬气；同几何下热量仍能通过（对照：换回导管时气体能过） |
| `[C6]` | 源码规则：`extends PipeBlock`、`PROPERTY_BY_DIRECTION`、`HeatNode.at(BlockGetter)`、`getConnection/updateShape/updateConnections`、`updateShape` 只重算变化面、`onWrenched` 只翻转点击面、`onPlace` 在 `oldState` 仍是热管时直接返回、热管两侧都同意才换热、三个 false 开关、没有 `SHAPE_CACHE`/BER/partial |

跑法：

```bash
python tools/selftest_heat_pipe_connection.py
# 或随全量守门：
powershell -File tools/run_gates.ps1 -SelfTest
```

## 5. 需实机确认

1. **单放**：应看到六个 `cap_*` 端环围住 6x6x6 核心（六通法兰），不是"满方块 + 十字花纹"；
   六个端环都应朝向各自的面，不能有面漏画或错位。
2. **直通**（东-西两连接）：两根管臂 + 两端边界收口环，另外四个面是端环；两管相接处是
   "双法兰背靠背"（本方案不区分"邻居是不是热管"，见 `HEAT-PIPE-MODEL.md` §3 的取舍）。
3. **两根新热管相邻放置**：应自动互连（放置配对）；任一侧扳手关掉，两侧一起断开；
   再扳回，两侧一起连上。
4. **热管 → 煤炉/加热器**：贴上去应出现臂；煤炉烧到 850 K 后热管在 1–2 秒内明显升温
   （外观档位从 cool 逐档变红），护目镜显示 `conducting N face(s), warm/hot/...`。
5. **热管 → 斯特林/燃气机**：把热管从煤炉引到机器，机器的 Temperature 应上升、效率应从 0% 起跳。
6. **热管链**：10 节以上仍能在一小段时间内把远端加热；用扳手关掉中间一面，远端应停止升温
   （臂与热路同时断开）。
7. **气体隔离**：热管串在两根导管之间时，两侧压力**不应**通过热管均衡（气不搬），但温度应能过去；
   把热管换成普通导管后压力才均衡（对照）。
8. **扳手闭锁**：关掉一面后，在热管另一侧放/挖方块，被关掉的面不应自己重新打开；
   只有这一面的邻居再次变化（或再扳一次）才重新判定。让热管温度跨过档位阈值（外观换档）
   后，闭锁也必须保持——HEAT 换档同样会走 `onPlace`，已用 `oldState` 守卫挡住。

## 附录 A：Create 管道的 `javap`/资源证据（可复现）

```powershell
$env:JAVA_HOME="C:\Users\yql11\java\dragonwell-21.0.8.0.8+9-GA"
# 类签名/方法
& "$env:JAVA_HOME\bin\javap.exe" -p -classpath clockwork_unoffical\libs\create-1.21.1-6.0.10.jar `
  com.simibubi.create.content.fluids.pipes.FluidPipeBlock
# 确认 1.21.1 没有 PipeRenderer/PipeModel/AllPartialModels.PIPE_*
& "$env:JAVA_HOME\bin\jar.exe" tf clockwork_unoffical\libs\create-1.21.1-6.0.10.jar |
  Select-String -Pattern "PipeRenderer|PipeModel|AllPartialModels"
# 烘焙 multipart 的结构
& "$env:JAVA_HOME\bin\jar.exe" xf clockwork_unoffical\libs\create-1.21.1-6.0.10.jar `
  assets/create/blockstates/fluid_pipe.json assets/create/models/block/fluid_pipe
```

官方 0.6.0 热管的连接规则/反编译命令见
`docs/HEAT-PIPE-MODEL.md` 附录；反编译输出仍在 `_probe_tmp/heatpipe_decomp/`（临时目录，可删）。
