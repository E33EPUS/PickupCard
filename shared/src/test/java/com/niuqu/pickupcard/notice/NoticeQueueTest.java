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
    @DisplayName("同一物品合并：数量累加，代数 +1")
    void mergesSameItem() {
        NoticeQueue<String> q = queue();
        q.absorb("diamond", "rare", "diamond-stack", 32, true, 1_000L, true, 5);
        var outcome = q.absorb("diamond", "rare", "diamond-stack", 64, false, 1_500L, true, 5);

        assertEquals(NoticeQueue.Change.MERGED, outcome.change());
        assertEquals(96, outcome.notice().count());
        assertEquals(1, outcome.notice().generation());
        assertEquals(1, q.size(), "合并之后屏上仍然只有一张卡");
        // firstTime 属于"第一次见"这件事，合并不该把它翻掉
        assertTrue(outcome.notice().firstTime());
    }

    /**
     * 用户 2026-09-17 报的那个 bug 的对照：隔很久（5 秒）再捡同一个物品，<b>只要那张卡还在
     * 屏上就必须合并</b>。
     * <p>
     * 从前这里有一条「合并窗口」（默认 1200ms）：超了就顶掉旧卡、单开一张。可那时旧卡往往
     * 正在淡出，渲染层只能把淡出那张整张换掉 —— 画面是「淡到一半突然全不透明」，
     * 而且账本多出一张、把另一张卡挤出局。这条断言就是钉死那件事的。
     */
    @Test
    @DisplayName("只要那张卡还在屏上，隔多久都合并（合并窗口已删）")
    void mergesAsLongAsTheCardIsAlive() {
        NoticeQueue<String> q = queue();
        q.absorb("diamond", "rare", "s", 1, true, 0L, true, 5);
        q.absorb("other", "l", "s", 1, true, 100L, true, 5);

        var outcome = q.absorb("diamond", "rare", "s", 1, false, 5_000L, true, 5);

        assertEquals(NoticeQueue.Change.MERGED, outcome.change(), "卡还在就该并，不是顶掉重来");
        assertEquals(2, outcome.notice().count());
        assertTrue(outcome.evicted().isEmpty(), "不许因为这次拾取把别的卡挤掉");
        assertEquals(2, q.size(), "屏上仍然是两张：diamond 与 other");
    }

    @Test
    @DisplayName("合并被关掉时，每次都单开一张；旧卡走淘汰通道（否则界面上会留两张同名卡）")
    void mergeDisabled() {
        NoticeQueue<String> q = queue();
        q.absorb("diamond", "rare", "s", 1, true, 0L, false, 5);
        var outcome = q.absorb("diamond", "rare", "s", 1, false, 10L, false, 5);
        assertEquals(NoticeQueue.Change.ADDED, outcome.change());
        assertEquals(1, outcome.evicted().size());
        assertEquals("diamond", outcome.evicted().get(0).key());
        assertEquals(0, outcome.evicted().get(0).generation(), "被顶掉的应该是那张旧卡");
    }

    @Test
    @DisplayName("外观不同不许合并：同名物品换档会串档位")
    void differentLookDoesNotMerge() {
        NoticeQueue<String> q = queue();
        q.absorb("sword", "common", "s", 1, true, 0L, true, 5);
        var outcome = q.absorb("sword", "epic", "s", 1, false, 100L, true, 5);
        assertEquals(NoticeQueue.Change.ADDED, outcome.change());
    }

    @Test
    @DisplayName("不同物品互不干扰，各自一张卡")
    void differentKeysAreIndependent() {
        NoticeQueue<String> q = queue();
        q.absorb("diamond", "rare", "s", 1, true, 0L, true, 5);
        q.absorb("iron", "common", "s", 1, true, 10L, true, 5);
        assertEquals(2, q.size());
        assertEquals(1, q.find("diamond").orElseThrow().count());
    }

    @Test
    @DisplayName("超过在屏上限时，淘汰最久没被碰过的那张，并且把被淘汰的报回来")
    void evictsTheLeastRecentlyTouched() {
        NoticeQueue<String> q = queue();
        q.absorb("a", "l", "s", 1, true, 0L, true, 2);
        q.absorb("b", "l", "s", 1, true, 10L, true, 2);
        // 碰一下 a：它变最新，于是被挤掉的应该是 b
        q.absorb("a", "l", "s", 1, false, 20L, true, 2);
        var outcome = q.absorb("c", "l", "s", 1, true, 30L, true, 2);

        assertEquals(2, q.size());
        assertTrue(q.find("a").isPresent(), "刚被碰过的 a 应该活着");
        assertTrue(q.find("c").isPresent(), "新来的 c 应该活着");
        assertFalse(q.find("b").isPresent(), "最久没被碰过的 b 被挤掉");

        // 界面那边靠这个列表给被挤掉的卡播退场 —— 空的话它会永远留在屏幕上
        assertEquals(1, outcome.evicted().size(), "必须报出被淘汰的那一张");
        assertEquals("b", outcome.evicted().get(0).key());
        assertTrue(q.isLeaving("b"), "被淘汰的那张还在淡出，账本要记着它");
    }

    /**
     * 用户报的那个 bug 的核心对照：<b>被淘汰的那张还在淡出时又捡到同一个物品</b>。
     * <p>
     * 要求：并回它（不是单开一张）、数量累加、<b>不挤掉别人</b>、而且它的画面会被救回
     * （渲染层收到 MERGED 后改播「淡回」，见 {@code CardView#beginRevive}）。
     */
    @Test
    @DisplayName("淡出中的卡被同一个物品救回：并回去，不重新排队、不挤掉下一张")
    void rescuesACardThatIsStillFading() {
        NoticeQueue<String> q = queue();
        q.absorb("a", "l", "s", 1, true, 0L, true, 2);
        q.absorb("b", "l", "s", 1, true, 10L, true, 2);
        var evicting = q.absorb("c", "l", "s", 1, true, 20L, true, 2);
        assertEquals(1, evicting.evicted().size(), "c 进来时挤掉了最久的 a");
        assertEquals("a", evicting.evicted().get(0).key());
        assertTrue(q.isLeaving("a"));

        var rescued = q.absorb("a", "l", "s", 1, false, 100L, true, 2);

        assertEquals(NoticeQueue.Change.MERGED, rescued.change(), "淡出中的同名卡应该被救回");
        assertEquals(2, rescued.notice().count(), "数量要累加到它身上");
        assertTrue(rescued.evicted().isEmpty(), "救回不该顺手挤掉别的卡");
        assertFalse(q.isLeaving("a"), "救回之后它不再是「离开中」");
        assertTrue(q.find("a").isPresent(), "它回到活表里了");
    }

    @Test
    @DisplayName("退场播完（渲染层通知）之后，同名拾取就是新的一张卡")
    void afterTheFadeFinishesTheNameIsFreeAgain() {
        NoticeQueue<String> q = queue();
        q.absorb("a", "l", "s", 1, true, 0L, true, 2);
        q.absorb("b", "l", "s", 1, true, 10L, true, 2);
        q.absorb("c", "l", "s", 1, true, 20L, true, 2);
        q.forgetLeft("a");

        var outcome = q.absorb("a", "l", "s", 1, false, 1_000L, true, 2);
        assertEquals(NoticeQueue.Change.ADDED, outcome.change());
        assertEquals(1, outcome.notice().count(), "新的一张卡只记这一次");
    }

    @Test
    @DisplayName("停留超时才退场，刚进来的不退")
    void sweepRespectsHold() {
        NoticeQueue<String> q = queue();
        q.absorb("a", "l", "s", 1, true, 0L, true, 5);

        assertTrue(q.sweep(500L, 2_000L).isEmpty(), "还在停留期内");
        assertEquals(1, q.size());

        assertEquals(1, q.sweep(2_000L, 2_000L).size(), "到点退场");
        assertEquals(0, q.size());
    }

    @Test
    @DisplayName("合并会刷新停留计时：连捡不停，卡就不该消失")
    void mergeRefreshesLifetime() {
        NoticeQueue<String> q = queue();
        q.absorb("a", "l", "s", 1, true, 0L, true, 5);
        q.absorb("a", "l", "s", 1, false, 1_500L, true, 5);

        assertTrue(q.sweep(2_500L, 2_000L).isEmpty(),
                "1.5s 时被刷新过，2.5s 时不该退场");
    }

    @Test
    @DisplayName("快照按最久没被碰过排前，DOM 反序就是最新在上")
    void snapshotOrder() {
        NoticeQueue<String> q = queue();
        q.absorb("old", "l", "s", 1, true, 0L, true, 5);
        q.absorb("new", "l", "s", 1, true, 100L, true, 5);

        var snap = q.snapshot();
        assertEquals("old", snap.get(0).key());
        assertEquals("new", snap.get(1).key());
    }
}
