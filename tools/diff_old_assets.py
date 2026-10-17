#!/usr/bin/env python3
"""对比旧版 Clockwork-1.20.1-main 的资源树与移植后的资源树。

任务前提是"旧版资源已全量拷贝"，本脚本用内容哈希验证这件事是否真的成立，
并列出：漏拷的文件 / 内容不同的文件 / 新增的文件。
"""
import hashlib
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OLD_ROOTS = [
    os.path.join(ROOT, "Clockwork-1.20.1-main", m, "src", "main", "resources", "assets", "vs_clockwork")
    for m in ("common", "fabric", "forge")
]
NEW_ROOTS = [
    os.path.join(ROOT, "clockwork_unoffical", "src", "main", "resources", "assets", "vs_clockwork"),
    os.path.join(ROOT, "clockwork_unoffical", "src", "generated", "resources", "assets", "vs_clockwork"),
]


def snap(roots, skip_bbmodel=True):
    out = {}
    for root in roots:
        if not os.path.isdir(root):
            print(f"  (跳过不存在的 {root})")
            continue
        for dp, _, fs in os.walk(root):
            for f in fs:
                if skip_bbmodel and f.endswith(".bbmodel"):
                    continue
                fp = os.path.join(dp, f)
                rel = os.path.relpath(fp, root).replace("\\", "/")
                out.setdefault(rel, []).append(fp)
    return out


def h(fp):
    return hashlib.sha256(open(fp, "rb").read()).hexdigest()


def main():
    old = snap(OLD_ROOTS)
    new = snap(NEW_ROOTS)
    print(f"旧版文件: {len(old)}    移植后文件: {len(new)}")

    missing = sorted(set(old) - set(new))
    extra = sorted(set(new) - set(old))
    print(f"\n-- 旧版有、移植后没有 ({len(missing)}):")
    for m in missing:
        print(f"    {m}   ({os.path.relpath(old[m][0], ROOT)})")

    print(f"\n-- 移植后新增 ({len(extra)}):")
    for m in extra[:80]:
        print(f"    {m}")
    if len(extra) > 80:
        print(f"    ... 还有 {len(extra) - 80} 条")

    print(f"\n-- 同名但内容不同:")
    diff = 0
    for rel in sorted(set(old) & set(new)):
        ho = h(old[rel][0])
        hn = h(new[rel][0])
        if ho != hn:
            diff += 1
            print(f"    {rel}  old={ho[:10]} new={hn[:10]}  ({os.path.getsize(old[rel][0])} -> {os.path.getsize(new[rel][0])} 字节)")
    print(f"    合计 {diff} 个")
    return 0


if __name__ == "__main__":
    sys.exit(main())
