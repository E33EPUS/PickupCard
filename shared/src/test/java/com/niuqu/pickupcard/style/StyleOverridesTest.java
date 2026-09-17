package com.niuqu.pickupcard.style;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StyleOverridesTest {

    private static final StyleModel THEME = StyleModel.defaults();

    @Test
    void nothingSetKeepsTheThemeExactly() {
        assertEquals(THEME, StyleOverrides.none().apply(THEME));
    }

    /** 只改圆角，其余每个字段都必须分毫不动 —— 这是"换主题不会被个人设置冲掉"的保证。 */
    @Test
    void onlyChangesWhatWasSet() {
        StyleModel out = StyleOverrides.builder().cornerRadius(9).build().apply(THEME);
        assertEquals(9, out.cornerRadius());
        assertEquals(THEME.paddingH(), out.paddingH());
        assertEquals(THEME.iconSize(), out.iconSize());
        assertEquals(THEME.fillTop(), out.fillTop(), "颜色不归它管，必须来自主题");
        assertEquals(THEME.enterMs(), out.enterMs());
    }

    /** 手写 TOML 可以填任意数字：覆盖之后仍然要被夹进合法区间。 */
    @Test
    void outOfRangeValuesAreClamped() {
        StyleModel wild = StyleOverrides.builder().iconSize(9999).barWidth(999).build().apply(THEME);
        assertTrue(wild.iconSize() >= 8 && wild.iconSize() <= 64, "图标边长夹进 8..64，实际 " + wild.iconSize());
        assertTrue(wild.barWidth() >= 1 && wild.barWidth() <= 24, "竖条宽夹进 1..24，实际 " + wild.barWidth());
    }

    @Test
    void builderKeepsFieldsIndependent() {
        StyleOverrides.Builder b = StyleOverrides.builder();
        b.gap(7);
        b.enterMs(1_000L);
        StyleModel out = b.build().apply(THEME);
        assertEquals(7, out.gap());
        assertEquals(1_000L, out.enterMs());
        assertEquals(THEME.paddingH(), out.paddingH());
    }

    /** 颜色覆盖：只动设过的那一项，其余照旧来自主题。 */
    @Test
    void colorsOverrideOneKeyAtATime() {
        StyleModel out = StyleOverrides.builder().fillTop(0x80123456).border(0xFF00FF00).build().apply(THEME);
        assertEquals(0x80123456, out.fillTop());
        assertEquals(0xFF00FF00, out.border());
        assertEquals(THEME.fillBottom(), out.fillBottom(), "没设的那一项必须还是主题的");
        assertEquals(THEME.nameColor(), out.nameColor());
    }

    /** 框粗细也能覆盖（默认 1）；夹逼在 0..4。 */
    @Test
    void borderWidthIsOverridableAndClamped() {
        assertEquals(2, StyleOverrides.builder().borderWidth(2).build().apply(THEME).borderWidth());
        assertEquals(4, StyleOverrides.builder().borderWidth(99).build().apply(THEME).borderWidth());
        assertEquals(0, StyleOverrides.builder().borderWidth(-3).build().apply(THEME).borderWidth());
    }

    /**
     * 玩家手打颜色：三种写法都要认，写坏了必须"当作没设过" ——
     * 一个手滑不该把卡面变成透明，也不该让整个主题回退成默认。
     */
    @Test
    void parsesPlayerTypedColorsAndRejectsGarbage() {
        assertEquals(0x80123456, StyleOverrides.parseArgb("#80123456").orElseThrow());
        assertEquals(0xFF123456, StyleOverrides.parseArgb("#123456").orElseThrow(), "没写 alpha 就是不透明");
        assertEquals(0xFF123456, StyleOverrides.parseArgb("123456").orElseThrow(), "不带 # 也认");
        assertEquals(0x80123456, StyleOverrides.parseArgb("  80123456  ").orElseThrow(), "两边空格不算错");

        assertTrue(StyleOverrides.parseArgb("").isEmpty(), "空 = 没设过");
        assertTrue(StyleOverrides.parseArgb("   ").isEmpty());
        assertTrue(StyleOverrides.parseArgb("#zzz").isEmpty(), "乱写 = 没设过");
        assertTrue(StyleOverrides.parseArgb("#12345").isEmpty(), "位数不对 = 没设过");
        assertTrue(StyleOverrides.parseArgb(null).isEmpty());
    }
}
