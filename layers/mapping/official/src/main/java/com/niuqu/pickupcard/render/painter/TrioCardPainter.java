package com.niuqu.pickupcard.render.painter;

import com.mojang.blaze3d.systems.RenderSystem;
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
import com.niuqu.pickupcard.style.RevealWindow;
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
        RevealWindow win = RevealWindow.of(barW, gap, slot.width(), clip, rise);
        float shift = clip ? 0f : -(1f - rise) * bodyW;

        // 退场淡出：这一张卡的每一样东西（影子 / 外壳 / 微光 / 图标 / 文字）都乘同一个系数。
        // 不能靠 RenderSystem.setShaderColor 一把梭 —— 形状与内容进的是各自的延迟批次，
        // 等它们真正提交时，那个全局色早被下一张卡改掉了。
        float alpha = exitAlphaOf(canvas, slot);

        PoseStack pose = gui.pose();
        pose.pushPose();
        pose.translate(slot.x(), slot.y(), 0f);

        // 【竖条先画、且不进窗口】它在窗口左边，是"洞口"（见 barShapes 的注释）
        ShapeBatch bar = new ShapeBatch(gui);
        barShapes(bar, canvas, slot, style, h, alpha);
        bar.flush();

        // 还在展开才需要裁剪；已就位的卡不开，形状层仍能合批
        boolean revealing = rise < 1f;
        if (revealing) {
            scissor(gui, pose, win, h);
        }

        ShapeBatch batch = new ShapeBatch(gui);
        chrome(batch, canvas, slot, style, h, gap, barW, bodyX, bodyW, shift, rise, alpha, win);
        batch.flush();

        body(gui, canvas, slot, font, style, h, gap, bodyX, shift, alpha);

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
                        float shift, float rise, float alpha, RevealWindow win) {
        Inbox.Card card = slot.view().notice().payload();
        int accent = accentOf(card);
        float radius = Math.min(style.cornerRadius(), h / 2f);
        float boxX = bodyX + shift;
        float nameX = boxX + h + gap;
        float nameW = Math.max(0f, bodyW - h - gap);

        // 1) 影子：同一个圆角矩形，边缘按 shadowBlur 软化（SDF 软化，不新增 pass）。
        //    soft 取"想要的实际模糊半径的一半"，有效区间约 0~4。
        float barFill = canvas.barOf(slot.view());
        contentShadows(batch, style, bodyX, shift, bodyW, h, radius, alpha, win, rise);

        // 2) 两个框：渐变底 → 顶部高光 → 描边。
        //    顺序跟 CSS 的叠法对齐（box-shadow: inset 在底色之上、outline 之下），
        //    不这么排的话高光会被描边压掉一半，看起来"画了但没效果"。
        batch.roundRectGradient(boxX, 0, h, h, radius,
                fade(style.fillTop(), alpha), fade(style.fillBottom(), alpha));
        topHighlight(batch, boxX, h, style, alpha);
        batch.roundRectStroked(boxX, 0, h, h, radius, 1f, fade(style.border(), alpha));
        if (nameW > 0f) {
            batch.roundRectGradient(nameX, 0, nameW, h, radius,
                    fade(style.fillTop(), alpha), fade(style.fillBottom(), alpha));
            topHighlight(batch, nameX, nameW, style, alpha);
            batch.roundRectStroked(nameX, 0, nameW, h, radius, 1f, fade(style.border(), alpha));
        }

        // 入场时的稀有度微光：只在高档位浅浅铺一层，不占布局
        cardGlow(batch, style, accent, isHighlighted(card), boxX, 0f, bodyW, h, radius, rise, alpha);
    }

    /**
     * 竖条（含它自己的影子）。**画在窗口之外** —— 它在窗口左边，是"洞口"。
     * <p>
     * 【为什么必须跟内容分开两批】SDF 回退那条把 scissor 开在整张卡之前，而竖条在窗口左边
     * （x 0..4 vs 窗口从 7 起）—— 于是**竖条被自己的裁剪吃掉了**。这是修窗口时漏掉的第 4 处，
     * 同一个"只补了一条路"的病。内容与竖条不可能重叠（窗口左边就是竖条右缘 + 间隔），
     * 所以先画竖条、再画内容，视觉与"竖条最后画"完全一致。
     */
    private void barShapes(ShapeBatch batch, CardCanvas canvas, CardSlot slot, StyleModel style,
                           float h, float alpha) {
        Inbox.Card card = slot.view().notice().payload();
        int accent = accentOf(card);
        float radius = Math.min(style.cornerRadius(), h / 2f);
        float barFill = canvas.barOf(slot.view());
        barShadow(batch, style, h, barFill, alpha);

        float bar = barFill;
        if (bar > 0.001f) {
            // 竖条上下各内缩 barInsetY：它比卡片矮一截，是设计稿定的比例
            float inset = style.barInsetY();
            float fullH = Math.max(0f, h - inset * 2f);
            float barH = fullH * bar;
            // 【从一头长，不是从中间往两头】用户报的"竖条从中间往两头长"就是这个居中算式。
            // 从上往下长 = 像隧道口的卷帘门被拉开；想改成从下往上，把这行改成
            // `barY = inset + fullH - barH` 就行（一行的事，所以没做成配置项）。
            float barY = inset;
            batch.roundRect(0, barY, style.barWidth(), barH,
                    Math.min(radius, style.barWidth() / 2f), fade(accent, alpha));
        }
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
            // 影子跟内容一起裁、竖条自己不裁 —— 与草稿的 drop-shadow 同一套（见 contentShadows）
            cardShadows(batch, canvas, slot, style, rise);
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
                // 退场淡出：NanoVG 有全局 alpha，正好是"整张卡一起淡"要的那个语义。
                // 放在 nvgSave/nvgRestore 之间，所以不用自己还原。
                NanoVG.nvgGlobalAlpha(vg, exitAlphaOf(canvas, slot));
                NvgCardPainter.paintCard(vg, style, slot.x(), slot.y(), slot.width(), slot.height(),
                        accentOf(slot.view().notice().payload()), canvas.barOf(slot.view()),
                        bodyShiftOf(canvas, slot, style, rise),
                        RevealWindow.of(style.barWidth(), style.gap(), slot.width(),
                                canvas.layout().appearMode() == LayoutSettings.Appear.CLIP, rise));
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
                    Math.min(style.cornerRadius(), slot.height() / 2f), rise, exitAlphaOf(canvas, slot));
            gui.pose().popPose();
        }
        glow.flush();

        for (CardSlot slot : slots) {
            contentOnly(gui, canvas, slot, font, exitAlphaOf(canvas, slot));
        }
    }

    /** 只画原版那部分（物品图标 + 名字 + 数量），带入场裁剪。外壳已经不在这趟里了。 */
    private void contentOnly(GuiGraphics gui, CardCanvas canvas, CardSlot slot, Font font, float alpha) {
        StyleModel style = canvas.style();
        float h = slot.height();
        float gap = style.gap();
        float bodyX = style.barWidth() + gap;
        float bodyW = Math.max(0f, slot.width() - bodyX);
        float rise = canvas.contentOf(slot.view());
        boolean clip = canvas.layout().appearMode() == LayoutSettings.Appear.CLIP;
        RevealWindow win = RevealWindow.of(style.barWidth(), gap, slot.width(), clip, rise);
        float shift = clip ? 0f : -(1f - rise) * bodyW;

        pushCardPose(gui, canvas, slot);
        boolean revealing = rise < 1f;
        if (revealing) {
            scissor(gui, gui.pose(), win, h);
        }
        body(gui, canvas, slot, font, style, h, gap, bodyX, shift, alpha);
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
        // 预览不淡出、也不入场：内容的影子按满开窗口裁（等于不裁），竖条的照常投
        RevealWindow open = RevealWindow.of(style.barWidth(), style.gap(), cardW, false, 1f);
        contentShadows(batch, style, bodyX, 0f, Math.max(0f, cardW - bodyX), h, radius, 1f, open, 1f);
        barShadow(batch, style, h, 1f, 1f);
        batch.flush();

        NvgCanvas nvg = NvgCanvas.shared();
        if (nvg != null && nvg.valid()) {
            gui.flush();
            float guiScale = (float) Minecraft.getInstance().getWindow().getGuiScale();
            nvg.begin(gui.guiWidth(), gui.guiHeight(), guiScale);
            try {
                // 预览是静止的最终态：窗口全程敞开，不裁
                NvgCardPainter.paintCard(nvg.handle(), style, x, y, cardW, h, accent, 1f, 0f,
                        RevealWindow.of(style.barWidth(), style.gap(), cardW, false, 1f));
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
    /**
     * 内容的影子（图标格 + 名字框）。**必须跟内容一起被隧道口裁**。
     * <p>
     * 【这是用户报的第二遍同一个 bug】内容修好了、影子没有：影子批原来是单独一趟、没有窗口，
     * 于是内容被裁在竖条右边、它的影子却从竖条左边冒出来。草稿那边是 CSS
     * {@code filter: drop-shadow} 加在 {@code .card} 上 —— 滤镜吃的是**裁剪之后**的可见形状
     * （实测：卡片左缘再往左只有竖条自己那圈约 3px 的模糊，没有内容的影子）。所以这里跟草稿
     * 同一套：内容裁、竖条不裁。
     * <p>
     * 【为什么用几何求交而不是套一层 scissor】窗口左边切下去时，草稿那边是"被裁的形状"投的
     * 影 —— 切面是竖直的、模糊照旧往外散。套 scissor 会把模糊也一起切掉（出现一条硬缝），
     * 求交只切形状、模糊留着。
     */
    private static void contentShadows(ShapeBatch batch, StyleModel style, float bodyX, float shift,
                                       float bodyW, float h, float radius, float alpha,
                                       RevealWindow win, float shine) {
        if (style.shadowAlpha() <= 0 || alpha <= 0.001f) {
            return;
        }
        float spread = SHADOW_SPREAD;
        float soft = style.shadowBlur() / 2f;
        // 【影子跟着形状一起长出来】用户报的"影子一出来就是满的"：内容才露出一个边，
        // 影子已经是全浓度 + 全模糊，看着像影子先到、卡片后到。浓度按出现比例给。
        int color = fade(withAlpha(0x000000, style.shadowAlpha()), alpha * Easing.clamp01(shine));
        float dy = style.shadowOffsetY();
        float boxX = bodyX + shift;

        shadowClipped(batch, boxX, dy, h, h, radius, spread, soft, color, win);
        float nameW = Math.max(0f, bodyW - h - style.gap());
        if (nameW > 0f) {
            shadowClipped(batch, boxX + h + style.gap(), dy, nameW, h, radius, spread, soft, color, win);
        }
    }

    /** 竖条的影子。竖条在窗口左边、是"洞口"，所以它**不进**窗口 —— 跟草稿里的竖条一样。 */
    private static void barShadow(ShapeBatch batch, StyleModel style, float h, float barFill, float alpha) {
        if (style.shadowAlpha() <= 0 || alpha <= 0.001f || barFill <= 0.01f) {
            return;
        }
        float spread = SHADOW_SPREAD;
        float soft = style.shadowBlur() / 2f;
        // 竖条的影子同样按它自己长出来多少给浓度（height=0 时干脆不画，见上面的 early return）
        int color = fade(withAlpha(0x000000, style.shadowAlpha()),
                alpha * Easing.clamp01(barFill));
        float radius = Math.min(style.cornerRadius(), h / 2f);
        float inset = style.barInsetY();
        float full = Math.max(0f, h - inset * 2f);
        float barH = full * Math.max(0f, Math.min(1f, barFill));
        if (barH <= 0.01f) {
            return;
        }
        float barW = style.barWidth();
        shadowOf(batch, 0f, style.shadowOffsetY() + inset + (full - barH) / 2f, barW, barH,
                Math.min(radius, barW / 2f), Math.min(spread, barW / 2f), soft, color);
    }

    /** 矩形 ∩ 隧道口之后再投 —— 求交之后宽度没了就整块不画。 */
    /**
     * 一张卡在这条路径上该投的全部影子：内容的（裁）+ 竖条的（不裁）。
     * <p>
     * 【为什么收成一个入口】影子原来散在三处调用、每处自己拼参数 —— 补窗口的时候漏掉一处，
     * 就是用户看到的那条"影子还在穿透"。现在两条路径都只说"给这张卡投影子"。
     */
    private static void cardShadows(ShapeBatch batch, CardCanvas canvas, CardSlot slot,
                                    StyleModel style, float rise) {
        float h = slot.height();
        float gap = style.gap();
        float bodyX = style.barWidth() + gap;
        float bodyW = Math.max(0f, slot.width() - bodyX);
        boolean clip = canvas.layout().appearMode() == LayoutSettings.Appear.CLIP;
        RevealWindow win = RevealWindow.of(style.barWidth(), gap, slot.width(), clip, rise);
        float alpha = exitAlphaOf(canvas, slot);
        contentShadows(batch, style, bodyX, clip ? 0f : -(1f - rise) * bodyW, bodyW, h,
                Math.min(style.cornerRadius(), h / 2f), alpha, win, rise);
        barShadow(batch, style, h, canvas.barOf(slot.view()), alpha);
    }

    private static void shadowClipped(ShapeBatch batch, float x, float y, float w, float h,
                                      float radius, float spread, float soft, int color,
                                      RevealWindow win) {
        float x0 = Math.max(x, win.left());
        float x1 = Math.min(x + w, win.right());
        if (x1 - x0 <= 0.01f) {
            return;
        }
        shadowOf(batch, x0, y, x1 - x0, h, radius, spread, soft, color);
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
                                 float boxX, float y, float bodyW, float h, float radius, float rise,
                                 float alpha) {
        if (rise <= 0.5f || style.glowAlpha() <= 0 || !highlighted) {
            return;
        }
        batch.shadow(boxX - 2f, y - 2f, bodyW + 4f, h + 4f, radius + 2f, 3f,
                fade(withAlpha(accent, style.glowAlpha()), alpha));
    }

    /**
     * 框顶那条 1px 高光，对应 CSS 里的 {@code box-shadow: inset 0 1px 0 var(--pc-highlight)}。
     * <p>
     * 【为什么必须要它】玻璃质感全靠这一条：没有它，框顶和框底一样暗，整块就是一片色块。
     * 它是主题里的一个键（{@code --pc-highlight}），alpha 为 0 就是不画 —— 这个语义
     * 跟主题注释里写的一致，不要在这里另加开关。
     */
    private static void topHighlight(ShapeBatch batch, float x, float w, StyleModel style, float alpha) {
        if ((style.highlight() >>> 24) == 0 || w <= 0f) {
            return;
        }
        batch.roundRect(x, 0f, w, HIGHLIGHT_H, HIGHLIGHT_H / 2f, fade(style.highlight(), alpha));
    }

    // ------------------------------------------------------------------
    // 内容
    // ------------------------------------------------------------------

    private void body(GuiGraphics gui, CardCanvas canvas, CardSlot slot, Font font,
                      StyleModel style, float h, float gap, float bodyX, float shift, float alpha) {
        CardView view = slot.view();
        Inbox.Card card = view.notice().payload();
        float x = bodyX + shift;
        int accent = accentOf(card);
        boolean fading = alpha < 0.999f;

        // 图标：居中缩放后交给原版渲染，附魔光效与耐久条白拿
        // 【淡出为什么借全局色调制向量】物品图标是原版画的，没有"染色"参数可传，这是唯一
        // 的入口。两件事让它安全：GuiGraphics.renderItem 内部自己会 flush（所以 set 与真正
        // 提交之间不会被别的卡插队），以及 ItemRenderer 全程不碰 setShaderColor。
        // 用完立刻还原 —— 它是全局状态，留着会波及后面每一张卡。
        if (fading) {
            // 【设色之前必须先冲一次】前面几张卡的文字此时正**排着队还没提交**，而
            // renderItem 内部自己那次 flush 会把它们一起冲出去 —— 那样它们就会跟着这张卡
            // 一起淡（受伤的是别人的字）。先把队列清空，这次设色就只落在这一张卡的图标上。
            gui.flush();
            RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        }
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
        if (fading) {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }

        // 文字：alpha 直接乘进颜色里（原版字形用的就是这个色的 alpha），不走全局色
        String name = CardMetrics.fittedName(canvas, font, card, view.notice().count());
        String count = canvas.countText(view.notice().count());
        float textY = (h - font.lineHeight) / 2f;
        float nameX = x + h + gap;
        gui.drawString(font, name, Math.round(nameX + style.paddingH()), Math.round(textY),
                fade(style.nameColor(), alpha), true);
        float countX = slot.width() + shift - style.paddingH() - font.width(count);
        gui.drawString(font, count, Math.round(countX), Math.round(textY), fade(accent, alpha), true);
    }

    // ------------------------------------------------------------------
    // 公共
    // ------------------------------------------------------------------

    /**
     * 退场不透明度（1 → 0）。入场一律是 1 —— 草稿里新卡是"原地出现"的，不淡入。
     * <p>
     * 【为什么退场<b>没有</b>位移】草稿里退役那张确实"升一格 + 淡出"，但那"一格"不是画上去
     * 的：它还在 live 里，布局会把它排到堆顶<b>上面</b>那一行（见 {@code StackLayout}），
     * {@code CardMove} 再用 340ms 把它推上去。这里要是再加一段位移，它就要动两格了。
     * 旧版反着来：+12px 的<b>向下</b>下沉（顶锚时代"被挤下去"的写法），换成下锚以后那是
     * 往反方向走。
     * <p>
     * 【为什么抽出来】外壳走 NanoVG（全局 alpha）、形状走批次顶点色、图标走全局色调制向量、
     * 文字走颜色 alpha —— 四条通道四个 API，但必须是同一个数。算错一处，退役那张就会
     * "框淡了字还在"。曲线取 easeOutCubic：快出慢停，{@code Easing} 里本来就注着
     * "透明度的默认选择"。
     */
    private static float exitAlphaOf(CardCanvas canvas, CardSlot slot) {
        return 1f - Easing.easeOutCubic(canvas.exitOf(slot.view()));
    }

    /** 把颜色自带的 alpha 再乘一个系数：{@code fade(0x80FF0000, 0.5f)} → alpha 64。 */
    private static int fade(int argb, float alpha) {
        if (alpha >= 0.999f) {
            return argb;
        }
        int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /**
     * 把局部坐标的裁剪窗按当前 pose 变换后交给 scissor。
     * <p>
     * 入场只有平移与缩放、没有旋转，所以四角包围盒是精确的。
     * 不这么做的话，动起来的那几帧裁剪框会停在原地，卡片"从框里滑出去"。
     */
    private static void scissor(GuiGraphics gui, PoseStack pose, RevealWindow win, float h) {
        Matrix4f m = pose.last().pose();
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            // 【左边是窗口的左边，不是卡片左缘】写 0 的话，内容在竖条那一段也照画不误 ——
            // 而竖条是画在位图之后才提交的，于是内容直接从竖条前面滑过去（真机上看就是"穿透"）。
            POS.set(i % 2 == 0 ? win.left() : win.right(), i < 2 ? 0f : h, 0f);
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
