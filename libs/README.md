# `libs/` —— 本地前置依赖（不入库）

本工程用 `files('libs/*.jar')` 的方式引用前置模组，**这些 jar 属于第三方版权物，不随本仓库分发**。
克隆仓库后必须自行把下表中的 jar 放进本目录，`build.gradle` 才能编译。

运行需要自行准备这些前置 jar：**Create 6.0.10 / Sable 2.0.6 / Create: Aeronautics 1.3.2
及其依赖**（Flywheel、Ponder、Registrate、Veil、Sable Companion 等）。

> **Sable 版本说明**：发布/推荐运行版本为 **Sable 2.0.6**；本工程 `build.gradle` 的编译基线是
> `sable-neoforge-1.21.1-2.0.5.jar`，`mods.toml` 要求 `sable >= 2.0.5`，2.0.x 内兼容。
> 文件名必须与下表**完全一致**；如果要换版本，请同时修改 `build.gradle` 里对应的文件名，
> 并把根目录 `gradle.properties` 的 `create_version` / `sable_version` / `aeronautics_version`
> 一起改掉（`mods.toml` 的前置版本范围由它们生成）。

## 编译 + 运行必需

| 文件名 | 对应模组 / 版本 | 获取方式 |
|---|---|---|
| `create-1.21.1-6.0.10.jar` | Create 6.0.10 | CurseForge / Modrinth 的 Create 页面 |
| `ponder-neoforge-1.0.82+mc1.21.1.jar` | Ponder（Create 依赖） | Create 依赖链 |
| `flywheel-neoforge-1.21.1-1.0.6.jar` | Flywheel | Create 依赖链 |
| `flywheel-neoforge-api-1.21.1-1.0.6-44.jar` | Flywheel API | Create 依赖链 |
| `Registrate-MC1.21-1.3.0+67.jar` | Registrate | Create 依赖链 |
| `sable-neoforge-1.21.1-2.0.5.jar` | Sable 2.0.5（编译基线；运行推荐 2.0.6） | CurseForge / Modrinth 的 Sable 页面 |
| `sable-companion-common-1.21.1-1.6.0.jar` | Sable Companion | Sable 依赖链 |
| `veil-neoforge-1.21.1-4.4.1.jar` | Veil | Sable 依赖链 |
| `dev.simulated_team.simulated.simulated-neoforge-1.21.1-1.3.2.jar` | Create: Aeronautics — Simulated | CurseForge / Modrinth |
| `dev.eriksonn.aeronautics.aeronautics-neoforge-1.21.1-1.3.2.jar` | Create: Aeronautics | 同上 |
| `dev.ryanhcode.offroad.offroad-neoforge-1.21.1-1.3.2.jar` | Create: Aeronautics — Offroad | 同上 |

## 编译期集成（同样需要放入，运行期可选）

这 3 个 jar 只参与编译（`compileOnly`），但源码里存在对应的 JEI / CC 集成类，缺少时
`compileJava` 会因找不到符号而失败；运行期是否安装由玩家决定。完整构建共需 14 个 jar
（11 个运行前置 + 3 个编译期集成）。

| 文件名 | 对应模组 |
|---|---|
| `jei-1.21.1-common-api-19.27.0.340.jar` | JEI（`compileOnly`） |
| `jei-1.21.1-neoforge-api-19.27.0.340.jar` | JEI API（`compileOnly`） |
| `cc-tweaked-1.21.1-forge-1.120.2.jar` | CC:Tweaked（`compileOnly`） |

## 为什么不提交 jar

Create / Sable / Create: Aeronautics / Flywheel / Registrate / JEI / CC:Tweaked 的版权归各自作者所有，
本仓库只分发自己编写的移植代码与资源，不分发这些 jar。缺少 `libs/*.jar` 时 Gradle 会报
`Could not find ...` / `No such file`，按上表补齐即可。

Minecraft / NeoForge 本体不放在这里：它们由 Gradle 从 `maven.neoforged.net`（或本机 Gradle 缓存）
解析，版本由 `gradle.properties` 的 `neo_version` 决定。
