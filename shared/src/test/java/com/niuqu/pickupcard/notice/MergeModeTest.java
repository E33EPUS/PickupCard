package com.niuqu.pickupcard.notice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 四档合并粒度的判据。
 * <p>
 * 【为什么要钉这三条】它们决定 {@code ItemIdentity} 产出的身份键长什么样，而键的粒度错了
 * 症状很隐蔽：太粗 → "我捡了一组钻石怎么弹了两张卡"；太细 → 同一堆东西各弹一张、刷屏。
 * 判据本身是纯的，所以在这里钉死，不用起游戏。
 */
class MergeModeTest {

    @Test
    @DisplayName("只有 SAME_NBT 档把 NBT 编进身份键")
    void onlyStrictUsesNbt() {
        assertTrue(MergeMode.SAME_NBT.usesNbt());
        assertFalse(MergeMode.SAME_ITEM.usesNbt());
        assertFalse(MergeMode.SAME_ITEM_KEEP_NAMED.usesNbt());
        assertFalse(MergeMode.NEVER.usesNbt());
    }

    @Test
    @DisplayName("只有 SAME_NBT 档比外观档位 —— 别的档位要比就等于没设")
    void onlyStrictUsesLook() {
        assertTrue(MergeMode.SAME_NBT.usesLook());
        assertFalse(MergeMode.SAME_ITEM.usesLook());
        assertFalse(MergeMode.SAME_ITEM_KEEP_NAMED.usesLook());
        assertFalse(MergeMode.NEVER.usesLook());
    }

    @Test
    @DisplayName("NEVER 每次都新身份；SAME_ITEM_KEEP_NAMED 只对改过名字的那么做")
    void uniquePerPickup() {
        for (boolean named : new boolean[]{true, false}) {
            assertTrue(MergeMode.NEVER.uniquePerPickup(named), "从不合并：每次都要新身份");
            assertEquals(named, MergeMode.SAME_ITEM_KEEP_NAMED.uniquePerPickup(named),
                    "SAME_ITEM_KEEP_NAMED：只有改过名字的那件要单独一张卡");
            assertFalse(MergeMode.SAME_ITEM.uniquePerPickup(named));
            assertFalse(MergeMode.SAME_NBT.uniquePerPickup(named));
        }
    }

    @Test
    @DisplayName("默认是最严的那一档")
    void defaultsToStrict() {
        assertEquals(MergeMode.SAME_NBT, MergeMode.defaults());
    }
}
