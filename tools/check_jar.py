#!/usr/bin/env python3
"""校验产物 jar 的 zip 结构完整性（纯 Python，不依赖 JVM）。

更强的 JDK 版校验（用 java.util.zip.ZipFile，也就是崩溃栈里那个读取器）见
tools/verify_jar_java.java：`java tools/verify_jar_java.java <jar...>`。

为什么需要：2026-10-11 20:37 的实机崩溃
`IllegalStateException: Failed to load registries due to above errors` 的
`Caused by: java.util.zip.ZipException: invalid LOC header (bad signature)`
不是数据格式问题，而是 mods 目录里那份 jar 的字节坏了 —— Minecraft 读不出
`data/vs_clockwork/{worldgen,neoforge/biome_modifier,jukebox_song}` 下的 5 个文件，
于是这 5 个注册项全部解析失败，加载注册表时直接崩客户端。
JSON 内容这时候完全合法，所以只查 JSON 的守门脚本永远是绿的，必须单独查 jar。

本脚本逐条比对"本地文件头（LOC）"与"中央目录（CD）"的 压缩方式/压缩后大小/原始大小/CRC32，
再逐个条目真正解压一遍，任何不一致都算问题。

用法：python tools/check_jar.py [jar 路径...]
      不给路径时默认检查 clockwork_unoffical/build/libs/vs_clockwork-0.6.0-1.21.1.jar
"""
import glob
import os
import struct
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT = glob.glob(os.path.join(ROOT, "clockwork_unoffical", "build", "libs", "vs_clockwork-*.jar"))

LOCAL_SIG = b"PK\x03\x04"


def check(jar):
    problems = []
    if not os.path.exists(jar):
        return [f"{jar}: 文件不存在"]
    try:
        zf = zipfile.ZipFile(jar)
    except Exception as exc:  # noqa: BLE001
        return [f"{jar}: 连中央目录都读不出来: {exc}"]

    entries = [zi for zi in zf.infolist() if not zi.is_dir()]
    with open(jar, "rb") as f:
        for zi in entries:
            f.seek(zi.header_offset)
            raw = f.read(30)
            if len(raw) < 30 or raw[:4] != LOCAL_SIG:
                problems.append(f"{zi.filename}: 本地文件头签名不是 PK\\x03\\x04"
                                f"（offset={zi.header_offset}，实际 {raw[:4]!r}）—— invalid LOC header")
                continue
            (_ver, flag, comp, _tm, _dt, crc, csz, usz, nlen, _elen) = struct.unpack_from("<HHHHHIIIHH", raw, 4)
            name = f.read(nlen)
            if name != zi.filename.encode("utf-8"):
                problems.append(f"{zi.filename}: 本地文件头里的文件名与中央目录不一致（{name[:60]!r}）")
            if (comp, csz, usz, crc) != (zi.compress_type, zi.compress_size, zi.file_size, zi.CRC):
                problems.append(f"{zi.filename}: 本地文件头与中央目录元数据不一致 "
                                f"(comp {comp}/{zi.compress_type}, csz {csz}/{zi.compress_size}, "
                                f"usz {usz}/{zi.file_size}, crc {crc:08x}/{zi.CRC:08x})")
            # PyZipFile 的 read() 会做 CRC 校验；配合上面的元数据比对足够定位坏条目
    for zi in entries:
        try:
            data = zf.read(zi)
        except Exception as exc:  # noqa: BLE001
            problems.append(f"{zi.filename}: 解压失败: {exc}")
            continue
        if len(data) != zi.file_size:
            problems.append(f"{zi.filename}: 解压后长度 {len(data)} != 中央目录声明 {zi.file_size}")

    # 关键数据目录是否都在（防止打包漏文件）
    names = set(zf.namelist())
    for required in ("data/vs_clockwork/worldgen/configured_feature/wanderlite_ore.json",
                     "data/vs_clockwork/worldgen/placed_feature/wanderlite_ore.json",
                     "data/vs_clockwork/neoforge/biome_modifier/wanderlite_overworld.json",
                     "data/vs_clockwork/neoforge/biome_modifier/wanderlite_end.json",
                     "data/vs_clockwork/jukebox_song/wanderlust.json",
                     "assets/vs_clockwork/sounds.json"):
        if required not in names:
            problems.append(f"缺少条目 {required}")

    print(f"{os.path.basename(jar)}: {len(entries)} 个条目，zip 结构问题 {len(problems)} 个")
    for p in problems[:40]:
        print(f"  {p}")
    if len(problems) > 40:
        print(f"  ... 还有 {len(problems) - 40} 条")
    return problems


def main():
    jars = sys.argv[1:] or DEFAULT
    if not jars:
        print("没找到 jar；先跑 cd clockwork_unoffical; .\\gradlew.bat --offline build")
        print("合计问题: 1 个")
        return 0
    bad = 0
    for jar in jars:
        bad += len(check(jar))
    print(f"合计问题: {bad} 个")
    return 0


if __name__ == "__main__":
    sys.exit(main())
