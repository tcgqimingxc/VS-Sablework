#!/usr/bin/env python3
"""从官方 Clockwork 的 CT 图集派生 ``duct_tank.png`` / ``duct_tank_top.png``（16×16）。

## 背景：两张图为什么存在

官方 ``models/block/duct_tank/block_top.json`` / ``block_bottom.json`` / ``block_middle.json``
把侧壁贴在 ``vs_clockwork:block/duct_tank``、顶/底外表面贴在 ``vs_clockwork:block/duct_tank_top``，
**但官方发布包里没有这两张 png**（只有两张 64×64 的 CT 图集）。1.20.1 上游靠
``ClockworkSpriteShifts`` 把 ``duct_tank`` 重定向到 ``duct_tank_connected``，再由
``DuctTankCTBehaviour`` + Create 的 ``AllCTTypes.RECTANGLE`` 逐面选格；本移植有同样的 CT 层
（``DuctTankModel`` + ``DuctTankCTBehaviour``），但 CT 要求模型引用的是 **16×16 基础图**
（uv 相对基础图，实际像素从图集重采样），所以必须有这两张基础图。

**注意：物品图标不再读这两张基础图。** 它们是"可平铺的一格墙"，被 ``block_single`` 的 uv
子区域裁着用，直接烘 item 会得到没有边框/铆钉的灰盒子。物品图标改用官方 ``block_single``
（64×64 图集 (0,3) 格）逐字节复制成 ``block_single_item.json``，见
``tools/gen_duct_tank_assets.py`` 文件头。本脚本的 ``check_single_model`` 会同时断言
"物品图标仍取图集 (0,3) 格" 和 "世界 block_single 仍用 16×16 基础图"。

## 第三轮：按用户在 64×64 图集上的手写标注重新裁切

用户直接在官方 CT 图集（渲染成 4×4 格）上标注了两个区域：

* ``duct_tank_top_connected.png`` —— **顶面** = 右中部那块**双层边框的大方块**
  （黄色笔），它横跨 4×4 格里 col 1..3 / row 0..2 的 **3×3 格**，实际像素矩形
  ``x 16..62, y 0..46``（47×47）：外圈 3 px 凸边 + 一圈凹槽 + 内圈 3 px 凸边 + 中间
  带 4 颗铆钉的平板。**它是"整块顶盖"贴图，不是单格。**
* ``duct_tank_connected.png`` —— **侧面** = 中左部那块**横向加强筋面板**
  （蓝色笔），即面板体 ``x 18..50, y 12..37`` 里那条加强筋周期；加强筋亮线在
  图集 y=11/27/43（周期 16），暗影带在 y=10/26/42，左右没有竖框（竖框只在列 0
  和 x≥56 的右边缘）。

### 顶面：整块盖板缩到 16×16（世界模型共用一张）

官方 3×3 罐的顶面是 CT 用 ``(1..3, 0..2)`` 这 9 格拼出来的**一整块大盖板**；
1×1 的**官方物品图标**用 ``(0,3)`` 格（16×16 的官方小盖板）。本移植的世界模型
（Create plain 几何）里，``block_single`` / ``block_top`` / ``block_bottom`` 的 up/down
都引用同一张 ``duct_tank_top.png``，所以：

* **世界里的 1×1×1** → ``block_single`` 的顶面也是这张 16×16 缩略整盖（不再是图集 ``(0,3)`` 格）；
* **物品图标** → ``block_single_item.json`` 仍取图集 ``(0,3)`` 格（官方外观，见
  ``tools/gen_duct_tank_assets.py``）。

``duct_tank_top.png`` = 用户标注的整块盖板 ``x16..47, y0..46`` **面积平均缩到 16×16**：
外圈/内圈双框、凹槽、平板、4 颗铆钉的相对位置全部保留，所以单看一块仍是"带双层边框的盖板"；
3×3 平铺时每 16 px 一道 1 px 浅框，读作**盖板拼缝**而不是"9 个方框"
（见 ``_probe_tmp/top_final.png``）。

为什么不做成"整块盖板只给 1×1、内部平板给多段"：``block_top`` 的 ``up`` 面
uv 固定 0..16，模型不可改，1×1×N 与 3×3 共用同一张图；
若选内部平板（上一轮做法）1×1×N 的顶面就退化成"一块灰板 + 4 颗铆钉"，
用户正是因此标注。缩略整盖是"两种尺寸都还能看"的唯一折中。

### 侧面：一个完整的 16 行加强筋周期（无竖框、水平无缝）

图集侧壁是 16 行一个周期的横向加强筋（亮线 y=11/27/43，暗影 10/26/42），
但只有 **x=32..47** 这一条 16 列既**没有竖框**（列 0 有 x0..3 / x13..15 两道竖框，
右边缘 x≥56 也在收暗）又**水平包裹边差 1.29**。所以取

* ``x 32..47``，``y 11..26``（16×16）：v=0 是加强筋亮线，v=15 是筋下暗影，
  中间是面板体 —— 一个完整的筋周期。

这样每 16 px 出现一条"亮筋 + 暗影"，正好对上官方图集自己的节奏（16 px 一根筋），
竖着堆叠时每个方块一根筋、横着铺时筋是连续的、块与块之间没有竖缝。
竖直包裹边差 ~49 是**设计本身**的"筋→暗影"过渡（图集下一行就是同一过渡，
脚本会核对"包裹边差 ≤ 图集自然续接边差"），不是平铺伪影。

### 单块物品图标保持不变（但它不再读这两张基础图）

物品图标 = 官方图集 (0,3) 格：``block_single_item.json`` 直接逐字节复制官方 ``block_single``
（``texture_size: [64,64]``、uv ``[0,12,4,16]``）。世界里的 ``block_single`` 则必须继续引用
本脚本生成的两张 16×16 基础图，否则 CT 匹配失败。脚本对两者分别断言。

## 为什么是 16×16、1.000 px/单位

``block_top/bottom/middle`` 的 uv 是 0..16 满铺一个面；16 像素 / 16 模型单位 = 1.000 px/单位。
``block_single_item`` 从 64×64 图集取一格（4 uv 单位 = 16 像素覆盖 16 单位）也是 1.000。
任何一边做成 64×64 满铺都会变成 4.000 px/单位，``check_assets.py [13]`` 会立刻报出来。

## 实现细节

本仓库的守门脚本要求"标准库就能跑"，所以这里自带一个只支持"8 位、非隔行"的最小 PNG
读写器（读官方图集：palette / RGB / RGBA 都行）→ 裁格/缩放 → 写 RGBA PNG。
顶盖的 47→16 缩放用**整数面积加权平均**（权重分母固定 47），不依赖 Pillow、无浮点误差，
输出字节是确定性的（zlib level 9、每行 filter 0），``--check`` 可以逐字节比对；同时核对：

* 两张产物都是 16×16、RGBA、alpha 全 255；
* 侧壁：左右包裹边差 ≤ ``TILE_EPS_H``，上下包裹边差 ≤ 图集"自然续接"边差 + 1；
* 顶面：左右/上下包裹边差 ≤ ``TILE_EPS_V``（缩略盖板的浅框差异）；
* 标注区域本身没被搞错：盖板外框在 crop 的边界上、侧壁 crop 没有竖框、v=0 是亮筋；
* 模型贴图引用全部是 ``vs_clockwork:block/*``，**不得出现 create:**；
* ``block_single_item`` 仍取 (0,3) 格（物品图标不变）；世界 ``block_single`` 必须用这两张
  16×16 基础图（CT 匹配）。

用法::

    python tools/gen_duct_tank_textures.py            # 生成/覆盖两张 16×16
    python tools/gen_duct_tank_textures.py --check    # 逐字节核对 + 平铺性/引用检查
"""

import argparse
import json
import os
import struct
import sys
import zlib

TOOLS = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(TOOLS)
PROJECT = os.path.join(ROOT, "clockwork_unoffical")
ASSETS = os.path.join(PROJECT, "src", "main", "resources", "assets", "vs_clockwork")
TEXTURE_DIR = os.path.join(ASSETS, "textures", "block")

# 官方解包树（权威来源）。工程里的图集与它逐字节相同；脚本会校验，防止"从改过的图派生"。
OFFICIAL_ROOT = os.path.join(ROOT, "assets", "vs_clockwork")

CELL_PX = 16          # 官方图集每格 16×16；多层模型的 uv 0..16 正对一整个面
ATLAS_PX = 64         # 4×4 图集

WALL_ATLAS = "duct_tank_connected"
TOP_ATLAS = "duct_tank_top_connected"

# ---------------------------------------------------------------------------
# 用户手写标注的区域（第三轮）

# 侧面：中左部"横向加强筋面板"里唯一"无竖框 + 水平无缝"的 16×16 子区。
# 图集列 0 有 x0..3 / x13..15 两道竖框（1 宽罐整格面板），右缘 x>=56 在收暗，
# 只有 x=32..47 这条是纯面板体；y=11 正是加强筋亮线（周期 16，下一根在 27/43）。
WALL_BOX = (32, 11)          # 左上角；尺寸固定 16×16 -> 像素 x 32..47, y 11..26

# 顶面：右中部"双层边框大方块"的精确外接矩形。
# 外框亮线：x=16（y1..46）、x=62（y0..45）、y=0（x17..62）、y=46（x14..61）
# => 矩形 x 16..62 / y 0..46 = 47×47，横跨 col 1..3 × row 0..2 的 3×3 格
#    （3×3 正是官方 3×3 罐顶面 CT 会选的 9 格，说明这就是"整块顶盖"）。
LID_BOX = (16, 0, 47, 47)    # x, y, w, h

# 竖框 / 亮筋自检阈值（取自图集亮度实测；见 docs/DUCT-TANK-LOOK.md）
FRAME_LUMA = 88      # 外框亮线平均亮度 >= 这个值
SHADOW_LUMA = 62     # 暗影带平均亮度 <= 这个值

# 可平铺判定
TILE_EPS_H = 4.0     # 左右包裹边差上限（竖缝必须看不见）；实测侧壁 1.29、顶面 3.31
TILE_EPS_V = 4.0     # 上下包裹边差上限（顶面缩略盖板）；实测顶面 3.15
DESIGN_SEAM_SLACK = 1.0   # 侧壁上下包裹边差允许超出"图集自然续接边差"的量

_PNG_SIG = b"\x89PNG\r\n\x1a\n"


# --------------------------------------------------------------------------- PNG 读写

def _paeth(a, b, c):
    p = a + b - c
    pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
    if pa <= pb and pa <= pc:
        return a
    if pb <= pc:
        return b
    return c


def read_png(data):
    """-> (width, height, [[(r, g, b, a), ...] 每行])，只支持 8 位、非隔行。"""
    if data[:8] != _PNG_SIG:
        raise ValueError("不是 PNG")
    pos = 8
    width = height = depth = color = interlace = None
    palette = None
    trns = b""
    idat = bytearray()
    while pos < len(data):
        if pos + 8 > len(data):
            raise ValueError("PNG 被截断")
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        ctype = data[pos + 4:pos + 8]
        payload = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if ctype == b"IHDR":
            width, height, depth, color, _comp, _filt, interlace = struct.unpack(">IIBBBBB", payload)
        elif ctype == b"PLTE":
            palette = [tuple(payload[i:i + 3]) for i in range(0, len(payload), 3)]
        elif ctype == b"tRNS":
            trns = payload
        elif ctype == b"IDAT":
            idat += payload
        elif ctype == b"IEND":
            break
    if depth != 8:
        raise ValueError(f"只支持 8 位 PNG（当前 {depth} 位）")
    if interlace != 0:
        raise ValueError("不支持隔行 PNG")
    bpp = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}.get(color)
    if bpp is None:
        raise ValueError(f"不支持的颜色类型 {color}")
    if color == 3 and palette is None:
        raise ValueError("调色板 PNG 缺 PLTE")

    raw = zlib.decompress(bytes(idat))
    stride = width * bpp
    prev = bytearray(stride)
    rows = []
    offset = 0
    for _y in range(height):
        if offset >= len(raw):
            raise ValueError("PNG 像素数据不足")
        ftype = raw[offset]
        offset += 1
        line = bytearray(raw[offset:offset + stride])
        if len(line) != stride:
            raise ValueError("PNG 像素数据不足")
        offset += stride
        if ftype == 1:
            for i in range(bpp, stride):
                line[i] = (line[i] + line[i - bpp]) & 0xFF
        elif ftype == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif ftype == 3:
            for i in range(stride):
                left = line[i - bpp] if i >= bpp else 0
                line[i] = (line[i] + ((left + prev[i]) >> 1)) & 0xFF
        elif ftype == 4:
            for i in range(stride):
                left = line[i - bpp] if i >= bpp else 0
                upleft = prev[i - bpp] if i >= bpp else 0
                line[i] = (line[i] + _paeth(left, prev[i], upleft)) & 0xFF
        elif ftype != 0:
            raise ValueError(f"非法 PNG 行过滤类型 {ftype}")

        row = []
        for x in range(width):
            px = line[x * bpp:(x + 1) * bpp]
            if color == 3:
                idx = px[0]
                if idx >= len(palette):
                    raise ValueError("调色板索引越界")
                r, g, b = palette[idx]
                a = trns[idx] if idx < len(trns) else 255
                row.append((r, g, b, a))
            elif color == 0:
                row.append((px[0], px[0], px[0], 255))
            elif color == 2:
                row.append((px[0], px[1], px[2], 255))
            elif color == 4:
                row.append((px[0], px[0], px[0], px[1]))
            else:
                row.append((px[0], px[1], px[2], px[3]))
        rows.append(row)
        prev = line
    return width, height, rows


def _chunk(tag, payload):
    return (struct.pack(">I", len(payload)) + tag + payload
            + struct.pack(">I", zlib.crc32(tag + payload) & 0xFFFFFFFF))


def write_png(width, height, rows):
    """8 位 RGBA、非隔行、每行 filter 0、zlib level 9 —— 同样输入永远得到同样字节。"""
    raw = bytearray()
    for row in rows:
        raw.append(0)
        for r, g, b, a in row:
            raw += bytes((r, g, b, a))
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    return (_PNG_SIG + _chunk(b"IHDR", ihdr)
            + _chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + _chunk(b"IEND", b""))


# --------------------------------------------------------------------------- 图集/模型工具

def _luma(px):
    """和 Minecraft/常见灰度一致的整数亮度（0..255）。"""
    return (px[0] * 299 + px[1] * 587 + px[2] * 114) // 1000


def _crop(rows, x0, y0, w, h):
    return [[rows[y][x] for x in range(x0, x0 + w)] for y in range(y0, y0 + h)]


def _cell(rows, c, r):
    return _crop(rows, c * CELL_PX, r * CELL_PX, CELL_PX, CELL_PX)


def load_atlas(name):
    """读工程内的图集，并核对它和官方树同名文件逐字节一致。"""
    src = os.path.join(TEXTURE_DIR, name + ".png")
    if not os.path.isfile(src):
        raise SystemExit(f"缺少图集 {src}")
    data = open(src, "rb").read()
    official = os.path.join(OFFICIAL_ROOT, "textures", "block", name + ".png")
    if os.path.isfile(official) and open(official, "rb").read() != data:
        raise SystemExit(f"{src} 与官方树同名文件不一致；请先恢复官方图集再派生")
    w, h, rows = read_png(data)
    if (w, h) != (ATLAS_PX, ATLAS_PX):
        raise SystemExit(f"{src}: 期望 {ATLAS_PX}x{ATLAS_PX} 的 4x4 图集，实际 {w}x{h}")
    return rows


def _uv_rect(uv, w, h):
    """uv（0..16 归一化）-> 像素矩形；用于核对 block_single 的 (0,3) 不变。"""
    return (round(uv[0] / 16.0 * w), round(uv[1] / 16.0 * h),
            round(uv[2] / 16.0 * w), round(uv[3] / 16.0 * h))


def check_single_model():
    """物品图标仍取官方图集 (0,3) 格；世界 block_single 仍用 16×16 基础图。

    两个文件是分开的：
      * ``block_single_item.json``（物品图标）→ 64×64 图集，uv 只能是 (0,3) 格；
      * ``block_single.json``（世界模型）→ 16×16 基础图 ``duct_tank``/``duct_tank_top``，
        否则 CTModel 的 sprite 匹配失败，拼接处会退回单块花纹。
    """
    item_path = os.path.join(ASSETS, "models", "block", "duct_tank", "block_single_item.json")
    if not os.path.isfile(item_path):
        raise SystemExit(f"缺少物品图标模型 {item_path}（应由 tools/gen_duct_tank_assets.py 生成）")
    with open(item_path, encoding="utf-8") as fh:
        model = json.load(fh)
    refs = model.get("textures") or {}
    if refs.get("1") != "vs_clockwork:block/" + WALL_ATLAS:
        raise SystemExit(f"block_single_item 的侧壁贴图变了：{refs.get('1')!r}（图标必须用官方图集）")
    if refs.get("0") != "vs_clockwork:block/" + TOP_ATLAS:
        raise SystemExit(f"block_single_item 的顶/底贴图变了：{refs.get('0')!r}（图标必须用官方图集）")
    if model.get("texture_size") != [ATLAS_PX, ATLAS_PX]:
        raise SystemExit(f"block_single_item 的 texture_size 变了：{model.get('texture_size')!r}")
    seen = set()
    for el in model.get("elements") or []:
        for face_dir, face in (el.get("faces") or {}).items():
            uv = face.get("uv")
            if not isinstance(uv, list) or len(uv) != 4:
                continue
            seen.add(tuple(_uv_rect(uv, ATLAS_PX, ATLAS_PX)))
    expected = (SINGLE_CELL[0] * CELL_PX, SINGLE_CELL[1] * CELL_PX,
                (SINGLE_CELL[0] + 1) * CELL_PX, (SINGLE_CELL[1] + 1) * CELL_PX)
    if seen != {expected}:
        raise SystemExit(f"block_single_item 的 uv 不再只指向 (0,3) 格：{sorted(seen)}；物品图标会变")

    world_path = os.path.join(ASSETS, "models", "block", "duct_tank", "block_single.json")
    with open(world_path, encoding="utf-8") as fh:
        world = json.load(fh)
    world_refs = world.get("textures") or {}
    if world_refs.get("1") != "vs_clockwork:block/duct_tank" \
            or world_refs.get("0") != "vs_clockwork:block/duct_tank_top":
        raise SystemExit(f"世界 block_single 必须用 16x16 基础图（当前 {world_refs!r}）；"
                         f"指向 *_connected 会让 CT 失效")
    return "物品图标 block_single_item 取图集 (0,3) 格；世界 block_single 用 16x16 基础图"


# --------------------------------------------------------------------------- 派生
# 这里只生成“多段模型”的两张 16×16 基础贴图；block_single 不读它们。

SINGLE_CELL = (0, 3)   # block_single 的 uv [0,12,4,16] -> 第 0 列第 3 行（仅用于断言）


def _row_mean(rows, y, x0=0, x1=CELL_PX):
    return sum(_luma(rows[y][x]) for x in range(x0, x1)) / float(x1 - x0)


def _col_mean(rows, x, y0=0, y1=CELL_PX):
    return sum(_luma(rows[y][x]) for y in range(y0, y1)) / float(y1 - y0)


def check_lid_box(top_rows):
    """核对"整块顶盖"矩形：四条外框线落在 crop 边界上，框外确实不是盖板。"""
    x0, y0, w, h = LID_BOX
    # 四条边必须是外框亮线；盖板外侧（左一列 / 上一行 / 右一列 / 下一行）不应同样亮。
    edges = {
        "上边 y0": _row_mean(top_rows, y0, x0 + 2, x0 + w - 2),
        "下边 y_last": _row_mean(top_rows, y0 + h - 1, x0 + 2, x0 + w - 2),
        "左边 x0": _col_mean(top_rows, x0, y0 + 2, y0 + h - 2),
        "右边 x_last": _col_mean(top_rows, x0 + w - 1, y0 + 2, y0 + h - 2),
    }
    for name, val in edges.items():
        if val < FRAME_LUMA:
            raise SystemExit(f"顶盖外框 {name} 平均亮度 {val:.1f} < {FRAME_LUMA}；LID_BOX={LID_BOX} 不对")
    outside = {
        "左侧一格": _col_mean(top_rows, x0 - 1, y0 + 2, y0 + h - 2),
        "上侧一格": _row_mean(top_rows, y0 - 1, x0 + 2, x0 + w - 2) if y0 > 0 else 0,
        "右侧一格": _col_mean(top_rows, x0 + w, y0 + 2, y0 + h - 2),
        "下侧一格": _row_mean(top_rows, y0 + h, x0 + 2, x0 + w - 2),
    }
    for name, val in outside.items():
        if val >= FRAME_LUMA:
            raise SystemExit(f"顶盖外框外 {name} 亮度 {val:.1f} >= {FRAME_LUMA}；LID_BOX={LID_BOX} 不是完整外框")
    # 内圈（第二道框）必须在：内侧 8..10 px 处还有一条亮线。
    inner = _row_mean(top_rows, y0 + 8, x0 + 8, x0 + w - 8)
    if inner < FRAME_LUMA:
        raise SystemExit(f"顶盖内圈亮线缺失（y0+8 平均亮度 {inner:.1f}）；这不是双层边框盖板")


def check_wall_box(wall_rows):
    """核对侧面 crop：左右都没有竖框、v=0 是亮筋、v=15 是暗影。"""
    x0, y0 = WALL_BOX
    cell = _crop(wall_rows, x0, y0, CELL_PX, CELL_PX)
    left = sum(_luma(cell[y][0]) for y in range(CELL_PX)) / CELL_PX
    right = sum(_luma(cell[y][CELL_PX - 1]) for y in range(CELL_PX)) / CELL_PX
    inner = sum(_luma(cell[y][x]) for y in range(CELL_PX) for x in range(4, 12)) / float(CELL_PX * 8)
    if left - inner > 6 or right - inner > 6:
        raise SystemExit(f"侧面 crop 左右有竖框（左 {left:.1f} / 右 {right:.1f} vs 内部 {inner:.1f}）")
    top = sum(_luma(cell[0][x]) for x in range(CELL_PX)) / CELL_PX
    bottom = sum(_luma(cell[CELL_PX - 1][x]) for x in range(CELL_PX)) / CELL_PX
    if top < FRAME_LUMA:
        raise SystemExit(f"侧面 crop 第一行不是加强筋亮线（{top:.1f} < {FRAME_LUMA}）；WALL_BOX={WALL_BOX}")
    if bottom > SHADOW_LUMA:
        raise SystemExit(f"侧面 crop 最后一行不是筋下暗影（{bottom:.1f} > {SHADOW_LUMA}）；WALL_BOX={WALL_BOX}")


def design_seam_v(wall_rows):
    """图集在 crop 正下方"自然续接"时的平均行差（侧壁 16 行周期的真实过渡）。"""
    x0, y0 = WALL_BOX
    y_next = y0 + CELL_PX
    if y_next >= ATLAS_PX:
        return 0.0
    total = 0.0
    for x in range(x0, x0 + CELL_PX):
        a, b = wall_rows[y_next - 1][x], wall_rows[y_next][x]
        total += sum(abs(a[i] - b[i]) for i in range(3)) / 3.0
    return total / CELL_PX


def derive_wall(wall_rows):
    """侧面：一个完整的加强筋周期（x32..47, y11..26），无竖框、水平无缝。"""
    return _crop(wall_rows, WALL_BOX[0], WALL_BOX[1], CELL_PX, CELL_PX)


def _downscale_area(rows, x0, y0, w, h, out=CELL_PX):
    """整数面积加权平均缩放到 out×out。

    输出像素 i 覆盖源区间 [i*w, (i+1)*w)（单位 1/out 像素）；源像素 s 覆盖
    [s*out, (s+1)*out)。权重 = 重叠长度，总权重 = w*out / out = w。
    权重分母固定 w，全整数运算 -> 同样的输入永远得到同样的输出。
    """
    cols = []   # 每个输出列 -> [(源列, 权重)]
    for i in range(out):
        lo, hi = i * w, (i + 1) * w
        spans = []
        for s in range(w):
            a, b = s * out, (s + 1) * out
            ov = min(hi, b) - max(lo, a)
            if ov > 0:
                spans.append((s, ov))
        cols.append(spans)
    assert all(sum(wt for _s, wt in spans) == w for spans in cols), "列权重和不为 w"

    tmp = []    # 先横向缩到 out×h
    for y in range(y0, y0 + h):
        row = []
        for spans in cols:
            acc = [0, 0, 0]
            for s, wt in spans:
                px = rows[y][x0 + s]
                for k in range(3):
                    acc[k] += px[k] * wt
            row.append([v // w for v in acc])
        tmp.append(row)

    out_rows = []
    for j in range(out):
        lo, hi = j * h, (j + 1) * h
        spans = []
        for s in range(h):
            a, b = s * out, (s + 1) * out
            ov = min(hi, b) - max(lo, a)
            if ov > 0:
                spans.append((s, ov))
        assert sum(wt for _s, wt in spans) == h, "行权重和不为 h"
        row = []
        for i in range(out):
            acc = [0, 0, 0]
            for s, wt in spans:
                px = tmp[s][i]
                for k in range(3):
                    acc[k] += px[k] * wt
            row.append((acc[0] // h, acc[1] // h, acc[2] // h, 255))
        out_rows.append(row)
    return out_rows


def derive_top(top_rows):
    """顶面：用户标注的"整块双层边框盖板"面积平均缩到 16×16。"""
    x0, y0, w, h = LID_BOX
    return _downscale_area(top_rows, x0, y0, w, h)


def derive():
    wall_rows = load_atlas(WALL_ATLAS)
    top_rows = load_atlas(TOP_ATLAS)
    check_wall_box(wall_rows)
    check_lid_box(top_rows)
    return derive_wall(wall_rows), derive_top(top_rows)


# --------------------------------------------------------------------------- 验收指标

def tile_metrics(rows):
    """包裹边差 vs 内部相邻差；用于证明可无缝平铺。"""
    n = len(rows)
    m = len(rows[0])

    def diff(a, b):
        return sum(abs(a[i] - b[i]) for i in range(3)) / 3.0

    wrap_h = sum(diff(rows[y][m - 1], rows[y][0]) for y in range(n)) / n
    wrap_v = sum(diff(rows[n - 1][x], rows[0][x]) for x in range(m)) / m
    adj_h = sum(diff(rows[y][x], rows[y][x + 1])
                for y in range(n) for x in range(m - 1)) / (n * (m - 1))
    adj_v = sum(diff(rows[y][x], rows[y + 1][x])
                for x in range(m) for y in range(n - 1)) / (m * (n - 1))
    border = sum(_luma(rows[y][x]) for y in range(n) for x in range(m)
                 if x in (0, m - 1) or y in (0, n - 1))
    border /= (2 * n + 2 * m - 4)
    inner = sum(_luma(rows[y][x]) for y in range(4, n - 4) for x in range(4, m - 4))
    inner /= (n - 8) * (m - 8)
    return {
        "wrap_h": wrap_h, "wrap_v": wrap_v, "adj_h": adj_h, "adj_v": adj_v,
        "border_lum": border, "inner_lum": inner,
    }


def check_tileable(out_name, rows, v_limit):
    if len(rows) != CELL_PX or len(rows[0]) != CELL_PX:
        return [f"{out_name}: 不是 {CELL_PX}x{CELL_PX}"]
    problems = []
    for y in range(CELL_PX):
        for x in range(CELL_PX):
            if rows[y][x][3] != 255:
                problems.append(f"{out_name}: ({x},{y}) alpha != 255")
                break
        if problems:
            break
    m = tile_metrics(rows)
    if m["wrap_h"] > TILE_EPS_H:
        problems.append(f"{out_name}: 左右包裹边差 {m['wrap_h']:.2f} > {TILE_EPS_H}（不能无缝平铺）")
    if m["wrap_v"] > v_limit:
        problems.append(f"{out_name}: 上下包裹边差 {m['wrap_v']:.2f} > {v_limit:.2f}"
                        f"（超过图集自然续接边差，会出现平铺伪影）")
    return problems


def _model_texture_refs(model_name, base=ASSETS):
    path = os.path.join(base, "models", "block", "duct_tank", model_name + ".json")
    if not os.path.isfile(path):
        raise SystemExit(f"缺少模型 {path}")
    with open(path, encoding="utf-8") as fh:
        model = json.load(fh)
    return path, model


def check_model_refs():
    """多段模型必须只引用 vs_clockwork:block/*，不得出现 create:。"""
    problems = []
    for name in ("block_single", "block_top", "block_bottom", "block_middle"):
        path, model = _model_texture_refs(name)
        for key, ref in (model.get("textures") or {}).items():
            if not isinstance(ref, str) or ref.startswith("#"):
                continue
            if "create:" in ref or not ref.startswith("vs_clockwork:block/"):
                problems.append(f"{os.path.basename(path)} 的 textures[{key!r}] = {ref!r}"
                                f"（必须 vs_clockwork:block/*，不得 create:）")
        text = open(path, encoding="utf-8", errors="replace").read()
        if "create:" in text:
            problems.append(f"{os.path.basename(path)} 含 create: 字样")
    # 多段模型必须真的指向这两张基础贴图（否则改了也没用）。
    for name in ("block_top", "block_bottom", "block_middle"):
        _path, model = _model_texture_refs(name)
        refs = model.get("textures") or {}
        if refs.get("1") != "vs_clockwork:block/duct_tank":
            problems.append(f"{name}.json 的 #1 不是 vs_clockwork:block/duct_tank（{refs.get('1')!r}）")
    for name in ("block_top", "block_bottom"):
        _path, model = _model_texture_refs(name)
        refs = model.get("textures") or {}
        if refs.get("0") != "vs_clockwork:block/duct_tank_top":
            problems.append(f"{name}.json 的 #0 不是 vs_clockwork:block/duct_tank_top（{refs.get('0')!r}）")
    return problems


def build():
    """-> [(绝对路径, 期望字节, 说明)]，顺序稳定，供 --check 复用。"""
    wall, top = derive()
    x0, y0 = WALL_BOX
    lx, ly, lw, lh = LID_BOX
    return [
        (os.path.join(TEXTURE_DIR, "duct_tank.png"),
         write_png(CELL_PX, CELL_PX, wall),
         f"{WALL_ATLAS}.png 的 ({x0},{y0})..({x0+CELL_PX},{y0+CELL_PX})（横向加强筋面板里"
         f"唯一无竖框、水平无缝的一格；v0=亮筋，v15=暗影）"),
        (os.path.join(TEXTURE_DIR, "duct_tank_top.png"),
         write_png(CELL_PX, CELL_PX, top),
         f"{TOP_ATLAS}.png 的整块顶盖 ({lx},{ly}) {lw}x{lh} 面积平均缩到 16x16"
         f"（双层边框 + 平板 + 4 颗铆钉）"),
    ]


def _print_metrics(name, rows):
    m = tile_metrics(rows)
    print(f"  {name}: 包裹边差 H={m['wrap_h']:.2f} V={m['wrap_v']:.2f} "
          f"（内部相邻 H={m['adj_h']:.2f} V={m['adj_v']:.2f}）; "
          f"边缘亮度 {m['border_lum']:.1f} vs 内部 {m['inner_lum']:.1f}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true",
                    help="只核对产物是否与图集派生结果逐字节一致，并验证可平铺/引用")
    args = ap.parse_args()

    single_note = check_single_model()
    items = build()
    wall_rows = read_png(items[0][1])[2]
    top_rows = read_png(items[1][1])[2]
    metrics = (("duct_tank.png", wall_rows), ("duct_tank_top.png", top_rows))

    seam = design_seam_v(load_atlas(WALL_ATLAS))
    v_limits = (max(TILE_EPS_V, seam + DESIGN_SEAM_SLACK), TILE_EPS_V)

    if args.check:
        problems = []
        for path, expected, note in items:
            rel = os.path.relpath(path, ROOT)
            if not os.path.isfile(path):
                problems.append(f"缺失 {rel}（应来自 {note}）")
                continue
            actual = open(path, "rb").read()
            if actual != expected:
                problems.append(f"与图集派生结果不一致 {rel}（应来自 {note}）")
                continue
            w, h, rows = read_png(actual)
            if (w, h) != (CELL_PX, CELL_PX):
                problems.append(f"{rel}: 不是 {CELL_PX}x{CELL_PX}（{w}x{h}）")
        for (name, rows), v_limit in zip(metrics, v_limits):
            problems.extend(check_tileable(name, rows, v_limit))
        problems.extend(check_model_refs())
        print("duct_tank 基础贴图（多段模型用）核对：")
        print("  " + single_note)
        print(f"  标注区域：顶面 {TOP_ATLAS} 整盖 x{LID_BOX[0]}..{LID_BOX[0]+LID_BOX[2]-1}"
              f" y{LID_BOX[1]}..{LID_BOX[1]+LID_BOX[3]-1}（{LID_BOX[2]}x{LID_BOX[3]}）；"
              f"侧面 {WALL_ATLAS} x{WALL_BOX[0]}..{WALL_BOX[0]+CELL_PX-1}"
              f" y{WALL_BOX[1]}..{WALL_BOX[1]+CELL_PX-1}")
        for (name, rows), v_limit in zip(metrics, v_limits):
            _print_metrics(name, rows)
            print(f"    -> 上下包裹边差上限 {v_limit:.2f}"
                  f"（{'图集自然续接 ' + format(seam, '.2f') + ' + 1' if v_limit > TILE_EPS_V else '缩略盖板阈值'}）")
        if problems:
            print("未通过：")
            for p in problems:
                print("  " + p)
            print("重跑 python tools/gen_duct_tank_textures.py")
            return 1
        print(f"通过：2 张 {CELL_PX}x{CELL_PX} RGBA 图与图集派生结果逐字节一致；"
              f"左右包裹边差均 <= {TILE_EPS_H}；上下包裹边差在阈值内；"
              f"模型引用全部 vs_clockwork:block/*（无 create:）。")
        return 0

    for path, data, note in items:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "wb") as fh:
            fh.write(data)
        print("wrote", os.path.relpath(path, ROOT), "<-", note)
    for (name, rows), v_limit in zip(metrics, v_limits):
        _print_metrics(name, rows)
        print(f"    -> 上下包裹边差上限 {v_limit:.2f}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
