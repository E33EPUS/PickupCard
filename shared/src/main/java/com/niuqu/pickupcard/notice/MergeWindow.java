package com.niuqu.pickupcard.notice;

/**
 * 合并判定：这条新拾取应该并进已有那张卡，还是单开一张？
 * <p>
 * 纯函数，不碰 ItemStack、不碰时钟 —— 时间由调用方传进来。这样"合并窗口"这条规则
 * 可以在单测里钉死。上一版在这里翻过车：演示脚本把第二个假拾取排在 2400ms，
 * 而窗口是 1200ms，于是它没合并、多弹了一张卡，直到看截图才发现。
 * 把判定独立出来之后，"窗口边界"这件事有测试盯着，不靠肉眼。
 * <p>
 * 并入的条件是三条同时成立：同一个物品、同一档外观、还在窗口内。
 * 【为什么"同一档外观"也要看】同名物品在不同 NBT 下可能分属不同稀有度，
 * 那种情况并起来会让卡面档位与内容对不上。
 */
public final class MergeWindow {

    private MergeWindow() {
    }

    /**
     * @param sameKey        是不是同一个物品（含 NBT 的规范化键）
     * @param sameLook       是不是同一套外观（稀有度档位、是否附魔等）
     * @param sinceLastMs    上一张卡"最近一次被刷新"至今经过的毫秒数
     * @param windowMs       合并窗口；<= 0 表示关掉合并
     * @return true = 并进旧卡并累加数量；false = 单开一张新卡
     */
    public static boolean shouldMerge(boolean sameKey, boolean sameLook, long sinceLastMs, long windowMs) {
        if (windowMs <= 0L) return false;
        if (!sameKey || !sameLook) return false;
        // 负数（时钟回拨/乱序）当作"刚刚发生"，并起来比另开一张更符合玩家预期
        return sinceLastMs <= windowMs;
    }
}
