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
import com.niuqu.pickupcard.render.nvg.NvgCanvas;
import com.niuqu.pickupcard.render.nvg.NvgCardPainter;
import com.niuqu.pickupcard.style.Easing;
import com.niuqu.pickupcard.style.StyleModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.nanovg.NanoVG;

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
    /** 顶部高光的厚度，对应 CSS 里的 1px。 */
    private static final float HIGHLIGHT_H = 1f;
    /**
     * 经验卡的图标：下界之星。
     * <p>
     * 【为什么是它】经验没有 ItemStack，原先画一块强调色方块占位。但"是什么"这件事
     * 不能靠形状猜 —— 卡片上得有个一眼认得出的记号。设计稿一直用的是下界之星贴图，
     * 参考图里经验卡那一格的主导色也正好是 nether_star.png 的三个主色
     * （#556B6B / #88A4A4 / #B9C9C9），所以照它来。
     * <p>
     * 只用来画，不动它，所以是共享的一份 —— 渲染路径不会改栈。
     */
    private static final ItemStack XP_ICON = new ItemStack(Items.NETHER_STAR);

    /** pose 变换用的复用向量，避免逐顶点分配。 */
    private static final Vector3f POS = new Vector3f();

    @Override
    public void paint(GuiGraphics gui, CardCanvas canvas, List<CardSlot> slots) {
        Font font = Minecraft.getInstance().font;
        NvgCanvas nvg = NvgCanvas.shared();
        if (nvg == null) {
            // 引擎不可用（native 没打进来 / GL3 后端初始化失败）就整条退回 SDF 层。
            // 这里没有"报错并跳过"这个选项：卡难看一点可以接受，HUD 不画不行。
            for (CardSlot slot : slots) {
                paint(gui, canvas, slot, font);
            }
            return;
        }
        paintViaNvg(gui, canvas, slots, font, nvg);
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
        motion(pose, canvas, slot);
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
        float barFill = canvas.barOf(slot.view());
        cardShadows(batch, style, bodyX, shift, bodyW, h, radius, barFill);

        // 2) 两个框：渐变底 → 顶部高光 → 描边。
        //    顺序跟 CSS 的叠法对齐（box-shadow: inset 在底色之上、outline 之下），
        //    不这么排的话高光会被描边压掉一半，看起来"画了但没效果"。
        batch.roundRectGradient(boxX, 0, h, h, radius, style.fillTop(), style.fillBottom());
        topHighlight(batch, boxX, h, style);
        batch.roundRectStroked(boxX, 0, h, h, radius, 1f, style.border());
        if (nameW > 0f) {
            batch.roundRectGradient(nameX, 0, nameW, h, radius, style.fillTop(), style.fillBottom());
            topHighlight(batch, nameX, nameW, style);
            batch.roundRectStroked(nameX, 0, nameW, h, radius, 1f, style.border());
        }

        // 3) 竖条最后画、盖在上面 —— 它是挡板，内容从它后面出来
        float bar = barFill;
        if (bar > 0.001f) {
            // 竖条上下各内缩 barInsetY：它比卡片矮一截，是设计稿定的比例
            float inset = style.barInsetY();
            float fullH = Math.max(0f, h - inset * 2f);
            float barH = fullH * bar;
            float barY = inset + (fullH - barH) / 2f;
            batch.roundRect(0, barY, barW, barH, Math.min(radius, barW / 2f), accent);
        }

        // 入场时的稀有度微光：只在高档位浅浅铺一层，不占布局
        cardGlow(batch, style, accent, isHighlighted(card), boxX, 0f, bodyW, h, radius, rise);
    }

    /**
     * 引擎路径，四趟：影子(SDF) -> 外壳(NanoVG) -> 微光(SDF) -> 内容(原版)。
     * <p>
     * 【外壳为什么能一趟画完所有卡】NanoVG 的 begin/endFrame 是全局的，而卡与卡之间不重叠，
     * 所以"先画完所有卡的外壳，再统一画内容"与逐张画视觉等价；唯一跨卡的东西是影子。
     * <p>
     * 【影子为什么还留在 SDF】NanoVG 没有模糊，而 SDF 那条 soft 软化是现成且量过的。
     * 混两套不漂亮，但比"为了统一把投影做丢"务实 —— 真要统一，等有对照再收口。
     * <p>
     * 【已知顺序差异】影子现在统一在外壳之前，旧版是"上一张的影子 -> 这张的框"。差别只在
     * 相邻两张之间那圈 3px 软边的叠色上；像素门禁若判超差就回头修。
     */
    private void paintViaNvg(GuiGraphics gui, CardCanvas canvas, List<CardSlot> slots, Font font, NvgCanvas nvg) {
        StyleModel style = canvas.style();

        ShapeBatch batch = new ShapeBatch(gui);
        for (CardSlot slot : slots) {
            float rise = canvas.contentOf(slot.view());
            pushCardPose(gui, canvas, slot);
            cardShadows(batch, style, style.barWidth() + style.gap(),
                    bodyShiftOf(canvas, slot, style, rise), bodyWOf(slot, style), slot.height(),
                    Math.min(style.cornerRadius(), slot.height() / 2f), canvas.barOf(slot.view()));
            gui.pose().popPose();
        }
        batch.flush();

        // 原版批次必须先上 GPU：NanoVG 是直接 GL
        gui.flush();
        float guiScale = (float) Minecraft.getInstance().getWindow().getGuiScale();
        nvg.begin(gui.guiWidth(), gui.guiHeight(), guiScale);
        try {
            long vg = nvg.handle();
            for (CardSlot slot : slots) {
                float rise = canvas.contentOf(slot.view());
                NanoVG.nvgSave(vg);
                motionToNvg(vg, canvas, slot);
                NvgCardPainter.paintCard(vg, style, slot.x(), slot.y(), slot.width(), slot.height(),
                        accentOf(slot.view().notice().payload()), canvas.barOf(slot.view()),
                        bodyShiftOf(canvas, slot, style, rise));
                NanoVG.nvgRestore(vg);
            }
        } finally {
            nvg.end();
        }

        ShapeBatch glow = new ShapeBatch(gui);
        for (CardSlot slot : slots) {
            float rise = canvas.contentOf(slot.view());
            Inbox.Card card = slot.view().notice().payload();
            pushCardPose(gui, canvas, slot);
            cardGlow(glow, style, accentOf(card), isHighlighted(card), boxXOf(canvas, slot, style, rise),
                    0f, bodyWOf(slot, style), slot.height(),
                    Math.min(style.cornerRadius(), slot.height() / 2f), rise);
            gui.pose().popPose();
        }
        glow.flush();

        for (CardSlot slot : slots) {
            contentOnly(gui, canvas, slot, font);
        }
    }

    /** 只画原版那部分（物品图标 + 名字 + 数量），带入场裁剪。外壳已经不在这趟里了。 */
    private void contentOnly(GuiGraphics gui, CardCanvas canvas, CardSlot slot, Font font) {
        StyleModel style = canvas.style();
        float h = slot.height();
        float gap = style.gap();
        float bodyX = style.barWidth() + gap;
        float bodyW = Math.max(0f, slot.width() - bodyX);
        float rise = canvas.contentOf(slot.view());
        boolean clip = canvas.layout().appearMode() == LayoutSettings.Appear.CLIP;
        float windowW = clip ? bodyW * rise : bodyW;
        float shift = clip ? 0f : -(1f - rise) * bodyW;

        pushCardPose(gui, canvas, slot);
        boolean revealing = rise < 1f;
        if (revealing) {
            scissor(gui, gui.pose(), bodyX + windowW, h);
        }
        body(gui, canvas, slot, font, style, h, gap, bodyX, shift);
        if (revealing) {
            // 原版内容还在 bufferSource 里排队：不在这里冲掉，它会在裁剪失效之后才画出来
            gui.bufferSource().endBatch();
            gui.disableScissor();
        }
        gui.pose().popPose();
    }

    /** 入场位移/缩放：先到卡中心缩放、再按 dy 平移、最后落到卡的左上角。 */
    private static void pushCardPose(GuiGraphics gui, CardCanvas canvas, CardSlot slot) {
        PoseStack pose = gui.pose();
        pose.pushPose();
        motion(pose, canvas, slot);
        pose.translate(slot.x(), slot.y(), 0f);
    }

    private static float bodyWOf(CardSlot slot, StyleModel style) {
        return Math.max(0f, slot.width() - style.barWidth() - style.gap());
    }

    /** 内容横向滑动量：CLIP 是"窗口变宽、内容不动"，另一模式是内容从竖条后面平移出来。 */
    private static float bodyShiftOf(CardCanvas canvas, CardSlot slot, StyleModel style, float rise) {
        if (canvas.layout().appearMode() == LayoutSettings.Appear.CLIP) {
            return 0f;
        }
        return -(1f - rise) * bodyWOf(slot, style);
    }

    private static float boxXOf(CardCanvas canvas, CardSlot slot, StyleModel style, float rise) {
        return style.barWidth() + style.gap() + bodyShiftOf(canvas, slot, style, rise);
    }

    /**
     * 配置界面里的实时预览：画一张样例卡。
     * <p>
     * 【为什么必须共用同一段代码】预览要是自己画一遍，它迟早和真卡不一样 ——
     * 那时候"所见即所得"就是假的，而且是<b>静默</b>的假（改了没用，但界面看着生效了）。
     * 这里调的正是真卡在用的三样东西：{@link #cardShadows}（SDF 影子）、
     * {@code NvgCardPainter}（外壳）、以及原版的图标与文字。
     *
     * @param cardW 卡片总宽；高度按样式算（图标 + 上下内边距）
     */
    public static void paintPreview(GuiGraphics gui, StyleModel style, float x, float y, float cardW,
                                    ItemStack icon, String name, String count, int accent) {
        float h = style.boxHeight();
        float bodyX = style.barWidth() + style.gap();
        float radius = Math.min(style.cornerRadius(), h / 2f);

        ShapeBatch batch = new ShapeBatch(gui);
        cardShadows(batch, style, bodyX, 0f, Math.max(0f, cardW - bodyX), h, radius, 1f);
        batch.flush();

        NvgCanvas nvg = NvgCanvas.shared();
        if (nvg != null && nvg.valid()) {
            gui.flush();
            float guiScale = (float) Minecraft.getInstance().getWindow().getGuiScale();
            nvg.begin(gui.guiWidth(), gui.guiHeight(), guiScale);
            try {
                NvgCardPainter.paintCard(nvg.handle(), style, x, y, cardW, h, accent, 1f, 0f);
            } finally {
                nvg.end();
            }
        }

        Font font = Minecraft.getInstance().font;
        float scale = style.iconSize() / CardMetrics.ICON_PX;
        gui.pose().pushPose();
        gui.pose().translate(x + bodyX + h / 2f, y + h / 2f, 0f);
        gui.pose().scale(scale, scale, 1f);
        gui.renderItem(icon, -8, -8);
        gui.pose().popPose();

        float textY = y + (h - font.lineHeight) / 2f;
        gui.drawString(font, name,
                Math.round(x + bodyX + h + style.gap() + style.paddingH()), Math.round(textY),
                style.nameColor(), true);
        gui.drawString(font, count, Math.round(x + cardW - style.paddingH() - font.width(count)),
                Math.round(textY), accent, true);
    }

    /**
     * 卡片投影：<b>按轮廓</b>投，不是一个包住整张卡的大矩形。
     * <p>
     * 【为什么必须这样】草稿用的是 CSS {@code filter: drop-shadow(...)}：它投的是
     * "竖条 + 图标格 + 名字框"这个<b>并集</b>的轮廓，三个框之间那几像素缝是透空的、
     * 缝里没有影子。旧版画一个覆盖整个内容宽度的大圆角矩形 —— 于是缝里多出一层灰，
     * 整块看起来就是一片廉价的深色方块（这一条用户直接指着截图问了）。
     * <p>
     * 三个形状互不重叠，所以"并集的影子"等于"三个影子并起来"，逐个投就对了。
     * 相邻两个的影子会在缝里叠一点点色，但 CSS 的模糊本来也会把缝桥接过去，
     * 所以那不是缺陷，反而更接近草稿。
     * <p>
     * 【为什么抽出来】它有两条调用路径：SDF 整条回退、以及引擎版里单独那一趟"影子"。
     * 各写一遍的话，改一处忘一处 = 回退之后忽然变丑，而且没人会注意到。
     */
    private static void cardShadows(ShapeBatch batch, StyleModel style, float bodyX, float shift,
                                    float bodyW, float h, float radius, float barFill) {
        if (style.shadowAlpha() <= 0) {
            return;
        }
        float spread = SHADOW_SPREAD;
        float soft = style.shadowBlur() / 2f;
        int color = withAlpha(0x000000, style.shadowAlpha());
        float dy = style.shadowOffsetY();
        float boxX = bodyX + shift;

        // 1) 图标格
        shadowOf(batch, boxX, dy, h, h, radius, spread, soft, color);
        // 2) 名字框
        float nameW = Math.max(0f, bodyW - h - style.gap());
        if (nameW > 0f) {
            shadowOf(batch, boxX + h + style.gap(), dy, nameW, h, radius, spread, soft, color);
        }
        // 3) 竖条（高度跟着入场动画长，影子也跟着长）
        float inset = style.barInsetY();
        float full = Math.max(0f, h - inset * 2f);
        float barH = full * Math.max(0f, Math.min(1f, barFill));
        if (barH > 0.01f) {
            float barW = style.barWidth();
            shadowOf(batch, 0f, dy + inset + (full - barH) / 2f, barW, barH,
                    Math.min(radius, barW / 2f), Math.min(spread, barW / 2f), soft, color);
        }
    }

    private static void shadowOf(ShapeBatch batch, float x, float y, float w, float h, float radius,
                                 float spread, float soft, int color) {
        batch.shadow(x - spread, y - spread, w + spread * 2f, h + spread * 2f, radius + spread, soft, color);
    }

    /**
     * 稀有度微光：叠在外壳<b>之上</b>的一层强调色柔光（经验卡、白名单强调的卡）。
     * 顺序不能挪到外壳之前 —— 那样会被框底色盖掉，看起来就是"没画"。
     */
    private static void cardGlow(ShapeBatch batch, StyleModel style, int accent, boolean highlighted,
                                 float boxX, float y, float bodyW, float h, float radius, float rise) {
        if (rise <= 0.5f || style.glowAlpha() <= 0 || !highlighted) {
            return;
        }
        batch.shadow(boxX - 2f, y - 2f, bodyW + 4f, h + 4f, radius + 2f, 3f,
                withAlpha(accent, style.glowAlpha()));
    }

    /**
     * 框顶那条 1px 高光，对应 CSS 里的 {@code box-shadow: inset 0 1px 0 var(--pc-highlight)}。
     * <p>
     * 【为什么必须要它】玻璃质感全靠这一条：没有它，框顶和框底一样暗，整块就是一片色块。
     * 它是主题里的一个键（{@code --pc-highlight}），alpha 为 0 就是不画 —— 这个语义
     * 跟主题注释里写的一致，不要在这里另加开关。
     */
    private static void topHighlight(ShapeBatch batch, float x, float w, StyleModel style) {
        if ((style.highlight() >>> 24) == 0 || w <= 0f) {
            return;
        }
        batch.roundRect(x, 0f, w, HIGHLIGHT_H, HIGHLIGHT_H / 2f, style.highlight());
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
            // 经验卡没有 ItemStack，图标是固定的下界之星（见 XP_ICON）
            gui.renderItem(XP_ICON, -8, -8);
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

    /**
     * 退场下沉量。**入场一律是 0**，两个原因：
     * <ol>
     *   <li>草稿里新卡是"原地出现"的（{@code animation.html}：新槽位直接落最终位置，
     *       复用过渡会让整张卡从别处滑进来）。旧版给它加了 8px 上升 + 0.94→1.0 缩放，
     *       还带 easeOutBack 的过冲 —— 那是草稿里根本没有的动作；</li>
     *   <li>缩放会让物品图标和文字<b>每帧重采样</b>，动起来就是发虚的。</li>
     * </ol>
     * 入场真正在跑的只有两条：竖条纵向展开、内容横向滑出。
     * <p>
     * 【为什么抽出来】外壳走 NanoVG、内容走原版，两边必须用同一个位移，
     * 否则文字会和框错位。所以数只算一处：这里。
     */
    private static float exitDropOf(CardCanvas canvas, CardSlot slot) {
        return Easing.easeInQuad(canvas.exitOf(slot.view())) * EXIT_DROP;
    }

    private static void motion(PoseStack pose, CardCanvas canvas, CardSlot slot) {
        pose.translate(0f, exitDropOf(canvas, slot), 0f);
    }

    private static void motionToNvg(long vg, CardCanvas canvas, CardSlot slot) {
        NanoVG.nvgTranslate(vg, 0f, exitDropOf(canvas, slot));
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
