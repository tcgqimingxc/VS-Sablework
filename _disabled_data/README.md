# `_disabled_data/` —— 暂不加载的数据/资源（保留原件，等实现后再放回）

这个目录**不在** `src/main/resources/` 下，所以打包时不会被读进 jar、游戏不会加载它。
里面的文件都是原封不动保留的：恢复 = 按"放回位置"移回 `src/main/resources/` 下的原路径，内容无需改动。

## 1. 叶片自定义配方 —— 已恢复（2026-10-18，不再是禁用项）

这三条配方已按旧版机制完整实现并移回 `src/main/resources/`，本目录不再保留副本：

| 已放回位置 | 对应实现 |
|---|---|
| `data/vs_clockwork/recipe/blade_crafting_recipe.json` | `content/.../blades/item/BladeCraftingRecipe.java`（工作台，两片以上同款叶片相加） |
| `data/vs_clockwork/recipe/blade_cutting/blade_cutting.json` | `content/.../blades/item/BladeCuttingRecipe.java`（动力锯，长度减半） |
| `data/vs_clockwork/recipe/blade_cutting/wide_blade_cutting.json` | 同上（宽叶版本） |

配套代码：`index/CWDataComponents`（`vs_clockwork:blade_length` 数据组件）、
`blades/item/BladeItems`（默认值 / 上下限 / 旧 NBT 迁移）、`blades/item/BladeRecipes`（两个配方类型与序列化器的注册）、
`mixin/content/blade/MixinSawBladeRecipe`（拦 Create 的 `SawBlockEntity#getRecipes` / `#applyRecipe`）。

当初禁用时的三条判断现在都不成立，记录在此便于追溯：

1. ~~1.21.1 已把展长改成物品固有属性~~ → 展长重新变成"每 stack"的，载体是原生 `DataComponentType<Integer>`
   （不是旧的自由 NBT），`BladeItem` / `BladeData` / 渲染器已全链路改为读组件。
2. ~~`blade_cutting` 需要的 mixin 没移植~~ → 已按 1.21.1 Create 6.0.10 重新实现
   （`rollResults` 在 1.21.1 多了 `RandomSource` 参数，用 `javap -p -c -l` 核实过签名与局部变量表）。
3. ~~"同物品进、同物品出"没有意义~~ → 截短结果由 `BladeCuttingRecipe.cut()` 改写组件长度后产出，
   与旧版 `cutBlade()` 等价（旧版下限 0.25，本移植按整数格取 1）。

旧的判断依据仍然保留在 `docs/PORT-STATUS.md` §5 的历史条目里；实现与取证见 `docs/BLADE-LENGTH.md`。

## 2. 非法路径资源

| 文件 | 原因 | 放回位置（实现后） |
|---|---|---|
| `assets/blockstates/OLD_flap_bearing.json` | 文件名含大写 `OLD_`，1.21.1 资源路径必须全小写，每次启动都刷 `Invalid path in pack: vs_clockwork:blockstates/OLD_flap_bearing.json, ignoring`；且它指向的 `vs_clockwork:block/flap_bearing/block` 属于旧版遗留，当前没有任何注册方块叫 `OLD_flap_bearing`（真正在用的是 `old_andesite_flap_bearing.json` / `andesite_flap_bearing.json` / `smart_flap_bearing.json`） | 只在真的要注册 `OLD_flap_bearing` 方块、并把文件名改成全小写后再放回 `assets/vs_clockwork/blockstates/` |

## 3. 说明

- 目录里剩下的空文件夹（`loot_table/blocks`、`recipe/crafting/logistics`、`recipe/crafting/pneumatics`）
  是更早一次"临时禁用后又恢复"留下的空壳，没有文件，可以忽略。
- 禁用清单只增不删：以后再有"数据文件引用了没实现的注册项"的情况，一律移动到这里并在上表登记原因。
