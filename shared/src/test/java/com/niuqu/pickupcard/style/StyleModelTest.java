package com.niuqu.pickupcard.style;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 主题解析的钉子：缺键补默认、坏文本不炸、颜色格式宽容。
 * 主题 JSON 是玩家/资源包作者手写的，解析必须"打不坏"。
 */
class StyleModelTest {

    @Test
    void blankFallsBackToDefaults() {
        assertEquals(StyleModel.defaults(), StyleModel.parse(null));
        assertEquals(StyleModel.defaults(), StyleModel.parse("   "));
    }

    @Test
    void brokenJsonFallsBackToDefaults() {
        assertEquals(StyleModel.defaults(), StyleModel.parse("{ not json !!"));
        assertEquals(StyleModel.defaults(), StyleModel.parse("[]"));
    }

    @Test
    void partialOverrideKeepsDefaultsForMissingKeys() {
        StyleModel parsed = StyleModel.parse("{\"geometry\": {\"cornerRadius\": 4}}");
        assertEquals(4, parsed.cornerRadius());
        assertEquals(StyleModel.defaults().paddingH(), parsed.paddingH());
        assertEquals(StyleModel.defaults().enterMs(), parsed.enterMs());
    }

    @Test
    void sixDigitColorGetsFullAlpha() {
        StyleModel parsed = StyleModel.parse("{\"text\": {\"nameColor\": \"#FF0000\"}}");
        assertEquals(0xFFFF0000, parsed.nameColor());
    }

    @Test
    void eightDigitColorIsRRGGBBAA() {
        // 网页习惯 RRGGBBAA：内部统一转 ARGB
        StyleModel parsed = StyleModel.parse("{\"material\": {\"fillTop\": \"#12345680\"}}");
        assertEquals(0x80123456, parsed.fillTop());
    }

    @Test
    void badColorFallsBackToThatKeyDefault() {
        StyleModel parsed = StyleModel.parse("{\"text\": {\"nameColor\": \"#zzz\"}}");
        assertEquals(StyleModel.defaults().nameColor(), parsed.nameColor());
    }

    @Test
    void numbersAreSanitized() {
        StyleModel parsed = StyleModel.parse(
                "{\"geometry\": {\"cornerRadius\": -5, \"iconSize\": 99}, \"material\": {\"glowAlpha\": 999}}");
        assertEquals(0, parsed.cornerRadius());
        assertEquals(64, parsed.iconSize(), "图标边长夹到上限 64");
        assertEquals(255, parsed.glowAlpha());
    }

    @Test
    void switchesCanBeTurnedOff() {
        StyleModel parsed = StyleModel.parse(
                "{\"animation\": {\"enterEnabled\": false, \"bumpEnabled\": false, \"glowPulseEnabled\": false}}");
        assertTrue(!parsed.enterEnabled());
        assertTrue(!parsed.bumpEnabled());
        assertTrue(!parsed.glowPulseEnabled());
    }
}
