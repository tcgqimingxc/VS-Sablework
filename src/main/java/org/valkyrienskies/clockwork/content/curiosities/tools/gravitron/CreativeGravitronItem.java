package org.valkyrienskies.clockwork.content.curiosities.tools.gravitron;

/**
 * 超充重力枪（Supercharged Gravitron）。
 *
 * <p>对应旧版 {@code tools/gravitron/CreativeGravitronItem.kt}。与普通重力枪共用整套抓取 / 装配 / 解体逻辑，
 * 差别只在两点：
 * <ul>
 *   <li>抓住结构后潜行时会把结构直接 <em>瞬移对齐</em>到视线前方（旧版创造道具的"冻结/对齐"手感），
 *       而不是用冲量牵引；</li>
 *   <li>创造标签里的稀有度更高（RARE，见 {@code CWToolsIndex}）。</li>
 * </ul>
 */
public class CreativeGravitronItem extends GravitronItem {

    public CreativeGravitronItem(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean isSupercharged() {
        return true;
    }
}
