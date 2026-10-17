#!/usr/bin/env python3
"""tools/check_assets.py 的自检：故意造出一类坏资源，必须被对应检查报出来；恢复后必须归 0。

为什么需要：守门脚本最危险的失效方式是"永远绿"（例如贴图只在本模组目录里找、
或帧序号语义写错，都会让检查形同虚设）。这个脚本逐个注入错误并核对**是哪一项**报出来的。

[12] 那几个用例是 2026-10-16 补的：blockstate 少写一个变体 / 属性名或取值写错 / 重复 selector
在游戏里都只打一行 WARN（甚至完全静默）然后渲染成黑紫或空白，只能靠静态检查拦住。

用法：python tools/selftest_assets.py
"""
import json
import os
import re
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TOOLS = os.path.join(ROOT, "tools")
GATE = os.path.join(TOOLS, "check_assets.py")
A = os.path.join(ROOT, "clockwork_unoffical", "src", "main", "resources", "assets", "vs_clockwork")


def run_gate(extra=()):
    env = dict(os.environ, PYTHONIOENCODING="utf-8")
    out = subprocess.run([sys.executable, GATE, *extra], capture_output=True, text=True,
                         encoding="utf-8", errors="replace", env=env)
    text = (out.stdout or "") + (out.stderr or "")
    total = None
    counts = {}
    for line in text.splitlines():
        if line.startswith("合计问题:"):
            total = int(line.split(":", 1)[1].strip().split(" ")[0])
        elif line.startswith("[") and "] " in line and "——" in line:
            num = line[1:line.index("]")]
            # 注意标题里也可能出现"问题"二字（如 [6] "...问题 —— 问题 1 个"），
            # 所以必须取**最后一处** "问题 N 个"。
            hits = re.findall(r"问题 (\d+) 个", line)
            if hits:
                counts[num] = int(hits[-1])
    return total, counts, text


def read_json(rel):
    with open(os.path.join(A, rel), encoding="utf-8") as fh:
        return json.load(fh)


def write_json(rel, data):
    with open(os.path.join(A, rel), "w", encoding="utf-8", newline="\n") as fh:
        json.dump(data, fh, indent=2, ensure_ascii=False)


def write_text(rel, text):
    with open(os.path.join(A, rel), "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)


# ---------------- 注入用例 ----------------
# 每项: (说明, 期望报出的检查编号, 注入函数, 还原函数)

def _inject_missing_item_model():
    rel = "models/item/wanderlite_block.json"
    dst = os.path.join(A, rel + ".bak")
    os.replace(os.path.join(A, rel), dst)
    return lambda: os.replace(dst, os.path.join(A, rel))


def _inject_broken_blockstate():
    rel = "blockstates/nyx.json"
    orig = open(os.path.join(A, rel), encoding="utf-8").read()
    write_text(rel, orig.replace('"vs_clockwork:block/nyx/nyx"',
                                 '"vs_clockwork:block/nyx/does_not_exist"'))
    return lambda: write_text(rel, orig)


def _inject_broken_parent():
    rel = "models/item/wanderlite_block.json"
    orig = open(os.path.join(A, rel), encoding="utf-8").read()
    data = json.loads(orig)
    data["parent"] = "vs_clockwork:block/no_such_parent_model"
    write_json(rel, data)
    return lambda: write_text(rel, orig)


def _inject_missing_texture_key():
    rel = "models/item/screwdriver.json"
    orig = open(os.path.join(A, rel), encoding="utf-8").read()
    write_text(rel, orig.replace('"texture": "#0"', '"texture": "#missing"'))
    return lambda: write_text(rel, orig)


def _inject_missing_texture_file():
    rel = "models/item/screwdriver.json"
    orig = open(os.path.join(A, rel), encoding="utf-8").read()
    write_text(rel, orig.replace("vs_clockwork:item/screwdriver",
                                 "vs_clockwork:item/no_such_texture"))
    return lambda: write_text(rel, orig)


def _inject_bad_png():
    rel = "textures/block/nyx.png"
    dst = os.path.join(A, rel + ".bak")
    os.replace(os.path.join(A, rel), dst)
    write_text(rel, "this is not a png")
    return lambda: (os.remove(os.path.join(A, rel)), os.replace(dst, os.path.join(A, rel)))


def _inject_bad_mcmeta_frames():
    rel = "textures/block/impactotron_animated.png.mcmeta"
    orig = open(os.path.join(A, rel), encoding="utf-8").read()
    data = json.loads(orig)
    data["animation"]["frames"] = [0, 1, 99]
    write_json(rel, data)
    return lambda: write_text(rel, orig)


def _inject_forge_obj_loader():
    rel = "models/block/reactionwheel/block.json"
    orig = open(os.path.join(A, rel), encoding="utf-8").read()
    write_text(rel, orig.replace('"neoforge:obj"', '"forge:obj"'))
    return lambda: write_text(rel, orig)


def _inject_bad_mtllib():
    # 必须挑**被模型引用**的 obj：models/block/reactionwheel/old/{wheelbottom,wheeltop,item}.obj
    # 是旧版遗留的孤儿（没有任何模型 json 引用），改它们不会影响游戏，检查项也不该报。
    rel = "models/block/reactionwheel/old/alsoold/wheelbottom.obj"
    orig = open(os.path.join(A, rel), encoding="utf-8").read()
    lines = orig.splitlines(True)
    for i, ln in enumerate(lines):
        if ln.startswith("mtllib"):
            lines[i] = "mtllib missing_material.mtl\n"
            break
    write_text(rel, "".join(lines))
    return lambda: write_text(rel, orig)


def _inject_uv_scale_mismatch():
    """[13] 的回归用例 = 2026-10-16 duct_tank 的真实毛病（同一 blockstate 的各变体用了
    不同 texel 密度 → 成型/拆分切换模型时贴图跳变）。

    2026-10-16 的现场是"block_middle 用整张 64x64（4 px/单位）、block_single 只取一格
    （1 px/单位）"；10-17 起外观已还原为官方模型 + 16x16 裁切贴图（全族 1.0），
    所以这里改成**从另一半下手**：把 block_middle 的 uv 跨度 16 砍成 8（密度 1.0 → 0.5），
    它与同一 blockstate 下 block_top / block_bottom 等对同一张贴图的密度差 2 倍，
    属于同一类毛病，必须照样被 [13] 报出来。"""
    rel = "models/block/duct_tank/block_middle.json"
    fp = os.path.join(A, rel)
    orig = open(fp, encoding="utf-8").read()
    assert "[0, 0, 16, 16]" in orig, "duct_tank/block_middle 的 uv 已经不是 16 了，用例失效"
    write_text(rel, orig.replace("[0, 0, 16, 16]", "[0, 0, 8, 8]"))
    return lambda: write_text(rel, orig)


def _inject_invalid_path():
    rel = "models/item/BAD_NAME.png.json"
    write_text(rel, "{}")
    return lambda: os.remove(os.path.join(A, rel))


def _inject_unpackaged_file():
    rel = "models/item/zz_unpackaged_probe.json"
    write_text(rel, '{"parent": "minecraft:item/generated"}')
    return lambda: os.remove(os.path.join(A, rel))


# ---- [12] blockstate 属性覆盖：这一类最危险，因为游戏里只会打一行 WARN 然后黑紫 ----

def _inject_missing_variant():
    """删掉一个 facing 变体：那一面的方块状态就没模型了（missing model）。"""
    rel = "blockstates/gimbal_bearing.json"
    orig = open(os.path.join(A, rel), encoding="utf-8").read()
    data = json.loads(orig)
    del data["variants"]["facing=down"]
    write_json(rel, data)
    return lambda: write_text(rel, orig)


def _inject_wrong_property_name():
    """变体键里的属性名写错：MC 抛 Unknown blockstate property，该键被丢弃。"""
    rel = "blockstates/clock.json"
    orig = open(os.path.join(A, rel), encoding="utf-8").read()
    write_text(rel, orig.replace('"facing=north"', '"facingg=north"', 1))
    return lambda: write_text(rel, orig)


def _inject_duplicate_selector():
    """multipart 里复制一条 selector（同 when 同模型）：同一份几何画两遍。"""
    rel = "blockstates/coal_burner.json"
    orig = open(os.path.join(A, rel), encoding="utf-8").read()
    data = json.loads(orig)
    data["multipart"].append(json.loads(json.dumps(data["multipart"][0])))
    write_json(rel, data)
    return lambda: write_text(rel, orig)


def _inject_bad_rotation():
    """x 不是 90 的倍数：BlockModelRotation.by 返回 null，整个 blockstate 文件作废。"""
    rel = "blockstates/phys_bearing.json"
    orig = open(os.path.join(A, rel), encoding="utf-8").read()
    data = json.loads(orig)
    data["variants"]["facing=north"]["x"] = 45
    write_json(rel, data)
    return lambda: write_text(rel, orig)


def _inject_bad_multipart_value():
    """multipart 条件里给了属性不存在的取值：该 selector 求值时抛异常被丢掉。"""
    rel = "blockstates/heat_pipe.json"
    orig = open(os.path.join(A, rel), encoding="utf-8").read()
    write_text(rel, orig.replace('"down": "false"', '"down": "nope"', 1))
    return lambda: write_text(rel, orig)


CASES = [
    ("[2] 已注册方块删掉 models/item 模型", "2", _inject_missing_item_model, ()),
    ("[4] blockstate 引用不存在的模型", "4", _inject_broken_blockstate, ()),
    ("[7] 模型 parent 指向不存在的模型", "7", _inject_broken_parent, ()),
    ("[7] 面引用未定义的贴图键 #missing", "7", _inject_missing_texture_key, ()),
    ("[8] 贴图引用指向不存在的 png", "8", _inject_missing_texture_file, ()),
    ("[9] 贴图文件不是合法 PNG", "9", _inject_bad_png, ()),
    ("[9] mcmeta 帧序号超出条带范围", "9", _inject_bad_mcmeta_frames, ()),
    ("[6] OBJ loader 退回 forge:obj", "6", _inject_forge_obj_loader, ()),
    ("[6] OBJ 的 mtllib 指向不存在的 mtl", "6", _inject_bad_mtllib, ()),
    ("[10] 资源路径含大写字母", "10", _inject_invalid_path, ()),
    ("[11] 源码有资源但产物 jar 里没有", "11", _inject_unpackaged_file, ("--jar",)),
    ("[13] 变体模型把 uv 放大 4 倍（切换模型时贴图跳变）", "13", _inject_uv_scale_mismatch, ()),
    ("[12] 少写一个 facing 变体（该状态黑紫）", "12", _inject_missing_variant, ()),
    ("[12] 变体键属性名写错（该键被丢弃）", "12", _inject_wrong_property_name, ()),
    ("[12] multipart 重复 selector", "12", _inject_duplicate_selector, ()),
    ("[12] 变体 x 不是 90 的倍数（整文件作废）", "12", _inject_bad_rotation, ()),
    ("[12] multipart 条件取值非法（selector 被丢弃）", "12", _inject_bad_multipart_value, ()),
]


def main():
    total, counts, text = run_gate()
    if total != 0:
        print(f"自检中止：当前工作区本来就有 {total} 个问题，先修好再自检。")
        print(text)
        return 1
    jar_total, _, _ = run_gate(("--jar",))
    if jar_total != 0:
        print(f"自检中止：带 --jar 时有 {jar_total} 个问题（先重建产物）。")
        return 1
    print(f"基线：[全部] 合计问题 {total} 个，[带 --jar] {jar_total} 个（符合预期）")

    failures = []
    for name, expect, inject, extra in CASES:
        restore = inject()
        try:
            t, c, out = run_gate(extra)
        finally:
            restore()
        got = c.get(expect, 0)
        if got > 0:
            print(f"  [已报出 {expect}:{got:>2}] {name}")
        else:
            failures.append(name)
            print(f"  [未报出] {name}（期望 [{expect}] > 0，实际 {c}）")
            print(out)

    after, c2, out2 = run_gate()
    print(f"恢复后：[全部] 合计问题 {after} 个 {c2}")
    if after != 0:
        failures.append("恢复后未归 0")
        print(out2)

    print(f"自检用例 {len(CASES)} 个，失败 {len(failures)} 个")
    for f in failures:
        print(f"  FAIL {f}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
