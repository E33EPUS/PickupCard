package com.niuqu.pickupcard.render.painter;

import com.niuqu.pickupcard.pickup.CardContent;
import com.niuqu.pickupcard.pickup.Inbox;
import com.niuqu.pickupcard.rarity.RarityAccent;
import com.niuqu.pickupcard.render.CardCanvas;
import com.niuqu.pickupcard.render.CardMetrics;
import com.niuqu.pickupcard.render.CardPainter;
import com.niuqu.pickupcard.render.CardSlot;
import com.niuqu.pickupcard.render.CardView;
import com.niuqu.pickupcard.style.Easing;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * 骨架期的基线画法：一块卡面 + 左侧强调色条 + 物品图标 + 名字 + 数量。
 * <p>
 * 【它为什么故意长得朴素】它的职责只有一个：<b>证明整条管线是通的</b> ——
 * 包嗅探 → 过滤 → 合并账本 → 事件 → 卡 → 排布 → 上屏 → 动画 → 退场。
 * 任何"好看的"决定都还没做，所以这里不该出现任何会被误当成风格的细节
 * （圆角、渐变、玻璃、稀有度双圈……都不在这里）。
 * <p>
 * 【换掉它有多容易】写一个新的 {@link CardPainter}，在 {@code CardStage} 上
 * {@code setPainter} 一下就完了。拾取管线、账本、排布、主题加载一行都不用动 ——
 * 这正是上一版缺的那道缝。
 * <p>
 * 【它已经实现了的、与画法无关的部分】入场/退场的位移与缩放。这三条曲线来自主题的
 * 时间轴，属于"动画性格"而不是"画风"，所以放在基线画法里先立起来，换画法时照抄即可。
 */
public final class BaselineCardPainter implements CardPainter {

    /** 入场时的起步下移量（像素）：从下方 18px 回弹上来。 */
    private static final float ENTER_RISE = 18f;
    /** 退场时的下沉量。 */
    private static final float EXIT_DROP = 12f;
    /** 左侧强调条的宽度。 */
    private static final int ACCENT_BAR = 3;

    @Override
    public void paint(GuiGraphics gui, CardCanvas canvas, List<CardSlot> slots) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        for (CardSlot slot : slots) {
            PoseStack pose = gui.pose();
            pose.pushPose();
            motion(pose, canvas, slot);
            // 之后一律用卡内局部坐标：原点 = 卡左上角。跟屏幕坐标不再混用。
            pose.translate(slot.x(), slot.y(), 0f);
            card(gui, canvas, slot, font);
            pose.popPose();
        }
    }

    /** 入场回弹 + 退场下沉的公共变换。绕卡身中心转，两遍绘制时参数保持一致。 */
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

    private void card(GuiGraphics gui, CardCanvas canvas, CardSlot slot, Font font) {
        var style = canvas.style();
        CardView view = slot.view();
        Inbox.Card card = view.notice().payload();

        int w = Math.round(slot.width());
        int h = Math.round(slot.height());
        int accent = accentOf(card);

        // 卡面与描边
        gui.fill(0, 0, w, h, style.fillTop());
        gui.renderOutline(0, 0, w, h, style.border());
        // 左侧强调条
        gui.fill(0, 0, ACCENT_BAR, h, accent);

        float gap = style.gap();
        float iconPx = CardMetrics.ICON_PX * style.iconScale();
        float textY = (h - font.lineHeight) / 2f;
        float x = style.paddingH();

        if (card.content() instanceof CardContent.Item item) {
            // 物品图标交给原版：附魔光效、耐久条、模型缩放全是白拿的
            gui.renderItem(item.stack(), Math.round(x), Math.round((h - CardMetrics.ICON_PX) / 2f));
        } else {
            // 经验卡先用一块方块占位，真正的珠形留给 UI 定案
            gui.fill(Math.round(x), Math.round((h - iconPx) / 2f),
                    Math.round(x + iconPx), Math.round((h + iconPx) / 2f), accent);
        }
        x += iconPx + gap;

        String name = CardMetrics.displayName(card);
        gui.drawString(font, name, Math.round(x), Math.round(textY), style.nameColor(), true);
        x += font.width(name) + gap;

        gui.drawString(font, canvas.countText(view.notice().count()),
                Math.round(x), Math.round(textY), accent, true);
    }

    private static int accentOf(Inbox.Card card) {
        if (card.content() instanceof CardContent.Item item) {
            return RarityAccent.of(item.stack());
        }
        return RarityAccent.XP;
    }
}
