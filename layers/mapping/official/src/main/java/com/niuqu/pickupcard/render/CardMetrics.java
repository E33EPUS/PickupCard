package com.niuqu.pickupcard.render;

import com.niuqu.pickupcard.pickup.CardContent;
import com.niuqu.pickupcard.layout.LayoutSettings;
import com.niuqu.pickupcard.notice.PickupCardSettings;
import com.niuqu.pickupcard.pickup.Inbox;
import net.minecraft.client.gui.Font;
import net.minecraft.core.registries.BuiltInRegistries;
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

    /** 三个框的统一高度（已按缩放放大到屏幕像素）。 */
    public static float height(CardCanvas canvas, Font font) {
        return canvas.style().boxHeight() * canvas.scale();
    }

    /**
     * 这张卡<b>不可再压</b>的宽度：竖条 + 间隙 + 图标框 + 间隙 + 内边距 + 数量。
     * <p>名字是唯一能被截掉的部分，所以这个数就是"再怎么截也这么大"。条带比它还窄时，
     * 只剩两条路：把整张卡缩小（下限 {@code MIN_AUTO_PERCENT}），或者回退位置 ——
     * 判据要有这个数才算得出来。
     */
    public static float fixedWidth(CardCanvas canvas, Font font, CardView view) {
        var style = canvas.style();
        float gap = style.gap();
        return style.barWidth() + gap + style.boxHeight() + gap
                + style.paddingH() * 2f + canvas.countWidth(view, font);
    }

    /**
     * 这张卡允许的最大宽度（屏幕像素）= <b>屏宽比例</b>与<b>右侧条带宽度</b>的较小者。
     * <p>
     * 【为什么要看条带】从前只有 {@code guiWidth × 0.45} 一条：卡片最宽可以到 192px，
     * 而快捷栏右边那条带在 427 宽的画布上只有 105.5px —— 长名字的卡会横着溢出条带、
     * 压在快捷栏上。收住上限之后，"名字太长"就退化成已有的那条路：截断加省略号，
     * 而不是把整张卡缩小（缩到 55% 的卡，8px 的字就成 4.4px 了）。
     */
    public static float maxWidth(CardCanvas canvas) {
        float ratio = canvas.guiWidth() * MAX_WIDTH_RATIO;
        float strip = canvas.stripWidth();
        return strip > 0f ? Math.min(ratio, strip) : ratio;
    }

    /**
     * 总宽 = 竖条 + 间隙 + 图标格 + 间隙 + 信息框。
     * <p>
     * 【信息框里有什么，由"显示物品名"决定】开着是「名字 + 间隙 + 数量」，关掉就只剩数量
     * —— 卡会明显变窄，而"竖条 + 图标 + 数量"这个最小组合仍然一眼能读。
     */
    public static float width(CardCanvas canvas, Font font, CardView view) {
        return naturalWidth(canvas, font, view) * canvas.scale();
    }

    /**
     * 同一张卡在 <b>100%</b> 下的宽度（不乘当前缩放）。
     * <p>
     * 【为什么要单独有一个】缩放在"知道卡有多宽"之前就得定下来（右侧条带装不下时要按宽度
     * 再收一次，见 {@code CardStage#layout}），而 {@link #width} 里已经乘过缩放了 ——
     * 用 {@code width()/scale} 反推的话，改缩放的那一刻就会拿错值。分成两个函数之后，
     * "未缩放宽度"只有一个出处。
     */
    public static float naturalWidth(CardCanvas canvas, Font font, CardView view) {
        Inbox.Card card = view.notice().payload();
        int count = view.notice().count();
        var style = canvas.style();
        float gap = style.gap();
        // 数字滚动中按"旧值/新值里宽的那个"占位，否则 1 → 10 会在滚到一半时把卡撑宽
        float infoBox = style.paddingH() * 2f + canvas.countWidth(view, font);
        if (canvas.settings().showItemName()) {
            infoBox += gap + font.width(fittedName(canvas, font, card, count));
        }
        return style.barWidth() + gap + style.boxHeight() + gap + infoBox;
    }

    /**
     * 卡上**实际画出来**的那个名字：太长就按像素宽度截断并补省略号。
     * <p>
     * 截断只作用于名字，<b>数量永远完整</b> —— 数量短、而且正是玩家最想知道的信息。
     * 用 {@code Font.plainSubstrByWidth} 按像素而不是按字符截，中英混排才不会截歪。
     */
    public static String fittedName(CardCanvas canvas, Font font, Inbox.Card card, int count) {
        // 溢出卡的名字要带上"还有几项"——那个数在 count 里，只有这里拿得到
        String name = card.content() instanceof CardContent.Overflow
                ? Component.translatable("pickupcard.overflow", count).getString()
                : displayName(card, canvas.settings());
        var style = canvas.style();
        float gap = style.gap();
        // 先算"除了名字之外固定要占的宽度"，剩下的才是名字能用的
        float fixed = style.barWidth() + gap + style.boxHeight() + gap
                + style.paddingH() * 2f + gap + font.width(canvas.countText(count));
        // 【全部在"未缩放单位"里算】文字是 pose 缩放后画的，字形本身按 100% 栅格化；
        // 屏宽上限与玩家设的名字宽度都是<b>屏幕像素</b>，所以除回缩放才是这里的可用宽度。
        float scale = canvas.scale();
        float room = Math.max(0f, maxWidth(canvas) / scale - fixed);
        // 玩家自己设了上限就用更严的那个（0 = 没设，按屏宽比例）
        int limit = canvas.settings().nameMaxWidth();
        if (limit > 0) {
            room = Math.min(room, limit / scale);
        }
        if (font.width(name) <= room) {
            return name;
        }
        int budget = (int) Math.max(0f, room - font.width(ELLIPSIS));
        return font.plainSubstrByWidth(name, budget) + ELLIPSIS;
    }

    /**
     * 卡上显示的名字原文（未截断）。经验卡没有 ItemStack，走翻译键。
     * <p>
     * 【"显示物品 ID"为什么要在这儿兑现】它是"看名字"的另一种写法，不是另一张卡 ——
     * 所以只换这一处文本，宽度、截断、颜色全都不用动（连截断逻辑都是同一份）。
     */
    public static String displayName(Inbox.Card card, PickupCardSettings settings) {
        if (card.content() instanceof CardContent.Item item) {
            if (settings.showItemId()) {
                return BuiltInRegistries.ITEM.getKey(item.stack().getItem()).toString();
            }
            return item.stack().getHoverName().getString();
        }
        if (card.content() instanceof CardContent.Overflow) {
            // 不带数的兜底（带数的那条在 fittedName 里，它拿得到 count）
            return Component.translatable("pickupcard.overflow.many").getString();
        }
        return Component.translatable("pickupcard.xp").getString();
    }
}
