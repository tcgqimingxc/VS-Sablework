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
| 问题反馈 | 同上（CurseForge 评论区 / Issues 入口） |
| 许可 | Apache-2.0（见 [LICENSE](LICENSE)） |

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
| Sable | **2.0.6** | 发布页推荐版本；本仓库编译基线为 2.0.5，`mods.toml` 要求 `>= 2.0.5` |
| Create: Aeronautics（Simulated） | **1.3.2** | 含 `simulated` / `aeronautics` / `offroad` 三个 jar |
| Java | **21** | NeoForge 1.21.1 硬性要求 |

可选：JEI（19.x）、CC:Tweaked（1.110+）——只在编译期以 `compileOnly` 参与，运行期装不装都可以。
所有前置的 jar 都**不随本仓库分发**，获取方式见 [`clockwork_unoffical/libs/README.md`](clockwork_unoffical/libs/README.md)。

## 构建

### 0. 准备 `libs/`

把 [`clockwork_unoffical/libs/README.md`](clockwork_unoffical/libs/README.md) 表里的 11 个必需 jar
放进 `clockwork_unoffical/libs/`，文件名必须与 `build.gradle` 中的一致。

### 1. 本机离线构建（当前开发机的真实环境）

本机无法访问 `maven.neoforged.net`，Minecraft / NeoForge 依赖走**本机 Gradle 缓存**
（`neo_version=21.1.228`），Create 生态、Sable、Aeronautics、Veil、JEI、CC 全部走 `libs/` 本地 jar，
所以构建**必须加 `--offline`**：

```powershell
cd clockwork_unoffical
$env:JAVA_HOME = "C:\path\to\jdk-21"      # 任意 JDK 21；本机为 Dragonwell 21
.\gradlew.bat --offline build
```

### 2. 正常联网环境下的标准命令

网络可达 `maven.neoforged.net` / Maven Central 时，去掉 `--offline` 即可：

```bash
cd clockwork_unoffical
./gradlew build
```

> `gradle.properties` 里的 `org.gradle.java.installations.paths` 是开发机上的绝对路径，
> 换机器时请改成自己的 JDK 21 路径，或直接删除该行让 Gradle 用 `JAVA_HOME`。

### 3. 产物与运行

- 产物：`clockwork_unoffical/build/libs/vs_clockwork-0.6.0-1.21.1.jar`
- 实机验证：把上面的 jar 丢进 `mods/` 即可（同时装好前置）。
- `gradlew runClient/runServer` 需要 `neoforge-<ver>-moddev-config.json`；该文件在离线缓存里
  可能缺失，因此本仓库不保证离线环境下开发运行配置可用，建议直接用整合包测试。

### 4. 静态守门脚本（可选，但改完资源/数据建议跑）

`tools/` 下有一套 Python 检查脚本（资源链、数据包引用、Ponder、jar 结构、方块状态属性等）：

```powershell
powershell -ExecutionPolicy Bypass -File tools\run_gates.ps1
```

脚本依赖本机的 Python 与部分 `libs/*.jar`（用于交叉校验），细节见
[`tools/README.md`](tools/README.md) 与 [`clockwork_unoffical/docs/PORT-STATUS.md`](clockwork_unoffical/docs/PORT-STATUS.md) §3。

## 目录结构

```
.
├─ clockwork_unoffical/              # 模组工程（唯一构建入口）
│  ├─ src/main/java/                 # 移植代码（约 300 个 .java）
│  ├─ src/main/resources/            # 贴图、模型、语言、数据包、Ponder 结构
│  ├─ src/main/templates/            # neoforge.mods.toml 模板（构建时展开）
│  ├─ docs/                          # 移植过程文档（12 篇，见下）
│  ├─ gradle/wrapper/                # Gradle Wrapper（9.2.1）
│  ├─ libs/                          # 本地前置 jar（不入库，只有 README）
│  ├─ build.gradle / settings.gradle / gradle.properties
│  └─ README.md                      # NeoForge MDK 模板原始说明（保留）
├─ docs/                             # 专题长文：资源映射、储罐、Extendon 朝向
├─ tools/                            # 资源/数据/渲染静态校验脚本
├─ .gitignore
├─ LICENSE                           # Apache-2.0
└─ README.md
```

> 参考资料源码树（`Clockwork-1.20.1-main/`、`sable-main/`、`Simulated-Project-main/` 等）、
> 官方资源解包目录、旧 jar 备份、构建产物均**不入库**，原因见 [`.gitignore`](.gitignore)。
> 上游 Clockwork 源码请到 <https://github.com/ValkyrienSkies/Clockwork> 获取。

## 功能模块概览

| 模块 | 内容 |
|---|---|
| 材料 / 装饰 | 游荡石系（矿、块、砖、台阶、雕纹、平滑、玻璃）、陨星岩系、热气球外壳 |
| 通用动力 | `universal_shaft`、`redstone_resistor`、机翼 / 襟翼（染色 + 连接体 + Sable 升力）、`goo`、`slicker`、`extendon` |
| 物理机械 | `physics_infuser`、`phys_bearing`（ROTATE/LOCK/FREE）、螺旋桨、可变桨距叶片与 `blade_controller`、`gimbal_bearing`、`gyro`、`reactionwheel`、襟翼轴承、`copter_bearing`、`sugar_rocket`、`ballooner` |
| 气体物流 | 自研气体网络（质量 + 温度 → 压力、BFS 连通域、流动 / 泄漏 / 换热）；管道、泵、阀、过滤器、储罐、换热管、进气口、压缩机、燃烧炉、蒸汽 / 燃气 / 斯特林引擎、气体合成、排气、推进器、气嘴、软管、储气背包 |
| 奇物 / 工具 | `wanderwand`（选区）、`gravitron`（抓取 / 装配）、螺丝刀、钻头、锯、三类传感器、`lodefocus`、`alt_meter`、`clock`、`solver`、飞行家护甲、`wanderlite` 方块 / 矩阵、唱片与食物 |
| 交付 | `delivery_cannon`（抛物线反解发射）、`delivery_chute` |
| 集成 | JEI（气体合成 / 资料页）、CC:Tweaked（3 个外设，条件加载）、Ponder（11 个官方场景全部注册） |

完整完成度、实机验证进度与未做项见
[`clockwork_unoffical/docs/PORT-STATUS.md`](clockwork_unoffical/docs/PORT-STATUS.md)；
渲染缺口见 [`RENDER-AUDIT.md`](clockwork_unoffical/docs/RENDER-AUDIT.md)；
方块状态覆盖见 [`BLOCKSTATE-COVERAGE.md`](clockwork_unoffical/docs/BLOCKSTATE-COVERAGE.md)。

## 文档索引

| 文档 | 内容 |
|---|---|
| [`PORT-STATUS.md`](clockwork_unoffical/docs/PORT-STATUS.md) | 移植状态、模块清单、实机验证记录、已知缺口 |
| [`API-REFERENCE.md`](clockwork_unoffical/docs/API-REFERENCE.md) | 用 javap 核实过的 Sable / Create / NeoForge API |
| [`PORT-CONVENTIONS.md`](clockwork_unoffical/docs/PORT-CONVENTIONS.md) | 命名、注册、资源路径等移植约定 |
| [`ASSET-AUDIT.md`](clockwork_unoffical/docs/ASSET-AUDIT.md) / [`RENDER-AUDIT.md`](clockwork_unoffical/docs/RENDER-AUDIT.md) | 资源与渲染审计、BER 缺口 |
| [`OFFICIAL-ASSETS.md`](clockwork_unoffical/docs/OFFICIAL-ASSETS.md) | 官方资源树 vs 工程资源树差分结论 |
| [`DATA-CHECKS.md`](clockwork_unoffical/docs/DATA-CHECKS.md) | 数据包 / 世界生成 / 音效的校验依据 |
| [`DUCT-TANK-LOOK.md`](clockwork_unoffical/docs/DUCT-TANK-LOOK.md) / [`DUCT-TANK-UV.md`](clockwork_unoffical/docs/DUCT-TANK-UV.md) | 储罐外观与 UV |
| [`GRAVITRON-SOUNDS.md`](clockwork_unoffical/docs/GRAVITRON-SOUNDS.md) | 引力子音效 |
| [`docs/TEXTURE-MAP.md`](docs/TEXTURE-MAP.md) / [`docs/GAS-TANK.md`](docs/GAS-TANK.md) / [`docs/EXTENDON-ORIENTATION.md`](docs/EXTENDON-ORIENTATION.md) | 根目录专题长文 |

## 许可与致谢

- **本仓库代码**：Apache License 2.0，全文见 [LICENSE](LICENSE)。
- **上游 Clockwork**：Copyright Valkyrien Skies Team，Apache-2.0。本移植的玩法设计、部分资源与
  数据来自上游 1.20.1 工程及官方发布包，在此致谢 **Valkyrien Skies Team** 与所有 Clockwork 贡献者。
- **NeoForge MDK 模板**：`clockwork_unoffical/TEMPLATE_LICENSE.txt`（MIT, NeoForged）。
- **前置模组**：Create、Flywheel / Ponder / Registrate、Sable、Veil、Create: Aeronautics（Simulated /
  Aeronautics / Offroad）、JEI、CC:Tweaked 的版权归各自作者所有，**仅作为前置依赖，不随本仓库分发**；
  使用或再分发请遵守各自的许可。
- 上游对美术 / 音频资源如有额外声明（例如 ARR），以官方发布页与上游仓库为准；本仓库仅作非官方移植用途，
  相关权利人如有异议可联系移除。

## 免责声明

本项目为**非官方**移植，未经 Valkyrien Skies Team、Create、Sable 或 Create: Aeronautics 作者授权或背书。
按 Apache-2.0 的「AS IS」条款提供，不附带任何担保；安装前请备份存档，模组冲突 / 崩溃 / 存档损坏的风险由使用者自负。
发布页：<https://www.curseforge.com/minecraft/mc-mods/sablework-clockworkvs-unofficial>。

---

## English

**VS-Sablework** is an unofficial port of [Clockwork](https://github.com/ValkyrienSkies/Clockwork)
(Create × Aeronautics) to **Minecraft 1.21.1 / NeoForge**, rebuilt on top of **Sable** sub-levels
instead of Valkyrien Skies 2. It is not affiliated with or endorsed by the Valkyrien Skies Team,
Create, Sable or Create: Aeronautics.

- **Requires:** Minecraft 1.21.1, NeoForge 21.1.x, Create 6.0.10, Sable 2.0.6, Create: Aeronautics 1.3.2, Java 21.
- **Build:** put the prerequisite jars listed in `clockwork_unoffical/libs/README.md` into
  `clockwork_unoffical/libs/`, then run `./gradlew build` (add `--offline` when Maven is unreachable).
  Output: `clockwork_unoffical/build/libs/vs_clockwork-0.6.0-1.21.1.jar`.
- **License:** Apache-2.0. Third-party mods are prerequisites only and are not distributed here.
- **Release page:** <https://www.curseforge.com/minecraft/mc-mods/sablework-clockworkvs-unofficial>
