#!/usr/bin/env python3
"""一次性资源修复（可重复执行，已修过的会跳过）。

修的都是**有实证**的缺陷（见 docs/ASSET-AUDIT.md）：
  R1 `models/block/creative_gas_generator/item.json` 引用了不存在的
     `vs_clockwork:block/creative_generator`（贴图实际叫 creative_gas_generator.png，
     同目录的 block.json 用的就是正确名字）→ 物品形态一直是黑紫。
  R2 `models/block/valve_duct/pointer.json` 的 particle 指向 `create:block/copper_plating`，
     Create 6.0.10 里没有这张贴图（已核对 create jar 的条目）→ 换成同义的 copper_casing。
  R3 5 个模型里残留 Blockbench 未分配贴图的面（`"texture": "#missing"`）。
     1.21.1 对无法解析的贴图键**完全静默**（SpriteLoader/ModelBakery 常量池里都没有相应日志文案），
     这些面会直接渲染成黑紫。改成该模型里已有的、语义最近的贴图键（不改形状）。
  R4 `textures/gui/gas_filter_OVERVIEW.png` 路径含大写字母，1.21 的包校验会**静默忽略**该文件
     （日志里 `Invalid path in pack: ... ignoring`），改名成小写。
  R5 删掉误入资源树的 0 字节无扩展名文件 `models/item/physics_infuser`（会被原样打进 jar）。

用法: python tools/fix_asset_refs.py [--apply]   （默认 dry-run）
"""
import argparse
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
A = os.path.join(ROOT, "clockwork_unoffical", "src", "main", "resources", "assets",
                 "vs_clockwork")

# (相对路径, 旧串, 新串, 说明)
TEXT_FIXES = [
    ("models/block/creative_gas_generator/item.json",
     '"vs_clockwork:block/creative_generator"',
     '"vs_clockwork:block/creative_gas_generator"',
     "R1 creative_gas_generator 物品模型贴图名写错（缺 _gas）"),
    ("models/block/valve_duct/pointer.json",
     '"particle": "create:block/copper_plating"',
     '"particle": "create:block/copper_casing"',
     "R2 Create 6.0.10 没有 copper_plating 贴图"),
    # R3：把未分配贴图的面指向本模型已有、语义最近的键
    ("models/block/brass_propeller_bearing/item.json", '"texture": "#missing"', '"texture": "#1"',
     "R3 黄铜螺旋桨轴承：顶盖下表面 -> bearing_top(#1)"),
    ("models/block/compressor/compressor.json", '"texture": "#missing"', '"texture": "#3"',
     "R3 压缩机方块：轴顶面 -> axis_top(#3)"),
    ("models/block/compressor/item.json", '"texture": "#missing"', '"texture": "#3"',
     "R3 压缩机物品：轴顶面 -> axis_top(#3)"),
    ("models/block/juryrigged_propeller_bearing/item.json", '"texture": "#missing"',
     '"texture": "#bearing_side"',
     "R3 拼装螺旋桨轴承：整根中轴 6 个面 -> mechanical_bearing_side"),
    ("models/block/physics_infuser/zap.json", '"texture": "#missing"', '"texture": "#0"',
     "R3 物理注入器电弧：零面积面 -> electricity(#0)"),
]

RENAMES = [
    ("textures/gui/gas_filter_OVERVIEW.png", "textures/gui/gas_filter_overview.png",
     "R4 大写路径会被 1.21 的资源包校验静默忽略"),
]

DELETES = [
    ("models/item/physics_infuser", "R5 0 字节无扩展名残留文件（会被打进 jar）"),
]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true", help="真正写入；默认只预览")
    args = ap.parse_args()
    mode = "APPLY" if args.apply else "DRY-RUN"
    changed = 0
    print(f"== 资源修复 ({mode}) 根目录 {A}\n")

    for rel, old, new, why in TEXT_FIXES:
        fp = os.path.join(A, rel.replace("/", os.sep))
        if not os.path.isfile(fp):
            print(f"  !! 缺文件 {rel}")
            continue
        text = open(fp, encoding="utf-8").read()
        if new in text and old not in text:
            print(f"  跳过（已修） {rel}: {why}")
            continue
        n = text.count(old)
        if n == 0:
            print(f"  跳过（找不到目标串） {rel}: {why}")
            continue
        print(f"  修 {rel}: {n} 处 —— {why}")
        if args.apply:
            open(fp, "w", encoding="utf-8", newline="").write(text.replace(old, new))
        changed += 1

    for rel, new_rel, why in RENAMES:
        src = os.path.join(A, rel.replace("/", os.sep))
        dst = os.path.join(A, new_rel.replace("/", os.sep))
        if not os.path.isfile(src):
            print(f"  跳过（已改或无此文件） {rel}: {why}")
            continue
        print(f"  改名 {rel} -> {new_rel} —— {why}")
        if args.apply:
            # Windows 文件系统大小写不敏感，直接 os.replace 到只有大小写不同的名字会变成
            # "同一个文件" 而不生效；必须先改成临时名再改成目标名。
            tmp = dst + ".rename_tmp"
            os.replace(src, tmp)
            os.replace(tmp, dst)
        changed += 1

    for rel, why in DELETES:
        fp = os.path.join(A, rel.replace("/", os.sep))
        if not os.path.isfile(fp):
            print(f"  跳过（已删） {rel}: {why}")
            continue
        size = os.path.getsize(fp)
        print(f"  删除 {rel} ({size} 字节) —— {why}")
        if args.apply:
            os.remove(fp)
        changed += 1

    print(f"\n{'已应用' if args.apply else '待应用'} {changed} 项")
    return 0


if __name__ == "__main__":
    sys.exit(main())
