package com.niuqu.pickupcard.notice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MergeWindowTest {

    @Test
    @DisplayName("合并关掉时一律不并：调用方于是每次单开一张")
    void disabledNeverMerges() {
        assertFalse(MergeWindow.shouldMerge(true, true, false));
    }

    @Test
    @DisplayName("item 或外观任一对不上就不并")
    void bothKeysMustMatch() {
        assertFalse(MergeWindow.shouldMerge(false, true, true), "不是同一个物品");
        assertFalse(MergeWindow.shouldMerge(true, false, true), "同一物品但外观档位不同");
        assertTrue(MergeWindow.shouldMerge(true, true, true));
    }
}
