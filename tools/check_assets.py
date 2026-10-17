#!/usr/bin/env python3
"""Clockwork 移植：资源一致性守门（blockstate → 模型 → 贴图 → 打包 全链路）。

检查项（方括号编号被 docs/PORT-STATUS.md 引用，含义保持稳定）：
  [1] 代码注册的方块是否有 blockstates/<id>.json
  [2] 代码注册的方块 / 物品是否有 models/item/<id>.json
      —— 注：**必须**用 tools/cw_ids.py 提取 id。早期脚本用一条正则把 `.item("x")` 也当方块，
         还把 `.blockEntity("x")` 当方块（机器方块与它的方块实体同名！），结果 40 多个机器方块
         既漏报又假报。现在 68 个方块 + 25 个物品全部对齐。
  [3] 是否有语言条目（提示性，不影响渲染）
  [4] blockstate → 模型（含 parent 链）→ 贴图 是否齐全
  [5] 有 blockstate 但代码没注册的方块（提示性）
  [6] OBJ 模型：loader 是否为 neoforge:obj、.obj/.mtl/mtllib/usemtl/map_Kd 是否自洽
  [7] **贴图键解析**：模型（含父模型继承的 elements）里每个 `#key` 能否沿 textures 链解析。
      1.21.1 对解析不出来的键**完全静默**（黑紫直接上屏、日志一行都没有），所以这一项是
      唯一能提前发现「一片黑紫但日志干净」的静态手段。
  [8] **具体贴图引用存在性**：跨命名空间查（本模组磁盘 → libs/*.jar → 实例 mods/*.jar →
      原版 client-*-extra.jar）。只在 vs_clockwork 里找会漏掉 create:/minecraft: 的错。
  [9] png 合法性 + .png.mcmeta 动画自洽（帧尺寸按 AnimationMetadataSection.calculateFrameSize 语义）
  [10] 非法资源路径（1.21 的资源包校验会静默忽略这些文件）
  [11] 产物 jar ↔ 源码资源树一致性 + jar 结构自检（--jar 时；对应「jar 被边跑边覆盖」那次事故）
  [12] **blockstate 属性覆盖**：代码里注册的方块状态（含父类注册的属性）与 blockstates/<id>.json
       的 variants/multipart 条件逐状态比对。为什么必须单列一项：`[4]` 只查「blockstate 里引用的
       模型文件在不在」，查不出「某个状态压根没有变体覆盖」—— 那种状态在 1.21.1 会静默换成
       missing model（黑紫），日志只有一行 WARN（`BlockStateModelLoader`：
       "missing model for variant: '<状态>'"）。属性真值取自 `tools/blockstate_properties.json`
       （由 `tools/probe_blockstates.py` 实例化方块类生成，见该脚本里的理由），
       快照与编译产物对不上会报「快照过期」。
  [13] **同一 blockstate 各变体的 UV 缩放（texel 密度）一致性**（提示性，见 tools/cw_assets_lib.py 里
       check_uv_scaling 一节的注释）。uv 是 0..16 归一化坐标，**`texture_size` 字段 Java 版根本不读**
       （1.21.1 class 常量池里没有该字符串，而 cullface/gui_light 都在），所以
       密度 = uv跨度/16 × 贴图像素 / 面尺寸。变体之间密度差 >1.5 倍 ⇒ 成型/拆分切换模型时贴图跳变
       （2026-10-16 duct_tank：单块 1 px/单位、多层 4 px/单位）。动画贴图按 mcmeta 的帧尺寸算，
       发光/动画/细节贴图因此不会误报；余下命中需人工判断，故只作提示、不计入合计。

用法：
  python tools/check_assets.py                 # [1]..[13]
  python tools/check_assets.py --jar           # 追加 [11]（默认用 build/libs 里的产物）
  python tools/check_assets.py --quiet         # 只打印计数与非零项
"""
import argparse
import itertools
import json
import os
import sys

TOOLS = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(TOOLS)
sys.path.insert(0, TOOLS)

import cw_assets_lib as L        # noqa: E402
import cw_ids as IDS             # noqa: E402

NS = L.NS
DEFAULT_JAR = "vs_clockwork-0.6.0-1.21.1.jar"
PROP_SNAPSHOT = os.path.join(TOOLS, "blockstate_properties.json")
# 硬失败项（合计问题只统计这些）；[3]/[5] 是提示
HARD = ("1", "2", "4", "6", "7", "8", "9", "10", "11", "12")

# `BlockModelRotation.by(x, y)` 只认这 4 个角度；别的值会让**整个** blockstate 解析失败
# （MultiVariant 是在解析期就反序列化的），于是该方块所有状态一起变黑紫。
VALID_ROT = (0, 90, 180, 270)


def section(num, title, problems, quiet=False, limit=40):
    print(f"\n[{num}] {title} —— 问题 {len(problems)} 个")
    if not problems:
        return
    for x in problems[:limit]:
        print("    " + x)
    if len(problems) > limit:
        print(f"    ... 还有 {len(problems) - limit} 条")


def check_ids(project):
    """[1][2][3][5]：代码注册 id ↔ 资源文件 / 语言条目。"""
    ids = IDS.collect(project)
    assets = os.path.join(project, "src", "main", "resources", "assets", NS)
    lang_path = os.path.join(assets, "lang", "en_us.json")
    lang = json.load(open(lang_path, encoding="utf-8")) if os.path.isfile(lang_path) else {}

    def has(*parts):
        return os.path.isfile(os.path.join(assets, *parts))

    miss_bs, miss_model, miss_lang = [], [], []
    for b in sorted(ids["blocks"]):
        if not has("blockstates", b + ".json"):
            miss_bs.append(f"block:{b} 缺 blockstates/{b}.json")
        if not has("models", "item", b + ".json"):
            miss_model.append(f"block:{b} 缺 models/item/{b}.json（物品形态会是 missing）")
        if f"block.{NS}.{b}" not in lang:
            miss_lang.append(f"block:{b} 缺 lang block.{NS}.{b}")
    for i in sorted(ids["items"]):
        if not has("models", "item", i + ".json"):
            miss_model.append(f"item:{i} 缺 models/item/{i}.json")
        if f"item.{NS}.{i}" not in lang and f"block.{NS}.{i}" not in lang:
            miss_lang.append(f"item:{i} 缺 lang item.{NS}.{i}")
    have_bs = {n[:-5] for n in os.listdir(os.path.join(assets, "blockstates"))
               if n.endswith(".json")}
    unused = sorted(have_bs - ids["blocks"])
    return ids, miss_bs, miss_model, miss_lang, unused


# ===================== [12] blockstate 属性覆盖 =====================
#
# 语义完全照 1.21.1 `BlockStateModelLoader` + `Selector`/`KeyValueCondition`（已核对反编译源码）：
#
#   * variants：每个 key 是逗号分隔的 `属性=值`，只写部分属性就只约束那部分，空 key（""）匹配全部。
#       - key 里的属性/值不认识 -> 该 key 抛异常被丢弃（WARN "Exception loading blockstate
#         definition: ... for variant: '...'"），它本该覆盖的状态退化成 missing model。
#       - **两个 key 同时命中一个状态** -> 也会被丢弃并换成 missing model（WARN
#         "Overlapping definition with: ..."）。注意判断式是 `prev.model != multipart`，
#         而 variants 定义里 multipart 恒为 null，所以任何重叠都算。
#       - 某状态没有任何 key 命中 -> missing model（WARN "missing model for variant: '<状态>'"）。
#   * multipart：所有状态先绑上 multipart 模型，**不会** missing model；但 when 里的属性/值不认识
#     会在求值时抛异常（WARN "Exception evaluating model definition"），该 selector 失效；
#     一个 selector 都没命中的状态渲染成「空」（看不见方块）。
#   * 解析期就致命的写法（`MultiVariant`/`Variant`/`Selector` 反序列化失败）会让**整个**
#     blockstate 文件作废、该方块所有状态一起黑紫，所以必须单独查：缺 model、x/y 不是
#     0/90/180/270、weight<1、variants 值为空数组、when 是空对象、缺 apply 等。

def _json_scalar(value):
    """blockstate JSON 里的值是字符串/布尔/数字，MC 一律走 JsonPrimitive#getAsString()。

    `"lit": true` 在游戏里是 "true"（小写！），第一版分析脚本用 Python 的 str(True) 得到 "True"，
    冤枉了 coal_burner 一把，这条注释就是那次教训。
    """
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, float) and value.is_integer():
        return str(int(value))
    return str(value)


def _parse_variant_key(key):
    """'facing=north,lit=true' -> ([(name, value)], err)。"""
    conds, err = [], None
    for part in key.split(","):
        name, sep, value = part.partition("=")
        if name == "":
            continue                      # 空 key 或多余的逗号：MC 当成"无约束"
        if not sep:
            err = err or f"'{name}' 后面没有 '=值'（MC 会抛 Unknown blockstate property）"
            continue
        conds.append((name, value))
    return conds, err


def _key_conditions(key, props):
    """-> (conds, err)：err 非空表示这个变体键会被 MC 丢弃。"""
    conds, err = _parse_variant_key(key)
    if err:
        return conds, err
    for name, value in conds:
        if name not in props:
            return conds, f"未知属性 '{name}'"
        if value not in props[name]:
            return conds, (f"属性 '{name}' 没有取值 '{value}'"
                           f"（合法值 {sorted(props[name])}）")
    return conds, None


def _matches(conds, state):
    for name, value in conds:
        if state[name] != value:
            return False
    return True


def _compile_when(when, props, where):
    """把 multipart 的 when 编译成条件树，顺便校验属性/取值。

    返回 (node, dropped, fatal)：
      * node：('true',) / ('kv', name, values, negated) / ('and'|'or', [node])，用于逐状态匹配；
      * dropped：求值才会抛的错（属性/取值不认识）—— MC 只丢这一个 selector（WARN
        "Exception evaluating model definition"）；
      * fatal：反序列化期就抛的错（when 是空对象、AND/OR 不是非空数组）—— 整个文件作废。
    注意 **不能**把 OR 拍平成"全部条件都要满足"：duct 就是靠 OR 里套 `true|leak` 这种写法
    （第一版 [12] 拍平后把 duct 的 8192 个状态全判成没命中，等于白报一次）。
    """
    dropped, fatal = [], []
    if not isinstance(when, dict):
        return None, dropped, [f"{where}: \"when\" 必须是对象"]
    if not when:
        return None, dropped, [f"{where}: \"when\" 是空对象"
                               f"（Selector 抛 \"No elements found in selector\" -> 整个文件作废）"]
    if len(when) == 1 and ("OR" in when or "AND" in when):
        kind = "OR" if "OR" in when else "AND"
        subs = when[kind]
        if not isinstance(subs, list) or not subs:
            return None, dropped, [f"{where}: \"{kind}\" 必须是非空数组"]
        nodes = []
        for sub in subs:
            node, d, f = _compile_when(sub, props, where)
            dropped += d
            fatal += f
            if node is not None:
                nodes.append(node)
        if not nodes:
            return None, dropped, fatal
        return (kind.lower(), nodes), dropped, fatal
    nodes = []
    for name, value in when.items():
        raw = _json_scalar(value)
        negated = raw.startswith("!")
        if negated:
            raw = raw[1:]
        values = raw.split("|")
        if name not in props:
            dropped.append(f"{where}.when: 未知属性 '{name}'")
            continue
        for one in values:
            if one not in props[name]:
                dropped.append(f"{where}.when: 属性 '{name}' 没有取值 '{one}'"
                               f"（合法值 {sorted(props[name])}）")
        nodes.append(("kv", name, values, negated))
    if not nodes:
        return None, dropped, fatal
    return ("and", nodes), dropped, fatal


def _when_matches(node, state):
    kind = node[0]
    if kind == "kv":
        _k, name, values, negated = node
        hit = state[name] in values
        return (not hit) if negated else hit
    if kind == "and":
        return all(_when_matches(n, state) for n in node[1])
    if kind == "or":
        return any(_when_matches(n, state) for n in node[1])
    return True


def _check_multipart(parts, props, states, problems):
    """multipart：查 when 的条件是否合法、有没有状态一个 selector 都命不上、有没有重复 selector。"""
    selectors = []
    seen = {}
    for i, part in enumerate(parts):
        where = f"multipart[{i}]"
        if not isinstance(part, dict):
            problems.append(f"{where}: 必须是对象")
            continue
        if "apply" not in part:
            problems.append(f"{where}: 缺 apply（MC 抛异常 -> 整个文件作废）")
            continue
        models = tuple(_check_apply_entry(part["apply"], where + ".apply", problems))
        if "when" not in part:
            selectors.append((where, ("true",)))          # 无条件 = 恒真
            continue
        node, dropped, fatal = _compile_when(part["when"], props, where)
        problems += fatal
        for e in dropped:
            problems.append(f"{e} -> 该 selector 被丢掉"
                            f"（WARN \"Exception evaluating model definition\"）")
        if node is None:
            continue
        key = json.dumps(part["when"], sort_keys=True)
        if key in seen and models == seen[key][1]:
            # 同一个 when + 同一批模型 = 同一份几何被画两遍（wing/flap 那种"同 when 不同模型"
            # 的叠加写法是合法的，所以这里必须连模型一起比，否则会误报）
            problems.append(f"{where}: when 与 {seen[key][0]} 相同且模型也相同"
                            f"（{', '.join(m or '(缺 model)' for m in models)}）"
                            f"-> 同一份几何画两遍 / 多半是 copy-paste 漏改一个方向")
        else:
            seen.setdefault(key, (where, models))
        selectors.append((where, node))
    empty = [s for s in states if not any(_when_matches(node, s) for _w, node in selectors)]
    if empty and selectors:
        problems.append(f"{len(empty)}/{len(states)} 个状态没有任何 selector 命中 -> 方块看不见"
                        f"，例: {_fmt_states(empty)}")


def _check_apply_entry(entry, where, problems):
    """校验 variants 的变体值 / multipart 的 apply 条目。返回模型 id 列表（缺 model 记为 None）。"""
    if isinstance(entry, list):
        if not entry:
            problems.append(f"{where}: 变体数组为空（MC 抛 Empty variant array -> 整个文件作废）")
            return []
        models = []
        for i, one in enumerate(entry):
            models += _check_apply_entry(one, f"{where}[{i}]", problems)
        return models
    if not isinstance(entry, dict):
        problems.append(f"{where}: 变体必须是对象（当前 {type(entry).__name__}）")
        return [None]
    model = entry.get("model")
    if not isinstance(model, str) or not model:
        problems.append(f"{where}: 缺 model（MC 抛 JsonSyntaxException -> 整个文件作废）")
        model = None
    for axis in ("x", "y"):
        if axis in entry:
            value = entry[axis]
            if isinstance(value, bool) or not isinstance(value, int):
                problems.append(f"{where}: \"{axis}\" 必须是整数（当前 {value!r}）")
            elif value % 360 not in VALID_ROT:
                problems.append(f"{where}: \"{axis}\"={value} 不是 0/90/180/270 的整数倍"
                                f"（BlockModelRotation.by 返回 null -> 整个文件作废）")
    if "uvlock" in entry and not isinstance(entry["uvlock"], bool):
        problems.append(f"{where}: \"uvlock\" 必须是布尔（当前 {entry['uvlock']!r}）")
    if "weight" in entry:
        value = entry["weight"]
        if isinstance(value, bool) or not isinstance(value, int) or value < 1:
            problems.append(f"{where}: \"weight\" 必须是 >=1 的整数（当前 {value!r}）")
    return [model]


def _fmt_states(states, limit=4):
    out = []
    for s in states[:limit]:
        out.append("[" + ",".join(f"{k}={v}" for k, v in s.items()) + "]")
    if len(states) > limit:
        out.append(f"...另有 {len(states) - limit} 个")
    return ", ".join(out)


def _check_variants(variants, props, names, states, problems):
    entries = []
    for key, value in variants.items():
        conds, err = _key_conditions(key, props)
        _check_apply_entry(value, f"变体 {key!r}", problems)
        if err:
            problems.append(f"变体键 {key!r} 会被丢弃（{err}）")
            entries.append((key, conds, err))
        else:
            entries.append((key, conds, None))
    live = [(k, c) for k, c, e in entries if e is None]
    missing, overlap = [], []
    for state in states:
        hits = [k for k, conds in live if _matches(conds, state)]
        if not hits:
            missing.append(state)
        elif len(hits) > 1:
            overlap.append((state, hits))
    if missing:
        problems.append(f"{len(missing)}/{len(states)} 个状态没有变体覆盖 -> missing model（黑紫）"
                        f"，例: {_fmt_states(missing)}")
    if overlap:
        problems.append(f"{len(overlap)} 个状态被多个变体同时命中 -> 也是 missing model（黑紫）"
                        f"，例: {_fmt_states([o[0] for o in overlap])}"
                        f"（命中 {overlap[0][1]}）")
    dead = [k for k, conds in live if not any(_matches(conds, s) for s in states)]
    if dead:
        problems.append(f"变体键 {dead} 一个状态都命不中（取值写错？提示项）")


def check_blockstate_coverage(project, quiet=False):
    """[12]：属性覆盖 / 条件合法性。"""
    bad = []
    if not os.path.isfile(PROP_SNAPSHOT):
        return [f"缺少属性快照 {os.path.relpath(PROP_SNAPSHOT, ROOT)}"
                f"（生成：python tools/probe_blockstates.py）"]

    snapshot = json.load(open(PROP_SNAPSHOT, encoding="utf-8"))
    try:
        sys.path.insert(0, TOOLS)
        import probe_blockstates as PB
        now = PB.class_fingerprint(project)
        if now and snapshot.get("classFingerprint") and now != snapshot["classFingerprint"]:
            bad.append(f"属性快照已过期（编译产物指纹 {snapshot['classFingerprint']} -> {now}）："
                       f"方块属性可能已改动，请重跑 python tools/probe_blockstates.py")
    except Exception as exc:                                     # noqa: BLE001
        bad.append(f"无法核对属性快照指纹（{exc}）")

    data = snapshot.get("blocks") or {}
    blocks = IDS.collect(project)["blocks"]
    ids, *_ = check_ids(project)
    bs_dir = os.path.join(project, "src", "main", "resources", "assets", NS, "blockstates")
    checked = 0
    for bid in sorted(ids["blocks"]):
        bs = os.path.join(bs_dir, bid + ".json")
        if not os.path.isfile(bs):
            continue                                     # [1] 已经报过
        info = data.get(bid)
        if info is None:
            bad.append(f"{bid}: 属性快照里没有这个方块（新注册的？重跑 tools/probe_blockstates.py）")
            continue
        if "error" in info:
            bad.append(f"{bid}: 属性快照里探针失败 {info['error'][:120]}")
            continue
        props = {p["name"]: list(p["values"]) for p in info["properties"]}
        names = [p["name"] for p in info["properties"]]
        states = [dict(zip(names, combo))
                  for combo in itertools.product(*[props[n] for n in names])]
        checked += 1
        problems = []
        try:
            doc = json.load(open(bs, encoding="utf-8"))
        except Exception as exc:                                 # noqa: BLE001
            bad.append(f"{bid}: blockstates JSON 解析失败 {exc}")
            continue
        if not isinstance(doc, dict):
            bad.append(f"{bid}: blockstates 顶层必须是对象")
            continue
        has_variants, has_multipart = "variants" in doc, "multipart" in doc
        if not has_variants and not has_multipart:
            bad.append(f"{bid}: 既没有 variants 也没有 multipart"
                       f"（MC 抛 \"Neither 'variants' nor 'multipart' found\" -> 全状态黑紫）")
            continue
        if has_variants:
            if not isinstance(doc["variants"], dict):
                bad.append(f"{bid}: \"variants\" 必须是对象")
            else:
                _check_variants(doc["variants"], props, names, states, problems)
        if has_multipart:
            if not isinstance(doc["multipart"], list) or not doc["multipart"]:
                bad.append(f"{bid}: \"multipart\" 必须是非空数组")
            else:
                _check_multipart(doc["multipart"], props, states, problems)
        bad += [f"{bid}（{len(states)} 状态）: {p}" for p in problems]

    total = snapshot.get("blockCount", 0)
    if not quiet:
        print(f"    [12] 已核对 {checked} 个方块（快照 {total} 个，"
              f"属性来自 tools/blockstate_properties.json）")
    for b in sorted(set(blocks) - set(data)):
        bad.append(f"{b}: 已注册但属性快照里没有（重跑 tools/probe_blockstates.py）")
    return bad


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--project", default="clockwork_unoffical")
    ap.add_argument("--jar", action="store_true", help="追加 [11] 产物 jar 一致性检查")
    ap.add_argument("--jar-path", default=None)
    ap.add_argument("--quiet", action="store_true", help="只打印计数")
    args = ap.parse_args()

    project = os.path.join(ROOT, args.project)
    A = L.Assets(project)
    if not os.path.isdir(A.assets):
        print(f"找不到资源目录: {A.assets}")
        return 2

    ids, miss_bs, miss_model, miss_lang, unused = check_ids(project)
    print(f"代码里注册：方块 {len(ids['blocks'])} 个，物品 {len(ids['items'])} 个"
          f"（方块实体 {len(ids['bes'])}，实体 {len(ids['entities'])}）")

    results = {}
    results["1"] = miss_bs
    results["2"] = miss_model
    results["3"] = miss_lang
    results["4"] = L.check_blockstates(A)
    results["5"] = [f"有 blockstates 但未注册（旧版遗留，会被忽略）: {u}" for u in unused]
    results["6"] = L.check_obj(A)
    results["7"] = L.check_model_keys(A)
    results["8"] = L.check_texture_refs(A)
    results["9"] = L.check_pngs(A) + L.check_mcmeta(A)
    results["10"] = L.check_invalid_paths(A)
    results["11"] = []
    if args.jar:
        jar = args.jar_path or os.path.join(project, "build", "libs", DEFAULT_JAR)
        results["11"] = L.check_jar_vs_source(A, jar)
    results["12"] = check_blockstate_coverage(project, quiet=args.quiet)
    results["13"] = L.check_uv_scaling(A)

    if args.quiet:
        for num in sorted(results, key=int):
            print(f"[{num}] {len(results[num])}")
    else:
        section("1", "方块缺 blockstate", results["1"])
        section("2", "方块/物品缺 models/item 模型", results["2"])
        section("3", "缺语言条目（提示）", results["3"], limit=20)
        section("4", "blockstate → 模型链 断裂", results["4"])
        section("5", "有资源但代码未注册的方块（提示）", results["5"])
        section("6", "OBJ 模型（loader/obj/mtl/贴图键）问题", results["6"])
        section("7", "贴图键无法解析（会静默渲染成黑紫）", results["7"])
        section("8", "贴图引用在所有 jar/原版资源里都不存在", results["8"])
        section("9", "png 合法性 / mcmeta 动画", results["9"])
        section("10", "非法资源路径（1.21 会静默忽略）", results["10"])
        section("12", "blockstate 属性覆盖 / 条件合法性（覆盖不全 = 黑紫）", results["12"])
        section("13", "变体模型间 UV 缩放不一致（提示：切换模型会跳变）", results["13"])
        if args.jar:
            section("11", "产物 jar ↔ 源码资源树 / jar 结构", results["11"])

    total = sum(len(results[k]) for k in HARD if k in results)
    soft = len(results["3"]) + len(results["5"]) + len(results["13"])
    print(f"\n合计问题: {total} 个（硬失败项 [{'/'.join(HARD)}]；另有提示 {soft} 条）")
    return 1 if total else 0


if __name__ == "__main__":
    sys.exit(main())
