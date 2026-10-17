#!/usr/bin/env python3
"""Ponder 场景静态自检：nbt 存在性 / nbt 里的方块是否注册 / lang 键是否齐全 / title 与 nbt 是否同名。

为什么需要它（三条都是"只在运行时才炸 / 才静默"的坑）：
  1. `PonderSceneRegistry` 在注册期就按 `<ns>:ponder/<id>.nbt` 读结构图，
     **nbt 缺失 = 加载期直接抛异常**，而这份 nbt 是二进制资源，编译器与资源守门都看不见；
  2. nbt 里如果引用了本移植没注册的方块，Ponder 世界读结构时会退化成空气 —— 场景看起来
     "缺件"，但不报错；本移植的方块 id 与旧版不完全一致，所以必须逐条比对 Java 里的注册点；
  3. Ponder 的 lang 键取自 **`scene.title("<id>")` 的 id**（不是 nbt 名、也不是组件 id；
     见 `PonderSceneBuilder#title` 字节码），所以 title 写错时文本会整段显示成键名。
     本脚本同时校验 title == nbt 名，以及每个 `text(scene, N)` 用到的 `.text_N` 都在
     en_us / zh_cn 里存在且 1..max 无缺口。

复用 `tools/nbt_dump.py` 的 NBT 读取器与 `tools/cw_ids.py` 的注册 id 索引，不重复造轮子。
退出码：0 = 无问题；1 = 有问题（与 check_data_refs.py / check_assets.py 的约定一致）。

用法：
    python tools/check_ponder.py                      # 检查本工程
    python tools/check_ponder.py --project <路径>      # 检查别的工程树
    python tools/check_ponder.py --selftest           # 注入 4 类坏数据，自证能检出
"""
import argparse
import json
import os
import re
import sys
import tempfile
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cw_ids                     # noqa: E402  注册 id 索引（与 check_assets.py 同源）
from nbt_dump import load as load_nbt   # noqa: E402  复用极简 NBT 读取器

MOD_ID = "vs_clockwork"
REGISTRY_REL = os.path.join(
    "src", "main", "java", "org", "valkyrienskies", "clockwork", "integration", "ponder",
    "ClockworkPonderScenes.java")
SCENES_REL = os.path.join(
    "src", "main", "java", "org", "valkyrienskies", "clockwork", "integration", "ponder", "scenes")

# 一行一个场景的注册写法：entries.forComponents(A, B).addStoryBoard("id", Cls::method);
REG_LINE = re.compile(
    r"forComponents\((?P<components>[^)]*)\)\s*\.addStoryBoard\(\s*"
    r'"(?P<id>[a-z0-9_/]+)"\s*,\s*(?P<cls>[A-Za-z_]\w*)::(?P<method>[A-Za-z_]\w*)')
TITLE = re.compile(r'\.title\(\s*"([^"]+)"')
TEXT_INDEX = re.compile(r"\btext\(\s*scene\s*,\s*(\d+)")


def _nbt_items(value):
    """nbt_dump 把 List 读成 (元素类型, [值...])，这里统一取列表部分。"""
    if isinstance(value, tuple):
        return value[1]
    return value or []


def _palette_names(root):
    """取结构图 palette 里所有方块 id。"""
    names = []
    for entry in _nbt_items(root.get("palette")):
        name = entry.get("Name")
        if name:
            names.append(name)
    return names


def _create_block_ids(project):
    """从 libs/create-*.jar 的 blockstates 反推 Create 已注册的方块 id（集合）。"""
    ids = set()
    libs = os.path.join(project, "libs")
    if not os.path.isdir(libs):
        return ids
    for jar_name in os.listdir(libs):
        if not (jar_name.startswith("create-") and jar_name.endswith(".jar")):
            continue
        try:
            with zipfile.ZipFile(os.path.join(libs, jar_name)) as jar:
                for entry in jar.namelist():
                    m = re.match(r"assets/create/blockstates/(.+)\.json$", entry)
                    if m:
                        ids.add("create:" + m.group(1))
        except zipfile.BadZipFile:
            continue
    return ids


def _registered_ids(project):
    """本模组已注册的方块/物品 id（带命名空间）。"""
    collected = cw_ids.collect(project)
    blocks = {MOD_ID + ":" + name for name in collected["blocks"]}
    items = {MOD_ID + ":" + name for name in collected["items"]}
    return blocks | items


def _read_json(path):
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def _default_project():
    """默认工程根：本仓库下的 clockwork_unoffical/；没有就退回仓库根。"""
    repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    candidate = os.path.join(repo, "clockwork_unoffical")
    return candidate if os.path.isdir(candidate) else repo


def _lang_files(project):
    base = os.path.join(project, "src", "main", "resources", "assets", MOD_ID, "lang")
    out = {}
    for name in ("en_us.json", "zh_cn.json"):
        path = os.path.join(base, name)
        if os.path.isfile(path):
            out[name] = _read_json(path)
        else:
            out[name] = {}
    return out


def _scene_sources(project):
    """{类名: 源码文本}，用于取 title 与 text 索引。"""
    folder = os.path.join(project, SCENES_REL)
    sources = {}
    if not os.path.isdir(folder):
        return sources
    for name in os.listdir(folder):
        if name.endswith(".java"):
            with open(os.path.join(folder, name), encoding="utf-8", errors="replace") as handle:
                sources[name[:-len(".java")]] = handle.read()
    return sources


def _write_nbt(path, block_ids, data_version=3953):
    """写一个最小的结构图 nbt（仅供 selftest 注入坏数据用）。

    注意：List 的元素是"纯 payload"，不含 tag id / 名字，所以 palette/blocks 的每个元素
    要直接拼 compound 的内容，不能复用带名字的 tag 编码器。
    """
    def string(value):
        raw = value.encode()
        return len(raw).to_bytes(2, "big") + raw

    def compound(entries):
        """entries: [(名字, tag id, payload)] -> compound 内容 + TAG_End。"""
        raw = b""
        for name, tag_id, payload in entries:
            raw += bytes([tag_id]) + len(name.encode()).to_bytes(2, "big") + name.encode() + payload
        return raw + b"\x00"

    def int_list(values):
        return len(values).to_bytes(4, "big") + b"".join(int(v).to_bytes(4, "big") for v in values)

    palette_entries = b"".join(
        compound([("Name", 8, string(block_id)), ("Properties", 10, compound([]))])
        for block_id in block_ids)
    palette = bytes([10]) + len(block_ids).to_bytes(4, "big") + palette_entries
    blocks = bytes([10]) + (1).to_bytes(4, "big") + compound([
        ("pos", 9, bytes([3]) + int_list([0, 0, 0])),
        ("state", 3, (0).to_bytes(4, "big")),
    ])
    root = compound([
        ("palette", 9, palette),
        ("blocks", 9, blocks),
        ("size", 9, bytes([3]) + int_list([1, 1, 1])),
        ("DataVersion", 3, data_version.to_bytes(4, "big")),
    ])
    name = b"Schematic"
    with open(path, "wb") as handle:
        handle.write(bytes([10]) + len(name).to_bytes(2, "big") + name + root)


def check(project):
    """跑全部检查，返回 (issues, hints, scenes)。issues 每条是一句话。"""
    issues = []
    hints = []

    registry_path = os.path.join(project, REGISTRY_REL)
    if not os.path.isfile(registry_path):
        return ["找不到注册表：%s" % REGISTRY_REL], hints, []
    with open(registry_path, encoding="utf-8", errors="replace") as handle:
        registry = handle.read()

    registrations = [m.groupdict() for m in REG_LINE.finditer(registry)]
    if not registrations:
        return ["注册表里没有解析到任何 addStoryBoard（写法必须是 "
                "forComponents(...).addStoryBoard(\"id\", Cls::method);）"], hints, []

    ponder_dir = os.path.join(project, "src", "main", "resources", "assets", MOD_ID, "ponder")
    lang = _lang_files(project)
    sources = _scene_sources(project)
    registered_ids = _registered_ids(project)
    create_ids = _create_block_ids(project)

    # 同一个 nbt 可能挂给多个组件（例如 plane_tips），按 nbt 去重后再做资源检查
    scene_ids = []
    for reg in registrations:
        if reg["id"] not in scene_ids:
            scene_ids.append(reg["id"])

    scenes = []
    for scene_id in scene_ids:
        regs = [r for r in registrations if r["id"] == scene_id]
        nbt_path = os.path.join(ponder_dir, scene_id + ".nbt")
        info = {"id": scene_id, "nbt": nbt_path, "components": [r["components"] for r in regs],
                "registrations": len(regs), "classes": sorted({r["cls"] for r in regs}), "texts": []}
        scenes.append(info)

        # ① nbt 存在且非空
        if not os.path.isfile(nbt_path):
            issues.append("[nbt] %s：缺少 assets/%s/ponder/%s.nbt（Ponder 注册期会直接抛异常）"
                          % (scene_id, MOD_ID, scene_id))
            continue
        if os.path.getsize(nbt_path) == 0:
            issues.append("[nbt] %s：%s.nbt 是 0 字节" % (scene_id, scene_id))
            continue

        # ② 结构图里的方块 id 必须都注册过
        try:
            root = load_nbt(nbt_path)
        except Exception as exc:                     # noqa: BLE001  坏 nbt 本身就是问题
            issues.append("[nbt] %s：解析失败（%s）" % (scene_id, exc))
            continue
        for block_id in _palette_names(root):
            namespace, _, path = block_id.partition(":")
            if namespace == MOD_ID:
                if block_id not in registered_ids:
                    issues.append("[block] %s：nbt 里的 %s 在本工程没有注册（或 id 与旧版不同）"
                                  % (scene_id, block_id))
            elif namespace == "create":
                if create_ids and block_id not in create_ids:
                    issues.append("[block] %s：nbt 里的 %s 在 create-*.jar 里不存在" % (scene_id, block_id))
            # minecraft: 原版方块不查（本机没有原版资源索引，且它们不会因为移植而改名）

        # ③ title 必须与 nbt 同名（Ponder 用 title 的 id 组 lang 键）
        for cls in info["classes"]:
            source = sources.get(cls)
            if source is None:
                issues.append("[java] %s：找不到场景类 scenes/%s.java" % (scene_id, cls))
                continue
            titles = TITLE.findall(source)
            if not titles:
                issues.append("[java] %s：%s 没有调用 scene.title(...)" % (scene_id, cls))
            elif scene_id not in titles:
                issues.append("[java] %s：%s 的 title 是 %s，与 nbt 名不一致（lang 键会整段失效）"
                              % (scene_id, cls, "/".join(titles)))

            # ④ text(scene, N) 与 lang 键
            indices = sorted({int(n) for n in TEXT_INDEX.findall(source)})
            info["texts"] = indices
            if not indices:
                hints.append("[lang] %s：%s 没有引用任何 text_N（纯动画场景可忽略）" % (scene_id, cls))
            for index in indices:
                key = "%s.ponder.%s.text_%d" % (MOD_ID, scene_id, index)
                for lang_name, table in lang.items():
                    if key not in table:
                        issues.append("[lang] %s：%s 缺 %s（%s 在用）" % (scene_id, lang_name, key, cls))
            if indices and indices != list(range(1, indices[-1] + 1)):
                missing = [i for i in range(1, indices[-1] + 1) if i not in indices]
                hints.append("[lang] %s：text_%s 有 lang 键但场景没用（不一定是错，留待后续）"
                             % (scene_id, ",".join(map(str, missing))))

        header = "%s.ponder.%s.header" % (MOD_ID, scene_id)
        for lang_name, table in lang.items():
            if header not in table:
                issues.append("[lang] %s：%s 缺 %s" % (scene_id, lang_name, header))

    # ⑤ 反向检查：资源里躺着的 nbt 有没有没人注册的（死资源）
    if os.path.isdir(ponder_dir):
        for name in sorted(os.listdir(ponder_dir)):
            if name.endswith(".nbt") and name[:-4] not in scene_ids:
                hints.append("[nbt] %s.nbt 存在但没有注册（死资源，或该场景被有意跳过）" % name[:-4])

    return issues, hints, scenes


def _print_report(project, issues, hints, scenes):
    print("== Ponder 场景注册表（%d 个场景 / %d 条注册）==" % (
        len(scenes), sum(s["registrations"] for s in scenes)))
    for scene in scenes:
        texts = scene["texts"]
        span = "text_%d..%d" % (texts[0], texts[-1]) if texts else "无文本"
        print("  %-20s nbt=%-22s %-14s ← %s" % (
            scene["id"], os.path.basename(scene["nbt"]), span,
            " | ".join(scene["components"])))
    print()
    print("== 检查结果 ==")
    for hint in hints:
        print("  提示：" + hint)
    if issues:
        for issue in issues:
            print("  问题：" + issue)
    print()
    print("合计问题: %d 个" % len(issues))
    return len(issues)


def _selftest():
    """在临时工程里注入 4 类坏数据，确认检查器能把它们全部报出来。"""
    root = tempfile.mkdtemp(prefix="cw_ponder_selftest_")
    try:
        # 一个"像模像样"的工程：注册过的方块 + 两个场景（一个正常，一个各带一种坏数据）
        java_index = os.path.join(root, "src", "main", "java", "demo")
        os.makedirs(java_index, exist_ok=True)
        with open(os.path.join(java_index, "DemoIndex.java"), "w", encoding="utf-8") as handle:
            handle.write("class DemoIndex {\n"
                         "  static { REGISTRATE.block(\"known_block\", Block::new); }\n"
                         "}\n")

        registry_path = os.path.join(root, REGISTRY_REL)
        os.makedirs(os.path.dirname(registry_path), exist_ok=True)
        with open(registry_path, "w", encoding="utf-8") as handle:
            handle.write(
                "entries.forComponents(A.BLOCK).addStoryBoard(\"good\", GoodScenes::good);\n"
                "entries.forComponents(A.BLOCK).addStoryBoard(\"bad_block\", BadBlockScenes::badBlock);\n"
                "entries.forComponents(A.BLOCK).addStoryBoard(\"bad_lang\", BadLangScenes::badLang);\n"
                "entries.forComponents(A.BLOCK).addStoryBoard(\"bad_title\", BadTitleScenes::badTitle);\n"
                "entries.forComponents(A.BLOCK).addStoryBoard(\"missing_nbt\", MissingScenes::missing);\n")

        ponder_dir = os.path.join(root, "src", "main", "resources", "assets", MOD_ID, "ponder")
        os.makedirs(ponder_dir, exist_ok=True)
        _write_nbt(os.path.join(ponder_dir, "good.nbt"), [MOD_ID + ":known_block"])
        _write_nbt(os.path.join(ponder_dir, "bad_block.nbt"), [MOD_ID + ":ghost_block"])
        _write_nbt(os.path.join(ponder_dir, "bad_lang.nbt"), [MOD_ID + ":known_block"])
        _write_nbt(os.path.join(ponder_dir, "bad_title.nbt"), [MOD_ID + ":known_block"])

        scenes_dir = os.path.join(root, SCENES_REL)
        os.makedirs(scenes_dir, exist_ok=True)
        scene_sources = {
            "GoodScenes": ('scene.title("good", "Good");\n'
                           'ClockworkPonderScenes.text(scene, 1);\n'),
            "BadBlockScenes": ('scene.title("bad_block", "BadBlock");\n'
                               'ClockworkPonderScenes.text(scene, 1);\n'),
            "BadLangScenes": ('scene.title("bad_lang", "BadLang");\n'
                              'ClockworkPonderScenes.text(scene, 1);\n'
                              'ClockworkPonderScenes.text(scene, 2);\n'),
            "BadTitleScenes": ('scene.title("wrong_title", "BadTitle");\n'
                               'ClockworkPonderScenes.text(scene, 1);\n'),
            "MissingScenes": ('scene.title("missing_nbt", "Missing");\n'
                              'ClockworkPonderScenes.text(scene, 1);\n'),
        }
        for cls, body in scene_sources.items():
            with open(os.path.join(scenes_dir, cls + ".java"), "w", encoding="utf-8") as handle:
                handle.write("class %s {\n%s}\n" % (cls, body))

        lang_dir = os.path.join(root, "src", "main", "resources", "assets", MOD_ID, "lang")
        os.makedirs(lang_dir, exist_ok=True)
        base_keys = ["%s.ponder.%s.header" % (MOD_ID, name) for name in
                     ("good", "bad_block", "bad_lang", "bad_title", "missing_nbt")]
        base_keys += ["%s.ponder.%s.text_1" % (MOD_ID, name) for name in
                      ("good", "bad_block", "bad_lang", "bad_title", "missing_nbt")]
        for lang_name in ("en_us.json", "zh_cn.json"):
            with open(os.path.join(lang_dir, lang_name), "w", encoding="utf-8") as handle:
                json.dump({key: key for key in base_keys}, handle)

        issues, _hints, _scenes = check(root)
        checks = [
            ("缺 nbt", any(i.startswith("[nbt] missing_nbt") for i in issues)),
            ("未注册方块", any("ghost_block" in i for i in issues)),
            ("缺 lang 键", any("bad_lang.text_2" in i for i in issues)),
            ("title 与 nbt 不同名", any("bad_title" in i and "title" in i for i in issues)),
        ]
        print("== selftest：注入 4 类坏数据 ==")
        for name, hit in checks:
            print("  %-4s %s" % ("检出" if hit else "漏报", name))
        passed = all(hit for _, hit in checks)
        if not passed:                      # 漏报时把检查器原话打出来，方便定位
            for issue in issues:
                print("    检查器输出：" + issue)
        print("selftest: %d/%d 用例被检出" % (sum(1 for _, hit in checks if hit), len(checks)))
        return 0 if passed else 1
    finally:
        import shutil
        shutil.rmtree(root, ignore_errors=True)


def main():
    parser = argparse.ArgumentParser(description="Ponder 场景静态自检")
    parser.add_argument("--project", default=_default_project(),
                        help="工程根目录（默认仓库下的 clockwork_unoffical/）")
    parser.add_argument("--selftest", action="store_true", help="注入坏数据，自检脚本本身")
    args = parser.parse_args()

    if args.selftest:
        return _selftest()

    project = os.path.abspath(args.project)
    issues, hints, scenes = check(project)
    count = _print_report(project, issues, hints, scenes)
    return 1 if count else 0


if __name__ == "__main__":
    sys.exit(main())
