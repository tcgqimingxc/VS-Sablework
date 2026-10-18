# 气体储罐（duct_tank）：物品图标 + 多方块"自己分离"修复（2026-10-17 第四轮）

> 反馈原话：**「气体储罐在物品栏时贴图错误，而且有时候气体储罐会自己分离。气体储罐可以参考机械动力原版流体储罐处理」**
>
> 本轮只动 `content/logistics/gas/tank/**`、`models/item/duct_tank.json`、储罐的 block 模型、
> `tools/**`、`docs/**`。对照物：Create 1.21.1 的
> `FluidTankBlock` / `FluidTankBlockEntity` / `FluidTankModel` / `FluidTankCTBehaviour`。
> （`Create-mc1.21.1-dev/AGENTS.md` 写了不接受 LLM 贡献；本轮只把它当**只读参照**，没有修改其中
> 任何文件。）

---

## 1. 物品栏贴图错误

### 症状与根因

上一轮为了让世界里的罐体吃到 Create 的连接纹理（CT），把世界模型 `block_single` 从**官方 1.20.1
的"64×64 图集单块立方体"**换成了 **Create 的 plain 模型 + 16×16 基础贴图**
（`duct_tank.png` / `duct_tank_top.png`）。但物品模型一直写着：

```json
{ "parent": "vs_clockwork:block/duct_tank/block_single" }
```

物品不经过 CT（`CreateRegistrate.blockModel` → `ModelSwapper` 只替换 **blockstate** 的
`ModelResourceLocation`，不碰 `vs_clockwork:duct_tank#inventory`），所以它拿到的是：

* 世界模型 `block_single` 的几何（Create plain 的"封闭盒子"）；
* 两张**为平铺裁出来的** 16×16 贴图（`duct_tank.png` 是图集 `x32..47 / y11..26` 的一格墙，
  不是完整面板）。

结果就是一个**没有边框、没有铆钉、没有双层盖板的灰盒子**。用等距预览渲染对比（渲染器为一次性
探针，未入库）：

| 修复前（parent 世界 `block_single`） | 修复后（`block_single_item`，官方图集 (0,3) 格） |
|---|---|
| 灰盒子，侧壁只有一道加强筋、看不出罐体 | 双层边框、四角铆钉、完整单块罐面板 |

### Create 是怎么做的

Create 的 `models/item/fluid_tank.json` 只有一行：

```json
{ "parent": "create:block/fluid_tank/block_single_window" }
```

它也没有 `display`、没有额外的 `textures` 覆盖、也没有走 `FluidTankModel`（CT）。**关键差别不在
写法，而在被 parent 的模型能不能当一个"静态图标"用**：

* Create 的 `block_single_window` 引用 `fluid_tank.png`，这张 16×16 本身就是**完整单块面板**
  （四周有边框/倒角），物品直接用不会缺细节；
* 我们的 `block_single` 是世界模型，为了 CT 必须引用 16×16 的**平铺墙**，直接烘图标就缺细节。

### 修法（选官方资源，而不是硬加 display）

新增 `models/block/duct_tank/block_single_item.json`：**逐字节复制官方 1.20.1 的
`models/block/duct_tank/block_single.json`** —— 单个 `0..16` 立方体、
`texture_size: [64,64]`、六个面 uv `[0,12,4,16]`（= 64×64 图集第 `(0,3)` 格 16×16 面板）。
物品模型改为：

```json
{ "parent": "vs_clockwork:block/duct_tank/block_single_item" }
```

为什么不是给物品模型加 `display` 或独立 `textures`：`display` 只改 GUI 的旋转/缩放，救不了
"贴图本身缺一整块面板"；独立 `textures` 覆盖只能换图，仍需一张"完整面板"的图 —— 官方模型已经把
这张图（图集 `(0,3)` 格）和正确的 uv 一起给出来了，逐字节复用最不容易错。

为什么不用 `block_single_window`：本工程没有窗户模型/贴图（上一轮已删 4 个 `*_window`），
硬加会变成 20 个不存在的模型引用（`check_assets.py` `[12]` 立刻报）。

### 涉及文件

| 文件 | 作用 |
|---|---|
| `models/block/duct_tank/block_single_item.json` | 新增；官方单块立方体（非 CT，物品专用） |
| `models/item/duct_tank.json` | parent 改为 `block_single_item` |
| `models/block/duct_tank/block_single.json` | **保持不变**（世界模型，CT + 16×16 基础图） |

`tools/gen_duct_tank_assets.py` 现在负责生成/核对 `block_single_item.json` 与 item parent，
并校验 4 个世界模型仍然是"Create 的几何 + vs_clockwork 的 16×16 贴图"（若工作区有
`Create-mc1.21.1-dev`，逐元素比对）；再也不会用官方模型覆盖世界模型。

---

## 2. 多方块"自己分离"

### 2.1 根因 A：客户端按 `BOTTOM` 推导 controller（主因，外观分离）

上一轮在客户端加了一个"自愈"：

```java
// 已删除
if (state.getValue(DuctTankBlock.BOTTOM)) {        // ← 错在这里
    if (controller != null && !controller.equals(getBlockPos())) {
        controller = null; height = 1; width = 1;
    }
}
```

`BOTTOM` 的语义是**"本段位于整罐最下一层"**，不是"我是 controller"。Create 的
`notifyMultiUpdated` 就是按 Y 坐标整层写 `BOTTOM=true`：

```java
state.setValue(BOTTOM, getController().getY() == getBlockPos().getY());
```

所以宽 3 的罐底有 **9 块**同时 `BOTTOM=true`，其中只有 1 块是 controller。客户端每 10 刻
（`lazyTick`）会把另外 **8 块**的 controller 清空、height/width 改成 1，于是：

* `ConnectivityHandler.isConnected`（CT 的 `connectsTo` 与 `FluidTankModel` 的面剔除都用它）
  比较的是 controller → 这 8 块与罐身/彼此都"不连通"；
* 罐体 CT 退回单块花纹，`DuctTankModel` 的 `CULL_PROPERTY` 也不再剔除贴合面 → 罐底看起来
  **与罐身分离**；
* 护目镜从这些段读到的是各自那份空数据（0 Pa、1 block）；
* 该 bug 只在**区块网格重建**时显现（改字段不触发 mesh 重建），所以是"有时候"。

Create 的客户端从不重新推导结构：`Controller/Height/Width` 由 `sendData` 权威下发，
`read` 里先清空再读；`controllerBE()` 每次都重新 `level.getBlockEntity`，主块方块实体晚到时下一次
调用自然解析成功。**所谓"数据包晚到会永久 0 Pa"并不存在**，自愈是多余的且有害的。

修法：删除 `reconcileClientState()` / `findControllerBelow()` 与 `lazyTick` 覆盖；
`controller/height/width` 只在 `read` / `setController` / `removeController` /
`setHeight` / `setWidth`（后两个只由服务端 `ConnectivityHandler` 调）里改。

### 2.2 根因 B：`onPlace` 把"外观同步"也当成"结构变化"

`LevelChunk.setBlockState` 对**任何**方块状态变化都会回调 `onPlace`（javap 核实 1.21.1：
非客户端分支在 block entity 创建之前**无条件**调用 `newState.onPlace(...)`，没有"换块"判断）。
`notifyMultiUpdated` 会 `setBlock` 写 `top/bottom`，于是每次成型/拆分都会对每一段再排一次
`queueConnectivityUpdate()` → 下一 tick `formMulti`。

当前 `ConnectivityHandler` 的 width/height 快速返回让这条路径**恰好**幂等
（`if (beWidth == bestWidth && beWidth * beWidth * be.getHeight() == bestAmount) return;`），
所以不是本轮"分离"的直接原因；但它把两条路径绑死了：任何一次 formMulti 条件改动都可能让
"写一次外观 → 拆一次罐"。

修法（与 Create `FluidTankBlock.onPlace` 逐条对齐）：

* `oldState.is(state.getBlock())` → return（同方块换状态不是放置）；
* `isMoving` → return（活塞/移动装置不在这里重算）；
* 放置时**同步**调 `tank.updateConnectivity()`（Create 同款，不是排到下一 tick）；
* 之后 `level.markAndNotifyBlock(pos, chunk, oldState, newState, UPDATE_ALL_IMMEDIATE, 512)`
  补发成型后的状态（Create 同款；否则 NeoForge 的放块流程会用"放之前"的状态再标一次，
  客户端停在默认 `block_single`）。

### 2.3 为什么周期同步不写方块状态

气体/温度同步在 `GasNodeState.syncIfChanged()` / `syncNow()`：只 `host.sendData()`，**没有**
任何 `setBlock`/`setBlockAndUpdate`。`DuctTankBlockEntity.tick()` 只消费结构事件排下的 flag，
不写状态；写 `top/bottom` 的唯一位置是 `notifyMultiUpdated`（结构成立）与
`removeController` → `applySingleShape`（拆罐）—— 与 Create 相同。

### 2.4 flag 语义（已是 Create 同款）

| 位置 | flag | 为什么 |
|---|---|---|
| `notifyMultiUpdated` | `UPDATE_CLIENTS \| UPDATE_INVISIBLE` = `2\|4` | 2 广播新状态；4 是 UPDATE_INVISIBLE，**不是**"跳过邻居"；真正不触发邻居是因为没带 1（UPDATE_NEIGHBORS），且本方块不重写 `updateShape` |
| `removeController` → `applySingleShape` | `2\|4\|16` = 22 | 16 = UPDATE_KNOWN_SHAPE，拆罐过程中不再扩散形状更新（Create `removeController` 同款） |

---

## 3. 自检

新增 `tools/selftest_tank_connectivity.py`（已挂到 `tools/run_gates.ps1 -SelfTest`）：

* `[T1]` `tick()` 不写 blockstate；
* `[T2]` 客户端没有 `reconcileClientState` / `findControllerBelow` / `lazyTick` 结构重算；
* `[T3]` `controller/height/width` 的赋值点收敛到允许的方法；
* `[T4]` `onPlace` 有两条 Create guard + 同步 `updateConnectivity()` + `markAndNotifyBlock`；
* `[T5]` `notifyMultiUpdated` 的 flag 正确、`GasNodeState` 的同步方法不写 blockstate；
* `[T6]` 物品模型 = 非 CT 的官方 `block_single_item`，uv 只取 `(0,3)` 格；
* `[T7]` 3×3 罐底状态模型：`BOTTOM=true` 9 块 / controller 1 块，旧规则会误清 8 块
  （`[T2]` 的回归说明）。

```powershell
python tools/selftest_tank_connectivity.py
python tools/gen_duct_tank_assets.py --check
python tools/gen_duct_tank_textures.py --check
```

---

## 4. 实机确认点

1. 创造物品栏里 `duct_tank` 图标应是**带双层边框/铆钉的单块罐**（与官方 1.20.1 一致），不是灰盒子。
2. 搭 **3×3×3** 静置 1 分钟：罐底不应再"分离"成 9 个单块；在罐子旁边频繁放/拆方块
   （强制该 section 重建网格）也不应变化。
3. 对罐底任意一块（非主块）看护目镜：应显示 `Tank: 27 blocks (3W x 3H)`，不是 1 block / 0 Pa。
4. 拆掉中间一段：剩余罐体应拆成上下两段并各自正确成型（`block_bottom` / `block_top`），
   不应停在"上下全开口"的 `block_middle` 外观。
5. 拆掉主方块（最下面那块）：罐内气体应转移到正上方那块，剩余段落重新成型，不丢气。

---

## 5. 关键文件

| 文件 | 说明 |
|---|---|
| `content/logistics/gas/tank/DuctTankBlock.java` | 对应 Create `FluidTankBlock`；onPlace guard / markAndNotifyBlock |
| `content/logistics/gas/tank/DuctTankBlockEntity.java` | 对应 Create `FluidTankBlockEntity`；删除客户端结构推导，`updateConnectivity()` |
| `content/logistics/gas/tank/client/DuctTankModel.java` | 对应 Create `FluidTankModel` |
| `content/logistics/gas/tank/client/DuctTankCTBehaviour.java` | 对应 Create `FluidTankCTBehaviour` |
| `models/item/duct_tank.json` + `models/block/duct_tank/block_single_item.json` | 物品图标 = 官方非 CT 单块模型 |
| `tools/selftest_tank_connectivity.py` | 本轮新增的静态自检 |
| `tools/gen_duct_tank_assets.py` / `gen_duct_tank_textures.py` | 区分"世界 CT 模型"与"物品图标模型"的生成/核对 |

---

## 6. 刚成型时无法充气（2026-10-18 第五轮）

> 反馈原话：**「气体储罐在刚建成多方块结构时无法充气（1x1 可以，2x2 刚建成时不行，重启才可以，3x3 同 2x2 一样）」**

### 6.1 根因 A（主因，服务端）：节点身份是主方块坐标，找邻居却用物理段坐标

整罐只有一个气体节点，身份 `gasPos() = 主方块坐标 C`；但导管可以贴在**任意一段** S 上。
旧 `GasNetwork.simulate` 的边去重是：

```java
BlockPos aPos = a.gasPos();            // 导管侧：D
BlockPos bPos = aPos.relative(dir);    // 物理位置 S（贴着的罐段）
if (bPos.asLong() <= aPos.asLong()) return; // ← 用物理坐标比较
```

而储罐侧的反向遍历从 `gasPos = C` 出发，只找 C 的邻居。于是：

* 从导管侧看，pair 是 `(D, S)`；
* 从储罐侧看，pair 是 `(C, C 的邻居)`；
* `S != C`，所以当 `S.asLong() < D.asLong()` 时导管侧跳过，储罐侧又找不到 D
  → **这条边永远不会被处理**。

`BlockPos.asLong` 是 `(x<<38)|(z<<12)|y` 的掩码打包，不是自然坐标序：x 为负会变成很大的正数。
结果就是"同一座 2×2/3×3 罐，导管接在主方块那一侧能进气、接在别的段上不进气"；1×1 因为
`gasPos == 物理位置`，两个方向都能配对，所以永远正常。

服务端存档可以复现这个几何：3×3 罐主方块 `(8,-60,-7)`，泵在 `(7,-60,-6)` 贴着**非主方块的**
`(8,-60,-6)`，x 更小（`7 < 8`）所以这条边恰好能处理，罐内累计到了 5435 kg；同一结构只要把泵
放到段的东/北侧，旧代码就不会产生任何 transfer。

### 6.2 根因 B：成型后没有显式重建注册表，幽灵节点留到区块卸载

旧 `GasNetworks.prune` 只检查"方块实体是否还在/是否被替换"，不看：

* `!gasNodeActive()`（旧主方块变成成员段后已不是节点）；
* 注册 key 是否还等于当前 `gasPos()`（成型后 gasPos 已经指到新主方块）。

旧主方块于是以 `key = 旧位置 A`、`gasPos = 新主方块 C` 的形式永久留在表里；它的
`GasNodeState.registered` 也不清。等它以后再次成为主方块时，`registered == true` 导致
**不会再注册**——拆分后怎么重搭都不进气，只有区块卸载/重进存档才恢复。

### 6.3 根因 C：客户端结构数据包的时序

成型瞬间 `setController` / `notifyMultiUpdated` 连发 `sendData()`，而方块状态广播要等本刻末。
客户端的方块实体若还没建/还没更新，数据包会被丢掉；而 `syncIfChanged` 是 10 刻 + 阈值，
阈值内的结构变化不会补发。表现就是"护目镜/气面在刚成型时不更新，重进存档（区块更新标签重发）才好"。
本轮不恢复客户端自愈（仍然由服务端权威下发），只在结构事件后 **事件刻 + 1 tick 补发一次**。

### 6.4 修法

| 位置 | 改动 |
|---|---|
| `GasNode` | 新增 `gasEndpoints()`（多方块的全部物理端点）与 `gasConnectedAt(endpoint, dir)`（按真实端点判连）；默认实现保持单方块节点不变 |
| `GasNetworks` | 新增 `endpoints` 表（物理位置 → 节点身份）与端点缓存；`nodeAt` 先查端点；`collect`/`simulate` 从端点找邻居、按**节点身份**去重；`prune` 剔除 `!gasNodeActive()` 与 key≠gasPos 的条目并清 `registered`；新增 `structureChanged()`（成型/拆分显式清表+重注册）与 `unregisterAt()` |
| `GasNetwork` | 流循环改为"每个端点 × 6 面"，边按 gasPos 去重；同一节点多个端点贴同一邻居只走一次 |
| `DuctTankBlockEntity` | `gasEndpoints()` 扫整罐并只收 `tankRoot()` 相同的段；`gasConnectedAt()` 按端点判"同罐段不互连"；`notifyMultiUpdated` 调 `structureChanged` + `absorbOrphanGas`（换主方块不丢气）+ 2 tick 后补发数据；`removeController` 显式 unregister；`tick` 只消费一次性的结构补发，不写 blockstate |
| `DuctTankBlock` | `onRemove` 在 `IBE.onRemove`/`splitMulti` 前先把该格从气体网络摘掉 |
| `GasMix` | 新增 `clearMass()`（孤儿气搬走后清空旧 mix） |
| `GasNodeState` | `serverTick` 对非节点主动 `unregister`（成员段不再留幽灵条目）；新增 `registerNow()` / `resetRegistration()`；`unregister` 改为按对象身份删表 |

### 6.5 自检

新增 `tools/selftest_tank_fill.py`（挂到 `tools/run_gates.ps1 -SelfTest`）：**71 条断言**。

* `[F1]` 1×1：六个方向接导管都能进气；
* `[F2]` 2×2×1：4 段 × 每个自由面（含东/北/上）都能进气；
* `[F3]` 3×3×1：9 段 × 每个自由面都能进气；
* `[F4]` 成型时旧主方块的残留注册项被结构事件清掉，新主方块立刻可进气；
* `[F5]` 拆中间一段后上下两段各自重新注册、各自进气；
* `[F6]` 2×2 的 8 个侧面接法进气量完全一致（不允许坐标偏置）；
* `[F7]` 静态约束：端点抽象、按身份去重、`structureChanged`、不丢气、下一 tick 补发、
  `prune` 清非节点、`removeController` 摘注册。

### 6.6 实机确认点

1. 搭 **2×2×1**，**立刻**在任意一段的任意自由面接导管 + 泵/压缩机：应开始进气（不需要重进存档）；
2. 拆掉其中一段再补回去：重新成型后仍能进气；
3. **1×1** 仍然正常；
4. 成型瞬间看护目镜：`Tank: N blocks` 与压力应在 1 tick 内更新，不应停在成型前的读数；
5. 罐里有气时加高/加宽：气不应归零（旧主方块的 orphan gas 会被搬进新主方块）。
