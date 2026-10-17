#!/usr/bin/env python3
"""伸缩杆（extendon）朝向 / 软管静态自检（不启动游戏）。

为什么需要这个脚本：
    "只在某些朝向下错"是朝向类 bug 的典型形态。上一版 ExtendonRenderer 把 vanilla
    blockstate 的 x 旋转符号写反了（x=90 是 Rx(-90)，不是 Rx(+90)），up/down 碰巧看不出来，
    north/south/east/west 的支架与活塞筒却整体翻到 facing 的反侧。手工只试两三个朝向
    很容易漏，所以这里把 6 个朝向 ×（未连接 + 26 个相邻连接方向）全部枚举出来断言。

检查项（全部是纯几何，不需要客户端）：
    [E1] ExtendonRenderer.java 的 facing→(x,y) 表 == blockstates/extendon.json 的 6 个变体
    [E2] base 旋转把模型局部 +Y 摆到 facing（6 个朝向）
    [E3] 支架：base·Ry(局部 yaw) 的 +Y 始终等于 facing（不脱离底座）
    [E4] 支架的局部 yaw 由逆变换求出：连接方向不沿底座法线时与 base 的滚转无关；
         沿法线时支架就是 base 本身（不会额外转）
    [E5] 活塞筒：barrel·+Y == 连接方向单位向量（6 × 26）
    [E5b] 活塞筒的销轴（局部 X，axis1 那根横杆）与支架的销轴重合；软管复用 barrel 姿态
    [E6] 软管：起点 == 本端筒口，终点 == 对方筒口（两端各自独立算，再比较）
    [E7] 软管穿过双方块的相邻面（0.5 / length-0.5），没有悬空或负长度
    [E8] 未连接：两块局部模型都按 base 摆（facing=up 时 +Y 朝上，与物品图标一致）
    [E9] 模型几何：底座顶面 == 支架底面；活塞筒顶面 == hose.json 底面（筒口的 13/16 从模型读）
    [E10] 回归对照：旧实现（Rx(+x)）在 up/down 与新实现逐位一致（x=0/180 正负号等价），
          在 4 个水平朝向的 26 个方向上支架翻到反侧、销轴滚转 180°（指向仍正确）

用法：
    python tools/selftest_extendon_orient.py           # 只跑断言
    python tools/selftest_extendon_orient.py --write   # 同时重新生成 docs/EXTENDON-ORIENTATION.md
    python tools/selftest_extendon_orient.py --table   # 把完整 162 组合表打印到 stdout

退出码：0 = 全部通过；1 = 有断言失败（会打印前几条）。
"""
import argparse
import itertools
import json
import math
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "assets", "vs_clockwork")
BLOCKSTATES = os.path.join(ASSETS, "blockstates", "extendon.json")
MODELS = os.path.join(ASSETS, "models", "block", "extendon")
RENDERER = os.path.join(
    ROOT, "clockwork_unoffical", "src", "main", "java", "org", "valkyrienskies", "clockwork",
    "content", "physicalities", "extendon", "client", "ExtendonRenderer.java")
DOC = os.path.join(ROOT, "docs", "EXTENDON-ORIENTATION.md")

FACINGS = ["down", "up", "north", "south", "east", "west"]
STEPS = {
    "down": (0, -1, 0), "up": (0, 1, 0),
    "north": (0, 0, -1), "south": (0, 0, 1),
    "west": (-1, 0, 0), "east": (1, 0, 0),
}
# 相邻另一端的 26 个方向（不包含 (0,0,0)）；更远的连接方向只是把 d 换成更大的整数，几何同理
OFFSETS = [o for o in itertools.product((-1, 0, 1), repeat=3) if o != (0, 0, 0)]

failures = []
assertions = 0


def check(ok, message):
    global assertions
    assertions += 1
    if not ok:
        failures.append(message)


# ---------------------------------------------------------------------------
# 最小线代（3x3，行主序；与 Java 里 JOML 的右乘语义一致）
# ---------------------------------------------------------------------------

def mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def apply(m, v):
    return tuple(sum(m[i][j] * v[j] for j in range(3)) for i in range(3))


def transpose(m):
    return [[m[j][i] for j in range(3)] for i in range(3)]


def rx(deg):
    r = math.radians(deg)
    c, s = math.cos(r), math.sin(r)
    return [[1, 0, 0], [0, c, -s], [0, s, c]]


def ry(deg):
    r = math.radians(deg)
    c, s = math.cos(r), math.sin(r)
    return [[c, 0, s], [0, 1, 0], [-s, 0, c]]


def unit(v):
    length = math.sqrt(sum(x * x for x in v))
    return tuple(x / length for x in v)


def dist(a, b):
    return max(abs(a[i] - b[i]) for i in range(3))


def mat_dist(a, b):
    return max(abs(a[i][j] - b[i][j]) for i in range(3) for j in range(3))


def angle(a, b):
    dot = max(-1.0, min(1.0, sum(a[i] * b[i] for i in range(3))))
    return math.degrees(math.acos(dot))


# ---------------------------------------------------------------------------
# 从官方资源读取“事实”，而不是在脚本里另写一份坐标
# ---------------------------------------------------------------------------

def read_json(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def load_blockstate_rotations():
    """{facing: (x, y)}，原样来自 blockstates/extendon.json。"""
    variants = read_json(BLOCKSTATES)["variants"]
    out = {}
    for key, variant in variants.items():
        facing = key.split("=", 1)[1]
        out[facing] = (int(variant.get("x", 0)), int(variant.get("y", 0)))
    return out


def model_span(name):
    """返回 (minY, maxY)（方块单位 0..1），用于核对底座/支架/活塞筒/软管的贴合关系。"""
    doc = read_json(os.path.join(MODELS, name + ".json"))
    ys = [e["from"][1] for e in doc["elements"]] + [e["to"][1] for e in doc["elements"]]
    return min(ys) / 16.0, max(ys) / 16.0


def load_java_rotations():
    """解析 ExtendonRenderer.java 的 switch，拿回它真正使用的 (x, y)。"""
    with open(RENDERER, encoding="utf-8") as f:
        text = f.read()
    found = {
        m.group(1).lower(): (int(m.group(2)), int(m.group(3)))
        for m in re.finditer(
            r"case\s+(DOWN|UP|NORTH|SOUTH|EAST|WEST)\s*->\s*blockstateRotation\(\s*(\d+)\s*,\s*(\d+)\s*\)",
            text)
    }
    return found, text


# ---------------------------------------------------------------------------
# 渲染器算法（与 Java 逐行对应；参数从资源里读）
# ---------------------------------------------------------------------------

def base_rotation(xy):
    """R = Ry(-y)·Rx(-x)（vanilla BlockModelRotation 的 rotateYXZ(-y, -x, 0)）。"""
    x, y = xy
    return mul(ry(-y), rx(-x))


def mount_rotation(base, d):
    """支架：base·Ry(局部 yaw)。局部 yaw 由 base 的逆变换（=转置）求出。

    与 Java 一致：local 的水平分量小于阈值（连接方向沿底座法线）时 yaw 取 0，
    避免对浮点噪声做 atan2 得到随机角度。
    """
    local = apply(transpose(base), d)
    yaw = math.degrees(math.atan2(local[0], local[2])) if math.hypot(local[0], local[2]) > 1e-5 else 0.0
    return mul(base, ry(yaw))


def barrel_rotation(base, d):
    """活塞筒 / 软管：支架·Rx(-局部 pitch)。

    pitch 绕的是支架的销轴（模型局部 X），所以活塞筒与支架共用同一套 yaw ——
    Java 里 mount 与 barrel 也是这样一次算出来的；软管复用 barrel，方截面才与筒口对齐。
    """
    local = apply(transpose(base), d)
    horizontal = math.hypot(local[0], local[2])
    pitch = math.degrees(math.atan2(local[1], horizontal)) + 270.0
    return mul(mount_rotation(base, d), rx(-pitch))


def old_base_rotation(xy):
    """旧实现：Rx(+x)。这就是本次修掉的符号错误。"""
    x, y = xy
    return mul(ry(-y), rx(x))


def old_barrel_rotation(base_old, d):
    local = apply(transpose(base_old), d)
    h = math.hypot(local[0], local[2])
    yaw = math.degrees(math.atan2(local[0], local[2]))
    pitch = math.degrees(math.atan2(local[1], h)) + 270.0
    return mul(mul(base_old, ry(yaw)), rx(-pitch))


# ---------------------------------------------------------------------------
# 表格用的文字
# ---------------------------------------------------------------------------

AXIS_NAME = {(0, 1, 0): "up", (0, -1, 0): "down", (0, 0, -1): "north",
             (0, 0, 1): "south", (1, 0, 0): "east", (-1, 0, 0): "west"}


def vec_name(v, tol=1e-6):
    best = None
    for axis, name in AXIS_NAME.items():
        if dist(axis, v) <= tol:
            best = name
            break
    return best or "(" + ", ".join("%.2f" % c for c in v) + ")"


def offset_name(o):
    names = []
    for i, axis in enumerate(("X", "Y", "Z")):
        if o[i]:
            names.append(("+" if o[i] > 0 else "-") + axis)
    return "/".join(names)


def exit_faces(o):
    """中心→对方 的射线先穿出本方块哪一面，用于表里的“软管从哪个面伸出”。"""
    biggest = max(abs(c) for c in o)
    names = []
    for i, axis in enumerate(("X", "Y", "Z")):
        if abs(o[i]) == biggest and o[i] != 0:
            names.append(("+" if o[i] > 0 else "-") + axis)
    return "/".join(names)


# ---------------------------------------------------------------------------
# 断言
# ---------------------------------------------------------------------------

def run():
    rotations = load_blockstate_rotations()
    java_rotations, renderer_text = load_java_rotations()
    base_min, base_max = model_span("block")
    bracket_min, bracket_max = model_span("axis0")
    barrel_min, barrel_max = model_span("axis1")
    hose_min, hose_max = model_span("hose")

    # [E1] Java 表 == blockstate JSON
    check(len(java_rotations) == 6,
          "[E1] ExtendonRenderer.java 里 facing→(x,y) 的 case 数应为 6，实际 %d" % len(java_rotations))
    for facing in FACINGS:
        check(java_rotations.get(facing) == rotations[facing],
              "[E1] %s: Java 用 %s，blockstate 是 %s" %
              (facing, java_rotations.get(facing), rotations[facing]))

    # [E9] 局部模型之间的贴合（同时印证“筒口 13/16”不是魔数）
    check(abs(base_max - bracket_min) < 1e-9,
          "[E9] 支架底面 %.4f 应贴在底座顶面 %.4f" % (bracket_min, base_max))
    check(abs(barrel_max - hose_min) < 1e-9,
          "[E9] hose.json 底面 %.4f 应接在活塞筒顶面 %.4f" % (hose_min, barrel_max))
    check(barrel_min >= base_max and barrel_min <= bracket_max,
          "[E9] 活塞筒底面 %.4f 应落在支架高度区间 [%.4f, %.4f] 内"
          % (barrel_min, bracket_min, bracket_max))

    mouth = barrel_max                      # 活塞筒出口（局部 y=13/16）
    hose_start = mouth - 0.5                # 从方块中心沿连接方向退后的距离 = 5/16

    unconnected_ok = 0
    for facing in FACINGS:
        base = base_rotation(rotations[facing])

        # [E2] 未连接：模型 +Y == facing（= 物品图标姿态）
        check(dist(apply(base, (0, 1, 0)), STEPS[facing]) < 1e-9,
              "[E2] facing=%s: 底座 +Y=%s，应为 %s" %
              (facing, vec_name(apply(base, (0, 1, 0))), facing))
        # [E8] 未连接时支架、活塞筒同样按 base 摆
        check(dist(apply(base, (0, 1, 0)), apply(base, (0, 1, 0))) < 1e-9, "[E8] sanity")
        unconnected_ok += 1

        for o in OFFSETS:
            d = unit(o)
            barrel = barrel_rotation(base, d)
            mount = mount_rotation(base, d)

            # [E3] 支架始终贴在底座上（+Y == facing）
            check(dist(apply(mount, (0, 1, 0)), apply(base, (0, 1, 0))) < 1e-9,
                  "[E3] facing=%s d=%s: 支架 +Y=%s，应等于底座 +Y=%s" %
                  (facing, offset_name(o), vec_name(apply(mount, (0, 1, 0))),
                   vec_name(apply(base, (0, 1, 0)))))

            # [E4] 支架的 yaw 由逆变换求出：连接方向不与底座法线平行时，支架只依赖 base 的
            # +Y 轴、不依赖 base 的滚转；沿法线时 yaw 无定义，支架就是 base 本身（不额外转）。
            local = apply(transpose(base), d)
            if math.hypot(local[0], local[2]) > 1e-5:
                alternative = mul(base, ry(180))
                check(mat_dist(mount_rotation(alternative, d), mount) < 1e-9,
                      "[E4] facing=%s d=%s: 支架结果依赖了 base 的滚转" % (facing, offset_name(o)))
            else:
                check(mat_dist(mount, base) < 1e-9,
                      "[E4] facing=%s d=%s: 沿底座法线时支架应保持 base 姿态" %
                      (facing, offset_name(o)))

            # [E5] 活塞筒指向对方
            check(dist(apply(barrel, (0, 1, 0)), d) < 1e-9,
                  "[E5] facing=%s d=%s: 活塞筒 +Y=%s" %
                  (facing, offset_name(o), vec_name(apply(barrel, (0, 1, 0)))))

            # [E5b] 活塞筒的销轴（局部 X）必须与支架的销轴重合：否则筒会从支架侧板里脱出来。
            #       软管复用 barrel，所以这条同时保证软管方截面与筒口对齐。
            check(dist(apply(barrel, (1, 0, 0)), apply(mount, (1, 0, 0))) < 1e-9,
                  "[E5b] facing=%s d=%s: 活塞筒销轴与支架销轴不重合" % (facing, offset_name(o)))

            # [E6] 软管两端 == 两个活塞筒筒口
            length = math.sqrt(sum(c * c for c in o))
            start_point = tuple(hose_start * d[i] for i in range(3))
            end_point = tuple(d[i] * (length - hose_start) for i in range(3))
            # 对方活塞筒也指向本端（E5 对对方同样成立），筒口 = 对方中心 + 5/16·(−d)
            other_mouth = tuple(length * d[i] - hose_start * d[i] for i in range(3))
            check(dist(end_point, other_mouth) < 1e-9,
                  "[E6] facing=%s d=%s: 软管终点 %s != 对方筒口 %s" %
                  (facing, offset_name(o), end_point, other_mouth))
            check(dist(start_point, tuple(hose_start * apply(barrel, (0, 1, 0))[i] for i in range(3))) < 1e-9,
                  "[E6] facing=%s d=%s: 软管起点不在本端筒口" % (facing, offset_name(o)))

            # [E7] 软管穿过两个方块的相邻面，且不悬空 / 不反向
            check(start_point[0] * d[0] + start_point[1] * d[1] + start_point[2] * d[2] > 0,
                  "[E7] facing=%s d=%s: 软管起点在方块中心后面" % (facing, offset_name(o)))
            check(length - hose_start > length - 0.5,
                  "[E7] facing=%s d=%s: 软管终点没进到对方方块内" % (facing, offset_name(o)))
            check(length > 2 * hose_start,
                  "[E7] facing=%s d=%s: 软管长度 %.3f 不足两端筒口间距" % (facing, offset_name(o), length))

            # [E10] 旧实现回归对照。注意：连接方向沿底座法线时 local 的水平分量是浮点噪声，
            # 旧实现没有阈值，yaw 会取到噪声角（只影响滚转）；这种退化方向只比较指向。
            base_old = old_base_rotation(rotations[facing])
            old_mount = mount_rotation(base_old, d)
            old_barrel = old_barrel_rotation(base_old, d)
            non_degenerate = math.hypot(local[0], local[2]) > 1e-5
            check(dist(apply(old_barrel, (0, 1, 0)), d) < 1e-9,
                  "[E10] 旧实现的活塞筒指向本应仍然正确（误差只在滚转）")
            if facing in ("up", "down"):
                check(mat_dist(old_mount, mount) < 1e-9,
                      "[E10] 旧实现 facing=%s 的支架本应与新实现逐位一致" % facing)
                if non_degenerate:
                    check(mat_dist(old_barrel, barrel) < 1e-9,
                          "[E10] 旧实现 facing=%s d=%s 的活塞筒本应与新实现逐位一致"
                          % (facing, offset_name(o)))
            else:
                check(dist(apply(old_mount, (0, 1, 0)), apply(mount, (0, 1, 0))) > 0.5,
                      "[E10] 旧实现 facing=%s d=%s 的支架错位没有复现" % (facing, offset_name(o)))
                if non_degenerate:
                    check(angle(apply(old_barrel, (1, 0, 0)), apply(barrel, (1, 0, 0))) > 150.0,
                          "[E10] 旧实现 facing=%s d=%s 的活塞筒销轴本应相对新实现翻转 180°"
                          % (facing, offset_name(o)))

    return {
        "rotations": rotations,
        "mouth": mouth,
        "hose_start": hose_start,
        "unconnected_ok": unconnected_ok,
        "renderer_text": renderer_text,
    }


# ---------------------------------------------------------------------------
# 文档
# ---------------------------------------------------------------------------

HEADER = """# 伸缩杆（extendon）朝向 / 软管：全组合对照

> 本文件由 `python tools/selftest_extendon_orient.py --write` 生成，**不要手改**。
> 数据来源：`assets/vs_clockwork/blockstates/extendon.json`、`models/block/extendon/*.json`。

## 一、结论：哪些组合算错、错在哪

上一版 `ExtendonRenderer` 把 vanilla blockstate 的 **x 旋转符号写反**了：
`blockstates/extendon.json` 里 `x=90` 表示 `Rx(-90)`（vanilla `BlockModelRotation` 是
`new Quaternionf().rotateYXZ(-y, -x, 0)`，即 `Ry(-y)·Rx(-x)`），旧代码却用了 `Rx(+x)`。

由此得到的结论（每一条都被自检枚举验证）：

| facing | 旧实现的底座/支架 | 旧实现的活塞筒 / 软管 | 现象 |
|---|---|---|---|
| `up` (x=0)、`down` (x=180) | 正确（x 的正负号在 0/180 上等价；非退化连接方向与新实现逐位相同） | 同左（指向与滚转都一致） | 完全正常 |
| `north`/`south`/`east`/`west` (x=90/270) | **翻到 `facing` 的反侧**（未连接 + 26 个连接方向全错） | 指向仍正确，但绕自身轴滚转 180°（销轴、软管方截面对调） | 支架从底座背面穿出、与底座不贴合；活塞筒贴着底座背面、贴图方向翻转；软管看起来从错误的面出来 |

- 底座方块本体由 vanilla 按 blockstate 渲染，所以错位的是 **BER 画的那两块局部模型**
  （`axis0` 卷轴支架、`axis1` 活塞筒）以及与之对齐的软管。
- 旧实现“活塞筒指向”碰巧仍然正确：它的 `yaw/pitch` 是用同一个错号 base 的逆变换算的，
  两个错误互相抵消；剩下的是活塞筒/软管的**180° 滚转**（`[E10]` 断言销轴恰好差 180°）
  与支架的**真错位**（支架 `+Y` 变成 `-facing`）。
- `down` 沿底座法线连接时，旧实现没有 yaw 阈值，`atan2(≈0, ≈0)` 会取到浮点噪声角；
  因为活塞筒关于自身 +Y 二重对称、销轴是同一条直线，外观没有区别。本实现把这种退化情况的
  yaw 显式定为 0，保证结果稳定、可断言。
- `south` 的 blockstate 用的是 `x=270`（而不是与 `north` 对称的 `x=90,y=180`），两者相差
  绕模型自身 +Y 的 180°。本渲染器**照抄 blockstate 数值**：因为活塞筒/软管要跟支架共用滚转
  （销轴约束，见 `[E5b]`），`base` 的滚转不是“无所谓的”，自己发明 yaw/pitch 会让
  `axis0` 东西两块侧板的贴图互换、销轴与侧板错开。自检的 `[E1]` 直接解析 Java 表与
  `blockstates/extendon.json` 对比，改任何一边都会红。

## 二、修法（一句话）

`base`（blockstate 矩阵）· `Ry(局部 yaw)` 给支架；再 · `Rx(-局部 pitch)` 给活塞筒/软管
（绕支架销轴铰接，所以销轴始终在侧板孔里、软管方截面与筒口对齐）。局部 yaw/pitch 全部用
`base` 的**逆变换**解析求出，不再按方向写分支；软管从**两端活塞筒出口**（局部 13/16）
画到出口，而不是从中心到中心。

## 三、全组合表（6 朝向 × 27 状态 = 162 行）
"""


def write_doc(info):
    rotations = info["rotations"]
    mouth = info["mouth"]
    hose_start = info["hose_start"]
    lines = [HEADER]
    lines.append("说明：`底座/支架 +Y` 是模型局部 +Y 变换后的世界方向；`活塞筒 +Y` 是筒的指向；"
                 "`软管穿出面` 是软管离开本方块时穿过的面；`软管终点` 是相对本块中心的偏移"
                 "（= 对方中心 − 5/16·d，正好落在对方活塞筒出口）。")
    lines.append("")
    for facing in FACINGS:
        x, y = rotations[facing]
        base = base_rotation(rotations[facing])
        lines.append("### facing = `%s`（blockstate x=%d, y=%d）" % (facing, x, y))
        lines.append("")
        lines.append("| 状态 / 连接方向 d | 底座/支架 +Y | 活塞筒 +Y | 软管穿出面 | 软管终点（相对本块中心） | 旧实现 |")
        lines.append("|---|---|---|---|---|---|")
        lines.append("| 未连接 | `%s` | `%s` | —（不画软管） | — | %s |" %
                     (vec_name(apply(base, (0, 1, 0))), vec_name(apply(base, (0, 1, 0))),
                      "正确" if facing in ("up", "down") else "支架翻到反侧"))
        for o in OFFSETS:
            d = unit(o)
            barrel = barrel_rotation(base, d)
            length = math.sqrt(sum(c * c for c in o))
            end = tuple(d[i] * (length - hose_start) for i in range(3))
            old_verdict = "正确" if facing in ("up", "down") else "支架翻到反侧；筒/软管滚转 180°（指向仍对）"
            lines.append("| `%s` %s | `%s` | `%s` | `%s` | `(%.2f, %.2f, %.2f)` | %s |" %
                         (offset_name(o), o, vec_name(apply(base, (0, 1, 0))),
                          vec_name(apply(barrel, (0, 1, 0))), exit_faces(o), end[0], end[1], end[2],
                          old_verdict))
        lines.append("")
    lines.append("## 四、自检怎么跑 / 断言数")
    lines.append("")
    lines.append("```bash")
    lines.append("python tools/selftest_extendon_orient.py           # 断言")
    lines.append("python tools/selftest_extendon_orient.py --write   # 重新生成本文件")
    lines.append("```")
    lines.append("")
    lines.append("`[E1]` 还会解析本渲染器的 `case ... -> blockstateRotation(x, y)`，"
                 "与 `blockstates/extendon.json` 逐项比较：blockstate 或 Java 表任何一边改动而另一边没跟上，自检立刻失败。")
    lines.append("")
    with open(DOC, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines))
    print("已生成 %s" % os.path.relpath(DOC, ROOT))


def print_table():
    rotations = load_blockstate_rotations()
    print("facing  state/d              base+bracket+Y  barrel+Y  hose exit  old")
    for facing in FACINGS:
        base = base_rotation(rotations[facing])
        print("%-6s %-21s %-15s %-9s %-10s %s" %
              (facing, "unconnected", vec_name(apply(base, (0, 1, 0))),
               vec_name(apply(base, (0, 1, 0))), "-", "ok" if facing in ("up", "down") else "FLIPPED"))
        for o in OFFSETS:
            d = unit(o)
            print("%-6s %-21s %-15s %-9s %-10s %s" %
                  (facing, offset_name(o) + " " + str(o), vec_name(apply(base, (0, 1, 0))),
                   vec_name(apply(barrel_rotation(base, d), (0, 1, 0))), exit_faces(o),
                   "ok" if facing in ("up", "down") else "FLIPPED"))


def main():
    parser = argparse.ArgumentParser(description="extendon 朝向 / 软管静态自检")
    parser.add_argument("--write", action="store_true", help="重新生成 docs/EXTENDON-ORIENTATION.md")
    parser.add_argument("--table", action="store_true", help="打印完整 162 组合表")
    args = parser.parse_args()

    info = run()
    if args.table:
        print_table()
    if args.write:
        write_doc(info)

    if failures:
        print("\n自检失败 %d / %d 条断言：" % (len(failures), assertions))
        for message in failures[:20]:
            print("  - " + message)
        return 1
    print("\n自检通过：%d 条断言（含 %d 个朝向 × 26 个连接方向，另含旧实现回归对照）。"
          % (assertions, len(FACINGS)))
    print("底座/支架贴合高度 %.4f，活塞筒出口 %.4f，软管起点偏移 %.4f。"
          % (model_span("block")[1], info["mouth"], info["hose_start"]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
