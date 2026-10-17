package org.valkyrienskies.clockwork.integration.ponder;

import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;
import net.createmod.catnip.registry.RegisteredObjectsHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;
import org.valkyrienskies.clockwork.ClockworkMod;
import org.valkyrienskies.clockwork.content.contraptions.CWContraptionsIndex;
import org.valkyrienskies.clockwork.content.curiosities.CWInstrumentsIndex;
import org.valkyrienskies.clockwork.content.curiosities.tools.CWToolsIndex;
import org.valkyrienskies.clockwork.content.kinetics.CWKineticsIndex;
import org.valkyrienskies.clockwork.content.logistics.gas.CWGasIndex;

/**
 * Clockwork 的 Ponder 标签。
 *
 * <p>对应旧版 {@code ClockworkPonderPlugin.registerTags}（本移植把它拆成独立类，与
 * Simulated 的 {@code SimPonderTags} 结构一致）。移植差异：
 * <ul>
 *   <li>本模组标签里的方块/物品必须<b>全部有场景</b>（否则玩家点进去是空白页）——
 *       现在 11 个场景都已注册，所以 {@link ClockworkPonderScenes} 里出现过的组件在这里都能找到。</li>
 *   <li>额外把气体物流挂到 Create 的 {@code LOGISTICS}、把动力件挂到
 *       {@code KINETIC_APPLIANCES} / {@code MOVEMENT_ANCHOR}（旧版没做这层挂接，
 *       属于"让 Create 老玩家更容易找到新方块"的补充）。</li>
 * </ul>
 */
public final class ClockworkPonderTags {

    /** 本模组自己的标签（旧版 {@code ClockworkPonderPlugin.CLOCKWORK_TAG}）。 */
    public static final ResourceLocation CLOCKWORK =
            ResourceLocation.fromNamespaceAndPath(ClockworkMod.MOD_ID, "cw_ponders");

    private ClockworkPonderTags() {
    }

    public static void register(final PonderTagRegistrationHelper<ResourceLocation> helper) {
        final PonderTagRegistrationHelper<ItemLike> itemHelper =
                helper.withKeyFunction(RegisteredObjectsHelper::getKeyOrThrow);

        helper.registerTag(CLOCKWORK)
                .addToIndex()
                .item(CWContraptionsIndex.PHYSICS_INFUSER.get())
                .title("VS: Clockwork")
                .description("Clockwork's physics contraptions and gas networks")
                .register();

        itemHelper.addToTag(CLOCKWORK)
                .add(CWToolsIndex.WANDERWAND.asItem())
                .add(CWContraptionsIndex.PHYSICS_INFUSER.asItem())
                .add(CWContraptionsIndex.ANDESITE_FLAP_BEARING.asItem())
                .add(CWContraptionsIndex.SMART_FLAP_BEARING.asItem())
                .add(CWContraptionsIndex.GYRO.asItem())
                .add(CWKineticsIndex.REDSTONE_RESISTOR.asItem())
                .add(CWKineticsIndex.WING.asItem())
                .add(CWKineticsIndex.FLAP.asItem())
                .add(CWInstrumentsIndex.ALT_METER.asItem())
                .add(CWGasIndex.DUCT.asItem())
                .add(CWGasIndex.PUMP_DUCT.asItem())
                .add(CWGasIndex.GAS_CRAFTER.asItem())
                .add(CWGasIndex.DELIVERY_CANNON.asItem())
                .add(CWGasIndex.DELIVERY_CHUTE.asItem());

        // Create 自带标签的挂接：让这些方块出现在玩家熟悉的分类里
        itemHelper.addToTag(AllCreatePonderTags.LOGISTICS)
                .add(CWGasIndex.DUCT.asItem())
                .add(CWGasIndex.PUMP_DUCT.asItem())
                .add(CWGasIndex.GAS_CRAFTER.asItem())
                .add(CWGasIndex.DELIVERY_CANNON.asItem())
                .add(CWGasIndex.DELIVERY_CHUTE.asItem());

        itemHelper.addToTag(AllCreatePonderTags.KINETIC_APPLIANCES)
                .add(CWContraptionsIndex.PHYS_BEARING.asItem())
                .add(CWContraptionsIndex.BRASS_PROPELLER_BEARING.asItem())
                .add(CWContraptionsIndex.ANDESITE_FLAP_BEARING.asItem())
                .add(CWContraptionsIndex.SMART_FLAP_BEARING.asItem())
                .add(CWContraptionsIndex.GYRO.asItem())
                .add(CWKineticsIndex.REDSTONE_RESISTOR.asItem());

        itemHelper.addToTag(AllCreatePonderTags.MOVEMENT_ANCHOR)
                .add(CWContraptionsIndex.PHYS_BEARING.asItem());
    }
}
