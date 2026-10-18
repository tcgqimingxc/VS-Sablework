# 螺旋桨叶片加长 / 切割机制（1.21.1 移植）

> 旧版 1.20.1 有、本移植初期按 `_disabled_data/README.md` 停用的两块玩法：
> 工作台把叶片接长（`vs_clockwork:blade_crafting`）、动力锯把叶片锯短（`vs_clockwork:blade_cutting`）。
> 本文记录**取证（旧版文件:行号）→ 实现 → 与旧版的差异 → 自检 / 验收**，供后续维护者对照。

## 1. 旧版机制取证

### 1.1 长度的读写（NBT `BladeLength`）

| 位置 | 行为 |
|---|---|
| `Clockwork-1.20.1-main/.../blades/item/BladeItem.kt:14-19` | `inventoryTick`：`BladeLength == 0` 时补写 `1.0`（即"缺失 = 1"） |
| `.../BladeItem.kt:21-26` | `getDefaultInstance()` 给新物品写 `BladeLength = 1.0` |
| `.../blades/BladeData.kt:27` | 从控制器方块 NBT 里的 `ItemStack` 读 `BladeLength`，缺省 `1.0` |
| `.../blades/LengthScrollValueBehaviour.kt:18` | 滚轮面板的剪贴板键也是 `BladeLength`（机器侧的同名参数，与物品长度是两套东西） |

### 1.2 两个配方

| 位置 | 行为 |
|---|---|
| `.../blades/item/CraftingTableBladeRecipe.kt:16-40` | `matches`：全是 `BladeItem`、片数 ≥ 2、**总长 `<= ClockworkConfig.SERVER.maxBladeSize`**；宽叶必须在最前（否则返回 false） |
| `.../CraftingTableBladeRecipe.kt:42-54` | `assemble`：结果 = 第一片的物品类型，长度 = **各片相加** |
| `.../CraftingTableBladeRecipe.kt:58-64` | `canCraftInDimensions`：`width * height >= 2`；serializer 走 `BLADE_CRAFTING_SERIALIZER` |
| `.../blades/item/CuttingBladeRecipe.kt:12-26` | 继承 `create:cutting` 的 `CuttingRecipe`；`cutBlade = max(length / 2, 0.25)`（double） |
| `.../ClockworkConfig.kt:113` | `var maxBladeSize = 8.0`（加长上限） |
| `.../ClockworkRecipes.kt:26-28` | `blade_crafting` → `SimpleCraftingRecipeSerializer(::CraftingTableBladeRecipe)` |
| `.../ClockworkRecipes.kt:36` | `BLADE_CUTTING(ProcessingRecipeBuilder.ProcessingRecipeFactory(::CuttingBladeRecipe))` |
| `.../mixin/content/blade/SawBladeRecipeMixin.java:35-42` | 输入是 `BladeItem` 时，`getRecipes()` 整个换成 `BLADE_CUTTING` 类型的全部配方 |
| `.../SawBladeRecipeMixin.java:44-51` | `applyRecipe` 的 `CuttingRecipe.rollResults()` 结果换成 `cutBlade(input)` |
| `common/src/main/resources/data/vs_clockwork/recipes/blade_crafting_recipe.json` | `{"type": "vs_clockwork:blade_crafting", "category": "misc"}` |
| `.../recipes/blade_cutting/{blade,wide_blade}_cutting.json` | 同物品进出 + `processingTime: 100`（1.20.1 字段名） |

### 1.3 长度如何进入推力（叶素模型）

`.../content/forces/PropellerController.kt` 的 `computeBladeForce`：

| 行 | 公式 |
|---|---|
| 295 | `bladeWidth = if (wide) 0.375 else 0.25`（弦长） |
| 296 | `r = blade.length`（叶素半径 = 展长） |
| 299 | `rotationalVelocity = |bearingSpeed| * r` |
| 321 | `dA = bladeWidth * r`（叶素面积） |
| 322-326 | `q = 0.5ρv²`；`dLift = q·dA·Cl`、`dDrag = q·dA·Cd`；`dThrust = (dLift·cosφ − dDrag·sinφ)·sf·velocityFalloff` |
| 328 | `force = worldAxis · dThrust · forceMulPerSailInPropeller · sign(ω)` |

即：**切向速度 ∝ r、面积 ∝ r**，`q ∝ v²` ⇒ 推力大致 ∝ r³（同转速、同桨距下），所以加长叶片的收益非常明显。
旧版另有应力影响 `PropellerBearingBlockEntity.kt:659`（`|length·sin(angle)|·(wide?1.5:1)`），本移植的轴承走
Sable 的 propeller actor，应力不在这条链路上（见 §3）。

### 1.4 渲染

| 位置 | 行为 |
|---|---|
| `.../blades/BladeControllerRenderer.kt:60` | 逐片读 `BladeLength` |
| `.../BladeControllerRenderer.kt:147-151` | 中段 `translate((L-1)*0.3)` + `scale(1,1,L)`；叶尖 `translate((L-1)*0.5)` + `translate(-L+1)` |
| `.../blades/item/BladeItemRenderer.kt:31-59` | 旧版**物品**也按长度画（三段 partial + 同样的 scale/translate），本移植**未移植**该物品渲染器（见 §3） |
| `assets/vs_clockwork/models/block/blade_controller/blade/{blade_base,blade_extension,blade_tip}.json`（+ `wideblade_*`） | 三段模型本来就在资源里，`BladePartials` 已登记，无需新增模型 |

## 2. 本移植实现

| 关注点 | 实现 |
|---|---|
| 存储 | `index/CWDataComponents#BLADE_LENGTH`：`DataComponentType<Integer>`，`persistent(Codec.INT)` + `networkSynchronized(VAR_INT)`，注册进 `Registries.DATA_COMPONENT_TYPE` |
| 读写 / 默认 / 夹取 | `blades/item/BladeItems`：`getLength`（缺失=1）、`setLength`（夹到 `[1, maxBladeSize]`，等于 1 时移除组件以便堆叠）、`maxLength`（读配置）、`migrateLegacyLength`（旧 `custom_data.BladeLength` → 组件） |
| 加长配方 | `blades/item/BladeCraftingRecipe`（`CustomRecipe`，serializer = 原版 `SimpleCraftingRecipeSerializer`） |
| 切割配方 | `blades/item/BladeCuttingRecipe`（`CuttingRecipe` 子类，覆写 `getType/getSerializer/getTypeInfo`） |
| 注册 | `blades/item/BladeRecipes`（`blade_cutting` 的 `RecipeType` + 两个 `RecipeSerializer`；由 `CWContraptionsIndex#register` 调用） |
| 锯子拦截 | `mixin/content/blade/MixinSawBladeRecipe`：`getRecipes` HEAD 换配方表 + `applyRecipe` HEAD 捕获输入 + `rollResults` `@WrapOperation` 改结果 |
| 推力 | `BladeData#of` 改读 `BladeItems.getLength`（`length`/`area()` 原样喂给 `PropellerBearingBlockEntity#elementThrust`） |
| 渲染 | `BladeControllerRenderer` 本来就用 `BladeData#length()` 拉伸；包围盒改为按 `maxBladeSize` 放大（否则长叶片被视锥剔除） |
| tooltip / lang | `BladeItem#appendHoverText` → `vs_clockwork.blade.length` + `.hint`（en_us / zh_cn） |
| 配置 | `CWServer.maxBladeSize`（int，默认 8 = 旧版 `8.0`） |
| 数据 | `data/vs_clockwork/recipe/blade_crafting_recipe.json`、`recipe/blade_cutting/{blade,wide_blade}_cutting.json`（1.21.1 字段名） |

### 2.1 与旧版的有意差异

1. **整数长度**：旧版 double（切割下限 0.25）；按需求改为整数格，下限 1（`cut(1)=1`，`cut(2)=1`，`cut(4)=2`）。
2. **加长配方的原料只有叶片**：旧版 `CraftingTableBladeRecipe#matches` 拒绝任何非 `BladeItem`，没有"材料"这一步；
   本移植照抄（两片以上同款叶片相加）。任务描述里的"叶片 + 材料"与旧版源码不符，以源码为准。
3. **宽窄不可混放**：旧版"窄 + 宽"能匹配、但结果取第一片类型，宽叶被静默降级；这里判定不匹配（避免吞材料）。
4. **数据组件 vs NBT**：1.21.1 没有物品自由 NBT；组件自带持久化 + 网络同步 + `ItemStack#copy` 复制。
5. **锯子结果计算点**：旧版 `rollResults()` 无参；1.21.1 是 `rollResults(RandomSource)`。输入物品改为在
   `applyRecipe` HEAD 从 `inventory.getStackInSlot(0)` 捕获（不依赖局部变量表 / MixinExtras `@Local`）。
6. **配方 JSON 字段名**：`processingTime` → `processing_time`，`item` 产物 → `id`（1.21.1 的
   `ProcessingRecipeParams` / `ProcessingOutput` codec；与工程里已有的 `create:cutting` 配方一致）。
7. **物品图标不随长度变化**：旧版有 `BladeItemRenderer`，本移植未实现（见 §3）。

### 2.2 为什么必须 mixin（已 javap 核实）

`javap -p -c -l libs/create-1.21.1-6.0.10.jar`：

- `SawBlockEntity#getRecipes()` 是 **private**，类型写死 `AllRecipeTypes.CUTTING.getType()`（+ `STONECUTTING`），
  没有扩展点；
- `SawBlockEntity#applyRecipe()` 是 **private**，结果计算写死 `if (recipe instanceof CuttingRecipe) … rollResults(level.random)`，
  调用点在方法字节码偏移 236，是全方法唯一的结果来源。

因此沿用旧版的两段注入。两条注入都写 `require = 0`：将来 Create 改私有方法名时最坏只是"锯子不截短"，
不会拒绝加载整个 mod。

## 3. 已知限制

- **JEI**：`blade_crafting` 是原版 `crafting` 类型（`getType()` 默认值），会出现在 JEI 的合成分类里，
  原料展示为 2 个"任意叶片"（`getIngredients()` 仅用于展示）；`blade_cutting` 是自定义类型，
  没有对应 JEI 分类，**JEI 不显示**（不报错）。旧版 1.20.1 同样没有为 `BLADE_CUTTING` 写 JEI 分类。
- **配方书**：`CustomRecipe#isSpecial()` 为 true，原版配方书不收录特殊配方（旧版同样如此），
  因此"可见性"由 JEI 承担。
- **物品图标**：手持/物品栏里的叶片模型仍是静态的 `blade/item.json`（不随长度缩放）。
  旧版用 `BladeItemRenderer` + Create 的 `CustomRenderedItemModel` 做到过，本移植需要接
  `RegisterClientExtensionsEvent` + partial 模型烘焙顺序，属于独立的一块，本次未做；
  世界内（叶片控制器 BER）与 tooltip 都已按长度变化。
- **应力影响**：旧版 `PropellerBearingBlockEntity.kt:659` 把长度算进 stress；本移植的螺旋桨轴承走 Sable
  的 propeller actor，应力由 Sable 侧处理，不在本次范围。

## 4. 自检 / 验收

```powershell
python tools/selftest_blade_length.py    # [L] 长度语义 + [R] 配方 JSON + [S] 注册接线
cd clockwork_unoffical; $env:JAVA_HOME="C:\Users\yql11\java\dragonwell-21.0.8.0.8+9-GA"; .\gradlew.bat --offline build
powershell -File tools/run_gates.ps1 -SelfTest
```

`selftest_blade_length.py` 的断言：默认 1；加长 = 相加且超上限不匹配；切割向下取整且不低于 1；
推力随长度严格递增（复刻旧版 `computeBladeForce` 单叶公式，宽/窄各一组）；三条配方 JSON 结构与物品引用；
以及"组件 / 配方类型 / mixin / lang / 配置"是否真的接上线（这类漏注册在历史上直接崩过游戏）。

实机确认点（自动化测不到）：工作台两片 1 格 → 2 格；锯子把 4 格锯成 2 格；同样转速下长叶片推力更大；
控制器里的叶片模型随长度变长（含宽叶）。

## 5. 实机加载证据（2026-10-18 18:00，`1.21.1-NeoForge_21.1.251/logs/latest.log`）

装包用的 jar（17:55 构建）里 `BladeItems` / `MixinSawBladeRecipe` / `CWDataComponents` / 两份 `blade_cutting` JSON
与本次最终构建的**条目哈希逐字节一致**。日志里可以确认：

- `MixinExtras ... version=0.5.3` 初始化成功；**没有任何** `MixinSawBladeRecipe` 的注入失败/`InvalidInjection` 记录；
- `RecipeManager: Loaded 3108 recipes`，**没有** `Parsing error loading recipe` / `Unknown recipe serializer`
  —— 说明两个序列化器与三条配方 JSON 都解析成功；
- `vs_clockwork-server.toml is not correct. Correcting` —— 旧配置文件缺 `maxBladeSize`，NeoForge 已自动补上（不是错误）；
- 同一份日志里唯一的 `vs_clockwork` 报错是 `reactionwheel/block.json` 模型（另一条并行任务正在改的 OBJ），与本功能无关。

功能层面仍需玩家实操确认：合成加长、锯切割、不同长度的推力差、控制器内叶片模型缩放。
