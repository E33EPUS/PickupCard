package com.niuqu.pickupcard.render;

import com.niuqu.pickupcard.pickup.CardContent;
import com.niuqu.pickupcard.layout.LayoutSettings;
import com.niuqu.pickupcard.pickup.Inbox;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * 一张卡该多大。跟排布分开是因为它需要字体（量文字宽度），而排布是纯数学。
 * <p>
 * 【结构】三段式：<b>稀有度竖条 | 物品图标格 | 名字+数量框</b>。三个框等高，
 * 高度由主题的图标倍率与垂直内边距推出来（{@code StyleModel.boxHeight()}）。
 * <p>
 * 【卡宽跟着内容走】这也是上一版走"烘位图贴图"路线最终失败的原因：贴图定宽，
 * 卡变宽只能横拉，圆角和描边全被重采样糊掉。所以这里按"现量现算"写。
 * <p>
 * 【上限】卡宽不超过 {@link #MAX_WIDTH_RATIO} × 屏宽。这个比例不是随手取的：
 * 之前定的 45% 是在"卡片左缘固定"的前提下算的（要给右边留空间），现在默认改成
 * 右缘固定了，那个约束就不存在了，唯一的要求只剩"别占满整屏"。
 */
public final class CardMetrics {

    /** 原版物品图标是 16×16，卡上的实际大小再乘主题里的倍率。 */
    public static final float ICON_PX = 16f;

    /**
     * 卡宽上限占屏宽的比例。
     * <p>
     * 【为什么是引用而不是再来一个 0.70】它和竖条锚点的"要给右边留多少"是<b>同一个数</b>：
     * 卡宽上限比锚点预留宽更大的话，每隔一段时间就会出现"卡宽到顶、竖条被软目标顶歪"的
     * 组合。两个地方各写一份，调了一处另一处不会跟着动 —— 上一版就是这么漂的。
     */
    public static final float MAX_WIDTH_RATIO = LayoutSettings.CONTENT_WIDTH_RATIO;

    /** 名字被截断时补在末尾的省略号。 */
    private static final String ELLIPSIS = "…";

    private CardMetrics() {
    }

    /** 三个框的统一高度。 */
    public static float height(CardCanvas canvas, Font font) {
        return canvas.style().boxHeight();
    }

    /** 这张卡允许的最大宽度。 */
    public static float maxWidth(CardCanvas canvas) {
        return canvas.guiWidth() * MAX_WIDTH_RATIO;
    }

    /** 总宽 = 竖条 + 间隙 + 图标格 + 间隙 + 名字框（名字框里含数量）。 */
    public static float width(CardCanvas canvas, Font font, Inbox.Card card, int count) {
        var style = canvas.style();
        float gap = style.gap();
        float nameBox = style.paddingH() * 2f
                + font.width(fittedName(canvas, font, card, count))
                + gap
                + font.width(canvas.countText(count));
        return style.barWidth() + gap + style.boxHeight() + gap + nameBox;
    }

    /**
     * 卡上**实际画出来**的那个名字：太长就按像素宽度截断并补省略号。
     * <p>
     * 截断只作用于名字，<b>数量永远完整</b> —— 数量短、而且正是玩家最想知道的信息。
     * 用 {@code Font.plainSubstrByWidth} 按像素而不是按字符截，中英混排才不会截歪。
     */
    public static String fittedName(CardCanvas canvas, Font font, Inbox.Card card, int count) {
        String name = displayName(card);
        var style = canvas.style();
        float gap = style.gap();
        // 先算"除了名字之外固定要占的宽度"，剩下的才是名字能用的
        float fixed = style.barWidth() + gap + style.boxHeight() + gap
                + style.paddingH() * 2f + gap + font.width(canvas.countText(count));
        float room = Math.max(0f, maxWidth(canvas) - fixed);
        if (font.width(name) <= room) {
            return name;
        }
        int budget = (int) Math.max(0f, room - font.width(ELLIPSIS));
        return font.plainSubstrByWidth(name, budget) + ELLIPSIS;
    }

    /** 卡上显示的名字原文（未截断）。经验卡没有 ItemStack，走翻译键。 */
    public static String displayName(Inbox.Card card) {
        if (card.content() instanceof CardContent.Item item) {
            return item.stack().getHoverName().getString();
        }
        return Component.translatable("pickupcard.xp").getString();
    }
}
