package com.niuqu.pickupcard.render.nvg.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 界面上那几处短动画的补间。
 * <p>
 * 【为什么值得钉】这些动画的坏法都不报错：悬停亮不起来（每帧重开）、切页时跳变、
 * 动画终点停在 0.98 再也不动。前两条是"看着不对劲但说不清"，第三条是"鼠标移开就没人发现"。
 * 全是纯函数，不必起游戏。
 */
class TweenTest {

    @Test
    @DisplayName("走到头就停在目标值上，不会过冲也不会差一点")
    void settlesExactly() {
        Tween t = Tween.at(0f, 0L);
        t.retarget(1f, 1_000L, 180L);
        assertEquals(0f, t.at(1_000L), 1e-4, "刚开始那一帧还是起点");
        assertTrue(t.moving(1_000L), "刚起步是在动");
        assertTrue(t.at(1_090L) > 0.5f, "中段要走过一半（easeOutCubic 前快后慢）");
        assertEquals(1f, t.at(1_180L), 1e-4, "到点就是目标值");
        assertEquals(1f, t.at(9_999L), 1e-4, "过了点还是目标值");
        assertFalse(t.moving(9_999L), "到点就不动了");
    }

    @Test
    @DisplayName("换目标从中途的值出发：不跳变，也不在同一个目标上反复重开")
    void retargetIsContinuousAndIdempotent() {
        Tween t = Tween.at(0f, 0L);
        t.retarget(1f, 0L, 100L);
        float mid = t.at(50L);
        assertTrue(mid > 0f && mid < 1f, "中段应该在中间：" + mid);

        // 中途掉头：起点必须是"此刻的值"，不是 0
        t.retarget(0f, 50L, 100L);
        assertEquals(mid, t.at(50L), 1e-4, "掉头的那一帧不能跳");

        // 悬停每一帧都会问一次：目标没变就什么都不做，否则动画永远停在第一帧
        t.retarget(0f, 60L, 100L);
        t.retarget(0f, 70L, 100L);
        assertEquals(0f, t.at(150L), 1e-4, "反复重申同一个目标之后仍然会走到位");
    }

    @Test
    @DisplayName("时长为 0 = 立刻到位；snap 之后不留上一段的进度")
    void instantAndSnap() {
        Tween t = Tween.at(3f, 0L);
        t.retarget(9f, 0L, 0L);
        assertEquals(9f, t.at(0L), 1e-4, "零时长立刻到位");

        t.snap(0f);
        assertEquals(0f, t.at(1L), 1e-4, "snap 完就是那个值");
        assertFalse(t.moving(1L), "snap 之后没有动画在跑");
    }

    @Test
    @DisplayName("颜色的淡出与插值：只动 alpha、端点精确")
    void fadeAndMix() {
        assertEquals(0xFF112233, NvgUi.fade(0xFF112233, 1f), "不透明时原样返回");
        assertEquals(0x80112233, NvgUi.fade(0xFF112233, 0.5f), "alpha 按比例乘（255×0.5≈128）");
        assertEquals(0x00112233, NvgUi.fade(0xFF112233, 0f), "透明度 0 = 全透明，RGB 不动");
        assertEquals(0x40112233, NvgUi.fade(0x80112233, 0.5f), "已经半透的再乘一半");

        assertEquals(0xFF000000, NvgUi.mix(0xFF000000, 0xFFFFFFFF, 0f), "t=0 就是起点色");
        assertEquals(0xFFFFFFFF, NvgUi.mix(0xFF000000, 0xFFFFFFFF, 1f), "t=1 就是终点色");
        assertEquals(0xFF808080, NvgUi.mix(0xFF000000, 0xFFFFFFFF, 0.5f), "中点是中灰");
        assertEquals(0xFF00FF00, NvgUi.mix(0xFFFF0000, 0xFF00FF00, 1f), "越界的 t 要夹住");
    }
}
