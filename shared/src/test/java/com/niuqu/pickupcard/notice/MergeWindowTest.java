package com.niuqu.pickupcard.notice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MergeWindowTest {

    @Test
    @DisplayName("NEVER 档一律不并：调用方于是每次单开一张")
    void noneNeverMerges() {
        assertFalse(MergeWindow.shouldMerge(true, true, MergeMode.NEVER));
    }

    @Test
    @DisplayName("item 或外观任一对不上就不并")
    void bothKeysMustMatch() {
        assertFalse(MergeWindow.shouldMerge(false, true, MergeMode.SAME_NBT), "不是同一个物品");
        assertFalse(MergeWindow.shouldMerge(true, false, MergeMode.SAME_NBT), "同一物品但外观档位不同");
        assertTrue(MergeWindow.shouldMerge(true, true, MergeMode.SAME_NBT));
        assertTrue(MergeWindow.shouldMerge(true, true, MergeMode.SAME_ITEM), "放宽的档位不挡外观");
    }
}
