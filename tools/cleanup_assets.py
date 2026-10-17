#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""资源卫生收口（2026-10-17）：删除已核实无引用的贴图 / 模型 / blockstate。

安全策略
========
1. 默认 **dry-run**：只做引用核查并打印清单，不删任何文件；确认后加 ``--apply``。
2. 只动 ``clockwork_unoffical/src/main/resources/assets/vs_clockwork/**``；
   官方树 ``assets/vs_clockwork/**`` 是只读参考，脚本里连写权限都不会碰。
3. 删除前做「模拟删除」核查：把本次要删的文件全部排除后，扫描工程树剩下的
   JSON（assets + data）和 Java（去掉注释），任何一处还引用待删贴图 / 模型 /
   blockstate 就中止。跨命名空间引用（``create:block/pipes``）不会被误判。
4. 每个文件删除前打印 SHA256；官方树里都留有同名副本，可随时回拷。

用法::

    python tools/cleanup_assets.py             # 只核查（安全）
    python tools/cleanup_assets.py --apply     # 执行删除

退出码：0 = 核查通过 / 删除完成；1 = 发现残留引用或环境不符。
"""
from __future__ import annotations

import hashlib
import os
import re
import shutil
import sys

TOOLS = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(TOOLS)
PROJ = os.path.join(ROOT, "clockwork_unoffical")
ASSETS = os.path.join(PROJ, "src", "main", "resources", "assets", "vs_clockwork")
DATA = os.path.join(PROJ, "src", "main", "resources", "data")
JAVA = os.path.join(PROJ, "src", "main", "java")
NS = "vs_clockwork"

# Java 渲染器直接绑定的贴图：任何情况下都不许进删除清单。
JAVA_BOUND = {"block/hose", "block/compressible_axis", "block/purple_hue"}

# ---------------------------------------------------------------------------
# 待删贴图：stem（相对 textures/）→ (分类, 为什么确认无引用)
# ---------------------------------------------------------------------------
TEXTURES = [
    # --- 被替换的同名贴图（模型已改用新图） ---
    ("block/cannon_barrel", "被替换",
     "delivery_cannon / delivery_chute 全部改用 block/cannon_barrel_largecheckers"),
    ("block/gas_crafter", "被替换",
     "gas_crafter/{block,frame,glow,mesh,tube}.json 的 0/particle 全部指向 block/pneumocrafter"),
    ("block/gas_engine_front", "被替换",
     "工程树 gas_engine/block.json 用 vs_gas_flow_engine + duct"),
    ("block/gas_engine_side", "被替换", "同上"),
    ("block/doink", "被替换",
     "slicker/doink.json 的 0/particle 指向 block/doinkborder；该模型本身也随本次删除"),
    # --- 1.20.1 旧版外观 ---
    ("block/compressor_old", "旧版外观",
     "compressor/compressor.json 用 block/compressor + block/compressor_fabric"),
    ("block/duct_old", "旧版外观",
     "duct/block_old.json / item_old.json 已改用 heatpipe_*"),
    ("block/phys_bearing_bigflap", "旧版外观",
     "phys_bearing/block.json 只用 block/phys_bearing"),
    ("block/pipes", "旧版外观",
     "ballooner 的 particle 是 create:block/pipes（Create 的图），本工程无引用"),
    ("block/pipes_connected", "旧版外观", "同上，无任何模型引用 vs_clockwork:block/pipes_connected"),
    ("block/reactionwheelstitched", "旧版外观",
     "现用 reactionwheel/reactionwheels.png（OBJ 材质键 0/1/2）"),
    # --- 占位 ---
    ("block/empty", "占位", "16×16 全透明占位图，模型 / Java 均无引用"),
    # --- 死局部模型用图（模型本身在 MODELS 里一起删） ---
    ("block/doinkborder", "死模型用图", "只被 slicker/doink.json 引用，该模型已确认无 blockstate/Java 根"),
    ("block/duct_tank_placeholder", "死模型用图",
     "只被 duct_tank/placeholder.json 引用；blockstates/duct_tank.json 只有 bottom/top 四个变体，不引用 placeholder"),
    ("block/electricity", "死模型用图",
     "只被 physics_infuser/zap.json 引用；PhysicsInfuserRenderer 注释写明 ZAP partial 全工程无引用"),
    ("block/gas_pump", "死模型用图",
     "只被 pump/cog.json 引用；PumpDuctRenderer 明确用 Create 的 MECHANICAL_PUMP_COG，不用本工程 pump/cog"),
    ("block/infuservortex", "死模型用图", "只被 physics_infuser/vortex.json 引用"),
    ("block/junk_gears", "死模型用图", "只被 juryrigged_infuser/item.json 引用"),
    ("block/juryrigged_infuser", "死模型用图", "同上"),
    ("block/peepulon", "死模型用图",
     "只被 peepotron.json 引用；距离传感器用 peepulon_1..4 + peepulon_animated"),
    ("block/reactionwheel", "死模型用图", "只被 reactionwheel/old/alsoold/{wheelbottom,wheeltop}.json 引用"),
    ("block/reactionwheelmid", "死模型用图", "同上"),
    ("block/reactionwheelspokes", "死模型用图", "同上"),
    ("block/vsified_top", "死模型用图", "只被 vstop.json 引用"),
    # --- 旧版独立螺旋桨轴承链（blockstate 已确认无注册点，见 BLOCKSTATES） ---
    ("block/propbearingbase", "旧版轴承链",
     "只被 propeller_bearing/{block,item}.json 引用；新轴承用 brass_propeller / juryrigged_propeller"),
    ("block/propbearingpistons", "旧版轴承链",
     "只被 propeller_bearing/piston*.json 引用；propeller_bearing/top.json + propbearingtop 是 Java partial，保留"),
]

# ---------------------------------------------------------------------------
# 待删模型：模型 id（相对 models/，不带 .json）
# ---------------------------------------------------------------------------
MODELS = [
    ("block/physics_infuser/core", "PhysicsInfuserRenderer 类注释：旧版 PHYSICS_CORE partial 全工程无引用"),
    ("block/physics_infuser/liquid", "同上（STRANGE_FLUID）"),
    ("block/physics_infuser/vortex", "同上（VORTEX）"),
    ("block/physics_infuser/zap", "同上（ZAP）"),
    ("block/pump/cog", "PumpDuctRenderer 用 Create 的 MECHANICAL_PUMP_COG；本模型无 blockstate/Java/模型引用"),
    ("block/vstop", "无 blockstate / 物品模型 / Java partial 引用"),
    ("block/peepotron", "无 blockstate / 物品模型 / Java partial 引用"),
    ("block/reactionwheel/old/alsoold/wheelbottom", "旧版 OBJ 链，无 blockstate / Java 引用"),
    ("block/reactionwheel/old/alsoold/wheeltop", "同上"),
    ("block/slicker/doink", "slicker 方块 blockstate 走 slicker/block.json（block/slicker），doink 链无人引用"),
    ("block/juryrigged_infuser/item", "无 models/item/juryrigged_infuser.json、无 blockstate / Java 引用"),
    ("block/duct_tank/placeholder", "blockstates/duct_tank.json 四个变体都不引用它，无 Java 引用"),
    # 流体 blockstate 的伴生模型（1.20.1 FabricClockworkFluids 的 fluid 注册整段注释掉）
    ("block/chocolate_frosting", "流体误留 blockstate 的伴生模型，只有 particle 键"),
    ("block/strawberry_frosting", "同上"),
    ("block/vanilla_frosting", "同上"),
    # 旧版独立螺旋桨轴承（1.20.1 / 1.21 都没有 plain propeller_bearing 方块注册）
    ("block/propeller_bearing/block", "旧版 propeller_bearing blockstate 的模型；现役是 brass/juryrigged"),
    ("block/propeller_bearing/item", "旧版物品模型链；该物品未注册（cw_ids 的 items 里没有）"),
    ("block/propeller_bearing/pistonbotl", "无任何 blockstate / 模型 / Java 引用"),
    ("block/propeller_bearing/pistonbotr", "同上"),
    ("block/propeller_bearing/pistontopl", "同上"),
    ("block/propeller_bearing/pistontopr", "同上"),
    ("item/propeller_bearing", "未注册物品的 item 模型（cw_ids items 无此 id、lang 无键）"),
]

# 整目录删除（含 .obj/.mtl；这些文件只被同目录两个 JSON 引用）
MODEL_DIRS = [
    ("block/reactionwheel/old", "旧版反应轮 OBJ/MTL 全部目录；两个 JSON 与其它 obj 已无外部引用"),
]

# ---------------------------------------------------------------------------
# 待删 blockstate：id → (分类, 依据)
# ---------------------------------------------------------------------------
BLOCKSTATES = [
    ("old_andesite_flap_bearing", "旧版别名",
     "注册方块是 andesite_flap_bearing，复用 andesite_flap_bearing/block；无 lang / 物品 / 代码注册点"),
    ("propeller_bearing", "旧版遗留",
     "1.20.1 与 1.21 都没有 plain propeller_bearing 方块注册；现役 brass/juryrigged_propeller_bearing"),
    ("chocolate_frosting", "流体误留",
     "1.20.1 FabricClockworkFluids.java:24-25 的 fluid 注册整段被注释；流体不该有 blockstate"),
    ("strawberry_frosting", "流体误留", "同上"),
    ("vanilla_frosting", "流体误留", "同上"),
]


def read_text(path):
    with open(path, "rb") as fh:
        return fh.read().decode("utf-8", "replace")


def iter_ref_files():
    """工程树里可能引用资源的文本文件（排除待删文件由调用方处理）。"""
    exts = (".json", ".bbmodel", ".mtl", ".obj")
    for root in (ASSETS, DATA):
        for dp, _, names in os.walk(root):
            for name in names:
                if name.endswith(exts):
                    yield os.path.join(dp, name)
    for dp, _, names in os.walk(JAVA):
        for name in names:
            if name.endswith(".java"):
                yield os.path.join(dp, name)


def strip_java_comments(text):
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


def ref_patterns(kind, stem_id):
    """ref_patterns('block/foo') → 能匹配 'block/foo' / 'vs_clockwork:block/foo' 的正则。"""
    q = re.escape(stem_id)
    return [
        # JSON / Java 字符串里的完整 id（必须引号收尾，避免 foo 匹配 foo_bar）
        re.compile(r'"' + re.escape(NS) + ":" + q + r'"'),
        re.compile(r'(?<![:\\w])"' + q + r'"'),
        # 贴图/模型文件路径的另一种写法（Java 直接绑定 textures/block/x.png）
        re.compile(r"(?:textures/)" + q + r"\.png"),
    ]


def is_deleted(path, deleted_paths, deleted_dirs):
    ap = os.path.abspath(path)
    if ap in deleted_paths:
        return True
    return any(ap == d or ap.startswith(d + os.sep) for d in deleted_dirs)


def check_no_refs(targets, deleted_paths, deleted_dirs):
    """targets: [(kind, id, why)]；返回问题列表。"""
    problems = []
    files = [p for p in iter_ref_files() if not is_deleted(p, deleted_paths, deleted_dirs)]
    cache = {}
    for kind, ident, _why in targets:
        pats = ref_patterns(kind, ident)
        for path in files:
            if path not in cache:
                text = read_text(path)
                if path.endswith(".java"):
                    text = strip_java_comments(text)
                cache[path] = text
            for pat in pats:
                m = pat.search(cache[path])
                if m:
                    rel = os.path.relpath(path, ROOT)
                    problems.append(f"{ident} 仍被引用: {rel} -> {m.group(0)}")
                    break
    return problems


def collect_targets():
    files, dirs = [], []
    for stem, _cat, _why in TEXTURES:
        if stem in JAVA_BOUND:
            raise SystemExit(f"拒绝删除 Java 直接绑定贴图: {stem}")
        png = os.path.join(ASSETS, "textures", stem + ".png")
        files.append(png)
        mcmeta = png + ".mcmeta"
        if os.path.exists(mcmeta):
            files.append(mcmeta)
    for mid, _why in MODELS:
        files.append(os.path.join(ASSETS, "models", mid + ".json"))
    for d, _why in MODEL_DIRS:
        dirs.append(os.path.join(ASSETS, "models", d))
    for bid, _cat, _why in BLOCKSTATES:
        files.append(os.path.join(ASSETS, "blockstates", bid + ".json"))
    # 目录里的 JSON 不再单独列（MODEL_DIRS 整目录删除，避免重复计数/重复 unlink）
    files = [p for p in files
             if not any(os.path.abspath(p).startswith(os.path.abspath(d) + os.sep) for d in dirs)]
    return files, dirs


def main(argv):
    apply = "--apply" in argv
    files, dirs = collect_targets()

    missing = [p for p in files if not os.path.exists(p)]
    if missing:
        print("以下文件不存在（可能已清理过）：")
        for p in missing:
            print("  " + os.path.relpath(p, ROOT))

    deleted_paths = {os.path.abspath(p) for p in files}
    deleted_dirs = {os.path.abspath(d) for d in dirs}

    targets = [("block", stem, why) for stem, _c, why in TEXTURES]
    for mid, why in MODELS:
        targets.append(("block" if mid.startswith("block/") else "item", mid, why))

    problems = check_no_refs(targets, deleted_paths, deleted_dirs)

    # blockstate 不是「被引用」的对象；核查它确实没有注册点
    sys.path.insert(0, TOOLS)
    import cw_ids  # noqa: PLC0415

    ids = cw_ids.collect(PROJ)
    for bid, _cat, _why in BLOCKSTATES:
        if bid in ids["blocks"]:
            problems.append(f"blockstate {bid} 对应的方块仍注册在 Java 里，不能删")
        if bid not in ids["blocks"] and not os.path.exists(os.path.join(ASSETS, "blockstates", bid + ".json")):
            print(f"  [已不存在] blockstates/{bid}.json")

    if problems:
        print("\n核查失败：")
        for p in problems:
            print("  " + p)
        return 1

    print(f"引用核查通过：待删贴图 {len(TEXTURES)}、模型 {len(MODELS) + sum(1 for _ in MODEL_DIRS)} 组、"
          f"blockstate {len(BLOCKSTATES)}；剩余工程树文件里 0 处引用。")

    total = 0
    for path in files:
        if not os.path.exists(path):
            continue
        data = open(path, "rb").read()
        print(f"  {os.path.relpath(path, ROOT)}  sha256={hashlib.sha256(data).hexdigest()[:16]}  {len(data)}B")
        total += 1
        if apply:
            os.remove(path)
    for d in dirs:
        if not os.path.isdir(d):
            continue
        for dp, _, names in os.walk(d):
            for name in sorted(names):
                p = os.path.join(dp, name)
                data = open(p, "rb").read()
                print(f"  {os.path.relpath(p, ROOT)}  sha256={hashlib.sha256(data).hexdigest()[:16]}  {len(data)}B")
                total += 1
        if apply:
            shutil.rmtree(d)
    print(("\n已删除" if apply else "\n[dry-run] 将删除") + f" {total} 个文件。")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
