package com.niuqu.pickupcard.layout;

/**
 * 卡片在屏幕上怎么摆、怎么出现。**行为参数，进 {@code config/pickupcard-client.toml}**，
 * 不进主题 JSON —— 判据是"换一套主题时，应不应该把它一起换掉"：换主题该换的是配色和
 * 质感，不该让卡片突然跳到屏幕另一边（那会被当成 bug）。
 *
 * @param stickTo    卡片靠屏幕哪一边停。{@link Side#RIGHT} = 右边固定、左边随内容伸缩（默认）；
 *                   {@link Side#LEFT} = 左边尽量停在 {@code leftEdge}。
 * @param leftEdge   {@code stickTo = LEFT} 时卡片左边想要停的位置（距屏幕左边多少像素）。
 *                   它是<b>软目标</b>：放不下时自动往左让，绝不把内容挤出屏幕。
 *                   默认值跟右边距取同一个数，两种对齐因此对称。
 * @param appearMode 卡片出现时怎么展开。{@link Appear#SLIDE} = 内容保持原样从左边平移出来；
 *                   {@link Appear#CLIP} = 内容不动、可见范围从左往右扩大。
 */
public record LayoutSettings(Side stickTo, int leftEdge, Appear appearMode) {

    /** 卡片靠哪一边停。 */
    public enum Side {
        /** 左边尽量停在 {@code leftEdge}，内容往右伸展。一摞卡的竖条成一条竖线。 */
        LEFT,
        /** 右边固定，左边随内容长短伸缩。内容再长也只是往左伸，不会超出屏幕右边。 */
        RIGHT
    }

    /** 卡片出现时的展开方式。 */
    public enum Appear {
        /** 内容保持原样，从左往右平移到最终位置：先看到最右端，再逐渐看到全部。 */
        SLIDE,
        /** 内容位置不动，可见范围从左往右慢慢扩大：先看到最左端。 */
        CLIP
    }

    /** 默认情况下卡片离屏幕左边（或右边）的距离。两种对齐取同一个值，才对称。 */
    private static final int MARGIN_X = 16;

    /**
     * 卡片内容允许占屏宽的比例 —— 竖条左缘的"自动锚点"就是按它算出来的。
     * <p>
     * 【为什么这个数住在布局里，而不是只住在 {@code CardMetrics}】它管着两件事：
     * 卡宽上限（{@code CardMetrics.maxWidth}）与竖条锚点要预留多少右边空间。两个地方
     * 各写一份的话，调了一处另一处就不对 —— 而"竖条被挤歪"正是这么来的。
     * {@code CardMetrics.MAX_WIDTH_RATIO} 现在直接引用这里，不许再有第二份。
     */
    public static final float CONTENT_WIDTH_RATIO = 0.45f;

    /**
     * {@code leftEdge = -1} 时的自动锚点：让**最宽的那张卡**右缘正好落在右边距上。
     * <p>
     * 【为什么默认是"自动"而不是一个好看的绝对数】{@code leftEdge} 是绝对 x，而画布宽
     * 随 GUI 缩放剧烈变化（1280×720 上 guiScale 3 是 426 宽、guiScale 5 只剩 256 宽）。
     * 写死一个数的话，在另一种缩放下会被右边界夹住 —— 那时"左对齐"实际渲染成右对齐，
     * **竖条又参差了**，而配置里那个数看着还挺正常（这是本仓库记过的老坑：设了等于没设的值
     * 比没有这个值更坏）。同一份 token 要在所有缩放档下都成立，锚点就该跟着画布算。
     * <p>
     * 代价要认：比"预留宽"更长的名字会被软目标顶歪（{@code leftEdge} 从来就不是绝对坐标）。
     */
    public static final int AUTO_LEFT_EDGE = -1;

    /** 自动锚点：竖条左缘该停在哪（距屏幕左边多少 px）。 */
    public static float autoLeftEdge(float guiWidth) {
        float budget = guiWidth * CONTENT_WIDTH_RATIO;
        return Math.max(0f, guiWidth - MARGIN_X - budget);
    }

    /**
     * 默认：<b>竖条左缘锚定</b>（{@code Side.LEFT} + {@link #AUTO_LEFT_EDGE}），内容往右伸。
     * <p>
     * 【这个默认翻过两次，两次都记在这儿】v1 是 LEFT(16) → 2026-09-17 早先拍板"锚在右下角"
     * 改成 RIGHT → 当天真机反馈"竖条应该锚定位置，而不是跟着卡片长度"（右缘固定时，卡越宽
     * 竖条越靠左，一摞卡的竖条参差），又翻回 LEFT。
     * <p>
     * 两次都只翻了一个枚举值，说明**这个决定不该由默认值一个人扛**：它能配置（TOML 的
     * {@code stickTo}/{@code leftEdge}），只是还没进配置界面 —— 在那之前，玩家换不了，
     * 就只能由默认值代表所有人。
     * <p>
     * 「一摞卡的竖条成一条竖线」是 LEFT 的好处；代价是卡的右缘参差，且比自己预留的宽度还长的
     * 名字会被软目标往左顶（{@link #autoLeftEdge} 的注释里写了这件事）。
     */
    public static LayoutSettings defaults() {
        return new LayoutSettings(Side.LEFT, AUTO_LEFT_EDGE, Appear.SLIDE);
    }

    /** 外部来的值一律过一遍：配置文件是玩家可改的，非法值不该变成崩溃或卡片消失。 */
    public LayoutSettings sanitized() {
        return new LayoutSettings(
                stickTo == null ? Side.LEFT : stickTo,
                // -1 = 自动（跟着画布算），其余是绝对 x。负数只许是 -1，别的负数按 0 处理
                leftEdge == AUTO_LEFT_EDGE ? AUTO_LEFT_EDGE : Math.max(0, leftEdge),
                appearMode == null ? Appear.SLIDE : appearMode);
    }

    /**
     * 卡片左边允许到的最右位置。右边固定时就是屏宽（即"尽量往右推"）；
     * 左边固定时是玩家设的锚点，{@link #AUTO_LEFT_EDGE} 则按画布算（见 {@link #autoLeftEdge}）。
     */
    public float leftLimit(float guiWidth) {
        if (stickTo == Side.RIGHT) {
            return guiWidth;
        }
        return leftEdge == AUTO_LEFT_EDGE ? autoLeftEdge(guiWidth) : leftEdge;
    }
}
