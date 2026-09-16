package com.niuqu.pickupcard.render;

import com.niuqu.pickupcard.notice.PickupCardSettings;
import com.niuqu.pickupcard.style.CardTimeline;
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
 * @param guiWidth  GUI 逻辑宽度
 * @param guiHeight GUI 逻辑高度
 */
public record CardCanvas(long now,
                         CardTimeline timeline,
                         StyleModel style,
                         PickupCardSettings settings,
                         int guiWidth,
                         int guiHeight) {

    /** 入场进度 ∈ [0,1]，1 = 已就位。 */
    public float enterOf(CardView view) {
        return timeline.enter(now, view.notice().bornAt());
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

    /** 数量该怎么写（`+64` / `×64` / `+1.2K`…）。 */
    public String countText(int count) {
        return settings.countFormat().gain(count);
    }
}
