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
}
