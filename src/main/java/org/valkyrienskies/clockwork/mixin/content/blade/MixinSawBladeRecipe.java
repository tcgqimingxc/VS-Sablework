package org.valkyrienskies.clockwork.mixin.content.blade;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.content.processing.recipe.ProcessingInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.clockwork.content.contraptions.propeller.blades.item.BladeCuttingRecipe;
import org.valkyrienskies.clockwork.content.contraptions.propeller.blades.item.BladeItem;
import org.valkyrienskies.clockwork.content.contraptions.propeller.blades.item.BladeRecipes;

import java.util.ArrayList;
import java.util.List;

/**
 * 让动力锯认 {@code vs_clockwork:blade_cutting} 配方，并在锯完时把叶片截短。
 *
 * <p>对应旧版 {@code mixin/content/blade/SawBladeRecipeMixin.java}
 * （1.20.1 用 {@code @Inject(getRecipes)} + {@code @WrapOperation(rollResults)}，结构原样保留）。
 *
 * <h2>为什么仍然需要 mixin（Create 6 没有可用的扩展点）</h2>
 * <p>用 {@code javap -p -c -l libs/create-1.21.1-6.0.10.jar} 核实了 1.21.1 Create 6.0.10 的
 * {@link SawBlockEntity}：
 * <ul>
 *   <li>{@code getRecipes()} 是 <b>private</b>，内部把类型写死成
 *       {@code AllRecipeTypes.CUTTING.getType()}（+ 可选的 {@code RecipeType.STONECUTTING}），
 *       没有"注册自定义锯配方类型"的注册表 / 事件；</li>
 *   <li>{@code applyRecipe()} 也是 <b>private</b>，结果计算写死为
 *       {@code if (recipe instanceof CuttingRecipe) results = ((CuttingRecipe) recipe).rollResults(level.random)}
 *       —— 该调用点（字节码偏移 236）是全方法里唯一能改动"锯出什么"的位置。</li>
 * </ul>
 * 因此与旧版一致：用 HEAD 注入换掉配方列表，用 {@code @WrapOperation} 换掉
 * {@code rollResults(RandomSource)} 的返回值（1.20.1 是无参版本，1.21.1 多了 {@link RandomSource}
 * 参数 —— 这是两版唯一的签名差异）。
 *
 * <h2>输入物品怎么拿到（与旧版写法不同，特意说明）</h2>
 * <p>{@code rollResults} 只拿得到随机源、拿不到输入物品，而"截短"必须基于输入当前的长度。
 * 旧版用 MixinExtras 的 {@code @Local(name = "input")} 按局部变量名取。这里改为在
 * {@code applyRecipe} 的 HEAD 把 {@code inventory.getStackInSlot(0)} 存进 {@link Unique} 字段：
 * <ul>
 *   <li>HEAD 时 {@code applyRecipe} 还没执行 {@code inventory.clear()}（字节码偏移 190），
 *       槽位 0 就是它随后读进局部变量 {@code input} 的同一份物品，语义与原写法完全等价；</li>
 *   <li>不依赖 Create 的 {@code LocalVariableTable}（局部变量名在混淆/重编译后可能丢），
 *       也不依赖 MixinExtras 的 {@code @Local} 糖；注入失败的风险面更小。</li>
 *   <li>字段在每次 {@code applyRecipe} 开头都会被刷新，且锯子是"一次只处理一个输入栈"的，
 *       不存在重入 / 串味。</li>
 * </ul>
 *
 * <h2>为什么 {@code require = 0}</h2>
 * <p>本 mod 的 mixin 配置是 {@code "required": true}：注入失败会拒绝加载。这里三条注入都写了
 * {@code require = 0}，万一以后 Create 改了私有方法名 / 调用点，最坏结果只是"锯子照常按
 * {@code create:cutting} 工作、叶片不被截短"，而不是整个游戏起不来
 * （与 {@code MixinSequencedRecipe} 的取舍一致）。
 *
 * <h2>为什么不写 refmap</h2>
 * <p>Create 用官方名发布、本 mod 也在官方名下编译，{@code build.gradle} 没有 mixin 注解处理器，
 * 产物里本来就没有 refmap（同 {@code MixinSequencedRecipe}）。
 */
@Mixin(SawBlockEntity.class)
public abstract class MixinSawBladeRecipe extends BlockEntity {

    /** Create 的锯子输入/输出槽（{@code javap} 核实是 {@code public ProcessingInventory inventory}）。 */
    @Shadow
    public ProcessingInventory inventory;

    /** {@code applyRecipe} 开头捕获的输入（理由见类注释"输入物品怎么拿到"）。 */
    @Unique
    private ItemStack vs_clockwork$bladeInput = ItemStack.EMPTY;

    protected MixinSawBladeRecipe(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
    }

    /**
     * 输入是叶片时，把"可锯配方"整个换成 {@code blade_cutting} 类型里的条目
     * （旧版 {@code vs_clockwork$addBladeRecipe}）。
     *
     * <p>比旧版多做一步"按输入过滤"：旧版返回该类型的<b>全部</b>配方，锯子按
     * {@code recipeIndex} 轮换，窄叶可能抽到宽叶配方（结果一样是"截短输入"，但 JEI/调试时很迷惑）。
     * 这里只保留第一原料匹配当前输入的条目，语义更严格、行为与旧版等价。
     */
    @Inject(method = "getRecipes", at = @At("HEAD"), cancellable = true, require = 0)
    private void vs_clockwork$useBladeCuttingRecipes(
            final CallbackInfoReturnable<List<RecipeHolder<? extends Recipe<?>>>> cir) {
        final ItemStack input = this.inventory.getStackInSlot(0);
        if (!(input.getItem() instanceof BladeItem) || this.level == null) {
            return;
        }
        final List<RecipeHolder<? extends Recipe<?>>> matches = new ArrayList<>();
        for (final RecipeHolder<BladeCuttingRecipe> holder
                : this.level.getRecipeManager().getAllRecipesFor(BladeRecipes.CUTTING_TYPE)) {
            final BladeCuttingRecipe recipe = holder.value();
            if (!recipe.getIngredients().isEmpty() && recipe.getIngredients().get(0).test(input)) {
                matches.add(holder);
            }
        }
        // 即使一条都没匹配上也要返回空表：否则会落到 Create 的 create:cutting 分支，
        // 让叶片被当成普通木头切成"默认长度"的新叶片（等于凭空重置长度）。
        cir.setReturnValue(matches);
    }

    /** 在 {@code applyRecipe} 读输入之前把它存下来（理由见类注释）。 */
    @Inject(method = "applyRecipe", at = @At("HEAD"), require = 0)
    private void vs_clockwork$captureInput(final CallbackInfo ci) {
        this.vs_clockwork$bladeInput = this.inventory.getStackInSlot(0);
    }

    /**
     * 把 {@code rollResults} 的结果换成"输入截短一半"（旧版 {@code vs_clockwork$applyBladeRecipe}）。
     *
     * <p>返回 {@code List.of(cut(input))}：{@code applyRecipe} 会对每个输入逐个 roll 并合并同类项，
     * 与旧版一样只产出一片。
     */
    @WrapOperation(
            method = "applyRecipe",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/kinetics/saw/CuttingRecipe;"
                            + "rollResults(Lnet/minecraft/util/RandomSource;)Ljava/util/List;"),
            require = 0
    )
    private List<ItemStack> vs_clockwork$halveBlade(final CuttingRecipe recipe,
                                                    final RandomSource random,
                                                    final Operation<List<ItemStack>> original) {
        if (recipe instanceof BladeCuttingRecipe) {
            return List.of(BladeCuttingRecipe.cut(this.vs_clockwork$bladeInput));
        }
        return original.call(recipe, random);
    }
}
