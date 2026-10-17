package org.valkyrienskies.clockwork.mixin.content.sequenced_assembly;

import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 防御性 mixin：任何模组的坏 {@code create:sequenced_assembly} 配方都不再把游戏打崩。
 *
 * <h2>崩溃链路（全部用 javap 在 {@code libs/create-1.21.1-6.0.10.jar} 上核实过）</h2>
 * <ol>
 *   <li>{@code SequencedAssemblyRecipeSerializer} 的 JSON({@code MapCodec}) 构造函数 lambda
 *       （{@code javap -v} 里是 {@code lambda$new$2}，由 {@code BootstrapMethods #11} 绑到
 *       {@code RecordCodecBuilder} 的 {@code Products$P5.apply}）在
 *       {@code for (i = 0; i < sequence.size(); i++)} 里对每一步调用
 *       {@code SequencedRecipe.initFromSequencedAssembly(recipe, i == 0)}（偏移 98 的 invokevirtual）。</li>
 *   <li>该方法体（{@code javap -c} 偏移 61）执行 {@code NonNullList.set(0, …)}：把第 0 个 ingredient 换成
 *       过渡物品（或“过渡物品+顶层原料”的 CompoundIngredient）。</li>
 *   <li>任一步的 {@code ingredients} 解出来是空表（长度 0，例如 JSON 里就是空数组 / 引用了未注册物品被丢掉）时，
 *       上一步直接抛 {@code IndexOutOfBoundsException: Index 0 out of bounds for length 0}。</li>
 *   <li>{@code net.minecraft.world.item.crafting.RecipeManager} 解析配方时只 catch
 *       {@code IllegalArgumentException} 与 {@code JsonParseException}（javap 异常表 {@code 84 → 145}），
 *       {@code IndexOutOfBoundsException} 不在其中 → 异常一路冒到创建世界，整个游戏崩。</li>
 * </ol>
 *
 * <h2>注入点</h2>
 * <p>目标方法签名（{@code javap -p} 核实）：
 * {@code void initFromSequencedAssembly(com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe, boolean)}
 * —— 包级私有、返回 {@code void}，所以用 {@code method = "名字+完整描述符"} 定位、{@code @At("HEAD")} + {@code ci.cancel()}。
 * 选择 HEAD 而不是方法体内其它位置：方法体从偏移 0 到 60 只做两件事 —— 判断 {@code supportsAssembly()}、构造一个
 * “过渡物品”的局部 {@code Ingredient}（{@code astore_3}），第一件会炸的事就是偏移 61 的 {@code getIngredients().set(0, …)}；
 * HEAD 是能最早、最省地拦下它的位置，而且整个方法体的作用只有“改写第 0 个 ingredient”，cancel 不会留下任何半成品状态。
 *
 * <h2>require = 0 是有意的</h2>
 * <p>本 mod 的 {@code vs_clockwork.mixins.json} 是 {@code "required": true}：mixin 一旦应用失败就会拒绝加载、直接崩游戏。
 * 所以这里显式写死 {@code require = 0} —— 万一以后 Create 改了方法名/描述符（或换了混淆环境），注入失败只让这条保护
 * 静默失效并打一条 mixin 警告，<b>最坏情况等于没加这个 mixin</b>，绝不因为“保护性代码自己坏了”而崩游戏。
 *
 * <h2>不写 refmap</h2>
 * <p>Create 是用官方（Mojang official）名发布的，本 mod 也在官方名下编译，refmap 在这里既无必要、又可能在注入时把
 * 名称重映射错；{@code build.gradle} 也没有引入 mixin 注解处理器，产物 jar 里本来就没有 refmap。
 *
 * <h2>为什么用 {@code getRecipe()} 而不是 {@code @Shadow} 私有字段</h2>
 * <p>{@code javap -p} 显示步骤配方是 {@code private final T wrapped;}（擦除后 {@code ProcessingRecipe wrapped}）的私有字段，
 * 而 HEAD 注入点能拿到的只有目标方法自己的两个参数（所属的序列装配配方 + 是否第一步），拿不到它。常规写法是
 * {@code @Shadow @Final private ProcessingRecipe<?, ?> wrapped;}，但 {@code @Shadow} 校验失败属于 mixin <b>应用期</b>硬失败，
 * {@code require = 0} 管不到它 —— 也就是说 Create 哪天删/改了这个私有字段，用 shadow 反而会把游戏搞崩，正好违背本 mixin
 * 的目的。这里改用同源的公开方法 {@code getRecipe()}（javap 核实 {@code public T getRecipe();}，返回的就是这个字段），
 * 把最坏情况压回“保护不生效”。
 *
 * @see org.valkyrienskies.clockwork.mixin.content.gas.boiler.MixinSteamEngineBlock 同类防御性注入的写法参考
 */
@Mixin(SequencedRecipe.class)
public class MixinSequencedRecipe {

    @Inject(
            method = "initFromSequencedAssembly("
                    + "Lcom/simibubi/create/content/processing/sequenced/SequencedAssemblyRecipe;Z)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void vs_clockwork$skipEmptyIngredients(final SequencedAssemblyRecipe assemblyRecipe,
                                                    final boolean firstStep,
                                                    final CallbackInfo ci) {
        // wrapped 是 private final，HEAD 注入点参数里拿不到，用公开的 getRecipe() 读同一个字段（理由见类注释）。
        final ProcessingRecipe<?, ?> stepRecipe = ((SequencedRecipe<?>) (Object) this).getRecipe();
        if (stepRecipe == null) {
            return;
        }

        final NonNullList<Ingredient> ingredients = stepRecipe.getIngredients();
        if (ingredients != null && !ingredients.isEmpty()) {
            return; // 长度 > 0：一个字都不改，行为和没有这个 mixin 时完全一致
        }
        // 只有“空表”（长度 0，实机崩的就是这种）和“压根没有表”才会走到这里：
        // 两种情况原代码都会在 getIngredients().set(0, …) 处炸（IOOBE / NPE），所以一起拦。

        try {
            final List<SequencedRecipe<?>> sequence = assemblyRecipe.getSequence();
            final int sequenceSize = sequence == null ? -1 : sequence.size();
            // SequencedRecipe 没有覆写 equals（javap 核实），indexOf 走 Object 身份比较，拿到的就是本步的下标。
            final int stepIndex = sequence == null ? -1 : sequence.indexOf((SequencedRecipe<?>) (Object) this);

            final StringBuilder resultsText = new StringBuilder();
            for (final ItemStack stack : stepRecipe.getRollableResultsAsItemStacks()) {
                if (resultsText.length() > 0) {
                    resultsText.append(", ");
                }
                // 未注册/查不到的物品 getKey() 会返回 null，字符串拼接会打成 "null" 而不是抛异常。
                resultsText.append(stack.isEmpty()
                        ? "<empty>"
                        : BuiltInRegistries.ITEM.getKey(stack.getItem()) + "x" + stack.getCount());
            }
            final ItemStack transitionalItem = assemblyRecipe.getTransitionalItem();
            final String transitionalText = transitionalItem == null || transitionalItem.isEmpty()
                    ? "<empty>"
                    : BuiltInRegistries.ITEM.getKey(transitionalItem.getItem()) + "x" + transitionalItem.getCount();

            // 坏的那一步 ingredients 是空的、输出也可能解不出来，光看它认不出是哪条配方；
            // 但上一步/下一步（以及整条序列的形状）通常是完整的，所以把每一步的“类型(ingredients 个数)”打出来。
            final StringBuilder sequenceText = new StringBuilder();
            if (sequence != null) {
                for (final SequencedRecipe<?> step : sequence) {
                    final ProcessingRecipe<?, ?> inner = step == null ? null : step.getRecipe();
                    final NonNullList<Ingredient> innerIngredients = inner == null ? null : inner.getIngredients();
                    if (sequenceText.length() > 0) {
                        sequenceText.append(" -> ");
                    }
                    sequenceText.append(inner == null || inner.getTypeInfo() == null
                            || inner.getTypeInfo().getId() == null
                            ? (inner == null ? "<null>" : inner.getClass().getSimpleName())
                            : inner.getTypeInfo().getId().getPath());
                    sequenceText.append("(ing=").append(innerIngredients == null ? -1 : innerIngredients.size())
                            .append(')');
                }
            }

            // 只有坏配方才会走到这里，所以直接在调用点取 logger（不引入静态字段，避免往目标类里合并 <clinit>）。
            // logger 名与 ClockworkMod.LOGGER 相同，输出到同一个频道。
            LoggerFactory.getLogger("Clockwork").warn(
                    "[Clockwork] 防御性拦截坏的 create:sequenced_assembly 步骤：第 {}/{} 步（firstStep={}）的 ingredients 为空。"
                            + "Create 接下来会执行 getIngredients().set(0, …) 抛 IndexOutOfBoundsException，而 RecipeManager 只捕获"
                            + " IllegalArgumentException/JsonParseException，所以这会直接把游戏打崩。已跳过该步（该步本身已坏，"
                            + " 本来就无法装配）。定位：stepType={}，stepClass={}，stepResults={}，transitionalItem={}，顶层原料={}，"
                            + "整条序列={}（ing=0 的就是坏步骤）；常见原因是这条配方该步的 ingredients 是空数组、或引用了未注册物品"
                            + "（可用 tools/check_data_refs.py、tools/scan_seq.py 扫出来）。",
                    stepIndex, sequenceSize, firstStep,
                    stepRecipe.getTypeInfo() == null || stepRecipe.getTypeInfo().getId() == null
                            ? "<unknown>" : stepRecipe.getTypeInfo().getId().toString(),
                    stepRecipe.getClass().getName(), resultsText, transitionalText,
                    String.valueOf(assemblyRecipe.getIngredient()), sequenceText);
        } catch (final Throwable diagnosticFailure) {
            // 收集诊断信息本身出问题（例如以后 Create 改了这些 getter）也不能影响“不崩”这个主目标。
            LoggerFactory.getLogger("Clockwork").warn(
                    "[Clockwork] 防御性拦截：坏的 create:sequenced_assembly 步骤（诊断信息收集失败：{}）",
                    diagnosticFailure.toString());
        }

        ci.cancel();
    }
}
