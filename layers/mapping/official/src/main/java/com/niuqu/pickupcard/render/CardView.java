package com.niuqu.pickupcard.render;

import com.niuqu.pickupcard.notice.Notice;
import com.niuqu.pickupcard.pickup.Inbox;

/**
 * 账本里的一张卡在渲染侧的影子：账本的 {@link Notice} 是权威数据，这里只多记
 * <b>渲染才关心</b>的两个时刻。
 * <p>
 * 【为什么要分开记两处时刻】{@code bornAt}（入场起点）与 {@code touchedAt}（合并窗口）
 * 是账本的语义；{@code lastBumpAt}（数字跳动的起点）与 {@code exitStartAt}（退场起点）
 * 纯粹是"屏幕上的这一次表演什么时候开始的"。合并会刷新跳动、也会把正在退场的卡拽回来，
 * 但这些都不该反过来改账本。
 */
public final class CardView {

    /** 退场还没开始。用负数而不是 0 是因为 0 是合法的纪元时刻（测试里会用到）。 */
    public static final long NO_EXIT = -1L;

    private Notice<Inbox.Card> notice;
    private long lastBumpAt = -1L;
    private long exitStartAt = NO_EXIT;

    public CardView(Notice<Inbox.Card> notice) {
        this.notice = notice;
    }

    public Notice<Inbox.Card> notice() {
        return notice;
    }

    public String key() {
        return notice.key();
    }

    public long lastBumpAt() {
        return lastBumpAt;
    }

    public long exitStartAt() {
        return exitStartAt;
    }

    public boolean exiting() {
        return exitStartAt != NO_EXIT;
    }

    /** 合并：换掉账本快照，顺便让数字跳一下，并把正在退场的卡拽回来。 */
    public void absorbMerge(Notice<Inbox.Card> merged, long now) {
        this.notice = merged;
        this.lastBumpAt = now;
        this.exitStartAt = NO_EXIT;
    }

    /** 开始退场。已经在退场中的不重来（否则每 tick 都会被推后）。 */
    public void beginExit(long now) {
        if (exitStartAt == NO_EXIT) {
            exitStartAt = now;
        }
    }
}
