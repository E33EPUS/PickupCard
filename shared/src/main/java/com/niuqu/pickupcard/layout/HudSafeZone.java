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
 * 副手槽 29×24（有副手时）      <b>跟主手相反的一侧</b>    H-23 .. H+1            Gui#renderHotbar
 * 攻击指示器 18×18（快捷栏档）  见 hotbarRightHalf()   H-20 .. H-2            Gui#renderHotbar
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
 * <p>【"物品栏和屏幕右侧之间那块空白"到底有多大】见 {@link #stripLeft} —— 它<b>跟主手有关</b>，
 * 而这正是 2026-09-18 之前算错的地方（把左撇子的几何当成了所有人的）。
 */
public final class HudSafeZone {

    // ---- 原版 HUD 的常量（逻辑像素）----
    /** 快捷栏/血量那一带向两侧各伸多少：182/2。 */
    public static final int HOTBAR_HALF = 91;
    /** 快捷栏底行（含选中框）距屏幕底多少像素。 */
    public static final int HOTBAR_TOP = 23;
    /** 副手槽宽：W/2+91 起、29 宽。它在<b>哪一侧</b>见 {@link #hotbarRightHalf}。 */
    public static final int OFFHAND_W = 29;
    /** 副手槽右缘（<b>仅当副手在右侧</b>时成立）：W/2 + 91 + 29。 */
    public static final int OFFHAND_RIGHT = HOTBAR_HALF + OFFHAND_W;
    /**
     * 攻击指示器（快捷栏档）的宽度与它距快捷栏右缘的距离：画在 {@code W/2+91+6} 起、18 宽。
     * <p>它<b>只在充能时出现</b>（挥击后那不到一秒），而且只占快捷栏那一带。
     */
    public static final int ATTACK_INDICATOR_W = 18;
    public static final int ATTACK_INDICATOR_GAP = 6;
    /**
     * 手持物品名的行：pose 在 {@code H-59}，文字画在 {@code -4} 处 → 占 H-63 .. H-54。
     * 居中，宽度随物品名变化 —— 所以它会横伸进右列。
     */
    public static final int HELD_NAME_TOP = 63;
    /**
     * <b>动作栏提示语</b>（{@code Gui#renderOverlayMessage}）：pose 在 {@code H-68}，
     * 文字画在 {@code -4} 处、背后还有一条半透明底 → 整块占 <b>H-73 .. H-62</b>。
     * 它也居中、宽度不定（"按 F3 打开调试"能有一两百像素宽）。
     * <p>【为什么要单独记住它】它是那一带<b>最高</b>的一块 —— 卡堆只要让开它，
     * 就等于让开了下面所有东西。上一版只让到 H-62（照手持物品名算的），还是会压它。
     */
    public static final int OVERLAY_TOP = 73;
    /** 状态效果图标：一行 26 高、每列 25 宽。 */
    public static final int EFFECT_ROW_H = 26;
    public static final int EFFECT_COL_W = 25;
    /** 留白：卡与 HUD / 屏幕边之间至少这么多像素。 */
    public static final int PAD = 2;

    /**
     * 快捷栏那一带<b>向右</b>伸到哪儿（距屏幕中心的半宽）。
     * <p>
     * 【为什么跟主手有关，而且必须问】副手槽画在<b>主手的反侧</b> —— 原版
     * {@code Gui#renderHotbar} 第一句就是 {@code HumanoidArm arm = player.getMainArm().getOpposite()}，
     * 然后 {@code if (arm == LEFT) blit(cx - 91 - 29, …) else blit(cx + 91, …)}。
     * 也就是说：<b>右手玩家（默认）副手在左边</b>，只有左撇子玩家才会伸到右边来。
     * <p>
     * 【这条曾经是错的，而它正是"卡片放不进右侧条带"的来源】2026-09-18 之前这里写着
     * "副手槽 W/2+91 .. W/2+120"，那是<b>左撇子</b>的几何，却被当成了所有人的。
     * 于是 427 宽的画布上右侧条带被算成 77.5px（真实 105.5px，宽了 36%），
     * 结论一度成了"那条缝放不下一张卡，所以整列放到 HUD 带上方去" —— 用户为此报了三次。
     * <p>
     * 攻击指示器（快捷栏档）画在 {@code cx+91+6} 起、18 宽，但<b>只在快捷栏那一带</b>；
     * 卡片按 {@link #bottomInset()} 压根不进那一带，所以它不用单独让。
     */
    public static int hotbarRightHalf(boolean leftHanded) {
        return leftHanded ? OFFHAND_RIGHT : HOTBAR_HALF + 1;
    }

    /**
     * 右侧条带的左缘：<b>卡片左缘不许小于它</b>
     * （用户 2026-09-18 的原话：「只能在物品栏和屏幕右侧之间，不管再右都不要左」）。
     */
    public static float stripLeft(float guiWidth, boolean leftHanded) {
        return guiWidth / 2f + hotbarRightHalf(leftHanded);
    }

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
     * 卡堆底部必须留出的空白（距屏幕底的像素）= <b>动作栏提示语那一块之上再留 2px</b>。
     * <p>它是底部那一带最高的居中块（H-73），让开它就等于让开了手持物品名（H-63）、
     * 护甲（H-50）、血量（H-40）、经验（H-29）、快捷栏（H-23）—— 整条带一次让完。
     */
    public static int bottomInset() {
        return OVERLAY_TOP + PAD;
    }

    /**
     * 原版底部那一带的所有矩形（快捷栏 / 经验 / 血量 / 护甲 / 两块瞬时居中文字）。
     * <p>【给谁用】单测拿它一条条断言"不相交"；诊断日志也拿它解释"这次为什么让了这么多"。
     */
    public static List<Rect> bottomBands(float guiWidth, float guiHeight,
                                        float overlayWidth, float heldNameWidth) {
        float cx = guiWidth / 2f;
        List<Rect> rects = new ArrayList<>();
        rects.add(fromBottom(guiHeight, cx, HOTBAR_HALF + 1, HOTBAR_TOP, 24f));      // 快捷栏 + 选中框
        rects.add(fromBottom(guiHeight, cx, HOTBAR_HALF, 29, 5f));                   // 经验条
        rects.add(fromBottom(guiHeight, cx, Math.max(6f, 24f), 36, 9f));             // 等级数字（居中）
        rects.add(fromBottom(guiHeight, cx, HOTBAR_HALF, 40, 10f));                  // 血量/食物
        rects.add(fromBottom(guiHeight, cx, HOTBAR_HALF, 50, 10f));                  // 护甲/气泡
        rects.add(fromBottom(guiHeight, cx, Math.max(2f, heldNameWidth / 2f), HELD_NAME_TOP, 10f));
        rects.add(fromBottom(guiHeight, cx, Math.max(2f, overlayWidth / 2f + 2f), OVERLAY_TOP, 11f));
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
     * 右侧条带这一帧的几何：<b>卡堆只准待在这个矩形里</b>。
     *
     * @param left        条带左缘 = {@link #stripLeft}（快捷栏那一带的右缘）
     * @param right       条带右缘 = 画布宽 − 右边距 − 右侧要让开的东西
     * @param width       {@code right − left}；<b>负数代表这条带装不下任何卡</b>
     * @param bottomInset 底部留白 = {@link #STRIP_BOTTOM}（<b>不是</b> {@link #bottomInset()}）
     */
    public record Strip(float left, float right, float width, float bottomInset) {

        /** 条带里能不能放下一张给定宽度的卡。 */
        public boolean fits(float cardWidth) {
            return cardWidth > 0f && cardWidth <= width;
        }

        /** 这一摞卡在条带里的矩形（贴右下；给单测与诊断用）。 */
        public Rect cards(float cardWidth, float guiHeight, float stackHeight) {
            return new Rect(right - cardWidth, guiHeight - bottomInset - stackHeight,
                    cardWidth, stackHeight);
        }
    }

    /**
     * 条带里的底部留白。<b>只有 2px</b> —— 卡片可以一路下到屏幕底、跟快捷栏并排。
     * <p>
     * 【为什么这里不用 {@link #bottomInset()}（75）】那 75px 是为了"让开底部那一整条居中 HUD 带"
     * 而算的，而它成立的前提是卡片<b>横跨在那一带上方</b>。进了条带之后卡片整体在快捷栏的
     * <b>右边</b>，横向上与快捷栏不相交，让开纵向那一整条就变成白让了 ——
     * 用户从 2026-09-17 起报了四次「为什么还是在物品栏上方」，要的就是"屏幕右侧和物品栏之间
     * 那一块区域"，而那一块是<b>从屏幕底一直到顶</b>的。
     * <p>
     * 【这条选择换来了什么、代价是什么】换来的是卡片真的落在那一块里（最新那张的底边与
     * 快捷栏的底边齐平）。代价要认：底部那一带还有两块<b>居中、宽度不定</b>的原版文字
     * （手持物品名 H-63、动作栏提示语 H-73），够宽的时候右端会伸进条带里。
     * 它们是瞬时文字、而卡片画在它们之上，冲突是"卡片盖住半句提示"，不是"卡片压住快捷栏"。
     * 真机上如果看着碍事，下一步就照 {@code reserve} 那套"碰上了才让"给它们加一条。
     */
    public static final int STRIP_BOTTOM = PAD;

    /**
     * 算出这一帧的右侧条带。
     * <p>
     * 【它取代了什么】从前这里叫 {@code place()}，返回一个"左缘软下限 + 底部留白"，
     * 而它的注释写着"那条缝只有 76 像素、放不下一张卡，所以整列放到 HUD 带上方去" ——
     * 那个 76 是<b>照左撇子的副手位置算出来的</b>（见 {@link #hotbarRightHalf}），
     * 右手玩家实际有 105.5px。用户为此报了三次「为什么还在物品栏上方」。
     * <p>
     * 现在这里只回答"条带在哪儿、多宽"，装不装得下交给调用方按真实的卡宽判断
     * （{@code CardStage} 会先按宽度截名字，实在装不下才回退到 HUD 带上方）。
     *
     * @param marginX      距屏幕右边的留白
     * @param rightReserve 右侧额外要让开多少（侧栏 / 状态效果图标），0 = 不用让
     * @param leftHanded   玩家是不是左撇子（副手在右，条带因此窄 29px）
     */
    public static Strip strip(float guiWidth, float guiHeight, int marginX, float rightReserve,
                              boolean leftHanded) {
        float left = stripLeft(guiWidth, leftHanded);
        float right = guiWidth - marginX - Math.max(0f, rightReserve);
        return new Strip(left, right, right - left, STRIP_BOTTOM);
    }

    /**
     * 右侧这一帧要额外让开多少 —— <b>只有卡堆真的碰上那一块才让</b>。
     * <p>
     * 【为什么不是"有就全让"】侧栏与状态效果图标都是"有时才在、而且在屏幕中部"的东西：
     * 画布 1080 高时卡堆贴着右下角（y≈890..1005），侧栏在 y≈472..607，两者根本碰不上，
     * 却因为一句无条件让位把整列推到快捷栏左边 —— 用户报的「低缩放下位置会变到物品栏左侧」
     * 就是这一条。状态效果图标与侧栏在这里共用一条规则，因为它们本来就是同一类东西。
     *
     * @param cards        卡堆这一帧（未左移时）占的矩形
     * @param sidebar      计分板侧栏的矩形；没有侧栏传 {@code null}
     * @param sidebarWidth 侧栏宽（让位量 = 它 + 5）
     * @param effects      状态效果图标带的矩形；没有效果传 {@code null}
     * @param effectsWidth 图标带的总宽（列数 × {@link #EFFECT_COL_W}）
     */
    public static float reserve(Rect cards, Rect sidebar, float sidebarWidth,
                                Rect effects, float effectsWidth) {
        float reserve = 0f;
        if (sidebar != null && cards.intersects(sidebar)) {
            reserve += sidebarWidth + 5f;
        }
        if (effects != null && cards.intersects(effects)) {
            reserve += effectsWidth;
        }
        return reserve;
    }

    /** 便捷：把一个矩形夹进"右侧留白"之内（左移多少）。 */
    public static float shiftLeft(float left, float cardWidth, float guiWidth, float rightReserve) {
        float overflow = (left + cardWidth) - (guiWidth - PAD - rightReserve);
        return overflow > 0f ? overflow : 0f;
    }
}
