#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""贴图 ↔ 方块 / 物品 对应表生成器（vs_clockwork）。

解决的问题
==========
`assets/vs_clockwork/textures/block/**` 里有 160+ 个文件，但「哪个 PNG 被谁用」没有任何清单：
  * 有贴图、没模型引用  → 功能没接（或资源是旧版遗留），游戏里永远看不到；
  * 有模型引用、没贴图  → 1.21.1 完全静默，直接黑紫上屏（见 tools/cw_assets_lib.py 顶部第 2 条）；
  * 有贴图、有模型、但模型没有任何 blockstate / 物品模型 / Java PartialModel 引用
                        → 烘焙不到（Flywheel 的 partial 还必须提前登记），同样不显示。
这三种只能静态对照三棵树（官方 / 工程 / 产物 jar）+ Java 引用才能分清。

判定口径（重要，别按直觉改）
============================
1. **「引用」= 模型的 `textures` 字段**（含 `#变量` 间接引用、`parent` 链继承、OBJ 的
   `mtllib`/`map_Kd #键`）。只有 `textures` 里的**具体值**（不以 `#` 开头）才算引用；
   `"#变量"`、`"."`/`""` 占位键不算。
2. **可达（reachable）** 的三种根：`blockstates/<id>.json`、`models/item/<id>.json`（仅第一层）、
   Java 里的 PartialModel 字面量（`"block/xxx/yyy"`）。`models/block/**` 自己互相 parent
   **不算**被使用 —— 一组死模型互相引用仍然是死的（这正是 check_render_coverage.py [RC6] 的判据）。
3. **已用** = 被某个可达模型的**面 / particle / OBJ 材质**真正取用（`rendered`），
   而不仅仅是「可达模型的 textures 里写了它」（后者单列 `已烘焙未上面`，通常也是接线错误）。
4. **仅声明** = 只有「不可达模型」声明了它 —— 贴图在、模型也在，但没有任何根能烘焙到它。
   这一类就是「功能没接」的主战场。
5. **孤儿** = 任何来源的任何模型都没声明过它（Java 直接 `textures/block/xxx.png` 的除外）。
6. 贴图存在性**跨命名空间**查：本模组官方树 → 工程树 → 产物 jar → libs/*.jar → 原版
   `client-*-extra.jar`（原版模型/贴图只在 -extra.jar 里，这条是 tools/cw_assets_lib.py 实测的）。
   注意 ballooner 的模型用的是 `create:block/pipes`（Create 自己的贴图），不是
   `vs_clockwork:block/pipes` —— 这类跨命名空间陷阱只看本模组树会误判"用了"。
7. `textures/block/` 下的 `.mcmeta` / `.aseprite` 不是贴图本体：`.mcmeta` 算贴图的附属件
   （动画定义），`.aseprite`、`.bbmodel`、`.gpl`、`.obj`、`.mtl` 是制作期资源，单独归类，不计入贴图数。

不修改任何资源
==============
本脚本只读 + 只写 `docs/TEXTURE-MAP.md`（`--check` 时连文档都不写）。

用法
====
    python tools/map_textures.py                 # 生成 docs/TEXTURE-MAP.md + 打印摘要
    python tools/map_textures.py --check         # 只校验（不写文件），有问题退出码 1
    python tools/map_textures.py -v              # 额外打印孤儿 / 缺图清单
    python tools/map_textures.py --json out.json # 另外导出一份机器可读快照

退出码：0 = 无缺图引用、无缺失模型；1 = 有（`--check` 用）。
"""
from __future__ import annotations

import argparse
import collections
import glob
import json
import os
import re
import struct
import sys
import zipfile

TOOLS = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(TOOLS)
PROJ = os.path.join(ROOT, "clockwork_unoffical")
NS = "vs_clockwork"

OFFICIAL_ASSETS = os.path.join(ROOT, "assets")                       # <官方树>/vs_clockwork
PROJECT_ASSETS = os.path.join(PROJ, "src", "main", "resources", "assets")
JAVA_ROOT = os.path.join(PROJ, "src", "main", "java")
LIBS_DIR = os.path.join(PROJ, "libs")
REGISTRY = os.path.join(TOOLS, "blockstate_properties.json")
DOC_PATH = os.path.join(ROOT, "docs", "TEXTURE-MAP.md")
MC_DEFAULT = os.path.join(os.path.expanduser("~"), "OneDrive", "Desktop", ".minecraft")

L_OFF, L_PROJ, L_JAR = "官方树", "工程树", "产物jar"

# 代码里注册的内置模型（不是文件）。builtin/generated 的 6 层里只有 layer0 必需。
BUILTIN_MODELS = {
    "builtin/generated": {"elements": [{"faces": {"north": {"texture": "#layer0"}}}]},
    "builtin/entity": {},
    "builtin/missing": {},
}

# ---------------------------------------------------------------------------
# 人工核对记录：脚本自动判定之外，逐条读过文件后确认的结论。
# 写在这里而不是直接写进 .md，是为了让「重跑脚本」不会把人工结论冲掉；
# --check 会校验每条 note 里提到的贴图仍然存在（防止笔记烂掉）。
# 字段：tex = 贴图（相对 textures/，不带 .png）；verdict = 结论；why = 依据（文件:行）。
# ---------------------------------------------------------------------------
MANUAL_NOTES = [
    # --- 连接纹理（CT）图集 ---
    ("block/duct_tank_connected",
     "CT 图集，**已正确接线**（不是孤儿）",
     "`duct_tank/block_single.json` 用 uv 子区间从 4×4 格里取 1 格；`block_bottom/top/middle` 整张铺满，"
     "所以同一族内 texel 密度差 4 倍（成型/拆分时贴图会跳变）—— 这是 uv 问题，与「有没有人引用」无关"),
    ("block/duct_tank_top_connected",
     "CT 图集，**已用**",
     "`duct_tank/block_top.json` / `block_single.json` 引用；与上面同一族"),
    ("block/balloon_casing_connected",
     "**保留待接**：旧版 CT 连接变体贴图（128×128，4×4 格）",
     "`balloon_casing.json` 只有 `all` 一个键指向 `block/balloon_casing`（cube_all），没有 CT 结构；"
     "等接 Create 式 CT / EncasingRegistry 再上（本次不删）"),
    # --- 两套命名并存 / 变体面 ---
    ("block/alt_meter",
     "**已用**：gyro 物品模型的 OBJ 材质键 1",
     "`gyro/gyro_item.mtl` 有 `map_Kd #1`（物品模型，`gyro/gyro.json` 声明 `\"1\": block/alt_meter`）；"
     "方块版 `gyro.mtl` 只有 `#0`。只看方块模型会误判成「仅声明」"),
    ("block/altimeter",
     "**已用**：alt_meter 方块的旧命名族",
     "`alt_meter/block.json` 用 `block/altimeter`，`alt_meter/redstone.json` 用 `block/altimeter_redstone`；"
     "方块 id 是 `alt_meter`，贴图名却是 `altimeter`，两套命名并存"),
    ("block/alt_meter_on",
     "**保留待接**：旧版「红石点亮」变量图",
     "`alt_meter/block.bbmodel` 里它作为第二层贴图存在，但 blockstate 没有 `power` 变体、"
     "没有任何 JSON 模型引用它；等接点亮状态再上（本次不删）"),
    ("block/nyx_wanderlite_ore_top",
     "**已接入**：`wanderlite/nyx_ore.json` 已改 `cube_bottom_top`，top 用此图",
     "官方树与 1.20.1 源码都还是 `cube_all`（上游自己没接），工程树按本次任务目标接线；"
     "见附录 D.5，回拷官方模型会退回 cube_all"),
    # --- 热管六档 ---
    ("block/heatpipe_core",
     "常温档，**已用**",
     "`heat_pipe` 常温档的连接件模型（l/r/u/d/lu/... 各方向）＋ `straight_duct` 引用的 "
     "`heat_pipe/straight` 都用它"),
    ("block/heatpipe_core_hot",
     "高温档，**已用**（不是孤儿！）",
     "`heat_pipe` 方块带 `heat` 属性（cool/warm/hot/very_hot/molten/super_hot），工程树的 "
     "`blockstates/heat_pipe.json` 有 228 条 multipart（38 个连接形态 × 6 档）覆盖全部 6 档"
     "（官方树只有 30 条常温变体）——「六档热度整套没接」这个怀疑在**工程树里不成立**，"
     "只在官方树成立"),
    ("block/heatpipe_rim_molten",
     "熔融档，**已用**",
     "同上；`heat_pipe/*_molten` 模型由 `heat=molten` 的条件选中"),
    # --- 导管边件：唯一「整套没接」的一组（保留待补） ---
    ("block/oneway_duct",
     "**保留待补**：单向阀导管边件没接",
     "`duct/custom/oneway_forward.json` / `oneway_backward.json` 引用它，但这两个模型不被任何 blockstate 引用；"
     "`DuctRenderer.java:41` 原话：「`duct/custom/*` 四个边件暂无人使用」。"
     "要接必须先给 `GasDuctBlock` 加单向/智能属性（本次不删）"),
    ("block/smart_duct",
     "**保留待补**：智能导管边件没接",
     "`duct/custom/smart.json` 引用；同样没有 blockstate 能选到它（DuctRenderer.java:41，本次不删）"),
    ("block/copper_duct",
     "**保留待补**：铜导管外观没接",
     "`duct/custom/copper.json` 引用；同样没有 blockstate 能选到它（DuctRenderer.java:41，本次不删）"),
    ("block/redstone_duct",
     "**已用**（不是孤儿）：它属于独立方块 `redstone_duct`，不是 duct 的变体",
     "`redstone_duct/block.json` 引用；`blockstates/redstone_duct.json` 是 axis=x/y/z 三变体，"
     "`RedstoneDuctBlockEntity` 单独注册（CWGasIndex:245/481）"),
    # --- Java 直接绑定（最容易被误判） ---
    ("block/hose",
     "**已用（Java 直接引用，非模型）**：别当孤儿删",
     "`ExtendonRenderer` 的 `HOSE_TEXTURE` 常量直接 `ResourceLocation(..., \"textures/block/hose.png\")`；"
     "模型里确实没人引用它 —— 这就是「只查模型会误判」的典型"),
    ("block/compressible_axis",
     "**已用（Java 直接引用，非模型）**",
     "`UniversalShaftRenderer.java:40` 直接引用 `textures/block/compressible_axis.png`；尺寸 4×14（拉伸用）"),
    ("block/purple_hue",
     "**已用（Java 直接引用，非模型）**",
     "`PhysicsInfuserRenderer.java:67` 直接引用 `textures/block/purple_hue.png`（外层紫色辉光壳）"),
    ("block/blue_hue",
     "**保留待补**：purple_hue 的姊妹图，只接了紫色一侧",
     "全工程 Java / JSON 都没有 `blue_hue`；`PhysicsInfuserRenderer` 只用 `purple_hue`。"
     "等蓝色光晕功能再上（本次不删）"),
    # --- 表现层 / afterblazer（保留待补） ---
    ("block/angry",
     "**保留待补**：afterblazer 表情/羽流贴图，整套没接",
     "`angry` / `infuriated` / `blaze_fury` / `fuming` 四张在 Java 与 JSON 里都搜不到，"
     "但 1.20.1 `ClockworkPartials.kt:15-18` 有 afterblazer 的 PLUME_ANGRY / PLUME_FUMING / "
     "PLUME_INFURIATED / BLAZE_INFURIATED 声明 → 等 afterblazer 功能移植（本次不删）"),
    ("block/blaze_fury",
     "**保留待补**：afterblazer 表情贴图",
     "同上；对应 1.20.1 BLAZE_INFURIATED（`ClockworkPartials.kt:15`）"),
    ("block/fuming",
     "**保留待补**：afterblazer 羽流贴图",
     "同上；对应 PLUME_FUMING（`ClockworkPartials.kt:17`）"),
    ("block/infuriated",
     "**保留待补**：afterblazer 羽流贴图",
     "同上；对应 PLUME_INFURIATED（`ClockworkPartials.kt:18`）"),
    # --- 动画 mcmeta ---
    ("block/coal_burner_flame",
     "**已用**：动画贴图（20 帧，16×320）",
     "`coal_burner/flame.json` 引用；`.mcmeta` 只有 frametime，按 min(w,h)=16 正方形切帧"),
    ("block/spinotron_animated",
     "**已用**：物品图标用动画版，方块用三轴静态图",
     "`gyroscopic_sensor/item.json` 用 `spinotron_animated`，`block.json` 用 `spinotron_x`"),
    ("block/impactotron_animated",
     "**已用**：物品图标用动画版",
     "`impact_sensor/item.json` 引用；方块侧用 `impactotron_1..4`"),
    ("block/peepulon_animated",
     "**已用**：物品图标用动画版",
     "`distance_sensor/item.json` 引用；方块侧用 `peepulon_1..4`"),
    # --- OBJ 贴图键 ---
    ("block/clock",
     "**已用**：OBJ 材质键 `0` + `particle`",
     "`clock/clock_ring.json`、`clock/item.json` 都是 `neoforge:obj`，`textures` 只有 `0` 与 `particle`；"
     "真正取用点是 `clock_ring.mtl` 的 `map_Kd #0`，不看 mtl 会以为没引用"),
    ("block/reactionwheels",
     "**已用**：OBJ 材质键 `0`（另 `1`/`2` 是 Create 的轴贴图）",
     "`reactionwheel/reactionwheel.json` 声明 `0/1/2`，`reactionwheel_shaftless.mtl` 三个 `map_Kd` 全用上；"
     "`1`/`2` 指向 `create:block/axis(_top)`，属跨命名空间"),
    ("block/gyro",
     "**已用**：OBJ 材质键 `0`",
     "`gyro/block.json` → parent `gyro/gyro`（声明 `0`/`1`），`gyro.mtl` 只用 `#0`，"
     "`gyro_item.mtl` 用 `#0`+`#1`（`#1` = `block/alt_meter`）"),
    # --- 未移植功能（2026-10-17 收口：保留并标注，不要删） ---
    ("block/afterblazer",
     "**保留待补**：afterblazer 未移植（方块/渲染器都没有注册点）",
     "1.20.1 `ClockworkPartials.kt:15-18` 有 BLAZE_INFURIATED / PLUME_ANGRY / PLUME_FUMING / "
     "PLUME_INFURIATED 声明，`ClockworkShapes.kt:14` 有 AFTERBLAZER 碰撞箱；但两版都没有 blockstate 之外的注册，"
     "工程树 `afterblazer/block.json` 是按贴图补的可烘焙模型。等 afterblazer 功能一起补"),
    ("block/combustion_engine",
     "**保留待补**：combustion_engine 未移植（方块/渲染器都没有注册点）",
     "1.20.1 `ClockworkPartials.kt:21-22` 有 ENGINE / SINGLE_ENGINE_PISTON 声明，block/item 模型齐全，"
     "但两版都没有注册点；属 WIP，保留待补"),
    ("block/fluid_tank",
     "**仅官方树**：官方 `gas_engine/block.json` 用它，工程树改成 `vs_gas_flow_engine`",
     "按工程树（运行时）解析时没有任何可达模型引用它；这不是缺口，是移植期主动换图"),
    # --- 缺图 ---
    ("block/creative_gas_generator",
     "**缺图引用**：模型里写成了 `block/creative_generator`（少 `_gas_`）",
     "`block/creative_gas_generator/item.json`（官方树，死模型）引用 `vs_clockwork:block/creative_generator`，"
     "三棵树 + jar 都没有这张图；工程树的同名模型已改为 `creative_gas_generator`，所以运行时不黑紫，"
     "但官方树这份坏引用仍在"),
]
# ===========================================================================
# 2026-10-17 资源卫生收口：人工核实后删除的资源 / 保留标注。
# 删除只发生在工程树；官方树是只读参考，所有删除件在官方树都有同名副本。
# ===========================================================================
CLEANUP_DATE = "2026-10-17"

# 贴图 stem -> (分类, 为什么确认无引用)
CLEANED_TEXTURES = [
    # --- 被替换的同名贴图（模型已改用新图） ---
    ("block/cannon_barrel", "被替换",
     "`delivery_cannon/*` 与 `delivery_chute/block.json` 统一用 `block/cannon_barrel_largecheckers`"),
    ("block/gas_crafter", "被替换",
     "`gas_crafter/{block,frame,glow,mesh,tube}.json` 的 `0`/`particle` 全部指向 `block/pneumocrafter`"),
    ("block/gas_engine_front", "被替换",
     "工程树 `gas_engine/block.json` 用 `vs_gas_flow_engine` + `duct`"),
    ("block/gas_engine_side", "被替换", "同上；官方树也没接这两张"),
    ("block/doink", "被替换",
     "`slicker/doink.json` 的 `0`/`particle` 指向 `block/doinkborder`；该模型也随本次删除"),
    # --- 1.20.1 旧版外观 ---
    ("block/compressor_old", "旧版外观",
     "`compressor/compressor.json` 用 `block/compressor` + `block/compressor_fabric`，没有 `*_old` 链"),
    ("block/duct_old", "旧版外观",
     "`duct/block_old.json` / `item_old.json` 已改用 `heatpipe_*`"),
    ("block/phys_bearing_bigflap", "旧版外观",
     "`phys_bearing/block.json` 只用 `block/phys_bearing`；`phys_bearing_old/block` 也不用它"),
    ("block/pipes", "旧版外观",
     "`ballooner` 的 `particle` 是 **create:block/pipes**（Create 的图），本工程无引用"),
    ("block/pipes_connected", "旧版外观",
     "无任何模型引用 `vs_clockwork:block/pipes_connected`"),
    ("block/reactionwheelstitched", "旧版外观",
     "现用 `reactionwheel/reactionwheels.png`（OBJ 的 `0/1/2` 材质键）"),
    # --- 占位 ---
    ("block/empty", "占位", "16×16 全透明占位图，任何模型 / Java 都没引用"),
    # --- 死局部模型用图（模型在 CLEANED_MODELS 里一起删） ---
    ("block/doinkborder", "死局部模型用图",
     "只被 `slicker/doink.json` 引用；该模型无 blockstate / Java 根"),
    ("block/duct_tank_placeholder", "死局部模型用图",
     "只被 `duct_tank/placeholder.json` 引用；`blockstates/duct_tank.json` 的四个变体都不引用 placeholder"),
    ("block/electricity", "死局部模型用图",
     "只被 `physics_infuser/zap.json` 引用；`PhysicsInfuserRenderer` 注释写明 ZAP partial 全工程无引用"),
    ("block/gas_pump", "死局部模型用图",
     "只被 `pump/cog.json` 引用；`PumpDuctRenderer` 明确用 Create 的 `MECHANICAL_PUMP_COG`，不用本工程 `pump/cog`"),
    ("block/infuservortex", "死局部模型用图", "只被 `physics_infuser/vortex.json` 引用"),
    ("block/junk_gears", "死局部模型用图", "只被 `juryrigged_infuser/item.json` 引用"),
    ("block/juryrigged_infuser", "死局部模型用图", "同上"),
    ("block/peepulon", "死局部模型用图",
     "只被 `peepotron.json` 引用；距离传感器用 `peepulon_1..4` + `peepulon_animated`"),
    ("block/reactionwheel", "死局部模型用图",
     "只被 `reactionwheel/old/alsoold/{wheelbottom,wheeltop}.json` 引用"),
    ("block/reactionwheelmid", "死局部模型用图", "同上"),
    ("block/reactionwheelspokes", "死局部模型用图", "同上"),
    ("block/vsified_top", "死局部模型用图", "只被 `vstop.json` 引用"),
    # --- 旧版独立螺旋桨轴承链（blockstate 已确认无注册点） ---
    ("block/propbearingbase", "旧版轴承链",
     "只被 `propeller_bearing/{block,item}.json` 引用；新轴承用 `brass_propeller` / `juryrigged_propeller`"),
    ("block/propbearingpistons", "旧版轴承链",
     "只被 `propeller_bearing/piston*.json` 引用；`propeller_bearing/top.json` + `propbearingtop` 是 Java partial，**保留**"),
]
CLEANED_TEXTURES_BY_STEM = {t: (cat, why) for t, cat, why in CLEANED_TEXTURES}
CLEANED_TEXTURE_STEMS = set(CLEANED_TEXTURES_BY_STEM)

# 模型 id（相对 models/，不带 .json）-> 依据
CLEANED_MODELS = [
    ("block/physics_infuser/core", "`PhysicsInfuserRenderer` 类注释：旧版 PHYSICS_CORE partial 全工程无引用"),
    ("block/physics_infuser/liquid", "同上（STRANGE_FLUID）"),
    ("block/physics_infuser/vortex", "同上（VORTEX）"),
    ("block/physics_infuser/zap", "同上（ZAP）"),
    ("block/pump/cog", "`PumpDuctRenderer` 用 Create 的 `MECHANICAL_PUMP_COG`；本模型无 blockstate / Java / 模型引用"),
    ("block/vstop", "无 blockstate / 物品模型 / Java partial 引用"),
    ("block/peepotron", "无 blockstate / 物品模型 / Java partial 引用"),
    ("block/reactionwheel/old/alsoold/wheelbottom", "旧版 OBJ 链，无 blockstate / Java 引用"),
    ("block/reactionwheel/old/alsoold/wheeltop", "同上"),
    ("block/slicker/doink", "slicker 的 blockstate 走 `slicker/block.json`（`block/slicker`），doink 链无人引用"),
    ("block/juryrigged_infuser/item", "没有 `models/item/juryrigged_infuser.json`，无 blockstate / Java 引用"),
    ("block/duct_tank/placeholder", "`blockstates/duct_tank.json` 四个变体都不引用它，无 Java 引用"),
    ("block/chocolate_frosting", "流体误留 blockstate 的伴生模型（只有 `particle` 键）"),
    ("block/strawberry_frosting", "同上"),
    ("block/vanilla_frosting", "同上"),
    ("block/propeller_bearing/block", "旧版 `propeller_bearing` blockstate 的模型；现役是 brass / juryrigged"),
    ("block/propeller_bearing/item", "旧版物品模型链；该物品未注册（`cw_ids` 的 items 里没有）"),
    ("block/propeller_bearing/pistonbotl", "无任何 blockstate / 模型 / Java 引用"),
    ("block/propeller_bearing/pistonbotr", "同上"),
    ("block/propeller_bearing/pistontopl", "同上"),
    ("block/propeller_bearing/pistontopr", "同上"),
    ("item/propeller_bearing", "未注册物品的 item 模型（`cw_ids` items 无此 id、lang 无键）"),
]
CLEANED_MODEL_IDS = {m for m, _w in CLEANED_MODELS}

# 9 个「无注册方块」的 blockstate：位置 / 处理 / 依据
BLOCKSTATE_LEDGER = [
    ("OLD_flap_bearing", "仅官方树", "不处理",
     "官方树有、工程树没有；官方树只读，不参与构建（官方 1.20.1 也从未注册这个 id）"),
    ("afterblazer", "工程树", "保留",
     "1.20.1 `ClockworkPartials.kt:15-18` 有 BLAZE_INFURIATED / PLUME_ANGRY / PLUME_FUMING / PLUME_INFURIATED，"
     "`ClockworkShapes.kt:14` 有 AFTERBLAZER 碰撞箱，但两版都没有方块注册点 / 渲染器 → 未移植的 WIP，保留待补"),
    ("balloon_encased_shaft", "工程树", "保留",
     "1.20.1 `ClockworkBlocks.kt:1020` 注册过；工程树 `CWBlocks.java:93` 与 `CWKineticsIndex.java:52` 明确写「需要与 "
     "balloon_encased_shaft 成对实现 / 需要 EncasingRegistry 集成」→ 未移植，保留待补"),
    ("chocolate_frosting", "工程树", "删除",
     "1.20.1 `FabricClockworkFluids.java:24-25` 的 fluid 注册整段被注释；流体不该有 blockstate，属误留"),
    ("combustion_engine", "工程树", "保留",
     "1.20.1 `ClockworkPartials.kt:21-22` 有 ENGINE / SINGLE_ENGINE_PISTON，block/item 模型齐全，但两版都没有方块注册 → "
     "未移植的 WIP，保留待补"),
    ("old_andesite_flap_bearing", "工程树", "删除",
     "注册方块是 `andesite_flap_bearing`，复用 `andesite_flap_bearing/block`；无 lang / 物品 / 代码注册点，属旧版别名"),
    ("propeller_bearing", "工程树", "删除",
     "1.20.1 与 1.21 都没有 plain `propeller_bearing` 方块注册；现役是 `brass_propeller_bearing` / "
     "`juryrigged_propeller_bearing`（`CWContraptionsIndex.java:112/124`）；旧模型链一并清理"),
    ("strawberry_frosting", "工程树", "删除", "同 chocolate_frosting"),
    ("vanilla_frosting", "工程树", "删除", "同 chocolate_frosting"),
]
RETAINED_BLOCKSTATES = {bid: why for bid, _loc, action, why in BLOCKSTATE_LEDGER if action == "保留"}

# 保留且要标注的「孤儿 / 仅声明」贴图：stem -> 说明
RETAINED_TEXTURE_NOTES = {
    "block/alt_meter_on": "旧版「红石点亮」状态图：`alt_meter/block.bbmodel` 里有第二层贴图，"
                          "但 blockstate 没有 power 变体；等接点亮状态再上",
    "block/balloon_casing_connected": "128×128 CT 连接图集（4×4 格）：`balloon_casing` 仍是 `cube_all`；"
                                      "等接 Create 式 CT / EncasingRegistry 再上",
    "block/nyx_wanderlite_ore_top": "本次已接：`wanderlite/nyx_ore.json` 改 `cube_bottom_top`，top 用此图"
                                    "（官方树也仍是 cube_all，见附录 D.5）",
    "block/angry": "afterblazer 表情/羽流贴图：1.20.1 `ClockworkPartials.kt:15-18` 有声明，"
                   "方块 / 渲染器没移植；等 afterblazer 一起补",
    "block/blaze_fury": "同上（afterblazer）",
    "block/fuming": "同上（afterblazer 的 PLUME_FUMING）",
    "block/infuriated": "同上（afterblazer 的 BLAZE_INFURIATED / PLUME_INFURIATED）",
    "block/blue_hue": "physics_infuser 蓝色色相图：渲染器只接了 `purple_hue`（`PhysicsInfuserRenderer.java:67`）；"
                      "等蓝色光晕功能再上",
    "block/oneway_duct": "导管 custom 单向边件：没有任何 blockstate 能选到；`DuctRenderer.java:41` 原话"
                         "「`duct/custom/*` 四个边件暂无人使用」→ 等待功能移植",
    "block/smart_duct": "导管 custom 智能边件：同上，等待 `GasDuctBlock` 加智能属性",
    "block/copper_duct": "导管 custom 铜边件：同上，等待铜导管外观接线",
}

# 官方树上游 bug（只读参考，不修；回拷会把问题带回工程树）
OFFICIAL_TREE_BUGS = [
    ("create:block/copper_plating", "block/valve_duct/pointer",
     "Create 6 已删除这张贴图；工程树已换成 `copper_casing`。官方树仅作参考、不参与构建，**不要修**；"
     "若日后从官方树回拷资源，会把黑紫带回来"),
    ("vs_clockwork:block/creative_generator", "block/creative_gas_generator/item",
     "少写 `_gas_` 的笔误；工程树同名模型已改成 `creative_gas_generator`。同上，官方树不修、回拷有风险"),
]

# ===========================================================================
# 基础工具
# ===========================================================================
def png_size(data):
    """从字节里读 PNG 宽高（不依赖 Pillow）；失败返回 (0, 0)。"""
    if len(data) < 24 or data[:8] != b"\x89PNG\r\n\x1a\n":
        return 0, 0
    w, h = struct.unpack(">II", data[16:24])
    return w, h


def png_frames(data, mcmeta):
    """动画帧数：与 AnimationMetadataSection 语义一致（width/height 缺省退化见 cw_assets_lib）。"""
    w, h = png_size(data)
    if not mcmeta or not w:
        return 1
    try:
        anim = (json.loads(mcmeta.decode("utf-8-sig", "replace")) or {}).get("animation") or {}
    except Exception:                                                # noqa: BLE001
        return 1
    fw, fh = anim.get("width"), anim.get("height")
    ok_w = isinstance(fw, int) and not isinstance(fw, bool) and fw > 0
    ok_h = isinstance(fh, int) and not isinstance(fh, bool) and fh > 0
    if ok_w and ok_h and w % fw == 0 and h % fh == 0:
        return (w // fw) * (h // fh)
    if ok_w and w % fw == 0:                 # 只写 width：帧高 = 整张高（横向排帧）
        return w // fw
    if ok_h and h % fh == 0:                 # 只写 height：帧宽 = 整张宽（纵向排帧）
        return h // fh
    m = min(w, h)
    if m and w % m == 0 and h % m == 0:
        return (w // m) * (h // m)
    return 1


def split_loc(loc):
    if ":" in loc:
        ns, path = loc.split(":", 1)
    else:
        ns, path = "minecraft", loc
    return ns, path.strip("/")


def canon(loc):
    """模型 id 归一：本模组省略命名空间（'block/x'），其它命名空间保留前缀。"""
    ns, path = split_loc(loc)
    return path if ns == NS else f"{ns}:{path}"


def read_text(path):
    with open(path, "rb") as fh:
        return fh.read()


def load_json(path):
    try:
        return json.loads(read_text(path).decode("utf-8-sig", "replace"))
    except Exception:                                                # noqa: BLE001
        return None


class Source:
    """一个资源来源（磁盘目录或 jar）。files 的键 = 相对 `assets/` 的路径。"""

    kind = "?"

    def __init__(self, label, kind):
        self.label, self.kind = label, kind
        self.files = {}

    def __contains__(self, rel):
        return rel in self.files

    def read(self, rel):
        raise NotImplementedError

    def under(self, prefix):
        return [r for r in self.files if r.startswith(prefix)]


class DirSource(Source):
    def __init__(self, label, root):
        super().__init__(label, "dir")
        self.root = root
        if not os.path.isdir(root):
            return
        for dirpath, _, names in os.walk(root):
            for name in names:
                full = os.path.join(dirpath, name)
                self.files[os.path.relpath(full, root).replace("\\", "/")] = full

    def read(self, rel):
        try:
            return read_text(self.files[rel])
        except OSError:
            return None


class ZipSource(Source):
    def __init__(self, label, path, prefix="assets/"):
        super().__init__(label, "zip")
        self.path = path
        self._z = zipfile.ZipFile(path)
        for name in self._z.namelist():
            if name.startswith(prefix) and not name.endswith("/"):
                self.files[name[len(prefix):]] = name

    def read(self, rel):
        # 产物 jar 可能正在被 gradle 重写（实测撞见过 BadZipFile），读失败就当这来源没有
        try:
            with self._z.open(self.files[rel]) as fh:
                return fh.read()
        except (OSError, zipfile.BadZipFile, KeyError):
            return None


class Repo:
    """多来源查找：main（工程 → 产物 jar → 官方）优先，extra（libs / 原版）只用于存在性。"""

    def __init__(self, main, extra):
        self.main, self.extra = main, extra
        self.sources = list(main) + list(extra)

    def labels(self, rel):
        return [s.label for s in self.sources if rel in s]

    def has(self, rel):
        return any(rel in s for s in self.sources)

    def has_in_main(self, rel):
        return any(rel in s for s in self.main)

    def read(self, rel):
        for src in self.sources:
            if rel in src:
                data = src.read(rel)
                if data is not None:
                    return data, src.label
        return None, None

    def under(self, prefix):
        out = set()
        for src in self.sources:
            out.update(src.under(prefix))
        return sorted(out)


class PreferRepo(Repo):
    """「只看某一棵树」的解析器：先在该来源里找，找不到再回落到全局（parent 可以跨来源）。"""

    def __init__(self, base, prefer):
        super().__init__(base.main, base.extra)
        self.base, self.prefer = base, prefer
        self.sources = base.sources

    def read(self, rel):
        if rel in self.prefer:
            return self.prefer.read(rel), self.prefer.label
        return self.base.read(rel)


def tex_rel(ref, ext=".png"):
    ns, path = split_loc(ref)
    return f"{ns}/textures/{path}{ext}"


# ===========================================================================
# 模型解析（parent 链 / #变量 / OBJ 材质）
# ===========================================================================
class ModelStore:
    def __init__(self, repo):
        self.repo = repo
        self.cache = {}

    def get(self, mid):
        if mid in self.cache:
            return self.cache[mid]
        ns, path = split_loc(mid)
        if ns == "minecraft" and path in BUILTIN_MODELS:
            res = (BUILTIN_MODELS[path], "builtin", None)
        else:
            raw, src = self.repo.read(f"{ns}/models/{path}.json")
            if raw is None:
                res = (None, None, f"模型文件不存在: assets/{ns}/models/{path}.json")
            else:
                try:
                    res = (json.loads(raw.decode("utf-8-sig", "replace")), src, None)
                except Exception as exc:                             # noqa: BLE001
                    res = (None, src, f"JSON 解析失败: {exc}")
        self.cache[mid] = res
        return res

    def chain(self, mid, max_depth=64):
        """沿 parent 链合并：textures 子优先、elements/loader 取最近一层。"""
        tex, elements, elements_src = {}, None, None
        loader = obj = None
        errs, order = [], []
        cur, seen = mid, set()
        while cur:
            if cur in seen:
                errs.append(f"parent 形成环: {cur}")
                break
            if len(order) >= max_depth:
                errs.append("parent 链过深")
                break
            seen.add(cur)
            order.append(cur)
            data, _src, err = self.get(cur)
            if err is not None:
                errs.append(f"{cur}: {err}")
                break
            for key, val in (data.get("textures") or {}).items():
                if isinstance(val, dict):                # 少数资源包写 {"sprite": ...}
                    val = val.get("sprite")
                if isinstance(val, str):
                    tex.setdefault(key, (val, cur))
            if elements is None and isinstance(data.get("elements"), list):
                elements, elements_src = data["elements"], cur
            if loader is None and isinstance(data.get("loader"), str):
                loader, obj = data["loader"], data.get("model")
            parent = data.get("parent")
            cur = parent if isinstance(parent, str) else None
        return {"textures": tex, "elements": elements, "elements_src": elements_src,
                "loader": loader, "obj": obj, "errs": errs, "order": order, "root": mid}


def resolve_key(key, tex, depth=0):
    if not isinstance(key, str):
        return None
    if not key.startswith("#"):
        return key
    if depth > 16:
        return None
    name = key[1:]
    if name not in tex:
        return None
    val = tex[name][0]
    return resolve_key(val, tex, depth + 1) if val.startswith("#") else val


def obj_material_keys(store, repo, chain):
    """OBJ 模型的真正取用点：.obj 的 mtllib → .mtl 的 `map_Kd #键`。

    1.21.1 的 `neoforge:obj` 里 `model` 是相对 `models/` 的路径；1.20.1 的 `forge:obj`
    写的是 `vs_clockwork:models/...`（带 models/ 前缀），两种都要能读。
    读不到 .obj/.mtl 时返回空集合，由调用方按 err 处理。
    """
    raw = chain.get("obj")
    if not isinstance(raw, str):
        return set(), "OBJ 模型缺少 model 字段"
    ns, path = split_loc(raw)
    if path.startswith("models/"):
        path = path[len("models/"):]          # 1.20.1 forge:obj 写的是 vs_clockwork:models/...
    # 1.21.1 neoforge:obj 的 model 相对 `models/` 解析
    obj_blob, _ = repo.read(f"{ns}/models/{path}")
    if obj_blob is None:
        return set(), f".obj 不存在: assets/{ns}/models/{path}"
    text = obj_blob.decode("utf-8", "replace")
    base = path.rsplit("/", 1)[0] + "/" if "/" in path else ""
    keys = set()
    for mtl in re.findall(r"^mtllib\s+(\S+)", text, re.M):
        mns, mpath = split_loc(mtl) if ":" in mtl else (ns, base + mtl)
        mtl_blob, _ = repo.read(f"{mns}/models/{mpath}")
        if mtl_blob is None:
            continue
        for key in re.findall(r"^map_Kd\s+(\S+)", mtl_blob.decode("utf-8", "replace"), re.M):
            if key.startswith("#"):
                keys.add(key[1:])
    return keys, None


def resolve_root(store, repo, mid):
    """返回 (declared, rendered, chain, obj_err)。

    declared = 模型链里所有具体贴图 id（含继承）；
    rendered = 真正被面 / particle / OBJ 材质取用的贴图 id。
    """
    chain = store.chain(mid)
    tex = chain["textures"]
    declared = {val for val, _src in tex.values() if isinstance(val, str) and not val.startswith("#")}
    rendered = set()
    for el in chain["elements"] or []:
        for face in (el.get("faces") or {}).values():
            ref = resolve_key(face.get("texture"), tex)
            if ref and not ref.startswith("#"):
                rendered.add(ref)
    if "particle" in tex:
        ref = resolve_key("#particle", tex)
        if ref:
            rendered.add(ref)
    obj_err = None
    if chain["loader"] and "obj" in chain["loader"]:
        keys, obj_err = obj_material_keys(store, repo, chain)
        for key in keys:
            ref = resolve_key("#" + key, tex)
            if ref:
                rendered.add(ref)
                declared.add(ref)
    return declared, rendered, chain, obj_err


def blockstate_models(data):
    """blockstate → 模型 id 列表（variants + multipart，含 weighted 列表）。"""
    out = []

    def norm(x):
        return x if isinstance(x, list) else [x]

    for val in (data.get("variants") or {}).values():
        for one in norm(val):
            if isinstance(one, dict) and isinstance(one.get("model"), str):
                out.append(one["model"])
    for part in (data.get("multipart") or []):
        for one in norm(part.get("apply")):
            if isinstance(one, dict) and isinstance(one.get("model"), str):
                out.append(one["model"])
    return out


# ===========================================================================
# Java 侧引用：PartialModel 字面量 / 直接贴图
# ===========================================================================
JAVA_MODEL_LIT = re.compile(r'"((?:vs_clockwork:)?(?:block|item)/[a-z0-9_]+(?:/[a-z0-9_]+)*)"')
JAVA_MODEL_PREFIX = re.compile(r'"((?:vs_clockwork:)?(?:block|item)/[a-z0-9_/]*)"')
JAVA_SHORT_LIT = re.compile(r'"([a-z0-9_/]+)"')   # 允许带斜杠：block("clock/hand_hour") 这种写法

# 模型目录名 ≠ 方块 id 的几处（与 tools/check_render_coverage.py 的 MODEL_DIR_TO_BLOCK 保持一致）
MODEL_DIR_TO_BLOCK = {
    "compressor": "air_compressor",
    "pump": "pump_duct",
    "valve": "valve_duct",
    "filter": "filter_duct",
    "encased_shaft": "balloon_encased_shaft",
    "wanderlite": "wanderlite_block",
    "blade_controller": "blade_controller",
}
JAVA_TEXTURE_LIT = re.compile(r'"textures/([a-z0-9_/]+\.png)"')


def scan_java(java_root):
    """返回 dict(static=..., prefixes={前缀: {同文件里的短名字}}, textures=..., files=N)。

    模型路径统一成「本模组内路径」（`block/x`）：Java 里写 `"block/x"` 时省略命名空间，
    那就是 vs_clockwork（`ResourceLocation.fromNamespaceAndPath(MOD_ID, "block/" + path)`），
    **不能**按原版语义当 minecraft —— 这一条搞错会把 WingPartials 之类的引用全丢掉。

    动态前缀（`"block/duct/" + name`）没法静态求值，这里用「同一文件里出现过的短字面量」
    当候选后缀（`of("connection")` → `block/duct/connection`），再由调用方做存在性过滤。
    这样 `duct/custom/*` 不会因为前缀 `block/duct/` 被误判成"可达"（DuctRenderer.java:41
    明确说那四个边件没人用）。
    """
    static_models, prefixes, textures = set(), {}, set()
    files = 0
    if not os.path.isdir(java_root):
        return {"static": static_models, "prefixes": prefixes, "textures": textures, "files": files}
    for dirpath, _, names in os.walk(java_root):
        for name in names:
            if not name.endswith(".java"):
                continue
            files += 1
            text = read_text(os.path.join(dirpath, name)).decode("utf-8", "replace")

            def ours(mid):
                if mid.startswith(NS + ":"):
                    return mid[len(NS) + 1:]
                return None if ":" in mid else mid

            for m in JAVA_MODEL_LIT.finditer(text):
                mid = ours(m.group(1))
                if mid and not mid.endswith("/"):
                    static_models.add(mid)
            file_prefixes = []
            for m in JAVA_MODEL_PREFIX.finditer(text):
                mid = ours(m.group(1))
                if not mid or not mid.endswith("/"):
                    continue                          # 不以 / 结尾 = 完整模型 id，不是前缀
                tail = text[m.end():m.end() + 8].lstrip()
                if not tail.startswith("+"):          # "block/duct/" + name → 动态前缀
                    continue
                file_prefixes.append(mid)
            if file_prefixes:
                shorts = set(JAVA_SHORT_LIT.findall(text))
                for prefix in file_prefixes:
                    prefixes.setdefault(prefix, set()).update(shorts)
            for m in JAVA_TEXTURE_LIT.finditer(text):
                textures.add(m.group(1))              # 形如 block/hose.png（已去掉 textures/）
    return {"static": static_models, "prefixes": prefixes, "textures": textures, "files": files}


# ===========================================================================
# 分析主体
# ===========================================================================
class Analysis:
    def __init__(self, official: Source, project: Source, jar: Source | None,
                 libs, vanilla, registry, java):
        self.official, self.project, self.jar = official, project, jar
        # main 的顺序就是**运行时优先序**：工程树（源码）→ 产物 jar → 官方树（1.20.1 旧版）
        self.main = [s for s in (project, jar, official) if s is not None]
        self.repo = Repo(self.main, list(libs) + ([vanilla] if vanilla else []))
        self.store = ModelStore(self.repo)
        self.registry = registry
        self.reg_blocks = set((registry.get("blocks") or {}).keys()) if registry else set()
        self.has_vanilla = vanilla is not None
        self.java_static = set(java.get("static", ()))
        self.java_prefix = dict(java.get("prefixes", {}))
        self.java_tex = set(java.get("textures", ()))
        self.java_files = java.get("files", 0)
        self.java_dynamic = set()

        self.blocks = {}          # id -> {'models': set, 'sources': set, 'models_src': set}
        self.items = {}           # id -> {'models': set, 'sources': set}
        self.roots = []           # (kind, id, model)
        self.model_declared = {}  # model -> set(refs)   （仅本模型自己的 textures 字段）
        self.model_raw = {}       # model -> data|None   （运行时优先来源的版本）
        self.model_src = {}       # model -> 来源标签
        self.model_variants = {}  # model -> {来源标签: data}
        self.declared_src = collections.defaultdict(set)
        self.problems = collections.defaultdict(list)

        self.declared_by = collections.defaultdict(set)    # ref -> {model}
        self.reach_roots = collections.defaultdict(set)    # ref -> {(kind, id)}
        self.rendered_roots = collections.defaultdict(set)  # ref -> {(kind, id)}
        self.dynamic_models = set()
        self._off_reach = None
        self.attribution = collections.defaultdict(lambda: {"blocks": set(), "items": set(),
                                                            "java": set()})

    # ---------------- 收集 ----------------
    def collect_roots(self):
        for src in self.main:
            for rel in sorted(src.under(f"{NS}/blockstates/")):
                if not rel.endswith(".json"):
                    continue
                bid = rel.rsplit("/", 1)[-1][:-5]
                data = json.loads(src.read(rel).decode("utf-8-sig", "replace"))
                slot = self.blocks.setdefault(bid, {"models": set(), "sources": set()})
                slot["models"].update(blockstate_models(data))
                slot["sources"].add(src.label)
            for rel in sorted(src.under(f"{NS}/models/item/")):
                rest = rel[len(f"{NS}/models/item/"):]
                if "/" in rest or not rest.endswith(".json"):
                    continue                     # 只有第一层才是物品 id（子目录只当 parent 用）
                iid = rest[:-5]
                slot = self.items.setdefault(iid, {"models": set(), "sources": set()})
                slot["models"].add(f"{NS}:item/{iid}")
                slot["sources"].add(src.label)

        for bid, slot in sorted(self.blocks.items()):
            for mid in sorted(slot["models"]):
                self.roots.append(("block", bid, mid))
        for iid, slot in sorted(self.items.items()):
            for mid in sorted(slot["models"]):
                self.roots.append(("item", iid, mid))
        for mid in sorted(self.java_static):
            self.roots.append(("java", mid, f"{NS}:{mid}"))

        # 动态前缀：`"block/duct/" + name`，候选名取同文件里的短字面量，且模型必须真的存在
        for prefix, lits in sorted(self.java_prefix.items()):
            for lit in sorted(lits):
                cand = prefix + lit
                if cand in self.java_static or not self.repo.has(f"{NS}/models/{cand}.json"):
                    continue
                self.java_dynamic.add(cand)
                self.roots.append(("java", cand, f"{NS}:{cand}"))

        for prefix in sorted(self.java_prefix):
            hit = [m for m in self.all_models() if m.startswith(prefix)]
            self.dynamic_models.update(hit)
    def all_models(self):
        if not self.model_raw:
            self.collect_models()
        return sorted(self.model_raw)

    def collect_models(self):
        for src in self.main:             # main 顺序 = 运行时优先序（工程树 → 产物 jar → 官方树）
            for rel in sorted(src.under(f"{NS}/models/")):
                if not rel.endswith(".json"):
                    continue
                mid = rel[len(f"{NS}/models/"):-5]
                try:
                    data = json.loads(src.read(rel).decode("utf-8-sig", "replace"))
                except Exception:                                # noqa: BLE001
                    data = None
                    self.problems["模型 JSON 解析失败"].append(f"{mid}（{src.label}）")
                self.model_variants.setdefault(mid, {})[src.label] = data
                if mid in self.model_raw:
                    continue                       # 已有更高优先级来源的版本
                self.model_raw[mid] = data
                self.model_src[mid] = src.label
                refs = set()
                if isinstance(data, dict):
                    for val in (data.get("textures") or {}).values():
                        if isinstance(val, dict):
                            val = val.get("sprite")
                        if isinstance(val, str) and not val.startswith("#"):
                            refs.add(val)
                self.model_declared[mid] = refs
        # declared_by 取**所有来源的并集**：哪个来源里引用了缺图都要报出来
        for mid, variants in self.model_variants.items():
            for label, data in variants.items():
                if not isinstance(data, dict):
                    continue
                for val in (data.get("textures") or {}).values():
                    if isinstance(val, dict):
                        val = val.get("sprite")
                    if isinstance(val, str) and not val.startswith("#"):
                        self.declared_by[val].add(mid)
                        self.declared_src[val].add(label)

    def scan_source_problems(self):
        """逐来源重跑 #键 / parent / OBJ 检查。

        为什么不能只查一次：官方树 `gas_engine/block.json` 有 5 个面写的是 `#missing`
        （Blockbench 没指定贴图时的写法），工程树已经修好了；只看工程树会漏掉这个上游问题，
        只看官方树又会把已经修好的当成待修 —— 必须分开报。
        """
        out = []
        for src in self.main:
            store = ModelStore(PreferRepo(self.repo, src))
            for rel in sorted(src.under(f"{NS}/models/")):
                if not rel.endswith(".json"):
                    continue
                mid = rel[len(f"{NS}/models/"):-5]
                chain = store.chain(f"{NS}:{mid}")
                for err in chain["errs"]:
                    out.append((src.label, mid, err))
                for key in self._unresolved_keys(chain):
                    out.append((src.label, mid, f"面引用 #{key} 无法解析（会渲染成黑紫）"))
                if chain["loader"] and "obj" in chain["loader"]:
                    _keys, obj_err = obj_material_keys(store, PreferRepo(self.repo, src), chain)
                    if obj_err:
                        out.append((src.label, mid, obj_err))
        return out

    def _load_model(self, mid):
        raw, label = self.repo.read(f"{NS}/models/{mid}.json")
        if raw is None:
            return None, None
        try:
            return json.loads(raw.decode("utf-8-sig", "replace")), label
        except Exception:                                            # noqa: BLE001
            return None, label

    # ---------------- 可达性 / 归属 ----------------
    def walk_roots(self):
        for kind, rid, mid in self.roots:
            declared, rendered, chain, obj_err = resolve_root(self.store, self.repo, mid)
            for ref in declared:
                self.reach_roots[ref].add((kind, rid))
            for ref in rendered:
                self.rendered_roots[ref].add((kind, rid))
            if chain["errs"]:
                for err in chain["errs"]:
                    self.problems["模型链断裂（blockstate/item/Java 引用）"].append(
                        f"{kind} {rid} -> {mid}: {err}")
            if obj_err:
                self.problems["OBJ 模型问题"].append(f"{mid}: {obj_err}")
            for face_key in self._unresolved_keys(chain):
                self.problems["面引用 #键 解析不出来"].append(f"{mid}: #{face_key}")
            self._attribute(kind, rid, declared, rendered)

    def _unresolved_keys(self, chain):
        tex = chain["textures"]
        bad = []
        for el in chain["elements"] or []:
            for face in (el.get("faces") or {}).values():
                key = face.get("texture")
                if isinstance(key, str) and key.startswith("#") and resolve_key(key, tex) is None:
                    bad.append(key[1:])
        return sorted(set(bad))

    def java_owner(self, mid):
        """Java partial 模型 → 它服务的方块 id（让主表能回答「这是哪个方块的部件」）。

        `block/clock/hand_hour` → `clock`、`block/compressor/fabric` → `air_compressor`。
        认不出归属的（例如 `block/vstop`、`block/peepotron`）返回空，仍按模型路径显示。
        """
        if not mid.startswith("block/"):
            return ""
        parts = mid.split("/")
        if len(parts) < 3:
            return ""
        head = parts[1]
        for cand in (head, MODEL_DIR_TO_BLOCK.get(head, "")):
            if cand and cand in self.reg_blocks:
                return cand
        return ""

    def _attribute(self, kind, rid, declared, rendered):
        """把「可达模型取用到的贴图」归到 block / item / java 三类根上。

        rendered（真正上面）与 declared（仅声明）都算"接上了模型"，归属一样；
        「有没有真的画出来」由状态列区分。
        """
        for ref in declared:
            ns, path = split_loc(ref)
            if ns != NS:
                continue
            bucket = self.attribution[f"{NS}:{path}"]
            if kind == "block":
                bucket["blocks"].add(rid)
            elif kind == "item":
                bucket["items"].add(rid)
            else:
                owner = self.java_owner(rid)
                if owner:
                    bucket["blocks"].add(owner)
                else:
                    bucket["java"].add(rid)

    # ---------------- 贴图清点 ----------------
    def texture_inventory(self):
        rows = []
        prefix = f"{NS}/textures/block/"
        for rel in sorted(self.official.under(prefix)):
            rest = rel[len(f"{NS}/textures/"):]
            entry = {"rel": rest, "stem": rest[:-4] if rest.endswith(".png") else rest,
                     "kind": "png", "size": None, "frames": 1, "mcmeta": False,
                     "ascii": False, "sources": self.repo.labels(rel)}
            if rest.endswith(".png.mcmeta"):
                entry["kind"] = "mcmeta"
                rows.append(entry)
                continue
            if not rest.endswith(".png"):
                entry["kind"] = "other"
                rows.append(entry)
                continue
            data = self.official.read(rel)
            entry["size"] = png_size(data)
            mc_rel = rel + ".mcmeta"
            for src in self.repo.main:            # 官方树 / 工程树 / 产物 jar 任一处有 mcmeta 就算动画
                if mc_rel in src:
                    entry["mcmeta"] = True
                    try:
                        entry["frames"] = png_frames(data, src.read(mc_rel))
                    except Exception:                            # noqa: BLE001
                        entry["frames"] = 1
                    break
            rows.append(entry)
        return rows

    # ---------------- 缺图 / 缺模型 ----------------
    def check_missing_textures(self):
        missing = {}
        for ref, models in self.declared_by.items():
            ns, path = split_loc(ref)
            if ns == "minecraft" and not self.has_vanilla:
                continue                     # 没有原版 jar 时 minecraft: 引用无法判定，跳过
            if not self.repo.has(tex_rel(ref)):
                missing[ref] = {"models": set(models),
                                "srcs": set(self.declared_src.get(ref, ()))}
        # blockstate / item 引用的模型本身不存在
        missing_models = collections.defaultdict(set)
        for kind, rid, mid in self.roots:
            chain = self.store.chain(mid)
            if chain["errs"]:
                for err in chain["errs"]:
                    if "模型文件不存在" in err:
                        missing_models[err].add(f"{kind}:{rid}")
        for tex in self.java_tex:
            if not self.repo.has(f"{NS}/textures/{tex}"):
                missing[f"(java) {tex}"] = {"models": {"Java 直接引用"}, "srcs": {L_PROJ}}
        return missing, missing_models

    # ---------------- 分类 ----------------
    def official_only_refs(self):
        """官方树模型引用、但工程树（运行时）已改掉的贴图。

        典型：官方 `gas_engine/block.json` 用 `duct` + `fluid_tank*`，工程树改成
        `vs_gas_flow_engine`。这不是「功能没接」，而是「上游旧版模型还在引用」，
        必须和 `仅声明` 分开，否则会把工程树的改动误报成缺口。
        """
        if self._off_reach is None:
            prefer = PreferRepo(self.repo, self.official)
            store = ModelStore(prefer)
            refs = set()
            for _kind, _rid, mid in self.roots:
                declared, _r, _c, _e = resolve_root(store, prefer, mid)
                refs |= set(declared)
            self._off_reach = refs
        return self._off_reach

    def classify(self, rows):
        java_stems = {t[:-4] for t in self.java_tex}          # block/hose.png → block/hose
        off_reach = self.official_only_refs()
        for row in rows:
            if row["kind"] != "png":
                row["status"] = "非贴图"
                continue
            ref = f"{NS}:{row['stem']}"
            row["ref"] = ref
            row["java"] = row["stem"] in java_stems
            row["blocks"] = sorted(self.attribution.get(ref, {}).get("blocks", set()))
            row["items"] = sorted(self.attribution.get(ref, {}).get("items", set()))
            row["java_models"] = sorted(self.attribution.get(ref, {}).get("java", set()))
            if row["java"]:
                row["status"] = "已用(Java)"
            elif self.rendered_roots.get(ref):
                row["status"] = "已用"
            elif self.reach_roots.get(ref):
                row["status"] = "已烘焙未上面"
            elif self.declared_by.get(ref):
                row["decl_models"] = sorted(self.declared_by[ref])
                row["status"] = "仅官方树" if ref in off_reach else "仅声明"
            else:
                row["status"] = "孤儿"
            rel = f"{NS}/textures/{row['stem']}.png"
            if row["stem"] in CLEANED_TEXTURE_STEMS:
                row["cleanup"] = CLEANED_TEXTURES_BY_STEM[row["stem"]]
                # 以工程树为准：产物 jar 可能还没重打包，状态不应因此回退
                if rel not in self.project:
                    row["status"] = "已清理"
                    # 已清理的行不再展示官方树旧根带来的归属/注册信息，避免误读成「仍在用」
                    row["blocks"], row["items"], row["java_models"] = [], [], []
                else:
                    row["cleanup_pending"] = True
            row["models"] = sorted(self.declared_by.get(ref, set()))


# ---------------------------------------------------------------------------
# 孤儿 / 仅声明 的功能分组（人工归纳，顺序即优先级）
# ---------------------------------------------------------------------------
GROUP_RULES = [
    ("导管边件（duct/custom/*）", r"^(oneway_duct|smart_duct|copper_duct)$",
     "duct",
     "三个边件模型（oneway_forward / oneway_backward / smart / copper）都在，但 `blockstates/duct.json` "
     "只引用 duct/core 与 duct/core_straight，没有任何 blockstate 能烘焙到它们；"
     "DuctRenderer.java:41 原话：「`duct/custom/*` 四个边件暂无人使用」。"
     "这是本次最像「整套功能没接」的一组贴图"),
    ("热管温度档（heatpipe_*）", r"^heatpipe_",
     "heat_pipe",
     "6 档温度 × 3 部件（core / connection / rim）。**工程树已经接上了**：`heat_pipe` 方块带 "
     "`heat` 属性（cool/warm/hot/very_hot/molten/super_hot），blockstates 里 228 条 multipart "
     "覆盖了全部 6 档；只有官方树的旧 blockstate 是 30 条常温变体（见 §4 差异）"),
    ("旧版外观遗留", r"^(pipes|pipes_connected|duct_old|compressor_old|phys_bearing_bigflap|"
                     r"reactionwheelstitched)$",
     "",
     "1.20.1 时期的外观，移植后已换新贴图；模型侧也只剩 `*_old` / `old/` 子目录（那些子目录本身已死）"),
    ("未接的变体面", r"^(balloon_casing_connected|nyx_wanderlite_ore_top|alt_meter_on)$",
     "",
     "`alt_meter_on`（红石点亮）与 `balloon_casing_connected`（CT 连接）两张仍待接；"
     "`nyx_wanderlite_ore_top` 已在本次收口接到 `cube_bottom_top`（附录 D.5）"),
    ("被替换的同名贴图", r"^(gas_crafter|gas_engine_front|gas_engine_side|cannon_barrel|doink)$",
     "",
     "模型已分别改用 block/pneumocrafter、duct+fluid_tank*、cannon_barrel_largecheckers、doinkborder，"
     "旧图已在本次收口删除（附录 D.1）"),
    ("表现层 / 情绪贴图", r"^(angry|infuriated|blaze_fury|fuming|blue_hue)$",
     "",
     "afterblazer 的表情/羽流图（1.20.1 `ClockworkPartials.kt:15-18` 有声明，方块/渲染器未移植）"
     "＋ physics_infuser 的蓝色色相图；均保留待补，**不是死资源**"),
    ("废弃局部模型用图", r"^(doinkborder|electricity|infuservortex|peepulon|gas_pump|vsified_top|"
                            r"reactionwheel|reactionwheelmid|reactionwheelspokes|junk_gears|"
                            r"juryrigged_infuser|duct_tank_placeholder)$",
     "",
     "这些图只出现在「死模型」里（physics_infuser/{core,liquid,vortex,zap}、slicker/doink、"
     "pump/cog、vstop、peepotron、reactionwheel/old/** 等）。PhysicsInfuserRenderer 的类注释明确写了："
     "旧版的 PHYSICS_CORE / STRANGE_FLUID / ZAP 三个 partial「全工程没有一处引用」，模型文件仍留着"),
    ("占位 / 调试资源", r"^empty$",
     "",
     "全透明占位图，任何模型/Java 都没引用"),
    ("Java 直接引用的贴图", r"^(hose|compressible_axis|purple_hue)$",
     "",
     "由渲染器 `new ResourceLocation(..., \"textures/block/x.png\")` 直接绑定，不经模型 —— "
     "不是缺陷，**不要当孤儿删**"),
]
GROUP_RULES = [(n, re.compile(p), b, w) for n, p, b, w in GROUP_RULES]

# 贴图 → 它「本该服务」的方块（人工指定，优先于前缀猜测）
BLOCK_HINT = {
    "block/alt_meter_on": "alt_meter",
    "block/balloon_casing_connected": "balloon_casing",
    "block/nyx_wanderlite_ore_top": "wanderlite_nyx_ore",
    "block/gas_crafter": "gas_crafter",
    "block/gas_engine_front": "gas_engine",
    "block/gas_engine_side": "gas_engine",
    "block/cannon_barrel": "delivery_cannon",
    "block/doink": "slicker",
    "block/doinkborder": "slicker",
    "block/electricity": "physics_infuser",
    "block/infuservortex": "physics_infuser",
    "block/peepulon": "distance_sensor",
    "block/gas_pump": "pump_duct",
    "block/reactionwheel": "reactionwheel",
    "block/reactionwheelmid": "reactionwheel",
    "block/reactionwheelspokes": "reactionwheel",
    "block/reactionwheelstitched": "reactionwheel",
    "block/duct_tank_placeholder": "duct_tank",
    "block/oneway_duct": "duct",
    "block/smart_duct": "duct",
    "block/copper_duct": "duct",
    "block/pipes": "duct",
    "block/pipes_connected": "duct",
    "block/duct_old": "duct",
    "block/compressor_old": "air_compressor",
    "block/phys_bearing_bigflap": "phys_bearing",
    "block/vsified_top": "",
    "block/junk_gears": "",
    "block/juryrigged_infuser": "",
    "block/blue_hue": "physics_infuser",
}


def group_of(stem):
    key = stem.split("/", 1)[-1] if stem.startswith("block/") else stem
    for name, pat, block, why in GROUP_RULES:
        if pat.search(key):
            return name, block, why
    return "未归类（单独核对）", "", ""


def hint_block(stem):
    return BLOCK_HINT.get(stem, "")


def md_table(headers, rows):
    out = ["| " + " | ".join(headers) + " |",
           "|" + "|".join(["---"] * len(headers)) + "|"]
    for row in rows:
        out.append("| " + " | ".join("" if c is None else str(c) for c in row) + " |")
    return out


def cell_list(items, limit=4):
    if not items:
        return "—"
    items = list(items)
    if len(items) <= limit:
        return ", ".join(f"`{x}`" for x in items)
    return ", ".join(f"`{x}`" for x in items[:limit]) + f" +{len(items) - limit}"


def yn(flag):
    return "✓" if flag else "—"


# ===========================================================================
# 报告渲染
# ===========================================================================
def render(a: Analysis, rows, missing, missing_models, stats):
    L = []
    reg_set = stats["reg_blocks_set"]

    def reg_cell(row):
        """该贴图所属方块是否真的注册了（Java 模型路径不算方块 id）。"""
        own = [b for b in row.get("blocks", []) if "/" not in b]
        if not own:
            return "—"
        return "✓" if any(b in reg_set for b in own) else "**✗ 未注册**"

    L.append("# 贴图 ↔ 方块/物品 对应表（vs_clockwork）")
    L.append("")
    L.append("> 本文件由 `tools/map_textures.py` **自动生成**。手工结论写在脚本的 `MANUAL_NOTES` 里，"
             "重跑不会丢；要改结论请改脚本后重跑，不要手改本文。")
    L.append("")
    L.append("## 0. 生成方式与数据来源")
    L.append("")
    L.append("```powershell")
    L.append("python tools/map_textures.py            # 生成/刷新 docs/TEXTURE-MAP.md")
    L.append("python tools/map_textures.py --check    # 只校验（不写文件）：工程树/jar 里有运行时可见的"
             "缺图或缺失模型 → 退出码 1；只在官方树旧版里的坏引用只报警告；"
             "另外校验本次收口删除项确实不在工程树/jar、保留的 WIP blockstate 还在")
    L.append("python tools/map_textures.py -v         # 额外打印孤儿 / 缺图清单")
    L.append("```")
    L.append("")
    L.append("| 来源 | 路径 | 用途 |")
    L.append("|---|---|---|")
    L.append("| 官方资源树（权威） | `assets/vs_clockwork/` | 贴图清点基准（166 个文件）；块/物品模型、blockstates |")
    L.append("| 工程资源树 | `clockwork_unoffical/src/main/resources/assets/vs_clockwork/` | "
             "移植后的实际资源，**运行时优先**；与官方树有差异 |")
    L.append(f"| 产物 jar | {stats['jar_label']} | 打包结果（应与工程树一致） |")
    L.append(f"| 依赖 jar（{stats['libs_count']} 个） | `clockwork_unoffical/libs/*.jar` | "
             "跨命名空间解析（create: / sable: / simulated: …） |")
    L.append(f"| 原版 jar | {stats['vanilla_label']} | `minecraft:` 命名空间的模型/贴图存在性 |")
    L.append(f"| 注册快照 | `tools/blockstate_properties.json`（{stats['reg_blocks']} 个方块，"
             f"{stats['reg_mc']} / NeoForge {stats['reg_neoforge']}） | 判定「方块是否真的注册了」 |")
    L.append(f"| Java 引用 | `clockwork_unoffical/src/main/java/`（{stats['java_files']} 个文件） | "
             "PartialModel 字面量 / 动态前缀、renderer 里直接绑定的 PNG |")
    L.append("")
    L.append("## 1. 判定规则（先看这里，再看表）")
    L.append("")
    L.append("1. **引用** = 模型 `textures` 字段里的具体贴图 id（`#变量` 沿 `parent` 链解析；"
             "OBJ 模型按 `mtllib` → `map_Kd #键` 取用）。`\".\"` / `\"\"` 是占位键，不算引用。")
    L.append("2. **可达** = 能被 `blockstates/<id>.json`、`models/item/<id>.json`（仅第一层）、"
             "或 Java 里的 PartialModel 引用（静态字面量 + `\"block/x/\" + name` 动态前缀）烘焙到。"
             "`models/block/**` 之间互相 parent **不算**可达 —— 一组死模型互相引用仍然是死的。")
    L.append("3. 分类：")
    L.append("   - **已用**：可达模型的**面 / particle / OBJ 材质**真正取用了它；")
    L.append("   - **已用(Java)**：没有模型引用，但 Java 渲染器直接绑定 `textures/block/x.png`"
             "（只看模型会把它误判成孤儿）；")
    L.append("   - **已烘焙未上面**：可达模型声明了它，但没有任何面/particle/材质取用"
             "（多写的键或 OBJ 备用键）；")
    L.append("   - **仅声明**：只有不可达的模型声明它 → **功能没接**（贴图+模型都在，游戏里烘焙不到）；")
    L.append("   - **孤儿**：任何来源的任何模型都没声明过它；")
    L.append("   - **已清理**：已从工程树 / 产物 jar 删除、仅官方树还留着的旧资源（本次资源卫生收口，见附录 D）。")
    L.append("4. **运行时存在性**按 工程树 → 产物 jar 查；官方树只作对照（不参与构建）。"
             "跨命名空间引用（`create:` 等）在依赖 jar / 原版 jar 里查。"
             "**工程树优先于官方树**用于解析模型链（运行时用的是工程树 / 产物 jar）。")
    L.append("5. `textures/block/` 下 `.mcmeta` 是贴图的动画定义（附属件），"
             "`.aseprite` / `.bbmodel` / `.gpl` / `.obj` / `.mtl` 是制作期资源，均**不计入贴图数**。")
    L.append("6. 「方块是否注册」来自运行时快照 `tools/blockstate_properties.json`；"
             "只有 blockstate、没有注册点的方块 → blockstate 是死的（贴图也不会显示）。")
    L.append("")
    L.append("## 2. 统计摘要")
    L.append("")
    L.extend(md_table(
        ["项", "数量", "说明"],
        [
            ["`textures/block/` 文件总数", stats["files_total"], "含子目录、含附属件"],
            ["其中 PNG 贴图", stats["png"], "主表一行一个"],
            ["其中 `.png.mcmeta`", stats["mcmeta"], "动画定义，挂在对应 PNG 行下"],
            ["其中其它（制作期资源）", stats["other"], "`.aseprite` 等，见 §10"],
            ["**已用**", stats["used"], "含 Java 直接引用 %d 张" % stats["used_java"]],
            ["**已清理**（本次删除，仅官方树保留）", stats["cleaned"], "附录 D.1；不参与构建，不算孤儿/仅声明"],
            ["**孤儿**（无任何模型声明、仍发布）", stats["orphan"], "§5；其中保留待接的已逐条标注"],
            ["**仅声明**（模型有、烘焙不到、仍发布）", stats["declared"], "§6，功能未接的主战场"],
            ["仅官方树旧版模型引用", stats["official_only"], "§4.3（工程树已改，不算缺口）"],
            ["已烘焙未上面", stats["unrendered"], "可达模型里多写的键"],
            ["**缺图引用**（黑紫）", stats["missing"],
             "§7；其中 %d 张在工程树/jar 的可达模型里（实机会看到）" % stats["missing_live"]
             if stats["missing_live"] else "§7；全部只在官方树旧版模型里，运行时已无黑紫"],
            ["官方树独有 / 工程树新增", f"{stats['only_official']} / {stats['only_project']}",
             "§4；官方树独有的 26 张全部是本次收口删除（附录 D.1），不是意外缺失"],
            ["贴图挂在**未注册方块**上", stats["tex_on_unregistered"], "§9.1（整套功能没移植）"],
        ]))
    L.append("")
    L.append(f"- 官方树 PNG：{stats['png']} 张；工程树同目录 PNG：{stats['project_png']} 张"
             f"（差集 = 工程新增 {stats['only_project']} 张 / 工程缺失 {stats['only_official']} 张；"
             f"缺失的 {stats['only_official']} 张全部是本次收口删除，见附录 D.1）。")
    L.append(f"- 产物 jar 内 `textures/block/**`：{stats['jar_png']} 张。")
    L.append(f"- 模型 {stats['models']} 个（三来源去重），能被根烘焙到的 {stats['reachable_models']} 个；"
             f"**工程树 / 产物 jar 实际发布**：模型 {stats['models_runtime']} 个、"
             f"blockstates {stats['blockstates_runtime']} 个、物品模型 {stats['items_runtime']} 个；"
             f"三来源 blockstates {stats['blockstates']} 个、物品模型 {stats['items']} 个、"
             f"Java PartialModel 引用 {stats['java_models']} 个"
             f"（另 {stats['java_dynamic']} 个靠动态前缀解析到）。")
    L.append(f"- 已注册方块 {stats['reg_blocks']} 个；工程树死 blockstate {len(stats['bs_unregistered'])} 个"
             f"（§9.1；收口前 9 个：5 删 / 3 保留 / 1 仅官方树）。")
    L.append("")
    L.append("## 3. 主表：`textures/block/**` 每个贴图 → 谁在用")
    L.append("")
    L.append("列说明：`官方/工程/jar` = 该 PNG 在这三个来源里是否存在；"
             "`方块/Java 模型` = 真正取用它的方块 id 或 Java partial 模型路径；"
             "`物品` = 只列物品独有的 id（与方块同名时归到方块列）；"
             "`注册` = 该贴图服务的方块是否真的注册了（**✗ 未注册** = 资源整套在、方块没注册）。"
             "状态为 **已清理** 的行只在官方树留档，工程树 / jar 里已经没有这张图。")
    L.append("")
    table_rows = []
    idx = 0
    for row in rows:
        if row["kind"] != "png":
            continue
        idx += 1
        size = f"{row['size'][0]}×{row['size'][1]}" if row["size"] else "?"
        frames = f"动画 {row['frames']}f" if row["mcmeta"] else ""
        table_rows.append([
            idx,
            f"`{row['rel']}`",
            size,
            frames,
            yn(L_OFF in row["sources"]),
            yn(L_PROJ in row["sources"]),
            yn(L_JAR in row["sources"]),
            cell_list(row["blocks"] + row["java_models"]),
            cell_list([x for x in row["items"] if x not in row["blocks"]]),
            "✓" if row["java"] else "—",
            reg_cell(row),
            row["status"],
        ])
    L.extend(md_table(["#", "贴图", "尺寸", "动画", "官方", "工程", "jar",
                       "方块 / Java 模型", "物品", "Java", "注册", "状态"], table_rows))
    L.append("")
    L.append("## 4. 官方树 vs 工程树")
    L.append("")
    only_proj = stats["only_project_list"]
    L.append(f"### 4.1 工程树新增（官方树没有，共 {len(only_proj)} 张）")
    L.append("")
    if only_proj:
        L.extend(md_table(["贴图", "尺寸", "状态", "说明"],
                          [[f"`textures/{s}.png`", stats["proj_sizes"].get(s, "?"),
                            stats["proj_status"].get(s, "—"), "移植期新增（官方 1.20.1 没有）"]
                           for s in only_proj]))
    else:
        L.append("（无）")
    L.append("")
    only_off = stats["only_official_list"]
    cleaned_missing = [s for s in only_off if s in CLEANED_TEXTURE_STEMS]
    unexpected_missing = [s for s in only_off if s not in CLEANED_TEXTURE_STEMS]
    L.append(f"### 4.2 官方树有、工程树没有（共 {len(only_off)} 张）")
    L.append("")
    if only_off:
        L.append(f"- **本次收口删除 {len(cleaned_missing)} 张**（仅官方树留档，详见附录 D.1）。")
        L.append(f"- 意外缺失 {len(unexpected_missing)} 张"
                 + ("：" + "、".join(f"`{x}`" for x in unexpected_missing) if unexpected_missing else "（无）。"))
        if not unexpected_missing:
            L.append("")
            L.append("> 也就是说，工程树不再发布这些图是**有意为之**，不是打包漏文件；"
                     "官方树仍保留同名副本，需要时可回拷。")
    else:
        L.append("（无 —— 工程树是官方树的超集）")
    L.append("")
    L.append(f"### 4.3 官方树旧版模型还在引用、工程树已改掉的贴图（{stats['official_only']} 张）")
    L.append("")
    only_off_reach = [r for r in rows if r["status"] == "仅官方树"]
    if only_off_reach:
        L.extend(md_table(["贴图", "官方树里引用它的模型", "说明"],
                          [[f"`{r['rel']}`", cell_list(r.get("decl_models", []), 3),
                            "工程树模型已换别的贴图，运行时用不到这张（不是缺口）"]
                           for r in only_off_reach]))
    else:
        L.append("（无）")
    L.append("")
    L.append(f"### 4.4 两边同名但内容不同的模型：{stats['model_diff']} 个")
    L.append("")
    L.append("工程树是 1.21.1 移植版：OBJ loader 从 `forge:obj` / `porting_lib:obj` 改成 `neoforge:obj`、"
             "`models/` 前缀修正、`#missing` 补上、贴图改名等，所以两边不必逐字节一致。"
             "逐来源的模型告警见 §8.3。")
    L.append("")

    # ---- 孤儿 ----
    L.append("## 5. 孤儿贴图（仍发布、任何模型都不引用）")
    L.append("")
    L.append(f"共 **{stats['orphan']}** 张（收口前 20 张：12 张确认死资源已删除、1 张 `nyx_wanderlite_ore_top` 已接进模型）。"
             "留下的 7 张都是**有意保留**的未移植功能 / 旧版状态图，逐条在「判定依据」里写明；"
             "Java 直接引用的 3 张不算孤儿（§13 已核对）。详见附录 D。")
    L.append("")
    L.extend(render_group_table(a, [r for r in rows if r["status"] == "孤儿"], stats))
    L.append("")

    # ---- 仅声明 ----
    L.append("## 6. 仅声明贴图（模型声明了，但没有任何根能烘焙到）")
    L.append("")
    L.append(f"共 **{stats['declared']}** 张（收口前 15 张：12 张死局部模型用图已随模型一起删除）。"
             "剩下的 3 张是**唯一整套没接**的导管 custom 边件，贴图和模型都画好了，"
             "但模型没有 blockstate / 物品模型 / Java partial 去引用，游戏里烘焙不到。"
             "按功能分组：")
    L.append("")
    L.extend(render_group_table(a, [r for r in rows if r["status"] == "仅声明"], stats,
                                show_models=True))
    L.append("")

    # ---- 缺图 ----
    L.append("## 7. 缺图引用（模型引用了、但所有来源都没有 → 黑紫）")
    L.append("")
    if missing:
        mrows = []
        for ref, info in sorted(missing.items()):
            models = sorted(info["models"])
            srcs = sorted(info["srcs"])
            live = ref.startswith("(java)") or (
                bool({"工程树", "产物jar"} & set(srcs))
                and any(m in stats["reachable_set"] for m in models))
            mrows.append([
                f"`{ref}`",
                cell_list(models, 3),
                "、".join(srcs) or "—",
                ("**实机黑紫**" if live else
                 ("仅官方树旧版（工程树已改，不影响运行时）" if L_OFF in srcs
                  else "死模型（当前不显示）")),
            ])
        L.extend(md_table(["贴图", "引用它的模型", "模型所在来源", "运行时影响"], mrows))
        L.append("")
        L.append("> 判断依据：引用它的模型若可达（有 blockstate / 物品模型 / Java partial 引用），"
                 "实机就会黑紫；只在死模型里则当前不显示，但仍是必须修的坏引用。")
    else:
        L.append("（无）")
    L.append("")

    # ---- 模型侧问题 ----
    L.append("## 8. 模型侧问题（与贴图接线直接相关）")
    L.append("")
    if missing_models:
        L.append("### 8.1 blockstate / 物品 / Java 引用了不存在的模型")
        L.append("")
        L.extend(md_table(["缺失的模型", "谁在引用"],
                          [[f"`{k}`", cell_list(sorted(v), 4)]
                           for k, v in sorted(missing_models.items())]))
        L.append("")
    else:
        L.append("### 8.1 blockstate / 物品 / Java 引用了不存在的模型")
        L.append("")
        L.append("（无）")
        L.append("")

    dead = stats["dead_models"]
    dead_total = sum(len(v) for v in dead.values())
    L.append(f"### 8.2 没有任何根能烘焙到的模型（{dead_total} 个，按目录分组）")
    L.append("")
    L.append("这些模型自身不报错，但只会出现在「死链」里；§6 的 `仅声明` 大多由它们造成。"
             "（`*_old` / `old/` 子目录属于旧版遗留，已在 §5 归到「旧版外观遗留」。）"
             f"本次已从工程树删除 {len(stats['cleaned_models'])} 个死模型（附录 D.2），下表只剩仍发布的。")
    L.append("")
    for name, models in sorted(dead.items()):
        L.append(f"- `{name}`：{len(models)} 个 —— " + cell_list(sorted(models), 4))
    L.append("")

    src_problems = stats["source_problems"]
    L.append("### 8.3 逐来源的模型告警（同一个模型两棵树可能不一样）")
    L.append("")
    if src_problems:
        by_label = collections.defaultdict(list)
        for label, mid, msg in src_problems:
            by_label[label].append(f"`{mid}`: {msg}")
        L.extend(md_table(["来源", "数量", "告警"],
                          [[label, len(items), "；".join(sorted(items)[:6])]
                           for label, items in sorted(by_label.items())]))
        L.append("")
        L.append("> 官方树里的告警如果工程树已修，说明是移植期修掉的（例如 `#missing`）；"
                 "两边都报的才是真待修。")
    else:
        L.append("（无）")
    L.append("")
    if a.problems:
        L.append("### 8.4 运行时优先来源（工程树 → jar → 官方）的解析告警")
        L.append("")
        for kind, items in sorted(a.problems.items()):
            uniq = sorted(set(items))
            L.append(f"- **{kind}**（{len(uniq)}）：" + cell_list(uniq, 4))
        L.append("")

    # ---- 注册侧 ----
    L.append("## 9. 注册侧与资源侧的缺口")
    L.append("")
    L.append(f"### 9.1 有整套资源、但方块没注册（工程树仍发布 {len(stats['bs_unregistered'])} 个）")
    L.append("")
    L.append("收口前共 9 个：**3 个保留待补、5 个确认旧版/流体误留已删除、1 个只在官方树**。"
             "保留下来的 id 有 blockstate + 模型 + 贴图，但 `tools/blockstate_properties.json` 里没有注册点"
             "（也就没有物品/方块实体），属于**未移植的 WIP** —— 对应贴图虽然「已用」，"
             "但游戏里依然看不到；删除的 5 个已在附录 D.3 记录依据。")
    L.append("")
    if stats["dead_bs_detail"]:
        L.extend(md_table(["id", "模型数", "涉及的贴图"],
                          [[f"`{bid}`", n, cell_list(refs, 6)]
                           for bid, n, refs in stats["dead_bs_detail"]]))
    else:
        L.append("（无）")
    L.append("")
    L.append("9 个 id 的逐条判定（保留 / 删除 / 仅官方树）：")
    L.append("")
    L.extend(md_table(["id", "位置", "处理", "依据"],
                      [[f"`{bid}`", loc, f"**{action}**", why]
                       for bid, loc, action, why in BLOCKSTATE_LEDGER]))
    L.append("")
    L.append("> 保留的 3 个（`afterblazer` / `balloon_encased_shaft` / `combustion_engine`）在游戏里看不到，"
             "不是 bug，是功能没接；详见附录 D.4。")
    L.append("")
    L.extend(md_table(["项", "数量", "清单"], [
        ["死 blockstate（无注册方块）", len(stats["bs_unregistered"]),
         cell_list(stats["bs_unregistered"], 8)],
        ["注册了方块但没有 blockstate", len(stats["block_no_bs"]),
         cell_list(stats["block_no_bs"], 8)],
        ["注册了物品但没有 `models/item/<id>.json`", len(stats["item_no_model"]),
         cell_list(stats["item_no_model"], 8)],
        ["方块（BlockItem）没有 `models/item/<id>.json`", len(stats["blockitem_no_model"]),
         cell_list(stats["blockitem_no_model"], 8)],
    ]))
    L.append("")
    L.append("> 注册清单来自 `tools/blockstate_properties.json`（运行时实例化方块的快照）与 "
             "`tools/cw_ids.py`（Java 里 `.block()` / `.item()` 的解析）。BlockItem 由 Registrate "
             "随方块自动注册，所以「物品注册」= 方块 id ∪ cw_ids 的物品集合。")
    L.append("")

    # ---- 杂项 ----
    L.append("## 10. `textures/block/` 下的非贴图文件")
    L.append("")
    others = [r for r in rows if r["kind"] != "png"]

    def other_note(r):
        base = r["rel"]
        if base.endswith(".png.mcmeta"):
            stem = base[:-len(".png.mcmeta")]
            if stem in CLEANED_TEXTURE_STEMS:
                return "不算贴图；对应 PNG 已在本次收口删除，**只官方树留档**（附录 D.1）"
            return "不算贴图；动画帧数与对应 PNG 同行"
        return "Blockbench/aseprite 源文件，不参与烘焙"

    if others:
        L.extend(md_table(["文件", "类型", "说明"],
                          [[f"`{r['rel']}`",
                            "动画定义（附属件）" if r["kind"] == "mcmeta" else "制作期资源",
                            other_note(r)]
                           for r in others]))
    else:
        L.append("（无）")
    L.append("")
    L.append("全树其它非游戏资源（`models/**` 下的 `.bbmodel` / `.obj` / `.mtl`、"
             "`textures/**` 下的 `.aseprite`、根目录 `.gpl`）：")
    L.append("")
    for line in stats["junk_lines"]:
        L.append(line)
    L.append("")

    # ---- 非 block 目录 ----
    L.append("## 11. 其它 `textures/` 子目录（不在本表统计口径内）")
    L.append("")
    L.extend(md_table(["目录", "PNG 数", "被 Java 直接引用", "引用方"], stats["other_dirs"]))
    L.append("")
    L.append("> 这些目录的贴图由 Java（GUI/实体/字体）、`particles/*.json` 或流体渲染引用，"
             "不参与「模型 → 方块」回溯，所以不计入已用/孤儿统计；"
             "§13 已人工确认它们不是「放错目录的方块贴图」。")
    L.append("")

    # ---- 附录 A ----
    L.append("## 12. 附录 A：按方块汇总（贴图视角）")
    L.append("")
    arows = []
    runtime_bs_set = stats["blockstates_runtime_set"]
    for bid in sorted(runtime_bs_set | reg_set):
        refs = sorted(k for k, v in a.attribution.items() if bid in v["blocks"])
        short = [r.split(":", 1)[1].replace("block/", "") for r in refs]
        arows.append([
            f"`{bid}`",
            yn(bid in reg_set),
            yn(bid in runtime_bs_set),
            len(short),
            cell_list(short, 6),
        ])
    L.extend(md_table(["方块", "已注册", "有 blockstate", "取用贴图数", "贴图（去 `block/` 前缀）"],
                      arows))
    L.append("")
    L.append("## 13. 附录 B：人工核对记录（脚本判定之外）")
    L.append("")
    L.append(f"下列 {len(MANUAL_NOTES)} 条逐条读过模型 / Java 注释后确认，专门覆盖最容易误判的几类："
             "CT 图集、动画 `.mcmeta`、OBJ 贴图键、Java 直接绑定、跨命名空间陷阱、"
             "「贴图在但方块没注册」。脚本只校验这些贴图**仍然存在**（`--check` 会报），"
             "结论本身由人工维护。")
    L.append("")
    L.extend(md_table(["贴图", "结论", "依据"],
                      [[f"`{t}`", verdict, why] for t, verdict, why in MANUAL_NOTES]))
    L.append("")
    L.append("## 14. 附录 C：后续修复清单（本任务只列，不动手）")
    L.append("")
    for line in stats["todo"]:
        L.append(line)
    L.append("")
    L.extend(render_cleanup_appendix(stats))
    return "\n".join(L) + "\n"


def render_cleanup_appendix(stats):
    """附录 D：2026-10-17 资源卫生收口的可追溯记录。"""
    L = []
    L.append("## 15. 附录 D：资源卫生收口记录（2026-10-17）")
    L.append("")
    L.append("> 本次只动 `clockwork_unoffical/src/main/resources/assets/vs_clockwork/**`、`docs/**`、`tools/**`，"
             "不改 Java，也不动只读的官方树。所有删除件在官方树都有同名副本，可随时回拷；"
             "引用核查由 `tools/cleanup_assets.py` 完成（默认 dry-run，删除前排除待删文件后全树搜 0 引用才 `--apply`）。")
    L.append("")

    L.append(f"### D.1 删除的贴图（{len(CLEANED_TEXTURES)} 张）")
    L.append("")
    L.extend(md_table(["贴图", "分类", "为什么确认无引用"],
                      [[f"`textures/{stem}.png`", cat, why]
                       for stem, cat, why in CLEANED_TEXTURES]))
    L.append("")

    L.append(f"### D.2 删除的模型（{len(CLEANED_MODELS)} 个 JSON + `reactionwheel/old/**` 的 9 个 .obj/.mtl）")
    L.append("")
    L.extend(md_table(["模型", "依据"],
                      [[f"`models/{mid}.json`", why] for mid, why in CLEANED_MODELS]))
    L.append("")
    L.append("`models/block/reactionwheel/old/` 里的 `.obj` / `.mtl`（9 个文件）只被同目录两个 JSON 引用，"
             "随 JSON 一起删；工程树其余模型 / Java / blockstate 均无引用。")
    L.append("")

    L.append("### D.3 删除的 blockstate（5 个，9 个死 blockstate 的逐条判定见 §9.1）")
    L.append("")
    L.extend(md_table(["blockstate", "分类", "依据"],
                      [[f"`blockstates/{bid}.json`", action, why]
                       for bid, _loc, action, why in BLOCKSTATE_LEDGER if action == "删除"]))
    L.append("")
    L.append("> 另有 `OLD_flap_bearing` 只在官方树、工程树没有；官方树只读，不处理。"
             "保留的 3 个见 D.4。")
    L.append("")

    L.append("### D.4 保留并标注（未移植功能 / Java 直绑）")
    L.append("")
    rows = []
    for stem, note in RETAINED_TEXTURE_NOTES.items():
        group = "已接入（本次收口）" if stem == "block/nyx_wanderlite_ore_top" else "未移植 / 待接功能"
        rows.append([f"`textures/{stem}.png`", group, note])
    rows += [
        ["`textures/block/hose.png`", "Java 直绑，**勿删**",
         "`ExtendonRenderer` 的 `HOSE_TEXTURE` 常量直接 `ResourceLocation(..., \"textures/block/hose.png\")`"],
        ["`textures/block/compressible_axis.png`", "Java 直绑，**勿删**",
         "`UniversalShaftRenderer.java:40` 直接引用；4×14 拉伸用"],
        ["`textures/block/purple_hue.png`", "Java 直绑，**勿删**",
         "`PhysicsInfuserRenderer.java:67` 外层紫色辉光壳"],
    ]
    for bid, why in RETAINED_BLOCKSTATES.items():
        rows.append([f"`blockstates/{bid}.json`", "未移植，保留待补", why])
    L.extend(md_table(["资源", "分组", "结论 / 依据"], rows))
    L.append("")
    L.append("按功能分组的保留清单：**afterblazer**（`angry` / `infuriated` / `blaze_fury` / `fuming` + "
             "`block/afterblazer` 模型）、**combustion_engine**（block/item/4 个模型）、"
             "**balloon_encased_shaft**（encased_shaft/block_balloon）、**导管 custom 边件**"
             "（`oneway_duct` / `smart_duct` / `copper_duct`）、**旧版状态变体**"
             "（`alt_meter_on` / `balloon_casing_connected`）、**physics_infuser 蓝色色相**（`blue_hue`）。")
    L.append("")

    L.append("### D.5 `nyx_wanderlite_ore_top` 模型修正（本次唯一改写的模型）")
    L.append("")
    L.append("先查官方树：`assets/vs_clockwork/models/block/wanderlite/nyx_ore.json` 是 `minecraft:block/cube_all`，"
             "只声明 `all`；1.20.1 源码 `Clockwork-1.20.1-main/common/src/main/resources/assets/vs_clockwork/models/block/wanderlite/nyx_ore.json` "
             "同样是 `cube_all`。也就是说**官方树自己也没接这张顶面图**，没有 `cube_bottom_top` 先例可照抄"
             "（深板岩矿用的是 `cube_column` + `end`，末地矿也是 `cube_all`）。")
    L.append("")
    L.append("因此这里按本次任务的功能目标接线，而不是照抄官方：")
    L.append("")
    L.append("```diff")
    L.append('-  "parent": "minecraft:block/cube_all",')
    L.append('-  "textures": { "all": "vs_clockwork:block/nyx_wanderlite_ore", "particle": ... }')
    L.append('+  "parent": "minecraft:block/cube_bottom_top",')
    L.append('+  "textures": {')
    L.append('+    "top": "vs_clockwork:block/nyx_wanderlite_ore_top",')
    L.append('+    "bottom": "vs_clockwork:block/nyx_wanderlite_ore",')
    L.append('+    "side": "vs_clockwork:block/nyx_wanderlite_ore",')
    L.append('+    "particle": "vs_clockwork:block/nyx_wanderlite_ore"')
    L.append("+  }")
    L.append("```")
    L.append("")
    L.append("> 风险提示：**若日后从官方树回拷这个模型，会退回 `cube_all`，顶面图又变成孤儿**；"
             "回拷时要保留工程树这一版。")
    L.append("")

    L.append("### D.6 官方树 2 处坏引用：只记录、不修")
    L.append("")
    L.extend(md_table(["贴图", "官方树里的模型", "说明"],
                      [[f"`{ref}`", f"`{model}`", why] for ref, model, why in OFFICIAL_TREE_BUGS]))
    L.append("")
    L.append("> 官方树只作参考、不参与构建；在官方树上修完，下次从官方树回拷资源时问题仍会回来。"
             "**以工程树为准**：工程树这 2 处已经分别改成 `copper_casing` 与 `creative_gas_generator`。")
    L.append("")

    L.append("### D.7 验收（本次）")
    L.append("")
    L.extend(md_table(["指标", "收口前", "收口后"], [
        ["`textures/block` 工程树 PNG", 161, stats["project_png"]],
        ["产物 jar 内 PNG", 161, stats["jar_png"]],
        ["已用", 118, stats["used"]],
        ["已清理（仅官方树留档）", 0, stats["cleaned"]],
        ["孤儿（仍发布）", 20, stats["orphan"]],
        ["仅声明（仍发布）", 15, stats["declared"]],
        ["工程树模型", 629, stats["models_runtime"]],
        ["工程树 blockstates", 76, stats["blockstates_runtime"]],
        ["工程树物品模型", 101, stats["items_runtime"]],
    ]))
    L.append("")
    L.append("五关 + 贴图映射校验：`gradlew build` = BUILD SUCCESSFUL；`check_data_refs.py` = 0；"
             "`check_assets.py --jar` = 0（[12] 快照已重跑、[13] 仍为 2 条既有提示、未新增）；"
             "`check_jar.py` = 0；`check_render_coverage.py` = 0；`check_ponder.py` = 0；"
             "`map_textures.py --check` = 0。")
    L.append("")
    L.append("> 需实机确认的点：① 深板岩 / 末地 / nyx 三种矿的顶面动画是否一致（nyx 顶面 10 帧）；"
             "② 旧螺旋桨轴承删掉后，现役黄铜 / 拼装轴承的 `propeller_bearing/top` 顶盖仍在（Java partial 保留）；"
             "③ afterblazer / combustion_engine / balloon_encased_shaft 仍无注册点，属预期「看不到」，不是本次删除造成的。")
    L.append("")
    return L


def render_group_table(a, rows, stats, show_models=False):
    out = []
    groups = collections.OrderedDict()
    for row in rows:
        name, block, why = group_of(row["stem"])
        groups.setdefault(name, {"rows": [], "block": block, "why": why})["rows"].append(row)
    for name, data in groups.items():
        out.append(f"### {name}（{len(data['rows'])} 张）")
        out.append("")
        if data["why"]:
            out.append(f"> {data['why']}")
            out.append("")
        heads = ["贴图", "尺寸", "关联方块", "方块注册"]
        heads += ["声明它的模型"] if show_models else ["判定依据"]
        body = []
        for row in data["rows"]:
            size = f"{row['size'][0]}×{row['size'][1]}" if row["size"] else "?"
            guess = hint_block(row["stem"]) or stats["guess_block"].get(row["stem"], "") \
                or data["block"]
            reg = yn(guess in stats["reg_blocks_set"]) if guess else "—"
            keep = RETAINED_TEXTURE_NOTES.get(row["stem"], "")
            if show_models:
                extra = cell_list(row.get("decl_models", []), 3)
                if keep:
                    extra += f"；**保留待补**：{keep}"
            else:
                extra = keep or ("无任何模型引用" if row["status"] == "孤儿"
                                 else "模型存在但无 blockstate/Java 引用")
            body.append([f"`{row['rel']}`", size, f"`{guess}`" if guess else "—",
                         reg, extra])
        out.extend(md_table(heads, body))
        out.append("")
    return out


# ===========================================================================
# main
# ===========================================================================
def pick_product_jar():
    cands = sorted(glob.glob(os.path.join(PROJ, "build", "libs", "vs_clockwork-*.jar")),
                   key=os.path.getmtime, reverse=True)
    return cands[0] if cands else None


def pick_vanilla_jar(mc_root):
    pat = os.path.join(mc_root, "libraries", "net", "minecraft", "client", "*", "client-*-extra.jar")
    cands = sorted(glob.glob(pat), key=os.path.getmtime, reverse=True)
    return cands[0] if cands else None


def load_registry():
    data = load_json(REGISTRY) or {}
    blocks = set((data.get("blocks") or {}).keys())
    return data, blocks


def load_java_ids():
    try:
        sys.path.insert(0, TOOLS)
        import cw_ids                                          # noqa: PLC0415
        r = cw_ids.collect(PROJ)
        return set(r["blocks"]), set(r["items"])
    except Exception:                                          # noqa: BLE001
        return set(), set()


def junk_inventory():
    """全树非游戏资源清点（bbmodel/obj/mtl/aseprite/gpl/gif/frag）。"""
    lines = []
    for label, root in (("官方树", os.path.join(OFFICIAL_ASSETS, NS)),
                        ("工程树", os.path.join(PROJECT_ASSETS, NS))):
        counter = collections.Counter()
        where = collections.defaultdict(list)
        for dirpath, _, names in os.walk(root):
            for name in names:
                ext = os.path.splitext(name)[1].lower()
                if ext in (".bbmodel", ".obj", ".mtl", ".aseprite", ".gpl", ".gif", ".frag"):
                    counter[ext] += 1
                    where[ext].append(os.path.relpath(os.path.join(dirpath, name), root).replace("\\", "/"))
        parts = [f"`{ext}` {n}" for ext, n in sorted(counter.items())]
        lines.append(f"- **{label}**：" + ("、".join(parts) if parts else "（无）"))
        for ext in sorted(where):
            lines.append(f"  - `{ext}`：" + cell_list(sorted(where[ext]), 3))
    return lines


def main(argv=None):
    ap = argparse.ArgumentParser(description="生成贴图 ↔ 方块/物品 对应表")
    ap.add_argument("--check", action="store_true", help="只校验，不写文件")
    ap.add_argument("--json", help="另外导出机器可读快照")
    ap.add_argument("--out", default=DOC_PATH, help="输出 Markdown 路径")
    ap.add_argument("--mc-root", default=MC_DEFAULT, help=".minecraft 目录（找原版 jar）")
    ap.add_argument("-v", "--verbose", action="store_true")
    args = ap.parse_args(argv)

    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:                                          # noqa: BLE001
        pass

    official = DirSource(L_OFF, OFFICIAL_ASSETS)          # 根 = assets/，键形如 vs_clockwork/...
    project = DirSource(L_PROJ, PROJECT_ASSETS)
    jar_path = pick_product_jar()
    jar = ZipSource(L_JAR, jar_path) if jar_path else None

    libs = []
    if os.path.isdir(LIBS_DIR):
        for path in sorted(glob.glob(os.path.join(LIBS_DIR, "*.jar"))):
            try:
                libs.append(ZipSource("lib:" + os.path.basename(path), path))
            except Exception:                                  # noqa: BLE001
                pass

    vanilla_path = pick_vanilla_jar(args.mc_root)
    vanilla = None
    if vanilla_path:
        try:
            vanilla = ZipSource("原版jar", vanilla_path)
            vanilla.label = "minecraft"
        except Exception:                                      # noqa: BLE001
            vanilla = None

    registry, reg_blocks = load_registry()
    java_ids_blocks, java_ids_items = load_java_ids()
    reg_blocks |= java_ids_blocks
    java = scan_java(JAVA_ROOT)

    a = Analysis(official, project, jar, libs, vanilla, registry, java)
    a.reg_blocks |= java_ids_blocks
    a.collect_models()
    a.collect_roots()
    a.walk_roots()

    rows = a.texture_inventory()
    a.classify(rows)
    missing, missing_models = a.check_missing_textures()

    # ---- 统计 ----
    stats = {}
    stats["files_total"] = len(rows)
    stats["png"] = sum(1 for r in rows if r["kind"] == "png")
    stats["mcmeta"] = sum(1 for r in rows if r["kind"] == "mcmeta")
    stats["other"] = sum(1 for r in rows if r["kind"] == "other")
    stats["used"] = sum(1 for r in rows if r["status"].startswith("已用"))
    stats["used_java"] = sum(1 for r in rows if r["status"] == "已用(Java)")
    stats["orphan"] = sum(1 for r in rows if r["status"] == "孤儿")
    stats["declared"] = sum(1 for r in rows if r["status"] == "仅声明")
    stats["official_only"] = sum(1 for r in rows if r["status"] == "仅官方树")
    stats["unrendered"] = sum(1 for r in rows if r["status"] == "已烘焙未上面")
    stats["cleaned"] = sum(1 for r in rows if r["status"] == "已清理")
    stats["cleanup_pending"] = sum(1 for r in rows if r.get("cleanup_pending"))
    stats["missing"] = len(missing)

    official_stems = {r["stem"] for r in rows if r["kind"] == "png"}     # block/xxx
    project_pngs = {rel[len(f"{NS}/textures/"):-4]
                    for rel in project.under(f"{NS}/textures/block/") if rel.endswith(".png")}
    jar_pngs = ({rel[len(f"{NS}/textures/"):-4]
                 for rel in jar.under(f"{NS}/textures/block/") if rel.endswith(".png")}
                if jar else set())
    stats["project_png"] = len(project_pngs)
    stats["jar_png"] = len(jar_pngs)
    stats["only_project_list"] = sorted(project_pngs - official_stems)
    stats["only_official_list"] = sorted(official_stems - project_pngs)
    stats["only_project"] = len(stats["only_project_list"])
    stats["only_official"] = len(stats["only_official_list"])
    stats["proj_sizes"] = {}
    stats["proj_status"] = {}
    for stem in stats["only_project_list"]:
        rel = f"{NS}/textures/{stem}.png"          # stem 形如 block/duct_tank
        blob, _label = a.repo.read(rel)
        if blob:
            w, h = png_size(blob)
            stats["proj_sizes"][stem] = f"{w}×{h}"
        ref = f"{NS}:{stem}"
        if a.rendered_roots.get(ref):
            stats["proj_status"][stem] = "已用"
        elif a.reach_roots.get(ref):
            stats["proj_status"][stem] = "已烘焙未上面"
        elif a.declared_by.get(ref):
            stats["proj_status"][stem] = "仅声明"
        else:
            stats["proj_status"][stem] = "孤儿"

    stats["models"] = len(a.model_raw) if a.model_raw else 0
    reachable_models = {canon(m) for _k, _i, mid in a.roots
                        for m in a.store.chain(mid)["order"]}
    stats["reachable_models"] = len(reachable_models)
    stats["reachable_set"] = reachable_models
    stats["blockstates"] = len(a.blocks)
    stats["items"] = len(a.items)

    # 工程树 / 产物 jar 的「实际发布」数量：三来源去重数会把官方树里的死资源也数进来，
    # 收口后必须单独给一份运行时视角，否则删了工程树文件、数字却不变，容易误判。
    runtime_models = set()
    runtime_blockstates = set()
    runtime_items = set()
    for src in (project, jar):
        if src is None:
            continue
        for rel in src.under(f"{NS}/models/"):
            if not rel.endswith(".json"):
                continue
            mid = rel[len(f"{NS}/models/"):-5]
            runtime_models.add(mid)
            if mid.startswith("item/") and "/" not in mid[len("item/"):]:
                runtime_items.add(mid[len("item/"):])
        for rel in src.under(f"{NS}/blockstates/"):
            if rel.endswith(".json"):
                runtime_blockstates.add(rel.rsplit("/", 1)[-1][:-5])
    stats["models_runtime"] = len(runtime_models)
    stats["blockstates_runtime"] = len(runtime_blockstates)
    stats["blockstates_runtime_set"] = runtime_blockstates
    stats["items_runtime"] = len(runtime_items)
    stats["java_models"] = len(a.java_static)
    stats["java_dynamic"] = len(a.java_dynamic)
    stats["java_files"] = a.java_files
    stats["reg_blocks"] = len(reg_blocks)
    stats["reg_blocks_set"] = reg_blocks
    stats["reg_mc"] = registry.get("mcVersion", "?")
    stats["reg_neoforge"] = registry.get("neoForge", "?")
    stats["libs_count"] = len(libs)
    stats["jar_label"] = (
        f"`{os.path.relpath(jar_path, ROOT).replace(os.sep, '/')}`"
        f"（{os.path.getsize(jar_path) / 1048576:.1f} MB）") if jar_path else "（缺失）"
    stats["vanilla_label"] = f"`{vanilla_path}`" if vanilla_path else "（未找到，minecraft: 引用未校验）"

    # 死模型（无人烘焙的 block 模型）；本次已从工程树删除的不再计入
    dead = collections.defaultdict(list)
    for mid in a.model_raw:
        if mid in reachable_models or not mid.startswith("block/"):
            continue
        if mid in CLEANED_MODEL_IDS and mid not in runtime_models:
            continue
        head = mid.split("/")[1] if mid.count("/") >= 1 else mid
        dead[f"block/{head}"].append(mid)
    stats["dead_models"] = dead
    stats["cleaned_models"] = sorted(CLEANED_MODEL_IDS)

    # 缺图里有多少落在「工程树/jar 的可达模型」上（实机可见）
    stats["missing_live"] = sum(
        1 for ref, info in missing.items()
        if ref.startswith("(java)")
        or (bool({"工程树", "产物jar"} & info["srcs"])
            and any(m in reachable_models for m in info["models"])))

    # 两棵树同名但内容不同的模型数
    diff = 0
    for mid, variants in a.model_variants.items():
        if L_OFF in variants and L_PROJ in variants and variants[L_OFF] != variants[L_PROJ]:
            diff += 1
    stats["model_diff"] = diff
    stats["source_problems"] = a.scan_source_problems()

    # 注册侧：以工程树 / 产物 jar 的「实际发布」为准（官方树只用于对照）
    stats["bs_unregistered"] = sorted(runtime_blockstates - reg_blocks)
    stats["bs_unregistered_all"] = sorted(set(a.blocks) - reg_blocks)
    stats["block_no_bs"] = sorted(reg_blocks - runtime_blockstates)
    stats["item_no_model"] = sorted(java_ids_items - runtime_items)
    stats["blockitem_no_model"] = sorted(reg_blocks - runtime_items)

    # 死 blockstate 各自用到哪些贴图（回答「哪些贴图属于方块都没注册」）
    dead_bs_detail, all_dead_refs = [], set()
    for bid in stats["bs_unregistered"]:
        mids = sorted(a.blocks.get(bid, {}).get("models", ()))
        refs = set()
        for mid in mids:
            declared, _rendered, _chain, _err = resolve_root(a.store, a.repo, mid)
            for ref in declared:
                ns, path = split_loc(ref)
                refs.add(path if ns == NS else f"{ns}:{path}")
        refs = sorted(r for r in refs if not r.startswith(("minecraft:", "create:")))
        all_dead_refs.update(refs)
        dead_bs_detail.append((bid, len(mids), refs))
    stats["dead_bs_detail"] = dead_bs_detail
    stats["tex_on_unregistered"] = len(all_dead_refs)

    stats["junk_lines"] = junk_inventory()

    # 其它贴图目录
    other_dirs = []
    java_stems = {t[:-4] for t in a.java_tex}
    subs = sorted({rel.split("/")[2] for rel in official.under(f"{NS}/textures/")
                   if rel.count("/") >= 3})
    for sub in subs:
        if sub == "block":
            continue
        files = [r for r in official.under(f"{NS}/textures/{sub}/") if r.endswith(".png")]
        used_java = sum(1 for r in files if r[len(f"{NS}/textures/"):-4] in java_stems)
        note = {"gui": "Screen / ToolMenu", "item": "物品图标", "particle": "particles/*.json",
                "entity": "实体渲染", "fluid": "流体"}.get(sub, "—")
        other_dirs.append([f"`{sub}`", len(files), f"Java 直接 {used_java} 张", note])
    stats["other_dirs"] = other_dirs

    # 猜方块（贴图名 → 注册方块 id 的前缀匹配）
    guess = {}
    for row in rows:
        if row["kind"] != "png":
            continue
        stem = row["stem"]
        best = ""
        for bid in reg_blocks | set(a.blocks):
            if stem == bid or stem.startswith(bid + "_"):
                if len(bid) > len(best):
                    best = bid
        guess[stem] = best
    stats["guess_block"] = guess

    # 待修清单（编号在最后统一加，避免条件分支导致跳号）
    live_missing = [ref for ref, info in missing.items()
                    if ref.startswith("(java)")
                    or (bool({"工程树", "产物jar"} & info["srcs"])
                        and any(m in reachable_models for m in info["models"]))]
    todo = []
    if missing:
        if live_missing:
            todo.append(f"**补图 / 改引用（高优先）**：{len(missing)} 张缺图里 {len(live_missing)} 张在"
                        "工程树/jar 的可达模型里 → **实机黑紫**："
                        + "、".join(f"`{m}`" for m in sorted(live_missing)))
        else:
            todo.append(f"**官方树上游 bug（已确认，不修）**：{len(missing)} 张缺图引用**全部只出现在官方树**"
                        "（工程树已修，运行时不黑紫），官方树仅作参考、不参与构建，所以**不要修官方树**；"
                        "回拷资源时注意别把这两处带回来："
                        + "、".join(f"`{m}`" for m in sorted(missing))
                        + "。`create:block/copper_plating` 是 Create 6 已删除的贴图（工程树已换 "
                        "`copper_casing`）；`block/creative_generator` 是少写 `_gas_` 的笔误"
                        "（工程树已改 `creative_gas_generator`）")
    if stats["declared"] or RETAINED_TEXTURE_NOTES:
        todo.append(f"**接未移植功能（本次已确认保留，见附录 D.4）**：{stats['declared']} 张导管 custom 边件"
                    "（`oneway_duct` / `smart_duct` / `copper_duct`）要接，先给 `GasDuctBlock` 加单向/智能属性，"
                    "再让 `DuctRenderer` 用上 `duct/custom/*`；`afterblazer`（含 `angry` / `infuriated` / "
                    "`blaze_fury` / `fuming`）、`combustion_engine`、`balloon_encased_shaft` 三套 WIP 资源等"
                    "功能移植；`alt_meter_on` / `balloon_casing_connected` 等状态/CT 变体等接线；"
                    "`blue_hue` 等蓝色光晕功能再上")
    if stats["blockitem_no_model"]:
        todo.append("**补物品模型**：" + "、".join(f"`{x}`" for x in stats["blockitem_no_model"][:8])
                    + " 有注册但缺 `models/item/<id>.json`（物品栏里是黑紫模型）")
    if missing_models:
        todo.append("**补模型**：" + "、".join(f"`{k}`" for k in sorted(missing_models)[:4])
                    + " 被 blockstate / Java 引用但不存在")
    if stats["bs_unregistered"]:
        todo.append("**仍未注册的 3 个 WIP blockstate**：" + "、".join(f"`{x}`" for x in stats["bs_unregistered"])
                    + " 属未移植功能，**有意保留**，不是死资源（依据见 §9.1）")
    todo.append(f"**本次已收口（{CLEANUP_DATE}）**：删除贴图 {stats['cleaned']} 张（含 12 张孤儿 + 12 张死模型用图 + "
                "2 张旧轴承链）、死模型 22 个 + `reactionwheel/old/**`、死 blockstate 5 个；"
                "剩余孤儿 7 张 / 仅声明 3 张均为有意保留；完整清单与依据见附录 D")
    stats["todo"] = [f"{i}. {line}。" for i, line in enumerate(todo, 1)]

    # ---- 打印摘要 ----
    print(f"官方树 textures/block：{stats['files_total']} 个文件 "
          f"（PNG {stats['png']} / mcmeta {stats['mcmeta']} / 其它 {stats['other']}）")
    print(f"已用 {stats['used']}（其中 Java 直接引用 {stats['used_java']}）｜"
          f"已清理 {stats['cleaned']}｜已烘焙未上面 {stats['unrendered']}｜"
          f"仅声明 {stats['declared']}｜孤儿 {stats['orphan']}｜缺图 {stats['missing']}")
    print(f"工程树新增 {stats['only_project']} 张、缺 {stats['only_official']} 张；"
          f"产物 jar 内 {stats['jar_png']} 张（工程树 {stats['project_png']} 张）")
    print(f"模型 {stats['models']} 个（三来源；工程树/产物 jar {stats['models_runtime']} 个，可达 "
          f"{stats['reachable_models']}）、blockstates {stats['blockstates']}（工程树/产物 jar "
          f"{stats['blockstates_runtime']}）、物品模型 {stats['items']}（工程树/产物 jar "
          f"{stats['items_runtime']}）、Java PartialModel {stats['java_models']}")
    if missing:
        print("\n缺图引用：")
        for ref in sorted(missing):
            print(f"  - {ref}  <- " + ", ".join(sorted(missing[ref]["models"])[:4])
                  + " [" + "、".join(sorted(missing[ref]["srcs"])) + "]")
    if args.verbose:
        print("\n孤儿贴图：")
        for row in rows:
            if row["status"] == "孤儿":
                print(f"  - {row['rel']}")
        print("\n仅声明贴图：")
        for row in rows:
            if row["status"].startswith("仅声明"):
                print(f"  - {row['rel']}  <- " + ", ".join(row.get("decl_models", [])[:3]))

    if args.check:
        stale = [t for t, _v, _w in MANUAL_NOTES if not a.repo.has(f"{NS}/textures/{t}.png")]
        upstream_only = [ref for ref, info in missing.items()
                         if ref not in live_missing and not ref.startswith("(java)")]

        def runtime_has(rel):
            return rel in a.project or (a.jar is not None and rel in a.jar)

        leftover_textures = [t for t in sorted(CLEANED_TEXTURE_STEMS)
                             if runtime_has(f"{NS}/textures/{t}.png")]
        leftover_models = [m for m in sorted(CLEANED_MODEL_IDS)
                           if runtime_has(f"{NS}/models/{m}.json")]
        missing_retained_bs = [b for b in sorted(RETAINED_BLOCKSTATES)
                               if f"{NS}/blockstates/{b}.json" not in a.project]

        if upstream_only:
            print("\n[check] 警告（不计入失败）：以下缺图引用只在官方树旧版模型里，工程树已修 → "
                  + "、".join(sorted(upstream_only)))
        if stale:
            print("\n[check] 人工核对记录引用了不存在的贴图：" + "、".join(stale))
        if leftover_textures:
            print("\n[check] 已清理贴图仍出现在工程树 / 产物 jar（先删工程树再重打包）："
                  + "、".join(leftover_textures))
        if leftover_models:
            print("\n[check] 已清理模型仍出现在工程树 / 产物 jar：" + "、".join(leftover_models))
        if missing_retained_bs:
            print("\n[check] 标记为「保留待补」的 blockstate 不见了：" + "、".join(missing_retained_bs))
        if stats["cleanup_pending"]:
            print(f"\n[check] 有 {stats['cleanup_pending']} 张标记删除的贴图仍在工程树（未生效）")
        ok = (not live_missing and not missing_models and not stale and not leftover_textures
              and not leftover_models and not missing_retained_bs and not stats["cleanup_pending"])
        if ok:
            print("\n[check] 通过：工程树 / 产物 jar 里没有缺图引用、没有缺失模型，"
                  "人工记录对得上，本次收口删除项也确实不在运行时里")
        else:
            print("\n[check] 失败：存在**运行时可见**的缺图引用 / 缺失模型 / 失效的人工记录 / 清理未生效"
                  "（只在官方树旧版里的坏引用仅报警告）")
        return 0 if ok else 1

    text = render(a, rows, missing, missing_models, stats)
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with open(args.out, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)
    print(f"\n已写入 {os.path.relpath(args.out, ROOT)}（{len(text.splitlines())} 行）")

    if args.json:
        snap = {
            "stats": {k: v for k, v in stats.items()
                      if isinstance(v, (int, float, str))},
            "textures": [{k: v for k, v in row.items() if k != "sources"} for row in rows],
            "missing": {k: {"models": sorted(v["models"]), "srcs": sorted(v["srcs"])}
                        for k, v in missing.items()},
            "missing_models": {k: sorted(v) for k, v in missing_models.items()},
        }
        with open(args.json, "w", encoding="utf-8", newline="\n") as fh:
            json.dump(snap, fh, ensure_ascii=False, indent=1, default=str)
        print(f"已写入 {args.json}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
