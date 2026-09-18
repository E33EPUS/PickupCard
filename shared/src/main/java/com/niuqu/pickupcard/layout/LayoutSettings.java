package com.niuqu.pickupcard.layout;

/**
 * 卡片在屏幕上怎么摆、怎么出现。**行为参数，进 {@code config/pickupcard-client.toml}**，
 * 不进主题 JSON —— 判据是"换一套主题时，应不应该把它一起换掉"：换主题该换的是配色和
 * 质感，不该让卡片突然跳到屏幕另一边（那会被当成 bug）。
 *
 * @param appearMode   卡片出现时怎么展开。{@link Appear#SLIDE} = 内容保持原样从左边平移出来；
 *                     {@link Appear#CLIP} = 内容不动、可见范围从左往右扩大。
 * @param separation   两张卡之间的空隙（像素）。它跟卡内间隙（{@code style.gap}）不是一回事，
 *                     刻意分成两个键：一个是"卡与卡"，一个是"框与框"。
 * @param scalePercent 卡片缩放百分比（100 = 原样）。{@link #AUTO_SCALE} = 自动：放不下就缩小
 *                     （见 {@link #scale}）。它<b>不</b>属于主题：主题管长相，缩放管"塞不塞得下"。
 * @param anchorX      卡堆锚点的横坐标（<b>画布宽度的比例</b> 0~1）＝ 第一张卡<b>竖条左缘</b>的位置。
 *                     {@link #AUTO_ANCHOR} = 自动：让最宽的那张卡右缘正好落在右边距上（竖条成线那条老公式）。
 * @param anchorY      卡堆锚点的纵坐标（<b>画布高度的比例</b> 0~1）＝ 第一张卡（最新）<b>顶边</b>的位置，
 *                     新卡永远出现在这里、旧的被挤下去。{@link #AUTO_ANCHOR} = 自动：准星下方（55% 高）。
 *
 * <p>【为什么锚点是分数而不是像素】画布宽随 GUI 缩放剧烈变化（1280×720 上 guiScale 3 是 426 宽、
 * guiScale 5 只剩 256 宽）。写死绝对坐标的话，换一档缩放锚点就被边界夹住 ——
 * "设了等于没设的值比没有这个值更坏"（本仓库记过的老坑）。分数在所有缩放档下都落在同一个相对位置，
 * 而且配置界面里的拖拽编辑天然算出来的就是分数。
 *
 * <p>【2026-09-18 删掉的两组键】{@code stickTo}（贴左/贴右）与 {@code leftEdge}（绝对像素）——
 * 它们管的事现在全部由 {@code anchorX}/{@code anchorY} 表达：旧 TOML 里的键读进来会被忽略。
 * 同时删掉的还有"快捷栏右侧条带"落点档：卡堆从 2026-09-18 晚起锚在准星下方、向下生长，
 * 不再贴着快捷栏，条带几何（stripLeft/hotbarRightHalf/STRIP_BOTTOM）随之失去意义。
 */
public record LayoutSettings(Appear appearMode, float separation, int scalePercent,
                             float anchorX, float anchorY) {

    /** 卡片间距的默认值（像素）。 */
    public static final float DEFAULT_SEPARATION = 4f;

    /** {@code scalePercent} = 自动：按"这一摞卡塞不塞得进可用高度"决定缩放。 */
    public static final int AUTO_SCALE = 0;
    /** 手动缩放的上下限（百分比）。 */
    public static final int MIN_SCALE_PERCENT = 50;
    public static final int MAX_SCALE_PERCENT = 200;
    /**
     * 自动缩放的<b>下限</b>：再小就看不清名字了 —— 到了这一步应该少显示几张卡
     * （{@code StackLayout#fittingCount} 会接着丢掉放不下的），而不是把卡缩成一条缝。
     */
    public static final int MIN_AUTO_PERCENT = 60;

    /** 锚点的"自动"哨兵：anchorX 跟画布宽算，anchorY 落在准星下方。 */
    public static final float AUTO_ANCHOR = -1f;
    /**
     * anchorY 自动档的落点：画布高的 55%。
     * <p>【为什么是 55%】准星在 50%，"准星的右下角区域"要的是卡片顶边落在准星<b>下方</b>一点
     * 而不是正中压着它；再往下就让出太多屏。可拖拽（配置界面「位置 → 拖拽调整」），
     * 这只是没人拖过时的默认。
     */
    public static final float DEFAULT_ANCHOR_Y = 0.55f;

    /** 卡片出现时的展开方式。 */
    public enum Appear {
        /** 内容保持原样，从左往右平移到最终位置：先看到最右端，再逐渐看到全部。 */
        SLIDE,
        /** 内容位置不动，可见范围从左往右慢慢扩大：先看到最左端。 */
        CLIP
    }

    /** 默认情况下卡片离屏幕左/右边的距离（自动锚点用它推"最宽卡右缘贴右边距"）。 */
    private static final int MARGIN_X = 16;

    /**
     * 卡片内容允许占屏宽的比例 —— 卡宽上限（{@code CardMetrics.maxWidth}）与自动锚点
     * 要预留多少右边空间共用它。两个地方各写一份的话，调了一处另一处就不对。
     * {@code CardMetrics.MAX_WIDTH_RATIO} 现在直接引用这里，不许再有第二份。
     */
    public static final float CONTENT_WIDTH_RATIO = 0.45f;

    /**
     * anchorX 自动档：让**最宽的那张卡**右缘正好落在右边距上。
     * <p>【为什么默认是"自动"而不是一个好看的绝对数】它要跨所有缩放档成立（见类注释），
     * 而这个公式就是原来「竖条左缘锚定」那条老路 —— 一摞卡的竖条因此成一条竖线。
     */
    public static float autoLeftEdge(float guiWidth) {
        float budget = guiWidth * CONTENT_WIDTH_RATIO;
        return Math.max(0f, guiWidth - MARGIN_X - budget);
    }

    /** 默认：锚点自动（准星右下），内容从锚点向右、向下伸。 */
    public static LayoutSettings defaults() {
        return new LayoutSettings(Appear.SLIDE, DEFAULT_SEPARATION, AUTO_SCALE,
                AUTO_ANCHOR, AUTO_ANCHOR);
    }

    /**
     * 这一帧该用多大的缩放（1.0 = 100%）。
     * <p>【自动档怎么算】需要的高度 = 张数 × 卡高 + 间距（全按 100% 算），锚点以下放不下时按比例缩，
     * 下限 {@link #MIN_AUTO_PERCENT}%；装得下就恒为 100% —— <b>空着的屏幕不该把卡撑大</b>。
     *
     * @param available  卡堆可用的高度（锚点以下、HUD 带以上）
     * @param cardHeight 100% 时一张卡的高
     * @param cards      这一帧想放几张
     * @param gap        卡与卡之间的间距（100% 时）
     */
    public float scale(float available, float cardHeight, int cards, float gap) {
        if (scalePercent > 0) {
            return scalePercent / 100f;
        }
        if (cards <= 0 || cardHeight <= 0f || available <= 0f) {
            return 1f;
        }
        float needed = cards * cardHeight + gap * Math.max(0, cards - 1);
        if (needed <= available) {
            return 1f;
        }
        float ratio = available / needed;
        return Math.max(MIN_AUTO_PERCENT / 100f, Math.min(1f, ratio));
    }

    /** 外部来的值一律过一遍：配置文件是玩家可改的，非法值不该变成崩溃或卡片消失。 */
    public LayoutSettings sanitized() {
        return new LayoutSettings(
                appearMode == null ? Appear.SLIDE : appearMode,
                Math.max(0f, Math.min(32f, separation)),
                // 0 = 自动；给了数值就夹进 50..200 —— 300% 会把卡顶出屏幕，10% 没人看得见
                scalePercent == AUTO_SCALE ? AUTO_SCALE
                        : Math.max(MIN_SCALE_PERCENT, Math.min(MAX_SCALE_PERCENT, scalePercent)),
                sanitizeAnchor(anchorX),
                sanitizeAnchor(anchorY));
    }

    /** 锚点只许两种值：自动哨兵，或者 0..1 的比例。别的统统回自动 —— 宁可回默认也不猜。 */
    private static float sanitizeAnchor(float v) {
        if (v == AUTO_ANCHOR) {
            return AUTO_ANCHOR;
        }
        return (v < 0f || v > 1f) ? AUTO_ANCHOR : v;
    }

    /** 这一帧锚点的横坐标（屏幕逻辑 px）＝ 第一张卡竖条左缘的位置。 */
    public float anchorLeft(float guiWidth) {
        return anchorX < 0f ? autoLeftEdge(guiWidth) : anchorX * guiWidth;
    }

    /** 这一帧锚点的纵坐标（屏幕逻辑 px）＝ 第一张卡（最新）顶边的位置。 */
    public float anchorTop(float guiHeight) {
        return anchorY < 0f ? DEFAULT_ANCHOR_Y * guiHeight : anchorY * guiHeight;
    }

    /**
     * 同上，但<b>夹进"至少放得下一张卡"的范围里</b>：锚点被拖得比"HUD 带顶 − 卡高"还低时，
     * 第一张卡自动抬到 HUD 带上方 —— 配置表达的是意图，落点必须让卡真的看得见。
     * 排布（{@code StackLayout#stack}）与"放几张"的取舍（{@code fittingCount}）必须用同一个值。
     */
    public float anchorTop(float guiHeight, float cardHeight, int bottomMargin) {
        return Math.min(anchorTop(guiHeight), Math.max(0f, guiHeight - bottomMargin - cardHeight));
    }
}
