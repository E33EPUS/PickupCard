package com.niuqu.pickupcard.notice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MergeWindowTest {

    @Test
    @DisplayName("窗口 <= 0 表示彻底关掉合并")
    void zeroWindowDisablesMerge() {
        assertFalse(MergeWindow.shouldMerge(true, true, 0L, 0L));
        assertFalse(MergeWindow.shouldMerge(true, true, 0L, -5L));
    }

    @Test
    @DisplayName("item 或外观任一对不上就不并")
    void bothKeysMustMatch() {
        assertFalse(MergeWindow.shouldMerge(false, true, 0L, 1_200L));
        assertFalse(MergeWindow.shouldMerge(true, false, 0L, 1_200L));
        assertTrue(MergeWindow.shouldMerge(true, true, 0L, 1_200L));
    }

    @Test
    @DisplayName("边界是闭区间：正好等于窗口仍算并")
    void boundaryIsInclusive() {
        assertTrue(MergeWindow.shouldMerge(true, true, 1_200L, 1_200L));
        assertFalse(MergeWindow.shouldMerge(true, true, 1_201L, 1_200L));
    }

    @Test
    @DisplayName("负的间隔（时钟回拨/乱序）当作刚发生，并起来")
    void negativeIntervalCountsAsNow() {
        assertTrue(MergeWindow.shouldMerge(true, true, -50L, 1_200L));
    }
}
