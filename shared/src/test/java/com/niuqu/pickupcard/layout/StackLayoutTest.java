package com.niuqu.pickupcard.layout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StackLayoutTest {

    private static final float EPS = 0.001f;

    @Test
    @DisplayName("没有卡时不算出任何位置")
    void emptyListProducesNothing() {
        assertTrue(StackLayout.bottomRight(List.of(), 320, 240, 16, 16, 8f).isEmpty());
    }

    @Test
    @DisplayName("最新的一张贴着底边，旧的在它上方隔着 gap")
    void newestCardSitsOnBottomEdge() {
        var sizes = List.of(new StackLayout.Size(100, 40), new StackLayout.Size(100, 40));
        var slots = StackLayout.bottomRight(sizes, 320, 240, 16, 16, 8f);

        assertEquals(240 - 16, slots.get(1).y() + slots.get(1).height(), EPS);
        assertEquals(slots.get(1).y() - 8f, slots.get(0).y() + slots.get(0).height(), EPS);
    }

    @Test
    @DisplayName("卡一律右对齐 —— 换行宽不同的名字也不会参差")
    void cardsAreRightAligned() {
        var sizes = List.of(new StackLayout.Size(80, 30), new StackLayout.Size(120, 30));
        var slots = StackLayout.bottomRight(sizes, 400, 300, 12, 12, 4f);

        for (var slot : slots) {
            assertEquals(400 - 12, slot.x() + slot.width(), EPS);
        }
    }

    @Test
    @DisplayName("返回顺序与传入顺序一致，index 指得回去")
    void orderMatchesInputOrder() {
        var sizes = List.of(
                new StackLayout.Size(50, 20),
                new StackLayout.Size(60, 20),
                new StackLayout.Size(70, 20));
        var slots = StackLayout.bottomRight(sizes, 200, 200, 10, 10, 5f);

        for (int i = 0; i < slots.size(); i++) {
            assertEquals(i, slots.get(i).index());
            assertEquals(sizes.get(i).width(), slots.get(i).width(), EPS);
        }
    }

    @Test
    @DisplayName("整体高度把间隙算进去 —— 屏幕塞不下时调用方靠它做取舍")
    void totalHeightIncludesGaps() {
        var sizes = List.of(new StackLayout.Size(10, 20), new StackLayout.Size(10, 30));
        assertEquals(20 + 30 + 5f, StackLayout.totalHeight(sizes, 5f), EPS);
    }
}
