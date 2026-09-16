package com.niuqu.pickupcard.render;

import com.niuqu.pickupcard.pickup.CardContent;
import com.niuqu.pickupcard.pickup.Inbox;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * 一张卡该多大。跟排布分开是因为它需要字体（量文字宽度），而排布是纯数学。
 * <p>
 * 【为什么卡宽可以变】卡身长度跟着名字走 —— 这也是上一版走"烘位图贴图"路线最终
 * 失败的原因：贴图是定宽的，卡变宽只能横拉，圆角和装饰全被重采样糊掉。所以这里
 * 从一开始就按"现量现算"写，逼着画法去适配可变宽度，而不是反过来。
 */
public final class CardMetrics {

    /** 原版物品图标是 16×16，卡上的实际大小再乘主题里的缩放系数。 */
    public static final float ICON_PX = 16f;

    private CardMetrics() {
    }

    /** 卡身高度：上下内边距各一份，中间一行字。 */
    public static float height(CardCanvas canvas, Font font) {
        return canvas.style().paddingV() * 2f + font.lineHeight;
    }

    /** 卡身宽度：内边距 + 图标 + 间隙 + 名字 + 间隙 + 数量 + 内边距。 */
    public static float width(CardCanvas canvas, Font font, Inbox.Card card, int count) {
        var style = canvas.style();
        float gap = style.gap();
        return style.paddingH() * 2f
                + ICON_PX * style.iconScale() + gap
                + font.width(displayName(card)) + gap
                + font.width(canvas.countText(count));
    }

    /** 卡上显示的名字。经验卡没有 ItemStack，走翻译键。 */
    public static String displayName(Inbox.Card card) {
        if (card.content() instanceof CardContent.Item item) {
            return item.stack().getHoverName().getString();
        }
        return Component.translatable("pickupcard.xp").getString();
    }
}
