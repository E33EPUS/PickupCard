package com.niuqu.pickupcard.render;

import com.niuqu.pickupcard.layout.LayoutSettings;
import com.niuqu.pickupcard.notice.PickupCardSettings;
import com.niuqu.pickupcard.style.CardTimeline;
import com.niuqu.pickupcard.style.Easing;
import com.niuqu.pickupcard.style.StyleModel;

/**
 * 一帧的绘制上下文：这一帧所有卡共用的东西。
 * <p>
 * 【为什么不让 painter 自己读配置】painter 每帧、每卡都要问"现在入场进度多少"，
 * 如果它各自去读配置、各自去算时间，就会出现同一帧里两张卡用了不同时刻的配置
 * （主题热重读正好卡在中间时）。在一处采样一次、往下传，这类不一致从结构上消失。
 * <p>
 * 【为什么把进度的计算放在这里】{@link CardView} 只记时刻，{@link CardTimeline} 只会算，
 * 把两者接起来需要"当前时刻"——那是这一帧的属性，不是卡或时间轴的。放这里，
 * painter 写起来就是 {@code canvas.enterOf(slot.view())}，不用自己拼三个参数。
 *
 * @param now      本帧时刻（毫秒）
 * @param timeline 入场/跳动时间轴（来自主题）
 * @param style    当前主题（已 sanitize）
 * @param settings 当前会话参数
 * @param layout   水平对齐与展开方式（行为参数，来自 TOML）
 * @param guiWidth  GUI 逻辑宽度
 * @param guiHeight GUI 逻辑高度
 * @param scale     本帧生效的卡片缩放（1.0 = 100%）。布局用它算卡的屏幕尺寸，
 *                  绘制用它决定 pose / NanoVG 变换 —— 两边必须是同一个数
 */
public record CardCanvas(long now,
                         CardTimeline timeline,
                         StyleModel style,
                         PickupCardSettings settings,
                         LayoutSettings layout,
                         int guiWidth,
                         int guiHeight,
                         float scale) {

    /** 入场进度 ∈ [0,1]，1 = 已就位。 */
    public float enterOf(CardView view) {
        return timeline.enter(now, view.notice().bornAt());
    }

    /** 竖条自身的展开进度 ∈ [0,1]。 */
    public float barOf(CardView view) {
        return timeline.bar(now, view.notice().bornAt());
    }

    /** 内容滑出的进度 ∈ [0,1]；入场位移与缩放都用它。 */
    public float contentOf(CardView view) {
        return timeline.content(now, view.notice().bornAt());
    }

    /** 数字跳动进度 ∈ [0,1]，1 = 无缩放。 */
    public float bumpOf(CardView view) {
        return timeline.bump(now, view.lastBumpAt());
    }

    /** 退场进度 ∈ [0,1]，0 = 还没退场。 */
    public float exitOf(CardView view) {
        if (!view.exiting()) return 0f;
        return CardTimeline.exit(now, view.exitStartAt(), settings.exitMs());
    }

    /** 淡回进度 ∈ [0,1]，1 = 已经回到全不透明；没在淡回时是 0。 */
    public float reviveOf(CardView view) {
        if (!view.reviving()) {
            return 0f;
        }
        long ms = CardTimeline.REVIVE_MS;
        return ms <= 0L ? 1f : Easing.clamp01((now - view.reviveAt()) / (float) ms);
    }

    /**
     * 这一帧该给这张卡的不透明度：退场是 1 → 0；淡回是从「撤消那一刻的 alpha」→ 1。
     * <p>
     * 【为什么两件事写在同一个方法里】外壳（NanoVG 的 {@code nvgGlobalAlpha}）与内容（图标的
     * 调制色、文字的颜色）必须拿到同一个数 —— 分散在两处迟早对不上，而「内容没跟着淡」
     * 正是这类 bug 的样子。
     */
    public float exitAlphaOf(CardView view) {
        if (view.reviving()) {
            // 撤消那一刻的不透明度：按「退场已经播到哪儿」算，跟当时画出的那一帧一致
            float from = 1f - Easing.easeOutCubic(
                    CardTimeline.exit(view.reviveAt(), view.exitStartAt(), settings.exitMs()));
            return from + (1f - from) * Easing.easeOutCubic(reviveOf(view));
        }
        if (!view.exiting()) {
            return 1f;
        }
        return 1f - Easing.easeOutCubic(exitOf(view));
    }

    /** 数量该怎么写（`+64` / `×64` / `+1.2K`…）。 */
    public String countText(int count) {
        return settings.countFormat().gain(count);
    }
}
