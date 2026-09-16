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

    /** 入场进度 ∈ [0,1]。未启用时恒为 1（直接出现在终点）。 */
    public float enter(long now, long bornAt) {
        if (!enterEnabled || enterMs <= 0L) return 1f;
        return Easing.clamp01((now - bornAt) / (float) enterMs);
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
