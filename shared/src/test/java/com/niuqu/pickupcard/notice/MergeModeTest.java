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
    @DisplayName("只有 STRICT 档把 NBT 编进身份键")
    void onlyStrictUsesNbt() {
        assertTrue(MergeMode.STRICT.usesNbt());
        assertFalse(MergeMode.TYPE.usesNbt());
        assertFalse(MergeMode.TYPE_NAMED.usesNbt());
        assertFalse(MergeMode.NONE.usesNbt());
    }

    @Test
    @DisplayName("只有 STRICT 档比外观档位 —— 别的档位要比就等于没设")
    void onlyStrictUsesLook() {
        assertTrue(MergeMode.STRICT.usesLook());
        assertFalse(MergeMode.TYPE.usesLook());
        assertFalse(MergeMode.TYPE_NAMED.usesLook());
        assertFalse(MergeMode.NONE.usesLook());
    }

    @Test
    @DisplayName("NONE 每次都新身份；TYPE_NAMED 只对改过名字的那么做")
    void uniquePerPickup() {
        for (boolean named : new boolean[]{true, false}) {
            assertTrue(MergeMode.NONE.uniquePerPickup(named), "从不合并：每次都要新身份");
            assertEquals(named, MergeMode.TYPE_NAMED.uniquePerPickup(named),
                    "TYPE_NAMED：只有改过名字的那件要单独一张卡");
            assertFalse(MergeMode.TYPE.uniquePerPickup(named));
            assertFalse(MergeMode.STRICT.uniquePerPickup(named));
        }
    }

    @Test
    @DisplayName("默认是最严的那一档")
    void defaultsToStrict() {
        assertEquals(MergeMode.STRICT, MergeMode.defaults());
    }
}
