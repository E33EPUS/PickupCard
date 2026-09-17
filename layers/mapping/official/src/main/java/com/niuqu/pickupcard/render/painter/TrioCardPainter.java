package com.niuqu.pickupcard.render.painter;

import com.mojang.blaze3d.vertex.PoseStack;
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

import java.util.List;

/**
 * 三段式画法（用户 2026-09-17 定的新版式）：
 * <pre>
 *   [稀有度竖条] gap [物品图标格] gap [物品名字 .......... x64]
 * </pre>
 * 三个独立的圆角矩形，等高；稀有度只体现在最左那根竖条上，不再是整张卡身的强调色。
 * <p>
 * 【与上一版画法的区别】旧版是"胶囊卡身 + 稀有度双圈描边 + 出头圆徽章 + 名字内嵌胶囊 +
 * 右端端点 + 左外月牙"，装饰层次多、对美术调参要求高。新版把信息压到三段里，
 * 形状数从每卡约 8 个降到 3 个（外加阴影），**结构上更不容易画歪**。
 * <p>
 * 【两趟提交】形状层（竖条 + 两个框 + 阴影）先整体提交并 flush，
 * 之后才画图标与文字。这不是可选的优化：{@code MultiBufferSource} 在 {@code endBatch()}
 * 时按 RenderType 分组绘制，不是按顶点插入序——所以"形状在内容之下"只能靠
 * "形状整批先提交"来保证。
 * <p>
 * 【坐标纪律】{@code pose} 里 {@code translate} 到卡左上角一次，之后一律用卡内局部坐标。
 * 上一版"装饰全跑到屏幕左边"就是绝对坐标与局部坐标混用造成的。
 */
public final class TrioCardPainter implements CardPainter {

    /** 入场时的起步下移量（像素）：从下方回弹上来。 */
    private static final float ENTER_RISE = 18f;
    /** 退场时的下沉量。 */
    private static final float EXIT_DROP = 12f;

    @Override
    public void paint(GuiGraphics gui, CardCanvas canvas, List<CardSlot> slots) {
        if (slots.isEmpty()) {
            return;
        }

        // 第一趟：全部卡的外壳，合在一个 ShapeBatch 里
        ShapeBatch batch = new ShapeBatch(gui);
        for (CardSlot slot : slots) {
            chrome(gui, batch, canvas, slot);
        }
        batch.flush();

        // 第二趟：图标与文字
        Font font = Minecraft.getInstance().font;
        for (CardSlot slot : slots) {
            content(gui, canvas, slot, font);
        }
    }

    // ------------------------------------------------------------------
    // 外壳（矢量，走形状层）
    // ------------------------------------------------------------------

    private void chrome(GuiGraphics gui, ShapeBatch batch, CardCanvas canvas, CardSlot slot) {
        StyleModel style = canvas.style();
        Inbox.Card card = slot.view().notice().payload();
        float h = slot.height();
        float boxW = h;                                   // 图标格是正方形
        float barW = style.barWidth();
        float gap = style.gap();
        float nameX = barW + gap + boxW + gap;
        float nameW = slot.width() - nameX;
        float radius = Math.min(style.cornerRadius(), h / 2f);
        int accent = accentOf(card);

        PoseStack pose = gui.pose();
        pose.pushPose();
        motion(pose, canvas, slot);
        pose.translate(slot.x(), slot.y(), 0f);

        // 稀有度竖条：整张卡里唯一带强调色的部分
        batch.roundRect(0, 0, barW, h, Math.min(radius, barW / 2f), accent);

        // 图标格与名字框：同一个玻璃底
        float iconX = barW + gap;
        batch.roundRectGradient(iconX, 0, boxW, h, radius, style.fillTop(), style.fillBottom());
        batch.roundRectGradient(nameX, 0, nameW, h, radius, style.fillTop(), style.fillBottom());

        // 描边：先画框再描边，避免描边被底色盖住一半
        batch.roundRectStroked(iconX, 0, boxW, h, radius, 1f, style.border());
        batch.roundRectStroked(nameX, 0, nameW, h, radius, 1f, style.border());

        pose.popPose();
    }

    // ------------------------------------------------------------------
    // 内容（图标 + 文字，走原版批量通道）
    // ------------------------------------------------------------------

    private void content(GuiGraphics gui, CardCanvas canvas, CardSlot slot, Font font) {
        StyleModel style = canvas.style();
        CardView view = slot.view();
        Inbox.Card card = view.notice().payload();
        float h = slot.height();
        float boxW = h;
        float gap = style.gap();
        float iconX = style.barWidth() + gap;
        float nameX = iconX + boxW + gap;
        int accent = accentOf(card);

        PoseStack pose = gui.pose();
        pose.pushPose();
        motion(pose, canvas, slot);
        pose.translate(slot.x(), slot.y(), 0f);

        // 图标：居中缩放后交给原版渲染，附魔光效与耐久条都白拿
        float iconSize = style.iconSize();
        float scale = iconSize / CardMetrics.ICON_PX;
        pose.pushPose();
        pose.translate(iconX + boxW / 2f, h / 2f, 0f);
        pose.scale(scale, scale, 1f);
        if (card.content() instanceof CardContent.Item item) {
            gui.renderItem(item.stack(), -8, -8);
        } else {
            // 经验卡没有物品图标：用一颗小珠代替，颜色走独立色系
            gui.fill(-4, -4, 4, 4, accent);
        }
        pose.popPose();

        String name = CardMetrics.displayName(card);
        String count = canvas.countText(view.notice().count());
        float textY = (h - font.lineHeight) / 2f;
        gui.drawString(font, name, Math.round(nameX + style.paddingH()), Math.round(textY),
                style.nameColor(), true);
        gui.drawString(font, count,
                Math.round(slot.width() - style.paddingH() - font.width(count)),
                Math.round(textY), accent, true);

        pose.popPose();
    }

    // ------------------------------------------------------------------
    // 公共
    // ------------------------------------------------------------------

    /** 入场回弹 + 退场下沉的公共变换，绕卡身中心转。两趟参数必须一致，否则文字会跟外壳错位。 */
    private static void motion(PoseStack pose, CardCanvas canvas, CardSlot slot) {
        float enter = canvas.enterOf(slot.view());
        float exit = canvas.exitOf(slot.view());
        float dy = (1f - Easing.easeOutBack(enter)) * ENTER_RISE + Easing.easeInQuad(exit) * EXIT_DROP;
        float scale = (0.92f + 0.08f * Easing.easeOutBack(enter)) * (1f - 0.06f * exit);

        float cx = slot.centerX();
        float cy = slot.centerY();
        pose.translate(cx, cy, 0f);
        pose.scale(scale, scale, 1f);
        pose.translate(-cx, -cy, 0f);
        pose.translate(0f, dy, 0f);
    }

    private static int accentOf(Inbox.Card card) {
        if (card.content() instanceof CardContent.Item item) {
            return RarityAccent.of(item.stack());
        }
        return RarityAccent.XP;
    }
}
