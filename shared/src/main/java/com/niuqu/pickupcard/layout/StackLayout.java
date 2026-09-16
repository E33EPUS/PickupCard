package com.niuqu.pickupcard.layout;

import java.util.ArrayList;
import java.util.List;

/**
 * 卡片竖排的纯几何：从屏幕右下角往上堆，最新的一张贴着底线。
 * <p>
 * 【为什么单独成一个类】排布是"几张卡、多大、屏幕多大"进去、"每张卡在哪"出来的纯函数，
 * 不认识 {@code GuiGraphics}、不认识字体、不认识 Forge。放在 shared 里的好处是它能在
 * 没有游戏的情况下跑单测 —— 上一版把这段数学写在 436 行的渲染器里，只能靠真机截图
 * 才能发现"卡叠在一起了"。
 * <p>
 * 【为什么新卡贴底】视线落在快捷栏上，"刚捡到的东西"应该出现在离视线最近的位置，
 * 旧的往上推。这同时也让入场动画统一从下方进入，不需要按卡序改方向。
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
     * 右下角锚点、底部对齐、向上生长。
     *
     * @param sizes     每张卡的尺寸，<b>按从老到新排列</b>（第 0 张最老，最后一张最新）
     * @param guiWidth  当前 GUI 逻辑宽度
     * @param guiHeight 当前 GUI 逻辑高度
     * @param marginX   距屏幕右边的留白
     * @param marginY   距屏幕下边的留白
     * @param gap       卡与卡之间的间隙
     * @return 与 {@code sizes} 同序的位置列表
     */
    public static List<Slot> bottomRight(List<Size> sizes, float guiWidth, float guiHeight,
                                        int marginX, int marginY, float gap) {
        int n = sizes.size();
        List<Slot> slots = new ArrayList<>(n);
        if (n == 0) return slots;

        float total = gap * (n - 1);
        for (Size size : sizes) total += size.height();

        float y = guiHeight - marginY - total;
        for (int i = 0; i < n; i++) {
            Size size = sizes.get(i);
            slots.add(new Slot(i, guiWidth - marginX - size.width(), y, size.width(), size.height()));
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
}
