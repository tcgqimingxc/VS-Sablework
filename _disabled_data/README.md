# `_disabled_data/` —— 暂不加载的数据/资源（保留原件，等实现后再放回）

这个目录**不在** `src/main/resources/` 下，所以打包时不会被读进 jar、游戏不会加载它。
里面的文件都是原封不动保留的：恢复 = 按"放回位置"移回 `src/main/resources/` 下的原路径，内容无需改动。

## 1. 叶片自定义配方（2026-10-11 实机日志里的 3 条 `Unknown registry key ... recipe_serializer`）

| 文件 | 原因 | 放回位置（实现后） |
|---|---|---|
| `recipe/blade_cutting/blade_cutting.json` | `vs_clockwork:blade_cutting` 配方类型未移植 | `data/vs_clockwork/recipe/blade_cutting/blade_cutting.json` |
| `recipe/blade_cutting/wide_blade_cutting.json` | 同上 | `data/vs_clockwork/recipe/blade_cutting/wide_blade_cutting.json` |
| `recipe/blade_crafting_recipe.json` | `vs_clockwork:blade_crafting` 配方类型未移植 | `data/vs_clockwork/recipe/blade_crafting_recipe.json` |

**为什么先禁用而不是直接移植**（详细理由见 1.20.1 旧版 `content/contraptions/propeller/blades/item/`）：

1. 这两个类型的**全部意义**是改写"每片叶子的展长"——旧版把它存在物品 NBT `BladeLength` 里：
   - `CuttingBladeRecipe`（= `create:cutting` 的子类）把 `BladeLength` **减半**（`CuttingBladeRecipe.kt:18-26`）；
   - `CraftingTableBladeRecipe`（= 工作台 `CustomRecipe`）把多片叶子的 `BladeLength` **相加**，上限
     `ClockworkConfig.maxBladeSize`（`CraftingTableBladeRecipe.kt:18-54`）。
   1.21.1 的移植已经把展长改成**每种叶片物品的固有属性**（`BladeItem#getBladeLength`、
   `BladeData` 类头注释里写明了这个取舍），而叶片本体 / 叶片控制器 / 螺旋桨**已经实机验证连通**，
   本次不允许改动那条玩法链路。移回这两条配方 = 必须重新引入"每 stack 展长"（数据组件 +
   BladeItem/BladeData/渲染器全链路改造），收益是"把叶片切成更短"这种当前没有语义的操作。
2. `blade_cutting` 还需要**没移植的 mixin**：旧版靠 `mixin/content/blade/SawBladeRecipeMixin`
   拦截 Create 的 `SawBlockEntity#getRecipes` / `#applyRecipe`，才让动力锯在输入叶片时改用
   `BLADE_CUTTING` 类型、并用 `cutBlade()` 重写结果。1.21.1 的 Create 6.0.10 里这两个方法的
   签名/描述符要重新用 javap 核实（本仓库铁律：不许猜 API），属于独立的一小块工作。
3. 这两条配方的 JSON 本身是"同物品进、同物品出"（`propeller_blade -> propeller_blade`）：
   只有配合 `cutBlade()` 的 NBT 改写才有意义；没有 NBT 时它等于一条什么都不做的无限配方。

**玩家仍然能获得叶片**（禁用这三条不影响获取路径，下面 4 条都是可用配方，已随本次构建一起进包）：

- `data/vs_clockwork/recipe/crafting/propeller_blade.json`：木台阶 + 木棍 → 窄叶片（工作台）
- `data/vs_clockwork/recipe/crafting/wide_propeller_blade.json`：木板 + 木棍 → 宽叶片（工作台）
- `data/vs_clockwork/recipe/cutting/propeller_blade.json`：`create:cutting` 木板 → 3 窄叶片（动力锯）
- `data/vs_clockwork/recipe/cutting/wide_propeller_blade.json`：`create:cutting` 木板 → 2 宽叶片（动力锯）

**恢复步骤**：先实现 `vs_clockwork:blade_cutting` / `vs_clockwork:blade_crafting`
（`RecipeType` + `RecipeSerializer`/`MapCodec` 注册，参考 `CWGasIndex` 里 `gas_crafting` 的写法）
以及所需 mixin，再把上表 3 个文件移回"放回位置"，最后跑一次 `tools/check_data_refs.py`
（它会检查 recipe 里的 `type` 是否在代码里注册过）。

## 2. 非法路径资源

| 文件 | 原因 | 放回位置（实现后） |
|---|---|---|
| `assets/blockstates/OLD_flap_bearing.json` | 文件名含大写 `OLD_`，1.21.1 资源路径必须全小写，每次启动都刷 `Invalid path in pack: vs_clockwork:blockstates/OLD_flap_bearing.json, ignoring`；且它指向的 `vs_clockwork:block/flap_bearing/block` 属于旧版遗留，当前没有任何注册方块叫 `OLD_flap_bearing`（真正在用的是 `old_andesite_flap_bearing.json` / `andesite_flap_bearing.json` / `smart_flap_bearing.json`） | 只在真的要注册 `OLD_flap_bearing` 方块、并把文件名改成全小写后再放回 `assets/vs_clockwork/blockstates/` |

## 3. 说明

- 目录里剩下的空文件夹（`loot_table/blocks`、`recipe/crafting/logistics`、`recipe/crafting/pneumatics`）
  是更早一次"临时禁用后又恢复"留下的空壳，没有文件，可以忽略。
- 禁用清单只增不删：以后再有"数据文件引用了没实现的注册项"的情况，一律移动到这里并在上表登记原因。
