package com.niuqu.pickupcard.render.painter;

import com.mojang.blaze3d.vertex.PoseStack;
import com.niuqu.pickupcard.layout.LayoutSettings;
import com.niuqu.pickupcard.pickup.CardContent;
import com.niuqu.pickupcard.pickup.Inbox;
import com.niuqu.pickupcard.rarity.RarityAccent;
import com.niuqu.pickupcard.render.CardCanvas;
import com.niuqu.pickupcard.render.CardMetrics;
import com.niuqu.pickupcard.render.CardPainter;
import com.niuqu.pickupcard.render.CardSlot;
import com.niuqu.pickupcard.render.CardView;
import com.niuqu.pickupcard.render.shape.ShapeBatch;
import com.niuqu.pickupcard.style.Easing;
import com.niuqu.pickupcard.style.StyleModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

/**
 * 三段式画法：<pre>[稀有度竖条] [物品图标格] [物品名字 ...... 数量]</pre>
 * 三个独立的圆角矩形，等高；稀有度只体现在最左那根竖条上。
 * <p>
 * 【入场动画：竖条先开，内容再出来】
 * <ol>
 *   <li>竖条在自己该在的位置上<b>纵向展开</b>（头 30% 时间）；</li>
 *   <li>内容随后从竖条右侧<b>滑出来</b>（从 18% 起跑）。</li>
 * </ol>
 * 关键不是"滑"这个动作，而是<b>卡片自身尺寸全程不变</b>——是位移，不是把内容拉长。
 * 宽度从 0 长到满会把文字挤变形，那是另一回事。
 * <p>
 * 【竖条为什么画在最上层】它是"挡板"：内容从它后面往右走，被它遮住左边那一段，
 * 看起来才是从一个固定点里出来的。所以裁剪窗从卡片左缘（含竖条那一段）开始，
 * 而竖条最后画、盖在上面。
 * <p>
 * 【裁剪只能靠 scissor】内容里有原版绘制（物品图标、文字），没法用 SDF 去"切"它们。
 * 两个必须记住的坑：
 * <ul>
 *   <li>原版内容进的是 {@code bufferSource}，<b>必须在关掉裁剪之前把它冲出去</b>，
 *       否则它会在裁剪失效之后才真正画出来，看起来就是"裁剪没生效"；</li>
 *   <li>裁剪矩形要按当前 pose 变换后再算（入场位移/缩放会一起动），
 *       否则动起来的那几帧裁剪框会跟卡片错位。</li>
 * </ul>
 * 只有还在展开的卡才开裁剪；已经就位的卡不开，形状层因此仍能合批。
 * <p>
 * 【坐标纪律】pose 里 translate 到卡左上角一次，之后一律用卡内局部坐标。
 * 上一版"装饰全跑到屏幕左边"就是绝对坐标与局部坐标混用造成的。
 */
public final class TrioCardPainter implements CardPainter {

    /** 内容滑出时顺带的上升量（像素）。纯横向滑动会显得太干。 */
    private static final float ENTER_RISE = 8f;
    /** 退场时的下沉量。 */
    private static final float EXIT_DROP = 12f;
    /** 影子比卡片往四周放大的量。 */
    private static final float SHADOW_SPREAD = 2f;

    /** pose 变换用的复用向量，避免逐顶点分配。 */
    private static final Vector3f POS = new Vector3f();

    @Override
    public void paint(GuiGraphics gui, CardCanvas canvas, List<CardSlot> slots) {
        Font font = Minecraft.getInstance().font;
        for (CardSlot slot : slots) {
            paint(gui, canvas, slot, font);
        }
    }

    private void paint(GuiGraphics gui, CardCanvas canvas, CardSlot slot, Font font) {
        StyleModel style = canvas.style();
        float h = slot.height();
        float gap = style.gap();
        float barW = style.barWidth();
        float bodyX = barW + gap;                       // 内容区起点（局部坐标）
        float bodyW = Math.max(0f, slot.width() - bodyX);
        float rise = canvas.contentOf(slot.view());

        // 展开方式：决定"窗口多宽"和"内容偏多少"
        boolean clip = canvas.layout().appearMode() == LayoutSettings.Appear.CLIP;
        float windowW = clip ? bodyW * rise : bodyW;
        float shift = clip ? 0f : -(1f - rise) * bodyW;

        PoseStack pose = gui.pose();
        pose.pushPose();
        motion(pose, canvas, slot, rise);
        pose.translate(slot.x(), slot.y(), 0f);

        // 还在展开才需要裁剪；已就位的卡不开，形状层仍能合批
        boolean revealing = rise < 1f;
        if (revealing) {
            scissor(gui, pose, bodyX + windowW, h);
        }

        ShapeBatch batch = new ShapeBatch(gui);
        chrome(batch, canvas, slot, style, h, gap, barW, bodyX, bodyW, shift, rise);
        batch.flush();

        body(gui, canvas, slot, font, style, h, gap, bodyX, shift);

        if (revealing) {
            // 原版内容还在 bufferSource 里排队，不在这里冲掉就会跑到裁剪失效之后才画
            gui.bufferSource().endBatch();
            gui.disableScissor();
        }
        pose.popPose();
    }

    // ------------------------------------------------------------------
    // 外壳
    // ------------------------------------------------------------------

    private void chrome(ShapeBatch batch, CardCanvas canvas, CardSlot slot, StyleModel style,
                        float h, float gap, float barW, float bodyX, float bodyW,
                        float shift, float rise) {
        Inbox.Card card = slot.view().notice().payload();
        int accent = accentOf(card);
        float radius = Math.min(style.cornerRadius(), h / 2f);
        float boxX = bodyX + shift;
        float nameX = boxX + h + gap;
        float nameW = Math.max(0f, bodyW - h - gap);

        // 1) 影子：同一个圆角矩形，边缘按 shadowBlur 软化（SDF 软化，不新增 pass）。
        //    soft 取"想要的实际模糊半径的一半"，有效区间约 0~4。
        if (style.shadowAlpha() > 0) {
            float spread = SHADOW_SPREAD;
            batch.shadow(boxX - spread, style.shadowOffsetY() - spread,
                    bodyW + spread * 2f, h + spread * 2f,
                    radius + spread, style.shadowBlur() / 2f,
                    withAlpha(0x000000, style.shadowAlpha()));
        }

        // 2) 两个框
        batch.roundRectGradient(boxX, 0, h, h, radius, style.fillTop(), style.fillBottom());
        if (nameW > 0f) {
            batch.roundRectGradient(nameX, 0, nameW, h, radius, style.fillTop(), style.fillBottom());
            batch.roundRectStroked(nameX, 0, nameW, h, radius, 1f, style.border());
        }
        batch.roundRectStroked(boxX, 0, h, h, radius, 1f, style.border());

        // 3) 竖条最后画、盖在上面 —— 它是挡板，内容从它后面出来
        float bar = canvas.barOf(slot.view());
        if (bar > 0.001f) {
            // 竖条上下各内缩 barInsetY：它比卡片矮一截，是设计稿定的比例
            float inset = style.barInsetY();
            float fullH = Math.max(0f, h - inset * 2f);
            float barH = fullH * bar;
            float barY = inset + (fullH - barH) / 2f;
            batch.roundRect(0, barY, barW, barH, Math.min(radius, barW / 2f), accent);
        }

        // 入场时的稀有度微光：只在高档位浅浅铺一层，不占布局
        if (rise > 0.5f && style.glowAlpha() > 0 && isHighlighted(card)) {
            batch.shadow(boxX - 2f, -2f, bodyW + 4f, h + 4f, radius + 2f, 3f,
                    withAlpha(accent, style.glowAlpha()));
        }
    }

    // ------------------------------------------------------------------
    // 内容
    // ------------------------------------------------------------------

    private void body(GuiGraphics gui, CardCanvas canvas, CardSlot slot, Font font,
                      StyleModel style, float h, float gap, float bodyX, float shift) {
        CardView view = slot.view();
        Inbox.Card card = view.notice().payload();
        float x = bodyX + shift;
        int accent = accentOf(card);

        // 图标：居中缩放后交给原版渲染，附魔光效与耐久条白拿
        float scale = style.iconSize() / CardMetrics.ICON_PX;
        gui.pose().pushPose();
        gui.pose().translate(x + h / 2f, h / 2f, 0f);
        gui.pose().scale(scale, scale, 1f);
        if (card.content() instanceof CardContent.Item item) {
            gui.renderItem(item.stack(), -8, -8);
        } else {
            gui.fill(-4, -4, 4, 4, accent);   // 经验卡没有物品图标
        }
        gui.pose().popPose();

        String name = CardMetrics.fittedName(canvas, font, card, view.notice().count());
        String count = canvas.countText(view.notice().count());
        float textY = (h - font.lineHeight) / 2f;
        float nameX = x + h + gap;
        gui.drawString(font, name, Math.round(nameX + style.paddingH()), Math.round(textY),
                style.nameColor(), true);
        float countX = slot.width() + shift - style.paddingH() - font.width(count);
        gui.drawString(font, count, Math.round(countX), Math.round(textY), accent, true);
    }

    // ------------------------------------------------------------------
    // 公共
    // ------------------------------------------------------------------

    /** 入场上升 + 退场下沉。两趟绘制共用，否则文字会和外壳错位。 */
    private static void motion(PoseStack pose, CardCanvas canvas, CardSlot slot, float rise) {
        float exit = canvas.exitOf(slot.view());
        float dy = (1f - Easing.easeOutBack(rise)) * ENTER_RISE + Easing.easeInQuad(exit) * EXIT_DROP;
        float scale = (0.94f + 0.06f * Easing.easeOutBack(rise)) * (1f - 0.06f * exit);

        float cx = slot.centerX();
        float cy = slot.centerY();
        pose.translate(cx, cy, 0f);
        pose.scale(scale, scale, 1f);
        pose.translate(-cx, -cy, 0f);
        pose.translate(0f, dy, 0f);
    }

    /**
     * 把局部坐标的裁剪窗按当前 pose 变换后交给 scissor。
     * <p>
     * 入场只有平移与缩放、没有旋转，所以四角包围盒是精确的。
     * 不这么做的话，动起来的那几帧裁剪框会停在原地，卡片"从框里滑出去"。
     */
    private static void scissor(GuiGraphics gui, PoseStack pose, float localRight, float h) {
        Matrix4f m = pose.last().pose();
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            POS.set(i % 2 == 0 ? 0f : localRight, i < 2 ? 0f : h, 0f);
            m.transformPosition(POS);
            minX = Math.min(minX, POS.x);
            minY = Math.min(minY, POS.y);
            maxX = Math.max(maxX, POS.x);
            maxY = Math.max(maxY, POS.y);
        }
        gui.enableScissor((int) Math.floor(minX), (int) Math.floor(minY),
                (int) Math.ceil(maxX), (int) Math.ceil(maxY));
    }

    private static int accentOf(Inbox.Card card) {
        if (card.content() instanceof CardContent.Item item) {
            return RarityAccent.of(item.stack());
        }
        return RarityAccent.XP;
    }

    /** 值得给一层稀有度微光的卡：经验卡与白名单强调的卡。 */
    private static boolean isHighlighted(Inbox.Card card) {
        return card.content() instanceof CardContent.Experience || card.emphasized();
    }

    private static int black(int alpha) {
        return Math.min(255, Math.max(0, alpha)) << 24;
    }

    private static int withAlpha(int argb, int alpha) {
        return (Math.min(255, Math.max(0, alpha)) << 24) | (argb & 0xFFFFFF);
    }
}
