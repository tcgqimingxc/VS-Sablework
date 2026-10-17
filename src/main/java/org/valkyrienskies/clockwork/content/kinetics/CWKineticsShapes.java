package org.valkyrienskies.clockwork.content.kinetics;

import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 动力部件切片用到的碰撞箱。
 *
 * <p>对应旧版 {@code ClockworkShapes} 里的 UNIVERSAL_SHAFT / SPINOFF_BEARING / WING 三项，
 * 数值原样搬运（单位 1/16 格）。旧版那三项都是 Create {@code AllShapes.Builder} 的语法糖，
 * 这里直接用 catnip 的 {@link VoxelShaper} 静态工厂，语义完全一致（默认参考方向 UP / Y）。
 */
public final class CWKineticsShapes {

    /** 通用轴：中心 6x6、向上 10 像素高的短轴（形状随 FACING 旋转）。 */
    public static final VoxelShaper UNIVERSAL_SHAFT =
            VoxelShaper.forDirectional(Block.box(5, 0, 5, 11, 10, 11), Direction.UP);

    /** 惰性轴承：下半砖体积（形状随 FACING 旋转）。 */
    public static final VoxelShaper SPINOFF_BEARING =
            VoxelShaper.forDirectional(Block.box(0, 0, 0, 16, 8, 16), Direction.UP);

    /** 机翼 / 襟翼：中间 8 像素厚的平板（形状随 AXIS 旋转）。 */
    public static final VoxelShaper WING =
            VoxelShaper.forAxis(Block.box(0, 4, 0, 16, 12, 16), Direction.Axis.Y);

    private CWKineticsShapes() {
    }
}
