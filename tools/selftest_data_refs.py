#!/usr/bin/env python3
"""tools/check_data_refs.py 的自检：故意造坏数据，必须被报出来；恢复后必须归 0。

为什么需要：守门脚本自己也可能"永远绿"（比如引用索引写错就变成全放过）。
这个脚本逐个注入一类错误，确认对应检查真的会失败，然后恢复原文件并确认回到 0 个问题。

用法：python tools/selftest_data_refs.py
"""
import json
import os
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GATE = os.path.join(ROOT, "tools", "check_data_refs.py")
DATA = os.path.join(ROOT, "clockwork_unoffical", "src", "main", "resources", "data", "vs_clockwork")

CF = os.path.join(DATA, "worldgen", "configured_feature", "wanderlite_ore.json")
PF = os.path.join(DATA, "worldgen", "placed_feature", "wanderlite_ore.json")
BM = os.path.join(DATA, "neoforge", "biome_modifier", "wanderlite_overworld.json")
JB = os.path.join(DATA, "jukebox_song", "wanderlust.json")


def run_gate():
    env = dict(os.environ, PYTHONIOENCODING="utf-8")
    out = subprocess.run([sys.executable, GATE], capture_output=True, text=True,
                         encoding="utf-8", errors="replace", env=env)
    text = (out.stdout or "") + (out.stderr or "")
    total = None
    for line in text.splitlines():
        if line.startswith("合计问题:"):
            total = int(line.split(":", 1)[1].strip().split(" ")[0])
    return total, text


def mutate(path, fn):
    """改一个文件，返回恢复用的原始内容。"""
    original = open(path, encoding="utf-8").read()
    data = json.loads(original)
    fn(data)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
    return original


def restore(path, original):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(original)


CASES = [
    ("configured_feature: 引用不存在的方块（state.Name）", CF,
     lambda d: d["config"]["targets"][0]["state"].__setitem__("Name", "vs_clockwork:no_such_ore")),
    ("configured_feature: 删除必填字段 size", CF,
     lambda d: d["config"].pop("size")),
    ("configured_feature: 未知的特征 type", CF,
     lambda d: d.__setitem__("type", "minecraft:definitely_not_a_feature")),
    ("configured_feature: 不存在的方块标签", CF,
     lambda d: d["config"]["targets"][0]["target"].__setitem__("tag", "minecraft:no_such_tag")),
    ("placed_feature: feature 指向不存在的 configured_feature", PF,
     lambda d: d.__setitem__("feature", "vs_clockwork:no_such_feature")),
    ("placed_feature: 未知的放置修饰符 type", PF,
     lambda d: d["placement"][0].__setitem__("type", "minecraft:no_such_modifier")),
    ("placed_feature: count 修饰符的 count 写成字符串", PF,
     lambda d: d["placement"][2].__setitem__("count", "100")),
    ("biome_modifier: step 写成非法枚举值", BM,
     lambda d: d.__setitem__("step", "underground_ores_typo")),
    ("biome_modifier: biomes 标签不存在", BM,
     lambda d: d.__setitem__("biomes", "#minecraft:no_such_biome_tag")),
    ("biome_modifier: features 指向不存在的 placed_feature", BM,
     lambda d: d.__setitem__("features", "vs_clockwork:no_such_placed_feature")),
    ("biome_modifier: 删除必填字段 step", BM,
     lambda d: d.pop("step")),
    ("jukebox_song: sound_event 未注册", JB,
     lambda d: d.__setitem__("sound_event", "vs_clockwork:no_such_sound")),
    ("jukebox_song: 删除必填字段 comparator_output", JB,
     lambda d: d.pop("comparator_output")),
    ("jukebox_song: comparator_output 超范围", JB,
     lambda d: d.__setitem__("comparator_output", 99)),
]


def main():
    base, text = run_gate()
    if base != 0:
        print(f"自检中止：当前工作区本来就有 {base} 个问题，先修好再自检。")
        print(text)
        return 1
    print(f"基线：合计问题 {base} 个（符合预期）")

    failures = []
    for name, path, fn in CASES:
        original = mutate(path, fn)
        try:
            total, out = run_gate()
        finally:
            restore(path, original)
        if total is None or total <= 0:
            failures.append(name)
            print(f"  [未报出] {name}")
        else:
            print(f"  [已报出 {total:>2} 个] {name}")
        if total is None:
            print(out)

    # 恢复后必须归 0
    after, out = run_gate()
    print(f"恢复后：合计问题 {after} 个")
    if after != 0:
        failures.append("恢复后未归 0")
        print(out)

    print(f"自检用例 {len(CASES)} 个，失败 {len(failures)} 个")
    for f in failures:
        print(f"  FAIL {f}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
