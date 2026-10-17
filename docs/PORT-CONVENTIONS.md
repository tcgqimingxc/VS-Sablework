# Clockwork 1.21.1 移植约定（所有参与者必读）

> 目标：把 `Clockwork-1.20.1-main`（1.20.1 + Valkyrien Skies 2）移植到
> `clockwork_unoffical`（Minecraft 1.21.1 / NeoForge 21.1.228），前置为
> Create 6.0.10 + Sable 2.0.5 + Create: Aeronautics(Simulated) 1.3.2。

## 0. 铁律：不许猜 API

写任何一行调用前，必须用下面两种方式之一确认签名：

1. **二进制（最终裁判）**：`libs/*.jar`
   ```powershell
   & "C:\Users\yql11\java\dragonwell-21.0.8.0.8+9-GA\bin\javap.exe" `
     -classpath "clockwork_unoffical\libs\sable-neoforge-1.21.1-2.0.5.jar" -p dev.ryanhcode.sable.xxx.Yyy
   ```
2. **源码范例**：`Simulated-Project-main/`（用 Sable 的真实写法）、`sable-main/`
   —— 直接照抄它们的调用方式，比自己推导安全。

旧版代码（`Clockwork-1.20.1-main/`）**只用于对照玩法行为**，它的 VS2 API 全部失效，不要照抄 API。

## 1. 目录与包约定

```
src/main/java/org/valkyrienskies/clockwork/
├── ClockworkMod.java          // @Mod 入口，只做初始化编排
├── ClockworkRegistrate.java   // CreateRegistrate 子类
├── config/                    // CWServer / CWClient / ClockworkConfig
├── index/                     // CWBlocks / CWItems / CWBlockEntities / CWSounds / CWParticles / CWTags / CWCreativeTabs
├── platform/                  // 与 Sable 打交道的薄封装（施力、装配、子空间查询）
├── content/
│   ├── contraptions/          // 物理机械（轴承 / 螺旋桨 / 陀螺 / 反作用轮 …）
│   ├── logistics/gas/         // 气体物流
│   ├── curiosities/           // 工具 / 护甲 / 材料 / 陨石
│   ├── kinetics/              // 通用轴 / 电阻 / 座椅等
│   └── worldgen/              // 陨石与矿物生成
└── util/                      // 无状态的纯工具
```

- **一个方块一个包**：`content/<类目>/<snake_case 名字>/XxxBlock.java`、`XxxBlockEntity.java`、`XxxRenderer.java`。
- **注册不集中写死**：公共 `index/CWBlocks.java`（材料）、`index/CWItems.java`（材料物品）由 Lead 维护；
  各切片**只在自己的包内**新建注册入口类，避免多人同时改同一个文件：
  | 切片 | 注册入口类 | 负责范围 |
  |---|---|---|
  | 物理机械 | `content/contraptions/CWContraptionsIndex#register()` | `content/contraptions/**`、`platform/**` |
  | 气体物流 | `content/logistics/gas/CWGasIndex#register()` | `content/logistics/gas/**` |
  | 通用动力 | `content/kinetics/CWKineticsIndex#register()` | `content/kinetics/**`、`content/physicalities/**` |
  | 奇物工具 | `content/curiosities/CWCuriositiesIndex#register()` | `content/curiosities/**`、`content/worldgen/**` |
  这些 `register()` 由 Lead 在 `ClockworkMod` 里按顺序调用（切片完成后告知 Lead 类名即可）。

## 2. 注册风格（照抄 Create/Aeronautics）

```java
private static final ClockworkRegistrate REGISTRATE = ClockworkMod.REGISTRATE;

public static final BlockEntry<FooBlock> FOO = REGISTRATE.block("foo", FooBlock::new)
        .initialProperties(SharedProperties::softMetal)
        .properties(p -> p.noOcclusion())
        .addLayer(() -> RenderType::cutoutMipped)     // 视觉层（Registrate 标记过时但生态通行）
        .tag(BlockTags.MINEABLE_WITH_PICKAXE)
        .item()
        .tab(CWCreativeTabs.physicalitiesKey())       // 标签用 ResourceKey
        .build()
        .register();
```

- 方块实体：`REGISTRATE.blockEntity("foo", FooBlockEntity::new).validBlocks(FOO).register();`
- 资源（模型/方块状态/贴图/语言）沿用旧版拷贝过来的 `src/main/resources/assets/vs_clockwork/**`，
  **不要**再调 `.blockstate(...)` 生成，避免覆盖。
- 数据（配方/战利品/标签）在 `src/main/resources/data/vs_clockwork/**`，1.21 起目录名是**单数**
  （`recipe/`、`loot_table/`、`tags/block/` …），旧版的复数目录需要重命名。

## 3. 物理：用 Sable，不要用 VS2

- 结构 = `ServerSubLevel`；装配入口是 Aeronautics 的
  `dev.simulated_team.simulated.util.SimAssemblyHelper#assembleFromSingleBlock(...)`（已用 javap 核实），
  或 Sable 的 `dev.ryanhcode.sable.api.SubLevelAssemblyHelper`。
- 施力：`RigidBodyHandle.of(serverSubLevel)` → `applyImpulseAtPoint(Vector3dc, Vector3dc)` /
  `applyLinearAndAngularImpulse(...)` / `applyForcesAndReset(ForceTotal)`（已核实）。
- 物理回调：`dev.ryanhcode.sable.platform.SableEventPlatform.INSTANCE.onPhysicsTick(...)`；
  方块级持续作用优先实现 `dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor` /
  `BlockSubLevelLiftProvider`（升力/阻力）。
- 力分组用 `ForceGroups.PROPULSION` / `BALLOON_LIFT` / `LIFT` 等（已核实存在）。
- **不要**引入 `org.valkyrienskies.core` / `org.valkyrienskies.mod`（VS2）依赖，它不在 1.21.1 依赖里。

## 4. 代码风格（用户要求：简洁、无冗余、便于维护）

- **中文注释**：类头说明"这个类是干什么的、对应旧版哪个类、移植时的关键差异"；
  每个不直观的常量/公式旁写清来源与单位。
- 不要保留旧版里注释掉的死代码、`TODO: idk`、未使用的 import / 字段。
- 同一个逻辑只写一遍：公共行为抽到 `util/` 或基类；不要在多个方块里复制粘贴。
- 每个类尽量 < 300 行；超过就拆（Block / BlockEntity / Renderer / Behaviour 分开）。
- 面向玩家可见的行为，如果与旧版不同，必须在类头注释里写明差异与原因。

## 5. 验收

- 每个切片结束时项目必须能编译：
  ```powershell
  cd clockwork_unoffical
  $env:JAVA_HOME="C:\Users\yql11\java\dragonwell-21.0.8.0.8+9-GA"
  .\gradlew.bat --offline build
  ```
  （`--offline` 是必须的：本机访问不了 maven.neoforged.net，依赖来自本机 Gradle 缓存与 `libs/`。）
- 若报错来自**别人负责的文件**，不要改别人的文件，记录下来在汇报里说明即可。
