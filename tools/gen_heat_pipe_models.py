"""生成热管（heat_pipe）缺失的方块模型 JSON。

背景：旧版与新仓库都只有 heat_pipe 的 blockstate 与贴图，没有 models/block/heat_pipe/*.json
（旧版靠 datagen 生成，未入库）。本脚本按 blockstate 里声明的模型名逐字补齐：

  lu_x / ru_x / ld_x / rd_x / ud_x / u_x / d_x / lr_x / l_x / r_x
  （y 与 z 两组同理）+ item_straight + core + full + full_wo_{六方向}

**方向映射的来源（2026-10-16 修正）**：`blockstates/heat_pipe.json` 是与 1.20.1 逐字节相同的
原始文件，它的 multipart 条件就是唯一权威。每个 selector 的条件恰好是这个形状该有的连接
方向，所以「模型名 → 臂朝向」是被条件**唯一确定**的：

  x 组条件用 {down,north,south,up}（YZ 平面）→ l=south(+Z) r=north(-Z) u=up(+Y) d=down(-Y)
  y 组条件用 {east,west,north,south}（XZ 平面）→ l=east(+X) r=west(-X) u=south(+Z) d=north(-Z)
  z 组条件用 {down,east,up,west}（XY 平面）→ l=east(+X) r=west(-X) u=up(+Y) d=down(-Y)

上一版把 y/z 两组的平面记反了（y 组按 XY 平面画、z 组按 XZ 平面画），还把 l/r 左右对调，
结果 30 个平面形状里 24 个的臂指向错误的邻居 —— 这个错误不会打任何日志，只能靠对照条件发现
（`tools/check_assets.py [12]` + `_probe_tmp` 里的一次性对照脚本）。加上 core/full/full_wo_*
共 38 个形状，每个形状 6 个热度档，共 228 个方块模型 + straight/item_straight 2 个 = 230 个。

温度档位：""（常温）/ _warm / _hot / _very_hot / _super_hot / _molten，
贴图分别是 heatpipe_{core,connection,rim}<后缀>.png。
"""

import json
import os

BASE = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "clockwork_unoffical", "src", "main", "resources", "assets", "vs_clockwork", "models", "block", "heat_pipe",
)

TIERS = ["", "_warm", "_hot", "_very_hot", "_super_hot", "_molten"]

# 方向 → (轴, 符号)：符号 +1 表示朝正方向（east/up/south）
DIRS = {
    "up": ("y", 1),
    "down": ("y", -1),
    "east": ("x", 1),
    "west": ("x", -1),
    "south": ("z", 1),
    "north": ("z", -1),
}

ALL_DIRECTIONS = ("down", "up", "north", "south", "west", "east")

# 每个模型名 → 连接方向集合（l/r/u/d 的含义见文件头，由原始 blockstate 的条件唯一确定）
VARIANTS = {}
for axis in ("x", "y", "z"):
    if axis == "x":                     # 条件用 down/north/south/up -> YZ 平面
        left, right, upper, lower = "south", "north", "up", "down"
    elif axis == "y":                   # 条件用 east/west/north/south -> XZ 平面
        left, right, upper, lower = "east", "west", "south", "north"
    else:                               # 条件用 down/east/up/west -> XY 平面
        left, right, upper, lower = "east", "west", "up", "down"

    VARIANTS[f"l_{axis}"] = (left,)
    VARIANTS[f"r_{axis}"] = (right,)
    VARIANTS[f"lr_{axis}"] = (left, right)
    VARIANTS[f"u_{axis}"] = (upper,)
    VARIANTS[f"d_{axis}"] = (lower,)
    VARIANTS[f"ud_{axis}"] = (upper, lower)
    VARIANTS[f"lu_{axis}"] = (left, upper)
    VARIANTS[f"ru_{axis}"] = (right, upper)
    VARIANTS[f"ld_{axis}"] = (left, lower)
    VARIANTS[f"rd_{axis}"] = (right, lower)

# 退化状态（blockstate 里没有任何平面组能命中的 8 个状态）专用的形状：
#   0 个连接 -> core；6 个连接 -> full；5 个连接 -> full_wo_<缺的那个方向>
VARIANTS["core"] = ()
VARIANTS["full"] = ALL_DIRECTIONS
for _d in ALL_DIRECTIONS:
    VARIANTS[f"full_wo_{_d}"] = tuple(x for x in ALL_DIRECTIONS if x != _d)

CORE_MIN, CORE_MAX = 5.0, 11.0
STUB_MIN, STUB_MAX = 4.925, 11.075  # 比核心略大，做出"接头"的层次
RIM_IN, RIM_OUT = 13.9, 16.15

# 三张贴图不是整张 16x16 都有效：core 只占左上 6x6，connection 占 (0,6)-(10,10)，
# rim 占左侧 7x16（见各 png 的 alpha bbox）。旧版/官方没有给出 UV 布局，这里按"内容区"
# 显式取景，避免用 [0,0,6,6] 去采样 connection/rim 的透明区域（那样接头和收口环是隐形的）。
UV_REGIONS = {
    "#core": (0.0, 0.0, 6.0, 6.0),
    "#connection": (0.0, 6.0, 10.0, 10.0),
    "#rim": (0.0, 0.0, 7.0, 16.0),
}
# 全局 texel 比例：所有面都按 "UV 跨度 = 面尺寸 × UV_SCALE" 取景。
# 取 0.97 是因为 rim 的最大面宽 7.2（CORE ± 0.6）略大于内容区宽 7.0，0.97 刚好放得下；
# 三张贴图共用同一个比例，`tools/check_assets.py [13]` 的"同族密度一致"才不会被破坏。
UV_SCALE = 0.97
FACE_SPAN = {
    "north": (0, 1), "south": (0, 1),
    "east": (2, 1), "west": (2, 1),
    "up": (0, 2), "down": (0, 2),
}


def face_uv(texture, face, lo, hi):
    """按面的真实尺寸在贴图内容区里居中取景，保证每张图的 texel 密度一致。"""
    ax, ay = FACE_SPAN[face]
    width = abs(hi[ax] - lo[ax]) * UV_SCALE
    height = abs(hi[ay] - lo[ay]) * UV_SCALE
    cx, cy, cw, ch = UV_REGIONS[texture]
    u0 = cx + (cw - width) * 0.5
    v0 = cy + (ch - height) * 0.5
    return [round(u0, 3), round(v0, 3), round(u0 + width, 3), round(v0 + height, 3)]


def box(x1, y1, z1, x2, y2, z2, texture, name=None):
    lo, hi = [x1, y1, z1], [x2, y2, z2]
    faces = {
        face: {"uv": face_uv(texture, face, lo, hi), "texture": texture}
        for face in ("north", "east", "south", "west", "up", "down")
    }
    element = {"from": [x1, y1, z1], "to": [x2, y2, z2], "faces": faces}
    if name:
        element["name"] = name
    return element


def core():
    return box(CORE_MIN, CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX, CORE_MAX, "#core", "core")


def stub(direction):
    """朝 direction 的接头：从核心外沿伸到方块边界（16），截面比核心略大。"""
    axis, sign = DIRS[direction]
    lo = [CORE_MIN, CORE_MIN, CORE_MIN]
    hi = [CORE_MAX, CORE_MAX, CORE_MAX]
    idx = {"x": 0, "y": 1, "z": 2}[axis]
    if sign > 0:
        lo[idx] = CORE_MAX
        hi[idx] = 16.0
        # 截面略微扩到 STUB 范围
        for other in range(3):
            if other != idx:
                lo[other], hi[other] = STUB_MIN, STUB_MAX
    else:
        hi[idx] = CORE_MIN
        lo[idx] = 0.0
        for other in range(3):
            if other != idx:
                lo[other], hi[other] = STUB_MIN, STUB_MAX
    return box(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2], "#connection", "stub_" + direction)


def rim(direction):
    """已连接方向末端的环。"""
    axis, sign = DIRS[direction]
    idx = {"x": 0, "y": 1, "z": 2}[axis]
    lo = [CORE_MIN - 0.6, CORE_MIN - 0.6, CORE_MIN - 0.6]
    hi = [CORE_MAX + 0.6, CORE_MAX + 0.6, CORE_MAX + 0.6]
    if sign > 0:
        lo[idx], hi[idx] = RIM_IN, RIM_OUT
    else:
        lo[idx], hi[idx] = -RIM_OUT + 16.0, 16.0 - RIM_IN
    return box(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2], "#rim", "rim_" + direction)


def write_model(path, elements, tiers_suffix):
    model = {
        "credit": "生成自 tools/gen_heat_pipe_models.py（旧版缺失，按 blockstate 补齐）",
        "texture_size": [16, 16],
        "textures": {
            "core": f"vs_clockwork:block/heatpipe_core{tiers_suffix}",
            "connection": f"vs_clockwork:block/heatpipe_connection{tiers_suffix}",
            "rim": f"vs_clockwork:block/heatpipe_rim{tiers_suffix}",
            "particle": f"vs_clockwork:block/heatpipe_core{tiers_suffix}",
        },
        "elements": elements,
    }
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(model, handle, indent=2, ensure_ascii=False)
        handle.write("\n")


def main():
    os.makedirs(BASE, exist_ok=True)
    written = 0
    for name, directions in VARIANTS.items():
        for tier in TIERS:
            elements = [core()]
            for direction in directions:
                elements.append(stub(direction))
            for direction in directions:
                elements.append(rim(direction))
            write_model(os.path.join(BASE, f"{name}{tier}.json"), elements, tier)
            written += 1
    # 直管只给"常温"一份：`blockstates/straight_duct.json` 没有 heat 属性，
    # 物品模型 `models/item/heat_pipe.json` 也是静态的；生成 *_warm/_hot/... 只会留下无人引用的死资源
    # （2026-10-17 的 RC6 提示就是这 10 个）。blockstate 里的 heat_pipe 直连形状用 ud_x/ud_y/ud_z。
    elements = [core(), stub("up"), stub("down"), rim("up"), rim("down")]
    write_model(os.path.join(BASE, "straight.json"), elements, "")
    write_model(os.path.join(BASE, "item_straight.json"), elements, "")
    written += 2
    # 幂等清理：旧版本曾为 straight/item_straight 生成过 5 档热度，重跑时删掉这些残留。
    for tier in TIERS[1:]:
        for name in (f"straight{tier}", f"item_straight{tier}"):
            stale = os.path.join(BASE, name + ".json")
            if os.path.isfile(stale):
                os.remove(stale)
    print(f"heat_pipe 模型已生成：{written} 个 → {BASE}")


if __name__ == "__main__":
    main()
