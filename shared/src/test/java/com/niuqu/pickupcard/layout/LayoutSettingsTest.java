package com.niuqu.pickupcard.layout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 布局设置的夹逼。锚点（anchorX/anchorY，2026-09-18 定案）是这一类的重心：
 * 它存的是<b>画布分数</b>，跨 GUI 缩放档稳定；-1 是"自动"哨兵，必须活着穿过夹逼。
 */
class LayoutSettingsTest {

    @Test
    void defaultsAnchorAutomatically() {
        LayoutSettings d = LayoutSettings.defaults();
        assertEquals(LayoutSettings.AUTO_ANCHOR, d.anchorX(), "横向自动 = 竖条成线的老公式");
        assertEquals(LayoutSettings.AUTO_ANCHOR, d.anchorY(), "纵向自动 = 准星下方");
        assertEquals(LayoutSettings.Appear.SLIDE, d.appearMode());
        assertEquals(4f, d.separation(), 1e-6);
        assertEquals(LayoutSettings.AUTO_SCALE, d.scalePercent(), "默认是自动缩放");
    }

    @Test
    void separationIsClamped() {
        assertEquals(0f, sepSanitized(-3f).separation(), 1e-6, "负数当 0");
        assertEquals(32f, sepSanitized(999f).separation(), 1e-6, "上限 32");
        assertEquals(7.5f, sepSanitized(7.5f).separation(), 1e-6);
    }

    private static LayoutSettings sepSanitized(float separation) {
        return new LayoutSettings(LayoutSettings.Appear.SLIDE, separation,
                LayoutSettings.AUTO_SCALE, LayoutSettings.AUTO_ANCHOR, LayoutSettings.AUTO_ANCHOR)
                .sanitized();
    }

    @Test
    @DisplayName("锚点只许两种值：-1 哨兵，或 0..1 的比例；别的统统回自动")
    void anchorSanitizing() {
        assertEquals(LayoutSettings.AUTO_ANCHOR, sanitized(-1f, -1f).anchorX(), "-1 活着穿过");
        assertEquals(LayoutSettings.AUTO_ANCHOR, sanitized(-1f, -1f).anchorY(), "-1 活着穿过");
        assertEquals(0.3f, sanitized(0.3f, 0.8f).anchorX(), 1e-6, "合法比例原样保留");
        assertEquals(0.8f, sanitized(0.3f, 0.8f).anchorY(), 1e-6);
        assertEquals(LayoutSettings.AUTO_ANCHOR, sanitized(-0.5f, 1.7f).anchorX(), "越界回自动 —— 宁可回默认也不猜");
        assertEquals(LayoutSettings.AUTO_ANCHOR, sanitized(-0.5f, 1.7f).anchorY());
    }

    private static LayoutSettings sanitized(float x, float y) {
        return new LayoutSettings(LayoutSettings.Appear.SLIDE, 4f,
                LayoutSettings.AUTO_SCALE, x, y).sanitized();
    }

    @Test
    @DisplayName("锚点解析：-1 给公式/准星，分数给比例")
    void anchorResolution() {
        LayoutSettings auto = LayoutSettings.defaults();
        assertEquals(LayoutSettings.autoLeftEdge(426f), auto.anchorLeft(426f), 1e-6);
        assertEquals(240f * LayoutSettings.DEFAULT_ANCHOR_Y, auto.anchorTop(240f), 1e-6,
                "自动纵向 = 准星下方 55%");

        LayoutSettings dragged = new LayoutSettings(LayoutSettings.Appear.SLIDE, 4f,
                LayoutSettings.AUTO_SCALE, 0.75f, 0.6f);
        assertEquals(426f * 0.75f, dragged.anchorLeft(426f), 1e-6);
        assertEquals(240f * 0.6f, dragged.anchorTop(240f), 1e-6);
    }

    @Test
    @DisplayName("锚点夹取：拖得太低时第一张卡自动抬到 HUD 带上方")
    void anchorTopClampsAboveTheHudBand() {
        LayoutSettings low = new LayoutSettings(LayoutSettings.Appear.SLIDE, 4f,
                LayoutSettings.AUTO_SCALE, 0.7f, 0.98f);
        // 240 高、留白 75、卡高 20 → 第一张卡的顶边最高只能到 240-75-20 = 145
        assertEquals(145f, low.anchorTop(240f, 20f, 75), 1e-6);
        // 正常锚点不受夹取影响
        LayoutSettings mid = new LayoutSettings(LayoutSettings.Appear.SLIDE, 4f,
                LayoutSettings.AUTO_SCALE, LayoutSettings.AUTO_ANCHOR, LayoutSettings.AUTO_ANCHOR);
        assertEquals(240f * LayoutSettings.DEFAULT_ANCHOR_Y, mid.anchorTop(240f, 20f, 75), 1e-6);
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
        LayoutSettings half = new LayoutSettings(LayoutSettings.Appear.SLIDE, 4f, 50, -1f, -1f);
        assertEquals(0.5f, half.scale(10_000f, 20f, 5, 4f), 0.001f, "手动档与可用高度无关");
        assertEquals(LayoutSettings.MAX_SCALE_PERCENT,
                new LayoutSettings(LayoutSettings.Appear.SLIDE, 4f, 9_999, -1f, -1f)
                        .sanitized().scalePercent());
        assertEquals(LayoutSettings.MIN_SCALE_PERCENT,
                new LayoutSettings(LayoutSettings.Appear.SLIDE, 4f, 10, -1f, -1f)
                        .sanitized().scalePercent());
        assertTrue(half.anchorTop(240f) > 0f, "顺带守一下：构造不再需要已删除的 Side/leftEdge");
    }
}
