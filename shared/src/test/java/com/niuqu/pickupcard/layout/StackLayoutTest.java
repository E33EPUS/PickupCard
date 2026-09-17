package com.niuqu.pickupcard.layout;

import com.niuqu.pickupcard.layout.LayoutSettings.Appear;
import com.niuqu.pickupcard.layout.LayoutSettings.Side;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StackLayoutTest {

    private static final float EPS = 0.001f;
    private static final int MARGIN = 16;

    private static LayoutSettings right() {
        return new LayoutSettings(Side.RIGHT, 320, Appear.SLIDE);
    }

    private static LayoutSettings left(int edge) {
        return new LayoutSettings(Side.LEFT, edge, Appear.SLIDE);
    }

    private static List<StackLayout.Slot> stack(LayoutSettings layout, StackLayout.Size... sizes) {
        return StackLayout.stack(List.of(sizes), 427, 240, layout, MARGIN, MARGIN, 6f);
    }

    @Test
    @DisplayName("没有卡时不算出任何位置")
    void emptyListProducesNothing() {
        assertTrue(stack(right()).isEmpty());
    }

    @Test
    @DisplayName("最新的一张贴着底边，旧的在它上方隔着间隙")
    void newestCardSitsOnBottomEdge() {
        var s = stack(right(), new StackLayout.Size(100, 40), new StackLayout.Size(100, 40));
        assertEquals(240 - MARGIN, s.get(1).y() + s.get(1).height(), EPS);
        assertEquals(s.get(1).y() - 6f, s.get(0).y() + s.get(0).height(), EPS);
    }

    @Test
    @DisplayName("右边固定：所有卡右缘对齐，内容再宽也只是往左伸")
    void rightModeAlignsRightEdges() {
        var s = stack(right(), new StackLayout.Size(80, 30), new StackLayout.Size(200, 30));
        for (var slot : s) {
            assertEquals(427 - MARGIN, slot.x() + slot.width(), EPS);
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
        float maxLeft = 427 - MARGIN - 200;
        assertEquals(maxLeft, s.get(0).x(), EPS);
        assertEquals(427 - MARGIN, s.get(0).x() + s.get(0).width(), EPS);
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
}
