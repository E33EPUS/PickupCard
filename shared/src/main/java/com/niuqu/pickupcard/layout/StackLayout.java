package com.niuqu.pickupcard.layout;

import java.util.ArrayList;
import java.util.List;

/**
 * 卡片竖排的纯几何：锚在<b>准星右下</b>（用户可拖拽），新卡永远从锚点出现、旧的被挤下去。
 * <p>
 * 【为什么单独成一个类】排布是"几张卡、多大、屏幕多大"进去、"每张卡在哪"出来的纯函数，
 * 不认识 {@code GuiGraphics}、不认识字体、不认识 Forge。放在 shared 里的好处是它能在
 * 没有游戏的情况下跑单测 —— 上一版把这段数学写在 436 行的渲染器里，只能靠真机截图
 * 才能发现"卡叠在一起了"。
 * <p>
 * 【为什么顶锚（2026-09-18 定案，第二次回到它】新卡出现在屏幕上<b>固定的一点</b>：不管堆里
 * 已经有几张，眼睛不用满屏找"这次卡在哪儿"。底锚版（新卡贴底、旧的往上顶）输在：最新那张
 * 离视线最远；顶锚版 Newest 在锚点、旧的往下走，与大多数游戏的拾取信息流同向。
 * <p>
 * 【顶锚会抖的旧账怎么清】2026-09-17 记过一笔："顶锚那版新卡把所有人往下推一格，
 * 退役那张走掉又整堆弹回来"。两半都有了解法：往下推由 {@code CardMove} 的 340ms 过渡
 * 平滑掉；弹回来一半本来就不发生 —— 到点退场的通常是最老的那张，它在顶锚下站在<b>堆底</b>，
 * 走掉不动任何人；中途被摘的（放不下丢最老也在堆底）同理。残余场景（中间那张被合并救活/
 * 同 key 重挂）是离散事件，一次 340ms 的滑动读起来是"队伍补位"，不是抖。
 * <p>
 * 【坐标系】GUI 缩放之后的像素，原点在屏幕左上角，y 向下。这跟 {@code GuiGraphics}
 * 的绘制坐标系一致 —— 调用方拿到就能直接画，不需要再换算。
 */
public final class StackLayout {

    /** 一张卡的尺寸请求。 */
    public record Size(float width, float height) {
    }

    /** 一张卡算出来的位置。{@code index} 指回调用方传进来的序号。 */
    public record Slot(int index, float x, float y, float width, float height) {
    }

    private StackLayout() {
    }

    /**
     * 顶锚、向下生长（最新的一张贴着锚点，旧的依次往下）。
     * <p>
     * 【水平只有一条公式】{@code x = clamp(锚点左缘, 0, 放得下的最右位置)}：
     * 锚点是竖条左缘"想停"的地方，放不下就往左让 —— <b>配置表达的是意图，不是绝对坐标</b>，
     * 内容再长也不会被挤出屏幕右缘。
     *
     * @param sizes     每张卡的尺寸，<b>第 0 张是最新的</b>（贴着锚点）。这个顺序跟
     *                  {@code CardStage} 传进来的顺序一致（live 是老到新，调用方反转过），别再反过来。
     * @param guiWidth  当前 GUI 逻辑宽度
     * @param guiHeight 当前 GUI 逻辑高度
     * @param layout    锚点与行为设置
     * @param marginX   卡片右缘距屏幕右边的安全留白
     * @param marginY   距屏幕<b>下边</b>的留白（HUD 带）；锚点被拖得太低时用它夹出一张卡的位置
     * @param gap       卡与卡之间的间隙
     * @return 与 {@code sizes} 同序的位置列表
     */
    public static List<Slot> stack(List<Size> sizes, float guiWidth, float guiHeight,
                                   LayoutSettings layout, int marginX, int marginY, float gap) {
        int n = sizes.size();
        List<Slot> slots = new ArrayList<>(n);
        if (n == 0) return slots;

        float left = layout.anchorLeft(guiWidth);
        // 锚点顶边就是第 0 张的顶边；之后每张都比前一张低一个"卡高 + 间隙"。
        // 从锚点往下数不需要预知总高，一张一张加就行。
        float y = layout.anchorTop(guiHeight, sizes.get(0).height(), marginY);
        for (int i = 0; i < n; i++) {
            Size size = sizes.get(i);
            // 放得下的最右位置；再夹进屏幕，避免超宽卡算出负坐标
            float maxLeft = guiWidth - marginX - size.width();
            // 【两种对齐一条线】左缘锚定：竖条左缘贴锚线（HTML placeX 的 anchor 档）；
            // 右缘对齐：卡右缘贴锚线，左缘随卡宽参差（HTML placeX 的 rightalign 档）。
            float x;
            if (layout.align() == LayoutSettings.Side.RIGHT) {
                x = Math.max(0f, Math.min(left - size.width(), maxLeft));
            } else {
                x = Math.max(0f, Math.min(left, maxLeft));
            }
            slots.add(new Slot(i, x, y, size.width(), size.height()));
            y += size.height() + gap;
        }
        return slots;
    }

    /** 这一摞卡的整体高度（含间隙）。屏幕放不下时调用方拿它做取舍。 */
    public static float totalHeight(List<Size> sizes, float gap) {
        if (sizes.isEmpty()) return 0f;
        float total = gap * (sizes.size() - 1);
        for (Size size : sizes) total += size.height();
        return total;
    }

    /**
     * 锚点以下、HUD 带以上能<b>完整</b>放下几张卡。
     * <p>
     * 【为什么取舍必须有人做】{@link #stack} 只做加法：{@code y} 会一路加过 HUD 带、加过屏幕底。
     * 用户报过「高缩放下会超出屏幕」—— 这个数只有一处能算对，所以单独成函数、带单测。
     *
     * @param anchorTop  锚点纵坐标（卡堆从这里开始向下）
     * @param guiHeight  画布高
     * @param marginY    距屏幕<b>下边</b>的留白（HUD 带）
     * @param cardHeight 一张卡的高（同屏的卡等高）
     * @param gap        卡与卡之间的间隙
     * @return 能完整放下的张数；0 = 连一张都放不下
     */
    public static int fittingCount(float anchorTop, float guiHeight, int marginY,
                                   float cardHeight, float gap) {
        if (cardHeight <= 0f) {
            return Integer.MAX_VALUE;
        }
        float room = guiHeight - marginY - anchorTop;
        if (room < cardHeight) {
            return 0;
        }
        return (int) ((room + gap) / (cardHeight + gap));
    }
}
