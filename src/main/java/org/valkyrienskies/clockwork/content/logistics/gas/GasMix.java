package org.valkyrienskies.clockwork.content.logistics.gas;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * 节点内的气体混合物：各组分质量 + 单一温度。
 *
 * <p>对应旧版 Kelvin 里"一个 DuctNode 内的气体状态"（{@code DuctNode.gasMass} + {@code temperature}）。
 * 旧版的气体状态由 Kelvin 求解器维护；这里由本模组自己维护。
 *
 * <p>单位约定：
 * <ul>
 *   <li>质量 [kg]，下标 = {@link GasType#index()}</li>
 *   <li>温度 [K]，同一节点内的气体视为完全混合（与旧版一致：Kelvin 每刻对节点内部做混合）</li>
 * </ul>
 *
 * <p>压力用理想气体定律的等价形式（见 {@link #pressure}），不引入摩尔数数组，避免每刻做除法。
 */
public final class GasMix {

    /** 质量小于该值视为空 [kg]。 */
    private static final double EPSILON = 1.0E-9;

    private final double[] mass = new double[GasTypes.count()];
    /** 温度 [K]。节点被抽空后保留最后一次的温度（视作管壁温度，护目镜仍可读）。 */
    private double temperature = GasPhysics.ambientTemperature();

    public double massOf(GasType type) {
        return mass[type.index()];
    }

    public double totalMass() {
        double total = 0;
        for (double m : mass) {
            total += m;
        }
        return total;
    }

    public boolean isEmpty() {
        return totalMass() <= EPSILON;
    }

    public double temperature() {
        return temperature;
    }

    public void setTemperature(double kelvin) {
        this.temperature = Math.max(1.0, kelvin);
    }

    /** 气体自身热容 [J/K]（不含管壁）。用于“搬走的气体带走多少能量”这种随质量移动的量。 */
    public double heatCapacity() {
        double capacity = 0;
        for (GasType type : GasTypes.all()) {
            capacity += mass[type.index()] * type.specificHeat();
        }
        return capacity;
    }

    /**
     * 节点热容 [J/K] = 气体热容 + 管壁热容。
     *
     * <p>为什么导热要用它而不是只用气体热容：节点温度是“金属管壁 + 管内气体”共同的热平衡温度，
     * 管壁（{@link GasPhysics#wallHeatCapacity()} = 44.9 J/K，取自旧版 {@code DuctPipeNode.heatCapacity}）
     * 也参与吸热/放热。用节点热容做串联热容，一次导热搬走的热量
     * {@code Q = k·ΔT·C₁C₂/(C₁+C₂)} 才对应真实温度变化，总热能 {@code C·T} 守恒。
     *
     * <p>与 {@link #heatCapacity()} 的分工：气体流动搬运的是“气体那份热容”（{@link #extract} /
     * {@link #absorb}），管壁不随气体移动；节点之间的导热则通过整块金属，所以用本方法。
     */
    public double nodeHeatCapacity() {
        return heatCapacity() + GasPhysics.wallHeatCapacity();
    }

    /**
     * 压力 [Pa]。
     *
     * <p>推导：节点容积固定为 V，由理想气体定律 P·V = (m/M)·R·T。把"额定容量 m₀ 在环境温度 T₀
     * 下对应环境压力 P₀"代入消去 V，得到与气体种类无关的简洁形式：
     * <pre>P = P₀ · (m / m₀) · (T / T₀)</pre>
     * （因为同温度同质量下不同气体的压力差异只体现在摩尔质量上，而 m₀ 也是同一种气体标定的；
     * 这里沿用旧版"以空气为基准标定管道容积"的做法，误差在玩法尺度内可接受。）
     *
     * @param capacityKg 该节点的额定容量 m₀ [kg]
     */
    public double pressure(double capacityKg) {
        if (capacityKg <= 0) {
            return 0;
        }
        double ambientT = GasPhysics.ambientTemperature();
        return GasPhysics.ambientPressure() * (totalMass() / capacityKg) * (temperature / ambientT);
    }

    /** 等温加入气体（新加入的气体先与环境温度无关，直接按当前节点温度并入）。 */
    public void addMass(GasType type, double kg) {
        if (kg <= 0 || Double.isNaN(kg)) {
            return;
        }
        mass[type.index()] += kg;
    }

    /** 移除指定气体的质量，返回实际移除量 [kg]。 */
    public double removeMass(GasType type, double kg) {
        if (kg <= 0) {
            return 0;
        }
        double removed = Math.min(kg, mass[type.index()]);
        mass[type.index()] -= removed;
        return removed;
    }

    /** 按比例整体缩放（用于泄漏/排气）。 */
    public void scale(double factor) {
        double f = Mth.clamp(factor, 0.0, 1.0);
        for (int i = 0; i < mass.length; i++) {
            mass[i] *= f;
            if (mass[i] < EPSILON) {
                mass[i] = 0;
            }
        }
    }

    /** 清空所有组分（保留温度，视作管壁温度）。结构合并时把旧主方块的气搬到新主方块后调用。 */
    public void clearMass() {
        for (int i = 0; i < mass.length; i++) {
            mass[i] = 0;
        }
    }

    /**
     * 抽取质量为 {@code kg} 的一份气体，组分比例与温度与本混合物一致。
     *
     * <p>用于节点间的流动：质量与热量一起被带走（气体的焓搬运）。
     * 抽走的是“与源节点同温度的一份气”，所以源节点温度不变（减少的只是它的气体热容，
     * 管壁仍留在原地，见 {@link #nodeHeatCapacity()}）。
     */
    public GasMix extract(double kg) {
        return extract(kg, null);
    }

    /**
     * 抽取质量为 {@code kg} 的一份气体，但<b>跳过</b> {@code excluded} 这一种（黑名单过滤用）。
     *
     * <p>为什么黑名单不能直接复用"只搬指定气体"的白名单路径：白名单是"只允许 X 过"，
     * 一次搬运的对象就是 X；黑名单是"除 X 外都能过"，搬运对象是<b>其余组分的混合物</b>。
     * 这里按其余组分的相对比例抽取，被拦的气体一滴都不动 —— 否则含 X 的混合气会把 X 一起带过去，
     * 黑名单就形同虚设。
     *
     * <p>{@code excluded = null} 时与 {@link #extract(double)} 完全等价（走同一条路径，
     * 避免两份实现漂移）。
     */
    public GasMix extract(double kg, @Nullable GasType excluded) {
        GasMix taken = new GasMix();
        taken.setTemperature(temperature);
        double allowed = totalMass() - (excluded == null ? 0.0 : mass[excluded.index()]);
        if (allowed <= EPSILON || kg <= 0) {
            return taken;
        }
        // 分母是"允许通过的那部分质量"：请求 0.1 kg 时抽走的是 0.1 kg 其余气体，
        // 而不是 0.1 kg 里有 30% 是被拦气体、于是只有 0.07 kg 真正过去。
        double fraction = Math.min(1.0, kg / allowed);
        for (int i = 0; i < mass.length; i++) {
            if (excluded != null && i == excluded.index()) {
                continue;
            }
            double moved = mass[i] * fraction;
            taken.mass[i] = moved;
            mass[i] -= moved;
            if (mass[i] < EPSILON) {
                mass[i] = 0;
            }
        }
        return taken;
    }

    /**
     * 并入另一份气体，温度按<b>节点热容</b>（气体 + 管壁）加权平均（热量守恒）。
     *
     * <p>物理含义：并入的气体带来 {@code C_parcel·T_parcel} 的热能，与目的节点原有的
     * {@code C_node·T_node} 混合，温度 = 总热能 / 总热容。用 {@link #nodeHeatCapacity()}
     * 而不是只算气体热容，是为了让“管壁也参与吸热”与 {@link #addHeat(double)} 的口径一致：
     * 热气体进入冷管时先被管壁分走一部分热，节点不会因为一次小气流就跳到气源温度。
     *
     * <p>空节点（只有管壁）也走同一公式：管壁热容占主导，所以温度只会缓慢上升——
     * 这正符合“空管先要把金属壁烤热”的直觉，而不是让一股气瞬间把整根管子点亮。
     */
    public void absorb(GasMix other) {
        double otherMass = other.totalMass();
        if (otherMass <= EPSILON) {
            return;
        }
        double selfCapacity = nodeHeatCapacity();
        double otherCapacity = other.heatCapacity();
        double totalCapacity = selfCapacity + otherCapacity;
        if (totalCapacity <= 0) {
            return;
        }
        double mixedTemperature = (selfCapacity * temperature + otherCapacity * other.temperature)
                / totalCapacity;
        for (int i = 0; i < mass.length; i++) {
            mass[i] += other.mass[i];
        }
        setTemperature(mixedTemperature);
    }

    /** 加入热量 [J]，按“气体 + 管壁”总热容升温（见 {@link #nodeHeatCapacity()}）。 */
    public void addHeat(double joules) {
        double capacity = nodeHeatCapacity();
        if (capacity <= 0) {
            return;
        }
        setTemperature(temperature + joules / capacity);
    }

    /** 当前热力学能（相对 0 K）[J]，供护目镜显示。 */
    public double thermalEnergy() {
        return totalMass() * specificHeatOfMix() * temperature;
    }

    /** 混合物的质量加权比热容 [J/(kg·K)]。 */
    public double specificHeatOfMix() {
        double total = totalMass();
        if (total <= EPSILON) {
            return 0;
        }
        return heatCapacity() / total;
    }

    public void copyFrom(GasMix other) {
        System.arraycopy(other.mass, 0, mass, 0, mass.length);
        temperature = other.temperature;
    }

    // ===================== NBT =====================

    /** 保存到 NBT：{@code GasMass} 为按 GasType 索引排列的质量数组，{@code GasTemperature} 为温度。 */
    public void save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (double m : mass) {
            list.add(DoubleTag.valueOf(m));
        }
        tag.put("GasMass", list);
        tag.putDouble("GasTemperature", temperature);
    }

    public void load(CompoundTag tag) {
        ListTag list = tag.getList("GasMass", Tag.TAG_DOUBLE);
        for (int i = 0; i < mass.length && i < list.size(); i++) {
            mass[i] = Math.max(0, list.getDouble(i));
        }
        if (tag.contains("GasTemperature")) {
            setTemperature(tag.getDouble("GasTemperature"));
        }
    }
}
