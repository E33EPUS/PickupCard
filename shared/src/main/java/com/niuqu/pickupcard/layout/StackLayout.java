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
     * 底部对齐、向上生长（最新的一张贴着底边）。
     * <p>
     * 【水平位置只有一条公式，两种对齐共用】<br>
     * {@code x = min(想要的左边, 放得下的最右位置)}，其中
     * <ul>
     *   <li>右边固定：想要的左边 = 屏宽（"尽量往右推"），结果就是 {@code 屏宽 - 边距 - 卡宽}
     *       —— 右缘天然对齐，内容再长也只是往左伸，永不溢出；</li>
     *   <li>左边尽量固定：想要的左边 = 玩家设的 {@code leftEdge}，放得下就钉住，
     *       放不下就自动往左让 —— <b>配置表达的是意图，不是绝对坐标</b>。</li>
     * </ul>
     * 之所以坚持共用一条公式：默认的右对齐其实就是左对齐的一个特例，
     * 少一条分支就少一处能出 bug 的地方。
     * <p>
     * 副作用要认：左边固定时，同一批卡里窄卡的左边停在 {@code leftEdge}，
     * 宽卡自动左移，于是<b>竖条只在放得下时才成一条直线</b>。
     *
     * @param sizes     每张卡的尺寸，<b>按从老到新排列</b>（第 0 张最老，最后一张最新）
     * @param guiWidth  当前 GUI 逻辑宽度
     * @param guiHeight 当前 GUI 逻辑高度
     * @param layout    水平对齐设置
     * @param marginX   距屏幕左边的安全留白（同时也是右边固定时的右边距）
     * @param marginY   距屏幕上边的留白
     * @param gap       卡与卡之间的间隙
     * @return 与 {@code sizes} 同序的位置列表
     */
    public static List<Slot> stack(List<Size> sizes, float guiWidth,
                                   LayoutSettings layout, int marginX, int marginY, float gap) {
        int n = sizes.size();
        List<Slot> slots = new ArrayList<>(n);
        if (n == 0) return slots;

        float limit = layout.leftLimit(guiWidth);

        float total = gap * (n - 1);
        for (Size size : sizes) total += size.height();

        // 【锚点在上边】草稿（animation.html）把卡从 TOP_Y 起往下排：最新的一张在最上面，
        // 旧的被往下挤。旧版是"贴着底边往上长"，方向正好相反 —— 效果是"新的出现在最下面、
        // 旧的被顶上去"，和草稿完全两回事（这一条用户第一眼就看出来并报回来了）。
        float y = marginY;
        for (int i = 0; i < n; i++) {
            Size size = sizes.get(i);
            // 放得下的最右位置；再夹到屏幕内，避免超宽卡算出负坐标
            float maxLeft = guiWidth - marginX - size.width();
            float x = Math.max(0f, Math.min(limit, maxLeft));
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
}
