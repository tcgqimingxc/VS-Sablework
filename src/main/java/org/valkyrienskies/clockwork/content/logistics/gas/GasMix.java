package org.valkyrienskies.clockwork.content.logistics.gas;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;

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

    /** 气体自身热容 [J/K]（不含管壁）。 */
    public double heatCapacity() {
        double capacity = 0;
        for (GasType type : GasTypes.all()) {
            capacity += mass[type.index()] * type.specificHeat();
        }
        return capacity;
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

    /**
     * 抽取质量为 {@code kg} 的一份气体，组分比例与温度与本混合物一致。
     *
     * <p>用于节点间的流动：质量与热量一起被带走。
     */
    public GasMix extract(double kg) {
        GasMix taken = new GasMix();
        taken.setTemperature(temperature);
        double total = totalMass();
        if (total <= EPSILON || kg <= 0) {
            return taken;
        }
        double fraction = Math.min(1.0, kg / total);
        for (int i = 0; i < mass.length; i++) {
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
     * 并入另一份气体，温度按质量热容加权平均（热量守恒）。
     *
     * <p>若并入的气体量极少而本节点为空，则直接采用对方的温度。
     */
    public void absorb(GasMix other) {
        double otherMass = other.totalMass();
        if (otherMass <= EPSILON) {
            return;
        }
        double selfCapacity = heatCapacity();
        double otherCapacity = other.heatCapacity();
        double selfMass = totalMass();
        double mixedTemperature;
        if (selfMass <= EPSILON || selfCapacity <= 0) {
            mixedTemperature = other.temperature;
        } else {
            mixedTemperature = (selfCapacity * temperature + otherCapacity * other.temperature)
                    / (selfCapacity + otherCapacity);
        }
        for (int i = 0; i < mass.length; i++) {
            mass[i] += other.mass[i];
        }
        setTemperature(mixedTemperature);
    }

    /** 加入热量 [J]，按"气体 + 管壁"总热容升温。 */
    public void addHeat(double joules) {
        double capacity = heatCapacity() + GasPhysics.wallHeatCapacity();
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
