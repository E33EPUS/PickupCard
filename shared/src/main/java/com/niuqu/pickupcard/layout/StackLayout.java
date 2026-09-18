package com.niuqu.pickupcard.layout;

import java.util.ArrayList;
import java.util.List;

/**
 * 卡片竖排的纯几何：锚在屏幕<b>右下角</b>，从底线往上堆，最新的一张贴着底线。
 * <p>
 * 【为什么单独成一个类】排布是"几张卡、多大、屏幕多大"进去、"每张卡在哪"出来的纯函数，
 * 不认识 {@code GuiGraphics}、不认识字体、不认识 Forge。放在 shared 里的好处是它能在
 * 没有游戏的情况下跑单测 —— 上一版把这段数学写在 436 行的渲染器里，只能靠真机截图
 * 才能发现"卡叠在一起了"。
 * <p>
 * 【为什么新卡贴底、旧的往上顶】视线落在快捷栏上，"刚捡到的东西"应该出现在离视线最近的
 * 位置。这同时让"入场口"成为屏幕上<b>固定的一点</b>：不管堆里已经有几张，新卡永远从同一条
 * 底线冒出来，眼睛不用满屏找它。
 * <p>
 * 【附带白捡一条：不会再抖】退场那张占的是堆顶<b>上面</b>那一行（它还在 live 里、淡出还没
 * 播完），所以底下的卡全程只往一个方向走。顶锚那版是反的：新卡插在最上面把所有人往下推
 * 一格，等退役那张走掉又整堆弹回来 —— 每次满员都抖一下。
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
     *   <li>右边固定（默认）：想要的左边 = 屏宽（"尽量往右推"），结果就是
     *       {@code 屏宽 - 边距 - 卡宽} —— 右缘天然对齐，内容再长也只是往左伸，永不溢出；</li>
     *   <li>左边尽量固定：想要的左边 = 玩家设的 {@code leftEdge}，放得下就钉住，
     *       放不下就自动往左让 —— <b>配置表达的是意图，不是绝对坐标</b>。</li>
     * </ul>
     * 之所以坚持共用一条公式：默认的右对齐其实就是左对齐的一个特例，少一条分支就少一处
     * 能出 bug 的地方。
     * <p>
     * 副作用要认：左边固定时，同一批卡里窄卡的左边停在 {@code leftEdge}，宽卡自动左移，
     * 于是<b>竖条只在放得下时才成一条直线</b>。
     *
     * @param sizes     每张卡的尺寸，<b>第 0 张是最新的</b>。这个顺序跟草稿
     *                  （{@code animation.html} 里 {@code slots[0]} = 队首）以及
     *                  {@code CardStage} 传进来的顺序一致，别再反过来。
     * @param guiWidth  当前 GUI 逻辑宽度
     * @param guiHeight 当前 GUI 逻辑高度
     * @param layout    水平对齐设置
     * @param marginX   距屏幕右边的安全留白（左边固定时，左边的默认值也取自它）
     * @param marginY   距屏幕<b>下边</b>的留白
     * @param gap       卡与卡之间的间隙
     * @param leftMin   左缘的<b>硬下限</b>：卡片左缘永远不小于它（右侧条带的左缘，见
     *                  {@link HudSafeZone#stripLeft}）。与 {@code layout} 给的"软目标"不同 ——
     *                  软目标会被内容顶歪，这个不会。传 0 = 不限制（配置界面的预览用它）。
     * @return 与 {@code sizes} 同序的位置列表
     */
    public static List<Slot> stack(List<Size> sizes, float guiWidth, float guiHeight,
                                   LayoutSettings layout, int marginX, int marginY, float gap,
                                   float leftMin) {
        int n = sizes.size();
        List<Slot> slots = new ArrayList<>(n);
        if (n == 0) return slots;

        float limit = layout.leftLimit(guiWidth);

        // 【锚点在下边】从底线往上放：第 0 张（最新）的**底边**贴着屏幕下边距，
        // 之后每一张都比前一张再高一个"卡高 + 间隙"。倒过来从下往上数也行，但那样要先知道
        // 总高，而现在这个写法不需要预知有几张。
        float y = guiHeight - marginY;
        for (int i = 0; i < n; i++) {
            Size size = sizes.get(i);
            y -= size.height();
            // 放得下的最右位置；再夹到屏幕内，避免超宽卡算出负坐标
            float maxLeft = guiWidth - marginX - size.width();
            // 【条带装不下时 maxLeft < leftMin】此时 min(limit, maxLeft) 必然 < leftMin，
            // max() 于是取 leftMin —— 卡会向右溢出条带。这不是"没处理"，而是**故意让溢出可见**：
            // 调用方（CardStage）负责先按宽度把缩放收到装得下，真收不下（低于缩放下限）才回退。
            // 悄悄把卡挤回条带左边反而会把"放不下"这件事藏起来。
            float x = Math.max(leftMin, Math.min(limit, maxLeft));
            slots.add(new Slot(i, x, y, size.width(), size.height()));
            y -= gap;
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
     * 画布能<b>完整</b>放下几张卡。
     * <p>
     * 【为什么取舍必须有人做】{@link #stack} 只做减法：{@code y} 会一路减到负数，
     * 于是 guiScale 4 的 320×180（让开 HUD 带之后只剩 105px）上第 5 张的 y = -15 ——
     * 直接画到屏幕外面去。用户报的「高缩放下会超出屏幕」就是它。这个数只有一处能算对，
     * 所以单独成函数、带单测（{@code StackLayoutTest#onlyWhatFitsStaysOnScreen}）。
     *
     * @param guiHeight  画布高
     * @param marginY    距屏幕下边的留白（HUD 带）
     * @param cardHeight 一张卡的高（同屏的卡等高）
     * @param gap        卡与卡之间的间隙
     * @return 能完整放下的张数；0 = 连一张都放不下
     */
    public static int fittingCount(float guiHeight, int marginY, float cardHeight, float gap) {
        if (cardHeight <= 0f) {
            return Integer.MAX_VALUE;
        }
        float room = guiHeight - marginY;
        if (room < cardHeight) {
            return 0;
        }
        return (int) ((room + gap) / (cardHeight + gap));
    }
}
