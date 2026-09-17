package com.niuqu.pickupcard.style;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 动画时间轴的钉子：过冲是特性、开关必须生效、退场单调。
 * 这些行为玩家是"看得见"的——弹性没了或退场闪烁，都从这里查起。
 */
class CardTimelineTest {

    private static final CardTimeline TL = new CardTimeline(320L, 300L, true, true);

    @Test
    void enterProgressesLinearlyToCompletion() {
        assertEquals(0f, TL.enter(1_000L, 1_000L), 1e-6);
        assertEquals(0.5f, TL.enter(1_160L, 1_000L), 1e-6);
        assertEquals(1f, TL.enter(1_400L, 1_000L), 1e-6);
        // 超时不越界：easing 在渲染层做，时间轴只给归一化 t
        assertEquals(1f, TL.enter(9_999L, 1_000L), 1e-6);
    }

    @Test
    void bumpStaysOneWhenNeverMerged() {
        assertEquals(1f, TL.bump(5_000L, -1L), 1e-6);
    }

    /**
     * 入场两段必须走草稿的曲线，不能是匀速。
     *
     * <p>【这条是给谁钉的】原来 {@link CardTimeline} 只把 t 归一化就交出去，靠一句注释
     * "easing 在渲染层做"顶着 —— 而渲染层从来没做，真机上的评价是"很僵硬，没有曲线"。
     * 曲线是这两条进度的<b>定义</b>，不是画的时候顺手加的装饰，所以钉在这里。
     */
    @Test
    void entranceSegmentsFollowTheDraftCurves() {
        // 竖条窗口 = 入场的头 30%：enter=0.15 落在窗口中点 → 该走 0.950（匀速只会是 0.5）
        assertEquals(0.949947f, TL.bar(1_048L, 1_000L), 2e-3, "竖条中段");
        assertEquals(1f, TL.bar(1_096L, 1_000L), 1e-6, "竖条 30% 处必须已经长满");
        assertEquals(0f, TL.bar(1_000L, 1_000L), 1e-6);

        // 内容窗口 = 18% → 100%：enter=0.59 落在窗口中点 → 该走 0.776（Material 曲线；
        // 草稿原来那条是 0.952 —— 换曲线时这条断言必须跟着改，否则它会替旧曲线把关）
        assertTrue(TL.content(1_057L, 1_000L) < 0.02f, "18% 之前内容不该动");
        assertEquals(0.775561f, TL.content(1_189L, 1_000L), 2e-3, "内容中段");
        assertEquals(1f, TL.content(1_320L, 1_000L), 1e-6);
    }

    @Test
    void bumpCompletesAndReturnsToOne() {
        assertEquals(0f, TL.bump(2_000L, 2_000L), 1e-6);
        assertEquals(1f, TL.bump(2_400L, 2_000L), 1e-6);
    }

    @Test
    void disabledAnimationsAreInstant() {
        CardTimeline off = new CardTimeline(320L, 300L, false, false);
        assertEquals(1f, off.enter(1_000L, 1_000L), 1e-6);
        assertEquals(1f, off.bump(1_000L, 1_000L), 1e-6);
    }

    @Test
    void exitIsMonotonicAndBounded() {
        assertEquals(0f, CardTimeline.exit(1_000L, 1_000L, 320L), 1e-6);
        assertEquals(0.5f, CardTimeline.exit(1_160L, 1_000L, 320L), 1e-3);
        assertEquals(1f, CardTimeline.exit(2_000L, 1_000L, 320L), 1e-6);
    }

    @Test
    void exitWithZeroDurationIsImmediatelyDone() {
        assertEquals(1f, CardTimeline.exit(1_000L, 1_000L, 0L), 1e-6);
    }

    @Test
    void easeOutBackOvershootsThenSettles() {
        // 过冲是"弹性"档的灵魂：中途必须超过 1，终点必须恰好落回 1
        assertTrue(Easing.easeOutBack(0.6f) > 1f, "easeOutBack 应该过冲");
        assertEquals(1f, Easing.easeOutBack(1f), 1e-6);
        assertEquals(0f, Easing.easeOutBack(0f), 1e-6);
    }

    @Test
    void pulsePeaksAtMiddleAndReturns() {
        assertEquals(1f, Easing.pulse(0f, 1.35f), 1e-6);
        assertEquals(1.35f, Easing.pulse(0.5f, 1.35f), 1e-4);
        assertEquals(1f, Easing.pulse(1f, 1.35f), 1e-6);
    }
}
