#!/usr/bin/env python3
"""把热管（heat_pipe）的 blockstate 从"单档"展开成"六档热度"。

背景
----
`models/block/heat_pipe/` 里已经有 6 档热度模型（`"" / _warm / _hot / _very_hot / _molten /
_super_hot`，由 `tools/gen_heat_pipe_models.py` 生成），但 `blockstates/heat_pipe.json` 只引用
了常温那一档，于是另外 5 档共 190 个模型没有任何消费者（`docs/RENDER-AUDIT.md` §7 第 5 条）。

`HeatPipeBlock.HEAT` 属性（cool/warm/hot/very_hot/molten/super_hot）接上之后，blockstate 必须
为每个连接组合 × 每个热度档各给一个模型，否则 1.21.1 会静默换 missing model（黑紫）。

本脚本为什么读"工程里的 blockstate"而不是硬编码条件
----------------------------------------------------
工程里的 heat_pipe.json 在官方 30 条 multipart 之外，还补了 8 条退化状态（core / full /
full_wo_*），这些是 2026-10-16 修 `[12]` 时加的。硬编码会漂移；这里直接以当前文件为准：

  1. 去掉每条 entry 的 `heat` 条件、剥掉模型名里的热度后缀；
  2. 按 (模型, 条件) 去重，得到"单档基准"38 条（脚本可重复运行，幂等）；
  3. 对 6 档各复制一份，条件加 `"heat": "<档位>"`，模型名加对应后缀。

用法：
    python tools/gen_heat_pipe_blockstate.py
"""

import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PATH = os.path.join(
    ROOT, "clockwork_unoffical", "src", "main", "resources", "assets", "vs_clockwork",
    "blockstates", "heat_pipe.json",
)
MODEL_PREFIX = "vs_clockwork:block/heat_pipe/"

# (blockstate 里的档位名, 模型名后缀)；顺序与 HeatPipeBlock.Heat 的声明顺序一致（只是可读性）。
TIERS = [
    ("cool", ""),
    ("warm", "_warm"),
    ("hot", "_hot"),
    ("very_hot", "_very_hot"),
    ("molten", "_molten"),
    ("super_hot", "_super_hot"),
]
# 剥后缀时必须从长到短："_super_hot" / "_very_hot" 也以 "_hot" 结尾。
SUFFIXES = sorted((suffix for _, suffix in TIERS if suffix), key=len, reverse=True)


def strip_tier(model):
    """从模型 id 末尾剥掉已知热度后缀；返回 (基准模型, 原后缀)。"""
    for suffix in SUFFIXES:
        if model.endswith(suffix):
            return model[: -len(suffix)], suffix
    return model, ""


def base_entries(doc):
    """把已展开的 blockstate 还原成单档基准（幂等）。"""
    seen = set()
    entries = []
    for entry in doc["multipart"]:
        when = dict(entry.get("when", {}))
        when.pop("heat", None)
        model = entry["apply"]["model"]
        model, _ = strip_tier(model)
        key = (model, tuple(sorted(when.items())))
        if key in seen:
            continue
        seen.add(key)
        entries.append((model, when))
    return entries


def main():
    with open(PATH, encoding="utf-8") as handle:
        doc = json.load(handle)

    entries = base_entries(doc)
    if not entries:
        raise SystemExit(f"没有解析出任何 multipart 条目: {PATH}")

    multipart = []
    for tier_name, suffix in TIERS:
        for model, when in entries:
            condition = dict(when)
            condition["heat"] = tier_name
            multipart.append({
                "apply": {"model": model + suffix},
                "when": condition,
            })

    doc["multipart"] = multipart
    with open(PATH, "w", encoding="utf-8") as handle:
        json.dump(doc, handle, indent=2)
        handle.write("\n")
    print(f"heat_pipe blockstate: {len(entries)} 个基准形状 × {len(TIERS)} 档 = "
          f"{len(multipart)} 条 multipart → {os.path.relpath(PATH, ROOT)}")


if __name__ == "__main__":
    main()
