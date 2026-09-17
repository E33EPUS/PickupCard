package com.niuqu.pickupcard.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 布局设置的夹逼。<b>卡片间距</b>是 2026-09-17 新增（原来写死在 {@code CardStage.STACK_GAP}），
 * 所以这里钉住它的默认值与上下限 —— 它是"卡与卡"的距离，跟卡内间隙不是一回事。
 */
class LayoutSettingsTest {

    @Test
    void defaultsAreLeftAnchoredAutomatic() {
        LayoutSettings d = LayoutSettings.defaults();
        assertEquals(LayoutSettings.Side.LEFT, d.stickTo());
        assertEquals(LayoutSettings.AUTO_LEFT_EDGE, d.leftEdge(), "-1 就是「跟着画布算」");
        assertEquals(LayoutSettings.Appear.SLIDE, d.appearMode());
        assertEquals(4f, d.separation(), 1e-6);
    }

    @Test
    void separationIsClamped() {
        assertEquals(0f, sanitized(-3f).separation(), 1e-6, "负数当 0");
        assertEquals(32f, sanitized(999f).separation(), 1e-6, "上限 32");
        assertEquals(7.5f, sanitized(7.5f).separation(), 1e-6);
    }

    /** 自动锚点那个 -1 必须能活着穿过夹逼（它不是一个"非法的负数"）。 */
    @Test
    void autoLeftEdgeSurvivesSanitizing() {
        assertEquals(LayoutSettings.AUTO_LEFT_EDGE, sanitized(4f).leftEdge());
        assertTrue(new LayoutSettings(LayoutSettings.Side.LEFT, -2, LayoutSettings.Appear.SLIDE, 4f)
                .sanitized().leftEdge() >= 0, "别的负数按 0 处理");
    }

    private static LayoutSettings sanitized(float separation) {
        return new LayoutSettings(LayoutSettings.Side.LEFT, LayoutSettings.AUTO_LEFT_EDGE,
                LayoutSettings.Appear.SLIDE, separation).sanitized();
    }
}
