# Clockwork 1.21.1 移植 · 静态审计报告 R23（真机测试前）

> 范围：`clockwork_unoffical/`（MC 1.21.1 / NeoForge 21.1.228 + Create 6.0.10 + Sable 2.0.5 + Aeronautics 1.3.2）
> 目的：在实机测试前挖出**只靠编译期发现不了**的运行期炸点 / 静默失效。
> 铁律：每条结论都带证据（`javap` 输出片段或 `文件:行号`），没有证据的一律不写。
> 审计者：静态审计（未启动游戏）。构建产物：`build/libs/vs_clockwork-0.6.0-1.21.1.jar`
> SHA256 `25D7D5ADE148E8E21943503AD5947A649FE32BE0562C7122822B6BBE6A128DC1`

## 0. 结论摘要

| 检查项 | ✅ | ⚠️ | ❌ | 已修 |
|---|---|---|---|---|
| 1. mixin 目标核对（4 个类 / 5 个注入点 + 1 组 accessor） | 4 | 0 | 2（MixinBoilerData 语义偏差、MixinSteamEngineBlockEntity 死代码） | 2（两处都改 mixin 写法修掉） |
| 2. mixins.json 健康度 | 6 | 1（`defaultRequire:1` 的升级脆弱性，建议保留） | 0 | 0 |
| 3. 区块实体 / validBlocks 注册顺序 | 48/48 调用点、50/50 参数 | 0 | 0 | 0（无需修） |
| 4. 可选依赖不变量（A/B/C/D） | 4 | 0 | 0 | 0 |
| 5. 资源 / 数据守门 | `[4]`=0、`[6]`=0、未注册 id=0 | 0 | 0 | 0 |

**最重要的一条**：`MixinSteamEngineBlockEntity` 原来那个 `@ModifyExpressionValue` 是**可证的死代码** ——
注入点所在的字节码分支要求 `tank != null`，而该 mixin 的判断正是 `if (tank != null) return 原值`，
两条合起来永远走「返回原值」这一条路。也就是说：**燃气机（`gas_engine`）原本发不出任何动力**。
已按旧版 1.20.1 的注入方式重写（详见 §1.4）。

---

## 1. mixin 目标核对（最高优先级）

数据来源：`clockwork_unoffical/libs/create-1.21.1-6.0.10.jar`，命令
`javap -p [-c] [-l] -classpath <create jar> <目标类>`。
配置来源：`clockwork_unoffical/src/main/resources/vs_clockwork.mixins.json`（全库仅此一个 mixins.json）。

### 1.1 汇总表

| # | mixin 类（`mixin/` 之后） | 目标类 | 目标成员 / 注入点 | 证据（javap 片段） | 结论 |
|---|---|---|---|---|---|
| 1 | `content.gas.boiler.FluidTankSizeAccessor` | `com.simibubi.create.content.fluids.tank.FluidTankBlockEntity` | `@Accessor("width")`、`@Accessor("height")` | `protected int width;` / `protected int height;`（javap -p 字段区） | ✅ 存在、可见性（protected）与 accessor 用法相容 |
| 2 | `content.gas.boiler.MixinBoilerData` | `com.simibubi.create.content.fluids.tank.BoilerData` | `@Shadow public int attachedEngines` | `public int attachedEngines;` | ✅ 存在，`public int` 描述符 `I` 一致 |
| 2b | 同上 | `BoilerData#evaluate` | 原 `@At("RETURN")` → **已改为** `FIELD ... needsHeatLevelUpdate:Z, opcode=PUTFIELD` | `public boolean evaluate(FluidTankBlockEntity);`；`232: aload_0 / 233: iconst_1 / 234: putfield #117 // Field needsHeatLevelUpdate:Z` | ⚠️→✅ 已修（§1.2） |
| 3 | `content.gas.boiler.MixinSteamEngineBlock` | `...kinetics.steamEngine.SteamEngineBlock` | `canAttach(LevelReader,BlockPos,Direction)`，`@At("RETURN")` + `cancellable` | `public static boolean canAttach(net.minecraft.world.level.LevelReader, net.minecraft.core.BlockPos, net.minecraft.core.Direction);` 全方法只有一个 `ireturn`（字节码 `19: ireturn`） | ✅ 存在、`static` 与 handler 的 `private static` 匹配、描述符一致、返回点唯一 |
| 3b | 同上 | 语义核对：`canAttach` 的入参用法 | 本 mixin 用 `reader.getBlockState(pos.relative(direction))` | `0: aload_1 / 1: aload_2 / 2: invokevirtual BlockPos.relative / 5: astore_3 / 6: aload_0 / 7: aload_3 / 8: invokeinterface LevelReader.getBlockState ... / 16: instanceof FluidTankBlock / 19: ireturn` | ✅ 与 Create 的取方块位置完全一致（只是把 `instanceof FluidTankBlock` 放宽到燃气机） |
| 4 | `content.gas.boiler.MixinSteamEngineBlockEntity` | `...kinetics.steamEngine.SteamEngineBlockEntity` | 原 `@ModifyExpressionValue` 指向 `tick` 内 `BoilerData.getEngineEfficiency(I)F` + `@Local FluidTankBlockEntity tank` | 目标确实存在：`234: invokevirtual #217 // Method BoilerData.getEngineEfficiency:(I)F`；LVT `9 426 1 tank Lcom/.../FluidTankBlockEntity;` | ❌ **注入成功但语义死**（目标可达性证明见 §1.4）→ 已改为 HEAD + `Level.isLoaded` 注入 |
| 4b | 同上 | 新增第二注入点：`tick` 内 `Level.isLoaded(BlockPos)Z` | `@At(value="INVOKE", target="Lnet/minecraft/world/level/Level;isLoaded(Lnet/minecraft/core/BlockPos;)Z")` | `100: invokevirtual #154 // Method net/minecraft/world/level/Level.isLoaded:(Lnet/minecraft/core/BlockPos;)Z`，**整个 `tick` 中只出现 1 次**（grep 计数 1） | ✅ 唯一匹配，无需 ordinal |
| 4c | 同上 | `@Shadow protected ScrollOptionBehaviour<...> movementDirection` | `protected com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour<com.simibubi.create.content.contraptions.bearing.WindmillBearingBlockEntity$RotationDirection> movementDirection;` | ✅ 存在，protected（必须 `@Shadow`，不能靠转型） |
| 4d | 同上 | 用到的其余 API 全部逐一核实 | `PoweredShaftBlockEntity.update(BlockPos,int,float)` / `getTheoreticalSpeed()F` / `hasSource()Z` / `SteamEngineBlock.getFacing(BlockState)` / `SteamEngineBlock.FACING` / `GeneratingKineticBlockEntity.convertToDirection(F,Direction)F` / `AllAdvancements.STEAM_ENGINE` / `SmartBlockEntity.award(CreateAdvancement)V` / `IRotate.getRotationAxis(BlockState)` / `ScrollOptionBehaviour.get()`+`setValue(I)V` | 均见 §1.4 的 javap 摘录 | ✅ 全部存在且描述符一致 |

**mixin 类清单 vs 磁盘文件**：json 里 4 条，磁盘上 4 个 `.java`，包路径 `org.valkyrienskies.clockwork.mixin` + 条目名 = 实际包
（`src/main/java/org/valkyrienskies/clockwork/mixin/content/gas/boiler/`）→ 4/4 对应 ✅。

### 1.2 `BoilerData#evaluate`：注入点从 `RETURN` 前移到 `needsHeatLevelUpdate` 的写入处

`javap -p -c BoilerData` 里 `evaluate` 的骨架（字节码偏移 → 行号）：

| 字节码 | 含义 | 出处 |
|---|---|---|
| `22-29` | `attachedEngines = 0; attachedWhistles = 0;`（**先归零再重扫**） | `_refs/boilerdata_c.txt:1008-1016` |
| `32-229` | 三重循环扫描 `width × height × width` 锅炉本体，`offset(x,y,z)` 与 `Iterate.directions` 逐字对应 | `_refs/boilerdata_c.txt:1017-1107` |
| `232-234` | `putfield BoilerData.needsHeatLevelUpdate:Z` ← **新的注入点** | `_refs/boilerdata_c.txt:1108-1110` |
| `237-252` | 拿「扫描前保存的旧值」(slot 4/5) 与扫描后的新值比较 | `_refs/boilerdata_c.txt:1111-1118` |
| `253-260` | `ireturn old != new` | `_refs/boilerdata_c.txt:1119-1122` |

- 结论：**旧版语义**（旧版在循环内部 `BlockPos.offset` 调用点 `attachedEngines++`，位于比较之前）
  ⇒ 返回值也把燃气机算进「连接数变化」。原来的 `@At("RETURN")` 在 `ireturn` 之后才加计数，
  返回值漏掉燃气机，`FluidTankBlockEntity#updateBoilerState` 里 `changed` 只用于
  `notifyUpdate()` + `checkPipeOrganAdvancement`（`_refs/ftbe_c.txt:808-816`），
  会导致「只增减燃气机」时锅炉不同步/成就不触发。→ 已修 ✅
- 归零语义核对：`evaluate` 每刻先归零，所以放在 `232` 追加计数**不会跨刻累加** ✅
- 本体判据核对：Create 用 `FluidTankBlock.isTank(state)`，`javap -c` 显示它**就是**
  `state.getBlock() instanceof FluidTankBlock`（`_refs/seblock_c.txt` 同级 `FluidTankBlock`：
  `0: aload_0 / 1: getBlockState / 4: instanceof FluidTankBlock / 7: ireturn`）→ 与本 mixin 的
  `tankState.getBlock() instanceof FluidTankBlock` 等价 ✅
- 计数判据核对：Create 数蒸汽机 = `AllBlocks.STEAM_ENGINE.has(attachedState) && SteamEngineBlock.getFacing(attachedState) == d`
  （`_refs/boilerdata_c.txt:1074-1084`）；本 mixin 数燃气机 = `GasEngineBlock.isGasEngine(...) && GasEngineBlock.getFacing(...) == d`
  → 逐条同构 ✅

### 1.3 `SteamEngineBlock#canAttach`

- 目标签名（javap -p）：`public static boolean canAttach(LevelReader, BlockPos, Direction);`
- 全方法仅一处返回（`_refs/seblock_c.txt:80-91`，`19: ireturn`）→ `@At("RETURN")` 命中 1 次 ✅
- handler 声明为 `private static void ...(LevelReader, BlockPos, Direction, CallbackInfoReturnable<Boolean>)`
  —— 目标方法是 `static`，handler 也必须 `static` ✅；返回类型 `boolean` ↔ `CallbackInfoReturnable<Boolean>` ✅
- 结论：**✅ 无需修改**（`src/main/java/.../MixinSteamEngineBlock.java:25-35`）

### 1.4 `SteamEngineBlockEntity#tick`：原注入点是**可证的死代码**（本次最重要的发现）

#### ① 目标确实存在，描述符一致（这部分原来是 ✅）

```
_refs/see_tick.txt:224   234: invokevirtual #217 // Method com/simibubi/create/content/fluids/tank/BoilerData.getEngineEfficiency:(I)F
_refs/see_tick.txt:377     9   426   1   tank   Lcom/simibubi/create/content/fluids/tank/FluidTankBlockEntity;   （LocalVariableTable）
```
`@Local FluidTankBlockEntity tank` 也能解析（槽 1，存活区间 9..435，覆盖注入点 234）——**注入本身不会失败**。

#### ② 但该调用点只在 `tank != null` 时可达

`tick` 的控制流（`_refs/see_tick.txt:112-321`）：

| 字节码 | 含义 |
|---|---|
| `4-8` | `tank = getTank()` → `astore_1` |
| `10-13` | `shaft = getShaft()` → `astore_2` |
| `14-26` | `if (tank != null && shaft != null && isValid())` 成立才 `ifne 117`（跳到有效分支） |
| `29-116` | 无效分支，**末尾 `116: return`**（含 `106-113 shaft.update(worldPosition, 0, 0)`） |
| `117-434` | 有效分支；`getEngineEfficiency` 的唯一调用点 `234` 在这一段 |

而 `getTank()` 的实现（`_refs/see_tick.txt:503-556`）是：读 `source` 弱引用，失效则看
`worldPosition.relative(facing.getOpposite())` **是不是 `FluidTankBlockEntity`**，否则返回 `null`。
`isValid()`（`_refs/see_tick.txt` 内 `public boolean isValid`）同样是判背面方块 `is(AllBlocks.FLUID_TANK)`。

⇒ 「背面是燃气机」时 **`tank == null`**，`234` 这个调用点根本不会被执行。
⇒ 原 mixin 的 `if (tank != null) return boilerEfficiency;` 在该点上**恒为真**，
   后面 `findGasEngine()` / `engine.getEngineEfficiency()` 是**永不可达的死代码**。
⇒ 实际后果：**燃气机不产生任何转速**（`gas_engine` 是死内容）。这就是旧版 1.20.1 要整体接管 `tick` 的原因。

#### ③ 改写方式（只接管旧版真正改掉的那一段）

- `@Inject(method = "tick", at = @At("HEAD"))`：**仅当** `getTank() == null` **且**背面是燃气机 **且**
  方块是 `AllBlocks.STEAM_ENGINE` 时，自己按 Create 有效分支的逻辑驱动一次动力轴；其它情况直接 `return`，完全交给 Create。
- `@Inject(method = "tick", at = @At(INVOKE, Level.isLoaded))` + `ci.cancel()`：Create 的无效分支
  紧接着会用 `shaft.update(worldPosition, 0, 0)` 把刚写好的转速清零（字节码 `106-113`），必须拦掉。
  该调用点在 `tick` 内唯一 ✅。
- 燃气路径逐条镜像有效分支（字节码 → 源码动作）：`119-176` 轴/朝向 → `177-219` 方块与 `facing`
  （含 `facing.getAxis() == Y` 时改用 `SteamEngineBlock.FACING`）→ `220-235` `Mth.clamp(eff,0,1)` 与成就
  → `258-317` `conveyedSpeedLevel` 与两次取反 → `319-397` 反向自动翻转 `movementDirection`
  → `397-406` `shaft.update(worldPosition, conveyedSpeedLevel, efficiency)`。
  **唯一替换的输入**是效率本身（`tank.boiler.getEngineEfficiency(tank.getTotalTankSize())` → 燃气机的 `getEngineEfficiency()`）。

#### ④ 副作用核对（说明为什么这次改写是安全的）

| 核对点 | 证据 | 结论 |
|---|---|---|
| `PoweredShaftBlockEntity.update` 在客户端会不会造成抖动 | `GeneratingKineticBlockEntity.updateGeneratedRotation()` 开头：`10: getfield level / 14: ifnull 27 / 21: getfield isClientSide / 24: ifeq 28 / 27: return` —— **客户端直接 return** | 客户端不重算转速，不存在「客户端把轴清零」的风险 |
| 是否需要 `ExecuteOnClientOnly` 之类 | 同上 | 不需要；已去掉客户端粒子（见风险 R3） |
| `super.tick()` 是否还会执行 | HEAD 注入**不 cancel**（`CallbackInfo` 无 `cancel`），Create 的 `0-1 invokespecial SmartBlockEntity.tick` 照常执行 | 行为方块与 `source/target` 弱引用刷新不受影响 |
| 反向自动翻转 | `movementDirection.setValue(1 - movementDirection.get().ordinal())` 与字节码 `369-395` 一致 | 与有效分支同义 |

---

## 2. mixins.json 健康度

文件：`clockwork_unoffical/src/main/resources/vs_clockwork.mixins.json`（14 行，全库唯一）

| 项 | 当前值 | 证据 / 对照 | 结论 |
|---|---|---|---|
| `required` | `true` | 与 Create 6.0.10 自带 `create.mixins.json`（jar 内，`required: true`）一致 | ✅ 合适 |
| `refmap` | **不存在该键** | 本 jar 内 `vs_clockwork.mixins.json` 无 `refmap`；`create.mixins.json` 同样无 | ✅ **正确**，千万不要补 refmap（Create/Sable 用官方名，加 refmap 反而注入失败） |
| 产物里有无 refmap 文件 | 无 | `build/libs/vs_clockwork-0.6.0-1.21.1.jar` 条目仅 `vs_clockwork.mixins.json` + `META-INF/neoforge.mods.toml`，无 `*.refmap.json` | ✅ |
| `injectors.defaultRequire` | `1` | 与 `create.mixins.json` 的 `"defaultRequire": 1` 一致 | ⚠️ 见下（建议保留） |
| `compatibilityLevel` | `JAVA_21` | 工具链 `JavaLanguageVersion.of(21)`（`build.gradle:23`） | ✅ |
| `package` | `org.valkyrienskies.clockwork.mixin` | 4 个类的实际包名 = 该前缀 + 条目名 | ✅ |
| 被 mod 元数据登记 | `[[mixins]] config = "vs_clockwork.mixins.json"` | `src/main/templates/META-INF/neoforge.mods.toml:18-19`；构建后 jar 内同款 | ✅ |

**注入点命中数复核（决定 `defaultRequire: 1` 是否安全）**

| 注入点 | 期望命中 | 实测 | 结论 |
|---|---|---|---|
| `BoilerData.evaluate` @ FIELD `needsHeatLevelUpdate:Z` PUTFIELD | ≥1 | `evaluate` 内该字段写入**仅 1 处**（字节码 234） | ✅ 达标 |
| `SteamEngineBlock.canAttach` @ RETURN | ≥1 | 唯一 `ireturn`（字节码 19） | ✅ |
| `SteamEngineBlockEntity.tick` @ HEAD | ≥1 | 方法存在（`public void tick()`） | ✅ |
| `SteamEngineBlockEntity.tick` @ INVOKE `Level.isLoaded` | ≥1 | `tick` 内该调用**仅 1 处**（字节码 100） | ✅ |
| `FluidTankBlockEntity` 的 `width`/`height` @Accessor | 字段存在 | 二者均为 `protected int` | ✅ |

**建议**：
1. **保持 `required: true` + `defaultRequire: 1`**。理由：这 4 个注入点是气体切片的全部接点，
   一旦 Create 小版本改动导致注入失败，`defaultRequire: 1` 会**立刻崩在启动阶段并打印目标方法**，
   比「静默失效、进游戏后燃气机不出力」好得多 —— 后者正是本次抓到的 bug 类型。
2. 若将来要放宽，**只该放宽到逐个 `@Inject(require = 0)`**（而不是全局 `defaultRequire: 0`），
   且必须先给对应逻辑加「失效时打 WARN 日志」的兜底；当前版本（锁死 create-1.21.1-6.0.10）没有这个必要。
3. 可选加固（未做，属可选）：显式写 `"minVersion": "0.8"`；`client`/`server` 无需拆分（4 个 mixin 全是公共代码，
   新写的 `MixinSteamEngineBlockEntity` 已**不引用任何客户端专属类**）。

---

## 3. 区块实体 / `validBlocks` 注册顺序

**判定依据（先证机制，再查数据）**：`javap -p -c Registrate-MC1.21-1.3.0+67.jar` 显示
`BlockEntityBuilder#validBlocks(NonNullSupplier...)` 只是
`Arrays.stream(...).forEach(this::validBlock)`，而 `validBlock` 只做 `Set.add`
（**不在调用点 `.get()`**）；真正的 `.get()` 发生在之后构建 `BlockEntityType` 时
（`validBlocks.stream().map(...).toArray(Block[]::new)` → `BlockEntityType.Builder.of(...)`）。
⇒ 若在调用点引用了尚未初始化的静态字段，Set 里就会塞进 `null`，注册期爆炸。
⇒ 因此判据 = **同一类内，被引用的 `BlockEntry` 声明行必须早于 `validBlocks(...)` 调用行**。

**核对方法**：新增脚本 `tools/audit_validblocks_order.py`（机械扫描，不靠人眼），
对每个 `validBlocks(...)` 参数回溯同类声明行并比较。输出存档 `_refs/vb_order.txt`。

| 结果 | 数值 |
|---|---|
| `validBlocks(...)` 调用点 | 48 |
| 参数（含 `WING, FLAP` 与两个螺旋桨轴承的多参调用） | 50 |
| 声明在前 ✅ | **50 / 50** |
| 有问题 ❌/⚠️ | **0** |
| `.blockEntity(...)` 注册点 | 48，且 **48/48 都带了 `validBlocks`** |
| 单数 `validBlock(` 用法 | 0 |

关键抽样（完整 50 条见 `_refs/vb_order.txt`）：

| 调用点 `文件:行` | 参数 | 声明处 | 结论 |
|---|---|---|---|
| `content/logistics/gas/CWGasIndex.java:460` | `GAS_ENGINE` | 同文件 `:247` | ✅ |
| `content/logistics/gas/CWGasIndex.java:400` | `DUCT` | 同文件 `:99` | ✅ |
| `content/logistics/gas/CWGasIndex.java:517` | `INTAKE` | 同文件 `:384` | ✅ |
| `content/curiosities/sensor/CWSensorsIndex.java:91` | `LODEFOCUS` | 同文件 `:78` | ✅（sensor 那条历史崩溃点，现在安全） |
| `content/contraptions/CWContraptionsIndex.java:93` | `PHYS_BEARING` | 同文件 `:80` | ✅ |
| `content/contraptions/CWContraptionsIndex.java:125` | `BRASS_PROPELLER_BEARING, JURYRIGGED_PROPELLER_BEARING` | 同文件 `:99`, `:111` | ✅ |
| `content/kinetics/CWKineticsIndex.java:133` | `WING, FLAP` | 同文件 `:110`, `:120` | ✅ |

**跨类 / 跨 Registrate 实例检查**：48 个 `validBlocks` 的参数**全部**是同类静态常量，
不存在「引用别的 index 类常量」的写法；全库只有 `ClockworkMod.REGISTRATE` 一个注册器实例
（`ClockworkMod.java:43`，`ClockworkRegistrate.create(MOD_ID)`），各 index 类都用
`private static final ClockworkRegistrate REGISTRATE = ClockworkMod.REGISTRATE;` 取同一个实例
（如 `index/CWBlocks.java:30`）⇒ 不存在「跨 Registrate 实例的 Builder」问题 ✅。
**本节无需修复（0 条）。**

> 备注（不猜）：用户提到的报错串 `Trying to access unbound value` 在本地
> `Registrate`/`create`/`neoforge-21.1.228-userdev` 三个 jar 的 class 常量池里**都搜不到**
> （已逐 jar 全类扫描），所以无法指认它的确切抛出点；但上面的顺序不变量是**直接对机制核验**的，
> 与该报错串的来源无关。

---

## 4. 可选依赖不变量

### A. `dan200.**` 只能在 `integration/cc/**` —— ✅

| 项 | 证据 | 结论 |
|---|---|---|
| `dan200` 出现位置 | 仅 5 个文件，全在 `src/main/java/org/valkyrienskies/clockwork/integration/cc/`：`ClockworkComputerCraft.java:3,4`、`ClockworkPeripheral.java:3`、`AltMeterPeripheral.java:3,4`、`PhysBearingPeripheral.java:3,4`、`ValveDuctPeripheral.java:3` | ✅ 无一例外 |
| 包外是否引用 CC 集成类 | 全库仅 1 处：`ClockworkMod.java:18`（import）+ `ClockworkMod.java:65`（调用） | ✅ |
| 是否条件加载 | `ClockworkMod.java:64` `if (ModList.get().isLoaded("computercraft"))` 包住 `:65` | ✅ 未装 CC 时该 `invokestatic` 不执行，JVM 惰性解析 → `ClockworkComputerCraft` 及其 `dan200` 引用都不解析 |
| 类层次是否会把 `dan200` 提前拖进来 | `ClockworkComputerCraft` 是 `final class`，**不 extends/implements 任何 dan200 类型**（只 import `IPeripheral`/`PeripheralCapability` 用于方法体与描述符） | ✅ |
| 检查能力注册时机 | `@SubscribeEvent registerCapabilities(RegisterCapabilitiesEvent)`（`ClockworkComputerCraft.java:62-68`），由 `:56 modBus.register(ClockworkComputerCraft.class)` 挂载，而后者只在守卫内执行 | ✅ |

### B. `mezz.jei.**` 只能在 `compat/jei/**` —— ✅

| 项 | 证据 | 结论 |
|---|---|---|
| `mezz.jei` 出现位置 | 仅 4 个文件，全在 `src/main/java/org/valkyrienskies/clockwork/content/logistics/gas/compat/jei/`：`ClockworkJeiPlugin.java:3-8`、`GasCrafterJeiCategory.java:4-10`、`GasInfoJeiCategory.java:3-9`、`GasIngredientJei.java:3-9` | ✅ |
| 包外是否引用 JEI 集成类 | grep `ClockworkJeiPlugin|GasCrafterJeiCategory|GasInfoJeiCategory|GasIngredientJei`：包外 **0 命中** | ✅ |
| 加载方式 | `ClockworkJeiPlugin implements IModPlugin` + JEI 自己的 `@JeiPlugin` 扫描；我们从不主动引用它 | ✅ 未装 JEI 时这些类不会被加载 |
| 依赖声明 | `neoforge.mods.toml` 里 `modId = "jei"` / `type = "optional"` | ✅ |

### C. 入口点里没有别人 modid 的 `@Mod` —— ✅

| 项 | 证据 | 结论 |
|---|---|---|
| `@Mod(` 全部出现 | **只有 1 处**：`ClockworkMod.java:34` `@Mod(ClockworkMod.MOD_ID)` | ✅ |
| modid 值 | `ClockworkMod.java:38` `public static final String MOD_ID = "vs_clockwork";` | ✅ 自己的 id |
| 历史炸点是否复现 | `@Mod("computercraft")` / `@Mod("jei")` / `@Mod("create")` 全部 0 命中；只有 `ClockworkComputerCraft.java:42-45` 的注释在警告不要加 | ✅ 已消除 |
| `@EventBusSubscriber` 的 modid | 3 处，全部 `modid = ClockworkMod.MOD_ID`：`integration/ponder/ClockworkPonderClient.java:22`、`content/kinetics/CWKineticsModBus.java:20`、`content/logistics/CWLogisticsModBus.java:15` | ✅ |

### D. `META-INF/neoforge.mods.toml` 只能有一个 `[[mods]]` —— ✅

| 项 | 源码模板 `src/main/templates/META-INF/neoforge.mods.toml` | 构建产物 jar 内 |
|---|---|---|
| `[[mods]]` 数量 | **1**（`:7`） | **1** |
| 该块 modId | `${mod_id}` → `vs_clockwork` | `vs_clockwork` |
| 其它 `[[mods]]` | 无 | 无 |
| `[[mixins]]` | 1（`:18-19`，`config = "vs_clockwork.mixins.json"`） | 1，同名 |
| `[[dependencies.*]]` | 7 块：neoforge / minecraft / create / sable / simulated（required）+ jei / computercraft（optional） | 同 |
| refmap 条目 | — | 无 |

---

## 5. 资源 / 数据守门

| 脚本 | 命令 | 关注项 | 实测 | 结论 |
|---|---|---|---|---|
| `tools/check_data_refs.py` | `python tools\check_data_refs.py` | 未注册 id | `代码里注册的 id: 100 个；数据包引用的未注册 id: 0 个` | ✅ **0** |
| `tools/check_assets.py` | `python tools\check_assets.py` | `[4]` 模型/贴图缺失 | `[4] 模型/贴图缺失（0）:` | ✅ **0** |
| 同上 | 同上 | `[6]` OBJ 问题 | `[6] OBJ 模型（loader/obj/mtl/贴图键）问题（0）:` | ✅ **0** |

`check_assets.py` 的 `[1]/[2]/[3]/[5]` 属于「物品无 blockstate / 语言条目待补 / 有资源但未注册方块」等
**提示性**输出（例如 `[5]` 列出的 8 个是旧版遗留资源），不属于本次要求的健康指标，
也不构成运行期崩溃点（未注册 = 不会被加载）。

---

## 6. 本次修掉的问题清单

| # | 文件 | 改动 | 为什么（证据） |
|---|---|---|---|
| F1 | `src/main/java/org/valkyrienskies/clockwork/mixin/content/gas/boiler/MixinBoilerData.java` | 注入点 `@At("RETURN")` → `@At(value="FIELD", target="...BoilerData;needsHeatLevelUpdate:Z", opcode=Opcodes.PUTFIELD)`；handler 由 `CallbackInfoReturnable<Boolean>` 改为 `CallbackInfo`；类注释补上 `evaluate` 的字节码骨架 | `evaluate` 的返回值 = 「连接数是否变化」（字节码 `237-260`），只用于 `notifyUpdate()` + `checkPipeOrganAdvancement`（`FluidTankBlockEntity.updateBoilerState` 字节码 `145-161`）。在 `RETURN` 之后才加计数会让返回值漏掉燃气机 → 只增减燃气机时锅炉不同步。移到字节码 `232` 的 `PUTFIELD` 处（循环后、比较前）即恢复旧版语义；该字段在 `evaluate` 内只写 1 次，`defaultRequire:1` 安全 |
| F2 | `src/main/java/org/valkyrienskies/clockwork/mixin/content/gas/boiler/MixinSteamEngineBlockEntity.java` | 删除死掉的 `@ModifyExpressionValue` + `@Local`；改为 ①`tick` 的 `@At("HEAD")` 注入（仅在 `getTank()==null` 且背面是燃气机时自己驱动动力轴，逐条镜像 Create 有效分支）②`tick` 内 `Level.isLoaded` 的 `@Inject(cancellable)`，拦住 Create 无效分支 `shaft.update(worldPosition, 0, 0)` 的清零 | 原注入点所在分支要求 `tank != null`（`ifne 117`），而 mixin 自身又在 `tank != null` 时直接返回原值 → 死代码，`gas_engine` 永远发不出动力（§1.4）。新写法只在「Create 本来就不处理」的场景接管，其它情况一律交回 Create |
| F3 | `tools/audit_validblocks_order.py`（新增） | 一次性审计脚本：机械核对 `validBlocks` 的声明顺序 | 让第 3 节的结论可复现，不依赖人眼；后续再加注册项时可当作守门脚本复用 |

- 未改动任何**玩法数值**、未新增玩法功能、未改他人负责的文件。
- **本次没有遇到别人的编译错误**：`compileJava` 实际执行（`--no-build-cache`，非缓存命中）→ `BUILD SUCCESSFUL`，
  只有一条 javac 的 deprecation 提示（`注: 某些输入文件使用或覆盖了已过时的 API`），无 warning-as-error。

---

## 7. 剩余风险（必须实机确认）

| # | 风险 | 触发条件 | 影响 | 建议观察点 |
|---|---|---|---|---|
| R1 | 新写的燃气路径**无法静态验证玩法手感** | 蒸汽机背面贴燃气机（背面无储罐） | 转速方向/大小、`movementDirection` 反向自动翻转是否与锅炉供能一致 | 放「燃气机 + 蒸汽机 + 动力轴」，看护目镜效率与轴转速；与「储罐 + 蒸汽机」对照方向 |
| R2 | Create 无效分支被 `ci.cancel()` 提前返回 | 同上 | 若 Create 未来在 `isLoaded` 之后还做别的事，会被一起跳过（当前 6.0.10 里 `isLoaded` 之后只有 `shaft.update(…,0,0)`，字节码 `106-116`） | 换 Create 版本时必须重看这段字节码 |
| R3 | 燃气路径**不发射粒子** | 同上 | 纯表现层：相比锅炉供能少了客户端粒子 | 若观感需要，再补 `@Shadow private void spawnParticles()`（本次为避免私有 shadow 带来的注入失败风险而省略，已在类注释写明差异） |
| R4 | `defaultRequire: 1` + `required: true` 的升级脆弱性 | 更新 Create（非 6.0.10）导致任一注入点找不到 | 启动阶段直接崩溃（这是刻意的「大声失败」） | 见 §2 建议 2；更换 Create 后先跑 `javap` 复核 5 个注入点 |
| R5 | 锅炉返回值语义变化（本次修的 F1 带来的行为差异） | 只增减燃气机 | 现在会额外触发一次 `notifyUpdate()`/成就检查（旧版本来就如此） | 放/拆燃气机时看锅炉护目镜是否即时刷新 |
| R6 | 其余实机手感项（物理轴承装配 / 螺旋桨推力 / 气网压力 / 机翼升力 / 气嘴喷流） | — | 不在本次静态审计范围 | 见 `PORT-STATUS.md` §4 |

---

## 8. 构建与装包

| 步骤 | 命令 / 路径 | 结果 |
|---|---|---|
| 构建 | `cd clockwork_unoffical; $env:JAVA_HOME="C:\Users\yql11\java\dragonwell-21.0.8.0.8+9-GA"; .\gradlew.bat --offline build` | `BUILD SUCCESSFUL`，`LASTEXITCODE=0` |
| 强制重编（排除缓存假象） | 删除 `build/classes/java/main/.../mixin/content/gas/boiler/*.class` 后 `.\gradlew.bat --offline --no-build-cache build` | `compileJava` 实际执行（非 FROM-CACHE）→ `BUILD SUCCESSFUL in 11s` |
| 产物 | `clockwork_unoffical/build/libs/vs_clockwork-0.6.0-1.21.1.jar` | 7 995 865 B，SHA256 `25D7D5ADE148E8E21943503AD5947A649FE32BE0562C7122822B6BBE6A128DC1` |
| 产物自检 | jar 内 `javap -p` 的 `MixinSteamEngineBlockEntity` | 含 `vs_clockwork$gasEngineTick` / `vs_clockwork$keepGasEngineShaft` / `vs_clockwork$driveShaftFromGasEngine` / `vs_clockwork$findGasEngine` / `vs_clockwork$self` → **确认打进去的是新代码** |
| 装包 1 | `C:\Users\yql11\OneDrive\Desktop\.minecraft\versions\1.21.1-NeoForge_21.1.251\mods\vs_clockwork-0.6.0-1.21.1.jar` | 覆盖成功，SHA256 与源一致 ✅ |
| 装包 2 | `C:\Users\yql11\AppData\Roaming\PrismLauncher\instances\CSN_The Song Of The Sky\minecraft\mods\vs_clockwork-0.6.0-1.21.1.jar` | 覆盖成功，SHA256 与源一致 ✅ |

---

## 附：证据文件索引

| 文件 | 内容 |
|---|---|
| `_refs/see_tick.txt` | `javap -p -c -l SteamEngineBlockEntity` 全文（tick 控制流、LVT、getTank、isValid、spawnParticles） |
| `_refs/boilerdata_c.txt` | `javap -p -c BoilerData` 全文（evaluate 骨架、attachedEngines 读写点） |
| `_refs/seblock_c.txt` | `javap -p -c SteamEngineBlock` 全文（canAttach） |
| `_refs/ftbe_c.txt` | `javap -p -c FluidTankBlockEntity` 全文（updateBoilerState 里 evaluate 返回值的用途） |
| `_refs/vb_order.txt` | `tools/audit_validblocks_order.py` 的 50 条逐项输出 |
