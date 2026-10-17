#!/usr/bin/env python3
"""渲染覆盖审计：全注册方块 / 物品的 BER、partial、RenderShape、模型链一次性核对。

为什么要有这个脚本（对应 2026-10 的实机反馈「建议检查所有方块的渲染」）：
  1. 1.21.1 *静默* 忽略很多东西 —— 模型贴图键解析不出来是黑紫、`models/item` 缺文件是黑紫、
     BER 没注册则是「方块还在，但旋转部件 / 指示条 / 指针干脆不画」。这三种在日志里都可能一行都没有，
     只能静态对照「旧版是谁在画」。
  2. 旧版的渲染入口只有一处：`ClockworkBlockEntities.kt` 的 `.renderer{}`（33 条），
     而工程是用 Registrate `blockEntity("x").validBlocks(...).renderer(() -> X::new)` 写的，
     两边名字也不总一样（`SterlingEngineRenderer` / `GasEngineRenderer`、
     `WingBlockEntityRenderer` / `WingColorRenderer`），所以必须按**方块 id**对照，不能按类名。

检查项（每项都只报「可证实的缺口」）：
  [RC1] 旧版有 BER 的方块 ↔ 工程是否登记了 BER（按 validBlocks 反查，不按类名）
  [RC2] 每个 `*Partials` 类：是否有幂等 `init()`、是否被 `CWClientSetup` 提前调用、
        每个 `PartialModel` 指向的模型文件是否真的存在（缺了就是 missingno 黑紫）
  [RC3] `RenderShape` 与旧版对照（漏设 `ENTITYBLOCK_ANIMATED` = 静态一份 + BER 一份叠着）
  [RC4] `models/item/<id>.json` 及其 parent / 贴图键链可解析（复用 tools/cw_assets_lib.py）
  [RC5] 旧版自定义物品渲染器（`*ItemRenderer`）↔ 工程是否等价（名字不同要显式登记）
  [RC6] 「孤立子模型」：既不被 blockstate/parent 链引用、也不被任何 Java 引用的
        `models/block/<block>/*.json` —— 这通常就是「该由 BER 画、但 BER 没写」的部件

用法：
    python tools/check_render_coverage.py            # 只打印问题
    python tools/check_render_coverage.py --write     # 重新生成 docs/RENDER-AUDIT.md
    python tools/check_render_coverage.py --selftest  # 注入故障，验证每项都能报出来
"""
import argparse
import glob
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROJ = os.path.join(ROOT, "clockwork_unoffical")
OLD = os.path.join(ROOT, "Clockwork-1.20.1-main")
OFFICIAL = os.path.join(ROOT, "assets", "vs_clockwork")
ASSETS = os.path.join(PROJ, "src", "main", "resources", "assets", "vs_clockwork")
JAVA = os.path.join(PROJ, "src", "main", "java")
CLIENT_SETUP = os.path.join(JAVA, "org", "valkyrienskies", "clockwork", "index", "CWClientSetup.java")

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cw_ids  # noqa: E402
from cw_assets_lib import Assets  # noqa: E402

# ---------------------------------------------------------------------------
# 排除 / 在案：范围与「不是缺陷的差异」都写在这里，脚本只对没登记的差异报警。
# 每个条目都必须给出可核查的理由（文件或行），否则就是在掩盖问题。
# ---------------------------------------------------------------------------

# 其它任务负责的方块（本轮不碰，也不计入问题）
EXCLUDED = {
    "air_compressor": "另一任务：空气压缩机",
    "clock": "另一任务：落地钟指针",
    "pump_duct": "另一任务：气泵",
    "hose_port": "与软管卷轴同一套交互（extendon_hose），随另一任务",
    "duct_tank": "另一任务：储罐外观",
}
# 2026-10-17：extendon 的 BER（ExtendonRenderer + ExtendonPartials）已在本任务补齐并做了
# 6 朝向 × 26 连接方向的静态自检（tools/selftest_extendon_orient.py），不再豁免：
# 取消豁免后本脚本 RC1..RC7 全部为 0，说明它现在也接受常规审计。

# 旧版有 BER、工程按设计没有 —— 理由必须写清（评审时只看这张表）
KNOWN_EQUIVALENT = {
    "goo_block": "旧版 GooBlockEntityRenderer.renderSafe 只有 super 调用（空实现），无动态部件",
    "universal_shaft": "旧版 ENTITYBLOCK_ANIMATED 由 BER 画整块；本移植改回 MODEL 形状，BER 只画连接软管"
                       "（UniversalShaftBlock 类注释：避免不注册 Flywheel visual 时方块消失）",
}

# 旧版有 BER、工程确实缺、但本轮无法安全补（缺前置设施）——单独列出，不计入「可修问题」。
# 2026-10-17 收口后 solver / delivery_chute 都已补上，这里清空；留表是为了后续再有"在案不修"时
# 有一个必须写理由的固定位置，而不是散落在文档里。
DEFERRED = {}


def rel(path):
    return os.path.relpath(path, ROOT).replace("\\", "/")


def read(path):
    with open(path, encoding="utf-8", errors="replace") as f:
        return f.read()


# ---------------------------------------------------------------------------
# 解析：Registrate 注册点 → {方块 id: ...} / {方块实体 id: ...}
# ---------------------------------------------------------------------------

BLOCK_DECL = re.compile(r'BlockEntry<\s*[\w.]+\s*>\s+([A-Z0-9_]+)\s*=\s*REGISTRATE\s*\.block\(\s*"([a-z0-9_]+)"', re.S)
# CWBlocks 这类文件把注册写在本地辅助方法里（simple("nyx", …) / ore("…")），
# 字面量只出现在调用点；这里按"字段类型 + 调用"把 id→类 补回来（否则矩阵里那十行是空的）
HELPER_BLOCK_DECL = re.compile(
    r'BlockEntry<\s*([\w.]+)\s*>\s+[A-Z0-9_]+\s*=\s*(?:simple|ore)\(\s*"([a-z0-9_]+)"')
BE_DECL = re.compile(r'\.blockEntity(?:<[^>]*>)?\(\s*"([a-z0-9_]+)"', re.S)
VALID = re.compile(r'\.validBlocks\(([^)]*)\)', re.S)
RENDERER_CALL = re.compile(r'\.renderer\(\s*\(\)\s*->\s*([\w.]+)\s*::\s*new')


def parse_java_registrations():
    """返回 (field_to_id, id_to_class, be_map[be_id]={'blocks':..,'renderer':..,'where':..})。"""
    field_to_id, id_to_class, be_map = {}, {}, {}
    for path in glob.glob(os.path.join(JAVA, "**", "*.java"), recursive=True):
        text = read(path)
        blocks = BLOCK_DECL.findall(text)
        for field, bid in blocks:
            field_to_id.setdefault(field, bid)
            id_to_class.setdefault(bid, None)
        for m in re.finditer(r'\.block\(\s*"([a-z0-9_]+)"\s*,\s*([\w.]+)::new', text):
            id_to_class[m.group(1)] = m.group(2)
        for cls, bid in HELPER_BLOCK_DECL.findall(text):
            id_to_class.setdefault(bid, cls.split(".")[-1])
        # 方块实体：从 .blockEntity("id" 到 .register()
        for m in BE_DECL.finditer(text):
            be_id = m.group(1)
            segment = text[m.start(): text.find(".register()", m.start())]
            if segment is None:
                continue
            renderer = RENDERER_CALL.search(segment)
            blocks = []
            for vm in VALID.finditer(segment):
                for name in re.findall(r'([A-Z0-9_]+)', vm.group(1)):
                    if name in field_to_id:
                        blocks.append(field_to_id[name])
            be_map.setdefault(be_id, {
                "blocks": blocks,
                "renderer": renderer.group(1).split(".")[-1] if renderer else None,
                "where": f"{rel(path)}:{text[:m.start()].count(chr(10)) + 1}",
                "raw": "",
            })
    return field_to_id, id_to_class, be_map


# 旧版（Kotlin / 1.20.1）：方块字段 → id 在 ClockworkBlocks.kt，BER 在 ClockworkBlockEntities.kt
OLD_BLOCK_DECL = re.compile(r'val\s+([A-Z0-9_]+)\s*(?::\s*[^=\n]+)?=\s*(.{0,400}?)\.block\(', re.S)
OLD_BE_SPLIT = re.compile(r'\n    val\s+([A-Z0-9_]+)')


def parse_old():
    blocks_kt = os.path.join(OLD, "common", "src", "main", "kotlin", "org", "valkyrienskies",
                             "clockwork", "ClockworkBlocks.kt")
    ents_kt = os.path.join(OLD, "common", "src", "main", "kotlin", "org", "valkyrienskies",
                           "clockwork", "ClockworkBlockEntities.kt")
    field_to_id = {}
    btext = read(blocks_kt)
    for m in OLD_BLOCK_DECL.finditer(btext):
        after = btext[m.end() - len(".block("):]
        q = re.match(r'\.block\(\s*"([a-z0-9_]+)"', after, re.S)
        if q:
            field_to_id.setdefault(m.group(1), q.group(1))

    text = read(ents_kt)
    # 去掉 Kotlin 行注释：旧版 HOSE_PORT 的 .renderer{} 整段是注释掉的，
    # 不去掉会把它当成"旧版有 BER"（第一版就在这里误报）
    text = re.sub(r'(?m)^\s*//.*$', '', text)
    be_map = {}
    # 用 "val 名字" 切段，段内找 .blockEntity("id") / validBlocks / renderer
    starts = [m.start() for m in OLD_BE_SPLIT.finditer(text)] + [len(text)]
    for i in range(len(starts) - 1):
        seg = text[starts[i]:starts[i + 1]]
        bm = BE_DECL.search(seg)
        if not bm:
            continue
        be_id = bm.group(1)
        # 泛型调用（BearingRenderer<PropellerBearingBlockEntity>(、FrequencySlotRenderer<T>(）也要认
        cls = re.search(r'\b(\w+Renderer)\s*(?:<[^>]*>)?\s*\(', seg)
        blocks = []
        for vm in VALID.finditer(seg):
            for name in re.findall(r'ClockworkBlocks\.([A-Z0-9_]+)', vm.group(1)):
                if name in field_to_id:
                    blocks.append(field_to_id[name])
        be_map.setdefault(be_id, {
            "blocks": blocks,
            "renderer": cls.group(1) if cls else None,
            "where": f"ClockworkBlockEntities.kt:{text[:starts[i]].count(chr(10)) + 1}",
        })
    return field_to_id, be_map


def be_by_block(be_map):
    out = {}
    for be_id, info in be_map.items():
        for b in info["blocks"]:
            out.setdefault(b, []).append((be_id, info))
    return out


# ---------------------------------------------------------------------------
# RenderShape
# ---------------------------------------------------------------------------

def render_shapes(src_root, ext):
    """{类名: 'ENTITYBLOCK_ANIMATED'|'INVISIBLE'|'MODEL默认'}。"""
    out = {}
    for path in glob.glob(os.path.join(src_root, "**", "*Block." + ext), recursive=True):
        text = read(path)
        name = os.path.basename(path)[: -len("." + ext)]
        if "ENTITYBLOCK_ANIMATED" in text:
            out[name] = "ENTITYBLOCK_ANIMATED"
        elif "RenderShape.INVISIBLE" in text:
            out[name] = "INVISIBLE"
        elif "getRenderShape" in text:
            out[name] = "覆盖但非动画"
    return out


# ---------------------------------------------------------------------------
# Partial 模型类
# ---------------------------------------------------------------------------

FIELD = re.compile(r'public\s+static\s+final\s+PartialModel\s+(\w+)\s*=\s*(.+?);', re.S)
HELPER = re.compile(
    r'PartialModel\s+(\w+)\s*\(\s*(?:final\s+)?String\s+\w+\s*\)\s*\{\s*return\s+PartialModel\.of\(\s*'
    r'ResourceLocation\.fromNamespaceAndPath\(\s*ClockworkMod\.MOD_ID\s*,\s*"([^"]*)"\s*\+\s*\w+', re.S)


def parse_partials():
    """返回 [{'class','file','init','fields':[(name, model_path)]}]。"""
    out = []
    for path in glob.glob(os.path.join(JAVA, "**", "*Partial*.java"), recursive=True):
        text = read(path)
        if "PartialModel" not in text:
            continue
        cls = os.path.basename(path)[:-5]
        helpers = {name: prefix for name, prefix in HELPER.findall(text)}
        fields = []
        for name, expr in FIELD.findall(text):
            loc = None
            m = re.search(r'\b(\w+)\("([^"]+)"\)', expr)
            if m and m.group(1) in helpers:
                loc = helpers[m.group(1)] + m.group(2)
            else:
                m = re.search(r'"(block|item)/([^"]+)"', expr)
                if m:
                    loc = m.group(0).strip('"')
            fields.append((name, loc))
        out.append({
            "class": cls,
            "file": rel(path),
            "init": bool(re.search(r'void\s+init\s*\(', text)),
            "fields": fields,
        })
    return out


# ---------------------------------------------------------------------------
# 快照 + 检查
# ---------------------------------------------------------------------------

class Snap:
    pass


def build():
    s = Snap()
    ids = cw_ids.collect(PROJ)
    s.blocks = sorted(ids["blocks"])
    s.items = sorted(ids["items"])
    s.field_to_id, s.id_to_class, s.new_be = parse_java_registrations()
    s.old_field_to_id, s.old_be = parse_old()
    s.old_by_block = be_by_block(s.old_be)
    s.new_by_block = be_by_block(s.new_be)
    s.shapes_old = render_shapes(os.path.join(OLD, "common", "src", "main", "kotlin"), "kt")
    s.shapes_new = render_shapes(JAVA, "java")
    s.partials = parse_partials()
    s.setup = read(CLIENT_SETUP)
    s.java_all = "\n".join(read(p) for p in glob.glob(os.path.join(JAVA, "**", "*.java"), recursive=True))
    s.java_files = {p: read(p) for p in glob.glob(os.path.join(JAVA, "**", "*Renderer.java"), recursive=True)}
    s.A = Assets(PROJ)
    s.blockstates = {os.path.basename(p)[:-5] for p in glob.glob(os.path.join(ASSETS, "blockstates", "*.json"))}
    s.rc6_hints = []
    return s


def check_rc1(s):
    problems, rows = [], []
    for b in s.blocks:
        if b in EXCLUDED:
            continue
        old = s.old_by_block.get(b, [])
        new = s.new_by_block.get(b, [])
        old_r = next((i["renderer"] for _, i in old if i["renderer"]), None)
        new_r = next((i["renderer"] for _, i in new if i["renderer"]), None)
        rows.append((b, old_r, new_r))
        if old_r and not new_r:
            if b in KNOWN_EQUIVALENT:
                continue
            if b in DEFERRED:
                continue
            problems.append(f"[RC1] {b}: 旧版 {old_r} 有 BER，工程未登记（方块实体 "
                            f"{new[0][0] if new else '无'}）")
    s.rc1_rows = rows
    return problems


def check_rc2(s):
    problems = []
    for p in s.partials:
        if not p["init"]:
            problems.append(f"[RC2] {p['class']}: 缺幂等 init()（{p['file']}）")
        elif f"{p['class']}.init();" not in s.setup:
            problems.append(f"[RC2] {p['class']}: 未在 CWClientSetup#onClientSetup 调用 init() → 烘焙后创建=missingno")
        for name, loc in p["fields"]:
            if not loc:
                problems.append(f"[RC2] {p['class']}.{name}: 解析不出模型路径（部分代码写法未识别）")
                continue
            fp = os.path.join(ASSETS, "models", loc + ".json")
            if not os.path.isfile(fp):
                problems.append(f"[RC2] {p['class']}.{name}: 模型文件缺失 assets/vs_clockwork/models/{loc}.json")
    return problems


def check_rc3(s):
    """按方块 id 对照 RenderShape：类名映射来自 id→类（新版 .block(id, X::new)，旧版 val XBlock）。"""
    problems = []
    old_blocks_kt = read(os.path.join(OLD, "common", "src", "main", "kotlin", "org", "valkyrienskies",
                                      "clockwork", "ClockworkBlocks.kt"))
    old_id_class = {}
    for m in OLD_BLOCK_DECL.finditer(old_blocks_kt):
        tail = old_blocks_kt[m.end(): m.end() + 600]
        cm = re.search(r'\b(\w+Block)\s*\(', tail)
        if cm:
            q = re.match(r'\.block\(\s*"([a-z0-9_]+)"', old_blocks_kt[m.end() - len(".block("):], re.S)
            if q:
                old_id_class.setdefault(q.group(1), cm.group(1))
    for b, cls in sorted(s.id_to_class.items()):
        if b in EXCLUDED or not cls:
            continue
        new_shape = s.shapes_new.get(cls)
        old_cls = old_id_class.get(b)
        old_shape = s.shapes_old.get(old_cls) if old_cls else None
        if old_shape == "ENTITYBLOCK_ANIMATED" and new_shape != "ENTITYBLOCK_ANIMATED" and b not in KNOWN_EQUIVALENT:
            problems.append(f"[RC3] {b}: 旧版 {old_cls} 设了 ENTITYBLOCK_ANIMATED，工程 {cls} 没有"
                            f"（会静态一份 + BER 一份叠着）")
    return problems


def chain_problems(A, model_id):
    data, err = A.model(model_id)
    if err:
        return [err]
    tex, bad, elements, errs = A.face_keys(model_id)
    out = list(errs)
    for k, why in bad:
        out.append(f"贴图键 {k}: {why}")
    return out


def check_rc4(s):
    problems = []
    for iid in s.items + s.blocks:
        if not os.path.isfile(os.path.join(ASSETS, "models", "item", iid + ".json")):
            problems.append(f"[RC4] 物品模型缺失 models/item/{iid}.json")
            continue
        for why in chain_problems(s.A, "vs_clockwork:item/" + iid):
            problems.append(f"[RC4] item/{iid}: {why}")
    return problems


OLD_ITEM_RENDERER = re.compile(r'customRenderedItem\s*\{\s*Supplier\s*\{\s*(\w+)\s*\(')
OLD_ITEM_DECL = re.compile(r'REGISTRATE\.item(?:<[^>]*>)?\(\s*"([a-z0-9_]+)"')


def check_rc5(s):
    """旧版每个用自定义渲染器的物品 → 工程里必须有等价的客户端注册（名字可不同，但要写进 EXPECTED_ITEM_RENDERERS）。"""
    problems = []
    items_kt = os.path.join(OLD, "common", "src", "main", "kotlin", "org", "valkyrienskies",
                            "clockwork", "ClockworkItems.kt")
    text = read(items_kt)
    starts = [(m.start(), m.group(1)) for m in OLD_ITEM_DECL.finditer(text)] + [(len(text), None)]
    used = {}
    for i in range(len(starts) - 1):
        seg = text[starts[i][0]: starts[i + 1][0]]
        rm = OLD_ITEM_RENDERER.search(seg)
        if rm:
            used[starts[i][1]] = rm.group(1)
    for iid, renderer in sorted(used.items()):
        if iid not in s.items:
            continue
        if iid in EXPECTED_ITEM_RENDERERS:
            continue
        if renderer in s.java_all:
            continue
        problems.append(f"[RC5] 物品 {iid}: 旧版用 {renderer}，工程既无同名类也未在 EXPECTED_ITEM_RENDERERS 登记")
    return problems


# 旧版自定义物品渲染器 → 工程做法的显式对照（每条都要有理由）
EXPECTED_ITEM_RENDERERS = {
    "wanderlite_cube": "旧版 WanderliteCubeItemRenderer 用自研 render type 画多层立方体；移植改用静态立方体模型"
                       "（models/item/wanderlite_cube.json 顶部 credit 已注明）",
    "wanderlite_matrix": "同上（静态多一层外壳的立方体模型）",
    "propeller_blade": "旧版 BladeItemRenderer 按 NBT BladeLength 拼三段；本工程 blade 长度尚未写入 NBT，静态模型见 "
                       "models/item/propeller_blade.json",
    "wide_propeller_blade": "同上（宽叶版）",
    "aeronaut_goggles": "旧版 AeronautGogglesRenderer 画护目镜三层；移植改用静态 item 模型",
}


def _model_refs(assets_dir):
    """{模型 id: {引用它的文件相对路径}}（只看 blockstates / models 里的引用）。"""
    refs = {}
    for path in glob.glob(os.path.join(assets_dir, "**", "*.json"), recursive=True):
        if os.sep + "models" + os.sep not in path and os.sep + "blockstates" + os.sep not in path:
            continue
        relpath = os.path.relpath(path, assets_dir).replace("\\", "/")
        # 1.21 会静默忽略非法资源路径（大写文件名/目录），旧版遗留的 OLD_* 不该算「被引用」
        if any(ch.isupper() for ch in relpath):
            continue
        text = read(path)
        ids = set()
        for m in re.findall(r'"([a-z0-9_]+:[a-z0-9_/]+)"', text):
            ids.add(m.split(":", 1)[1])
        for m in re.findall(r'"((?:block|item)/[a-z0-9_/]+)"', text):
            ids.add(m)
        for mid in ids:
            refs.setdefault(mid, set()).add(relpath)
    return refs


def check_rc6(s):
    """孤立子模型。三种结论分开：
    - 官方（跨目录）有引用、工程没有 → 回归（真问题）
    - 两边都没有、只有工程有该文件 → 死资源（真问题）
    - 官方自己也没跨目录引用     → 上游遗留（只记录，不计入问题）
    「跨目录」很重要：一组 dead 子模型之间互相 parent 不算被使用（如 flap_bearing/*)。
    Java 侧引用以 RC2 解析出的 PartialModel 路径为准（比字符串猜测准）。
    """
    problems, upstream = [], []
    proj_refs = _model_refs(ASSETS)
    off_refs = _model_refs(OFFICIAL)
    java_paths = {loc for p in s.partials for _, loc in p["fields"] if loc}

    def outside(model_dir, refs):
        """引用里是否存在来自「另一组模型」的文件（同目录族的 self-reference 不算）。"""
        for r in refs:
            if r.startswith("blockstates/"):
                return True
            if not r.startswith("models/block/" + model_dir + "/"):
                return True
        return False

    dead = {}
    regress, hints = [], []
    for path in glob.glob(os.path.join(ASSETS, "models", "block", "**", "*.json"), recursive=True):
        mid = rel(path).split("models/", 1)[1][:-5]
        if mid in java_paths:
            continue
        block_dir = mid.split("/")[1] if mid.startswith("block/") else mid.split("/")[0]
        bid = MODEL_DIR_TO_BLOCK.get(block_dir, block_dir)
        if bid in EXCLUDED:
            continue
        if any(tok in mid for tok in ("_old", "/old", "placeholder", "/custom/")):
            continue
        if outside(block_dir, proj_refs.get(mid, ())):
            continue
        if outside(block_dir, off_refs.get(mid, ())):
            regress.append(mid)
        elif os.path.isfile(os.path.join(OFFICIAL, "models", mid + ".json")):
            upstream.append(mid)
        elif block_dir in RC6_ACCEPTED:
            hints.append(mid)
        else:
            dead.setdefault(block_dir, []).append(mid)
    for mid in regress:
        problems.append(f"[RC6] {mid}: 官方有（跨目录）引用、工程没人引用（缺 BER / 缺引用，属回归）")
    for block_dir, mids in sorted(dead.items()):
        sample = ", ".join(m.split("/")[-1] for m in sorted(mids)[:4])
        problems.append(f"[RC6] block/{block_dir}: {len(mids)} 个工程新增模型无人引用"
                        f"（{sample}{'…' if len(mids) > 4 else ''}）")
    for block_dir, mids in sorted(((d, [m for m in hints if m.split("/")[1] == d]) for d in
                                   {m.split("/")[1] for m in hints})):
        problems_hint = (f"[提示] block/{block_dir}: {len(mids)} 个工程新增模型无人引用 —— "
                         f"{RC6_ACCEPTED[block_dir]}")
        s.rc6_hints.append(problems_hint)
    s.rc6_upstream = upstream
    return problems


MODEL_DIR_TO_BLOCK = {
    "compressor": "air_compressor",
    "pump": "pump_duct",
    "valve": "valve_duct",
    "filter": "filter_duct",
    "encased_shaft": "balloon_encased_shaft",
    "wanderlite": "wanderlite_block",
    "nyx": "nyx",
}

# [RC6] 的「提示」白名单：工程新增、确实没人引用、但本轮不删（有理由）。
# 2026-10-17：heat_pipe 的六档模型已全部被 blockstate 引用（HeatPipeBlock.HEAT），
# 10 个 straight/item_straight 热度变体已删除，白名单清空。
RC6_ACCEPTED = {}


KINETIC_CLASS = re.compile(r'class\s+(\w+)\s+extends\s+KineticBlockEntityRenderer<')


def check_rc7(s):
    """kinetic 渲染器里「靠 super.renderSafe 画部件」= 必然不画（或被叠一层）。

    Create 6 的 `KineticBlockEntityRenderer#renderSafe` 第一句是
    `if (VisualizationManager.supportsVisualization(level)) return;`（javap -c 核实，字节码 0-10）。
    本工程不注册任何 Flywheel visual，所以父类那句永远为真 → 依赖父类的部件在大世界里一次都不会被画。
    反过来，若子类没覆写 getRenderedBlockState，父类画的是"方块自己"，
    在 Flywheel 关闭的客户端上就会和区块渲染器叠一层。
    """
    problems = []
    for path, text in sorted(s.java_files.items()):
        m = KINETIC_CLASS.search(text)
        if not m or "super.renderSafe(" not in text:
            continue
        problems.append(f"[RC7] {m.group(1)}: 继承 KineticBlockEntityRenderer 却调用 super.renderSafe —— "
                        "父类在 Flywheel 可视化开启时直接 return（本工程不注册 visual → 该部件永不绘制），"
                        "Flywheel 关闭时又按默认方块状态把方块叠画一遍")
    return problems


CHECKS = [
    ("RC1", "BER 覆盖（旧版 .renderer{} ↔ 工程 .renderer()）", check_rc1),
    ("RC2", "partial 登记 / 提前 init / 模型文件", check_rc2),
    ("RC3", "RenderShape（ENTITYBLOCK_ANIMATED）对照", check_rc3),
    ("RC4", "物品模型与 parent / 贴图链", check_rc4),
    ("RC5", "自定义物品渲染器对照", check_rc5),
    ("RC6", "孤立子模型（无人引用）", check_rc6),
    ("RC7", "kinetic 渲染器依赖 super.renderSafe", check_rc7),
]


def run(s, quiet=False):
    total = 0
    results = {}
    for key, title, fn in CHECKS:
        problems = fn(s)
        results[key] = problems
        if not quiet:
            print(f"\n[{key}] {title}: {len(problems)} 个问题")
            for p in problems[:40]:
                print("   " + p)
            if len(problems) > 40:
                print(f"   …另有 {len(problems) - 40} 条")
        total += len(problems)
    if not quiet:
        for hint in getattr(s, "rc6_hints", []):
            print("\n" + hint)
        print(f"\n合计问题 {total} 个"
              f"（另有提示 {len(getattr(s, 'rc6_hints', []))} 条，不计入问题数）")
    return total, results


# ---------------------------------------------------------------------------
# 文档生成
# ---------------------------------------------------------------------------

DOC_HEADER = """# 全方块 / 全物品渲染覆盖审计（RENDER-AUDIT）

> 起因：实机反馈「空气压缩机模型异常、时钟指针错位、气泵无齿轮、气动活塞模型异常、软管卷轴不显示」，
> 并要求「检查所有方块的渲染」。那五项由另一任务负责；本文件覆盖**其余全部注册方块与物品**。
>
> 判据（每条都能静态复现，脚本 `tools/check_render_coverage.py`）：
> 1. **旧版 BER**：唯一权威是 `Clockwork-1.20.1-main/.../ClockworkBlockEntities.kt` 的 33 条 `.renderer` 调用，
>    按 `.validBlocks(...)` 反查到方块 id（不按类名，因为类名有改名）。
> 2. **partial**：`PartialModel.of()` 只在 `ModelEvent.RegisterAdditional` 时已存在才会被烘焙；
>    烘焙之后才创建就是 missingno（黑紫）。所以每个 `*Partials` 类必须有幂等 `init()` 且被
>    `CWClientSetup#onClientSetup` 调用。
> 3. **RenderShape.ENTITYBLOCK_ANIMATED**：旧版某些方块整块由 BER 画，漏设就是「静态一份 + 动态一份」。
> 4. **物品模型**：`models/item/<id>.json` 必须存在，且 parent / `#贴图键` 链可解析（1.21 对解析失败静默）。
> 5. **孤立子模型**：`models/block/<block>/*.json` 既不被 blockstate/parent 引用、也不被 Java 引用 →
>    该部件没有消费者，通常等于「BER 没写」。

## 0. 统计

{stats}

## 1. 全量矩阵（{n_blocks} 个注册方块）

列说明：**旧版 BER** = 1.20.1 由谁画；**工程 BER** = 1.21.1 现在由谁画；
**Shape** = RenderShape（`ANIM` 即 ENTITYBLOCK_ANIMATED）；**物品模型** = `models/item/<id>.json`
是否存在且 parent/贴图链可解析；结论 ✅ 正常 / ⚠️ 存疑（附理由，含"旧版有 BER 但本工程按设计没有"）/
❌ 缺失（缺 BER / 缺 RenderShape / 孤立子模型 / 物品模型坏）。

{table}

## 2. 物品（{n_items} 个注册物品）

| 物品 | 物品模型 | 旧版自定义渲染器 | 工程做法 | 结论 |
|---|---|---|---|---|
{item_table}

## 3. 检查项输出

{checks}

## 4. 修复记录

{fixes}

## 5. 已判定但本轮未修（在案，不计入问题数）

{deferred}

## 6. 上游遗留模型（官方自己也没引用，仅记录）

{upstream}

## 7. 仍需实机确认 / 无法静态判定

{unknown}
"""


def build_doc(s, results, total):
    def bad_blocks(key):
        out = set()
        for p in results.get(key, []):
            m = re.match(r'\[RC\d\] ([\w/]+):', p)
            if m:
                out.add(m.group(1))
        return out

    rc1 = bad_blocks("RC1")
    rc3 = bad_blocks("RC3")
    rc6 = bad_blocks("RC6")
    rows, counts = [], {"✅": 0, "⚠️": 0, "❌": 0, "—": 0}
    for b in s.blocks:
        old_rs = [i["renderer"] or "—" for _, i in s.old_by_block.get(b, [])]
        new_rs = [i["renderer"] or "—" for _, i in s.new_by_block.get(b, [])]
        old_r = next((r for r in old_rs if r != "—"), "—")
        new_r = next((r for r in new_rs if r != "—"), "—")
        cls = s.id_to_class.get(b) or "—"
        shape = "ANIM" if s.shapes_new.get(cls) == "ENTITYBLOCK_ANIMATED" else "model"
        item_ok = os.path.isfile(os.path.join(ASSETS, "models", "item", b + ".json")) and \
            not chain_problems(s.A, "vs_clockwork:item/" + b)
        if b in EXCLUDED:
            verdict, mark = "—（另一任务）", "—"
        elif b in rc1 or b in rc3 or b in rc6 or not item_ok:
            detail = []
            if b in rc1:
                detail.append("缺 BER")
            if b in rc3:
                detail.append("RenderShape")
            if b in rc6:
                detail.append("孤立子模型")
            if not item_ok:
                detail.append("物品模型")
            verdict, mark = "❌ " + "/".join(detail), "❌"
        elif b in DEFERRED:
            verdict, mark = "⚠️ " + DEFERRED[b].split("。")[0], "⚠️"
        elif old_r != "—" and new_r == "—":
            verdict, mark = "⚠️ " + KNOWN_EQUIVALENT.get(b, "待查"), "⚠️"
        else:
            verdict, mark = "✅", "✅"
        counts[mark] = counts.get(mark, 0) + 1
        rows.append(f"| `{b}` | {cls} | {old_r} | {new_r} | {shape} | "
                    f"{'✅' if item_ok else '❌'} | {verdict} |")
    table = ("| 方块 | 类 | 旧版 BER | 工程 BER | Shape | 物品模型 | 结论 |\n"
             "|---|---|---|---|---|---|---|\n" + "\n".join(rows))
    item_rows = []
    for iid in s.items:
        ok = os.path.isfile(os.path.join(ASSETS, "models", "item", iid + ".json")) and \
            not chain_problems(s.A, "vs_clockwork:item/" + iid)
        why = EXPECTED_ITEM_RENDERERS.get(iid, "")
        item_rows.append(f"| `{iid}` | {'✅' if ok else '❌'} | {why.split('：')[0] if why else '—'} | "
                         f"{why or '烘焙模型'} | {'✅' if ok else '❌'} |")
    item_table = "\n".join(item_rows)
    upstream = getattr(s, "rc6_upstream", [])
    stats = (f"- 注册方块 **{len(s.blocks)}** 个（其中 **{len(EXCLUDED)}** 个由另一任务负责："
             f"{', '.join(sorted(EXCLUDED))}）。\n"
             f"- 本轮审计范围：**{len(s.blocks) - len(EXCLUDED)}** 个方块 + **{len(s.items)}** 个物品。\n"
             f"- 结论分布：✅ **{counts.get('✅', 0)}** / ⚠️ **{counts.get('⚠️', 0)}** / "
             f"❌ **{counts.get('❌', 0)}** / —（另一任务）**{counts.get('—', 0)}**。\n"
             f"- 检查项合计问题 **{total}** 个（分项见 §3）。\n"
             f"- 上游遗留（官方自己也没引用，仅记录，不计入问题）**{len(upstream)}** 个模型。")
    checks = "\n".join(
        f"### [{k}] {t}：{len(results[k])} 个\n" +
        ("\n".join("- " + p for p in results[k]) if results[k] else "- 无")
        for k, t, _ in CHECKS)
    return DOC_HEADER.format(
        stats=stats, table=table, n_blocks=len(s.blocks),
        item_table=item_table, n_items=len(s.items),
        checks=checks,
        fixes=FIXES,
        unknown=UNKNOWN,
        deferred="\n".join(f"- `{k}`：{v}" for k, v in DEFERRED.items()) or "- 无",
        upstream=", ".join(f"`{u}`" for u in upstream[:20]) + (" …" if len(upstream) > 20 else "")
        if upstream else "无",
    )


FIXES = """本轮（2026-10-17）按「全方块/全物品穷举」修掉 12 项，全部有静态证据（旧版类名 + 行号）：

| # | 方块 / 项 | 原因（证据） | 修法 |
|---|---|---|---|
| F1 | `sterling_engine` | 旧版 `ClockworkBlockEntities.kt:665` 有 `SterlingEngineRenderer`（飞轮 = Create `MECHANICAL_PUMP_COG`），工程没登记 → 无齿轮 | 新增 `engine/client/SterlingEngineRenderer`（`getRotatedModel` 与旧版逐字一致）+ 登记 `.renderer()` |
| F2 | `alt_meter` | `:314` `AltMeterRenderer` 缺失；`block/alt_meter/redstone` 只可能由 BER 画 | 新增 `altmeter/client/AltMeterRenderer` + `AltMeterPartials`（提前 init），颜色公式照抄 |
| F3 | `andesite_flap_bearing` / `smart_flap_bearing` | `:269` / `:294` `FlapBearingRenderer` 缺失；顶盖 `block/flap_bearing/top` 与半根轴无人画 | 新增 `flap/client/FlapBearingRenderer` + `FlapBearingPartials`，两个方块实体共用 |
| F4 | `duct` | `:462` `DuctRenderer` 缺失；`duct/connection`、`duct/leak`、`duct/rim` 三个接口件无人画（blockstate 只有管身） | 新增 `duct/client/DuctRenderer` + `DuctPartials`，逐面按 `DuctConnectionType` 画 |
| F5 | `valve_duct` | `:523` `ValveDuctRenderer` 缺失；`valve_duct/pointer` 无人画 → 看不出开度 | 新增 `valve/client/ValveDuctRenderer` + `ValveDuctPartials`，指针按 `openness()` 转 0→-90° |
| F6 | `gas_nozzle` | `:605` `GasNozzleRenderer` 缺失；`gas_nozzle/dial`、`/axis` 无人画 | 新增 `nozzle/client/GasNozzleRenderer` + `GasNozzlePartials`；本移植喷嘴非动力方块，两件按姿态静态绘制（见 §5） |
| F7 | `delivery_cannon` | `:784` `DeliveryCannonRenderer` 缺失；blockstate 只引用 3 元素底座，炮座/转台/炮管/天线（共 11 元素）无人画 → 世界里只是一座空底座 | 新增 `cannon/client/DeliveryCannonRenderer` + `DeliveryCannonPartials`，按静止姿态绘制并复刻 blockstate 的 y 旋转 |
| F8 | `lodefocus` | `:333` `LodefocusRenderer` 缺失；装进去的指南针无处显示 | 新增 `sensor/rotation/client/LodefocusRenderer`；给 `LodefocusBlockEntity` 加只读 `getCompass()` |
| F9 | `sugar_rocket` | 旧版 `SugarRocketBlock.kt:40` 返回 `ENTITYBLOCK_ANIMATED`，工程漏设；而 `SugarRocketRenderer` 会整块重画 → 静态一份 + BER 一份叠着，燃烧染色只染一份 | `SugarRocketBlock` 补 `getRenderShape() = ENTITYBLOCK_ANIMATED` |
| F10 | `reactionwheel` | 依赖 `super.renderSafe` 画轴：父类在 Flywheel 可视化开启时直接 return（本工程无 visual）→ 轴永不绘制；且未覆写 `getRenderedBlockState`，Flywheel 关闭时会把轮子叠画一遍 | 改显式 `renderRotatingBuffer`，并补 `getRenderedBlockState() = shaft(axis)`（旧版原本就是这么写的） |
| F11 | `brass_propeller_bearing` / `juryrigged_propeller_bearing`、`phys_bearing`、`redstone_resistor` | 同上：`super.renderSafe` 在本工程等于"不画"，其中前两者是输入轴/整体，后两者会把方块叠一层 | 统一改成显式 `renderRotatingBuffer` / 去掉无用的父类调用（类注释写明原因） |
| F12 | `CWClientSetup` | 新增 6 个 partial 类必须提前登记，否则烘焙后创建 = missingno 黑紫 | 追加 `AltMeterPartials/FlapBearingPartials/DuctPartials/ValveDuctPartials/GasNozzlePartials/DeliveryCannonPartials.init()`，并在各 partial 类头写明"为什么必须提前 init" |

### 2026-10-17 收口轮（F13–F16）

| # | 方块 / 项 | 原因（证据） | 修法 |
|---|---|---|---|
| F13 | `solver` 光束 | 旧版 `SolverRenderer.kt:69` 用自研 `ClockworkRenderTypes.BEAM`（`POSITION_COLOR_TEX_LIGHTMAP` + 自注册 shader）+ `util/beam/BeamRenderer` 画 64 格滚动贴图；本移植一条都没搬，实机完全看不到光束 | 新增 `solver/client/SolverRenderer`：改用原版 `RenderType.entityTranslucentEmissive(beam.png)`（javap 核实 `NO_CULL + COLOR_WRITE + emissive`），4 条 45° 交叉带子 + 亮芯；**不用** `RenderType.lightning()`（javap 核实其绑定 `WEATHER_TARGET`）。服务端新增 `beamDistance` 随 `sendData()` 同步（旧版每帧客户端 `clip`），宽度/颜色进 `CWServer.solverBeamWidth/solverBeamColor` |
| F14 | `delivery_chute` 频率槽 | 旧版 `DeliveryChuteBlockEntities` 登记 `FrequencySlotRenderer<DeliveryChuteBlockEntity>`，槽位来自 `FrequencySlotBehaviour.kt`；本移植既无 behaviour 也无渲染器，频率只是隐藏字段 | 新增 `frequency_slot/FrequencySlotBehaviour`（NBT 键沿用 `Frequency`、剪贴板 `DeliveryFrequencies`、`setFrequency` 只存 1 个）+ `FrequencySlotRenderer`（`ValueBoxRenderer` 画在滑道顶部开口中心 y=15/16）。交互：潜行右键 / 直击槽位设频率，空手潜行清空；`hasFrequency` 与加农炮匹配逻辑不变 |
| F15 | `command_seat` 轴半块 | 旧版 `SequencedSeatRenderer.kt:33` 画 1 根朝下 + 4 根水平 `AllPartialModels.SHAFT_HALF`；审计曾误记"轴已做进 block 模型"，实际 `models/block/command_seat/block.json` 15 个 element 全是座体（坐标 6..10 的轴截面一个都没有） | 新增 `sequenced_seat/client/CommandSeatRenderer`：朝下输入轴沿用旧版 `renderRotatingBuffer`（随输入转速转），4 根水平输出轴静态绘制（旧版输出轴旋转代码本就注释掉）；`CWKineticsIndex` 补 `.renderer()`。`buttonone/buttontwo/joystick` 三个子模型保持不用（旧版 blockstate/渲染器同样没引用，属上游遗留） |
| F16 | `heat_pipe` 六档热度 | 200 个 `*_warm/_hot/_very_hot/_molten/_super_hot` 模型无人引用；blockstate 只按六面连接选常温模型。模型还是按 `[0,0,6,6]` 硬编码 UV 生成的，而 `heatpipe_connection` 内容区在 (0,6)-(16,16)、`heatpipe_rim` 在 0..7×0..16，直接引用会采到透明区域 | `HeatPipeBlock.Heat`（cool/warm/hot/very_hot/molten/super_hot，阈值 373.15/500/800/1300/2000 K，对齐加热器三段温度）+ `HEAT` 属性；`HeatPipeBlockEntity` 每 20 tick 检查、按 `worldPosition` 哈希错相位、**跨档才** `setBlockAndUpdate`。`tools/gen_heat_pipe_blockstate.py` 从现有条件幂等展开 38×6=228 条 multipart；`tools/gen_heat_pipe_models.py` 改为按贴图内容区 + 统一 `UV_SCALE=0.97` 取景，删掉 10 个无消费者的 straight/item_straight 热度变体。`[13]` 提示 27→2（仅剩 valve_duct 旧提示） |
| — | `goo_block` | 旧版 `GooBlockEntityRenderer.renderSafe` 只有 `super`（空实现） | 本轮确认**不做**，保持 `KNOWN_EQUIVALENT` |"""

UNKNOWN = """1. **襟翼轴承顶盖的角度来源**：旧版用 `BearingBlockEntity#getInterpolatedAngle`（伺服摆角）。
   本移植的 `AbstractBearingBlockEntity` 每刻推进 `targetAngleDegrees` 但<b>不</b> `sendData()`，
   客户端拿不到伺服角，因此改用输入轴角（`getAngleForBe`）。**需实机确认**：顶盖"随输入轴转"
   是否可接受，若要还原成"随襟翼摆"，需要在 BE 里每刻同步目标角（非渲染改动，本轮未做）。
2. **气嘴表盘/叶轮轴**：本移植的 `gas_nozzle` 不是动力方块（类注释：没有输入轴），
   没有转速或开度可驱动指针，因此按静态绘制（指针固定 180°）。**需实机确认**观感；
   若后续把"喷气量/气压"接到表盘，只需把 `GasNozzleRenderer.NEEDLE_DEGREES` 换成动态值。
3. **交付加农炮**：只恢复静止姿态（BE 明确不做转头动画，也没有 `xRot/yRot` 状态）。
   炮身完整了，但**不会瞄准**。若要做动画，须先把旧版 `xRot/yRot/clientShotProgress` 搬回 BE。
4. **`solver` 光束是"近似还原"**：没有照搬旧版自研 shader，改用原版
   `entityTranslucentEmissive(beam.png)` + 4 条 45° 交叉带子；颜色/宽度在
   `CWServer.solver(solverBeamColor / solverBeamWidth)`。**需实机确认**：贴图滚动方向/亮度是否可接受；
   旧版的命中点迸射（`renderImpactBurst`）没有画，若需要可在 `SolverRenderer` 末尾按
   `start + facing × distance` 再画一组带子。性能上每帧不建对象，但 4 条 shell + 4 条 core
   共 8 个 quad、64 格长度，多个解算器同屏时建议看一眼 FPS。
5. **`delivery_chute` 频率槽**：槽位在滑道顶部开口中心 y=15/16（旧版位置在顶板下方，实机不可见），
   物品用 `ValueBoxRenderer` 的 FIXED 姿态平躺。**需实机确认**：物品是否浮得太高/穿模；
   若位置不合适，只改 `FrequencySlotBehaviour.ChuteSlot.LOCAL_OFFSET` 一个常量。
   交互：潜行右键或直接点中槽位设置频率；空手潜行右键清空；方块物品仍走放置。
6. **`command_seat` 的 5 根轴**：朝下输入轴沿用旧版 `renderRotatingBuffer`（随输入转速转，
   `getSpeed` 由 Create 同步），四根水平输出轴静态绘制（旧版输出轴旋转代码本就注释掉）。
   **需实机确认**：轴是否与相邻 Create 轴对齐（`hasShaftTowards` 除 UP 外全 true）；
   `buttonone/buttontwo/joystick` 仍是上游遗留、未引用。
7. **`heat_pipe` 六档外观**：阈值是纯观感常量（373.15/500/800/1300/2000 K），与加热器的
   500/800/1300 K 对齐；每 20 tick 检查一次、跨档才写 blockstate。**需实机确认**：
   温度在阈值附近时外观是否稳定（不应每 tick 闪）、20 tick 延迟是否可接受；
   若觉得换档太慢/太快，改 `HeatPipeBlockEntity.HEAT_REFRESH_TICKS` 一个常量即可。
8. **`duct/custom/*` 四个边件**（单向/过滤/智能）同样无人引用：`DuctConnectionType` 只有
   4 个值，边类型的玩法入口未移植（该类注释已说明）。
9. **无法静态判定**：Flywheel 的 `supportsVisualization` 在目标客户端是否为真（取决于后端设置）、
   BER 实际可见性/朝向是否与截图一致 —— 本轮只做到"代码路径正确 + 资源齐全"，最终观感需实机截图确认。
10. 本轮未改：`air_compressor`、`clock`、`pump_duct`、`extendon`、`hose_port`、`duct_tank`
    （另一任务范围内）；`content/logistics/gas/tank/**` 与 Ponder 集成同样未动。"""


# ---------------------------------------------------------------------------
# 自检：注入故障，确认每项都能报出来
# ---------------------------------------------------------------------------

def selftest():
    ok = True
    base = run(build(), quiet=True)[1]

    def expect(name, key, mutate):
        nonlocal ok
        s2 = build()
        mutate(s2)
        res = run(s2, quiet=True)[1]
        hit = len(res[key]) > len(base[key])
        print(f"  {'PASS' if hit else 'FAIL'}  注入「{name}」→ [{key}] "
              f"{len(base[key])} → {len(res[key])} 个问题")
        ok = ok and hit

    def drop_renderer(t):
        # 选一个「旧版确实有 BER、且不在 KNOWN_EQUIVALENT / DEFERRED 里」的方块做注入，
        # 否则删掉的是本来就不该有 BER 的登记，检查项当然不会报（第一版自检就踩了这个坑）
        for info in t.new_be.values():
            if info["renderer"] == "SterlingEngineRenderer":
                info["renderer"] = None

    def drop_init(t):
        t.setup = t.setup.replace("SlickerPartials.init();", "")

    def flip_shape(t):
        t.shapes_new = dict(t.shapes_new)
        t.shapes_new["GyroBlock"] = "MODEL"

    def break_item(t):
        t.items = list(t.items) + ["definitely_not_an_item"]

    def orphan_partial(t):
        t.partials = list(t.partials) + [{
            "class": "GhostPartials", "file": "x", "init": True,
            "fields": [("GHOST", "block/ghost/ghost")]}]

    def dead_model(t):
        # 真的落一个"工程新增、无人引用"的模型文件，跑完删掉（验证 RC6 能看见死资源）
        path = os.path.join(ASSETS, "models", "block", "ghost_probe", "ghost.json")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            f.write('{"textures": {"particle": "vs_clockwork:block/ghost"}}')
        t._ghost_model = path

    def super_render(t):
        t.java_files = dict(t.java_files)
        t.java_files[os.path.join(JAVA, "GhostRenderer.java")] = (
            "public class GhostRenderer extends KineticBlockEntityRenderer<X> {\n"
            "  protected void renderSafe(X be, float p) { super.renderSafe(be, p); }\n}")

    def cleanup():
        path = os.path.join(ASSETS, "models", "block", "ghost_probe", "ghost.json")
        if os.path.isfile(path):
            os.remove(path)
        try:
            os.rmdir(os.path.dirname(path))
        except OSError:
            pass

    try:
        expect("删掉 sterling_engine 的 BER 登记", "RC1", drop_renderer)
        expect("删掉 SlickerPartials.init() 调用", "RC2", drop_init)
        expect("把 GyroBlock 的 ENTITYBLOCK_ANIMATED 改回默认", "RC3", flip_shape)
        expect("虚构一个缺物品模型的 item", "RC4", break_item)
        expect("虚构一个指向不存在模型的 partial", "RC2", orphan_partial)
        expect("落一个无人引用的新增模型（RC6）", "RC6", dead_model)
        expect("虚构一个依赖父类 renderSafe 的 kinetic 渲染器（RC7）", "RC7", super_render)
    finally:
        cleanup()
    print(f"\n自检{'通过' if ok else '失败'}")
    return 0 if ok else 1


def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    ap = argparse.ArgumentParser()
    ap.add_argument("--write", action="store_true", help="重新生成 clockwork_unoffical/docs/RENDER-AUDIT.md")
    ap.add_argument("--selftest", action="store_true", help="注入故障验证每项检查")
    args = ap.parse_args()
    if args.selftest:
        return selftest()
    s = build()
    total, results = run(s)
    if args.write:
        out = os.path.join(PROJ, "docs", "RENDER-AUDIT.md")
        os.makedirs(os.path.dirname(out), exist_ok=True)
        with open(out, "w", encoding="utf-8") as f:
            f.write(build_doc(s, results, total))
        print(f"\n已写入 {rel(out)}")
    return 0 if total == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
