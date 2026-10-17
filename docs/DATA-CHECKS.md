# 数据文件格式依据（worldgen / biome_modifier / jukebox_song）

> 为什么有这份文档：`docs/PORT-CONVENTIONS.md` 第 0 条是"不许猜 API/格式"。
> 这三类数据只在**创建/加载世界**时解析，平时进游戏不会报错，一旦格式错就是
> `Failed to load registries due to above errors` 直接崩客户端。所以每一个字段名都要有出处。
> 下面每条都是本机可复现的（命令照抄即可）。

## 0. 权威样例从哪来

| 来源 | 文件 | 覆盖内容 |
|---|---|---|
| 1.21.1 原版数据 | `clockwork_unoffical/build/moddev/artifacts/neoforge-21.1.228-client-extra-aka-minecraft-resources.jar` 里的 `data/minecraft/**` | 原版 `worldgen/configured_feature/*.json`、`worldgen/placed_feature/*.json`、`jukebox_song/*.json`、`tags/**` |
| 1.21.1 字节码 | `clockwork_unoffical/build/moddev/artifacts/neoforge-21.1.228.jar`（用 `javap -c -p`） | codec 的 `fieldOf("...")` 字面量、枚举 `getSerializedName()`、判别式类型注册 id |
| NeoForge 21.1.228 源码 | `~/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/21.1.228/*/neoforge-21.1.228-sources.jar` | `BiomeModifiers.java`（javadoc 带 json 样例）、`NeoForgeMod.java`（`add_features` 的 `.fieldOf(...)`） |
| 生态内同格式样例 | `libs/create-1.21.1-6.0.10.jar`、`libs/dev.eriksonn.aeronautics.*.jar` | Create 的 `neoforge/biome_modifier/*.json`、`worldgen/{configured,placed}_feature/zinc_ore.json`；Aeronautics 的 `jukebox_song/cloud_skipper.json` |

`javap` 命令模板（JAVA_HOME 用移植文档里那个 dragonwell）：

```powershell
$jp="C:\Users\yql11\java\dragonwell-21.0.8.0.8+9-GA\bin\javap.exe"
$j="clockwork_unoffical\build\moddev\artifacts\neoforge-21.1.228.jar"
& $jp -classpath $j -c -p net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration |
  Select-String '// String'
```

## 1. `minecraft:ore` 的 configured_feature

样例：原版 `data/minecraft/worldgen/configured_feature/ore_coal.json`。

`javap -c -p OreConfiguration` 输出的 `Codec.fieldOf(...)` 字面量：

```
// String targets
// String size
// String discard_chance_on_air_exposure
```

`javap -c -p OreConfiguration$TargetBlockState`：

```
// String target
// String state
```

所以结构是固定的：

```json
{
  "type": "minecraft:ore",
  "config": {
    "discard_chance_on_air_exposure": 0.0,
    "size": 3,
    "targets": [
      { "state": {"Name": "..."}, "target": {"predicate_type": "...", "...": "..."} }
    ]
  }
}
```

- `state` 用的是 **BlockState 编解码**（`{"Name": ...}` 首字母大写），不是 `{"block": ...}`。
- `RuleTest` 的判别式字段名是 **`predicate_type`**（`javap -c -p net.minecraft.world.level.structure.templatesystem.RuleTest`
  或直接看原版 `ore_coal.json`），取值见下表（`javap -c -p` 各 RuleTest 内部类的注册 id）：
  `always_true` / `block_match` / `tag_match` / `blockstate_match`。
- ⚠️ `tag_match` 的 `tag` 字段是**裸标签 id**（`minecraft:deepslate_ore_replaceables`），**不带 `#`**；
  而战利品表/`biome_modifier.biomes` 里的标签是 `"#minecraft:is_overworld"` 形式。这两处不一样，
  守门脚本里分别用 `check_tag_plain` / `check_tag` 处理。

## 2. placed_feature

样例：原版 `data/minecraft/worldgen/placed_feature/ore_coal_lower.json`。

`javap -c -p PlacedFeature`：`// String feature`、`// String placement`。

每个 placement 修饰符的判别式是 `type`，取值来自
`javap -c -p net.minecraft.world.level.levelgen.placement.PlacementModifierType` 的注册字面量：

```
block_predicate_filter, rarity_filter, surface_relative_threshold_filter, surface_water_depth_filter,
biome, count, noise_based_count, noise_threshold_count, count_on_every_layer, environment_scan,
heightmap, height_range, in_square, random_offset, carving_mask, fixed_placement
```

用到的三个：

- `minecraft:count` → `javap -c -p CountPlacement` 的 `// String count`。
- `minecraft:height_range` → `javap -c -p HeightRangePlacement` 的 `// String height`；
  `height` 内部是高度提供器，判别式 `type` 取值来自
  `javap -c -p net.minecraft.world.level.levelgen.heightproviders.HeightProviderType`：
  `constant, uniform, biased_to_bottom, very_biased_to_bottom, trapezoid, weighted_list`；
  `trapezoid` 的字段来自 `javap -c -p TrapezoidHeight`：`min_inclusive` / `max_inclusive` / `plateau`。
  锚点是 `{"absolute": int}` / `{"above_bottom": int}` / `{"below_top": int}`（1.21.1 还有 `absolute` 的旧写法兼容）。
- `minecraft:in_square`、`minecraft:biome` → 无字段（`Codec.unit`）。

## 3. NeoForge biome modifier

依据：`NeoForgeMod.java` 第 227-233 行的注册：

```java
BIOME_MODIFIER_SERIALIZERS.register("add_features", () -> RecordCodecBuilder.mapCodec(
    builder -> builder.group(
        Biome.LIST_CODEC.fieldOf("biomes").forGetter(AddFeaturesBiomeModifier::biomes),
        PlacedFeature.LIST_CODEC.fieldOf("features").forGetter(AddFeaturesBiomeModifier::features),
        Decoration.CODEC.fieldOf("step").forGetter(AddFeaturesBiomeModifier::step))
        .apply(builder, AddFeaturesBiomeModifier::new)));
```

因此：类型 id `neoforge:add_features`（1.20.1 是 `forge:add_features`，目录也从 `forge/` 改成 `neoforge/`），
字段 `biomes`（`Biome.LIST_CODEC`：生物群系 id、id 列表或 `#标签`）、`features`（`PlacedFeature.LIST_CODEC`）、
`step`（`Decoration` 枚举，`javap -c -p 'net.minecraft.world.level.levelgen.GenerationStep$Decoration'` 的常量名转小写
= `raw_generation, lakes, local_modifications, underground_structures, surface_structures, strongholds,
underground_ores, underground_decoration, fluid_springs, vegetal_decoration, top_layer_modification`）。

生态内同格式样例（Create 6.0.10，`libs/create-1.21.1-6.0.10.jar`）：

```json
{ "type": "neoforge:add_features", "biomes": "#minecraft:is_overworld",
  "features": "create:zinc_ore", "step": "underground_ores" }
```

## 4. jukebox_song

`javap -c -p net.minecraft.world.item.JukeboxSong` 的 codec 字段：

```
// String sound_event
// String description
// String length_in_seconds
// String comparator_output
```

原版样例 `data/minecraft/jukebox_song/11.json`：

```json
{ "comparator_output": 11, "description": {"translate": "jukebox_song.minecraft.11"},
  "length_in_seconds": 71.0, "sound_event": "minecraft:music_disc.11" }
```

- `sound_event` 是 **SoundEvent 注册表引用**（`RegistryFileCodec`），必须真实注册；
  1.21 起只写 `assets/<ns>/sounds.json` **不够**（那只决定"这个事件播哪些 ogg"），
  必须在代码里 `DeferredRegister<SoundEvent>` 注册同名事件 —— 见 `index/CWSounds.java`。
- `description` 是文本组件（原版用 `{"translate": ...}`）。
- 生态样例：Aeronautics 的 `data/aeronautics/jukebox_song/cloud_skipper.json`，字段完全一致。

## 5. 1.21.1 里"只在建世界时加载"的三类，怎么在本地先验证

`tools/check_data_refs.py` 第 [4] 类检查做了：

1. **结构**：必填字段是否在、类型对不对（`count` 必须是整数等）、判别式取值是否在注册表里。
2. **引用**：
   - `state.Name` / `target.block` → 方块必须存在（我们的看
     `assets/vs_clockwork/blockstates/*.json` + 代码注册；原版看 client-extra jar 的
     `assets/minecraft/blockstates/*.json`，共 1062 个方块）；
   - `target.tag` → 方块标签必须有 `data/<ns>/tags/block/<...>.json`；
   - `placed_feature.feature` → 必须有对应的 `configured_feature` 文件；
   - `biome_modifier.biomes` → 必须是真的生物群系 id 或 `data/<ns>/tags/worldgen/biome/` 下的标签；
   - `biome_modifier.features` → 必须是真的 `placed_feature` 或对应标签；
   - `jukebox_song.sound_event` → `vs_clockwork:` 的必须在 `CWSounds.java` 里注册过，
     且在 `sounds.json` 里有同名条目。

配套自检：`python tools/selftest_data_refs.py` —— 逐个注入 14 类错误（缺字段、类型错、枚举错、
引用不存在），每一条都必须被报出来，恢复后必须回到 `合计问题: 0 个`。
守门脚本自己"永远绿"是这类工具最常见的失效方式，所以自检和检查脚本一起维护。

## 6. 2026-10-11 那次 `Failed to load registries` 的真正原因（重要教训）

`crash-2026-10-11_20.37.01-client.txt` + `logs/latest.log` 里报的 5 个失败项是：

```
minecraft:jukebox_song            vs_clockwork:wanderlust
minecraft:worldgen/configured_feature  vs_clockwork:wanderlite_ore
minecraft:worldgen/placed_feature      vs_clockwork:wanderlite_ore
neoforge:biome_modifier           vs_clockwork:wanderlite_overworld / wanderlite_end
```

看起来像"5 个数据文件格式不对"，但日志里每一项的 `Caused by` 都是：

```
Caused by: java.util.zip.ZipException: invalid LOC header (bad signature)
	at jdk.zipfs/jdk.nio.zipfs.ZipFileSystem$EntryInputStream.initDataPos(ZipFileSystem.java:2598)
	at ...RegistryDataLoader.loadElementFromResource(RegistryDataLoader.java:208)
```

**这是 zip 读取失败，不是 JSON 解析失败**：`RegistryDataLoader` 先 `Files.newInputStream(...)`
拿字节、再交给 Gson，`ZipException` 发生在"读字节"这一步，所以 JSON 内容根本没被解析过。
换句话讲，光看这 5 个 JSON 的字段，永远查不出这个崩溃。

排查时先做这一步：**逐个条目比对 zip 的本地文件头（LOC）与中央目录（CD）**，再逐个解压。
本仓库提供了两个工具：

- `python tools/check_jar.py [jar...]`：纯 Python，比对 LOC/CD 的压缩方式、压缩大小、原始大小、
  CRC32 与文件名，并真正解压每个条目；损坏时报
  `本地文件头签名不是 PK\x03\x04 ... —— invalid LOC header`。
- `java tools/verify_jar_java.java [jar...]`：用 `java.util.zip.ZipFile`
  （也就是崩溃栈里那个读取器）把每个条目读一遍。

另外注意：**改完 jar 别在游戏运行时覆盖 mods 里的文件**。崩溃日志里
`Found mod file "vs_clockwork-0.6.0-1.21.1.jar"` 的扫描时间是 20:35:43，而 mods 里那份 jar 的
写入时间是 20:36:29（扫描之后），随后 20:37:01 就崩了 —— 游戏一边在读、文件一边在被替换，
就会读到半个文件、拿到 `invalid LOC header`。装包请用"先写临时文件、校验 sha256、再原子替换"：

```powershell
$src = "clockwork_unoffical\build\libs\vs_clockwork-0.6.0-1.21.1.jar"
$dst = "<mods 目录>\vs_clockwork-0.6.0-1.21.1.jar"
Copy-Item $src "$dst.new" -Force
if ((Get-FileHash $src).Hash -ne (Get-FileHash "$dst.new").Hash) { throw "复制不完整" }
Move-Item "$dst.new" $dst -Force     # 同目录 Move-Item 是原子替换
python tools\check_jar.py $dst
```

