package com.niuqu.pickupcard.render;

import com.niuqu.pickupcard.pickup.CardContent;
import com.niuqu.pickupcard.pickup.Inbox;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * 一张卡该多大。跟排布分开是因为它需要字体（量文字宽度），而排布是纯数学。
 * <p>
 * 【结构】三段式：<b>稀有度竖条 | 物品图标格 | 名字+数量框</b>。三个框等高，
 * 高度由主题的图标倍率与垂直内边距推出来（{@code StyleModel.boxHeight()}）。
 * <p>
 * 【为什么卡宽可以变】宽度跟着名字和数量走。这也是上一版走"烘位图贴图"路线最终失败的
 * 原因：贴图定宽，卡变宽只能横拉，圆角和描边全被重采样糊掉。所以这里从一开始就按
 * "现量现算"写，逼着画法去适配可变宽度，而不是反过来。
 */
public final class CardMetrics {

    /** 原版物品图标是 16×16，卡上的实际大小再乘主题里的倍率。 */
    public static final float ICON_PX = 16f;

    private CardMetrics() {
    }

    /** 三个框的统一高度。 */
    public static float height(CardCanvas canvas, Font font) {
        return canvas.style().boxHeight();
    }

    /** 总宽 = 竖条 + 间隙 + 图标格 + 间隙 + 名字框。 */
    public static float width(CardCanvas canvas, Font font, Inbox.Card card, int count) {
        var style = canvas.style();
        float box = style.boxHeight();
        float nameBox = style.paddingH() * 2f
                + font.width(displayName(card))
                + style.gap()
                + font.width(canvas.countText(count));
        return style.barWidth() + style.gap() + box + style.gap() + nameBox;
    }

    /** 卡上显示的名字。经验卡没有 ItemStack，走翻译键。 */
    public static String displayName(Inbox.Card card) {
        if (card.content() instanceof CardContent.Item item) {
            return item.stack().getHoverName().getString();
        }
        return Component.translatable("pickupcard.xp").getString();
    }
}
