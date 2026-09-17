package com.niuqu.pickupcard.layout;

import org.junit.jupiter.api.DisplayName;
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
        assertEquals(LayoutSettings.AUTO_SCALE, d.scalePercent(), "默认是自动缩放");
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
        assertTrue(new LayoutSettings(LayoutSettings.Side.LEFT, -2, LayoutSettings.Appear.SLIDE, 4f,
                LayoutSettings.AUTO_SCALE)
                .sanitized().leftEdge() >= 0, "别的负数按 0 处理");
    }

    private static LayoutSettings sanitized(float separation) {
        return new LayoutSettings(LayoutSettings.Side.LEFT, LayoutSettings.AUTO_LEFT_EDGE,
                LayoutSettings.Appear.SLIDE, separation, LayoutSettings.AUTO_SCALE).sanitized();
    }

    @Test
    @DisplayName("自动缩放：装得下恒为 100%，装不下按比例缩，下限 60%")
    void autoScale() {
        LayoutSettings auto = LayoutSettings.defaults();          // scalePercent = 0 = 自动
        assertEquals(1f, auto.scale(200f, 20f, 5, 4f), 0.001f, "装得下不该动它");
        // 5 张 20 高 + 4 个 4 间距 = 116；可用 87 → 87/116 = 0.75
        assertEquals(0.75f, auto.scale(87f, 20f, 5, 4f), 0.001f);
        // 可用只有 20 → 比例 0.17，但下限 60%
        assertEquals(0.6f, auto.scale(20f, 20f, 5, 4f), 0.001f, "缩到看不清不如少显示几张");
        assertEquals(1f, auto.scale(0f, 20f, 0, 4f), 0.001f, "没有卡就没有缩放");
    }

    @Test
    @DisplayName("手动档：用玩家给的数、不看装不装得下，且被夹进 50..200")
    void manualScale() {
        LayoutSettings half = new LayoutSettings(LayoutSettings.Side.LEFT,
                LayoutSettings.AUTO_LEFT_EDGE, LayoutSettings.Appear.SLIDE, 4f, 50);
        assertEquals(0.5f, half.scale(10_000f, 20f, 5, 4f), 0.001f, "手动档与可用高度无关");
        assertEquals(LayoutSettings.MAX_SCALE_PERCENT, new LayoutSettings(LayoutSettings.Side.LEFT,
                LayoutSettings.AUTO_LEFT_EDGE, LayoutSettings.Appear.SLIDE, 4f, 9_999)
                .sanitized().scalePercent());
        assertEquals(LayoutSettings.MIN_SCALE_PERCENT, new LayoutSettings(LayoutSettings.Side.LEFT,
                LayoutSettings.AUTO_LEFT_EDGE, LayoutSettings.Appear.SLIDE, 4f, 10)
                .sanitized().scalePercent());
    }
}
