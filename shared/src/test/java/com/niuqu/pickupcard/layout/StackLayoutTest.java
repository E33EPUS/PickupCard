package com.niuqu.pickupcard.layout;

import com.niuqu.pickupcard.layout.LayoutSettings.Appear;
import com.niuqu.pickupcard.layout.LayoutSettings.Side;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 排布这件事的"已知答案"。
 * <p>
 * 【为什么要钉锚点】handoff 里记着那次翻车：游戏是"底锚 + 老的在上面"、草稿是"顶锚 + 新的在
 * 上面"，两边方向相反，而像素门禁只逐张比卡的结构（竖条宽、间距…），**不比对锚点**，
 * 于是一路绿灯。所以这里专钉两件事：<b>锚在哪</b>、<b>谁在哪一位</b>。
 */
class StackLayoutTest {

    private static final float EPS = 0.001f;
    private static final int MARGIN = 16;
    /** 逻辑画布：1280x720 @ guiScale 3 → 426x240。取 240 高，"底线"就是 224。 */
    private static final float GUI_W = 427f;
    private static final float GUI_H = 240f;
    private static final float BOTTOM = GUI_H - MARGIN;

    private static LayoutSettings right() {
        return new LayoutSettings(Side.RIGHT, 320, Appear.SLIDE, LayoutSettings.DEFAULT_SEPARATION);
    }

    private static LayoutSettings left(int edge) {
        return new LayoutSettings(Side.LEFT, edge, Appear.SLIDE, LayoutSettings.DEFAULT_SEPARATION);
    }

    private static List<StackLayout.Slot> stack(LayoutSettings layout, StackLayout.Size... sizes) {
        return StackLayout.stack(List.of(sizes), GUI_W, GUI_H, layout, MARGIN, MARGIN, 6f);
    }

    @Test
    @DisplayName("没有卡时不算出任何位置")
    void emptyListProducesNothing() {
        assertTrue(stack(right()).isEmpty());
    }

    @Test
    @DisplayName("最新的一张贴着下边，旧的被往上顶一个级差")
    void newestCardSitsOnBottomEdge() {
        var s = stack(right(), new StackLayout.Size(100, 40), new StackLayout.Size(100, 40));
        assertEquals(BOTTOM, s.get(0).y() + s.get(0).height(), EPS,
                "index 0 = 最新 = 贴着底线");
        assertEquals(s.get(0).y() - 6f - s.get(1).height(), s.get(1).y(), EPS,
                "旧的比最新那张再高一个级差");
    }

    @Test
    @DisplayName("入场口不随张数漂：1 张和 5 张，最新那张的底边是同一个 y")
    void entryEdgeIsStable() {
        var one = stack(right(), new StackLayout.Size(100, 22));
        var many = stack(right(), new StackLayout.Size(100, 22), new StackLayout.Size(100, 22),
                new StackLayout.Size(100, 22), new StackLayout.Size(100, 22),
                new StackLayout.Size(100, 22));
        assertEquals(one.get(0).y(), many.get(0).y(), EPS);
        // 而且越老的越高（y 越小）
        for (int i = 1; i < many.size(); i++) {
            assertTrue(many.get(i).y() < many.get(i - 1).y(), "第 " + i + " 张没有比前一张更高");
        }
    }

    @Test
    @DisplayName("退场那张占的是堆顶上面一行，底下几张的位置一点不动")
    void leavingCardTakesTheRowAboveAndLeavesTheRestAlone() {
        var five = stack(right(), new StackLayout.Size(100, 22), new StackLayout.Size(100, 22),
                new StackLayout.Size(100, 22), new StackLayout.Size(100, 22),
                new StackLayout.Size(100, 22));
        // 第六张进来（退役那张还在 live 里，所以这次算 6 张）
        var six = stack(right(), new StackLayout.Size(100, 22), new StackLayout.Size(100, 22),
                new StackLayout.Size(100, 22), new StackLayout.Size(100, 22),
                new StackLayout.Size(100, 22), new StackLayout.Size(100, 22));
        for (int i = 0; i < 4; i++) {
            assertEquals(five.get(i + 1).y(), six.get(i + 1).y(), EPS,
                    "第 " + (i + 1) + " 张被顶了一下 —— 顶锚那版就是在这里抖的");
        }
        assertEquals(five.get(4).y() - 22f - 6f, six.get(5).y(), EPS, "退役那张没升到堆顶上面一行");
    }

    @Test
    @DisplayName("右边固定：所有卡右缘对齐，内容再宽也只是往左伸")
    void rightModeAlignsRightEdges() {
        var s = stack(right(), new StackLayout.Size(80, 30), new StackLayout.Size(200, 30));
        for (var slot : s) {
            assertEquals(GUI_W - MARGIN, slot.x() + slot.width(), EPS);
        }
        assertTrue(s.get(1).x() >= 0f, "不应该算出负坐标");
    }

    @Test
    @DisplayName("左边固定且放得下时：左缘停在设定位置")
    void leftModePinsLeftEdgeWhenItFits() {
        var s = stack(left(100), new StackLayout.Size(120, 30), new StackLayout.Size(150, 30));
        assertEquals(100f, s.get(0).x(), EPS);
        assertEquals(100f, s.get(1).x(), EPS);
    }

    @Test
    @DisplayName("左边固定但放不下时：自动往左让，绝不把内容挤出屏幕")
    void leftModeShiftsLeftWhenTooWide() {
        // 左边想停在 320，但屏宽 427、边距 16 → 最多只能容纳 91 宽；给一张 200 宽的卡
        var s = stack(left(320), new StackLayout.Size(200, 30));
        float maxLeft = GUI_W - MARGIN - 200;
        assertEquals(maxLeft, s.get(0).x(), EPS);
        assertEquals(GUI_W - MARGIN, s.get(0).x() + s.get(0).width(), EPS);
        assertTrue(s.get(0).x() >= 0f);
    }

    @Test
    @DisplayName("右边固定就是左边固定的特例：两者在放得下时给出同一个结果")
    void rightModeIsSpecialCaseOfLeft() {
        var r = stack(right(), new StackLayout.Size(120, 30));
        var l = stack(left(100_000), new StackLayout.Size(120, 30));
        assertEquals(r.get(0).x(), l.get(0).x(), EPS);
    }

    @Test
    @DisplayName("默认：竖条左缘停在自动锚点，内容往右伸")
    void defaultAnchorsTheBarLeftEdge() {
        var s = StackLayout.stack(List.of(new StackLayout.Size(120, 30)), GUI_W, GUI_H,
                LayoutSettings.defaults(), MARGIN, MARGIN, 6f);
        assertEquals(LayoutSettings.autoLeftEdge(GUI_W), s.get(0).x(), EPS, "竖条左缘停在自动锚点");
        assertEquals(BOTTOM, s.get(0).y() + s.get(0).height(), EPS);
    }

    @Test
    @DisplayName("自动锚点跟着画布算：两种 GUI 缩放下，最宽的卡右缘都正好落在右边距上")
    void autoAnchorIsScaleInvariant() {
        // 这条是给"写死一个绝对 x"那个坑立的：guiScale 3 的画布 426 宽、guiScale 5 只剩 256 宽，
        // 同一个绝对锚点在后一档会被右边界夹住 —— 那时竖条又参差了，而配置里看着挺正常。
        for (float w : new float[]{426f, 256f}) {
            float anchor = LayoutSettings.autoLeftEdge(w);
            assertEquals(w - MARGIN, anchor + w * LayoutSettings.CONTENT_WIDTH_RATIO, 0.01f,
                    "画布 " + w + " 宽时，最宽的卡右缘应该正好落在右边距上");
            assertTrue(anchor > 0f, "锚点被算到屏幕外了");
        }
    }

    @Test
    @DisplayName("返回顺序与传入顺序一致，index 指得回去")
    void orderMatchesInputOrder() {
        var sizes = new StackLayout.Size[]{
                new StackLayout.Size(50, 20), new StackLayout.Size(60, 20), new StackLayout.Size(70, 20)};
        var s = stack(right(), sizes);
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
    @DisplayName("画布矮时：号称放得下的那几张全在屏幕里，多出来的那一张必然在屏幕外")
    void onlyWhatFitsStaysOnScreen() {
        for (float h : new float[]{144f, 180f, 240f, 360f, 480f, 1080f}) {
            int fits = StackLayout.fittingCount(h, 75, 20f, 4f);
            assertTrue(fits >= 1, "画布 " + h + " 高时一张都放不下，取舍就无从谈起");
            // 正好多要一张：用来验证"第 fits 张就在屏幕外"
            var slots = cards(h, fits + 1);
            for (int i = 0; i < fits; i++) {
                assertTrue(slots.get(i).y() >= 0f,
                        "画布 " + h + " 高：第 " + i + " 张号称放得下，却在屏幕外（y=" + slots.get(i).y() + "）");
            }
            assertTrue(slots.get(fits).y() < 0f,
                    "画布 " + h + " 高：第 " + fits + " 张号称放不下，却在屏幕里（y=" + slots.get(fits).y() + "）");
        }
    }

    /**
     * 反例对照：<b>没有</b>夹紧时，5 张卡在 180 高的画布上会顶到屏幕外。
     * <p>
     * 这就是用户报的「高缩放下会超出屏幕」：guiScale 4 的 320×180 上，让开 75 的 HUD 带
     * 只剩 105px，第 5 张的 y 是 −11 —— 而 {@code stack()} 从不检查这件事。
     * 谁把取舍删掉，这条会立刻红。
     */
    @Test
    @DisplayName("没有夹紧时 5 张卡在 180 高的画布上必然顶出屏幕（那个 bug 的对照）")
    void withoutTheClampFiveCardsSpillOffScreen() {
        var slots = cards(180f, 5);
        assertTrue(slots.get(4).y() < 0f, "第 5 张（index 4）本该在屏幕外，y=" + slots.get(4).y());
        assertEquals(4, StackLayout.fittingCount(180f, 75, 20f, 4f), "180 高只放得下 4 张");
        assertEquals(3, StackLayout.fittingCount(144f, 75, 20f, 4f), "144 高只放得下 3 张");
        assertEquals(7, StackLayout.fittingCount(240f, 75, 20f, 4f), "240 高（guiScale 3）放得下 7 张");
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
