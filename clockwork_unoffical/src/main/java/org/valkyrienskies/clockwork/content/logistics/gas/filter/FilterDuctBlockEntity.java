package org.valkyrienskies.clockwork.content.logistics.gas.filter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.clockwork.content.logistics.gas.GasLink;
import org.valkyrienskies.clockwork.content.logistics.gas.GasNodeBlockEntity;
import org.valkyrienskies.clockwork.content.logistics.gas.GasNodeTooltip;
import org.valkyrienskies.clockwork.content.logistics.gas.GasType;
import org.valkyrienskies.clockwork.content.logistics.gas.GasTypes;

import java.util.List;

/**
 * 滤管方块实体：保存"目标气体 + 白/黑名单"设置，并把过滤规则提供给气体网络。
 *
 * <p>见 {@link FilterDuctBlock} 的类头注释（本方块旧版没有实现源码，属自行设计）。
 * 过滤在 {@code GasNetwork} 的搬运环节生效：
 * <ul>
 *   <li>{@link #gasFilter()} 返回非 null 时，本节点参与的所有连接只搬运该气体（白名单）。</li>
 *   <li>黑名单模式下返回 null，改用 {@link #gasLink(Direction)} 携带"除目标气体外的第一种气体"
 *       作为该面的过滤条件 —— 因为一条连接一次只能搬运一种气体，这是本简化设计的明确边界。</li>
 * </ul>
 */
public class FilterDuctBlockEntity extends GasNodeBlockEntity {

    /** 默认过滤种类：空气（世界初装时几乎一定有）。 */
    private GasType filterType = GasTypes.AIR;
    private boolean whitelist = true;

    public FilterDuctBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, 1.0);
    }

    @Nullable
    public GasType filterType() {
        return filterType;
    }

    public boolean isWhitelist() {
        return whitelist;
    }

    /** 循环切换过滤的气体：优先跳到"本节点当前真的有"的气体上，方便直接看到效果。 */
    public void cycleGas() {
        final List<GasType> all = GasTypes.all();
        if (all.isEmpty()) {
            return;
        }
        final int current = filterType == null ? -1 : filterType.index();
        for (int step = 1; step <= all.size(); step++) {
            final GasType candidate = all.get((current + step + all.size()) % all.size());
            if (gasState().contains(candidate)) {
                filterType = candidate;
                setChanged();
                return;
            }
        }
        filterType = all.get((current + 1 + all.size()) % all.size());
        setChanged();
    }

    /** 白名单 ↔ 黑名单。 */
    public void toggleMode() {
        whitelist = !whitelist;
        setChanged();
    }

    @Override
    @Nullable
    public GasType gasFilter() {
        return whitelist ? filterType : null;
    }

    @Override
    public GasLink gasLink(Direction dir) {
        if (whitelist || filterType == null) {
            return GasLink.PIPE;
        }
        // 黑名单：按"第一种不是被挡气体"的气体过（一条连接一次只能搬运一种气体）
        for (GasType type : GasTypes.all()) {
            if (type != filterType && gasState().contains(type)) {
                return GasLink.filtered(type);
            }
        }
        return GasLink.PIPE;
    }

    @Override
    public boolean gasConnected(Direction dir) {
        return getBlockState().getBlock() instanceof FilterDuctBlock
                && dir.getAxis() == getBlockState().getValue(FilterDuctBlock.AXIS);
    }

    @Override
    public boolean isGasPipe() {
        return true;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        GasNodeTooltip.addFunctionLine(getBlockState().getBlock(), tooltip);
        tooltip.add(Component.literal("    ")
                .append(Component.literal("Filter: ").withStyle(net.minecraft.ChatFormatting.GRAY))
                .append(Component.literal(String.format("%s %s", whitelist ? "only" : "block",
                                filterType == null ? "all" : filterType.displayName()))
                        .withStyle(net.minecraft.ChatFormatting.AQUA)));
        return GasNodeTooltip.add(this, tooltip, isPlayerSneaking);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        tag.putString("FilterGas", filterType == null ? "" : filterType.id().toString());
        tag.putBoolean("Whitelist", whitelist);
        super.write(tag, provider, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        final String id = tag.getString("FilterGas");
        filterType = id.isEmpty() ? null : GasTypes.byId(ResourceLocation.tryParse(id));
        whitelist = !tag.contains("Whitelist") || tag.getBoolean("Whitelist");
    }
}
