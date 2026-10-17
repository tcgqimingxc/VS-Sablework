#!/usr/bin/env python3
"""逐方块打印：blockstate 引用的基础模型（含 elements 归属）+ 该方块旧版渲染器用到的部件模型。

用来人工核对「方块本体模型是否已经包含部件」——即"形状是否只剩一半"。
"""
import json
import os
import re
import sys

TOOLS = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(TOOLS)
sys.path.insert(0, TOOLS)
import cw_assets_lib as L   # noqa: E402

PROJ = os.path.join(ROOT, "clockwork_unoffical")
A = L.Assets(PROJ)
OLD = os.path.join(ROOT, "Clockwork-1.20.1-main")
OLD_PARTIALS = os.path.join(OLD, "common", "src", "main", "kotlin",
                            "org", "valkyrienskies", "clockwork", "ClockworkPartials.kt")


def blockstate_models(name):
    fp = os.path.join(A.assets, "blockstates", name + ".json")
    if not os.path.isfile(fp):
        return None
    data = json.load(open(fp, encoding="utf-8"))
    out = []
    for v in (data.get("variants") or {}).values():
        for one in (v if isinstance(v, list) else [v]):
            if isinstance(one, dict) and isinstance(one.get("model"), str):
                out.append(one["model"])
    for part in (data.get("multipart") or []):
        ap = part.get("apply")
        for one in (ap if isinstance(ap, list) else [ap]):
            if isinstance(one, dict) and isinstance(one.get("model"), str):
                out.append(one["model"])
    return out


def elements_of(mid):
    owner, els, err = A.chain(mid)
    n = 0 if els is None else len(els)
    return owner, n, el_ids(els)


def el_ids(els):
    names = []
    for el in els or []:
        n = el.get("name")
        if n:
            names.append(n)
    return names


def partials_versions():
    """旧版 ClockworkPartials.kt 里定义的 partial -> 路径。"""
    out = {}
    text = open(OLD_PARTIALS, encoding="utf-8", errors="replace").read()
    for m in re.finditer(r'val\s+(\w+)\s*[:=][^=]*?of\(ClockworkMod\.asResource\("([^"]+)"\)\)', text):
        out[m.group(1)] = m.group(2)
    return out


def main():
    names = sys.argv[1:]
    parts = partials_versions()
    if not names:
        names = sorted(n[:-5] for n in os.listdir(os.path.join(A.assets, "blockstates"))
                       if n.endswith(".json"))
    for name in names:
        ms = blockstate_models(name)
        print(f"### {name}")
        if ms is None:
            print("    (无 blockstate)")
            continue
        seen = []
        for m in ms:
            if m in seen:
                continue
            seen.append(m)
        for m in seen:
            mid = m if ":" in m else f"{L.NS}:{m}"
            owner, n, els = elements_of(mid)
            extra = f"  names={els[:6]}" if els else ""
            print(f"    base {mid}: elements={n} @ {owner}{extra}")
        # 该方块相关的旧版 partial（按名字前缀猜）
        for k, v in sorted(parts.items()):
            if any(t in v for t in (name, name.replace("_duct", "/"), name.replace("_bearing", "_bearing"))):
                pm = f"{L.NS}:{v}"
                owner, n, _ = elements_of(pm)
                mark = "有" if n else "空/缺"
                print(f"    partial {k:26s} {v:42s} elements={n} ({mark})")
        print()


if __name__ == "__main__":
    main()
