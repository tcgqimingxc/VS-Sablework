#!/usr/bin/env python3
"""把官方树 vs 工程树的 C 类（两边不同）冲突再细分：

  C0 仅换行符/末尾空行差异（\\r\\n vs \\n）        -> 语义相同，保留工程版
  C1 纯文本、归一化后仍不同                       -> 需要逐条判断
  C2 JSON 语义相同（重新序列化后相等）             -> 语义相同，保留工程版
  C3 二进制（png/ogg/obj 等）                      -> 需要逐条判断

用法::

    python tools/diff_official_assets.py --json _probe_tmp/official_diff.json
    python tools/diff_official_assets.py            # 先跑上游脚本产出 JSON
    python tools/classify_official_conflicts.py
"""

import json
import os
import sys

TOOLS = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(TOOLS)
OFFICIAL = os.path.join(ROOT, "assets", "vs_clockwork")
PROJECT = os.path.join(ROOT, "clockwork_unoffical", "src", "main", "resources",
                       "assets", "vs_clockwork")
DIFF_JSON = os.path.join(ROOT, "_probe_tmp", "official_diff.json")

TEXT_EXT = {".json", ".mcmeta", ".txt", ".obj", ".mtl", ".xml", ".properties"}


def norm_newlines(b):
    return b.replace(b"\r\n", b"\n").replace(b"\r", b"\n")


def main():
    if not os.path.isfile(DIFF_JSON):
        raise SystemExit(f"先跑 python tools/diff_official_assets.py 生成 {DIFF_JSON}")
    report = json.load(open(DIFF_JSON, encoding="utf-8"))

    buckets = {"C0_newline": [], "C2_json_equal": [], "C1_text": [], "C3_binary": [],
               "C0_json_format_plus_newline": []}
    for row in report["C_different"]:
        rel = row["path"]
        ob = open(os.path.join(OFFICIAL, rel), "rb").read()
        pb = open(os.path.join(PROJECT, rel), "rb").read()
        ext = os.path.splitext(rel)[1].lower()
        if norm_newlines(ob) == norm_newlines(pb):
            buckets["C0_newline"].append(row)
            continue
        if ext == ".json":
            try:
                jo = json.loads(ob.decode("utf-8-sig"))
                jp = json.loads(pb.decode("utf-8-sig"))
            except Exception:
                buckets["C1_text"].append(row)
                continue
            if jo == jp:
                buckets["C2_json_equal"].append(row)
                continue
            buckets["C1_text"].append(row)
            continue
        if ext in TEXT_EXT:
            buckets["C1_text"].append(row)
        else:
            buckets["C3_binary"].append(row)

    print(f"C 类共 {len(report['C_different'])} 条，细分：")
    for k in ("C0_newline", "C2_json_equal", "C1_text", "C3_binary"):
        print(f"  {k:14s} {len(buckets[k]):3d}")
    print()
    for k in ("C1_text", "C3_binary"):
        if not buckets[k]:
            continue
        print(f"--- {k}（需要逐条判断）---")
        for row in buckets[k]:
            print(f"  {row['path']}")
            print(f"      官方 {row['official_bytes']:>7d}B {row['official_sha']}"
                  f"   工程 {row['project_bytes']:>7d}B {row['project_sha']}")
        print()
    if buckets["C2_json_equal"]:
        print("--- C2 JSON 语义相同（仅格式/换行不同）---")
        for row in buckets["C2_json_equal"]:
            print(f"  {row['path']}")

    with open(os.path.join(ROOT, "_probe_tmp", "official_conflicts.json"), "w",
              encoding="utf-8") as fh:
        json.dump(buckets, fh, ensure_ascii=False, indent=1)
    print("\n细分结果 -> _probe_tmp/official_conflicts.json")
    return 0


if __name__ == "__main__":
    sys.exit(main())
