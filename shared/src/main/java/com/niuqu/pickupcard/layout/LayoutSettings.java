package com.niuqu.pickupcard.layout;

/**
 * 卡片在屏幕上怎么摆、怎么出现。**行为参数，进 {@code config/pickupcard-client.toml}**，
 * 不进主题 JSON —— 判据是"换一套主题时，应不应该把它一起换掉"：换主题该换的是配色和
 * 质感，不该让卡片突然跳到屏幕另一边（那会被当成 bug）。
 *
 * @param stickTo    卡片靠屏幕哪一边停。{@link Side#RIGHT} = 右边固定、左边随内容伸缩（推荐）；
 *                   {@link Side#LEFT} = 左边尽量停在 {@code leftEdge}。
 * @param leftEdge   {@code stickTo = LEFT} 时卡片左边想要停的位置（距屏幕左边多少像素）。
 *                   它是<b>软目标</b>：放不下时自动往左让，绝不把内容挤出屏幕。
 * @param appearMode 卡片出现时怎么展开。{@link Appear#SLIDE} = 内容保持原样从左边平移出来；
 *                   {@link Appear#CLIP} = 内容不动、可见范围从左往右扩大。
 */
public record LayoutSettings(Side stickTo, int leftEdge, Appear appearMode) {

    /** 卡片靠哪一边停。 */
    public enum Side {
        /** 右边固定，左边随内容长短伸缩。内容再长也只是往左伸，不会超出屏幕右边。 */
        RIGHT,
        /** 左边尽量停在 {@code leftEdge}，内容往右伸展。 */
        LEFT
    }

    /** 卡片出现时的展开方式。 */
    public enum Appear {
        /** 内容保持原样，从左往右平移到最终位置：先看到最右端，再逐渐看到全部。 */
        SLIDE,
        /** 内容位置不动，可见范围从左往右慢慢扩大：先看到最左端。 */
        CLIP
    }

    public static LayoutSettings defaults() {
        return new LayoutSettings(Side.RIGHT, 320, Appear.SLIDE);
    }

    /** 外部来的值一律过一遍：配置文件是玩家可改的，非法值不该变成崩溃或卡片消失。 */
    public LayoutSettings sanitized() {
        return new LayoutSettings(
                stickTo == null ? Side.RIGHT : stickTo,
                Math.max(0, leftEdge),
                appearMode == null ? Appear.SLIDE : appearMode);
    }

    /** 卡片左边允许到的最右位置。右边固定时就是屏宽（即"尽量往右推"）。 */
    public float leftLimit(float guiWidth) {
        return stickTo == Side.RIGHT ? guiWidth : leftEdge;
    }
}
