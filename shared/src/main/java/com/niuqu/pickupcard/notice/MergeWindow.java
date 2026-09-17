package com.niuqu.pickupcard.notice;

/**
 * 合并判定：这条新拾取应该并进已有那张卡，还是单开一张？
 * <p>
 * 纯函数，不碰 ItemStack、不碰时钟 —— 这样合并规则能在单测里钉死。
 * <p>
 * 【为什么没有时间参数了】从前并入的条件里有一条「合并窗口」（默认 1200ms）：超过就单开
 * 一张新卡。可那个判据是错的 —— 超窗口的时候<b>旧卡往往还在屏幕上</b>（它正淡出），于是
 * 屏幕要同时表示同一物品两张卡：渲染层按物品为键，只能把淡出那张整张换掉，画面就是
 * 「淡到一半突然全不透明，顺手把别的卡挤出局」（用户 2026-09-17 报的那个 bug）。
 * 正确的判据是<b>那张卡还在不在</b>，而"在不在"由调用方（{@link NoticeQueue}）查活表得出，
 * 于是这里只剩两个"对不对得上"加一个总开关。
 */
public final class MergeWindow {

    private MergeWindow() {
    }

    /**
     * 并入的条件是三条同时成立：合并开着、同一个物品、同一档外观。
     * <p>
     * 【为什么「同一档外观」也要看】同名物品在不同 NBT 下可能分属不同稀有度，
     * 那种情况并起来会让卡面档位与内容对不上。
     *
     * @param sameKey      是不是同一个物品（含 NBT 的规范化键）
     * @param sameLook     是不是同一套外观（稀有度档位、是否附魔等）
     * @param mergeMode 合并粒度档位；{@link MergeMode#NEVER} 表示从不合并
     * @return true = 并进旧卡并累加数量；false = 单开一张新卡
     */
    public static boolean shouldMerge(boolean sameKey, boolean sameLook, MergeMode mergeMode) {
        // NONE 档下身份键本来就每张不同（见 ItemIdentity），这里再挡一道只是把语义写明白
        return mergeMode != MergeMode.NEVER && sameKey && sameLook;
    }
}
