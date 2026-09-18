package com.niuqu.pickupcard.layout;

import com.niuqu.pickupcard.layout.LayoutSettings.Appear;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 排布这件事的"已知答案"（2026-09-18 起为<b>顶锚</b>语义）。
 * <p>
 * 【为什么专钉锚点】handoff 里记着那次翻车：游戏与草稿锚点方向相反，像素门禁只逐张比
 * 卡的结构（竖条宽、间距…），不比对锚点，于是一路绿灯。所以这里专钉两件事：
 * <b>锚在哪</b>、<b>谁在哪一位</b>。顶锚 = 最新那张贴着锚点、旧的被挤下去（用户 2026-09-18 定案）。
 */
class StackLayoutTest {

    private static final float EPS = 0.001f;
    private static final int MARGIN = 16;
    /** 逻辑画布：1280x720 @ guiScale 3 → 427x240。 */
    private static final float GUI_W = 427f;
    private static final float GUI_H = 240f;
    /** 准星下方那条默认锚线（55% 高）。 */
    private static final float ANCHOR_Y = GUI_H * LayoutSettings.DEFAULT_ANCHOR_Y;

    private static LayoutSettings anchored(Float x, Float y) {
        return new LayoutSettings(Appear.SLIDE, LayoutSettings.DEFAULT_SEPARATION,
                LayoutSettings.AUTO_SCALE,
                x == null ? LayoutSettings.AUTO_ANCHOR : x,
                y == null ? LayoutSettings.AUTO_ANCHOR : y);
    }

    private static List<StackLayout.Slot> stack(LayoutSettings layout, StackLayout.Size... sizes) {
        return StackLayout.stack(List.of(sizes), GUI_W, GUI_H, layout, MARGIN, 75, 6f);
    }

    @Test
    @DisplayName("没有卡时不算出任何位置")
    void emptyListProducesNothing() {
        assertTrue(stack(anchored(null, null)).isEmpty());
    }

    @Test
    @DisplayName("最新的一张贴着锚点顶边，旧的被挤下去一个级差")
    void newestCardSitsOnTheAnchor() {
        // 卡高 30：240 高、留白 75 的画布上锚点 (132) 不触发夹取（240-75-30 = 135 > 132）
        var s = stack(anchored(null, null), new StackLayout.Size(100, 30), new StackLayout.Size(100, 30));
        assertEquals(ANCHOR_Y, s.get(0).y(), EPS, "index 0 = 最新 = 贴着锚点");
        assertEquals(s.get(0).y() + s.get(0).height() + 6f, s.get(1).y(), EPS,
                "旧的比最新那张低一个级差");
    }

    @Test
    @DisplayName("入场口不随张数漂：1 张和 5 张，最新那张的顶边是同一个 y")
    void entryEdgeIsStable() {
        var one = stack(anchored(null, null), new StackLayout.Size(100, 22));
        var many = stack(anchored(null, null), new StackLayout.Size(100, 22), new StackLayout.Size(100, 22),
                new StackLayout.Size(100, 22), new StackLayout.Size(100, 22),
                new StackLayout.Size(100, 22));
        assertEquals(one.get(0).y(), many.get(0).y(), EPS);
        // 而且越老的越低（y 越大）
        for (int i = 1; i < many.size(); i++) {
            assertTrue(many.get(i).y() > many.get(i - 1).y(), "第 " + i + " 张没有比前一张更低");
        }
    }

    @Test
    @DisplayName("退场那张在堆底走人，其余卡一动不动（顶锚的抖动老账）")
    void leavingCardLeavesFromTheBottomWithoutDisturbingTheRest() {
        // 六张同型卡：新卡插在锚点上时，旧的全体下移一格（由 CardMove 平滑，不是瞬移）
        var five = stack(anchored(null, null), new StackLayout.Size(100, 22), new StackLayout.Size(100, 22),
                new StackLayout.Size(100, 22), new StackLayout.Size(100, 22),
                new StackLayout.Size(100, 22));
        var six = stack(anchored(null, null), new StackLayout.Size(100, 22), new StackLayout.Size(100, 22),
                new StackLayout.Size(100, 22), new StackLayout.Size(100, 22),
                new StackLayout.Size(100, 22), new StackLayout.Size(100, 22));
        for (int i = 0; i < 5; i++) {
            assertEquals(five.get(i).y() + 22f + 6f, six.get(i + 1).y(), EPS,
                    "第 " + i + " 张应该整体被挤下去一个级差");
        }
        // 到点退场的是最老那张（顶锚下在堆底，six 的第 5 张）。它走掉之后，剩下的五张
        // 与"从来只有五张"的排布完全一致 —— 也就是没有任何人需要补位。
        var backToFive = stack(anchored(null, null), new StackLayout.Size(100, 22),
                new StackLayout.Size(100, 22), new StackLayout.Size(100, 22),
                new StackLayout.Size(100, 22), new StackLayout.Size(100, 22));
        for (int i = 0; i < 5; i++) {
            assertEquals(backToFive.get(i).y(), six.get(i).y(), EPS,
                    "堆底那张走掉时第 " + i + " 张不该动 —— 这是顶锚不抖的根据");
        }
    }

    @Test
    @DisplayName("水平：锚点是竖条左缘，所有卡左缘对齐；宽卡放不下时往左让")
    void anchorPinsTheBarLeftEdge() {
        // 锚点是**画布分数**：100/427 ≈ 0.234 → 在 427 宽画布上就是 x=100
        var s = stack(anchored(100f / GUI_W, null), new StackLayout.Size(120, 30),
                new StackLayout.Size(150, 30));
        assertEquals(100f, s.get(0).x(), EPS);
        assertEquals(100f, s.get(1).x(), EPS, "左缘对齐 —— 竖条成一条竖线");

        // 锚点想停在 320（320/427），但 427 宽 − 16 边距只装得下 91 宽的卡；给一张 200 宽的 → 自动左让
        var wide = stack(anchored(320f / GUI_W, null), new StackLayout.Size(200, 30));
        float maxLeft = GUI_W - MARGIN - 200;
        assertEquals(maxLeft, wide.get(0).x(), EPS, "放不下时往左让，绝不把内容挤出屏幕");
        assertTrue(wide.get(0).x() >= 0f);
    }

    @Test
    @DisplayName("默认锚点：横向 = 自动锚点公式，纵向 = 准星下方")
    void defaultAnchorsBelowTheCrosshair() {
        var s = StackLayout.stack(List.of(new StackLayout.Size(120, 30)), GUI_W, GUI_H,
                LayoutSettings.defaults(), MARGIN, 75, 6f);
        assertEquals(LayoutSettings.autoLeftEdge(GUI_W), s.get(0).x(), EPS, "竖条左缘停在自动锚点");
        assertEquals(GUI_H * LayoutSettings.DEFAULT_ANCHOR_Y, s.get(0).y(), EPS, "顶边落在准星下方");
    }

    @Test
    @DisplayName("分数锚点跨缩放档稳定：同一分数在大小两块画布上落在同一个相对位置")
    void fractionalAnchorIsScaleInvariant() {
        // fx=0.5 / fy=0.3 在三档画布上都不触发夹取（x 方向 100 宽的卡放得下，y 方向 20 高也在 HUD 带上）
        LayoutSettings dragged = anchored(0.5f, 0.3f);
        for (float[] canvas : new float[][] {{426f, 240f}, {256f, 144f}, {640f, 360f}}) {
            var s = StackLayout.stack(List.of(new StackLayout.Size(100, 20)), canvas[0], canvas[1],
                    dragged, MARGIN, 75, 4f);
            assertEquals(canvas[0] * 0.5f, s.get(0).x(), EPS,
                    canvas[0] + " 宽：x 应该在 50% 处");
            assertEquals(canvas[1] * 0.3f, s.get(0).y(), EPS,
                    canvas[1] + " 高：y 应该在 30% 处");
        }
    }

    @Test
    @DisplayName("锚点被拖得太低：第一张卡自动抬到 HUD 带上方（至少放得下一张）")
    void anchorDraggedTooLowGetsClampedAboveTheHudBand() {
        int marginY = 75;
        LayoutSettings low = anchored(0.7f, 0.98f);
        float cardH = 20f;
        float clampedTop = low.anchorTop(GUI_H, cardH, marginY);
        assertEquals(GUI_H - marginY - cardH, clampedTop, EPS, "锚点夹到 HUD 带上方正好一张卡");

        var slots = StackLayout.stack(List.of(new StackLayout.Size(100, cardH)), GUI_W, GUI_H,
                low, MARGIN, marginY, 4f);
        assertEquals(clampedTop, slots.get(0).y(), EPS, "排布与夹取必须用同一个锚点");
        // 而取舍也用同一个值：正好放得下 1 张
        assertEquals(1, StackLayout.fittingCount(clampedTop, GUI_H, marginY, cardH, 4f));
    }

    @Test
    @DisplayName("返回顺序与传入顺序一致，index 指得回去")
    void orderMatchesInputOrder() {
        var sizes = new StackLayout.Size[]{
                new StackLayout.Size(50, 20), new StackLayout.Size(60, 20), new StackLayout.Size(70, 20)};
        var s = stack(anchored(null, null), sizes);
        for (int i = 0; i < s.size(); i++) {
            assertEquals(i, s.get(i).index());
            assertEquals(sizes[i].width(), s.get(i).width(), EPS);
        }
    }

    @Test
    @DisplayName("整体高度把间隙算进去")
    void totalHeightIncludesGaps() {
        var sizes = List.of(new StackLayout.Size(10, 20), new StackLayout.Size(10, 30));
        assertEquals(20 + 30 + 5f, StackLayout.totalHeight(sizes, 5f), EPS);
    }

    @Test
    @DisplayName("锚点以下放不下就丢：号称放得下的全在 HUD 带上方，多一张必然越界")
    void onlyWhatFitsStaysAboveTheHudBand() {
        for (float h : new float[]{144f, 180f, 240f, 360f, 480f, 1080f}) {
            float top = LayoutSettings.defaults().anchorTop(h, 20f, 75);
            int fits = StackLayout.fittingCount(top, h, 75, 20f, 4f);
            assertTrue(fits >= 1, "画布 " + h + " 高时一张都放不下，取舍就无从谈起");
            var slots = cards(h, fits + 1);
            for (int i = 0; i < fits; i++) {
                assertTrue(slots.get(i).y() + 20f <= h - 75f,
                        "画布 " + h + " 高：第 " + i + " 张号称放得下，却压过 HUD 带（底 y="
                                + (slots.get(i).y() + 20f) + "）");
            }
            assertTrue(slots.get(fits).y() + 20f > h - 75f,
                    "画布 " + h + " 高：第 " + fits + " 张号称放不下，却在 HUD 带上方（y=" + slots.get(fits).y() + "）");
        }
    }

    /**
     * 反例对照：自动锚点在 55% 高时，可放张数比贴底时代少 —— 这是把卡挪到准星旁边的
     * 直接代价（可放高度从"全屏减 HUD 带"变成"锚点以下那一段"）。差额由排队 + 溢出卡接手。
     * <p>谁要是把锚点改回底部却忘改这里，这条会红。
     */
    @Test
    void anchoredColumnFitsFewerCardsThanABottomAnchoredOne() {
        // 240 高（guiScale 3 的 1280×720）：锚点 132、HUD 带顶 165 → 只放得下 1 张
        assertEquals(1, fitsAt(240f));
        // 360 高（1920×1080 的 guiScale 3）：锚点 198、HUD 带顶 285 → 3 张
        assertEquals(3, fitsAt(360f));
        // 贴底在同一块 240 高画布上是 7 张 —— 少放是多出来的，锚点高了就该少
        assertTrue(fitsAt(240f) < (int) ((240f - 75f + 4f) / 24f));
    }

    private static int fitsAt(float guiHeight) {
        float top = LayoutSettings.defaults().anchorTop(guiHeight, 20f, 75);
        return StackLayout.fittingCount(top, guiHeight, 75, 20f, 4f);
    }

    /** n 张 100×20 的卡，用真实的底部留白（75）在给定画布高上排一遍。 */
    private static List<StackLayout.Slot> cards(float guiHeight, int n) {
        var sizes = new StackLayout.Size[n];
        for (int i = 0; i < n; i++) {
            sizes[i] = new StackLayout.Size(100, 20);
        }
        return StackLayout.stack(List.of(sizes), GUI_W, guiHeight, LayoutSettings.defaults(),
                MARGIN, 75, 4f);
    }
}
