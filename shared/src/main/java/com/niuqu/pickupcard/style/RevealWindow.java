package com.niuqu.pickupcard.style;

/**
 * 入场时"内容能被看见的那一段"—— 也就是草稿里的<b>隧道口</b>。
 *
 * <p>草稿（{@code design/animation.html}）里这个窗口不是画出来的，是 <b>CSS 结构</b>给的：
 * <pre>.card { [竖条] gap [ .tunnel(overflow:hidden) [ .body 内容 ] ] }</pre>
 * 所以窗口的左边 = <b>竖条宽 + 间隙</b>，内容再往左滑也被裁掉 —— 视觉上就是"从竖条后面
 * 冒出来"。游戏里没有 DOM，这个窗口必须由代码算出来、交给三条绘制路径（SDF 裁剪、
 * NanoVG 裁剪、原版内容裁剪）用同一个数。
 *
 * <p>【为什么单独成类】这里曾经有两处各算各的：SDF 那条把窗口左边当成卡片左缘（0），
 * NanoVG 那条<b>压根没有窗口</b>。于是内容在坐标上滑过去了、却没有任何东西挡，
 * 表现为"卡片从左往右穿过竖条"。同一条几何算两遍，就一定会有一个地方漏。
 * 放进 {@code shared} 是为了它能被单测钉住 —— 渲染路径上的东西不好测，"窗口在哪"能测。
 *
 * @param left  窗口左边（卡片内局部坐标）
 * @param width 窗口宽度；{@code 0} = 内容全被挡住
 */
public record RevealWindow(float left, float width) {

    /** 内容区起点：竖条宽 + 间隙。窗口的左边就是它，不是卡片左缘。 */
    public static float contentLeft(float barWidth, float gap) {
        return barWidth + gap;
    }

    /** 内容自然宽（= 卡片总宽减去竖条与间隙）。 */
    public static float contentWidth(float cardWidth, float barWidth, float gap) {
        return Math.max(0f, cardWidth - barWidth - gap);
    }

    /**
     * 算出这一帧的窗口。
     *
     * <p>两种展开方式只差"窗口多宽"，不差"窗口从哪开始"：
     * <ul>
     *   <li>{@code slide}（草稿的火车档）：窗口<b>全程是内容自然宽</b>，是内容自己在里面
     *       从 {@code -100%} 平移到 0；</li>
     *   <li>{@code clip}（草稿的拉幕档）：内容不动，窗口从 0 往右长到内容自然宽。</li>
     * </ul>
     * 两种都要裁左边 —— 少了这一条，滑出去的内容会先出现在竖条<b>上面</b>。
     */
    public static RevealWindow of(float barWidth, float gap, float cardWidth,
                                  boolean clip, float rise) {
        float left = contentLeft(barWidth, gap);
        float full = contentWidth(cardWidth, barWidth, gap);
        return new RevealWindow(left, clip ? full * Easing.clamp01(rise) : full);
    }

    /** 窗口右边。 */
    public float right() {
        return left + width;
    }
}
