#!/usr/bin/env python3
"""检查数据包/资源里引用的 vs_clockwork 注册项是否真的在代码里注册过。

为什么需要：数据包在加载期解析配方/战利品表时，如果引用了不存在的物品，轻则报
"Parsing error loading recipe ..."，重则让 Create 的序列装配解析直接抛
IndexOutOfBoundsException 把游戏打崩（实机遇到过）。这个脚本就是用来提前发现这类"孤儿数据"。

五项检查（都是实机日志换来的）：
  [1] 物品/方块 id：数据包引用的 `vs_clockwork:<id>` 是否在代码里注册过。
      —— `create:sequenced_assembly` 的 ingredient 引用未注册物品会让 Create 抛 IOOBE 崩游戏。
  [2] 配方类型：`data/vs_clockwork/recipe/**/*.json` 里的 `"type": "vs_clockwork:<id>"`
      是否在代码里注册过 `RecipeType`/`RecipeSerializer`。
      —— 2026-10-11 实机日志里 `vs_clockwork:blade_cutting` / `vs_clockwork:blade_crafting`
      两条类型没注册，导致 3 条配方解析失败刷 ERROR（脚本当时没覆盖这一类）。
  [3] 音效：代码里注册过的 `SoundEvent` 是否在 `assets/vs_clockwork/sounds.json` 里有同名条目，
      以及 sounds.json 指向的 `vs_clockwork:sounds/<x>.ogg` 是否真的存在。
      —— 实机 `Missing sound for event: vs_clockwork:doink` 是 sounds.json 把条目拼成了
      "doing"；`File vs_clockwork:sounds/egg_reverie.ogg does not exist` 是条目指向了不存在的 ogg。
  [4] worldgen / biome_modifier / jukebox_song 三类"只在建世界时加载"的数据：
      结构校验（必填字段、字段类型、判别式取值）+ 引用校验（方块 id、标签、placed_feature、
      SoundEvent、翻译键）。这些文件平时不加载，一旦坏了就是在点"单人游戏"时
      `Failed to load registries due to above errors` 直接崩，所以必须在这里拦住。

字段/取值依据（不猜，全部来自 1.21.1 真实文件，详见 docs/DATA-CHECKS.md）：
  - 原版 `data/minecraft/worldgen/configured_feature/ore_*.json`、`placed_feature/ore_*.json`
    （本机 `build/moddev/artifacts/neoforge-21.1.228-client-extra-aka-minecraft-resources.jar`）；
  - `javap` 反编译 21.1.1 的 `OreConfiguration` / `TargetBlockState` / `TrapezoidHeight` /
    `CountPlacement` / `HeightRangePlacement` / `PlacedFeature` / `JukeboxSong` /
    `PlacementModifierType` / `HeightProviderType` / `Feature` / `GenerationStep$Decoration`
    拿到的 codec 字段名与枚举名；
  - NeoForge 21.1.228 源码 `net/neoforged/neoforge/common/world/BiomeModifiers.java`（javadoc 里带 json 样例）
    与 `NeoForgeMod.java`（`add_features` 的 `.fieldOf("biomes"/"features"/"step")`）；
  - 生态内同格式样例：`libs/create-1.21.1-6.0.10.jar` 的 `zinc_ore` 三条文件、
    `libs/*aeronautics*.jar` 的 `jukebox_song/cloud_skipper.json`。

用法：python tools/check_data_refs.py
"""
import glob
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROJECT = os.path.join(ROOT, "clockwork_unoffical")
JAVA = os.path.join(PROJECT, "src", "main", "java")
RES = os.path.join(PROJECT, "src", "main", "resources")
DATA = os.path.join(RES, "data", "vs_clockwork")
DATAROOT = os.path.join(RES, "data")
RECIPE = os.path.join(DATA, "recipe")
ASSETS = os.path.join(RES, "assets", "vs_clockwork")
SOUNDS_JSON = os.path.join(ASSETS, "sounds.json")
LANG_EN = os.path.join(ASSETS, "lang", "en_us.json")
NS = "vs_clockwork"

BLOCK_OR_ITEM = re.compile(r'\.(?:block|item)\(\s*"([a-z0-9_]+)"')
ANY_LITERAL = re.compile(r'\(\s*"([a-z0-9_]+)"')
REF = re.compile(r'"(?:item|id|name|result|ingredient)"\s*:\s*"vs_clockwork:([a-z0-9_/]+)"')

# 配方 JSON 顶层/嵌套的 "type"，只看自己命名空间的（create:/minecraft: 等由对应模组负责）
RECIPE_TYPE_REF = re.compile(r'"type"\s*:\s*"' + NS + r':([a-z0-9_/]+)"')
# 配方类型的注册点：DeferredRegister 上带 id 的 register(...)（见 CWGasIndex 的 gas_crafting）
RECIPE_REGISTRY_KEY = re.compile(r'Registries\.RECIPE_(?:TYPE|SERIALIZER)')
NAMED_REGISTER = re.compile(r'\.register\(\s*"([a-z0-9_/]+)"')
# 少数写法直接把 id 传给 ResourceLocation/自定义 create(...)
IMPL_RECIPE = re.compile(r'implements\s+[^{]*Recipe(?:Type|Serializer)')
ID_LITERAL = re.compile(r'(?:fromNamespaceAndPath|create|register|of)\(\s*(?:[A-Za-z_][\w.]*\s*,\s*)?"([a-z0-9_/]+)"')

# 声音事件注册点：SoundEvent.createVariableRangeEvent / Registries.SOUND_EVENT 所在文件
SOUND_REGISTRY_KEY = re.compile(r'Registries\.SOUND_EVENT|SoundEvent\.create\w*RangeEvent')
SOUND_ID_LITERAL = re.compile(r'\b(?:create|sound|register)\(\s*"([a-z0-9_/]+)"\s*\)')
SOUND_FILE_REF = re.compile(r'"name"\s*:\s*"' + NS + r':([a-z0-9_/]+)"')

# ---------------------------------------------------------------- [4] 的结构常量
# 全部来自 javap（1.21.1）：Feature / PlacementModifierType / HeightProviderType 的注册 id
FEATURE_TYPES = {
    "bamboo", "basalt_columns", "basalt_pillar", "block_column", "block_pile", "blue_ice",
    "bonus_chest", "chorus_plant", "coral_claw", "coral_mushroom", "coral_tree", "delta_feature",
    "desert_well", "disk", "dripstone_cluster", "end_gateway", "end_island", "end_platform",
    "end_spike", "fill_layer", "flower", "forest_rock", "fossil", "freeze_top_layer", "geode",
    "glowstone_blob", "huge_brown_mushroom", "huge_fungus", "huge_red_mushroom", "ice_spike",
    "iceberg", "kelp", "lake", "large_dripstone", "monster_room", "multiface_growth",
    "nether_forest_vegetation", "netherrack_replace_blobs", "no_bonemeal_flower", "no_op", "ore",
    "pointed_dripstone", "random_boolean_selector", "random_patch", "random_selector",
    "replace_single_block", "root_system", "scattered_ore", "sculk_patch", "sea_pickle", "seagrass",
    "simple_block", "simple_random_selector", "spring_feature", "tree", "twisting_vines",
    "underwater_magma", "vegetation_patch", "vines", "void_start_platform",
    "waterlogged_vegetation_patch", "weeping_vines",
}
PLACEMENT_TYPES = {
    "block_predicate_filter", "rarity_filter", "surface_relative_threshold_filter",
    "surface_water_depth_filter", "biome", "count", "noise_based_count", "noise_threshold_count",
    "count_on_every_layer", "environment_scan", "heightmap", "height_range", "in_square",
    "random_offset", "carving_mask", "fixed_placement",
}
HEIGHT_PROVIDER_TYPES = {
    "constant", "uniform", "biased_to_bottom", "very_biased_to_bottom", "trapezoid", "weighted_list",
}
RULE_TEST_TYPES = {"always_true", "block_match", "tag_match", "blockstate_match"}
# javap GenerationStep$Decoration 的 getSerializedName()
DECORATION_STEPS = {
    "raw_generation", "lakes", "local_modifications", "underground_structures",
    "surface_structures", "strongholds", "underground_ores", "underground_decoration",
    "fluid_springs", "vegetal_decoration", "top_layer_modification",
}
# javap: JukeboxSong 的 codec 字段
JUKEBOX_REQUIRED = ("comparator_output", "description", "length_in_seconds", "sound_event")

# 只在对应目录里合法的标签注册表：标签不能跨注册表混用（原版标签位置 data/<ns>/tags/<kind>/）
TAG_REGISTRY_DIRS = {
    "block": "tags/block",
    "item": "tags/item",
    "biome": "tags/worldgen/biome",
    "placed_feature": "tags/worldgen/placed_feature",
    "configured_feature": "tags/worldgen/configured_feature",
    "configured_carver": "tags/worldgen/configured_carver",
    "entity_type": "tags/entity_type",
}


def read(path):
    return open(path, encoding="utf-8", errors="replace").read()


def java_files():
    return glob.glob(os.path.join(JAVA, "**", "*.java"), recursive=True)


def registered_ids():
    ids = set()
    for path in java_files():
        text = read(path)
        ids.update(BLOCK_OR_ITEM.findall(text))
        if os.path.basename(path).endswith("Index.java") or os.path.basename(path) in ("CWBlocks.java", "CWItems.java"):
            ids.update(ANY_LITERAL.findall(text))
    return ids


def registered_recipe_types():
    """代码里注册过的配方类型/序列化器 id。"""
    ids = set()
    for path in java_files():
        text = read(path)
        if RECIPE_REGISTRY_KEY.search(text):
            ids.update(NAMED_REGISTER.findall(text))
        if IMPL_RECIPE.search(text):
            ids.update(ID_LITERAL.findall(text))
    return ids


def registered_sound_events():
    """代码里注册过的 SoundEvent id（sounds.json 的键必须与它同名）。"""
    ids = set()
    for path in java_files():
        text = read(path)
        if SOUND_REGISTRY_KEY.search(text):
            ids.update(SOUND_ID_LITERAL.findall(text))
    return ids


def check_item_refs():
    ids = registered_ids()
    problems = {}
    for path in glob.glob(os.path.join(DATA, "**", "*.json"), recursive=True):
        text = read(path)
        for ref in set(REF.findall(text)):
            if "/" in ref or ref in ids:
                continue
            problems.setdefault(ref, []).append(os.path.relpath(path, DATA))
    print(f"代码里注册的 id: {len(ids)} 个；数据包引用的未注册 id: {len(problems)} 个")
    for ref, files in sorted(problems.items()):
        print(f"  {ref:<24} <- {', '.join(sorted(files)[:3])}")
    return len(problems)


def check_recipe_types():
    known = registered_recipe_types()
    problems = {}
    for path in glob.glob(os.path.join(RECIPE, "**", "*.json"), recursive=True):
        text = read(path)
        for ref in set(RECIPE_TYPE_REF.findall(text)):
            if ref in known:
                continue
            problems.setdefault(ref, []).append(os.path.relpath(path, RECIPE).replace("\\", "/"))
    print(f"代码里注册的配方类型/序列化器: {len(known)} 个 "
          f"({', '.join(sorted(known)) or '无'})；"
          f"配方引用的未注册配方类型: {len(problems)} 个")
    for ref, files in sorted(problems.items()):
        print(f"  {NS}:{ref:<18} <- {', '.join(sorted(files)[:3])}"
              f"{' 等' if len(files) > 3 else ''}")
    return len(problems)


def check_sounds():
    events = registered_sound_events()
    data = {}
    if os.path.exists(SOUNDS_JSON):
        try:
            # utf-8-sig：容忍 BOM（Minecraft 自己读不了带 BOM 的 JSON，这里不因此崩掉检查）
            data = json.load(open(SOUNDS_JSON, encoding="utf-8-sig"))
        except Exception as exc:  # noqa: BLE001
            print(f"[音效] sounds.json 解析失败: {exc}")
            return 1

    missing_entry = sorted(e for e in events if e not in data)
    missing_ogg = {}
    for event, body in data.items():
        for sound in (body or {}).get("sounds") or []:
            name = sound.get("name") if isinstance(sound, dict) else sound
            if not isinstance(name, str) or not name.startswith(NS + ":"):
                continue
            ogg = os.path.join(ASSETS, "sounds", name.split(":", 1)[1] + ".ogg")
            if not os.path.exists(ogg):
                missing_ogg.setdefault(event, []).append(name)

    print(f"代码里注册的音效事件: {len(events)} 个；sounds.json 条目: {len(data)} 个；"
          f"注册但 sounds.json 无条目: {len(missing_entry)} 个；"
          f"sounds.json 指向不存在的 ogg: {len(missing_ogg)} 个")
    for event in missing_entry:
        print(f"  {NS}:{event:<20} 已注册 SoundEvent 但 sounds.json 没有同名条目（播放时会 Missing sound）")
    for event, names in sorted(missing_ogg.items()):
        print(f"  {NS}:{event:<20} -> {', '.join(sorted(names))}（assets/{NS}/sounds/ 下无对应 .ogg）")
    return len(missing_entry) + len(missing_ogg)


# ================================================================ [4] 数据资源索引
def _res_id(relpath):
    """'data/vs_clockwork/worldgen/placed_feature/x.json' -> 'vs_clockwork:worldgen/placed_feature/x'。"""
    parts = relpath.replace("\\", "/").split("/")
    if len(parts) < 4 or parts[0] != "data":
        return None
    return f"{parts[1]}:" + "/".join(parts[3:])[:-5]


class Universe:
    """真实存在的资源 id 索引：我们自己的数据 + 1.21.1 原版 jar 的资源 + javap 出来的类型注册表。"""

    def __init__(self):
        # key: 'worldgen/placed_feature' / 'tags/block' / ...；value: set('ns:path')
        self.ids = {}
        self.blocks = set()          # 可验证的方块 id
        self.notes = []

    def add(self, kind, ident):
        self.ids.setdefault(kind, set()).add(ident)

    def add_many(self, kind, idents):
        self.ids.setdefault(kind, set()).update(idents)

    def has(self, kind, ident):
        return bool(ident) and ident in self.ids.get(kind, ())

    def add_kind_dir(self, ns, kind, dirpath, block=False):
        """把 dirpath/*.json 登记为 ns:kind 下的裸 id（'ns:文件名'）。"""
        if not os.path.isdir(dirpath):
            return
        for name in os.listdir(dirpath):
            if not name.endswith(".json"):
                continue
            self.add(f"{ns}:{kind}", f"{ns}:{name[:-5]}")
            if block:
                self.blocks.add(f"{ns}:{name[:-5]}")


def java_block_ids():
    """代码里注册的方块 id（block(...) / 索引类里的字面量）。"""
    ids = set()
    for path in java_files():
        text = read(path)
        for b in BLOCK_OR_ITEM.findall(text):
            # 无法区分 block/item，两个前缀都登记，宁可漏报也不误报
            ids.add(NS + ":" + b)
        if os.path.basename(path).endswith("Index.java") or os.path.basename(path) in ("CWBlocks.java", "CWItems.java"):
            for b in ANY_LITERAL.findall(text):
                ids.add(NS + ":" + b)
    return ids


def find_client_extra():
    hits = sorted(glob.glob(os.path.join(PROJECT, "build", "moddev", "artifacts", "*client-extra*.jar")))
    return hits[0] if hits else None


def build_universe():
    u = Universe()
    # 0) 判别式类型注册表：javap 出来的常量（不依赖任何本地 jar，保证离线也能跑）
    #    注意：类型注册表没有"每项一个文件"，所以这里存的是裸 id（'minecraft:ore'）。
    u.add_many("minecraft:worldgen/configured_feature_type", {"minecraft:" + t for t in FEATURE_TYPES})
    u.add_many("minecraft:worldgen/placement_modifier_type", {"minecraft:" + t for t in PLACEMENT_TYPES})
    u.add_many("minecraft:worldgen/height_provider_type", {"minecraft:" + t for t in HEIGHT_PROVIDER_TYPES})
    u.add_many("minecraft:worldgen/rule_test", {"minecraft:" + t for t in RULE_TEST_TYPES})
    # 1) 我们自己的数据 + 资源
    for kind, sub in (("worldgen/configured_feature", "worldgen/configured_feature"),
                      ("worldgen/placed_feature", "worldgen/placed_feature"),
                      ("worldgen/biome", "worldgen/biome"),
                      ("worldgen/configured_carver", "worldgen/configured_carver"),
                      ("neoforge/biome_modifier", "neoforge/biome_modifier"),
                      ("jukebox_song", "jukebox_song"),
                      ("tags/block", "tags/block"),
                      ("tags/item", "tags/item"),
                      ("tags/worldgen/biome", "tags/worldgen/biome"),
                      ("tags/worldgen/placed_feature", "tags/worldgen/placed_feature"),
                      ("tags/worldgen/configured_feature", "tags/worldgen/configured_feature"),
                      ("tags/worldgen/configured_carver", "tags/worldgen/configured_carver"),
                      ("tags/entity_type", "tags/entity_type")):
        u.add_kind_dir(NS, kind, os.path.join(DATA, sub.replace("/", os.sep)))
    u.blocks.update(java_block_ids())
    u.blocks.update(asset_block_ids())

    # 2) 1.21.1 原版资源（client-extra jar：data/minecraft/** 与 assets/minecraft/**）
    jar = find_client_extra()
    if jar:
        import zipfile
        with zipfile.ZipFile(jar) as z:
            names = z.namelist()
            for name in names:
                if not name.endswith(".json"):
                    continue
                if name.startswith("data/minecraft/") and not name.startswith("data/minecraft/datapacks/"):
                    rest = name[len("data/minecraft/"):-5]
                    kind = rest.rsplit("/", 1)[0]      # 'worldgen/configured_feature/ore_coal' -> 前两段
                    u.add(f"minecraft:{kind}", "minecraft:" + rest.rsplit("/", 1)[1])
                elif name.startswith("assets/minecraft/blockstates/"):
                    # 原版方块 id 的权威来源：blockstates/<block>.json
                    u.blocks.add("minecraft:" + name[len("assets/minecraft/blockstates/"):-5])
        u.notes.append(f"原版索引来自 {os.path.basename(jar)}"
                       f"（data/minecraft/** 全部注册表 + assets/minecraft/blockstates/** 共 "
                       f"{sum(1 for b in u.blocks if b.startswith('minecraft:'))} 个方块）")
    else:
        u.notes.append("未找到 build/moddev/artifacts/*client-extra*.jar："
                       "跳过一切 minecraft: 引用校验（只校验 vs_clockwork: 自身）——请先跑一次构建再执行本脚本")
    return u


def _norm(ident, default_ns="minecraft"):
    if not isinstance(ident, str):
        return None
    return ident if ":" in ident else f"{default_ns}:{ident}"


def walk_json(node, path=""):
    """深度遍历，产出 (字段名或索引路径, 值, 字典键路径)。"""
    if isinstance(node, dict):
        for k, v in node.items():
            yield k, v, path
            yield from walk_json(v, f"{path}.{k}" if path else k)
    elif isinstance(node, list):
        for i, v in enumerate(node):
            yield from walk_json(v, f"{path}[{i}]")


def check_worldgen(u):
    """[4a] configured_feature / placed_feature 的结构 + 引用校验。"""
    problems = []
    cf_dir = os.path.join(DATA, "worldgen", "configured_feature")
    pf_dir = os.path.join(DATA, "worldgen", "placed_feature")

    for path in sorted(glob.glob(os.path.join(cf_dir, "*.json"))):
        rel = f"worldgen/configured_feature/{os.path.basename(path)}"
        data = load_json(path, rel, problems)
        if data is None:
            continue
        ftype = data.get("type")
        if not isinstance(ftype, str):
            problems.append(f"{rel}: 缺少字符串字段 \"type\"")
            continue
        if not check_type(u, "%s:worldgen/configured_feature_type", ftype):
            problems.append(f"{rel}: \"type\": \"{ftype}\" 不是已注册的特征类型（原版 1.21.1 无此 id）")
        config = data.get("config")
        if not isinstance(config, dict):
            problems.append(f"{rel}: 缺少对象字段 \"config\"")
            continue

        if _norm(ftype) == "minecraft:ore":
            # javap OreConfiguration: Codec.fieldOf("targets"/"size"/"discard_chance_on_air_exposure")
            for f in ("targets", "size"):
                if f not in config:
                    problems.append(f"{rel}: minecraft:ore 的 config 缺少必填字段 \"{f}\"")
            if "size" in config and not isinstance(config["size"], int):
                problems.append(f"{rel}: config.size 必须是整数，实际 {type(config['size']).__name__}")
            if "discard_chance_on_air_exposure" in config and not isinstance(
                    config["discard_chance_on_air_exposure"], (int, float)):
                problems.append(f"{rel}: config.discard_chance_on_air_exposure 必须是数字")
            targets = config.get("targets")
            if targets is not None and not isinstance(targets, list):
                problems.append(f"{rel}: config.targets 必须是数组")
                targets = None
            for i, tgt in enumerate(targets or []):
                where = f"{rel}: config.targets[{i}]"
                if not isinstance(tgt, dict):
                    problems.append(f"{where} 必须是对象")
                    continue
                # javap TargetBlockState: Codec.fieldOf("target"/"state")
                for f in ("target", "state"):
                    if f not in tgt:
                        problems.append(f"{where} 缺少必填字段 \"{f}\"（TargetBlockState 的 codec 字段）")
                state = tgt.get("state")
                if isinstance(state, dict):
                    name = state.get("Name")
                    if not isinstance(name, str):
                        problems.append(f"{where}.state 缺少 \"Name\"（BlockState 的字段名首字母大写）")
                    elif not check_block(u, name):
                        problems.append(f"{where}.state.Name: 方块 \"{name}\" 不存在")
                rule = tgt.get("target")
                if not isinstance(rule, dict):
                    continue
                ptype = rule.get("predicate_type")
                if not isinstance(ptype, str):
                    problems.append(f"{where}.target 缺少 \"predicate_type\"")
                    continue
                if not check_type(u, "%s:worldgen/rule_test", ptype):
                    problems.append(f"{where}.target.predicate_type: \"{ptype}\" 不是已注册的 RuleTest 类型")
                    continue
                kind = _norm(ptype)
                if kind == "minecraft:block_match":
                    if not isinstance(rule.get("block"), str):
                        problems.append(f"{where}.target: block_match 需要字符串字段 \"block\"")
                    elif not check_block(u, rule["block"]):
                        problems.append(f"{where}.target.block: 方块 \"{rule['block']}\" 不存在")
                elif kind == "minecraft:tag_match":
                    tag = rule.get("tag")
                    if not isinstance(tag, str):
                        problems.append(f"{where}.target: tag_match 需要字符串字段 \"tag\"")
                    elif not check_tag_plain(u, "%s:tags/block", tag):
                        problems.append(f"{where}.target.tag: 方块标签 \"{tag}\" 不存在"
                                        f"（须有 data/<ns>/tags/block/....json；"
                                        f"注意 RuleTest 的 tag 字段是裸 id，不带 #）")

    for path in sorted(glob.glob(os.path.join(pf_dir, "*.json"))):
        rel = f"worldgen/placed_feature/{os.path.basename(path)}"
        data = load_json(path, rel, problems)
        if data is None:
            continue
        feat = data.get("feature")
        if not isinstance(feat, str):
            problems.append(f"{rel}: 缺少字符串字段 \"feature\"")
        elif not check_resource(u, "%s:worldgen/configured_feature", feat):
            problems.append(f"{rel}: feature \"{feat}\" 不存在（缺 data/<ns>/worldgen/configured_feature/....json）")
        placement = data.get("placement")
        if not isinstance(placement, list):
            problems.append(f"{rel}: 缺少数组字段 \"placement\"")
            continue
        for i, mod in enumerate(placement):
            where = f"{rel}: placement[{i}]"
            if not isinstance(mod, dict):
                problems.append(f"{where} 必须是对象")
                continue
            mtype = mod.get("type")
            if not isinstance(mtype, str):
                problems.append(f"{where} 缺少字符串字段 \"type\"")
                continue
            if not check_type(u, "%s:worldgen/placement_modifier_type", mtype):
                problems.append(f"{where}.type: \"{mtype}\" 不是已注册的放置修饰符类型")
                continue
            kind = _norm(mtype)
            if kind == "minecraft:count":
                # javap CountPlacement: Codec.fieldOf("count")
                cnt = mod.get("count")
                if not isinstance(cnt, int):
                    problems.append(f"{where}: count 修饰符需要整数字段 \"count\"")
            elif kind == "minecraft:height_range":
                # javap HeightRangePlacement: Codec.fieldOf("height")
                h = mod.get("height")
                if not isinstance(h, dict):
                    problems.append(f"{where}: height_range 需要对象字段 \"height\"")
                    continue
                htype = h.get("type")
                if not isinstance(htype, str) or not check_type(u, "%s:worldgen/height_provider_type", htype):
                    problems.append(f"{where}.height.type: \"{htype}\" 不是已注册的高度提供器类型")
                    continue
                if _norm(htype) in ("minecraft:trapezoid", "minecraft:uniform",
                                    "minecraft:biased_to_bottom", "minecraft:very_biased_to_bottom"):
                    # javap TrapezoidHeight: Codec.fieldOf("min_inclusive"/"max_inclusive"/"plateau")
                    for f in ("min_inclusive", "max_inclusive"):
                        anchor = h.get(f)
                        if not isinstance(anchor, dict) or not any(
                                k in anchor for k in ("absolute", "above_bottom", "below_top")):
                            problems.append(
                                f"{where}.height.{f}: 需要 {{\"absolute\"|\"above_bottom\"|\"below_top\": int}}")
    return problems


def check_biome_modifiers(u):
    """[4b] neoforge/biome_modifier 的结构 + 引用校验。"""
    problems = []
    base = os.path.join(DATA, "neoforge", "biome_modifier")
    # 依据：NeoForgeMod 的 BIOME_MODIFIER_SERIALIZERS.register("<id>", ...)
    required = {
        "neoforge:add_features": ("biomes", "features", "step"),
        "neoforge:remove_features": ("biomes", "features"),
        "neoforge:add_spawns": ("biomes", "spawners"),
        "neoforge:remove_spawns": ("biomes", "entity_types"),
        "neoforge:add_carvers": ("biomes", "carvers", "step"),
        "neoforge:remove_carvers": ("biomes", "carvers"),
        "neoforge:add_spawn_costs": ("biomes", "entity_types", "spawn_cost"),
        "neoforge:remove_spawn_costs": ("biomes", "entity_types"),
        "neoforge:none": (),
    }
    for path in sorted(glob.glob(os.path.join(base, "*.json"))):
        rel = f"neoforge/biome_modifier/{os.path.basename(path)}"
        data = load_json(path, rel, problems)
        if data is None:
            continue
        btype = data.get("type")
        if not isinstance(btype, str):
            problems.append(f"{rel}: 缺少字符串字段 \"type\"")
            continue
        key = str(btype)
        if key not in required:
            problems.append(f"{rel}: \"type\": \"{btype}\" 不是 NeoForge 21.1 的 biome modifier 类型")
            continue
        for f in required[key]:
            if f not in data:
                problems.append(f"{rel}: 类型 {key} 缺少必填字段 \"{f}\"")
        # biomes：生物群系 id / id 列表 / #标签（NeoForge 的 Biome.LIST_CODEC）
        for entry in as_list(data.get("biomes")):
            if not check_biome_entry(u, entry):
                problems.append(f"{rel}: biomes 里的 \"{entry}\" 既不是生物群系 id 也不是已存在的生物群系标签")
        # features：placed_feature id / 列表 / #标签
        if "features" in data:
            for entry in as_list(data["features"]):
                if not check_ref_or_tag(u, "%s:worldgen/placed_feature", entry):
                    problems.append(f"{rel}: features 里的 \"{entry}\" 既不是 placed_feature 也不是已存在的标签")
        if "step" in data:
            step = data["step"]
            if not isinstance(step, str) or step not in DECORATION_STEPS:
                problems.append(f"{rel}: step \"{step}\" 不是 Decoration 枚举值"
                                f"（javap GenerationStep$Decoration，例如 underground_ores）")
        if "carvers" in data:
            for entry in as_list(data["carvers"]):
                if not check_ref_or_tag(u, "%s:worldgen/configured_carver", entry):
                    problems.append(f"{rel}: carvers 里的 \"{entry}\" 不存在")
        if "entity_types" in data:
            for entry in as_list(data["entity_types"]):
                if not check_ref_or_tag(u, "%s:entity_type", entry):
                    problems.append(f"{rel}: entity_types 里的 \"{entry}\" 不存在")
    return problems


def check_jukebox_songs(u):
    """[4c] jukebox_song 的结构 + SoundEvent/翻译键校验。"""
    problems = []
    events = registered_sound_events()
    lang = load_lang(problems)
    base = os.path.join(DATA, "jukebox_song")
    for path in sorted(glob.glob(os.path.join(base, "*.json"))):
        rel = f"jukebox_song/{os.path.basename(path)}"
        data = load_json(path, rel, problems)
        if data is None:
            continue
        for f in JUKEBOX_REQUIRED:
            if f not in data:
                problems.append(f"{rel}: 缺少必填字段 \"{f}\"（javap JukeboxSong 的 codec 字段）")
        if "comparator_output" in data and not isinstance(data["comparator_output"], int):
            problems.append(f"{rel}: comparator_output 必须是整数")
        elif isinstance(data.get("comparator_output"), int) and not 0 <= data["comparator_output"] <= 15:
            problems.append(f"{rel}: comparator_output 必须在 0..15")
        if "length_in_seconds" in data and not isinstance(data["length_in_seconds"], (int, float)):
            problems.append(f"{rel}: length_in_seconds 必须是数字")
        if "sound_event" in data:
            ev = data["sound_event"]
            if not isinstance(ev, str):
                problems.append(f"{rel}: sound_event 必须是字符串（注册表 id）")
            else:
                rid = _norm(ev, NS)
                ns, p = rid.split(":", 1)
                if ns == NS:
                    if p not in events:
                        problems.append(f"{rel}: sound_event \"{ev}\" 没有在代码里注册 SoundEvent"
                                        f"（index/CWSounds.java 里加一条 register(\"{p}\")）")
                    elif not sound_event_in_sounds_json(p):
                        problems.append(f"{rel}: sound_event \"{ev}\" 已注册但 sounds.json 没有同名条目")
                # 非本模组命名空间的声音事件不做存在性校验：
                # 1.21.1 原版 SoundEvent 清单不在 data/ 里（javap 也拿不到全部），强校验必然误报。
                pass
        desc = data.get("description")
        if desc is not None:
            if not isinstance(desc, dict):
                problems.append(f"{rel}: description 必须是文本组件对象（如 {{\"translate\": \"...\"}}）")
            else:
                key = desc.get("translate")
                if isinstance(key, str) and key.startswith(NS + ".") and lang and key not in lang:
                    problems.append(f"{rel}: description.translate \"{key}\" 在 lang/en_us.json 里没有条目")
    return problems


def sound_event_in_sounds_json(event):
    if not os.path.exists(SOUNDS_JSON):
        return False
    try:
        return event in json.load(open(SOUNDS_JSON, encoding="utf-8-sig"))
    except Exception:  # noqa: BLE001
        return False


def load_lang(problems):
    if not os.path.exists(LANG_EN):
        return {}
    try:
        return json.load(open(LANG_EN, encoding="utf-8-sig"))
    except Exception as exc:  # noqa: BLE001
        problems.append(f"assets/{NS}/lang/en_us.json 解析失败: {exc}")
        return {}


def load_json(path, rel, problems):
    """读取并解析一个数据 JSON；解析失败记问题并返回 None。"""
    try:
        return json.load(open(path, encoding="utf-8-sig"))
    except Exception as exc:  # noqa: BLE001
        problems.append(f"{rel}: JSON 解析失败: {exc}")
        return None


def as_list(v):
    if v is None:
        return []
    return v if isinstance(v, list) else [v]


def check_block(u, ident):
    """方块是否存在：自己的数据看代码注册/blockstate，原版看原版索引。"""
    rid = _norm(ident)
    return rid is not None and rid in u.blocks


def asset_block_ids():
    """assets/vs_clockwork/blockstates/*.json 里真实存在的方块。"""
    ids = set()
    for path in glob.glob(os.path.join(ASSETS, "blockstates", "*.json")):
        ids.add(NS + ":" + os.path.basename(path)[:-5])
    return ids


def _dir_key(kind, rid):
    """把 (目录 kind, 完整资源 id) 拼成索引键（kind 里的 %s 用 rid 的命名空间替换）。

    ('%s:worldgen/placed_feature', 'vs_clockwork:wanderlite_ore') -> 'vs_clockwork:worldgen/placed_feature'
    """
    return kind.replace("%s", rid.split(":", 1)[0])


def check_type(u, kind, ident):
    """判别式字段（feature type / placement modifier type / rule test ...）是否为已注册类型。

    类型注册表没有"每项一个文件"，索引里存的就是裸 id。
    """
    rid = _norm(ident)
    return rid is not None and u.has(kind.replace("%s", rid.split(":", 1)[0]), rid)


def check_resource(u, kind, ident, default_ns=NS):
    """普通资源引用是否为已存在的资源（不接受 #标签）。kind 里可用 %s 表示命名空间。"""
    rid = _norm(ident, default_ns)
    return rid is not None and u.has(_dir_key(kind, rid), rid)


def check_tag(u, kind, ident):
    """标签引用（必须以 # 开头）；kind 给出该标签必须在哪个目录（标签不能跨注册表混用）。"""
    if not isinstance(ident, str) or not ident.startswith("#"):
        return False
    rid = _norm(ident[1:])
    return rid is not None and u.has(kind.replace("%s", rid.split(":", 1)[0]), rid)


def check_tag_plain(u, kind, ident):
    """不带 # 的标签 id（例如 `minecraft:tag_match` 的 `tag` 字段，见 1.21.1 ore_coal.json）。"""
    rid = _norm(ident)
    return rid is not None and u.has(kind.replace("%s", rid.split(":", 1)[0]), rid)


def check_ref_or_tag(u, kind, ident):
    """引用可写具体资源 id，也可写 #标签。"""
    if not isinstance(ident, str):
        return False
    if ident.startswith("#"):
        return check_tag(u, kind, ident)
    return check_resource(u, kind, ident)


def check_biome_entry(u, entry):
    if not isinstance(entry, str):
        return False
    if entry.startswith("#"):
        return check_tag(u, "%s:tags/worldgen/biome", entry)
    return check_resource(u, "%s:worldgen/biome", entry)


def check_worldgen_and_registries():
    u = build_universe()
    problems = check_worldgen(u)
    problems += check_biome_modifiers(u)
    problems += check_jukebox_songs(u)
    print(f"worldgen/biome_modifier/jukebox_song 校验："
          f"configured_feature {len(glob.glob(os.path.join(DATA, 'worldgen', 'configured_feature', '*.json')))} 个、"
          f"placed_feature {len(glob.glob(os.path.join(DATA, 'worldgen', 'placed_feature', '*.json')))} 个、"
          f"biome_modifier {len(glob.glob(os.path.join(DATA, 'neoforge', 'biome_modifier', '*.json')))} 个、"
          f"jukebox_song {len(glob.glob(os.path.join(DATA, 'jukebox_song', '*.json')))} 个；"
          f"问题 {len(problems)} 个")
    for x in problems:
        print(f"  {x}")
    print(f"  （{bom_report()}）")
    for n in u.notes:
        print(f"  （{n}）")
    return len(problems)


def bom_report():
    """带 UTF-8 BOM 的数据 JSON：不影响游戏（Gson 2.10.1 自己会跳过 BOM，见 docs/DATA-CHECKS.md），
    只报告不判失败，避免为了统一编码去动无关文件。"""
    bom = []
    for path in glob.glob(os.path.join(DATA, "**", "*.json"), recursive=True):
        try:
            if open(path, "rb").read(3) == b"\xef\xbb\xbf":
                bom.append(os.path.relpath(path, DATA).replace("\\", "/"))
        except OSError:
            continue
    return f"带 UTF-8 BOM 的数据 JSON: {len(bom)} 个（不影响解析）" + (
        f" -> {', '.join(sorted(bom)[:5])}" if bom else "")


def main():
    bad = 0
    bad += check_item_refs()
    bad += check_recipe_types()
    bad += check_sounds()
    bad += check_worldgen_and_registries()
    print(f"合计问题: {bad} 个")
    return 0


if __name__ == "__main__":
    sys.exit(main())
