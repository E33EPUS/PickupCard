package com.niuqu.pickupcard.render.nvg;

import com.mojang.blaze3d.systems.RenderSystem;
import com.niuqu.pickupcard.PickupCard;
import com.niuqu.pickupcard.layout.LayoutSettings;
import com.niuqu.pickupcard.pickup.CardContent;
import com.niuqu.pickupcard.pickup.Inbox;
import com.niuqu.pickupcard.rarity.RarityAccent;
import com.niuqu.pickupcard.render.CardCanvas;
import com.niuqu.pickupcard.render.CardMetrics;
import com.niuqu.pickupcard.render.CardSlot;
import com.niuqu.pickupcard.render.CardView;
import com.niuqu.pickupcard.style.Easing;
import com.niuqu.pickupcard.style.RevealWindow;
import com.niuqu.pickupcard.style.StyleModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.system.MemoryStack;

import java.util.List;

import static org.lwjgl.nanovg.NanoVG.nvgBeginPath;
import static org.lwjgl.nanovg.NanoVG.nvgBoxGradient;
import static org.lwjgl.nanovg.NanoVG.nvgFill;
import static org.lwjgl.nanovg.NanoVG.nvgFillColor;
import static org.lwjgl.nanovg.NanoVG.nvgFillPaint;
import static org.lwjgl.nanovg.NanoVG.nvgGlobalAlpha;
import static org.lwjgl.nanovg.NanoVG.nvgLinearGradient;
import static org.lwjgl.nanovg.NanoVG.nvgRGBA;
import static org.lwjgl.nanovg.NanoVG.nvgRect;
import static org.lwjgl.nanovg.NanoVG.nvgRestore;
import static org.lwjgl.nanovg.NanoVG.nvgRoundedRect;
import static org.lwjgl.nanovg.NanoVG.nvgSave;
import static org.lwjgl.nanovg.NanoVG.nvgScale;
import static org.lwjgl.nanovg.NanoVG.nvgScissor;
import static org.lwjgl.nanovg.NanoVG.nvgTranslate;
import static org.lwjgl.nanovg.NanoVG.nvgStroke;
import static org.lwjgl.nanovg.NanoVG.nvgStrokeColor;
import static org.lwjgl.nanovg.NanoVG.nvgStrokeWidth;

/**
 * <b>本 mod 唯一的卡面画法。</b>三段式：<pre>[稀有度竖条] [物品图标格] [物品名字 ...... 数量]</pre>
 * 三个独立的圆角矩形，等高；稀有度只体现在最左那根竖条上。
 *
 * <h2>为什么只剩一条路</h2>
 * 2026-09-17 之前，同一张卡面有四份实现：原版渲染、DOM 草稿当贴图、SDF 着色器图层、
 * NanoVG 外壳。用户的原话是"我们的项目不干净"。代价是实测出来的：
 * <ul>
 *   <li>修"内容穿透竖条"那个 bug 时，裁剪补进了三条路径、<b>漏了第四条</b>
 *       （NanoVG 的影子批没有窗口），用户第二遍才报回来；</li>
 *   <li>同一轮里 SDF 回退那条还把<b>竖条自己</b>裁掉了。</li>
 * </ul>
 * 同一个几何写 N 遍，就一定会有 N-1 遍是错的。所以这里把绘制收到一处：
 * <b>微光、竖条、两个内容框、入场裁剪全在 NanoVG 里</b>，SDF 图层与它的着色器一起删掉了。
 * <p>
 * <b>投影与顶部高光后来也删了</b>（用户 2026-09-17："直接把影子和高光删了"）——
 * 参数一起从主题里拿掉，理由见 {@link StyleModel} 的类注释。
 *
 * <h2>为什么物品图标与文字还在原版</h2>
 * 它们不是"第二种画法"，而是 MC 自己拥有的两样东西：物品图标是 3D 模型 + 附魔光效 +
 * 耐久条的渲染结果，文字是 MC 的字形图集（含中文）。这两样搬进 NanoVG 只有两条路 ——
 * 把物品塞进字体，或者把字形图集当贴图 —— 前者不可能，后者是把"文字"降级成位图。
 * 所以它们照旧走原版批次，只是排在 NanoVG 那一帧<b>之后</b>，画在卡面之上。
 *
 * <h2>入场动画：竖条先开，内容再出来</h2>
 * <ol>
 *   <li>竖条在自己该在的位置上<b>纵向展开</b>（头 30% 时间），影子跟着涨；</li>
 *   <li>内容随后从竖条右侧<b>滑出来</b>（从 18% 起跑），被隧道口 {@link RevealWindow} 裁住。</li>
 * </ol>
 * 关键不是"滑"这个动作，而是<b>卡片自身尺寸全程不变</b>——是位移，不是把内容拉长。
 *
 * <h2>退场淡出为什么用 nvgGlobalAlpha</h2>
 * 外壳是矢量、图标走原版的全局色调制向量、文字走颜色里的 alpha —— 三条通道三个 API，
 * 但必须是同一个数（{@link #exitAlphaOf}）。NanoVG 这一路正好有全局 alpha，
 * 放在 save/restore 之间，等于"整张卡一起淡"，不用自己给每个颜色乘系数。
 */
public final class NvgCardPainter {

    /** 名字被截断时补的那个字符（跟 {@code CardMetrics#ELLIPSIS} 同一个字符）。 */
    private static final String ELLIPSIS = "\u2026";

    /** 稀有度微光往外扩的量与软边宽度。 */
    private static final float GLOW_SPREAD = 2f;
    private static final float GLOW_FEATHER = 3f;

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

    /** "引擎没了"这件事每次会话只该在屏幕上说一次，日志里也只需要一条。 */
    private boolean reportedMissing;

    // ------------------------------------------------------------------
    // 一帧：HUD 与 dev harness 共用这一条路径
    // ------------------------------------------------------------------

    /**
     * 画这一帧的所有卡。
     *
     * <p>【为什么先 gui.flush()】NanoVG 是直接 GL：前面 HUD 打出来的原版批次还排在
     * {@code bufferSource} 里，不等它们上 GPU 就开 NanoVG 的帧，两边的 GL 状态会打架
     * （症状是卡片周围的人名/提示错位或消失）。
     */
    public void paint(GuiGraphics gui, CardCanvas canvas, List<CardSlot> slots) {
        Font font = Minecraft.getInstance().font;

        gui.flush();

        NvgCanvas nvg = NvgCanvas.shared();
        if (nvg == null || !nvg.valid()) {
            if (!reportedMissing) {
                reportedMissing = true;
                PickupCard.LOGGER.error("NanoVG 上下文不可用：卡片这一帧不画。"
                        + "日志上面那条 [nvg] 有原因；native 缺失是发布打包的问题（见 build.gradle 的 unpackNvg）。");
            }
            return;
        }

        StyleModel style = canvas.style();
        float guiScale = (float) Minecraft.getInstance().getWindow().getGuiScale();
        nvg.begin(gui.guiWidth(), gui.guiHeight(), guiScale);
        try {
            long vg = nvg.handle();
            for (CardSlot slot : slots) {
                float rise = canvas.contentOf(slot.view());
                Inbox.Card card = slot.view().notice().payload();
                if (slot.view().exiting() || slot.view().reviving()) {
                    // 【为什么要这一行】用户报过「淡出最后一帧图标和文字完全不透明，然后消失」。
                    // 这件事只有逐帧数值能定死：alpha 一路单调到 0 说明问题在绘制那一路；
                    // alpha 中途跳回 1 就是这张卡被救回来 / 重挂了（见 CardView#absorbMerge）。
                    // 退场只有十几帧，不会刷屏。
                    PickupCard.LOGGER.info("[退场/淡回] key={} 进度={} alpha={}", slot.view().key(),
                            String.format(java.util.Locale.ROOT, "%.2f",
                                    slot.view().reviving() ? canvas.reviveOf(slot.view())
                                            : canvas.exitOf(slot.view())),
                            String.format(java.util.Locale.ROOT, "%.2f", exitAlphaOf(canvas, slot)));
                }
                nvgSave(vg);
                // 退场：整张卡（外壳 + 竖条 + 微光 + 影子）一起淡，见类注释
                nvgGlobalAlpha(vg, exitAlphaOf(canvas, slot));
                // 【缩放落在变换上】外壳一律按"未缩放的卡"画，位置与大小由这两个变换给。
                // 这样竖条宽、圆角、描边、微光全都一起缩，不会出现"卡小了但边还是粗的"。
                // NanoVG 的 scissor 也会被当前变换带走，所以 paintShell 里的裁剪框照样对。
                // 【两个缩放不是一回事，别合并】S 是布局缩放（把"未缩放的卡"换算成屏幕像素），
                // p 是脉冲倍率（再次拾起时整张卡鼓一下）。外壳按 W0×H0 画，屏幕尺寸 = W0·S·p，
                // 所以 W0 要除 S 而不是除 S·p —— 合并成一个的话卡会越鼓越小。
                // 【脉冲为什么以卡心为原点】以左上角为原点时卡片会一边放大一边往右下"长出去"，
                // 读起来是位移；以卡心为原点才是原地鼓。内容那一路必须用同一个原点，否则错开。
                float cardScale = canvas.scale();
                float pulse = canvas.pulseOf(slot.view());
                float w0 = slot.width() / cardScale;
                float h0 = slot.height() / cardScale;
                nvgTranslate(vg, slot.x() + slot.width() / 2f, slot.y() + slot.height() / 2f);
                nvgScale(vg, cardScale * pulse, cardScale * pulse);
                paintShell(vg, style, -w0 / 2f, -h0 / 2f, w0, h0,
                        accentOf(card, style.accents()), canvas.barOf(slot.view()),
                        bodyShiftOf(canvas, slot, style, rise), rise, isHighlighted(card),
                        windowOf(canvas, slot, style, rise));
                nvgRestore(vg);
            }
        } finally {
            nvg.end();
        }

        // 内容排在 NanoVG 之后：它画在卡面之上（用的是同一批屏幕坐标）
        for (CardSlot slot : slots) {
            content(gui, canvas, slot, font);
        }
    }

    // ------------------------------------------------------------------
    // 外壳：影子 -> 竖条 -> 两个框 -> 微光
    // ------------------------------------------------------------------

    /**
     * 一张卡的全部矢量部分。退场淡出不在这里 —— 调用方用 {@code nvgGlobalAlpha} 一笔带过，
     * 这里只管"浓度随入场进度"的那部分（影子、竖条）。
     *
     * @param x,y      卡片左缘 / 顶边（屏幕逻辑坐标）
     * @param barFill  竖条展开比例 0~1
     * @param bodyShift 两个内容框的横向偏移（内容从竖条后面滑出来用）；竖条自己不动，
     *                  它是"洞口"，所以只有内容偏移
     * @param rise     内容出现进度 0~1。影子浓度与微光都按它给 —— 用户报过"影子一出来就是
     *                 满的，看着像影子先到、卡片后到"
     * @param highlighted 值得给一层稀有度微光的卡（经验卡与白名单强调的卡）
     * @param window   隧道口：内容能被看见的那一段。**偏移必须有它配套**：只有偏移没有裁剪，
     *                 两个框就会从竖条前面滑过去 —— 真机上看就是"卡片穿透竖条"
     */
    public static void paintShell(long vg, StyleModel style, float x, float y, float cardW, float cardH,
                                  int accent, float barFill, float bodyShift, float rise,
                                  boolean highlighted, RevealWindow window) {
        float gap = style.gap();
        float barW = style.barWidth();
        float bodyX = barW + gap;
        float bodyW = Math.max(0f, cardW - bodyX);
        float radius = Math.min(style.cornerRadius(), cardH / 2f);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            // 竖条在窗口左边，是"洞口"，不受窗口影响，单独画
            bar(vg, stack, style, x, y, cardH, radius, accent, barFill);

            // 【两个内容框必须在窗口里画】偏移让框往左走，窗口把左边多出来的那截切掉，
            // 合起来才是"从隧道口冒出来"。用 save/restore 包住：scissor 是 NanoVG 的
            // 状态，留着会影响后面每一张卡。
            nvgSave(vg);
            nvgScissor(vg, x + window.left(), y, window.width(), cardH);
            if (window.width() > 0.01f) {
                box(vg, stack, style, x + bodyX + bodyShift, y, cardH, cardH, radius);
                float infoX = x + bodyX + cardH + gap;
                float infoW = Math.max(0f, x + cardW - infoX);
                if (infoW > 0f) {
                    box(vg, stack, style, infoX + bodyShift, y, infoW, cardH, radius);
                }
            }
            nvgRestore(vg);

            // 微光叠在外壳之"上"：画在框之前会被底色盖掉，看起来就是没画
            if (highlighted && rise > 0.5f && style.glowAlpha() > 0) {
                softBox(vg, stack, x + bodyX - GLOW_SPREAD, y - GLOW_SPREAD,
                        bodyW + GLOW_SPREAD * 2f, cardH + GLOW_SPREAD * 2f, radius + GLOW_SPREAD,
                        GLOW_FEATHER, withAlpha(accent, style.glowAlpha()));
            }
        }
    }

    /**
     * 一个框：渐变底 -> 内缩 1px 描边（顺序与 CSS 叠法一致）。
     * <p>
     * 【描边为什么要内缩 0.5】草稿写的是 {@code outline: 1px / outline-offset: -1px}，
     * 那 1px 整条都在框内。NanoVG 的 stroke 是骑在路径上的（各出一半），照外框描会有
     * 一半跑到框外，看起来比草稿粗一倍还会盖住相邻的框。路径内缩半个线宽就等价了。
     */
    private static void box(long vg, MemoryStack stack, StyleModel style,
                            float x, float y, float w, float h, float radius) {
        if (w <= 0f || h <= 0f) {
            return;
        }
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, w, h, radius);
        NVGPaint paint = nvgLinearGradient(vg, x, y, x, y + h,
                color(stack, style.fillTop()), color(stack, style.fillBottom()), NVGPaint.mallocStack(stack));
        nvgFillPaint(vg, paint);
        nvgFill(vg);

        // 描边粗细可配：路径按半个线宽内缩，线才会整条落在框内（见上面的注释）
        float stroke = style.borderWidth();
        if (stroke > 0f) {
            float half = stroke / 2f;
            nvgBeginPath(vg);
            nvgRoundedRect(vg, x + half, y + half, Math.max(0f, w - stroke), Math.max(0f, h - stroke), radius);
            nvgStrokeWidth(vg, stroke);
            nvgStrokeColor(vg, color(stack, style.border()));
            nvgStroke(vg);
        }
    }

    /** 稀有度竖条：从上往下长，不是从中间往两头长（用户报过那个版本）。 */
    private static void bar(long vg, MemoryStack stack, StyleModel style, float x, float y, float cardH,
                            float radius, int accent, float barFill) {
        float inset = style.barInsetY();
        float full = Math.max(0f, cardH - inset * 2f);
        float barH = full * Easing.clamp01(barFill);
        if (barH <= 0.01f) {
            return;
        }
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y + inset, style.barWidth(), barH,
                Math.min(radius, style.barWidth() / 2f));
        nvgFillColor(vg, color(stack, accent));
        nvgFill(vg);
    }

    // ------------------------------------------------------------------
    // 微光的软边：NanoVG 里没有模糊，用 boxGradient 做
    // ------------------------------------------------------------------

    /**
     * 一块<b>软边矩形</b>：矩形的边往外 {@code feather} 像素由 {@code argb} 渐变到全透明，
     * 矩形内部是实心 {@code argb}。
     *
     * <p>【现在只有微光用它】投影删了之后，这是唯一还需要软边的地方（{@link #softBox} 的
     * 名字是照着当年的用途留下的）。NanoVG 没有高斯模糊，但 {@code nvgBoxGradient} 干的
     * 正好是这件事：一个从"框内实心"到"框外 N 像素处透明"的渐变。
     */
    private static void softBox(long vg, MemoryStack stack, float x, float y, float w, float h,
                                float radius, float feather, int argb) {
        int alpha = argb >>> 24;
        if (w <= 0f || h <= 0f || feather <= 0f || alpha == 0) {
            return;
        }
        NVGColor inner = color(stack, argb);
        NVGColor outer = color(stack, argb & 0x00FFFFFF);
        NVGPaint paint = nvgBoxGradient(vg, x, y, w, h, radius, feather, inner, outer,
                NVGPaint.mallocStack(stack));
        nvgBeginPath(vg);
        // 路径要比框大一圈：外圈的渐变也在这条路径里面才是画得出来的
        nvgRect(vg, x - feather, y - feather, w + feather * 2f, h + feather * 2f);
        nvgFillPaint(vg, paint);
        nvgFill(vg);
    }

    // ------------------------------------------------------------------
    // 内容：物品图标 + 名字 + 数量（原版批次，画在卡面之上）
    // ------------------------------------------------------------------

    /** 只画原版那部分，带入场裁剪。 */
    private static void content(GuiGraphics gui, CardCanvas canvas, CardSlot slot, Font font) {
        StyleModel style = canvas.style();
        CardView view = slot.view();
        Inbox.Card card = view.notice().payload();
        // 【内容全部在"未缩放单位"里算】缩放交给 pose；文字因此跟着一起缩，
        // 而字形按 100% 栅格化后重采样 —— 缩小时略有锯齿，但比"卡小了字还在外面"强。
        // 尺寸按布局缩放换算（见 paint 里那段：S 与脉冲 p 是两回事）
        float cardScale = canvas.scale();
        float h = slot.height() / cardScale;
        float cardW = slot.width() / cardScale;
        float gap = style.gap();
        float barW = style.barWidth();
        float bodyX = barW + gap;
        float bodyW = Math.max(0f, cardW - bodyX);
        float rise = canvas.contentOf(view);
        boolean clip = canvas.layout().appearMode() == LayoutSettings.Appear.CLIP;
        RevealWindow win = RevealWindow.of(barW, gap, cardW, clip, rise);
        float shift = clip ? 0f : -(1f - rise) * bodyW;
        float alpha = exitAlphaOf(canvas, slot);
        float x = bodyX + shift;
        int accent = accentOf(card, style.accents());

        gui.pose().pushPose();
        // 与外壳同一个变换：以卡心为原点、按 S·p 缩放（脉冲内外一致，见 paint 里那段说明）
        float effScale = cardScale * canvas.pulseOf(view);
        gui.pose().translate(slot.x() + slot.width() / 2f, slot.y() + slot.height() / 2f, 0f);
        if (effScale != 1f) {
            gui.pose().scale(effScale, effScale, 1f);
        }
        gui.pose().translate(-cardW / 2f, -h / 2f, 0f);
        boolean revealing = rise < 1f;
        if (revealing) {
            scissor(gui, gui.pose(), win, h);
        }

        // 图标：居中缩放后交给原版渲染，附魔光效与耐久条白拿
        // 【淡出为什么借全局色调制向量】物品图标是原版画的，没有"染色"参数可传，这是唯一
        // 的入口。两件事让它安全：GuiGraphics.renderItem 内部自己会 flush（所以 set 与真正
        // 提交之间不会被别的卡插队），以及 ItemRenderer 全程不碰 setShaderColor。
        boolean fading = alpha < 0.999f;
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
        ItemStack iconStack = card.content() instanceof CardContent.Item item
                ? item.stack()
                : card.content() instanceof CardContent.Overflow overflow
                        ? cycleIcon(overflow, canvas.now()) : XP_ICON;
        gui.renderItem(iconStack, -8, -8);
        gui.pose().popPose();
        if (fading) {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }

        // 文字：alpha 直接乘进颜色里（原版字形用的就是这个色的 alpha），不走全局色
        String count = canvas.countText(view.notice().count());
        float textY = (h - font.lineHeight) / 2f;
        if (canvas.settings().showItemName()) {
            String name = CardMetrics.fittedName(canvas, font, card, view.notice().count());
            float nameX = x + h + gap;
            gui.drawString(font, name, Math.round(nameX + style.paddingH()), Math.round(textY),
                    fade(style.nameColor(), alpha), true);
        }
        drawCount(gui, canvas, view, font, cardW + shift - style.paddingH(), textY, accent, alpha);

        if (revealing) {
            // 原版内容还在 bufferSource 里排队：不在这里冲掉，它会在裁剪失效之后才画出来
            gui.bufferSource().endBatch();
            gui.disableScissor();
        }
        gui.pose().popPose();
    }

    // ------------------------------------------------------------------
    // 配置界面的实时预览
    // ------------------------------------------------------------------

    // 【这里原来有一个 paintPreview：单独把静止的最终态重画一遍】
    // 它 2026-09-18 删掉了，原因有两层：
    //   ① 它和 drawContent 是**两份**绘制实现（图标 / 名字截断 / 数字各画一遍），
    //      而预览迟早和真卡不一样就是这个界面的老毛病；
    //   ② 用户要预览**重播时间线**（第 2 条），而那份静止实现没有时间的概念。
    // 现在配置界面自己造一张真的 Notice + CardView + CardSlot，直接调本类的 paint() ——
    // 预览与游戏里共用同一条时间线、同一份排版、同一套截断，改了真卡预览自动跟上。
    // ------------------------------------------------------------------
    // 公共
    // ------------------------------------------------------------------

    /** 这一帧这张卡的隧道口。两种展开方式只差宽度，见 {@link RevealWindow#of}。 */
    private static RevealWindow windowOf(CardCanvas canvas, CardSlot slot, StyleModel style, float rise) {
        // 窗口是"卡内坐标"，所以要用未缩放的宽度（它在变换后的空间里被解释）
        return RevealWindow.of(style.barWidth(), style.gap(), slot.width() / canvas.scale(),
                canvas.layout().appearMode() == LayoutSettings.Appear.CLIP, rise);
    }

    /** 内容横向滑动量：CLIP 是"窗口变宽、内容不动"，另一模式是内容从竖条后面平移出来。 */
    private static float bodyShiftOf(CardCanvas canvas, CardSlot slot, StyleModel style, float rise) {
        if (canvas.layout().appearMode() == LayoutSettings.Appear.CLIP) {
            return 0f;
        }
        float bodyW = Math.max(0f, slot.width() / canvas.scale() - style.barWidth() - style.gap());
        return -(1f - rise) * bodyW;
    }

    /**
     * 退场不透明度（1 → 0）。入场一律是 1 —— 草稿里新卡是"原地出现"的，不淡入。
     * <p>
     * 【为什么退场<b>没有</b>位移】草稿里退役那张确实"升一格 + 淡出"，但那"一格"不是画上去
     * 的：它还在 live 里，布局会把它排到堆顶<b>上面</b>那一行（见 {@code StackLayout}），
     * {@code CardMove} 再用 340ms 把它推上去。这里要是再加一段位移，它就要动两格了。
     * 曲线取 easeOutCubic：快出慢停，{@code Easing} 里本来就注着"透明度的默认选择"。
     */
    private static float exitAlphaOf(CardCanvas canvas, CardSlot slot) {
        return canvas.exitAlphaOf(slot.view());
    }

    private static int accentOf(Inbox.Card card, StyleModel.Accents accents) {
        // 【为什么用 if 而不是 switch】1.20.1 这一支是 Java 17，模式匹配的 switch 还是预览特性
        if (card.content() instanceof CardContent.Item item) {
            return RarityAccent.of(item.stack(), accents);
        }
        return card.content() instanceof CardContent.Overflow
                ? RarityAccent.overflow(accents) : RarityAccent.xp(accents);
    }

    /**
     * 溢出卡的图标轮播：在成员之间轮流显示。
     * <p>
     * 【间隔为什么随张数变慢】3 个图标时快点没问题，8 个时再快就成了闪烁。常量是我们自己定的
     * （下限 1/4 秒、每多一个成员再慢 40ms），只借"成员越多、轮得越慢"这个行为。
     */
    private static ItemStack cycleIcon(CardContent.Overflow overflow, long nowMs) {
        List<ItemStack> stacks = overflow.stacks();
        if (stacks.isEmpty()) {
            return ItemStack.EMPTY;
        }
        long interval = Math.max(250L, 900L - 40L * stacks.size());
        return stacks.get((int) ((nowMs / interval) % stacks.size()));
    }

    /** 值得给一层稀有度微光的卡：经验卡与白名单强调的卡。 */
    private static boolean isHighlighted(Inbox.Card card) {
        return card.content() instanceof CardContent.Experience || card.emphasized();
    }

    /**
     * 把局部坐标的裁剪窗按当前 pose 变换后交给 scissor。
     * <p>
     * 入场只有平移与缩放、没有旋转，所以四角包围盒是精确的。不这么做的话，动起来的那几帧
     * 裁剪框会停在原地，卡片"从框里滑出去"。
     */
    private static void scissor(GuiGraphics gui, com.mojang.blaze3d.vertex.PoseStack pose,
                                RevealWindow win, float h) {
        Matrix4f m = pose.last().pose();
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            // 【左边是窗口的左边，不是卡片左缘】写 0 的话，内容在竖条那一段也照画不误
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

    /**
     * 数量：平时就是一行字；合并那一刻<b>从旧值滚到新值</b>（用户 2026-09-17 选的 C 档）。
     * <p>【为什么要把两行字裁在一个框里】滚动是"旧的往上走、新的从下面上来"。不裁剪的话
     * 两行会同时完整地叠在那儿，看着像重影；裁在数字这一行的高度里，才是"卷上去"。
     * <p>【为什么整串滚，而不是逐位滚】逐位要在等宽数字上做进位对齐，而位数变化（9 → 10）
     * 根本没有对应关系。整串滚在位数变化时一样成立，读起来也不差。
     */
    private static void drawCount(GuiGraphics gui, CardCanvas canvas, CardView view, Font font,
                                  float right, float textY, int accent, float alpha) {
        String cur = canvas.countText(view.notice().count());
        String prev = canvas.prevCountText(view);
        float roll = canvas.rollOf(view);
        if (prev == null || roll >= 1f) {
            gui.drawString(font, cur, Math.round(right - font.width(cur)),
                    Math.round(textY), fade(accent, alpha), true);
            return;
        }
        float t = Easing.easeOutCubic(roll);
        float lineH = font.lineHeight;
        float left = right - Math.max(font.width(cur), font.width(prev));
        // 【为什么先 flush】裁剪是"画的时候才生效"的，而前面几张卡的文字正排着队还没提交 ——
        // 不冲掉的话它们会一起被这个框裁掉（同一批 buffer 共用同一个裁剪状态）。
        gui.flush();
        // 【坐标必须是屏幕坐标】这里的 left/right/textY 是"卡内未缩放单位"，而
        // GuiGraphics#enableScissor 吃的是经当前 pose 变换后的屏幕像素 —— 直接把卡内坐标喂进去
        // 会得到一个贴着画布左上角的小框，等于把这一行字整个裁没（2026-09-18 就这么错过一次：
        // 合并之后数字从屏幕上彻底消失）。所以跟 scissor() 一样先过一遍矩阵。
        scissorLocal(gui, gui.pose(), left, textY, right, textY + lineH);
        gui.drawString(font, prev, Math.round(right - font.width(prev)),
                Math.round(textY - t * lineH), fade(accent, alpha), true);
        gui.drawString(font, cur, Math.round(right - font.width(cur)),
                Math.round(textY + (1f - t) * lineH), fade(accent, alpha), true);
        gui.flush();
        gui.disableScissor();
    }

    /**
     * 把一个<b>卡内未缩放单位</b>的矩形变成屏幕像素并开启裁剪。
     * <p>与 {@link #scissor} 同一件事，区别只是它吃的是 {@code RevealWindow}、这里吃四个边。
     */
    private static void scissorLocal(GuiGraphics gui, PoseStack pose,
                                     float x1, float y1, float x2, float y2) {
        Matrix4f m = pose.last().pose();
        Vector3f a = m.transformPosition(new Vector3f(x1, y1, 0f));
        Vector3f b = m.transformPosition(new Vector3f(x2, y2, 0f));
        gui.enableScissor((int) Math.floor(Math.min(a.x, b.x)), (int) Math.floor(Math.min(a.y, b.y)),
                (int) Math.ceil(Math.max(a.x, b.x)), (int) Math.ceil(Math.max(a.y, b.y)));
    }

    /** 把颜色自带的 alpha 再乘一个系数：{@code fade(0x80FF0000, 0.5f)} → alpha 64。 */
    private static int fade(int argb, float alpha) {
        if (alpha >= 0.999f) {
            return argb;
        }
        return withAlpha(argb, Math.round(((argb >>> 24) & 0xFF) * Easing.clamp01(alpha)));
    }

    private static int withAlpha(int argb, int alpha) {
        return (Math.min(255, Math.max(0, alpha)) << 24) | (argb & 0xFFFFFF);
    }

    /** 主题里的 ARGB -> NanoVG 要的 RGBA 分量。 */
    private static NVGColor color(MemoryStack stack, int argb) {
        return nvgRGBA((byte) (argb >> 16), (byte) (argb >> 8), (byte) argb, (byte) (argb >>> 24),
                NVGColor.mallocStack(stack));
    }
}
