# VS-Sablework — Clockwork 非官方 1.21.1 移植

> 把 Valkyrien Skies 的 **Clockwork**（Create × 航空学）移植到 **Minecraft 1.21.1 / NeoForge**，
> 物理基座从 VS2 换成了 **Sable** 的 sub-level。
>
> **本仓库是非官方移植，与 Valkyrien Skies Team、Create、Sable、Create: Aeronautics 的作者没有隶属关系。**

| 项目 | 内容 |
|---|---|
| 模组名 / Mod ID | Clockwork / `vs_clockwork` |
| 版本 | `0.6.0-1.21.1` |
| 作者 | **TCGqimingxc, Deepseek** |
| 官方发布页 | <https://www.curseforge.com/minecraft/mc-mods/sablework-clockworkvs-unofficial> |
| 源码仓库 | <https://github.com/tcgqimingxc/VS-Sablework> |
| 许可 | Apache-2.0（见 [LICENSE](LICENSE)） |

仓库根目录就是模组工程本身（原 `clockwork_unoffical/`），clone 下来即可用 Gradle 构建，没有额外的子目录层级。

## 这个模组是什么

Clockwork 原本是 Valkyrien Skies 2 时代的「Create + 飞空艇」扩展：用 Create 的应力与机械件
驱动可组装的物理结构。本移植保留这套玩法，但把物理部分改接到 **Sable** 的 sub-level 系统上，
并补齐了气体物流、可变桨距、飞行家护甲、交付炮等模块。全部内容重写在 1.21.1 / NeoForge 的
注册体系、数据包格式与渲染管线上，不是简单的版本号替换。

## 前置要求

| 依赖 | 版本 | 说明 |
|---|---|---|
| Minecraft | **1.21.1** | 仅此版本 |
| NeoForge | **21.1.x** | 构建脚本锁定 `21.1.228`（见 `gradle.properties`） |
| Create | **6.0.10** | 编译期使用 `libs/create-1.21.1-6.0.10.jar` |
| Sable | **2.0.6** | 发布/推荐运行版本；本仓库编译基线为 2.0.5，`mods.toml` 要求 `sable >= 2.0.5`，2.0.x 内兼容 |
| Create: Aeronautics（Simulated） | **1.3.2** | 含 `simulated` / `aeronautics` / `offroad` 三个 jar |
| Java | **21** | NeoForge 1.21.1 硬性要求 |

可选：JEI（19.x）、CC:Tweaked（1.110+）——只在编译期以 `compileOnly` 参与，运行期装不装都可以。

所有前置模组的 jar 都**不随本仓库分发**，需要自行下载后放入 `libs/`；文件名清单与获取方式见
[`libs/README.md`](libs/README.md)。

## 构建

### 0. 准备 `libs/`

按 [`libs/README.md`](libs/README.md) 的表格准备 11 个必需 jar，文件名必须与 `build.gradle` 中
`files('libs/...')` 的写法完全一致。JEI / CC:Tweaked 的 3 个 jar 只在需要编译对应集成时放入，
不放也能构建（只是不编译那部分集成代码）。

### 1. 标准构建（可访问 Maven 的环境）

```bash
git clone https://github.com/tcgqimingxc/VS-Sablework.git
cd VS-Sablework
./gradlew build
```

Windows：

```powershell
git clone https://github.com/tcgqimingxc/VS-Sablework.git
cd VS-Sablework
.\gradlew.bat build
```

需要 JDK 21，并且能访问 `maven.neoforged.net` / Maven Central（Minecraft、NeoForge 及
ModDevGradle 依赖由 Gradle 在线解析）。

### 2. 离线构建（本移植的开发机环境）

本机访问不了 `maven.neoforged.net`，Minecraft / NeoForge 依赖走本机 Gradle 缓存
（`neo_version=21.1.228`），Create 生态、Sable、Aeronautics、Veil、JEI、CC 全部走 `libs/`
本地 jar，因此构建需要加 `--offline`：

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-21"
.\gradlew.bat --offline build
```

> `gradle.properties` 里的 `org.gradle.java.installations.paths` 是开发机上的绝对路径。
> 换机器时请改成自己的 JDK 21 路径，或直接删除该行让 Gradle 使用 `JAVA_HOME`。

### 3. 产物

```
build/libs/vs_clockwork-0.6.0-1.21.1.jar
```

把 jar 放进 `mods/`，同时安装好前置模组即可。`gradlew runClient` / `runServer` 需要
`neoforge-<ver>-moddev-config.json`；离线缓存里可能缺少该文件，因此推荐直接在正常整合包实例中
实机测试。

## 目录结构

```text
.
├─ src/main/java/                 # 移植代码（约 300 个 .java）
├─ src/main/resources/            # 贴图、模型、语言、数据包、Ponder 结构
├─ src/main/templates/            # neoforge.mods.toml 模板（构建时展开）
├─ docs/                          # 移植过程文档（API、资源、数据、渲染、状态）
├─ _disabled_data/                # 暂不启用的数据文件原样保留（不会打进 jar）
├─ gradle/wrapper/                # Gradle Wrapper
├─ libs/                          # 仅 README；前置 jar 需自行放入（不入库）
├─ .github/workflows/build.yml    # GitHub Actions 构建工作流
├─ build.gradle / settings.gradle / gradle.properties
├─ gradlew / gradlew.bat
├─ TEMPLATE_LICENSE.txt           # NeoForge MDK 模板许可（MIT）
├─ README.md / LICENSE / .gitignore
```

`build/`、`.gradle/`、`run/`、`out/`、`local-maven/`、`libs/*.jar` 以及从构建产物解包到根目录的
`assets/`、`data/`、`META-INF/`、`org/`、`resourcepacks/` 等均被 `.gitignore` 排除；
资源正本在 `src/main/resources/`。

## 功能概览

| 模块 | 内容 |
|---|---|
| 材料 / 装饰 | 游荡石系（矿、块、砖、台阶、雕纹、平滑、玻璃）、陨星岩系、热气球外壳 |
| 通用动力 | `universal_shaft`、`redstone_resistor`、机翼 / 襟翼（染色 + 连接体 + Sable 升力）、`goo`、`slicker`、`extendon` |
| 物理机械 | `physics_infuser`、`phys_bearing`（ROTATE/LOCK/FREE）、螺旋桨、可变桨距叶片与 `blade_controller`、`gimbal_bearing`、`gyro`、`reactionwheel`、襟翼轴承、`copter_bearing`、`sugar_rocket`、`ballooner` |
| 气体物流 | 自研气体网络（质量 + 温度 → 压力、BFS 连通域、流动 / 泄漏 / 换热）；管道、泵、阀、过滤器、储罐、换热管、进气口、压缩机、燃烧炉、蒸汽 / 燃气 / 斯特林引擎、气体合成、排气、推进器、气嘴、软管、储气背包 |
| 奇物 / 工具 | `wanderwand`（选区）、`gravitron`（抓取 / 装配）、螺丝刀、钻头、锯、三类传感器、`lodefocus`、`alt_meter`、`clock`、`solver`、飞行家护甲、`wanderlite` 方块 / 矩阵、唱片与食物 |
| 交付 | `delivery_cannon`（抛物线反解发射）、`delivery_chute` |
| 集成 | JEI（气体合成 / 资料页）、CC:Tweaked（3 个外设，条件加载）、Ponder（11 个官方场景全部注册） |

完整完成度、实机验证进度与未做项见 [`docs/PORT-STATUS.md`](docs/PORT-STATUS.md)；
渲染缺口见 [`docs/RENDER-AUDIT.md`](docs/RENDER-AUDIT.md)；
方块状态覆盖见 [`docs/BLOCKSTATE-COVERAGE.md`](docs/BLOCKSTATE-COVERAGE.md)。

## 文档索引

| 文档 | 内容 |
|---|---|
| [`docs/PORT-STATUS.md`](docs/PORT-STATUS.md) | 移植状态、模块清单、实机验证记录、已知缺口 |
| [`docs/API-REFERENCE.md`](docs/API-REFERENCE.md) | 用 javap 核实过的 Sable / Create / NeoForge API |
| [`docs/PORT-CONVENTIONS.md`](docs/PORT-CONVENTIONS.md) | 命名、注册、资源路径等移植约定 |
| [`docs/ASSET-AUDIT.md`](docs/ASSET-AUDIT.md) / [`docs/RENDER-AUDIT.md`](docs/RENDER-AUDIT.md) | 资源与渲染审计、BER 缺口 |
| [`docs/OFFICIAL-ASSETS.md`](docs/OFFICIAL-ASSETS.md) | 官方资源树 vs 工程资源树差分结论 |
| [`docs/DATA-CHECKS.md`](docs/DATA-CHECKS.md) | 数据包 / 世界生成 / 音效的校验依据 |
| [`docs/BLOCKSTATE-COVERAGE.md`](docs/BLOCKSTATE-COVERAGE.md) | 方块状态与属性覆盖审计 |
| [`docs/DUCT-TANK-LOOK.md`](docs/DUCT-TANK-LOOK.md) / [`docs/DUCT-TANK-UV.md`](docs/DUCT-TANK-UV.md) | 储罐外观与 UV |
| [`docs/GRAVITRON-SOUNDS.md`](docs/GRAVITRON-SOUNDS.md) | 引力子音效 |
| [`docs/AUDIT-R23.md`](docs/AUDIT-R23.md) | R23 轮审计记录 |

## 许可与致谢

- **本仓库代码**：Apache License 2.0，全文见 [LICENSE](LICENSE)。
- **上游 Clockwork**：Copyright Valkyrien Skies Team，Apache-2.0
  （<https://github.com/ValkyrienSkies/Clockwork>）。本移植的玩法设计、部分资源与数据来自上游
  1.20.1 工程及官方发布包，在此致谢 **Valkyrien Skies Team** 与所有 Clockwork 贡献者。
- **NeoForge MDK 模板**：`TEMPLATE_LICENSE.txt`（MIT, NeoForged）。
- **前置模组**：Create、Flywheel / Ponder / Registrate、Sable、Veil、Create: Aeronautics（Simulated /
  Aeronautics / Offroad）、JEI、CC:Tweaked 的版权归各自作者所有，**仅作为前置依赖，不随本仓库
  分发**；使用或再分发请遵守各自的许可。
- 上游对美术 / 音频资源如有额外声明（例如 ARR），以官方发布页与上游仓库为准；本仓库仅作非官方
  移植用途，相关权利人如有异议可联系移除。

## 非官方声明

本项目为**非官方**移植，未经 Valkyrien Skies Team、Create、Sable 或 Create: Aeronautics 作者授权
或背书。按 Apache-2.0 的「AS IS」条款提供，不附带任何担保；安装前请备份存档，模组冲突 / 崩溃 /
存档损坏的风险由使用者自负。发布页：
<https://www.curseforge.com/minecraft/mc-mods/sablework-clockworkvs-unofficial>。

---

## English

**VS-Sablework** is an unofficial port of [Clockwork](https://github.com/ValkyrienSkies/Clockwork)
(Create × Aeronautics) to **Minecraft 1.21.1 / NeoForge**, rebuilt on top of **Sable** sub-levels
instead of Valkyrien Skies 2. The repository root is the Gradle project itself. It is not
affiliated with or endorsed by the Valkyrien Skies Team, Create, Sable or Create: Aeronautics.

- **Requires:** Minecraft 1.21.1, NeoForge 21.1.x, Create 6.0.10, Sable 2.0.6 (compile baseline
  2.0.5, `mods.toml` requires `>= 2.0.5`), Create: Aeronautics 1.3.2, Java 21.
- **Build:** put the 11 prerequisite jars listed in [`libs/README.md`](libs/README.md) into `libs/`,
  then run `./gradlew build` (Windows: `.\gradlew.bat build`). Add `--offline` when Maven is
  unreachable and the Gradle cache is already populated.
- **Output:** `build/libs/vs_clockwork-0.6.0-1.21.1.jar`.
- **License:** Apache-2.0. Third-party mods are prerequisites only and are not distributed here.
- **Release page:** <https://www.curseforge.com/minecraft/mc-mods/sablework-clockworkvs-unofficial>
