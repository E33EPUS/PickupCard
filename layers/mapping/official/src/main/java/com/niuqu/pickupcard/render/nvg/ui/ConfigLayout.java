package com.niuqu.pickupcard.render.nvg.ui;

/**
 * 配置界面那三列的几何：<b>纯函数，能离线单测</b>（{@code ConfigLayoutTest}）。
 * <p>
 * 【为什么断点按像素判】画布尺寸本身就是缩放的结果（guiScale 3 的 1280×720 是 427×240，
 * guiScale 5 只剩 256×144）。再去问"现在是哪一档缩放"等于把同一件事记两遍，改一处漏一处。
 * <p>
 * 【退让顺序】先收预览（它最不关键），再收标签列（标签挪到顶上一行），<b>配置项那一列永远在</b> ——
 * 玩家的目的是改配置，为了好看的排布把配置藏起来就本末倒置了。
 */
public record ConfigLayout(boolean tabsOnTop,
                           boolean previewVisible,
                           Rect tabs,
                           Rect items,
                           Rect preview,
                           float tabRow) {

    /** 一块矩形（屏幕逻辑坐标）。 */
    public record Rect(float x, float y, float w, float h) {
        public float right() {
            return x + w;
        }

        public float bottom() {
            return y + h;
        }
    }

    /** 屏幕四边的留白（和标签/配置列之间的缝分开：一个是"贴边"，一个是"列间距"）。 */
    public static final float MARGIN = 8f;
    public static final float GAP = 8f;
    /** 顶部标题两行、底部那行说明各占多少。 */
    public static final float TOP = 30f;
    public static final float BOTTOM = 22f;
    /** 标签列：跟着画布宽一点，但有上下限（太窄装不下"位置与堆叠"，太宽就白占地方）。 */
    public static final float TAB_RATIO = 0.22f;
    public static final float TAB_MIN = 72f;
    public static final float TAB_MAX = 110f;
    /** 标签行高（列排时是行高，顶排时是那一行的高度）。 */
    public static final float TAB_ROW = 18f;
    /** 配置项列：它的宽由内容（一行 label + 一个控件）定，同样有上下限。 */
    public static final float ITEMS_RATIO = 0.38f;
    public static final float ITEMS_MIN = 150f;
    public static final float ITEMS_MAX = 260f;
    /** 预览列的下限：比这窄就干脆收掉（挤成一条的预览比没有更难看）。 */
    public static final float PREVIEW_MIN = 120f;

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /**
     * 按画布尺寸算三列。
     *
     * @param width  逻辑画布宽
     * @param height 逻辑画布高
     */
    public static ConfigLayout compute(float width, float height) {
        float inner = Math.max(1f, width - MARGIN * 2f);
        float top = TOP;
        float bottom = Math.max(top + TAB_ROW, height - BOTTOM);

        float tabW = clamp(inner * TAB_RATIO, TAB_MIN, TAB_MAX);
        // 标签列留着还塞不下配置列 → 标签挪到顶上一行
        boolean tabsOnTop = inner - tabW - GAP < ITEMS_MIN;

        Rect tabs;
        float itemsTop = top;
        if (tabsOnTop) {
            tabs = new Rect(MARGIN, top, inner, TAB_ROW);
            itemsTop = top + TAB_ROW + GAP;
        } else {
            tabs = new Rect(MARGIN, top, tabW, bottom - top);
        }

        float itemsX = tabsOnTop ? MARGIN : MARGIN + tabs.w() + GAP;
        float itemsRoom = tabsOnTop ? inner : inner - tabs.w() - GAP;
        float want = clamp(inner * ITEMS_RATIO, ITEMS_MIN, ITEMS_MAX);
        float rest = itemsRoom - want - GAP;
        boolean previewVisible = rest >= PREVIEW_MIN;

        float itemsW = previewVisible ? want : itemsRoom;
        Rect items = new Rect(itemsX, itemsTop, itemsW, Math.max(0f, bottom - itemsTop));
        Rect preview = previewVisible
                ? new Rect(items.right() + GAP, itemsTop, rest, items.h())
                : new Rect(items.right(), itemsTop, 0f, 0f);
        return new ConfigLayout(tabsOnTop, previewVisible, tabs, items, preview, TAB_ROW);
    }

    /** 标签一行该多宽（顶排时按数量均分；列排时就是列宽）。 */
    public float tabWidth(int count) {
        if (!tabsOnTop || count <= 0) {
            return Math.max(0f, tabs.w() - 4f);
        }
        return Math.max(0f, (tabs.w() - (count - 1) * 4f) / count);
    }

    /** 标签第 index 颗的位置（列排从上往下，顶排从左往右）。 */
    public Rect tabRect(int index, int count) {
        float w = tabWidth(count);
        if (tabsOnTop) {
            return new Rect(tabs.x() + index * (w + 4f), tabs.y(), w, tabRow);
        }
        return new Rect(tabs.x() + 2f, tabs.y() + index * (tabRow + 2f), w, tabRow);
    }
}
