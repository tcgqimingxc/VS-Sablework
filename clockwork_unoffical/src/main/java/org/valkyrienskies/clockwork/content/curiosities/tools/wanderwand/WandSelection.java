package org.valkyrienskies.clockwork.content.curiosities.tools.wanderwand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;

/**
 * 游荡法杖的选区数据。
 *
 * <p>对应旧版 {@code WanderwandItem.kt} 里 {@code selectedBlocks} 的读写 + {@code AABBHelper.mergeAdjacentFast}
 * 的合并逻辑。旧版把选区存成 NBT 上的 {@code AABBSet} 列表（{@code BlockPos.asLong()} 记录两个角）。
 *
 * <p>移植差异：
 * <ul>
 *   <li>1.21.1 的 {@link ItemStack} 不能再直接读写 NBT，改用原版数据组件
 *       {@link DataComponents#CUSTOM_DATA}（{@link CustomData}）承载同样的结构，键名与旧版保持一致
 *       （{@code SelectedAreas / lowerCorner / upperCorner}）。</li>
 *   <li>旧版的 {@code mergeAdjacentFast} 是一个复杂的贪心分组合并；这里用等价的"反复把相交/相邻的盒
 *       合并成外接矩形直到稳定"实现，行为对玩家可见的部分一致（相邻选区会并成一块），代码量小很多。</li>
 *   <li><b>取消选择（DESELECT）</b>：旧版是 AABB 精确相减（{@code AABBHelper.subtractWithAABB}，可切出最多 6 个子盒）；
 *       这里退化为"删掉与新框相交的已选区域"（整块删除，不做几何切割）。玩家可见差异仅在"想从大选区里抠掉
 *       一小块"时：旧版留下被抠过的形状，这里会把整块选区删掉，提示会明确写出删了几块。</li>
 * </ul>
 *
 * <p><b>写入时机</b>：本类所有写操作都只应在**服务端**调用（{@code WanderwandItem} 的
 * {@code onItemUseFirst} 服务端分支），客户端不要写，避免两端数据打架。
 */
public final class WandSelection {

    /** 旧版 NBT 键 {@code selectedBlocks}；这里沿用同一名字便于存档对照。 */
    private static final String KEY_BOXES = "SelectedAreas";
    private static final String KEY_LOWER = "lowerCorner";
    private static final String KEY_UPPER = "upperCorner";

    /**
     * "半截角点"（选了 A、还没选 B）的键名。{@link WanderwandItem} 的读写也引这个常量，
     * 键名只有这一处定义，不会出现"改了一边、另一边还在按旧键名读"。
     */
    static final String KEY_CORNER = "WandCornerA";

    private WandSelection() {
    }

    /** 一个闭区间方块盒（两个角都是"含"的）。 */
    public record Box(BlockPos min, BlockPos max) {

        public static Box of(BlockPos a, BlockPos b) {
            return new Box(new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ())),
                    new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ())));
        }

        public boolean contains(BlockPos pos) {
            return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                    && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                    && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
        }

        /** 两个盒是否有公共方块（取消选择用：与新框相交的选区会被删掉）。 */
        public boolean intersects(Box other) {
            return min.getX() <= other.max.getX() && max.getX() >= other.min.getX()
                    && min.getY() <= other.max.getY() && max.getY() >= other.min.getY()
                    && min.getZ() <= other.max.getZ() && max.getZ() >= other.min.getZ();
        }

        /** 体积（方块数），用于给玩家显示选了多少格。 */
        public long volume() {
            return (long) (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
        }

        /** 外扩 1 格后是否与另一盒相接（用于"相邻即合并"）。 */
        private boolean touches(Box other) {
            return min.getX() - 1 <= other.max.getX() && max.getX() + 1 >= other.min.getX()
                    && min.getY() - 1 <= other.max.getY() && max.getY() + 1 >= other.min.getY()
                    && min.getZ() - 1 <= other.max.getZ() && max.getZ() + 1 >= other.min.getZ();
        }

        private Box union(Box other) {
            return new Box(
                    new BlockPos(Math.min(min.getX(), other.min.getX()), Math.min(min.getY(), other.min.getY()), Math.min(min.getZ(), other.min.getZ())),
                    new BlockPos(Math.max(max.getX(), other.max.getX()), Math.max(max.getY(), other.max.getY()), Math.max(max.getZ(), other.max.getZ())));
        }
    }

    public static List<Box> read(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return new ArrayList<>();
        }
        CompoundTag tag = data.copyTag();
        ListTag list = tag.getList(KEY_BOXES, Tag.TAG_COMPOUND);
        List<Box> boxes = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            boxes.add(new Box(BlockPos.of(entry.getLong(KEY_LOWER)), BlockPos.of(entry.getLong(KEY_UPPER))));
        }
        return boxes;
    }

    /**
     * 读取当前挂着的"半截角点"（{@link WanderwandItem} 里那个 {@code WandCornerA}）。
     *
     * <p>结构上它和长方体列表一样是法杖的选区数据，所以键名与读取逻辑都收在这里，
     * 免得客户端渲染器、服务端逻辑各写一份 {@code getLong} 而哪次改了键名只改一半。
     *
     * <p><b>调用方注意</b>：{@link #read} 与本方法都会走 {@link CustomData#copyTag()}（复制整棵 NBT），
     * 每帧调用会持续产生垃圾；客户端渲染请缓存结果，别在渲染循环里现读（见
     * {@code client/WanderwandSelectionRenderer}）。
     */
    public static BlockPos pendingCorner(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag tag = data.copyTag();
        return tag.contains(KEY_CORNER) ? BlockPos.of(tag.getLong(KEY_CORNER)) : null;
    }

    public static void write(ItemStack stack, List<Box> boxes) {
        if (boxes.isEmpty()) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(KEY_BOXES));
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            ListTag list = new ListTag();
            for (Box box : boxes) {
                CompoundTag entry = new CompoundTag();
                entry.putLong(KEY_LOWER, box.min().asLong());
                entry.putLong(KEY_UPPER, box.max().asLong());
                list.add(entry);
            }
            tag.put(KEY_BOXES, list);
        });
    }

    /**
     * 把新盒并入现有选区后**先算不写**，交给调用方检查体积上限再决定是否 {@link #write}。
     * 顺序敏感：合并会把相邻/相交的盒并成外接矩形，所以必须用合并后的总量去比上限。
     */
    public static List<Box> merged(ItemStack stack, Box box) {
        List<Box> boxes = read(stack);
        boxes.add(box);
        return merge(boxes);
    }

    /** 删掉与新框相交的选区并写入；返回剩余选区。 */
    public static List<Box> removeIntersecting(ItemStack stack, Box box) {
        List<Box> boxes = read(stack);
        boxes.removeIf(existing -> existing.intersects(box));
        write(stack, boxes);
        return boxes;
    }

    public static void clear(ItemStack stack) {
        write(stack, List.of());
    }

    /**
     * 选区总格数。合并后的盒之间不会重叠（{@code touches} 用的是外扩 1 格判定，任何重叠都会被并掉），
     * 所以直接相加即可，不用去重。
     */
    public static long totalVolume(List<Box> boxes) {
        long total = 0;
        for (Box box : boxes) {
            total += box.volume();
        }
        return total;
    }

    /** 反复合并"相接或相交"的盒，直到不再变化。 */
    private static List<Box> merge(List<Box> input) {
        List<Box> boxes = new ArrayList<>(input);
        boolean changed = true;
        while (changed) {
            changed = false;
            outer:
            for (int i = 0; i < boxes.size(); i++) {
                for (int j = i + 1; j < boxes.size(); j++) {
                    if (boxes.get(i).touches(boxes.get(j))) {
                        Box union = boxes.get(i).union(boxes.get(j));
                        boxes.remove(j);
                        boxes.set(i, union);
                        changed = true;
                        break outer;
                    }
                }
            }
        }
        return boxes;
    }
}
