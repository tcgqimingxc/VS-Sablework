#!/usr/bin/env python3
"""气体储罐（duct_tank）的外观资源：世界模型（CT）+ 物品图标（非 CT）+ 内容物 BER。

本轮（2026-10-17）实机反馈："气体储罐在物品栏时贴图错误"。原因是上一轮为了让方块吃到
Create 的连接纹理（CT），把 **世界模型** ``block_single`` 从官方 1.20.1 的"图集单块立方体"
换成了 Create 的 plain 模型 + 16×16 基础贴图；而物品模型一直 parent 到 ``block_single``，
于是物品栏也用那张"从 4×4 图集裁出来、为了平铺而设计的一格墙"去烘图标 —— 得到的是没有
边框/铆钉的灰盒子，不是官方那个带双层盖板的罐子。

本脚本现在区分两类模型（这是本轮修复的核心，改动前先读完）：

1. **世界模型** ``models/block/duct_tank/{block_single,block_top,block_bottom,block_middle}.json``
   - 几何 = Create 6.0.10 ``fluid_tank/block_*.json``（plain，无观察窗）；贴图全部换成
     ``vs_clockwork:block/{duct_tank,duct_tank_top,fluid_tank_inner}``。
   - **必须**用 16×16 基础贴图：方块模型被 ``DuctTankModel``（``CTModel``）接管，模型的 uv 是
     "基础图内的相对位置"，实际像素由 64×64 图集按邻居重采样（见 ``DuctTankModel`` 类注释）。
     把贴图直接写成 ``*_connected`` 会让 CT 匹配失败（sprite != shift.original），拼接处露出
     单块的花纹。
   - 这 4 个文件已按 Create 的模型入库；脚本**不重写**它们，只做语义校验（若工作区里存在
     ``Create-mc1.21.1-dev``，会逐元素比对，防止有人凭印象改几何）。

2. **物品图标** ``block_single_item.json`` + ``models/item/duct_tank.json``
   - 把官方 1.20.1 的 ``block_single``（单个 0..16 立方体；64×64 图集 uv ``[0,12,4,16]``
     = (0,3) 格 = 完整单块面板）**逐字节**复制为 ``block_single_item.json``；
     item 模型 parent 指向它。
   - 为什么不能复用世界 ``block_single``：它是 CT 模型、贴图是平铺墙，烘 item 会丢边框/铆钉。
   - 为什么用图集格而不是 16×16：物品不经过 CT，需要一张"自带完整单块面板"的静态贴图；
     官方就是这么做的。Create 的 item 也指向普通 block 模型 —— 区别只是 Create 的基础贴图
     ``fluid_tank.png`` 本身就是完整面板，而我们的基础贴图是从图集裁的平铺格，所以这里
     显式回退到官方的图集立方体。

其余产物：内容物 BER 的 ``gas_volume.json`` + 纯白 ``duct_tank_gas.png``；两张 16×16 基础贴图
由 ``tools/gen_duct_tank_textures.py`` 从官方 4×4 图集派生（本脚本一并调用）。

用法::

    python tools/gen_duct_tank_assets.py            # 生成物品图标 / BER 资源 / 基础贴图
    python tools/gen_duct_tank_assets.py --check    # 逐字节核对 + 世界模型语义校验
"""

import argparse
import base64
import json
import os
import sys

TOOLS = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(TOOLS)
PROJECT = os.path.join(ROOT, "clockwork_unoffical")
ASSETS = os.path.join(PROJECT, "src", "main", "resources", "assets", "vs_clockwork")

# 官方解包树（1.20.1 发布包，物品图标/图集的权威来源）。
OFFICIAL = os.path.join(ROOT, "assets", "vs_clockwork")
OFFICIAL_MODELS = os.path.join(OFFICIAL, "models", "block", "duct_tank")

# Create 1.21.1 开发树（世界模型的几何来源，可选：不存在时跳过逐元素比对）。
CREATE = os.path.join(ROOT, "Create-mc1.21.1-dev")
CREATE_TANK_MODELS = os.path.join(CREATE, "src", "main", "resources", "assets", "create",
                                  "models", "block", "fluid_tank")

MODEL_DIR = os.path.join(ASSETS, "models", "block", "duct_tank")
BLOCKSTATE = os.path.join(ASSETS, "blockstates", "duct_tank.json")
ITEM_MODEL = os.path.join(ASSETS, "models", "item", "duct_tank.json")
TEXTURE_DIR = os.path.join(ASSETS, "textures", "block")
GAS_TEXTURE = os.path.join(TEXTURE_DIR, "duct_tank_gas.png")

# 世界模型（CT 用，16×16 基础贴图）。不是"官方逐字节复制"，见文件头。
WORLD_MODELS = ("block_single", "block_top", "block_bottom", "block_middle")

# 物品图标模型：官方 block_single 的逐字节副本（非 CT，64×64 图集 (0,3) 格）。
ITEM_ICON_SOURCE = os.path.join(OFFICIAL_MODELS, "block_single.json")
ITEM_ICON_MODEL = os.path.join(MODEL_DIR, "block_single_item.json")
ITEM_PARENT = "vs_clockwork:block/duct_tank/block_single_item"

# 上一轮从 Create 复制来的观察窗模型 —— 官方没有窗，必须删掉，否则成死资源/误导。
OBSOLETE_MODELS = ("block_single_window", "block_top_window",
                   "block_bottom_window", "block_middle_window")

# Create 模型贴图 -> 本工程贴图。注意先长后短（fluid_tank_top / _inner 不能被 fluid_tank 抢走）。
TEXTURE_MAP = (
    ("create:block/fluid_tank_top", "vs_clockwork:block/duct_tank_top"),
    ("create:block/fluid_tank_inner", "vs_clockwork:block/fluid_tank_inner"),
    ("create:block/fluid_tank", "vs_clockwork:block/duct_tank"),
)

# 世界模型必须引用的基础贴图（键 -> 期望值）。block_middle 没有顶/底贴图。
EXPECTED_TEX = {
    "block_single": {"0": "vs_clockwork:block/duct_tank_top",
                     "1": "vs_clockwork:block/duct_tank",
                     "4": "vs_clockwork:block/fluid_tank_inner"},
    "block_top": {"0": "vs_clockwork:block/duct_tank_top",
                  "1": "vs_clockwork:block/duct_tank",
                  "4": "vs_clockwork:block/fluid_tank_inner"},
    "block_bottom": {"0": "vs_clockwork:block/duct_tank_top",
                     "1": "vs_clockwork:block/duct_tank",
                     "4": "vs_clockwork:block/fluid_tank_inner"},
    "block_middle": {"1": "vs_clockwork:block/duct_tank"},
}

# 内容物 BER 的单位立方体（纯白，渲染时用 PoseStack 缩放 + SuperByteBuffer.color 染色）
GAS_VOLUME_MODEL = {
    "parent": "block/block",
    "textures": {
        "gas": "vs_clockwork:block/duct_tank_gas",
        "particle": "vs_clockwork:block/duct_tank_gas",
    },
    "elements": [
        {
            "name": "GasVolume",
            "from": [0, 0, 0],
            "to": [16, 16, 16],
            "faces": {
                "north": {"uv": [0, 0, 16, 16], "texture": "#gas"},
                "east": {"uv": [0, 0, 16, 16], "texture": "#gas"},
                "south": {"uv": [0, 0, 16, 16], "texture": "#gas"},
                "west": {"uv": [0, 0, 16, 16], "texture": "#gas"},
                "up": {"uv": [0, 0, 16, 16], "texture": "#gas"},
                "down": {"uv": [0, 0, 16, 16], "texture": "#gas"},
            },
        }
    ],
}


def _read(path):
    if not os.path.isfile(path):
        raise SystemExit(f"缺少 {path}（官方资源树 / Create 开发树是否完整？）")
    with open(path, "rb") as fh:
        return fh.read()


def _json(path):
    with open(path, encoding="utf-8") as fh:
        return json.load(fh)


def build():
    """-> [(绝对路径, 期望字节, 说明)]，顺序稳定，供 --check 复用。

    只生成"物品图标 + item 模型 + BER 资源 + 基础贴图"。世界模型由 Create 的几何入库、
    本函数不重写（校验见 stale_problems）。
    """
    items = [
        (ITEM_ICON_MODEL, _read(ITEM_ICON_SOURCE),
         "官方 models/block/duct_tank/block_single.json 逐字节（单块立方体 + 图集 (0,3) 格）"),
        (ITEM_MODEL, (json.dumps({"parent": ITEM_PARENT}, indent=2) + "\n").encode("utf-8"),
         "物品模型：parent 指向非 CT 的 block_single_item（不是世界 block_single）"),
        (os.path.join(MODEL_DIR, "gas_volume.json"),
         (json.dumps(GAS_VOLUME_MODEL, indent=2, ensure_ascii=False) + "\n").encode("utf-8"),
         "内容物 BER 的单位立方体（纯白，颜色由渲染器给）"),
        (GAS_TEXTURE, base64.b64decode(_GAS_PNG_B64),
         "内容物 BER 的纯白 16x16 贴图"),
    ]

    sys.path.insert(0, TOOLS)
    import gen_duct_tank_textures as textures          # noqa: E402  （同目录脚本）

    for path, data, note in textures.build():
        items.append((path, data, note))
    return items


# 16x16 RGBA、全部 (255,255,255,255)、无辅助 chunk（原由 Pillow 写出的那 95 字节）
_GAS_PNG_B64 = (
    "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAJklEQVR4nGP8////fwYKABMlmkcN"
    "gAAmBgoB06gBDKNhwEB5GAAAa8oEHLq5H1oAAAAASUVORK5CYII="
)


def _create_mapped(name):
    """Create 的 plain 模型 + 贴图改名；Create 树不存在时返回 None。"""
    path = os.path.join(CREATE_TANK_MODELS, name + ".json")
    if not os.path.isfile(path):
        return None
    text = _read(path).decode("utf-8")
    for old, new in TEXTURE_MAP:
        text = text.replace(old, new)
    return json.loads(text)


def world_model_problems():
    """世界模型语义校验：parent / 贴图键 / 不出现 create: / （可选）逐元素对照 Create。"""
    problems = []
    for name in WORLD_MODELS:
        path = os.path.join(MODEL_DIR, name + ".json")
        rel = os.path.relpath(path, ROOT)
        if not os.path.isfile(path):
            problems.append(f"缺失 {rel}")
            continue
        try:
            model = _json(path)
        except Exception as exc:  # noqa: BLE001
            problems.append(f"{rel}: JSON 解析失败：{exc}")
            continue
        if model.get("parent") != "block/block":
            problems.append(f"{rel}: parent 必须是 block/block（当前 {model.get('parent')!r}）")
        refs = model.get("textures") or {}
        for key, expected in EXPECTED_TEX[name].items():
            if refs.get(key) != expected:
                problems.append(f"{rel}: textures[{key!r}] 必须是 {expected!r}（当前 {refs.get(key)!r}）"
                                f"—— 世界模型走 CT，必须用 16x16 基础贴图")
        for key, ref in refs.items():
            if isinstance(ref, str) and "create:" in ref:
                problems.append(f"{rel}: textures[{key!r}] 仍引用 create:（世界模型贴图必须全是 vs_clockwork）")
        if not model.get("elements"):
            problems.append(f"{rel}: 没有 elements")
        expected = _create_mapped(name)
        if expected is not None and model.get("elements") != expected.get("elements"):
            problems.append(f"{rel}: elements 与 Create 6.0.10 {name}.json 不一致"
                            f"（几何必须照 Create；只允许改贴图命名空间）")
    return problems


def stale_problems(items):
    """--check 的全部检查：逐字节 + 世界模型语义 + 不该存在的窗模型 + item parent。"""
    problems = []
    for path, expected, note in items:
        rel = os.path.relpath(path, ROOT)
        if not os.path.isfile(path):
            problems.append(f"缺失 {rel}（应来自 {note}）")
        elif open(path, "rb").read() != expected:
            problems.append(f"与期望值不一致 {rel}（应来自 {note}）")
    for name in OBSOLETE_MODELS:
        rel = os.path.relpath(os.path.join(MODEL_DIR, name + ".json"), ROOT)
        if os.path.isfile(os.path.join(MODEL_DIR, name + ".json")):
            problems.append(f"残留 Create 版模型 {rel}（官方没有观察窗，应删除）")
    for name in WORLD_MODELS + ("gas_volume",):
        path = os.path.join(MODEL_DIR, name + ".json")
        if not os.path.isfile(path):
            continue
        text = open(path, encoding="utf-8", errors="replace").read()
        if "create:" in text:
            problems.append(f"{os.path.relpath(path, ROOT)} 仍引用 create: 贴图")
    item = _json(ITEM_MODEL) if os.path.isfile(ITEM_MODEL) else {}
    if item.get("parent") != ITEM_PARENT:
        problems.append(f"{os.path.relpath(ITEM_MODEL, ROOT)}: parent 必须是 {ITEM_PARENT!r}"
                        f"（当前 {item.get('parent')!r}；指向世界 block_single 会复用 CT 贴图 → 图标错）")
    problems.extend(world_model_problems())
    return problems


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true",
                    help="只核对产物与官方/Create 资源是否一致（不一致非零退出）")
    args = ap.parse_args()

    items = build()
    if args.check:
        problems = stale_problems(items)
        if problems:
            print("duct_tank 外观资源不是最新（重跑 python tools/gen_duct_tank_assets.py）：")
            for p in problems:
                print("  " + p)
            return 1
        compared = "已与 Create 逐元素比对" if os.path.isdir(CREATE_TANK_MODELS) else "未找到 Create 开发树，跳过逐元素比对"
        print(f"通过：{len(items)} 个生成产物逐字节一致；4 个世界模型贴图/几何校验通过（{compared}）；"
              f"item 图标 = 官方 block_single（非 CT），parent = {ITEM_PARENT}")
        return 0

    removed = 0
    for name in OBSOLETE_MODELS:
        path = os.path.join(MODEL_DIR, name + ".json")
        if os.path.isfile(path):
            os.remove(path)
            removed += 1
            print("removed", os.path.relpath(path, ROOT))
    for path, data, note in items:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "wb") as fh:
            fh.write(data)
        print("wrote", os.path.relpath(path, ROOT), "<-", note)
    if not removed:
        print("（没有需要删除的 Create 版 *_window 模型）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
