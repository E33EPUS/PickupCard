package com.niuqu.pickupcard.style;

/**
 * 一张卡的动画时间轴：只回答"现在 t 是多少"，不碰颜色、不碰渲染。
 * <p>
 * 【为什么是纯函数】时间轴的三段（入场/跳动/退场）全部由调用方传时刻进来，
 * 单测不需要伪造任何时钟。"合并把一张正在退场的卡拉回来"这类边界由渲染层改状态，
 * 本类只负责算。
 *
 * @param enterMs      入场时长（0 = 没有入场，t 恒为 1）
 * @param bumpMs       数字跳动时长
 * @param enterEnabled 入场动画开关（逐动画可关，动画敏感玩家友好）
 * @param bumpEnabled  跳动开关
 */
public record CardTimeline(long enterMs, long bumpMs, boolean enterEnabled, boolean bumpEnabled) {

    public static CardTimeline defaults() {
        return new CardTimeline(320L, 300L, true, true);
    }

    /** 竖条展开占入场总时长的比例（前 30%）。 */
    private static final float BAR_END = 0.30f;
    /** 内容从入场总时长的 18% 起跑 —— 与竖条尾部留一点重叠，两条动画才不会显得脱节。 */
    private static final float CONTENT_START = 0.18f;

    /** 入场进度 ∈ [0,1]。未启用时恒为 1（直接出现在终点）。 */
    public float enter(long now, long bornAt) {
        if (!enterEnabled || enterMs <= 0L) return 1f;
        return Easing.clamp01((now - bornAt) / (float) enterMs);
    }

    /**
     * 竖条自身的展开进度 ∈ [0,1]：在入场的头 30% 里从 0 长到满。
     * <p>
     * 【为什么竖条要单独有一条进度】"竖条先开、内容再出"是两段式，不是一条进度曲线能
     * 表达的。合成一条就会出现"竖条还没长完、内容已经开始挤出来"，看起来像卡在了半路。
     */
    public float bar(long now, long bornAt) {
        return window(enter(now, bornAt), 0f, BAR_END);
    }

    /**
     * 内容滑出的进度 ∈ [0,1]：从 18% 起跑，到尾端跑完。
     * 入场位移与缩放也用它——两处必须同源，否则外壳和文字会错位。
     */
    public float content(long now, long bornAt) {
        return window(enter(now, bornAt), CONTENT_START, 1f);
    }

    /** 把 [0,1] 的总进度映射到子区间 [a,b] 上的 [0,1]。 */
    private static float window(float t, float a, float b) {
        if (b <= a) return 1f;
        return Easing.clamp01((t - a) / (b - a));
    }

    /** 数字跳动进度 ∈ [0,1]。未启用或从未跳动时恒为 1（无缩放）。 */
    public float bump(long now, long bumpAt) {
        if (!bumpEnabled || bumpMs <= 0L || bumpAt < 0L) return 1f;
        return Easing.clamp01((now - bumpAt) / (float) bumpMs);
    }

    /** 退场进度 ∈ [0,1]。退场由渲染层记 startAt，本类只按 {@code exitMs} 归一化。 */
    public static float exit(long now, long exitStartAt, long exitMs) {
        if (exitMs <= 0L) return 1f;
        return Easing.clamp01((now - exitStartAt) / (float) exitMs);
    }
}
