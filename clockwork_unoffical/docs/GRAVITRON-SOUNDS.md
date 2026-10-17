# 重力枪音效清单与 LOCK / UNLOCK 选型依据

> 起因（三次实机反馈原话）：
> 1. **「重力枪锁定/解锁音效错误运用了抓取/释放音效」** —— LOCK / UNLOCK 直接复用了
>    `GRAVITRON_GRAB` / `GRAVITRON_RELEASE`，听感不对，需要区分开。
> 2. **「重力枪怎么调用的是航空学的锁定/解锁音效？不是应该是自己自带的音效吗？」** ——
>    上一轮把采样指向了 Aeronautics(Simulated) 物理手杖的 `simulated:item/physics_staff/lock_*`
>    与 `extinguish_*`，属于跨模组依赖，用户要求**只用本模组自带的音效**。
> 3. **「重力枪锁定/解锁的音调怎么不对啊（感觉太高了），你可以从 clockwork 的源代码参考一下」** ——
>    上一轮为了拉开两条采样，人为把 LOCK 移调到 pitch 1.1、UNLOCK 压到 0.9。旧版**从不移调**，
>    本轮按旧版真实参数还原为 **volume 1.0 / pitch 1.0**。
>
> 本文把"到底有哪些音效可用""旧版到底用什么参数""为什么最后选这两个"写成可复核的事实
> （含客观测量与 `文件:行号` 证据），免得下一个人再从文件名猜。
>
> **结论先说**：LOCK = 本模组 `gravitron_freeze`、UNLOCK = 本模组 `gravitron_fling`，
> 两者都按旧版 **volume 1.0f / pitch 1.0f** 播放（**移调已移除**：用户实机反馈偏高 + 旧版未移调，
> 见 §2.1）。**不使用任何跨模组采样**，`sounds.json` 里已无 `simulated:` 引用。

## 1. 可用音效清点（只列事实）

### 1.1 本模组 `assets/vs_clockwork/sounds/**`（全部 61 个 ogg）

重力枪相关的 6 个（**全部是旧版 1.20.1 原样带过来的**，本移植没有新增任何 ogg）：

| ogg | 时长 | 全曲质心 | 起音 | 旧版用途（1.20.1） |
|---|---|---|---|---|
| `gravitron_start.ogg` | 3.66 s | 4470 Hz | 612 ms（峰值 1281 ms） | `GravitronHandler.tick` 拿起枪（`GRAVITRON_START`） |
| `gravitron_grab.ogg` | 3.23 s | 472 Hz | 594 ms（峰值 643 ms） | `GrabTool` 抓住结构（`GRAVITRON_GRAB`） |
| `gravitron_release.ogg` | 3.38 s | 409 Hz | 27 ms（峰值 44 ms） | `GrabTool.dropShip` 放开（`GRAVITRON_RELEASE`） |
| `gravitron_shutdown.ogg` | 4.56 s | 1980 Hz | 0 ms | `GravitronHandler.tick` 收起枪（`GRAVITRON_SHUTDOWN`） |
| `gravitron_fling.ogg` | 5.01 s | 2319 Hz | 32 ms（峰值 45 ms） | 左键"发射/甩出"（`GRAVITRON_LAUNCH`，`GravitronLeftClickPacket.kt:67-74` 以 **1f / 1f** 播放；本移植无此左键动作 → **现在用作 UNLOCK 采样**） |
| `gravitron_freeze.ogg` | 4.08 s | 2635 Hz | 27 ms（峰值 44 ms） | 超充枪左键把船 `isStatic` 取反（`GRAVITRON_FREEZE`，`GravitronLeftClickPacket.kt:82-89` 以 **1f / 1f** 播放 → **现在用作 LOCK 采样**） |

> 全曲质心是 `tools/sfx_stats.py` 的幅度加权值（整段 FFT）。它对"开头一记亮咔 + 后面低频体"
> 这种音会偏高，所以下文的听感判断一律以**分段（时间线）**数据为准，见 §1.3。

其余可当"机械咔哒"候选用的本模组 ogg：`tick` / `tock`（各 0.27 s，起音 3.6~4.0 ms、
前 100 ms 能量占比 1.00，是**时钟方块的钟摆声**，见 `ClockBlockEntity:124`）、
`hose_attach` / `hose_release`（0.45 / 0.37 s，软管接/脱）、`joint_break`（1.10 s，断裂）、
`gear_whirr`（1.30 s，起音 230 ms 的转动长音）、`wand_weld` / `wand_start`、
`metal_creak_1..8`（吱呀）、`doink` / `boing` / `thwoom`。
它们都已被否决，理由见 §4。

### 1.2 其它模组（**本轮全部不采用**）

| 来源 | 曾经/可能被借用的采样 | 为什么不用 |
|---|---|---|
| Simulated（Aeronautics bundled） | `item/physics_staff/lock_1..3`（1.5~1.85 s）、`extinguish_1..3`（0.92 s） | **上一轮用过，被用户点名否决**。而且 lock/unlock 在上游是同一条采样，两个方向本来就分不开；引用别家内部资源路径还会因上游换版本而静默失效 |
| Create 6.0.10 | `contraption_assemble`（= 木活板门开）、`wrench_rotate`（= 木活板门关）、`contraption_disassemble`（= 铁活板门关） | 语义是"装配/解体"，会和本模组既有装配/解体音撞车；`wrench_*` 是木活板门/拾取音，材质感与重力枪的金属/能量族不搭 |

**规则**：本模组的声音事件一律只引用 `vs_clockwork:` 自己的 ogg，跨模组采样一律不用
（上游改版本、挪文件时那种引用会静默失效：字幕还在，日志里只剩找不到采样）。

### 1.3 五个候选的客观听感数据（本轮实测）

`tools/sfx_stats.py`（整体标量）：

| 文件 | 时长 | 峰值@ | 起音 | 前 100 ms 能量占比 | 包络尾长 | 过零率 |
|---|---|---|---|---|---|---|
| `gravitron_freeze` | 4.08 s | 44 ms | 27 ms | 0.09 | 480 ms | 773 |
| `gravitron_fling` | 5.01 s | 45 ms | 32 ms | 0.26 | 1120 ms | 641 |
| `gravitron_grab` | 3.23 s | 643 ms | 594 ms | 0.02 | 590 ms | 558 |
| `gravitron_release` | 3.38 s | 44 ms | 27 ms | 0.05 | 350 ms | 491 |

`tools/sfx_compare.py`（分段，dB 相对各自的 10 ms RMS 包络峰值）：

| 文件 | 0–0.1 s | 0.1–0.5 s | 0.5–1.0 s | 1.0–1.5 s | 1.5–2.5 s | 2.5–3.3 s | 3.3–4.2 s | 4.2–5.1 s |
|---|---|---|---|---|---|---|---|---|
| `gravitron_freeze` | −4.1 | −6.1 | **−2.6** | −14.9 | −32.6 | −47.4 | −65.9 | — |
| `gravitron_fling` | −3.2 | −7.3 | −10.0 | −18.0 | −29.3 | −35.9 | −46.2 | −68.3 |
| `gravitron_grab` | −13.1 | −8.7 | −5.3 | −15.5 | −38.0 | −66.3 | — | — |
| `gravitron_release` | −6.4 | −5.5 | −3.7 | −6.5 | −29.2 | −63.3 | — | — |

前 100 ms 的音色（功率质心 / <200 Hz 能量占比）：

| 文件 | 前 100 ms 质心 | 前 100 ms 低频占比 | 整曲低频占比 | 整曲 200–1000 Hz 占比 |
|---|---|---|---|---|
| `gravitron_freeze` | **1163 Hz** | **20.3%**（亮"咔"） | 98.2% | 1.6% |
| `gravitron_fling` | 316 Hz | 74.9%（闷"砰"） | 62.7% | 36.0% |
| `gravitron_grab` | — | — | 78.2% | 21.8% |
| `gravitron_release` | — | — | 69.0% | 30.9% |

读法（三条互相独立的差异）：

1. **起音音色**：freeze 的前 100 ms 是一记 1163 Hz 的亮"咔"（低频只占 20%），
   随后向下扫 614 → 430 → 305 → 141 Hz；fling 的前 100 ms 是 316 Hz 的闷"砰"（低频 75%）。
2. **时长与衰减**：freeze 最响的一段是 0.5–1.0 s，1.5 s 后 −33 dB、3.3 s 后基本无声；
   fling 从 1.0 s 起一路慢衰减，2.5–4.2 s 仍保有 −36 ~ −46 dB 的尾巴。
3. **频段重心**：整曲 freeze 98.2% 在 200 Hz 以下，fling 只有 62.7%（另有 36% 在 200–1000 Hz），
   所以 freeze 是"低频结冰闷响"、fling 是"带中频力感的放能长音"。

**但必须承认一个坑**：只取**开头 0.5 s** 时两者的倍频程能量分布几乎一模一样
（80.1 / 7.2 / 6.5 vs 80.5 / 8.3 / 7.4 %，相关系数 **1.00**）。
也就是说，光靠文件本身，玩家在"第一下"可能听不出区别。
上一轮为此用 pitch 移调（1.1 / 0.9）去拉开，**本轮已按用户要求 + 旧版做法移除**（见 §2.1 与 §3 理由 3）：
两条现在都是 pitch 1.0，区别只在"听完整条音"时才明显（音色/时长/衰减/频段重心）。

## 2. 旧版（1.20.1）锁定/解锁到底播什么

- 旧版**没有 LOCK 模式**；最接近的是超充重力枪左键把 `ship.isStatic` 取反
  （`GravitronLeftClickPacket.kt:81`），**锁定与解锁都播同一个 `GRAVITRON_FREEZE`**
  （`ClockworkSounds.kt:186-189`，字幕 "Gravitron freezing ship"）。
- 上游 Simulated 的物理手杖同样是 lock / unlock 共用同一条采样（只换字幕）。
- 结论：旧版只给了我们**两个专用音**（`FREEZE` = 冻结、`LAUNCH` = 甩出），
  而且旧版自己对"两个方向"不区分；本移植要"锁定与解锁彼此不同、且都不同于抓取/放开"，
  就必须自己把这**两个闲置音**分配成一锁一解。

### 2.1 旧版六个重力枪音的真实播放参数（本轮逐行抄出，**全部是固定值，无随机范围**）

| 事件 | 采样 ogg | 调用点（文件:行号） | volume | pitch | 源码写法 |
|---|---|---|---|---|---|
| `GRAVITRON_FREEZE` | `gravitron_freeze.ogg` | `common/.../gravitron/GravitronLeftClickPacket.kt:82-89` | **1f**（:87） | **1f**（:88） | `level.playSound(null, serverPlayer.blockPosition(), ClockworkSounds.GRAVITRON_FREEZE.mainEvent!!, SoundSource.PLAYERS, 1f, 1f)` |
| `GRAVITRON_LAUNCH` | `gravitron_fling.ogg` | `GravitronLeftClickPacket.kt:67-74` | 1f（:72） | 1f（:73） | 同上，事件换 `GRAVITRON_LAUNCH` |
| `GRAVITRON_GRAB` | `gravitron_grab.ogg` | `gravitron/tool/GrabTool.kt:243-250` | 1f（:248） | 1f（:249） | `level.playSound(null, player.blockPosition(), ..., player.soundSource, 1f, 1f)` |
| `GRAVITRON_RELEASE` | `gravitron_release.ogg` | `GrabTool.kt:187-194`（`dropShip` 分支） | 1f（:192） | 1f（:193） | 同上 |
| `GRAVITRON_START` | `gravitron_start.ogg` | `gravitron/GravitronHandler.kt:76-83` | 0.3f（:81） | 1.0f（:82） | 客户端 tick 里"刚拿起枪" |
| `GRAVITRON_SHUTDOWN` | `gravitron_shutdown.ogg` | `GravitronHandler.kt:55-62` | 0.5f（:60） | 1.0f（:61） | 客户端 tick 里"枪离手" |

关键事实（回答"旧版到底怎么调"）：

1. **`isStatic` 取反的两个方向共用同一事件、同一组参数** —— `GravitronLeftClickPacket.kt:81`
   只写了一句 `ship.isStatic = !ship.isStatic`，紧接着第 82-89 行播 `GRAVITRON_FREEZE`，
   参数是写死的 `1f, 1f`。也就是说：**旧版根本没有 LOCK/UNLOCK 之分，也不存在移调**
   （"冻结"和"解冻"听上去完全一样）。
2. **没有任何 pitch 变体或随机范围**：`ClockworkSounds.kt:166-189` 里这些事件的注册是
   `create(name).subtitle(...).category(...).attenuationDistance(...).build()`，只有字幕/类别/衰减距离，
   **没有 pitch**；`ClockworkSounds.kt:290` 的 `playExisting(event, volume: Float = 1f, pitch: Float = 1f)`
   默认值就是 1f/1f；`ClockworkSounds.kt:427` 只是把条目里的 pitch 乘上运行时传入的 pitch，
   而这些条目全都没设过 pitch —— 所以**旧版所有重力枪音的 pitch 恒等于 1.0**。
   在 `gravitron/` 目录里 grep `0.9f / 1.1f / nextFloat / random / Mth.` 只命中渲染器里的
   材质动画角速度，与音频无关。
3. 因此本移植的 LOCK / UNLOCK 一律用 **volume 1.0f / pitch 1.0f**（命名常量
   `GravitronItem#LOCK_SOUND_VOLUME` / `#LOCK_SOUND_PITCH`，注释里引用了上面这些行号）。

## 3. 最终选型

| 动作 | 事件（已注册） | 采样（**本模组自带**） | 播放 volume / pitch | 字幕 |
|---|---|---|---|---|
| 锁定 | `vs_clockwork:gravitron_lock` | `vs_clockwork:gravitron_freeze` | **1.0f / 1.0f**（旧版值，`GravitronLeftClickPacket.kt:87-88`） | `vs_clockwork.subtitle.gravitron_lock` = "Gravitron Locking Structure" / "重力枪：锁定物理结构" |
| 解锁 | `vs_clockwork:gravitron_unlock` | `vs_clockwork:gravitron_fling` | **1.0f / 1.0f**（旧版值，`GravitronLeftClickPacket.kt:72-73`） | `vs_clockwork.subtitle.gravitron_unlock` = "Gravitron Unlocking Structure" / "重力枪：解锁物理结构" |

理由：

1. **只用自己的资源**：两条事件都指向 `assets/vs_clockwork/sounds/` 里随本模组打包的 ogg，
   不存在"上游换版本 → 静默失效"的跨模组依赖。这两个文件正是旧版两个**此前闲置**的重力枪音
   （本移植没有左键发射/冻结动作，事件没注册过），配上 LOCK/UNLOCK 后不再有死资源。
2. **语义**：`gravitron_freeze` 是旧版"把船冻在原地"的专用音 → 正好是"把结构焊死在世界坐标系"；
   `gravitron_fling` 是"甩出去"的专用音 → 解锁就是"约束松开、力重新作用在结构上"。
   LOCK / UNLOCK 的实现语义仍对标 Aeronautics 物理手杖的 `LOCK` 动作（`CWSubLevelLocks`），
   但那只是**实现**参考，**声音不再取它的采样**。
3. **【本轮修改】音高回到旧版默认，不再人为移调**：上一轮用 pitch 1.1 / 0.9 把两条"拉开约
   3.5 个半音"，但旧版对同类动作（`isStatic` 取反）自始至终是 `1f, 1f`（§2.1），
   而且实机反馈正是"音调偏高"。现改为两条都 **1.0f / 1.0f**（命名常量
   `GravitronItem#LOCK_SOUND_VOLUME` / `#LOCK_SOUND_PITCH`，一个常量对供两个方向用，
   呼应旧版"两个方向同一条音、同一组参数"的做法）。区分度改由**采样本身**承担：
   起音音色（freeze 前 100 ms 是 1163 Hz 亮"咔"、低频仅 20.3%；fling 是 316 Hz 闷"砰"、低频 74.9%）、
   时长（4.08 vs 5.01 s）、衰减形状（freeze 1.5 s 后 −33 dB 收干 vs fling 拖到 4 s 仍有
   −36~−46 dB 尾巴）、频段重心（整曲低频 98.2% vs 62.7%）四个维度都不同 —— 见 §1.3。
   其中"开头 0.5 s 倍频程几乎相同（相关系数 1.00）"这个坑仍然存在，但**接受它**：
   宁可与旧版听感一致，也不再为了区分而移调（这是用户本次的明确要求）。
4. **不与 grab / release 相同**（用户硬要求）：grab 是 3.23 s 慢起音（峰值 643 ms）的"拉起长音"，
   release 是 3.38 s 的低频收力；LOCK 是"亮咔 + 下扫 + 1 s 内收干"，
   UNLOCK 是"5 s 带中频的放能长音"，四条在时长、起音、频段上互不重叠。
5. **为什么不用 §1.1 里那些更像"咔哒"的小音效**：`tick` / `tock` 是**时钟方块**的钟摆声
   （`ClockBlockEntity` 正在播），借来当锁扣声语义不成立；`hose_attach/release` 是软管、
   `joint_break` 是断裂、`wand_*` 是游荡法杖、`metal_creak_*` 是吱呀 —— 都不是"重力枪"的声音。
   用户要的是"重力枪自带的音效"，所以只能在这 6 个 `gravitron_*` 里选。

## 4. 没有改动的音效（以及和旧版的差异，仅记录）

- 抓取 `GRAVITRON_GRAB`、放开 `GRAVITRON_RELEASE`：与旧版 `GrabTool` 一致，未动。
- 换枪 `GRAVITRON_START` / `GRAVITRON_SHUTDOWN`：与旧版 `GravitronHandler` 一致，未动。
- 装配（`GravitronItem#assemble`）播 `START` @ pitch 0.7，解体（`#disassemble`）播 `SHUTDOWN`：
  **旧版是装配播 `PHYSICS_INFUSER_FINISH`、解体完全不播音**。这是本移植的简化，
  但用户明确要求"装配/解体/换枪的既有音效不要改动"，所以保持原样。
- "组装并抓取"模式会连播 `START@0.7` + `GRAB`，与旧版 `grabssemble`（灌注器完成音 + 抓取音）结构相同。
- 抓取时会自动解锁（`CWSubLevelLocks.unlock`）：不额外播 `UNLOCK`，因为紧接着就播 `GRAB`。
- `sounds.json` 里旧版的 `gravitron_fling` / `gravitron_freeze` **事件条目本身**保留但**不注册**
  （它们没有代码在播；注册成事件等于留死代码）。它们现在是被 LOCK / UNLOCK 引用的**采样**。
- 衰减距离：`gravitron_lock` / `gravitron_unlock` 都是 `attenuation_distance: 16`，
  与 grab / release / shutdown 一致（start / fling 旧条目是 32，但那是它自己的事件）。

## 5. 复现与验收

```powershell
# 1) 时长（不需要解码器：读 ogg 末页 granule）
python tools/ogg_duration.py clockwork_unoffical\src\main\resources\assets\vs_clockwork\sounds

# 2) 解码成 PCM（用 jorbis，本机没有 ffmpeg，WinRT 转码不认这些文件）
$J="C:\Users\yql11\.gradle\caches\modules-2\files-2.1\org.jcraft\jorbis\0.0.17\8872d22b293e8f5d7d56ff92be966e6dc28ebdc6\jorbis-0.0.17.jar"
$env:JAVA_HOME="C:\Users\yql11\java\dragonwell-21.0.8.0.8+9-GA"
javac -cp $J -d $env:TEMP\cw_dec2 tools\DecodeOgg.java
java -cp "$J;$env:TEMP\cw_dec2" DecodeOgg <in.ogg> <out.pcm>
# stdout 每行 "rate channels frames bytes"，收集成 manifest.tsv（name <TAB> rate <TAB> ch）

# 3) 客观指标（起音/能量分布/频谱质心/过零率）
python tools\sfx_stats.py <pcm 目录> --manifest <manifest.tsv>

# 4) 两两可比性（包络/倍频程相关系数 + 时间线）—— 本轮新增
python tools\sfx_compare.py <pcm 目录> --manifest <manifest.tsv> --timeline gravitron_freeze,gravitron_fling
python tools\sfx_compare.py <pcm 目录> --manifest <manifest.tsv> --first 0.5   # 只看开头 0.5 s
```

- `tools/ogg_duration.py`、`tools/DecodeOgg.java`、`tools/OggProbe.java`、`tools/sfx_stats.py`、
  `tools/sfx_compare.py` 是一次性分析工具（不参与打包，`tools/` 不在任何守门脚本的扫描范围内）。
- 守门：`tools/check_data_refs.py`（含"注册的事件 ↔ sounds.json 条目 ↔ ogg 存在"三向检查）
  合计 0 个；`tools/check_assets.py` `[4]=0`、`[6]=0`；`tools/check_jar.py` 0 个。

## 6. 残余风险

- **跨模组采样风险：已消除。** 两条事件的 `name` 都是 `vs_clockwork:...`，
  指向本模组 jar 内的 ogg；`sounds.json` 里已无 `simulated:`（可 `grep` 复核）。
- **移调风险：已消除（本轮）。** 两条事件都以旧版值 `volume = 1.0f` / `pitch = 1.0f` 播放，
  数值集中在 `GravitronItem.LOCK_SOUND_VOLUME` / `LOCK_SOUND_PITCH` 两个命名常量里
  （注释引用了旧版 `GravitronLeftClickPacket.kt:87-88`），`toggleLock` 里没有裸数字。
  **仍然存在的问题**：两条采样开头 0.5 s 的音色几乎相同（§1.3），
  所以锁定与解锁在"第一下"可能难以分辨 —— 这是接受旧版做法的代价，不再用移调弥补。
- 剩下的是**听感**问题，不是引用问题：锁定/解锁各自 4.08 s / 5.01 s，比"一下咔哒"长；
  若实机仍觉得不对，改 `LOCK_SOUND_VOLUME` / `LOCK_SOUND_PITCH` 即可（两个方向共用），
  不必再动 `sounds.json`；**但改 pitch 前请先读 §2.1** —— 旧版是所有重力枪音一律 1f/1f。
- 若将来真要"更像锁扣的短咔哒"，正确做法是**给本模组新增 ogg**（放在
  `assets/vs_clockwork/sounds/`），而不是借别的模组的文件。
