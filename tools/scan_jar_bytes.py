#!/usr/bin/env python3
"""在 jar 的所有 class 里做原始字节扫描（比解析常量池更快更稳）。

用法: python tools/scan_jar_bytes.py <jar> <关键字> [关键字...]
"""
import sys
import zipfile


def main():
    jar = sys.argv[1]
    needles = [n.encode() for n in sys.argv[2:]]
    z = zipfile.ZipFile(jar)
    hits = {n: [] for n in needles}
    for name in z.namelist():
        if not name.endswith(".class"):
            continue
        data = z.read(name)
        for n in needles:
            if n in data:
                hits[n].append(name)
    for n, files in hits.items():
        print(f"{n.decode()}: {len(files)} 个 class")
        for f in files[:12]:
            print("    " + f)


if __name__ == "__main__":
    main()
