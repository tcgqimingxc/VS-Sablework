#!/usr/bin/env python3
"""从 jar 里抽取指定 class 的常量池 UTF8 字符串（无需 javap，用于确认日志文案）。

用法: python tools/dump_class_strings.py <jar> <class 路径前缀> [grep 关键字]
"""
import re
import sys
import zipfile


def utf8_strings(data):
    """极简 class 常量池解析：只取 CONSTANT_Utf8 项。"""
    idx = 8
    count = int.from_bytes(data[idx:idx + 2], "big")
    idx += 2
    out = []
    i = 1
    while i < count:
        tag = data[idx]
        idx += 1
        if tag == 1:                      # Utf8
            ln = int.from_bytes(data[idx:idx + 2], "big")
            idx += 2
            out.append(data[idx:idx + ln].decode("utf-8", "replace"))
            idx += ln
        elif tag in (7, 8, 16, 19, 20):   # Class/String/MethodType/Module/Package
            idx += 2
        elif tag in (15,):                # MethodHandle
            idx += 3
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            idx += 4
        elif tag in (5, 6):               # Long/Double 占两个槽
            idx += 8
            i += 1
        else:
            raise ValueError(f"未知常量池 tag {tag} @ {idx}")
        i += 1
    return out


def main():
    jar, prefix = sys.argv[1], sys.argv[2]
    pats = [re.compile(p, re.I) for p in sys.argv[3:]] or [re.compile(".")]
    z = zipfile.ZipFile(jar)
    names = [n for n in z.namelist() if n.startswith(prefix) and n.endswith(".class")]
    print(f"# {len(names)} 个 class 匹配 {prefix}")
    for n in sorted(names):
        try:
            strs = utf8_strings(z.read(n))
        except Exception as exc:  # noqa: BLE001
            print(f"  !! {n}: {exc}")
            continue
        hits = [s for s in strs if any(p.search(s) for p in pats)]
        if hits:
            print(f"\n=== {n}")
            for s in hits:
                print("   ", s)


if __name__ == "__main__":
    main()
