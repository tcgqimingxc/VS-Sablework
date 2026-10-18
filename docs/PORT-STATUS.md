# 移植状态（Lead 维护 · 最新）

> 目标：`clockwork_unoffical/` = Clockwork on **Minecraft 1.21.1 / NeoForge 21.1.228**
> 前置：**Create 6.0.10 + Sable 2.0.5 + Create: Aeronautics(Simulated) 1.3.2**
> 参考：`Clockwork-1.20.1-main/`（旧版行为）、`Simulated-Project-main/`、`sable-main/`、`docs/API-REFERENCE.md`（javap 核实过的 API）

## 1. 构建（本机必须离线）

```powershell
cd clockwork_unoffical
$env:JAVA_HOME="C:\Users\yql11\java\dragonwell-21.0.8.0.8+9-GA"
.\gradlew.bat --offline build       # 当前 BUILD SUCCESSFUL，产物 build/libs/vs_clockwork-0.6.0-1.21.1.jar
```

- 本机**访问不了 `maven.neoforged.net`**：NeoForge/MC 依赖走 Gradle 缓存（`neo_version=21.1.228`），
  Create 生态 + Sable + Aeronautics + Veil + CC + JEI 全部用 `libs/*.jar` 本地依赖 → 构建必须带 `--offline`。
- `build.gradle` 里排除了 `io.netty:netty-transport-native-epoll`（离线缓存缺 Linux 原生库，Windows 运行不需要）。
- `gradlew runClient/runServer` 在本机**跑不起来**：ModDevGradle 需要 `neoforge-<ver>-moddev-config.json`，
  缓存里没有、官方 maven 不可达、Maven Central/阿里云也没有（实测 404）。实机验证只能通过把 jar 放进
  `C:\Users\yql11\OneDrive\Desktop\.minecraft\versions\1.21.1-NeoForge_21.1.251\mods\` 启动。

## 2. 内容完成度（~220 个 Java 文件 / 100 个注册项）

| 模块 | 状态 |
|---|---|
| 材料 / 装饰 | 游荡石系（矿/块/砖/台阶/雕纹/平滑/玻璃）、陨星岩系、热气球外壳 ✅ |
| 通用动力 | `universal_shaft`(+连接器渲染)、`redstone_resistor`、`wing`/`flap`(染色+连接体+**升力已接 Sable lift provider**)、`goo`、`slicker`(Sable 固定约束)、`extendon` ✅ |
| 物理机械 | `physics_infuser`、`phys_bearing`(ROTATE/LOCK/FREE)、brass/juryrigged 螺旋桨、**可变桨距 `blade_controller`+叶片（展长可加长/切割，`vs_clockwork:blade_length` 数据组件 + 两个配方类型，见 `docs/BLADE-LENGTH.md`）**、`gimbal_bearing`、`gyro`、`reactionwheel`、`andesite/smart_flap_bearing`、`copter_bearing`、`sugar_rocket`、`ballooner` ✅ |
| 气体物流 | 自研气体网络（质量+温度→压力、BFS 连通域、流动/泄漏/换热、10 刻阈值同步）；`duct`/`straight_duct`/`pump`/`valve`/`filter`/`redstone`/`tank`/`heat_pipe`/`intake`/`compressor`/`coal_burner`/`steam_generator`/`creative_generator`/`gas_engine`(4 个 mixin 接 Create 锅炉)/`sterling_engine`/`gas_crafter`(自定义配方)/`gas_heater`/`exhaust`(接 Create 气流)/`gas_thruster`/`gas_nozzle`/`hose_port`/`gas_backtank` ✅ |
| 奇物 / 工具 | `wanderwand`(选区→`IAreaSelector`)、`gravitron`(抓取+**装配/解体**)、screwdriver、drill、saw、三类传感器、`lodefocus`、`alt_meter`、`clock`、`solver`、飞行家护甲(贴图已修)、`wanderlite_cube/matrix`、唱片、食物、`asteroid_block`、陨石与矿物生成 ✅ |
| 交付 | `delivery_cannon`(抛物线反解发射)、`delivery_chute` ✅ |
| 集成 | JEI（气体合成/气体资料两类）、CC:Tweaked（3 个外设，**条件加载**）、Ponder（**11 个场景**，见 §7）✅ |

## 3. 守门指标（每次改完都要跑）

```powershell
powershell -ExecutionPolicy Bypass -File tools\run_gates.ps1      # 三关一起跑
# 或者分别跑：
& $ python tools\check_data_refs.py    # 必须「合计问题: 0 个」
& python tools\check_assets.py --jar # 若 [12] 报「属性快照已过期」，按提示重跑 tools/probe_blockstates.py
& python tools\check_jar.py          # 产物 jar 的 zip 结构必须 0 个问题
& python tools\check_ponder.py       # Ponder 11 个场景的 nbt/方块注册/lang 键必须 0 个问题
& python tools\selftest_data_refs.py # 守门脚本自检：14 类坏数据必须都被报出来
& python tools\selftest_assets.py    # 资源守门自检：11 类坏资源必须都被报出来
& python tools\check_ponder.py --selftest   # Ponder 自检脚本自检：4 类坏数据必须都被报出来
```

- `check_data_refs.py`：数据包引用的 `vs_clockwork:` 物品是否真的注册过；配方类型；
  音效事件 ↔ sounds.json ↔ ogg；以及 **worldgen / neoforge:biome_modifier / jukebox_song**
  三类"只在建世界时加载"的数据的结构 + 引用校验（字段依据见 `docs/DATA-CHECKS.md`）。
  **这条是拿实机崩溃换来的**：`create:sequenced_assembly` 的 ingredient 里只要有未注册物品，
  Create 会在 `SequencedRecipe.initFromSequencedAssembly` 抛 `IndexOutOfBoundsException` 把游戏打崩（已复现+修复）；
  2026-10-11 建世界崩溃后补上了后三类。
- `check_assets.py`：11 项资源检查（blockstate → 模型 → 贴图 → 打包）。除原有的整链检查外，
  2026-10-11 针对"物品/方块纹理几乎全错"的反馈重写并新增了：
  **[1][2] 注册 id 提取**（旧正则把 `.item()`/`.blockEntity()` 当方块，误报 82 条 + 漏报 40 多个机器方块，
  现抽到 `tools/cw_ids.py`）、**[7] 贴图键 `#key` 能否沿 parent 链解析**（1.21.1 对这类失败**完全静默**，
  只会在屏幕上黑紫、日志无痕 —— 这是唯一能提前发现它的静态手段）、
  **[8] 跨命名空间贴图存在性**（查 libs/*.jar + 实例 mods/*.jar + 原版 `client-*-extra.jar`）、
  **[9] png 合法性 / mcmeta 动画**（按 `AnimationMetadataSection.calculateFrameSize` 真实语义）、
  **[10] 非法资源路径**、**[11] 产物 jar ↔ 源码资源树 + 0 字节条目 + 逐条 CRC**。
  2026-10-16 针对"物理轴承/陀螺仪方块状态纹理异常"的反馈又加了
  **[12] blockstate 属性覆盖**（属性真值由 `tools/probe_blockstates.py` 实例化方块类取得，
  快照 `tools/blockstate_properties.json` 带编译产物指纹；覆盖不全 / 条件非法 = 黑紫或看不见，
  诊断与被修缺陷见 `docs/BLOCKSTATE-COVERAGE.md`）。
  诊断过程与被排除的假设（`forge:obj`、物品模型缺失、渲染层、`texture_size` …）逐条留证于 `docs/ASSET-AUDIT.md`。
- `check_jar.py` / `verify_jar_java.java`：**产物 jar 本身的 zip 完整性**。
  `Failed to load registries due to above errors` 那次真正原因是 mods 里那份 jar 字节坏了，
  5 个数据条目抛 `java.util.zip.ZipException: invalid LOC header`，只看 JSON 永远查不出来。
- `tools/install_jar.ps1`：装包（临时文件 → sha256 校验 → 同目录 Move-Item 原子替换，
  装前自动确认没有 javaw、`latest.log` 距今 > 2 分钟），并先把旧 jar 备份到 `_cw_backups/`。


## 4. 实机验证进度（每一步都是真机崩溃换来的修复）

| 关卡 | 结果 |
|---|---|
| FML 加载（mod 元数据/入口点） | ✅ 已修：曾因 CC 集成写了 `@Mod("computercraft")` 导致"为不存在的 mod 声明入口点"直接拒绝加载 |
| 注册表（RegisterEvent） | ✅ 已修：`SensorBlockBase` 忘记 `builder.add(FACING)`（1.21.1 的 `DirectionalBlock` 不代劳）导致构造期异常 |
| 模型 | ✅ 已修：8 个模型残留 `"loader": "forge:obj"` + 2 个 obj 的 `mtllib` 指向不存在的文件 |
| 数据包（创建世界） | ✅ 已修：两个序列装配中间产物没注册 → IOOBE 崩溃；4 个未实现方块的孤儿配方/战利品表已实现并恢复 |
| 建世界加载注册表（2026-10-11 20:37） | ✅ 已修：`Failed to load registries due to above errors`。5 个报错项（`jukebox_song/wanderlust`、`worldgen/{configured,placed}_feature/wanderlite_ore`、`neoforge/biome_modifier/wanderlite_{overworld,end}`）的 `Caused by` 全是 `java.util.zip.ZipException: invalid LOC header`，即 **mods 里那份 jar 字节坏了**，不是数据格式错——这三类 JSON 与 1.21.1 schema 逐字段核对后完全正确（依据见 `docs/DATA-CHECKS.md`）。已 clean 重建 + 原子替换安装，并用 `tools/check_jar.py` 与 `java.util.zip.ZipFile` 双重复核 1590 条目 0 失败 |
| 标签页构建（BuildCreativeModeTabContentsEvent） | ✅ 已修：服务端 tick 里 `Itemstack 1 vs_clockwork:wanderlite_deepslate_ore already exists in the tab's list` —— 同一物品被插入两次，来源是 Registrate 构造器默认的隐式默认标签页 `CreativeModeTabs.SEARCH`（`AbstractRegistrate#item` 会给每个物品追加一次 `tab(SEARCH)`）与各切片显式 `.tab(...)` 的 `PARENT_AND_SEARCH_TABS` 可见性撞在 search 集合上；`ClockworkRegistrate.create()` 改为 `.defaultCreativeTab((ResourceKey) null)` 关掉隐式入页，物品归属仍全由显式 `.tab(...)` 决定 |
| **进入世界 + 玩法链路** | ⏳ 待实机（物理轴承装配 / 螺旋桨推力 / 气网压力 / 机翼升力 / 气嘴喷流） |

## 5. 已知未做 / 未验证

- **Ponder 已全覆盖**：官方 11 个 nbt 全部注册（`alt_meter`、`cambered_wings`、`duct`、`flap_bearing`、
  `gyro`、`plane_tips`、`resistor`、`smart_flap_bearing`、`solid_delivery`、`wanderwand`、`wings`），
  注册表集中在 `integration/ponder/ClockworkPonderScenes`，静态校验见 `tools/check_ponder.py`（§7）。
  `phys_bearing` 官方发布包里**也没有** nbt（旧版就没做），所以它不是移植缺口。
- `wanderlite_cube/matrix` 用静态模型替代旧版自定义 BER（旧版依赖 Create 自定义 ItemRenderer + 自定 shader）。
- `incomplete_wanderwand` 配方从"铁粒/锌粒二选一"简化为 `c:nuggets/iron`（要恢复需验证 `neoforge:compound` 反序列化）。
- 实机手感全部未校验：伺服/关节阻尼、推力与升力数值、气体平衡、炮口朝向与命中率、粒子观感。
- **叶片加长 / 切割（2026-10-18 已补齐，原 `_disabled_data` 条目）**：旧版两个配方类型恢复
  （`blade_crafting` 工作台相加、`blade_cutting` 动力锯减半），展长改为原生
  `DataComponentType<Integer>`（默认 1、上限 `CWServer.maxBladeSize = 8`、旧 NBT 自动迁移），
  推力 / 渲染 / tooltip 已全链路接入，mixin 见 `mixin/content/blade/MixinSawBladeRecipe`。
  取证与差异见 `docs/BLADE-LENGTH.md`，纯逻辑自检 `tools/selftest_blade_length.py`（已挂进 `run_gates.ps1 -SelfTest`）。
  仍缺：物品图标不随长度缩放（旧版 `BladeItemRenderer` 未移植）、`blade_cutting` 没有 JEI 分类（旧版也没有）。
- 防御性加固（**已做**）：`mixin/content/sequenced_assembly/MixinSequencedRecipe` 在 `SequencedRecipe#initFromSequencedAssembly` 的 HEAD 拦下 `ingredients` 长度为 0 的步骤（WARN 打日志 + `ci.cancel()`），让**别的模组**的坏序列装配配方也只打日志不崩游戏（长度 > 0 时完全不干预）；注入点写死 `require = 0`，取舍是：以后 Create 改签名/描述符时最坏只是这条保护静默失效，绝不会因为保护代码自身注入失败而拒绝加载，代价是注入失效时不再有加载期报错提醒。

## 6. 方块实体渲染器（BER）覆盖情况（2026-10-16 补）

> 触发原因：实机反馈"物理轴承 / 陀螺方块状态纹理异常"。审计（`docs/BLOCKSTATE-COVERAGE.md`）已确认
> 资源与属性覆盖都没问题，真因是**旧版有 BER、本移植没实现** —— 方块只画了静态 blockstate 模型，
> 于是缺部件 / 不转 / 比例怪（但不会黑紫）。

### 6.1 已补齐（本轮）

| 方块 | 渲染器 | 旧版对应 | 关键组成 |
|---|---|---|---|
| `gyro` | `content/contraptions/gyro/client/GyroRenderer` | `GyroBlockEntityRenderer.kt` | `GYRO_BASE` + 自转的方块模型 + `PHYS_SHAFT`；`GyroBlock` 改回 `ENTITYBLOCK_ANIMATED` |
| `phys_bearing` | `content/contraptions/phys/bearing/client/PhysBearingRenderer` | `PhysBearingRenderer.kt` | 四片 `PHYS_*_WING` + `PHYS_ATTACHER` + `PHYS_SHAFT`，翼面随装配开合 |
| `reactionwheel` | `content/contraptions/reactionwheel/client/ReactionWheelRenderer` | `ReactionWheelRenderer.kt` | 轮子绕输入轴自转；`ReactionWheelBlock` 改回 `ENTITYBLOCK_ANIMATED` |

这些 partial 统一声明在 `content/contraptions/client/ContraptionPartials.java`，由 `index/CWClientSetup`
在 `FMLClientSetupEvent` 里提前 `init()`（必须早于模型烘焙，否则 `missingno` 黑紫；机理见该类注释）。

### 6.2 仍缺（旧版有 BER，本移植没有）

> **2026-10-17 收口注**：本节是 2026-10-16 的快照；之后 `solver`、`delivery_chute`、
> `command_seat`、`heat_pipe` 的外观/渲染已补齐，全量矩阵与证据以 `docs/RENDER-AUDIT.md` §4
> 的 F13–F16 为准。下面列表保留历史，不再作为当前缺口清单。

1. **可以直接照抄、只是还没人做**（渲染器不依赖 VS2 飞船姿态）：
   - `sugar_rocket` — `SugarRocketRenderer.kt`
   - `physics_infuser` — `PhysicsInfuserRenderer.kt`（`PHYSICS_CORE` / `STRANGE_FLUID` / `ZAP`）
   - `goo` — `GooBlockEntityRenderer.kt`（旧版 `renderSafe` 实际只调 `super`，等于没有额外绘制）
2. **需要新写**（旧版依赖 VS2 `ClientShip` 的渲染姿态换算，Sable 下没有对应物）：
   - `gimbal_bearing` — `GimbalBearingRenderer.kt`（295 行，`shipToWorld` / `worldToShip`）
   - `copter_bearing` — `CopterBearingRenderer.kt`（依赖客户端 `clientTiltQuat` 同步）
3. **依赖未移植的公共设施**：
   - 三层水晶核心（`RenderUtil.renderCubeMatrix` 的 `CRYSTAL` / `CRYSTAL_INNER` / `CRYSTAL_OUTER`）
     依赖自研 `ClockworkRenderTypes.CRYSTAL` + shader，因此 `gyro` / `phys_bearing` 的核心未画。
   - `propeller_bearing` — 旧版直接用 Create 的 `BearingRenderer<PropellerBearingBlockEntity>`
     （画 `AllPartialModels.BEARING_TOP`）；本移植的 `PropellerBearingBlockEntity` 改继承
     `AbstractBearingBlockEntity`、没有实现 Create 的 `IBearingBlockEntity`，因此接不上，
     需要另写一个只画顶盖的 BER。
4. **已按设计放弃**：`wanderlite_cube/matrix` 用静态模型替代（见 §5）。

## 7. Ponder 集成（2026-10-17 补齐）

> 官方发布包 `assets/vs_clockwork/ponder/*.nbt` 共 **11 个**，与工程树逐字节一致
> （见 `docs/OFFICIAL-ASSETS.md` §3.3）。本轮把这 11 个全部注册，注册表**只有一处**：
> `src/main/java/org/valkyrienskies/clockwork/integration/ponder/ClockworkPonderScenes.java`，
> 一行一个场景，行上注释写明"官方哪个 nbt / 旧版哪个场景 / 有什么差异"。

### 7.1 注册 API（已用 javap 核实）

| 环节 | 依据（二进制） |
|---|---|
| 插件入口 | `net.createmod.ponder.api.registration.PonderPlugin`（`getModId` / `registerScenes` / `registerTags` / `registerSharedText`）；本移植按 Simulated / Aeronautics 的惯例继承 `com.simibubi.create.foundation.ponder.CreatePonderPlugin` |
| 挂插件的时机 | `net.createmod.ponder.foundation.PonderIndex#addPlugin(PonderPlugin)`，在 `FMLClientSetupEvent` 里调用（`ClockworkPonderClient`） |
| 注册场景 | `PonderSceneRegistrationHelper#withKeyFunction(Function)` → `forComponents(T...)` → `MultiSceneBuilder#addStoryBoard(String, PonderStoryBoard)` |
| 结构图路径 | `addStoryBoard("id", ...)` 里的 `id` 经 `PonderSceneRegistrationHelper#asLocation` 变成 `vs_clockwork:id`，运行时读 `assets/vs_clockwork/ponder/<id>.nbt`（**文件缺失 = 注册期抛异常**） |
| lang 键 | 由 **`SceneBuilder#title("<id>", ...)`** 决定：`PonderSceneBuilder#title` 把 `sceneId` 置为 `namespace:id`，之后 `PonderScene#getString` 读 `vs_clockwork.ponder.<id>.header / .text_N`（**不是** nbt 名、也不是组件 id） |

> 因此每个场景类的 `scene.title("<id>", ...)` 必须与注册用的 nbt 名一致 —— 这条由
> `tools/check_ponder.py` 静态校验。

### 7.2 场景 ↔ 组件 ↔ 旧版

| 官方 nbt | 挂载组件 | 旧版场景 | 本移植差异 |
|---|---|---|---|
| `wanderwand` | `wanderwand`、`physics_infuser` | `PhysicsPonders#createShip` | 开头 0 tick 空操作删除；结构下沉的 `moveSection` 序列照旧 |
| `resistor` | `redstone_resistor` | `KineticPonders#redstoneResistor` | 文本改用 lang 键（旧版硬编码英文） |
| `alt_meter` | `alt_meter` | `PhysicsPonders#altMeter` | `moveSectionAsShip`→`animateAsShip`；粒子平面→`chasePlane`；0 tick 瞬移→1 tick |
| `flap_bearing` | `andesite_flap_bearing`、`smart_flap_bearing` | `PhysicsPonders#flap` | 无（`TargetAngle` NBT 键与 `AbstractBearingBlockEntity` 一致） |
| `smart_flap_bearing` | `smart_flap_bearing` | `PhysicsPonders#smart_flap` | 无 |
| `plane_tips` | 两种摆转轴承 + `wing`、`flap` | `PhysicsPonders#planeTips` | `chaseParallelogram` 1:1 移植；旧版末尾 TODO 仍不做 |
| `wings` | `wing`、`flap` | `PhysicsPonders#wings` | 升力由 Sable lift provider 接管，概念演示照旧 |
| `cambered_wings` | `wing`、`flap` | `PhysicsPonders#camberedWings` | `moveSectionAsShip`→`animateAsShip` |
| `gyro` | `gyro` | `PhysicsPonders#gyro` | VS2 稳定力矩→Sable 反作用轮；四向模拟杆演示照旧 |
| `solid_delivery` | `delivery_cannon`、`delivery_chute` | `OtherPonders#solid_delivery` | 无 |
| `duct` | `duct` | `KelvinPonders#duct` | **只演 text_1..3 / 9..10**：本移植没有"边类型（One-way/Filtered/Smart）"，text_4..8 暂不上台 |

### 7.3 静态校验

`tools/check_ponder.py`（`--selftest` 注入 4 类坏数据自证）：

1. 每个注册场景的 `assets/vs_clockwork/ponder/<id>.nbt` 存在且非空；
2. nbt palette 里的 `vs_clockwork:*` 必须在 Java 注册索引里（复用 `tools/cw_ids.py`），
   `create:*` 必须在 `libs/create-*.jar` 的 blockstates 里（`minecraft:*` 不查，见脚本注释）；
3. `scene.title` 的 id 必须等于 nbt 名；每个 `text(scene, N)` 用到的
   `.header` / `.text_N` 在 `en_us.json` 与 `zh_cn.json` 里都存在；
4. 反向提示：`ponder/` 下躺着但没人注册的 nbt（死资源）。

### 7.4 实机状态（2026-10-17 10:37）

装进整合包并启动后，`latest.log` 里
`PonderIndex: Registering Ponder Scenes took 521.4 ms` / `Registering Ponder Tags took 16.81 ms`，
**无任何异常**（11 个结构图在注册期全部读入并编译成功）。
仍需人工确认的是"观感"：索引页里 11 个场景是否都能点开、动画/文本时序是否对得上
（脚本只能静态校验，看不到画面）。
