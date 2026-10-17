#!/usr/bin/env python3
"""官方解包资源树 vs 工程资源树 的逐文件差分盘点。

官方树: ``assets/vs_clockwork/**``（从 Clockwork 官方发布包解出的原始资源）
工程树: ``clockwork_unoffical/src/main/resources/assets/vs_clockwork/**``

输出三类清单，并把完整明细写成 JSON，供 docs/OFFICIAL-ASSETS.md 引用：

  A. 工程缺失、官方有      -> 候选"照收"
  B. 两边都有且字节一致    -> 无需动
  C. 两边都有但字节不同    -> 需要逐条判断（谁是 1.21.1 修正版）
  D. 工程有、官方没有      -> 1.21.1 新增 / 我们补的

用法::

    python tools/diff_official_assets.py                # 打印摘要 + 明细摘要
    python tools/diff_official_assets.py --json out.json
    python tools/diff_official_assets.py --list A        # 只看某一类
"""

import argparse
import hashlib
import json
import os
import sys

TOOLS = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(TOOLS)
OFFICIAL = os.path.join(ROOT, "assets", "vs_clockwork")
PROJECT = os.path.join(ROOT, "clockwork_unoffical", "src", "main", "resources",
                       "assets", "vs_clockwork")

DEFAULT_JSON = os.path.join(ROOT, "_probe_tmp", "official_diff.json")


def sha(path):
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 16), b""):
            h.update(chunk)
    return h.hexdigest()


def walk(base):
    out = {}
    if not os.path.isdir(base):
        return out
    for dirpath, _dirnames, filenames in os.walk(base):
        for name in filenames:
            full = os.path.join(dirpath, name)
            out[os.path.relpath(full, base).replace("\\", "/")] = full
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--json", default=DEFAULT_JSON)
    ap.add_argument("--list", choices=["A", "B", "C", "D"], default=None)
    ap.add_argument("--group", action="store_true",
                    help="按顶层目录/扩展名汇总")
    args = ap.parse_args()

    off = walk(OFFICIAL)
    proj = walk(PROJECT)
    if not off:
        raise SystemExit(f"找不到官方解包树：{OFFICIAL}")
    if not proj:
        raise SystemExit(f"找不到工程资源树：{PROJECT}")

    missing, same, diff, extra = [], [], [], []
    for rel in sorted(set(off) | set(proj)):
        o, p = off.get(rel), proj.get(rel)
        if o and not p:
            missing.append({"path": rel, "official_bytes": os.path.getsize(o)})
        elif p and not o:
            extra.append({"path": rel, "project_bytes": os.path.getsize(p)})
        else:
            ho, hp = sha(o), sha(p)
            if ho == hp:
                same.append({"path": rel, "bytes": os.path.getsize(o)})
            else:
                diff.append({"path": rel,
                             "official_bytes": os.path.getsize(o),
                             "project_bytes": os.path.getsize(p),
                             "official_sha": ho[:16], "project_sha": hp[:16]})

    report = {"officialRoot": OFFICIAL, "projectRoot": PROJECT,
              "counts": {"official": len(off), "project": len(proj),
                         "A_missing_in_project": len(missing),
                         "B_identical": len(same),
                         "C_different": len(diff),
                         "D_project_only": len(extra)},
              "A_missing_in_project": missing,
              "C_different": diff,
              "D_project_only": extra,
              "B_identical": same}

    os.makedirs(os.path.dirname(args.json), exist_ok=True)
    with open(args.json, "w", encoding="utf-8") as fh:
        json.dump(report, fh, ensure_ascii=False, indent=1)

    c = report["counts"]
    print(f"官方 {c['official']} 个文件 / 工程 {c['project']} 个文件")
    print(f"  A 工程缺失、官方有 : {c['A_missing_in_project']:4d}")
    print(f"  B 两边一致         : {c['B_identical']:4d}")
    print(f"  C 两边不同         : {c['C_different']:4d}")
    print(f"  D 工程独有         : {c['D_project_only']:4d}")
    print(f"明细已写入 {os.path.relpath(args.json, ROOT)}")

    def bucket(rows, key):
        agg = {}
        for r in rows:
            top = r["path"].split("/")[0]
            agg[top] = agg.get(top, 0) + 1
        return "  ".join(f"{k}={v}" for k, v in sorted(agg.items()))

    if args.group or not args.list:
        print("\n按顶层目录：")
        for label, rows in (("A", missing), ("B", same), ("C", diff), ("D", extra)):
            print(f"  [{label}] {bucket(rows, 'path')}")

    if args.list:
        rows = {"A": missing, "B": same, "C": diff, "D": extra}[args.list]
        for r in rows:
            print("  " + json.dumps(r, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
