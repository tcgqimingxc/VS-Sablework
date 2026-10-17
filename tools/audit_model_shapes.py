#!/usr/bin/env python3
"""模型形状/部件完整性审计（第 3 项"交叉验证手段"的独立工具）。

产出两张清单 + 两张渲染需求清单：

  A. **有 elements 的模型 vs 只有 parent 的模型**
     - 对每个 `models/item/*.json` 与每个 blockstate 变体引用的模型，
       沿 parent 链解析，记录"链上第一处 elements 出现在哪个文件"；
     - `elements == []`（空数组）与"整条链都没有 elements"是**不同**的缺陷：
       前者渲染成空气，后者会在 1.21.1 由 ModelBakery 报 missing model。

  B. **需要 BER/自定义渲染器但没有渲染器的方块/物品**
     - 数据来源是**旧版 1.20.1 的真实注册**（`ClockworkBlockEntities.kt` 里的
       `ClockworkSimpleBlockEntityVisualFactory`、`ClockworkItems.kt` 里的
       `customRenderedItem`），不是猜测；
     - 与本移植 `src/main/java/**` 里实际出现的 `EntityRenderersEvent` /
       `SimpleCustomRenderer.create` / `.renderer(` 对照。

用法：
  python tools/audit_model_shapes.py                 # 打印两张清单
  python tools/audit_model_shapes.py --json out.json # 同时落盘
"""
import argparse
import json
import os
import re
import sys

TOOLS = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(TOOLS)
sys.path.insert(0, TOOLS)

import cw_assets_lib as L    # noqa: E402
import cw_ids as IDS         # noqa: E402

OLD = os.path.join(ROOT, "Clockwork-1.20.1-main")


# --------------------------- A. 模型形状 ---------------------------
def model_shape(A, mid):
    """返回 (elements 定义处, 元素个数, 错误)。

    elements 为 None 表示整条 parent 链都没有；为空数组表示显式声明了空 elements。
    """
    cur, seen = mid, set()
    while cur is not None:
        if cur in seen:
            return None, 0, f"parent 成环: {cur}"
        seen.add(cur)
        data, err = A.model(cur)
        if err is not None:
            return None, 0, err
        els = data.get("elements")
        if isinstance(els, list):
            return cur, len(els), None
        p = data.get("parent")
        cur = p if isinstance(p, str) else None
    return None, 0, None


def shape_report(A):
    items, blocks = [], []
    for rel, _fp in sorted(L.iter_models(A.assets)):
        mid = f"{L.NS}:" + rel[len("models/"):-len(".json")]
        owner, count, err = model_shape(A, mid)
        row = dict(model=mid, file=rel, elements_at=owner, elements=count,
                   error=err)
        if rel.startswith("models/item/"):
            items.append(row)
        else:
            blocks.append(row)
    return items, blocks


# --------------------------- B. 渲染需求 ---------------------------
def old_visual_blocks():
    """旧版 ClockworkBlockEntities.kt 里挂了 visual / renderer 的方块 id。"""
    path = os.path.join(OLD, "common", "src", "main", "kotlin",
                        "org", "valkyrienskies", "clockwork",
                        "ClockworkBlockEntities.kt")
    if not os.path.isfile(path):
        return {}
    text = open(path, encoding="utf-8", errors="replace").read()
    out = {}
    # REGISTRATE.blockEntity("duct", ...) ... 同一段里出现 Visual/ renderer
    for m in re.finditer(r'blockEntity\(\s*"([a-z0-9_]+)"', text):
        name = m.group(1)
        seg = text[m.start():m.start() + 3000]
        kinds = []
        if "Visual(" in seg or "VisualFactory" in seg:
            kinds.append("flywheel-visual")
        if "Renderer(" in seg:
            kinds.append("renderer")
        if kinds:
            out[name] = kinds
    return out


def old_custom_items():
    """旧版 ClockworkItems.kt 里 customRenderedItem / itemModelWithPartials 的物品 id。"""
    path = os.path.join(OLD, "common", "src", "main", "kotlin",
                        "org", "valkyrienskies", "clockwork", "ClockworkItems.kt")
    out = {}
    if not os.path.isfile(path):
        return out
    text = open(path, encoding="utf-8", errors="replace").read()
    # 每个 item 注册块：从 REGISTRATE.item... 到 .register()
    for m in re.finditer(r'item<[^>]*>\(\s*"([a-z0-9_]+)"|item\(\s*"([a-z0-9_]+)"', text):
        name = m.group(1) or m.group(2)
        end = text.find(".register()", m.start())
        seg = text[m.start():end if end > 0 else m.start() + 2500]
        flags = []
        if "customRenderedItem" in seg:
            flags.append("custom-renderer")
        if "itemModelWithPartials" in seg:
            flags.append("partials")
        if flags:
            out[name] = flags
    return out


def new_client_facts(project):
    """本移植实际存在的：BER 注册、物品渲染器注册、renderer 类。"""
    java = os.path.join(project, "src", "main", "java")
    be_regs, item_regs, classes = {}, {}, set()
    for dp, _, fs in os.walk(java):
        for f in fs:
            if not f.endswith(".java"):
                continue
            p = os.path.join(dp, f)
            text = open(p, encoding="utf-8", errors="replace").read()
            for m in re.finditer(r'class\s+(\w+Renderer)\b', text):
                classes.add(m.group(1))
            for m in re.finditer(r'\.renderer\(\s*\(\)\s*->\s*([\w\.]+)::new', text):
                be_regs[m.group(1).split(".")[-1]] = os.path.basename(p)
            for m in re.finditer(
                    r'SimpleCustomRenderer\.create\(\s*([\w\.]+)\.get\(\)\s*,\s*([\w\.]+)', text):
                item_regs[m.group(1).split(".")[-1]] = (m.group(2), os.path.basename(p))
    return be_regs, item_regs, classes


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--project", default="clockwork_unoffical")
    ap.add_argument("--json", default=None)
    args = ap.parse_args()
    project = os.path.join(ROOT, args.project)
    A = L.Assets(project)
    ids = IDS.collect(project)

    items, blocks = shape_report(A)
    be_regs, item_regs, classes = new_client_facts(project)

    print("=" * 78)
    print("A1. 物品模型形状（models/item/*.json）")
    print("=" * 78)
    no_el = [r for r in items if r["elements_at"] is None]
    empty = [r for r in items if r["elements_at"] is not None and r["elements"] == 0]
    print(f"共 {len(items)} 个；整条链无 elements: {len(no_el)}；显式空 elements: {len(empty)}")
    for r in no_el:
        print(f"    [无 elements] {r['model']}  ({r['file']}) {r['error'] or ''}")
    for r in empty:
        print(f"    [空 elements] {r['model']}  定义于 {r['elements_at']}")

    print("\n" + "=" * 78)
    print("A2. 方块模型形状（models/block/**）")
    print("=" * 78)
    nb = [r for r in blocks if r["elements_at"] is None]
    eb = [r for r in blocks if r["elements_at"] is not None and r["elements"] == 0]
    print(f"共 {len(blocks)} 个；整条链无 elements: {len(nb)}；显式空 elements: {len(eb)}")
    for r in nb:
        print(f"    [无 elements] {r['model']}  ({r['file']}) {r['error'] or ''}")
    for r in eb:
        print(f"    [空 elements] {r['model']}  定义于 {r['elements_at']}")

    print("\n" + "=" * 78)
    print("B1. 旧版有 visual/renderer 的方块实体 ↔ 本移植是否有渲染器")
    print("=" * 78)
    ov = old_visual_blocks()
    missing = []
    for name in sorted(ov):
        # 本移植可能改名/合并：先精确匹配，再看是否有任意 renderer 类名含该名字
        hit = name in be_regs or any(name.replace("_", "") in c.lower() for c in classes)
        mark = "OK " if hit else "缺 "
        if not hit:
            missing.append(name)
        print(f"    {mark} {name:32s} 旧版 {ov[name]}")
    print(f"    —— 其中本移植没找到渲染器/visual 的: {len(missing)} 个")

    print("\n" + "=" * 78)
    print("B2. 旧版自定义物品渲染器 ↔ 本移植是否注册")
    print("=" * 78)
    ot = old_custom_items()
    miss_items = []
    for name in sorted(ot):
        hit = name in item_regs
        if not hit:
            miss_items.append(name)
        print(f"    {'OK ' if hit else '缺 '} {name:28s} 旧版 {ot[name]}"
              f"{'  本移植 -> ' + str(item_regs[name]) if hit else ''}")
    print(f"    —— 其中本移植没注册物品渲染器的: {len(miss_items)} 个")

    print("\n" + "=" * 78)
    print("B3. 本移植已注册的 BER")
    print("=" * 78)
    for k, v in sorted(be_regs.items()):
        print(f"    {k:34s} <- {v}")
    print("\n本移植已注册的物品渲染器：")
    for k, v in sorted(item_regs.items()):
        print(f"    {k:34s} <- {v}")

    if args.json:
        json.dump(dict(items=items, blocks=blocks,
                       old_visuals=ov, old_custom_items=ot,
                       new_be_renderers=be_regs,
                       new_item_renderers={k: list(v) for k, v in item_regs.items()},
                       missing_visual_blocks=missing,
                       missing_item_renderers=miss_items),
                  open(args.json, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
        print(f"\n已写出 {args.json}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
