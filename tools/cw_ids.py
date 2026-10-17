#!/usr/bin/env python3
"""从 Java 源码里正确提取「注册了哪些方块 / 物品 / 方块实体 / 实体」。

为什么需要单独一个模块（而不是几行正则）：
  - Registrate 的 `.block("id", ...)` / `.item("id", ...)` 才是注册点；
    `.blockEntity("id",...)` / `.entity("id",...)` **不是**物品或方块，早期脚本把它们
    当成方块 id，导致 check_assets.py 的 [1]/[2] 项既有假阳性又有漏报。
  - CWBlocks 这类文件把注册包在本地辅助方法里（`simple("nyx", true)`、`ore(...)`），
    字面量只出现在调用点，所以要先把「辅助方法名 -> 种类」解析出来。
"""
import os
import re

# 定义辅助方法：private static ... name(String ... ) { ... REGISTRATE.block(name ... ) }
HELPER_DEF = re.compile(
    r'\b(?:static\s+)?[\w<>,\[\]\.\?\s]+?\s+(\w+)\s*\(\s*String\s+(\w+)\s*[,)]')
# 只认**裸调用**（前面不是 '.'）：Registrate 自己的 API 里也有 .simple(name, registry, factory)，
# 例如 CWAeronautIndex 的 `.simple("flight", Registries.ARMOR_MATERIAL, ...)` 是护甲材料，
# 不是方块；早期脚本把它当成方块 → 假报「flight 缺 blockstate / item 模型」。
CALL_ANY = re.compile(r'(?<![\w.])(\w+)\s*\(\s*"([a-z0-9_]+)"')

REG_BLOCK = re.compile(r'\.block\(\s*"([a-z0-9_]+)"')
REG_ITEM = re.compile(r'\.item\(\s*"([a-z0-9_]+)"')
REG_BE = re.compile(r'\.blockEntity\(\s*"([a-z0-9_]+)"')
REG_ENTITY = re.compile(r'\.entity\(\s*"([a-z0-9_]+)"')


def _helper_kinds(text):
    """返回 {辅助方法名: 'block'|'item'}（只认名字参数被直接喂给 REGISTRATE.block/item 的）。"""
    kinds = {}
    for m in HELPER_DEF.finditer(text):
        name, param = m.group(1), m.group(2)
        # 取该方法体（简单做法：从定义处到下一个同缩进的 '}' 或文件末尾 4000 字符）
        seg = text[m.start():m.start() + 4000]
        if re.search(r'\.block\(\s*' + re.escape(param) + r'\b', seg):
            kinds[name] = 'block'
        elif re.search(r'\.item\(\s*' + re.escape(param) + r'\b', seg):
            kinds[name] = 'item'
    return kinds


def collect(project):
    """返回 dict(blocks=set, items=set, bes=set, entities=set, sources=dict)。"""
    blocks, items, bes, entities = set(), set(), set(), set()
    helpers = {}
    java_files = []
    src = os.path.join(project, "src", "main", "java")
    for dirpath, _, files in os.walk(src):
        for f in files:
            if f.endswith(".java"):
                java_files.append(os.path.join(dirpath, f))

    texts = {p: open(p, encoding="utf-8", errors="replace").read() for p in java_files}

    for path, text in texts.items():
        blocks.update(REG_BLOCK.findall(text))
        items.update(REG_ITEM.findall(text))
        bes.update(REG_BE.findall(text))
        entities.update(REG_ENTITY.findall(text))
        # 辅助方法按文件解析（同名方法在不同类里含义可能不同）
        local = _helper_kinds(text)
        helpers.update({f"{os.path.basename(path)}::{k}": v for k, v in local.items()})
        for name, arg in CALL_ANY.findall(text):
            kind = local.get(name)
            if kind == 'block':
                blocks.add(arg)
            elif kind == 'item':
                items.add(arg)
    # 注意：**不能**减掉 blockEntity id —— 机器方块与它的方块实体通常同名
    # （例如 duct 既是方块也是 BE），早期脚本正是因为这个把 40 多个机器方块全漏掉了。
    # 只有 entity（真实体）id 一定不是方块/物品。
    return dict(blocks=blocks - entities,
                items=items - entities,
                bes=bes, entities=entities, helpers=helpers,
                files=len(java_files))


if __name__ == "__main__":
    import json
    import sys
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    proj = os.path.join(root, sys.argv[1] if len(sys.argv) > 1 else "clockwork_unoffical")
    r = collect(proj)
    print(f"java 文件 {r['files']} 个；辅助方法 {r['helpers']}")
    print(f"\n方块 {len(r['blocks'])} 个:")
    print("  " + ", ".join(sorted(r['blocks'])))
    print(f"\n物品 {len(r['items'])} 个:")
    print("  " + ", ".join(sorted(r['items'])))
    print(f"\n方块实体 {len(r['bes'])} 个: " + ", ".join(sorted(r['bes'])))
    print(f"实体 {len(r['entities'])} 个: " + ", ".join(sorted(r['entities'])))
