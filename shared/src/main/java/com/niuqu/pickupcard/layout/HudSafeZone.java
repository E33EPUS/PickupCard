package com.niuqu.pickupcard.layout;

import java.util.ArrayList;
import java.util.List;

/**
 * 原版 HUD 占了哪几块，以及<b>卡堆该落在哪</b> —— 全部算出来，不写死留白。
 *
 * <p>【为什么会有这个类】2026-09-17 用户第三次报"卡片和物品栏 HUD 重叠"。前两次都在调那个
 * 魔数（16 → 52），第三次才去读原版源码：<b>漏的不是快捷栏，是"手持物品名"那一行</b>。
 * 所有数字抄自 1.20.1 的 {@code net/minecraft/client/gui/Gui.java}，注释里带行号 ——
 * 下次原版改版，对着行号重看一遍就知道该改哪个数。
 *
 * <pre>
 * 元素                        x 范围                距屏幕底多少像素        出处
 * 快捷栏 182×22               W/2 ± 91              H-22 .. H              Gui#renderHotbar
 * 选中框（高 1px）             W/2 ± 92              H-23 .. H-1            Gui#renderHotbar
 * 副手槽 29×24（有副手时）      W/2+91 .. W/2+120     H-23 .. H+1            Gui#renderHotbar
 * 经验条 182×5                W/2 ± 91              H-29 .. H-24           Gui#renderExperienceBar
 * 经验等级数字（居中）          (W-w)/2 .. (W+w)/2    H-35 .. H-27           Gui#renderExperienceBar
 * 血量/食物（居中一排）         W/2 ± 91              H-39 .. H-30           Gui#renderPlayerHealth
 * 护甲/气泡（居中一排）         W/2 ± 91              H-49 .. H-40           Gui#renderPlayerHealth
 * <b>手持物品名（居中，宽度不定）</b>   (W-w)/2 .. (W+w)/2    H-59 .. H-50           Gui#renderSelectedItemName
 * 状态效果图标 24×24           W-25n .. W            1 .. 25 / 27 .. 51     Gui#renderEffects
 * 计分板侧栏                  W-w-5 .. W           H/2 上下 n*9           Gui#displayScoreboardSidebar
 * </pre>
 *
 * <p>【为什么"手持物品名"是关键】它是<b>居中</b>的、宽度随物品名变化 —— 而卡堆在右列：
 * 只要名字够长就会横伸过来。它的行在 H-59，而旧的底部留白 52 让最下面那张卡正好落在
 * H-52..H-72，与它整行相交。所以底部留白必须让到 H-60 以上（{@link #bottomInset}）。
 *
 * <p>【"物品栏和屏幕右侧之间那块空白"到底有多大】按上表算：右侧那块要避开副手槽（W/2+120）
 * 和侧栏，在 guiScale 3 的画布（426×240）上只剩约 76 逻辑像素宽 —— 放不下一张卡
 * （卡最宽可到 0.45×屏宽 ≈ 192）。所以"最优"不是挤进那条缝，而是<b>把整列放在 HUD 带的上方</b>：
 * 那一带在 y &lt; H-60 之后整屏宽度都空着，卡的宽度可以照常。
 */
public final class HudSafeZone {

    // ---- 原版 HUD 的常量（逻辑像素）----
    /** 快捷栏/血量那一带向两侧各伸多少：182/2。 */
    public static final int HOTBAR_HALF = 91;
    /** 快捷栏底行（含选中框）距屏幕底多少像素。 */
    public static final int HOTBAR_TOP = 23;
    /** 副手槽右缘：W/2 + 91 + 29。 */
    public static final int OFFHAND_RIGHT = HOTBAR_HALF + 29;
    /** 手持物品名的行：距屏幕底 H-59 .. H-50。**卡堆必须整个在它上面。** */
    public static final int HELD_NAME_TOP = 60;
    /** 状态效果图标：一行 26 高、每列 25 宽。 */
    public static final int EFFECT_ROW_H = 26;
    public static final int EFFECT_COL_W = 25;
    /** 留白：卡与 HUD / 屏幕边之间至少这么多像素。 */
    public static final int PAD = 2;

    private HudSafeZone() {
    }

    /** 一块矩形（屏幕绝对坐标，y 向下）。 */
    public record Rect(float x, float y, float w, float h) {
        public boolean intersects(Rect other) {
            return x < other.x + other.w && other.x < x + w
                    && y < other.y + other.h && other.y < y + h;
        }
    }

    /** 把 y 以"距屏幕底多少像素"表示的带子，转成屏幕坐标的矩形。 */
    private static Rect fromBottom(float guiHeight, float centerX, float halfWidth,
                                  int topFromBottom, float height) {
        return new Rect(centerX - halfWidth, guiHeight - topFromBottom,
                halfWidth * 2f, height);
    }

    /**
     * 卡堆底部必须留出的空白（距屏幕底的像素）。
     * <p>取"手持物品名"那一行的顶端再往上 2px —— 它比护甲行更高，也比快捷栏更高，
     * 所以让开它就等于让开下面所有行。
     */
    public static int bottomInset() {
        return HELD_NAME_TOP + PAD;
    }

    /** 原版底部那些居中带（快捷栏 / 经验 / 血量 / 护甲 / 手持物品名）各自的矩形。只给单测与诊断用。 */
    public static List<Rect> bottomBands(float guiWidth, float guiHeight, float heldNameWidth) {
        float cx = guiWidth / 2f;
        List<Rect> rects = new ArrayList<>();
        rects.add(fromBottom(guiHeight, cx, HOTBAR_HALF + 1, HOTBAR_TOP, 24f));      // 快捷栏 + 选中框
        rects.add(fromBottom(guiHeight, cx, HOTBAR_HALF, 29, 5f));                   // 经验条
        rects.add(fromBottom(guiHeight, cx, Math.max(6f, 20f), 36, 9f));             // 等级数字（居中）
        rects.add(fromBottom(guiHeight, cx, HOTBAR_HALF, 40, 10f));                  // 血量/食物
        rects.add(fromBottom(guiHeight, cx, HOTBAR_HALF, 50, 10f));                  // 护甲/气泡
        rects.add(fromBottom(guiHeight, cx, Math.max(2f, heldNameWidth / 2f), HELD_NAME_TOP, 10f));
        return rects;
    }

    /** 状态效果图标区（右上）：有益一排、有害再一排；没有效果就是空的。 */
    public static List<Rect> effectIcons(float guiWidth, int beneficial, int harmful) {
        List<Rect> rects = new ArrayList<>();
        if (beneficial > 0) {
            rects.add(new Rect(guiWidth - EFFECT_COL_W * beneficial, 1f,
                    EFFECT_COL_W * beneficial, 24f));
        }
        if (harmful > 0) {
            rects.add(new Rect(guiWidth - EFFECT_COL_W * harmful, 27f,
                    EFFECT_COL_W * harmful, 24f));
        }
        return rects;
    }

    /** 计分板侧栏（右侧一竖条）；没有就传 0。 */
    public static Rect sidebar(float guiWidth, float guiHeight, float width, int lines) {
        float h = Math.max(9f, lines * 9f + 12f);
        return new Rect(guiWidth - width - 5f, guiHeight / 2f - h / 2f, width + 5f, h);
    }

    /**
     * 卡堆这一帧该落在哪儿。
     *
     * @param guiWidth      逻辑画布宽
     * @param guiHeight     逻辑画布高
     * @param cardWidth     最宽的那张卡
     * @param stackHeight   整摞卡的高度（含间隙）
     * @param rightReserve  右侧要额外让开多少（侧栏宽度 / 效果图标占的宽），0 = 不用让
     */
    public record Placement(float leftMin, float leftMax, float bottomInset, float rightReserve) {

        /** 这一摞卡的实际矩形（给单测与诊断用）。 */
        public Rect cards(float left, float cardWidth, float guiHeight, float stackHeight) {
            return new Rect(left, guiHeight - bottomInset - stackHeight, cardWidth, stackHeight);
        }
    }

    /**
     * 算出一帧的落点。<b>卡堆先按"底部让到 H-60 以上"落，再按需要整体左移让开右侧的东西。</b>
     * <p>为什么不是"挤进快捷栏右边那条缝"：那条缝在常见画布下只有 76 逻辑像素宽
     * （要避开副手槽 W/2+120 和侧栏），放不下一张卡。上面那一带才是真正的空区域 ——
     * 它的宽度是整个屏幕，高度从 H-60 一直上去。
     */
    public static Placement place(float guiWidth, float guiHeight, float cardWidth, float stackHeight,
                                 float rightReserve) {
        float leftMax = Math.max(0f, guiWidth - PAD - Math.max(0f, rightReserve) - cardWidth);
        // 左缘的软下限：竖条成一条竖线（跟着卡宽走），不要越到屏幕外
        float leftMin = Math.min(leftMax, Math.max(0f, guiWidth - 16f - cardWidth));
        return new Placement(leftMin, leftMax, bottomInset(), Math.max(0f, rightReserve));
    }

    /** 便捷：把一个矩形夹进"右侧留白"之内（左移多少）。 */
    public static float shiftLeft(float left, float cardWidth, float guiWidth, float rightReserve) {
        float overflow = (left + cardWidth) - (guiWidth - PAD - rightReserve);
        return overflow > 0f ? overflow : 0f;
    }
}
