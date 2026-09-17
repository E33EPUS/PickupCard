package com.niuqu.pickupcard.layout;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 卡堆不许碰原版 HUD —— 这条是用户报了<b>三次</b>的那个 bug 的回归测试。
 *
 * <p>【为什么值得单独一个测试类】前两次都在调一个魔数（16 → 52），每次都说"这次好了"，
 * 而每次漏的都是另一块：第一次漏血量/护甲，第二次漏<b>手持物品名</b>（H-59，居中、宽度不定，
 * 会横伸进右列）。魔数改成"从原版 HUD 的矩形算出来"之后，这类漏检才有可能被一次测掉：
 * 这里把常见画布尺寸 × 各种名字宽度都扫一遍，断言卡堆与每一块都不相交。
 */
class HudSafeZoneTest {

    /** 常见画布尺寸（逻辑像素）：guiScale 3/2 的 1280×720 与 1920×1080，再加小画布档。 */
    private static final float[][] CANVASES = {
            {426f, 240f}, {320f, 240f}, {256f, 144f}, {640f, 360f}, {854f, 480f},
    };

    /** 两块瞬时居中文字的宽度：从"一个字符"到"很长的一句提示语"。 */
    private static final float[] TEXT_WIDTHS = {8f, 60f, 160f, 320f};

    @Test
    void cardsNeverTouchAnyVanillaBand() {
        for (float[] canvas : CANVASES) {
            for (float width : TEXT_WIDTHS) {
                assertNoOverlap(canvas[0], canvas[1], width);
            }
        }
    }

    private void assertNoOverlap(float guiWidth, float guiHeight, float textWidth) {
        float cardWidth = guiWidth * 0.45f;                    // 卡宽上限
        float stackHeight = 5 * 20f + 4 * 4f;                  // 5 张卡 + 间隙
        HudSafeZone.Placement place = HudSafeZone.place(guiWidth, guiHeight, cardWidth, stackHeight, 0f);
        // 最靠左的那种摆法（右缘贴右边距）—— 最容易撞到居中那一带
        float left = Math.max(0f, guiWidth - 16f - cardWidth);
        HudSafeZone.Rect cards = place.cards(left, cardWidth, guiHeight, stackHeight);

        for (HudSafeZone.Rect band : HudSafeZone.bottomBands(guiWidth, guiHeight, textWidth, textWidth)) {
            assertFalse(cards.intersects(band),
                    canvasLabel(guiWidth, guiHeight, textWidth) + " 卡堆 " + describe(cards)
                            + " 撞上了原版带 " + describe(band));
        }
    }

    /**
     * 反例对照：旧的底部留白（写死的 52）在那一带<b>必然相交</b>（先撞动作栏提示语、再撞手持物品名）。
     * <p>这就是用户第三次报的那个 bug —— 如果哪天有人把底部留白又改回一个拍脑袋的数，
     * 这条断言会告诉他那个数错在哪。
     */
    @Test
    void theOldHardcodedInsetWouldStillCollide() {
        float guiWidth = 426f;
        float guiHeight = 240f;
        float cardWidth = guiWidth * 0.45f;
        float stackHeight = 5 * 20f + 4 * 4f;
        float left = guiWidth - 16f - cardWidth;
        HudSafeZone.Rect old = new HudSafeZone.Rect(left, guiHeight - 52f - stackHeight,
                cardWidth, stackHeight);
        boolean hit = false;
        for (HudSafeZone.Rect band : HudSafeZone.bottomBands(guiWidth, guiHeight, 180f, 120f)) {
            hit |= old.intersects(band);
        }
        assertTrue(hit, "旧留白 52 应该会撞上手持物品名那一行 —— 不然这条对照就失效了");
    }

    /**
     * 小画布（guiScale 5 → 256×144）上也必须放得下至少 3 张卡。
     * <p>【算式为什么是 (A+gap)/perCard】n 张卡占 {@code 20n + 4(n-1)} —— 最后一张后面没有间隙，
     * 所以可用高度里要多算一条 gap 才除。写错这条会把"刚好放 3 张"算成 2。
     */
    @Test
    void stillFitsThreeCardsOnTheSmallestCanvas() {
        float guiHeight = 144f;
        int inset = HudSafeZone.bottomInset();
        float cardHeight = 20f;
        float gap = 4f;
        float available = guiHeight - inset;
        int fits = (int) ((available + gap) / (cardHeight + gap));
        assertTrue(fits >= 3, "256×144 上只放得下 " + fits + " 张（底部留白 " + inset + "）");
    }

    /** 右侧有侧栏 / 状态效果时，卡堆整体左移，不许压上去。 */
    @Test
    void sidebarAndEffectsPushTheColumnLeft() {
        float guiWidth = 426f;
        float guiHeight = 240f;
        float cardWidth = guiWidth * 0.45f;
        float stackHeight = 5 * 20f + 4 * 4f;
        float sidebarWidth = 90f;
        HudSafeZone.Rect sidebar = HudSafeZone.sidebar(guiWidth, guiHeight, sidebarWidth, 8);
        float left = guiWidth - 16f - cardWidth;
        float shift = HudSafeZone.shiftLeft(left, cardWidth, guiWidth, sidebarWidth + 5f);
        HudSafeZone.Rect cards = new HudSafeZone.Rect(left - shift,
                guiHeight - HudSafeZone.bottomInset() - stackHeight, cardWidth, stackHeight);
        assertFalse(cards.intersects(sidebar), "左移之后还压着侧栏：" + describe(cards));

        for (HudSafeZone.Rect effect : HudSafeZone.effectIcons(guiWidth, 2, 1)) {
            assertFalse(cards.intersects(effect), "左移之后还压着效果图标：" + describe(effect));
        }
    }

    /** 效果图标区只按"有几列"算宽，没有效果时是空的。 */
    @Test
    void effectIconsAreEmptyWithoutEffects() {
        assertEquals(0, HudSafeZone.effectIcons(426f, 0, 0).size());
        List<HudSafeZone.Rect> two = HudSafeZone.effectIcons(426f, 2, 0);
        assertEquals(1, two.size());
        assertEquals(50f, two.get(0).w(), 0.01f, "两列 = 50 宽");
        assertTrue(two.get(0).intersects(new HudSafeZone.Rect(400f, 2f, 10f, 10f)),
                "效果图标在右上，x 从 426-50=376 起");
    }

    private static String canvasLabel(float w, float h, float textWidth) {
        return "[" + (int) w + "×" + (int) h + " 文字宽 " + (int) textWidth + "]";
    }

    private static String describe(HudSafeZone.Rect r) {
        return String.format("(x=%.0f y=%.0f w=%.0f h=%.0f)", r.x(), r.y(), r.w(), r.h());
    }
}
