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
import static org.lwjgl.nanovg.NanoVG.nvgScissor;
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
 * <b>影子、微光、竖条、两个内容框、入场裁剪全在 NanoVG 里</b>，SDF 图层与它的着色器
 * 一起删掉了。影子改用 NanoVG 自带的 {@code nvgBoxGradient}（见 {@link #softBox}），
 * 不用模糊也能拿到软边。
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

    /** 影子比卡片往四周放大的量。 */
    private static final float SHADOW_SPREAD = 2f;
    /** 顶部高光的厚度，对应 CSS 里的 1px。 */
    private static final float HIGHLIGHT_H = 1f;
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
                nvgSave(vg);
                // 退场：整张卡（外壳 + 竖条 + 微光 + 影子）一起淡，见类注释
                nvgGlobalAlpha(vg, exitAlphaOf(canvas, slot));
                paintShell(vg, style, slot.x(), slot.y(), slot.width(), slot.height(),
                        accentOf(card), canvas.barOf(slot.view()),
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
            // 影子的浓度在颜色里预乘，退场那份交给 nvgGlobalAlpha —— 两件事分开算，
            // 才不会出现"退场时影子没跟着淡"。
            // 【两个浓度，不是一个】用户报过"影子一出来就是满的，看着像影子先到、卡片后到"：
            // 内容的影子按**内容**露出的比例给浓度，竖条的影子按**竖条**长出来的比例给。
            // 共用一个数就会出现"竖条才长一半、影子已经全浓度"，或者竖条在长而影子还没来。
            float soft = style.shadowBlur() / 2f;
            int contentShade = withAlpha(0x000000,
                    Math.round(style.shadowAlpha() * Easing.clamp01(rise)));
            int barShade = withAlpha(0x000000,
                    Math.round(style.shadowAlpha() * Easing.clamp01(barFill)));

            if ((contentShade >>> 24) != 0) {
                // 【坐标纪律】影子按**屏幕绝对坐标**画。NanoVG 这条没有 pose（不像原版那条
                // 可以先 translate 到卡原点），而窗口是卡片局部坐标 —— 所以在这里加一次卡原点，
                // contentShadow 只吃屏幕坐标。
                // 2026-09-17 第一次写的时候把局部坐标直接喂了进来：影子被画到屏幕左上角，
                // 棋盘底截图上一眼就能看出来，而纯黑底的测量页（compare.py 的输入）**完全看不见**
                // —— 这就是"阴影从来没被量过"那个盲点的第二个实例。
                float winLeft = x + window.left();
                float winRight = x + window.right();
                float dy = y + style.shadowOffsetY();
                float iconX = x + bodyX + bodyShift;
                contentShadow(vg, stack, iconX, dy, cardH, cardH, radius, contentShade, soft, winLeft, winRight);
                float nameX = iconX + cardH + gap;
                float nameW = Math.max(0f, x + cardW - nameX);
                if (nameW > 0f) {
                    contentShadow(vg, stack, nameX, dy, nameW, cardH, radius, contentShade, soft, winLeft, winRight);
                }
            }
            if ((barShade >>> 24) != 0) {
                barShadow(vg, stack, style, x, y, cardH, barFill, barShade, soft);
            }

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
     * 一个框：渐变底 -> 顶部 1px 高光 -> 内缩 1px 描边（顺序与 CSS 叠法一致）。
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

        if ((style.highlight() >>> 24) != 0) {
            // CSS: box-shadow: inset 0 1px 0 <highlight> —— 贴着顶边的 1px 高光。
            // 玻璃质感全靠这一条：没有它，框顶和框底一样暗，整块就是一片色块。
            nvgBeginPath(vg);
            nvgRoundedRect(vg, x + 0.5f, y + 0.5f, Math.max(0f, w - 1f), HIGHLIGHT_H, HIGHLIGHT_H / 2f);
            nvgFillColor(vg, color(stack, style.highlight()));
            nvgFill(vg);
        }

        nvgBeginPath(vg);
        nvgRoundedRect(vg, x + 0.5f, y + 0.5f, Math.max(0f, w - 1f), Math.max(0f, h - 1f), radius);
        nvgStrokeWidth(vg, 1f);
        nvgStrokeColor(vg, color(stack, style.border()));
        nvgStroke(vg);
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
    // 影子：NanoVG 里没有模糊，用 boxGradient 做软边
    // ------------------------------------------------------------------

    /**
     * 一块<b>软边矩形</b>：矩形的边往外 {@code feather} 像素由 {@code argb} 渐变到全透明，
     * 矩形内部是实心 {@code argb}。
     *
     * <p>【它替掉了什么】原先影子走 SDF 着色器（{@code gui_shape.fsh} 里那段 IQ 软化），
     * 那条路要注册着色器、要自己的顶点格式、还要在 NanoVG 之外单独开一趟绘制。
     * NanoVG 没有高斯模糊，但 {@code nvgBoxGradient} 干的正好是同一件事：一个从
     * "框内实心"到"框外 N 像素处透明"的渐变。影子画在卡面<b>下面</b>，实心那部分被卡面
     * 盖住，露出来的就只有外圈那条软边 —— 与 SDF 那条的观感一致。
     *
     * <p>【为什么收成一个私有函数】影子、微光、竖条影子全是它，参数不同而已。
     * 之前这三种形状各写一遍，改一处忘一处就是用户看到的那条"影子还在穿透"。
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

    /**
     * 一处内容影子（图标格或名字框）。**必须跟内容一起被隧道口裁**。
     * <p>
     * 【这是用户报的第二遍同一个 bug】内容修好了、影子没有：影子原来是单独一趟、没有窗口，
     * 于是内容被裁在竖条右边、它的影子却从竖条左边冒出来。草稿那边是 CSS
     * {@code filter: drop-shadow} 加在 {@code .card} 上 —— 滤镜吃的是**裁剪之后**的可见形状。
     * <p>
     * 【为什么用几何求交而不是套一层 scissor】窗口左边切下去时，草稿那边是"被裁的形状"投的
     * 影 —— 切面是竖直的、模糊照旧往外散。套 scissor 会把模糊也一起切掉（出现一条硬缝），
     * 求交只切形状、模糊留着。
     *
     * @param x,y       影子矩形的左上角（**屏幕绝对坐标**）
     * @param winLeft   隧道口左边界（屏幕绝对坐标，已贴现）
     */
    private static void contentShadow(long vg, MemoryStack stack,
                                      float x, float y, float w, float h, float radius,
                                      int color, float soft, float winLeft, float winRight) {
        float spread = SHADOW_SPREAD;
        float x0 = Math.max(x, winLeft);
        float x1 = Math.min(x + w, winRight);
        if (x1 - x0 <= 0.01f) {
            return;
        }
        softBox(vg, stack, x0 - spread, y - spread, (x1 - x0) + spread * 2f, h + spread * 2f,
                radius + spread, soft, color);
    }

    /**
     * 竖条的影子（屏幕绝对坐标）。竖条在窗口左边、是"洞口"，所以它**不进**窗口 ——
     * 跟草稿里的竖条一样。
     * <p>
     * 【2026-09-17 修】影子原先垂直居中（{@code inset + (full-barH)/2}），而竖条是从上往下长
     * 的（见 {@link #bar}）—— 长到一半时影子已经四平八稳地摊在中间，比竖条还高一截。
     * 现在跟着竖条的头走。
     */
    private static void barShadow(long vg, MemoryStack stack, StyleModel style, float x, float y,
                                  float cardH, float barFill, int color, float soft) {
        float filled = Easing.clamp01(barFill);
        if (filled <= 0.01f) {
            return;
        }
        float radius = Math.min(style.cornerRadius(), cardH / 2f);
        float inset = style.barInsetY();
        float full = Math.max(0f, cardH - inset * 2f);
        float barH = full * filled;
        float spread = Math.min(SHADOW_SPREAD, style.barWidth() / 2f);
        softBox(vg, stack, x - spread, y + style.shadowOffsetY() + inset - spread,
                style.barWidth() + spread * 2f, barH + spread * 2f,
                Math.min(radius, style.barWidth() / 2f) + spread, soft, color);
    }

    // ------------------------------------------------------------------
    // 内容：物品图标 + 名字 + 数量（原版批次，画在卡面之上）
    // ------------------------------------------------------------------

    /** 只画原版那部分，带入场裁剪。 */
    private static void content(GuiGraphics gui, CardCanvas canvas, CardSlot slot, Font font) {
        StyleModel style = canvas.style();
        CardView view = slot.view();
        Inbox.Card card = view.notice().payload();
        float h = slot.height();
        float gap = style.gap();
        float barW = style.barWidth();
        float bodyX = barW + gap;
        float bodyW = Math.max(0f, slot.width() - bodyX);
        float rise = canvas.contentOf(view);
        boolean clip = canvas.layout().appearMode() == LayoutSettings.Appear.CLIP;
        RevealWindow win = RevealWindow.of(barW, gap, slot.width(), clip, rise);
        float shift = clip ? 0f : -(1f - rise) * bodyW;
        float alpha = exitAlphaOf(canvas, slot);
        float x = bodyX + shift;
        int accent = accentOf(card);

        gui.pose().pushPose();
        gui.pose().translate(slot.x(), slot.y(), 0f);
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
        gui.renderItem(card.content() instanceof CardContent.Item item ? item.stack() : XP_ICON, -8, -8);
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

    /**
     * 画一张样例卡（静止的最终态）。
     * <p>
     * 【为什么必须共用同一段代码】预览要是自己画一遍，它迟早和真卡不一样 ——
     * 那时候"所见即所得"就是假的，而且是<b>静默</b>的假（改了没用，但界面看着生效了）。
     * 这里调的正是真卡在用的 {@link #paintShell}（影子 + 竖条 + 两个框 + 微光）
     * 与下面那两行原版内容。
     *
     * @param cardW 卡片总宽；高度按样式算（图标 + 上下内边距）
     * @param glow  预览是不是"会被强调的那种卡"（经验卡 / 白名单卡）。样例画的是经验，
     *              所以预览里看得到那层微光 —— 否则玩家永远调不出它
     */
    public static void paintPreview(GuiGraphics gui, StyleModel style, float x, float y, float cardW,
                                    ItemStack icon, String name, String count, int accent, boolean glow) {
        float h = style.boxHeight();
        float bodyX = style.barWidth() + style.gap();
        float gap = style.gap();

        gui.flush();
        NvgCanvas nvg = NvgCanvas.shared();
        if (nvg != null && nvg.valid()) {
            float guiScale = (float) Minecraft.getInstance().getWindow().getGuiScale();
            nvg.begin(gui.guiWidth(), gui.guiHeight(), guiScale);
            try {
                // 静止的最终态：竖条全开、窗口全开、不淡出
                paintShell(nvg.handle(), style, x, y, cardW, h, accent, 1f, 0f, 1f, glow,
                        RevealWindow.of(style.barWidth(), gap, cardW, false, 1f));
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
                Math.round(x + bodyX + h + gap + style.paddingH()), Math.round(textY),
                style.nameColor(), true);
        gui.drawString(font, count, Math.round(x + cardW - style.paddingH() - font.width(count)),
                Math.round(textY), accent, true);
    }

    // ------------------------------------------------------------------
    // 公共
    // ------------------------------------------------------------------

    /** 这一帧这张卡的隧道口。两种展开方式只差宽度，见 {@link RevealWindow#of}。 */
    private static RevealWindow windowOf(CardCanvas canvas, CardSlot slot, StyleModel style, float rise) {
        return RevealWindow.of(style.barWidth(), style.gap(), slot.width(),
                canvas.layout().appearMode() == LayoutSettings.Appear.CLIP, rise);
    }

    /** 内容横向滑动量：CLIP 是"窗口变宽、内容不动"，另一模式是内容从竖条后面平移出来。 */
    private static float bodyShiftOf(CardCanvas canvas, CardSlot slot, StyleModel style, float rise) {
        if (canvas.layout().appearMode() == LayoutSettings.Appear.CLIP) {
            return 0f;
        }
        float bodyW = Math.max(0f, slot.width() - style.barWidth() - style.gap());
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
        return 1f - Easing.easeOutCubic(canvas.exitOf(slot.view()));
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
