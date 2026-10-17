#!/usr/bin/env python3
"""一次性诊断：找出「没有任何入口引用」的模型（孤儿模型）。

一个模型如果既不被 blockstate / models/item 引用（含 parent 链），也不在 Java 源码里
以字符串形式出现，那么它在游戏里永远不会被渲染 —— 如果它是某个部件的模型，
说明"该部件缺失"。这是定位"形状不全"的机械手段。
"""
import json
import os
import sys

TOOLS = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(TOOLS)
sys.path.insert(0, TOOLS)
import cw_assets_lib as L  # noqa: E402

PROJ = os.path.join(ROOT, "clockwork_unoffical")
A = L.Assets(PROJ)


def all_models():
    out = {}
    for rel, fp in L.iter_models(A.assets):
        out[f"{L.NS}:" + rel[len("models/"):-len(".json")]] = fp
    return out


def java_text():
    buf = []
    for dp, _, fs in os.walk(os.path.join(PROJ, "src", "main", "java")):
        for f in fs:
            if f.endswith(".java"):
                buf.append(open(os.path.join(dp, f), encoding="utf-8", errors="replace").read())
    return "\n".join(buf)


def refs_from_entrypoints(models):
    """blockstate / models/item 里出现的模型 + 其 parent 链。"""
    seen = set()

    def walk(mid):
        if mid in seen:
            return
        seen.add(mid)
        data, err = A.model(mid)
        if err or not isinstance(data, dict):
            return
        p = data.get("parent")
        if isinstance(p, str):
            walk(p if ":" in p else f"minecraft:{p}")

    bs_dir = os.path.join(A.assets, "blockstates")
    for name in os.listdir(bs_dir):
        if not name.endswith(".json"):
            continue
        data = json.load(open(os.path.join(bs_dir, name), encoding="utf-8"))
        for v in (data.get("variants") or {}).values():
            for one in (v if isinstance(v, list) else [v]):
                if isinstance(one, dict) and isinstance(one.get("model"), str):
                    m = one["model"]
                    walk(m if ":" in m else f"{L.NS}:{m}")
        for part in (data.get("multipart") or []):
            ap = part.get("apply")
            for one in (ap if isinstance(ap, list) else [ap]):
                if isinstance(one, dict) and isinstance(one.get("model"), str):
                    m = one["model"]
                    walk(m if ":" in m else f"{L.NS}:{m}")
    item_dir = os.path.join(A.assets, "models", "item")
    for name in os.listdir(item_dir):
        if name.endswith(".json"):
            walk(f"{L.NS}:item/{name[:-5]}")
    return seen


def main():
    models = all_models()
    used = refs_from_entrypoints(models)
    jt = java_text()
    orphan_java, orphan_all = [], []
    for mid in sorted(models):
        if mid in used:
            continue
        short = mid[len(L.NS) + 1:]                 # e.g. block/duct/rim
        if short in jt or short.split("/")[-1] in jt:
            orphan_java.append(mid)
        else:
            orphan_all.append(mid)
    print(f"模型总数 {len(models)}；被 blockstate/item 引用（含 parent 链）{len(used)}")
    print(f"\n[A] 未被 blockstate/item 引用，但 Java 源码里出现了路径 -> 由渲染器/partial 使用: {len(orphan_java)}")
    for m in orphan_java:
        print("    " + m)
    print(f"\n[B] 完全没有引用的孤儿模型: {len(orphan_all)}")
    for m in orphan_all:
        print("    " + m)


if __name__ == "__main__":
    main()
