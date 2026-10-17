#!/usr/bin/env python3
"""对比两个 jar 内 assets/vs_clockwork 的差异（用于回溯"上一轮修了什么"）。

用法: python tools/diff_two_jars.py <jarA> <jarB> [子目录前缀]
"""
import hashlib
import sys
import zipfile


def snap(jar, prefix="assets/vs_clockwork/", sub=""):
    z = zipfile.ZipFile(jar)
    out = {}
    for n in z.namelist():
        if n.endswith("/") or not n.startswith(prefix + sub):
            continue
        out[n] = hashlib.sha256(z.read(n)).hexdigest()
    return out


def main():
    a, b = sys.argv[1], sys.argv[2]
    sub = sys.argv[3] if len(sys.argv) > 3 else ""
    A, B = snap(a, sub=sub), snap(b, sub=sub)
    print(f"A={a}\n   条目 {len(A)}")
    print(f"B={b}\n   条目 {len(B)}")
    only_a = sorted(set(A) - set(B))
    only_b = sorted(set(B) - set(A))
    diff = sorted(k for k in set(A) & set(B) if A[k] != B[k])
    print(f"\n-- 只在 A ({len(only_a)}):")
    for k in only_a[:60]:
        print("   ", k)
    print(f"\n-- 只在 B ({len(only_b)}):")
    for k in only_b[:60]:
        print("   ", k)
    print(f"\n-- 内容不同 ({len(diff)}):")
    for k in diff[:60]:
        print("   ", k)


if __name__ == "__main__":
    main()
