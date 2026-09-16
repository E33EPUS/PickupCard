package com.niuqu.pickupcard.notice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoticeQueueTest {

    /** 载荷用 String 代替 ItemStack：队列规则与物品类型无关，单测因此不必启动游戏。 */
    private NoticeQueue<String> queue() {
        return new NoticeQueue<>();
    }

    @Test
    @DisplayName("窗口内同一物品合并：数量累加，代数 +1")
    void mergesWithinWindow() {
        NoticeQueue<String> q = queue();
        q.absorb("diamond", "rare", "diamond-stack", 32, true, 1_000L, true, 1_200L, 5);
        var outcome = q.absorb("diamond", "rare", "diamond-stack", 64, false, 1_500L, true, 1_200L, 5);

        assertEquals(NoticeQueue.Change.MERGED, outcome.change());
        assertEquals(96, outcome.notice().count());
        assertEquals(1, outcome.notice().generation());
        assertEquals(1, q.size(), "合并之后屏上仍然只有一张卡");
        // firstTime 属于"第一次见"这件事，合并不该把它翻掉
        assertTrue(outcome.notice().firstTime());
    }

    @Test
    @DisplayName("超出窗口就是两张卡 —— 上一版演示脚本正是死在这里")
    void windowBoundary() {
        NoticeQueue<String> q = queue();
        q.absorb("diamond", "rare", "s", 1, true, 0L, true, 1_200L, 5);

        // 正好等于窗口：仍然算并（边界取闭区间，和 <= windowMs 一致）
        assertEquals(NoticeQueue.Change.MERGED,
                q.absorb("diamond", "rare", "s", 1, false, 1_200L, true, 1_200L, 5).change());

        // 越过窗口哪怕 1ms：不并，而是顶掉旧的
        NoticeQueue<String> q2 = queue();
        q2.absorb("diamond", "rare", "s", 1, true, 0L, true, 1_200L, 5);
        var after = q2.absorb("diamond", "rare", "s", 1, false, 1_201L, true, 1_200L, 5);
        assertEquals(NoticeQueue.Change.ADDED, after.change());
        assertEquals(0, after.notice().generation(), "新卡是第 0 代");
        assertEquals(1, after.notice().count(), "新卡只记这一次的数量");
    }

    @Test
    @DisplayName("合并被关掉时，每次都单开一张")
    void mergeDisabled() {
        NoticeQueue<String> q = queue();
        q.absorb("diamond", "rare", "s", 1, true, 0L, false, 1_200L, 5);
        var outcome = q.absorb("diamond", "rare", "s", 1, false, 10L, false, 1_200L, 5);
        assertEquals(NoticeQueue.Change.ADDED, outcome.change());
    }

    @Test
    @DisplayName("外观不同不许合并：同名物品换档会串档位")
    void differentLookDoesNotMerge() {
        NoticeQueue<String> q = queue();
        q.absorb("sword", "common", "s", 1, true, 0L, true, 1_200L, 5);
        var outcome = q.absorb("sword", "epic", "s", 1, false, 100L, true, 1_200L, 5);
        assertEquals(NoticeQueue.Change.ADDED, outcome.change());
    }

    @Test
    @DisplayName("不同物品互不干扰，各自一张卡")
    void differentKeysAreIndependent() {
        NoticeQueue<String> q = queue();
        q.absorb("diamond", "rare", "s", 1, true, 0L, true, 1_200L, 5);
        q.absorb("iron", "common", "s", 1, true, 10L, true, 1_200L, 5);
        assertEquals(2, q.size());
        assertEquals(1, q.find("diamond").orElseThrow().count());
    }

    @Test
    @DisplayName("超过在屏上限时，淘汰最久没被碰过的那张，并且把被淘汰的报回来")
    void evictsTheLeastRecentlyTouched() {
        NoticeQueue<String> q = queue();
        q.absorb("a", "l", "s", 1, true, 0L, true, 1_200L, 2);
        q.absorb("b", "l", "s", 1, true, 10L, true, 1_200L, 2);
        // 碰一下 a：它变最新，于是被挤掉的应该是 b
        q.absorb("a", "l", "s", 1, false, 20L, true, 1_200L, 2);
        var outcome = q.absorb("c", "l", "s", 1, true, 30L, true, 1_200L, 2);

        assertEquals(2, q.size());
        assertTrue(q.find("a").isPresent(), "刚被碰过的 a 应该活着");
        assertTrue(q.find("c").isPresent(), "新来的 c 应该活着");
        assertFalse(q.find("b").isPresent(), "最久没被碰过的 b 被挤掉");

        // 界面那边靠这个列表给被挤掉的卡播退场 —— 空的话它会永远留在屏幕上
        assertEquals(1, outcome.evicted().size(), "必须报出被淘汰的那一张");
        assertEquals("b", outcome.evicted().get(0).key());
    }

    @Test
    @DisplayName("同类不能并时，旧卡走淘汰通道，新卡走新增通道")
    void replacedCardIsReportedAsEvicted() {
        NoticeQueue<String> q = queue();
        q.absorb("a", "l", "s", 1, true, 0L, true, 1_200L, 5);
        // 越过窗口 → 顶掉旧的开新的；旧卡必须从"淘汰"出去，否则 DOM 里会留两张 a
        var outcome = q.absorb("a", "l", "s", 1, false, 5_000L, true, 1_200L, 5);

        assertEquals(NoticeQueue.Change.ADDED, outcome.change());
        assertEquals(1, outcome.evicted().size());
        assertEquals("a", outcome.evicted().get(0).key());
        assertEquals(0, outcome.evicted().get(0).generation(), "被顶掉的应该是那张旧卡");
    }

    @Test
    @DisplayName("停留超时才退场，刚进来的不退")
    void sweepRespectsHold() {
        NoticeQueue<String> q = queue();
        q.absorb("a", "l", "s", 1, true, 0L, true, 1_200L, 5);

        assertTrue(q.sweep(500L, 2_000L).isEmpty(), "还在停留期内");
        assertEquals(1, q.size());

        assertEquals(1, q.sweep(2_000L, 2_000L).size(), "到点退场");
        assertEquals(0, q.size());
    }

    @Test
    @DisplayName("合并会刷新停留计时：连捡不停，卡就不该消失")
    void mergeRefreshesLifetime() {
        NoticeQueue<String> q = queue();
        q.absorb("a", "l", "s", 1, true, 0L, true, 1_200L, 5);
        q.absorb("a", "l", "s", 1, false, 1_500L, true, 1_200L, 5);

        assertTrue(q.sweep(2_500L, 2_000L).isEmpty(),
                "1.5s 时被刷新过，2.5s 时不该退场");
    }

    @Test
    @DisplayName("快照按最久没被碰过排前，DOM 反序就是最新在上")
    void snapshotOrder() {
        NoticeQueue<String> q = queue();
        q.absorb("old", "l", "s", 1, true, 0L, true, 1_200L, 5);
        q.absorb("new", "l", "s", 1, true, 100L, true, 1_200L, 5);

        var snap = q.snapshot();
        assertEquals("old", snap.get(0).key());
        assertEquals("new", snap.get(1).key());
    }
}
