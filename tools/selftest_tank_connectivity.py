#!/usr/bin/env python3
"""气体储罐连通性静态自检（`tools/run_gates.ps1 -SelfTest` 的一部分）。

## 为什么需要这一关

实机反馈"气体储罐会自己分离"。根因不是 `ConnectivityHandler` 算错，而是**结构字段被结构之外的
路径改写**：

1. 客户端曾经每 10 刻按方块状态 `BOTTOM=true` 把 controller 清空。`BOTTOM` 的语义是
   "本段位于整罐最下一层"，宽 3 的罐底有 9 块同时为 true、只有 1 块是 controller；于是另外
   8 块被当成独立单块，`ConnectivityHandler.isConnected` 认为罐底与罐身断开 → 外观分离。
2. `LevelChunk.setBlockState` 对**任何**方块状态变化都会回调 `onPlace`（javap 核实），
   而 `notifyMultiUpdated` 会写 `top/bottom`。若 `onPlace` 不先判"还是同一块方块"，
   写一次外观就会排一次 `formMulti` —— 把"结构重算"和"外观同步"绑在一起。

这两个坑都不会让服务端的 width/height 变错，所以靠看 `formMulti` 的返回值永远查不出来；
只能用"结构字段只允许在哪些方法里改"这类静态不变量守住。

## 检查项

  [T1] `DuctTankBlockEntity.tick()` 不写方块状态（周期同步只发数据包）。
  [T2] 客户端不再有 `reconcileClientState` / `findControllerBelow` 之类的结构自愈。
  [T3] `controller` 只在 `read` / `setController` / `removeController` 三处赋值。
  [T4] `onPlace` 同时具备"同方块"与"isMoving"两个 Create 同款 guard，且同步调
       `updateConnectivity()`（不是 queue）。
  [T5] `notifyMultiUpdated` 用 `UPDATE_CLIENTS | UPDATE_INVISIBLE`；`GasNodeState` 的
       `syncIfChanged`/`syncNow` 不写方块状态。
  [T6] 物品模型指向非 CT 的 `block_single_item`，且该模型取 64×64 图集 (0,3) 格。
  [T7] 3×3 罐底的状态模型自检：`BOTTOM=true` 有 9 块、controller 只有 1 块；旧的
       "按 BOTTOM 推导 controller"会误清 8 块 —— 这是 [T2] 的回归说明。

退出码：0 = 全部通过；1 = 有失败项。
"""

import json
import os
import re
import sys

TOOLS = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(TOOLS)
JAVA = os.path.join(ROOT, "clockwork_unoffical", "src", "main", "java")
RES = os.path.join(ROOT, "clockwork_unoffical", "src", "main", "resources")
TANK = os.path.join(JAVA, "org", "valkyrienskies", "clockwork", "content", "logistics", "gas", "tank")
BE = os.path.join(TANK, "DuctTankBlockEntity.java")
BLOCK = os.path.join(TANK, "DuctTankBlock.java")
GAS_STATE = os.path.join(JAVA, "org", "valkyrienskies", "clockwork", "content", "logistics", "gas",
                         "GasNodeState.java")
ITEM_MODEL = os.path.join(RES, "assets", "vs_clockwork", "models", "item", "duct_tank.json")
ITEM_ICON = os.path.join(RES, "assets", "vs_clockwork", "models", "block", "duct_tank",
                         "block_single_item.json")


def read(path):
    with open(path, encoding="utf-8", errors="replace") as fh:
        return fh.read()


def method_body(text, name):
    """按 `void name(...) {` 找方法体并做花括号配对；找不到返回 None。"""
    m = re.search(r'\bvoid\s+' + re.escape(name) + r'\s*\([^)]*\)\s*\{', text)
    if not m:
        return None
    start = text.index("{", m.start())
    depth = 0
    for i in range(start, len(text)):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                return text[start:i + 1]
    return None


def check(problems, name, ok, why):
    print(("  [OK]   " if ok else "  [FAIL] ") + name + ("" if ok else " —— " + why))
    if not ok:
        problems.append(name + "：" + why)


def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    problems = []
    be = read(BE)
    block = read(BLOCK)
    gas = read(GAS_STATE)

    print("[T1] tick() 不写方块状态")
    tick = method_body(be, "tick")
    check(problems, "tick() 存在", tick is not None, "找不到 DuctTankBlockEntity.tick()")
    if tick:
        check(problems, "tick() 不调用 setBlock/setBlockAndUpdate",
              not re.search(r'\bsetBlock(?:AndUpdate)?\s*\(', tick),
              "周期 tick 里写方块状态会触发邻居更新 → 结构重算；气体同步只应走 sendData")

    print("[T2] 客户端不做结构自愈")
    for banned in ("reconcileClientState", "findControllerBelow"):
        # 只看"方法定义/调用"，注释里提名字是允许的（类注释要解释这个坑）。
        check(problems, f"不存在 {banned}() 的定义/调用",
              re.search(r'\b' + banned + r'\s*\(', be) is None,
              f"{banned}() 会按 BOTTOM 重新推导 controller；3×3 罐底会误清 8 块（见 [T7]）")
    check(problems, "没有 lazyTick 覆盖（结构字段不按刻重算）",
          method_body(be, "lazyTick") is None,
          "lazyTick 里改 controller/height/width 会在客户端周期性地拆罐")

    print("[T3] controller/height/width 的写入点收敛")
    # setHeight/setWidth 是 IMultiBlockEntityContainer 的接口实现，只由服务端的
    # ConnectivityHandler.formMulti 调用（Create 的 FluidTankBlockEntity 同款），允许。
    allowed = {"read", "setController", "removeController", "setHeight", "setWidth"}
    assign_methods = set()
    for m in re.finditer(r'\b(controller|height|width)\s*=(?!=)', be):
        # 找到该赋值所在的方法（用最近的、位于其前面的 void 方法签名）
        prefix = be[:m.start()]
        candidates = list(re.finditer(r'\bvoid\s+(\w+)\s*\([^)]*\)\s*\{', prefix))
        if candidates:
            assign_methods.add(candidates[-1].group(1))
    unexpected = sorted(assign_methods - allowed)
    check(problems, "controller/height/width 只在 read/setController/removeController/"
                    "setHeight/setWidth 赋值",
          not unexpected, f"额外写入点：{unexpected}")

    print("[T4] onPlace 与 Create 的 guard 一致")
    on_place = method_body(block, "onPlace")
    check(problems, "onPlace() 存在", on_place is not None, "找不到 DuctTankBlock.onPlace")
    if on_place:
        check(problems, "同方块状态变化直接 return",
              re.search(r'oldState\.is\s*\(\s*state\.getBlock\(\)\s*\)', on_place) is not None,
              "notifyMultiUpdated 写 top/bottom 会回调 onPlace；没有这个 guard 就会反复 formMulti")
        check(problems, "isMoving 直接 return",
              re.search(r'if\s*\(\s*isMoving\s*\)', on_place) is not None,
              "活塞/移动装置搬方块时不该重新搜多方块（Create 同款）")
        check(problems, "onPlace 同步调用 updateConnectivity()",
              re.search(r'\.updateConnectivity\s*\(\s*\)', on_place) is not None
              and "queueConnectivityUpdate" not in on_place,
              "Create 在 onPlace 里直接成型；排到下一 tick 会让客户端先看到默认 block_single")
        check(problems, "markAndNotifyBlock 补发成型后的状态",
              "markAndNotifyBlock" in on_place,
              "Create FluidTankBlock.onPlace 用 markAndNotifyBlock 修正放置流程的旧状态")

    print("[T5] 写状态只发生在正确的路径/flag")
    notify = method_body(be, "notifyMultiUpdated")
    check(problems, "notifyMultiUpdated() 存在", notify is not None, "找不到 notifyMultiUpdated")
    if notify:
        check(problems, "notifyMultiUpdated 用 UPDATE_CLIENTS|UPDATE_INVISIBLE",
              re.search(r'Block\.UPDATE_CLIENTS\s*\|\s*Block\.UPDATE_INVISIBLE', notify) is not None,
              "必须和 Create FluidTankBlockEntity 同 flag；UPDATE_NEIGHBORS(1) 会触发邻居重算")
    for name in ("syncIfChanged", "syncNow"):
        body = method_body(gas, name)
        check(problems, f"GasNodeState.{name}() 不写方块状态",
              body is not None and not re.search(r'\bsetBlock(?:AndUpdate)?\s*\(', body),
              "气体/热量的周期同步只允许 sendData，不能写 blockstate")

    print("[T6] 物品图标 = 非 CT 的官方单块模型")
    item = json.load(open(ITEM_MODEL, encoding="utf-8"))
    check(problems, "item model parent = block_single_item",
          item.get("parent") == "vs_clockwork:block/duct_tank/block_single_item",
          "指向世界 block_single 会复用 CT 用的 16×16 平铺贴图 → 图标没有边框/铆钉")
    icon = json.load(open(ITEM_ICON, encoding="utf-8"))
    refs = icon.get("textures") or {}
    check(problems, "图标模型引用 64×64 图集",
          refs.get("0") == "vs_clockwork:block/duct_tank_top_connected"
          and refs.get("1") == "vs_clockwork:block/duct_tank_connected",
          "物品不经过 CT，必须用官方 (0,3) 格图集")
    uvs = {(tuple(f.get("uv") or [])) for el in icon.get("elements") or []
           for f in (el.get("faces") or {}).values()}
    check(problems, "图标 uv 只取 [0,12,4,16]（图集 (0,3) 格）",
          uvs == {(0, 12, 4, 16)}, f"实际 uv: {sorted(uvs)}")

    print("[T7] 3×3 罐底状态模型（回归说明）")
    # 一个宽 3 的罐：底层 9 块 BOTTOM=true，但只有 origin 那块是 controller。
    bottom = [{"pos": (x, 0, z), "bottom": True,
               "controller": None if (x, z) == (0, 0) else (0, 0, 0)}
              for x in range(3) for z in range(3)]
    would_clear = [b for b in bottom if b["bottom"] and b["controller"] not in (None, b["pos"])]
    check(problems, "罐底 BOTTOM=true 的块数 = 9、controller = 1",
          len(bottom) == 9 and sum(1 for b in bottom if b["controller"] is None) == 1,
          "测试模型本身写错了")
    check(problems, "旧的按 BOTTOM 推导会误清 8 块 controller",
          len(would_clear) == 8,
          "回归模型与 [T2] 描述不一致")
    print(f"       -> 旧规则会把这 {len(would_clear)} 块改成独立单块（控制器只剩 1 块）")

    print()
    if problems:
        print(f"失败 {len(problems)} 项：")
        for p in problems:
            print("  - " + p)
        return 1
    print("气体储罐连通性自检通过：结构字段只在服务端结构路径改写；周期同步不写 blockstate；"
          "物品图标不再复用 CT 世界模型。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
