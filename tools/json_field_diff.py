#!/usr/bin/env python3
"""对 C 类冲突做逐字段 JSON 差分，输出"官方 -> 工程"的实际改动。

用途：判断每条冲突到底是 1.21.1 的修正（保留工程版）还是纯粹的格式/丢内容（可考虑官方版）。

用法::

    python tools/diff_official_assets.py          # 先产出 _probe_tmp/official_diff.json
    python tools/json_field_diff.py               # 打印全部逐字段差异
    python tools/json_field_diff.py --path blockstates/coal_burner.json
"""

import argparse
import json
import os
import sys

TOOLS = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(TOOLS)
OFFICIAL = os.path.join(ROOT, "assets", "vs_clockwork")
PROJECT = os.path.join(ROOT, "clockwork_unoffical", "src", "main", "resources",
                       "assets", "vs_clockwork")
DIFF_JSON = os.path.join(ROOT, "_probe_tmp", "official_diff.json")


def load(path):
    with open(path, encoding="utf-8-sig") as fh:
        return json.load(fh)


def walk_diff(a, b, path=""):
    """产出 (路径, 官方值, 工程值) 的差异列表。"""
    out = []
    if type(a) is not type(b):
        return [(path or "$", a, b)]
    if isinstance(a, dict):
        for k in sorted(set(a) | set(b)):
            sub = f"{path}.{k}" if path else k
            if k not in a:
                out.append((sub, "<缺>", b[k]))
            elif k not in b:
                out.append((sub, a[k], "<缺>"))
            else:
                out.extend(walk_diff(a[k], b[k], sub))
        return out
    if isinstance(a, list):
        if len(a) != len(b):
            out.append((f"{path}.length", len(a), len(b)))
        for i in range(min(len(a), len(b))):
            out.extend(walk_diff(a[i], b[i], f"{path}[{i}]"))
        return out
    if a != b:
        out.append((path or "$", a, b))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--path", default=None, help="只分析指定相对路径")
    ap.add_argument("--max", type=int, default=40, help="每条最多打印多少行差异")
    args = ap.parse_args()

    report = json.load(open(DIFF_JSON, encoding="utf-8"))
    rows = report["C_different"]
    if args.path:
        rows = [r for r in rows if r["path"] == args.path]
        if not rows:
            raise SystemExit(f"C 类里没有 {args.path}")

    for row in rows:
        rel = row["path"]
        if not rel.endswith(".json"):
            print(f"=== {rel} —— 非 JSON，跳过逐字段差分"
                  f"（官方 {row['official_bytes']}B / 工程 {row['project_bytes']}B）")
            continue
        a = load(os.path.join(OFFICIAL, rel))
        b = load(os.path.join(PROJECT, rel))
        diffs = walk_diff(a, b)
        print(f"=== {rel} —— {len(diffs)} 处差异"
              f"（官方 {row['official_bytes']}B / 工程 {row['project_bytes']}B）")
        for p, va, vb in diffs[:args.max]:
            sa = json.dumps(va, ensure_ascii=False)
            sb = json.dumps(vb, ensure_ascii=False)
            if len(sa) > 150:
                sa = sa[:150] + "..."
            if len(sb) > 150:
                sb = sb[:150] + "..."
            print(f"    {p}")
            print(f"        官方: {sa}")
            print(f"        工程: {sb}")
        if len(diffs) > args.max:
            print(f"    ...另有 {len(diffs) - args.max} 处")
        print()
    return 0


if __name__ == "__main__":
    sys.exit(main())
