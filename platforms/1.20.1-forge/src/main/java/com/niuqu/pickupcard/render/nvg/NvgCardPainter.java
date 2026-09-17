package com.niuqu.pickupcard.render.nvg;

import com.niuqu.pickupcard.style.RevealWindow;
import com.niuqu.pickupcard.style.StyleModel;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.nanovg.NanoVG.nvgBeginPath;
import static org.lwjgl.nanovg.NanoVG.nvgFill;
import static org.lwjgl.nanovg.NanoVG.nvgFillColor;
import static org.lwjgl.nanovg.NanoVG.nvgFillPaint;
import static org.lwjgl.nanovg.NanoVG.nvgLinearGradient;
import static org.lwjgl.nanovg.NanoVG.nvgRGBA;
import static org.lwjgl.nanovg.NanoVG.nvgRestore;
import static org.lwjgl.nanovg.NanoVG.nvgRoundedRect;
import static org.lwjgl.nanovg.NanoVG.nvgSave;
import static org.lwjgl.nanovg.NanoVG.nvgScissor;
import static org.lwjgl.nanovg.NanoVG.nvgStroke;
import static org.lwjgl.nanovg.NanoVG.nvgStrokeColor;
import static org.lwjgl.nanovg.NanoVG.nvgStrokeWidth;

/**
 * 用 NanoVG 画一张卡的外壳：[稀有度竖条] [物品图标格] [名字框]。
 *
 * <p>几何跟 TrioCardPainter 的 chrome() 是同一个式子，逐项对齐：
 * 图标格是边长等于卡高的正方形、名字框接在它右边隔一个 gap、竖条在最左且上下各内缩 barInsetY。
 * 两边不一致时 design/compare.py 的"固定的是哪条边""间距"会立刻红 —— 这是故意的。
 *
 * <p>【描边为什么要内缩 0.5】草稿写的是 outline: 1px / outline-offset: -1px，
 * 那 1px 整条都在框内。NanoVG 的 stroke 是骑在路径上的（各出一半），照外框描会有
 * 一半跑到框外，看起来比草稿粗一倍还会盖住相邻的框。路径内缩半个线宽就等价了。
 *
 * <p>【还没做：投影】NanoVG 没有模糊（那是 Skia 的东西），草稿用的是 CSS drop-shadow 的软边。
 * 这个取舍留到能像素对照之后再定，不靠猜。
 */
public final class NvgCardPainter {

    private NvgCardPainter() {
    }

    /**
     * 画一张完整的卡面外壳。
     *
     * @param vg      NanoVG 上下文
     * @param style   设计参数（由 tokens.css 编译出来的那一份）
     * @param x       卡片左缘（逻辑坐标）
     * @param y       卡片顶边
     * @param cardW   卡片总宽
     * @param cardH   卡片总高
     * @param accent  稀有度强调色（ARGB）
     * @param barFill   竖条高度比例 0~1（入场动画用）
     * @param bodyShift 两个框整体的横向偏移（入场时内容从竖条后面滑出来用）。竖条自己不动 ——
     *                  它是"洞口"，内容从它后面走，所以只有内容框偏移。
     * @param window    隧道口：内容能被看见的那一段。**偏移必须有它配套**，只有偏移没有裁剪，
     *                  两个框就会从竖条前面滑过去 —— 真机上看就是"卡片穿透竖条"。
     *                  这条几何的算式在 {@code shared} 的 {@link RevealWindow}，有单测钉着。
     */
    public static void paintCard(long vg, StyleModel style, float x, float y, float cardW, float cardH,
                                 int accent, float barFill, float bodyShift, RevealWindow window) {
        float gap = style.gap();
        float barW = style.barWidth();
        float bodyX = barW + gap;
        float radius = Math.min(style.cornerRadius(), cardH / 2f);
        float iconW = cardH;
        float infoX = x + bodyX + iconW + gap;
        float infoW = Math.max(0f, x + cardW - infoX);
        boolean useHighlight = (style.highlight() >>> 24) != 0;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            NVGColor top = color(stack, style.fillTop());
            NVGColor bottom = color(stack, style.fillBottom());
            NVGColor border = color(stack, style.border());
            NVGColor highlight = color(stack, style.highlight());
            NVGColor accentColor = color(stack, accent);

            // 【两个内容框必须在窗口里画】偏移让框往左走，窗口把左边多出来的那截切掉，
            // 合起来才是草稿的"从隧道口冒出来"。少了窗口，框会连竖条一起盖过去。
            // 用 save/restore 包住：scissor 是 NanoVG 的状态，留着会影响后面每一张卡。
            nvgSave(vg);
            nvgScissor(vg, x + window.left(), y, window.width(), cardH);
            if (window.width() > 0.01f) {
                box(vg, stack, top, bottom, border, highlight, useHighlight,
                        x + bodyX + bodyShift, y, iconW, cardH, radius);
                if (infoW > 0f) {
                    box(vg, stack, top, bottom, border, highlight, useHighlight,
                            infoX + bodyShift, y, infoW, cardH, radius);
                }
            }
            nvgRestore(vg);

            // 竖条在窗口左边，不受窗口影响，单独画
            float inset = style.barInsetY();
            float full = Math.max(0f, cardH - inset * 2f);
            float barH = full * Math.max(0f, Math.min(1f, barFill));
            if (barH > 0.01f) {
                nvgBeginPath(vg);
                // 与 SDF 那条同一个规则：从**上头**往下长（见 TrioCardPainter.barShapes 的注释）
                nvgRoundedRect(vg, x, y + inset, barW, barH,
                        Math.min(radius, barW / 2f));
                nvgFillColor(vg, accentColor);
                nvgFill(vg);
            }
        }
    }

    /** 一个框：渐变底 -> 顶部 1px 高光 -> 内缩 1px 描边（顺序与 CSS 叠法一致）。 */
    private static void box(long vg, MemoryStack stack, NVGColor top, NVGColor bottom, NVGColor border,
                            NVGColor highlight, boolean useHighlight,
                            float x, float y, float w, float h, float radius) {
        if (w <= 0f || h <= 0f) {
            return;
        }
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, w, h, radius);
        NVGPaint paint = nvgLinearGradient(vg, x, y, x, y + h, top, bottom, NVGPaint.mallocStack(stack));
        nvgFillPaint(vg, paint);
        nvgFill(vg);

        if (useHighlight) {
            // CSS: box-shadow: inset 0 1px 0 <highlight> —— 贴着顶边的 1px 高光
            nvgBeginPath(vg);
            nvgRoundedRect(vg, x + 0.5f, y + 0.5f, Math.max(0f, w - 1f), 1f, 0.5f);
            nvgFillColor(vg, highlight);
            nvgFill(vg);
        }

        nvgBeginPath(vg);
        nvgRoundedRect(vg, x + 0.5f, y + 0.5f, Math.max(0f, w - 1f), Math.max(0f, h - 1f), radius);
        nvgStrokeWidth(vg, 1f);
        nvgStrokeColor(vg, border);
        nvgStroke(vg);
    }

    /** 主题里的 ARGB -> NanoVG 要的 RGBA 分量。 */
    private static NVGColor color(MemoryStack stack, int argb) {
        return nvgRGBA((byte) (argb >> 16), (byte) (argb >> 8), (byte) argb, (byte) (argb >>> 24),
                NVGColor.mallocStack(stack));
    }
}
