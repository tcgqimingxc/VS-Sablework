package org.valkyrienskies.clockwork.integration.ponder;

import com.tterrag.registrate.util.entry.ItemProviderEntry;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.TextElementBuilder;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.foundation.PonderSceneBuilder;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.valkyrienskies.clockwork.ClockworkMod;
import org.valkyrienskies.clockwork.content.contraptions.CWContraptionsIndex;
import org.valkyrienskies.clockwork.content.curiosities.CWInstrumentsIndex;
import org.valkyrienskies.clockwork.content.curiosities.tools.CWToolsIndex;
import org.valkyrienskies.clockwork.content.kinetics.CWKineticsIndex;
import org.valkyrienskies.clockwork.content.logistics.gas.CWGasIndex;
import org.valkyrienskies.clockwork.integration.ponder.instruction.AnimateShipSectionInstruction;
import org.valkyrienskies.clockwork.integration.ponder.scenes.AltMeterScenes;
import org.valkyrienskies.clockwork.integration.ponder.scenes.CamberedWingsScenes;
import org.valkyrienskies.clockwork.integration.ponder.scenes.DuctScenes;
import org.valkyrienskies.clockwork.integration.ponder.scenes.FlapBearingScenes;
import org.valkyrienskies.clockwork.integration.ponder.scenes.GyroScenes;
import org.valkyrienskies.clockwork.integration.ponder.scenes.PlaneTipsScenes;
import org.valkyrienskies.clockwork.integration.ponder.scenes.ResistorScenes;
import org.valkyrienskies.clockwork.integration.ponder.scenes.SmartFlapBearingScenes;
import org.valkyrienskies.clockwork.integration.ponder.scenes.SolidDeliveryScenes;
import org.valkyrienskies.clockwork.integration.ponder.scenes.WanderwandScenes;
import org.valkyrienskies.clockwork.integration.ponder.scenes.WingsScenes;

/**
 * Clockwork 的 Ponder 场景注册表 —— <b>本模组唯一的场景注册处</b>。
 *
 * <p>场景资源来自官方发布包的 {@code assets/vs_clockwork/ponder/*.nbt}（11 个，与工程树逐字节一致，
 * 见 {@code docs/OFFICIAL-ASSETS.md} §3.3），场景语义（文本顺序、动画、指向点）来自旧版
 * {@code Clockwork-1.20.1-main/common/src/main/kotlin/org/valkyrienskies/clockwork/ClockworkPonders.kt}
 * 及其 {@code content/ponders/*.kt}。
 *
 * <p><b>注册约定</b>（新增/删除场景只改下面这一段，`tools/check_ponder.py` 会静态校验）：
 * <ul>
 *   <li>一行一个场景：{@code entries.forComponents(组件...).addStoryBoard("nbt 名", 场景类::方法)}；</li>
 *   <li>每个场景类的 {@code scene.title("<id>", ...)} 必须与这里的 nbt 名<b>完全一致</b> ——
 *       Ponder 用 title 的 id 组 lang 键（{@code vs_clockwork.ponder.<id>.header / .text_N}），
 *       不是用 nbt 名，也不是用组件 id；</li>
 *   <li>nbt 文件必须存在于 {@code assets/vs_clockwork/ponder/<id>.nbt}，否则加载期直接抛异常。</li>
 * </ul>
 *
 * <p>官方 nbt ↔ 组件 ↔ 旧版场景的对应关系见每行上方的注释。
 *
 * <p><b>整体移植差异</b>（逐场景的细节写在各场景类的类注释里）：
 * <ul>
 *   <li>旧版 {@code moveSectionAsShip}（VS2 指令）换成
 *       {@link AnimateShipSectionInstruction}，语义相同（惯性 + 衰减），见 {@link #animateAsShip}；</li>
 *   <li>旧版 {@code sparklingPlane} 自绘粉尘粒子平面换成扁盒描边（{@link #chasePlane}），观感略简但无粒子依赖；</li>
 *   <li>旧版 {@code chaseParallelogram} 是本仓库的 Kotlin 扩展，这里 1:1 移植为 {@link #chaseParallelogram}。</li>
 * </ul>
 */
public final class ClockworkPonderScenes {

    private ClockworkPonderScenes() {
    }

    public static void register(final PonderSceneRegistrationHelper<ResourceLocation> helper) {
        final PonderSceneRegistrationHelper<ItemProviderEntry<?, ?>> entries =
                helper.withKeyFunction(ItemProviderEntry::getId);

        // ===================== 官方 11 个 ponder nbt：一行一个场景 =====================
        // wanderwand —— 官方 wanderwand.nbt；旧版 PhysicsPonders#createShip；差异：结构下沉改用逐段 moveSection。
        entries.forComponents(CWToolsIndex.WANDERWAND, CWContraptionsIndex.PHYSICS_INFUSER).addStoryBoard("wanderwand", WanderwandScenes::wanderwand);
        // resistor —— 官方 resistor.nbt；旧版 KineticPonders#redstoneResistor；差异：文本改用 lang 键（旧版是硬编码英文）。
        entries.forComponents(CWKineticsIndex.REDSTONE_RESISTOR).addStoryBoard("resistor", ResistorScenes::resistor);
        // alt_meter —— 官方 alt_meter.nbt；旧版 PhysicsPonders#altMeter；差异：sparklingPlane 换 chasePlane，Nixie NBT 键已核对为 6.0.10 的 RedstoneStrength。
        entries.forComponents(CWInstrumentsIndex.ALT_METER).addStoryBoard("alt_meter", AltMeterScenes::altMeter);
        // flap_bearing —— 官方 flap_bearing.nbt；旧版 PhysicsPonders#flap；差异：TargetAngle 的 NBT 键与本移植 AbstractBearingBlockEntity 一致，可照写。
        entries.forComponents(CWContraptionsIndex.ANDESITE_FLAP_BEARING, CWContraptionsIndex.SMART_FLAP_BEARING).addStoryBoard("flap_bearing", FlapBearingScenes::flapBearing);
        // smart_flap_bearing —— 官方 smart_flap_bearing.nbt；旧版 PhysicsPonders#smart_flap；差异：无（只用到 Filter 槽提示）。
        entries.forComponents(CWContraptionsIndex.SMART_FLAP_BEARING).addStoryBoard("smart_flap_bearing", SmartFlapBearingScenes::smartFlapBearing);
        // plane_tips —— 官方 plane_tips.nbt；旧版 PhysicsPonders#planeTips；差异：chaseParallelogram 已 1:1 移植；末尾 "flap bearing 最佳实践" 旧版就没做，保持未做。
        entries.forComponents(CWContraptionsIndex.ANDESITE_FLAP_BEARING, CWContraptionsIndex.SMART_FLAP_BEARING, CWKineticsIndex.FLAP, CWKineticsIndex.WING).addStoryBoard("plane_tips", PlaneTipsScenes::planeTips);
        // wings —— 官方 wings.nbt；旧版 PhysicsPonders#wings；差异：升力数值由 Sable lift provider 接管，场景只演示"翼面形状/阻力"概念。
        entries.forComponents(CWKineticsIndex.FLAP, CWKineticsIndex.WING).addStoryBoard("wings", WingsScenes::wings);
        // cambered_wings —— 官方 cambered_wings.nbt；旧版 PhysicsPonders#camberedWings；差异：moveSectionAsShip 换 animateAsShip。
        entries.forComponents(CWKineticsIndex.FLAP, CWKineticsIndex.WING).addStoryBoard("cambered_wings", CamberedWingsScenes::camberedWings);
        // gyro —— 官方 gyro.nbt；旧版 PhysicsPonders#gyro；差异：VS2 稳定力矩换成 Sable 反作用轮，场景仍用四向模拟杆演示"输入 → 姿态被稳定"。
        entries.forComponents(CWContraptionsIndex.GYRO).addStoryBoard("gyro", GyroScenes::gyro);
        // solid_delivery —— 官方 solid_delivery.nbt；旧版 OtherPonders#solid_delivery；差异：无（Depot/Chute/Belt/配重弹出器都是 Create 方块）。
        entries.forComponents(CWGasIndex.DELIVERY_CANNON, CWGasIndex.DELIVERY_CHUTE).addStoryBoard("solid_delivery", SolidDeliveryScenes::solidDelivery);
        // duct —— 官方 duct.nbt；旧版 KelvinPonders#duct；差异：无结构差异。
        // 单向 / 过滤 / 智能是"导管某个面的边类型"（旧版机制），不是独立方块，
        // 所以场景只挂在 duct 上 —— 用螺丝刀在接缝上循环切换（见 DuctScenes 类注释）。
        entries.forComponents(CWGasIndex.DUCT).addStoryBoard("duct", DuctScenes::gasDuct);
    }

    /**
     * 取当前场景第 {@code index} 条文本（{@code vs_clockwork.ponder.<scene>.text_N}）。
     *
     * <p>公开是因为有几个场景（gyro）的文本不指向任何方块，需要直接用
     * {@code overlay().showText(ticks).text(...)} 起头。
     */
    public static String text(final PonderSceneBuilder scene, final int index) {
        return Component.translatable(ClockworkMod.MOD_ID + ".ponder." + scene.getScene().getId().getPath()
                + ".text_" + index).getString();
    }

    /**
     * 绑一段指向目标点的场景文本。
     *
     * <p>旧版是 Kotlin 扩展函数 {@code PonderSceneBuilder.ponderLang(index)} + 中缀调用链
     * （{@code showText(60).text(scene.ponderLang(1)).pointAt(...)}）；Java 没有扩展函数，
     * 因此把"取 lang 文本 + 建 TextElement + 指向目标"合成这一个工具方法，让每个场景只写一行。
     */
    public static TextElementBuilder text(final PonderSceneBuilder scene, final int index, final int duration,
                                          final Vec3 target) {
        return scene.overlay().showText(duration).text(text(scene, index)).pointAt(target);
    }

    /**
     * 让结构段像"被物理引擎接管的飞船"一样按惯性移动/旋转（旧版 {@code moveSectionAsShip} 的移植）。
     *
     * @param velocity         初始线速度，格/tick
     * @param rotationVelocity 初始角速度，度/tick
     */
    public static void animateAsShip(final PonderSceneBuilder scene, final ElementLink<WorldSectionElement> link,
                                     final int ticks, final Vec3 velocity, final Vec3 rotationVelocity) {
        scene.addInstruction(new AnimateShipSectionInstruction(link, ticks, velocity, rotationVelocity));
    }

    /**
     * 画一个"海拔平面"：把立方体区域沿 {@code face} 压扁后描边（旧版 {@code sparklingPlane} 的替代）。
     *
     * <p>旧版把平面压到零厚度、再靠自绘粉尘粒子让它可见；这里改成 0.01 格厚的薄盒，
     * 只靠 Ponder 自带的描边就够了，不引入粒子 API（也就没有粒子相关的不确定性）。
     * 压扁方向与旧版一致：UP→贴 minY、DOWN→贴 maxY、EAST→贴 minX、WEST→贴 maxX、
     * NORTH→贴 maxZ、SOUTH→贴 minZ。
     */
    public static void chasePlane(final PonderSceneBuilder scene, final PonderPalette color, final AABB area,
                                  final Direction face, final int ticks) {
        final double t = 0.01;
        final AABB plane = switch (face) {
            case UP -> new AABB(area.minX, area.minY - t, area.minZ, area.maxX, area.minY + t, area.maxZ);
            case DOWN -> new AABB(area.minX, area.maxY - t, area.minZ, area.maxX, area.maxY + t, area.maxZ);
            case WEST -> new AABB(area.maxX - t, area.minY, area.minZ, area.maxX + t, area.maxY, area.maxZ);
            case EAST -> new AABB(area.minX - t, area.minY, area.minZ, area.minX + t, area.maxY, area.maxZ);
            case NORTH -> new AABB(area.minX, area.minY, area.maxZ - t, area.maxX, area.maxY, area.maxZ + t);
            case SOUTH -> new AABB(area.minX, area.minY, area.minZ - t, area.maxX, area.maxY, area.minZ + t);
        };
        scene.overlay().chaseBoundingBoxOutline(color, plane, plane, ticks);
    }

    /**
     * 在某个面上画一个倾斜的平行四边形轮廓（1:1 移植旧版 {@code ClockworkPonders.kt#chaseParallelogram}）。
     *
     * <p>用途：cambered_wings / plane_tips 里表示"翼面相对气流的攻角"。
     * {@code origin} 是平行四边形的一个角，{@code width}/{@code height} 是两条边，{@code angleRad} 是倾角。
     */
    public static void chaseParallelogram(final PonderSceneBuilder scene, final PonderPalette color,
                                          final Vec3 origin, final double width, final double height,
                                          final double angleRad, final Direction face, final int ticks) {
        // 2D 旋转：宽度沿 +x 起转，高度沿 +y 起转（与旧版 rotate2D 的公式一致）
        final double cos = Math.cos(angleRad);
        final double sin = Math.sin(angleRad);
        final double widthX = width * cos;
        final double widthY = width * sin;
        final double heightX = -height * sin;
        final double heightY = height * cos;

        final Vec3 bottomLeft = origin;
        final Vec3 bottomRight = mapToFace(origin, widthX, widthY, face);
        final Vec3 topLeft = mapToFace(origin, heightX, heightY, face);
        final Vec3 topRight = topLeft.add(bottomRight.subtract(bottomLeft));

        scene.overlay().showLine(color, bottomLeft, bottomRight, ticks);
        scene.overlay().showLine(color, bottomRight, topRight, ticks);
        scene.overlay().showLine(color, topRight, topLeft, ticks);
        scene.overlay().showLine(color, topLeft, bottomLeft, ticks);
    }

    /** 把平面坐标 (dx, dy) 映射到指定朝向的面上（与旧版 map() 的三个分支一致）。 */
    private static Vec3 mapToFace(final Vec3 origin, final double dx, final double dy, final Direction face) {
        return switch (face) {
            case UP, DOWN -> new Vec3(origin.x + dx, origin.y, origin.z + dy);
            case NORTH, SOUTH -> new Vec3(origin.x + dx, origin.y + dy, origin.z);
            case EAST, WEST -> new Vec3(origin.x, origin.y + dy, origin.z + dx);
        };
    }
}
