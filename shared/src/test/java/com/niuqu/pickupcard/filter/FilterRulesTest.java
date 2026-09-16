package com.niuqu.pickupcard.filter;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 过滤优先级的全部钉子：白名单压黑名单、内置表可关、静音正交、坏规则跳过。
 * 这些规则一旦在游戏里才发现错，玩家看到的是"我明明加了白名单为什么不弹"——
 * 那种问题从截图里看不出来，只能在这里拦。
 */
class FilterRulesTest {

    private static FilterSubject subject(String id, String mod, String... tags) {
        return new FilterSubject(id, mod, Set.of(tags));
    }

    private static FilterSettings settings(List<String> black, List<String> white,
                                           List<String> mute, boolean builtin) {
        return new FilterSettings(black, white, mute, builtin);
    }

    // ---- 解析 ----

    @Test
    void parseAcceptsTheThreeRuleKinds() {
        assertNotNull(FilterRule.parse("minecraft:cobblestone"));
        assertNotNull(FilterRule.parse("#forge:ores"));
        assertNotNull(FilterRule.parse("@somebotania"));
        assertNotNull(FilterRule.parse("  #forge:ores  "));
    }

    @Test
    void parseRejectsBrokenRules() {
        assertNull(FilterRule.parse(null));
        assertNull(FilterRule.parse(""));
        assertNull(FilterRule.parse("   "));
        assertNull(FilterRule.parse("#"));
        assertNull(FilterRule.parse("@"));
        // 不带命名空间的物品 id 拒绝：宁可让玩家写全，也不悄悄猜
        assertNull(FilterRule.parse("cobblestone"));
    }

    @Test
    void ruleMatchingRespectsKind() {
        var cobble = subject("minecraft:cobblestone", "minecraft", "forge:cobblestones");
        assertTrue(FilterRule.parse("minecraft:cobblestone").matches(cobble));
        assertTrue(FilterRule.parse("#forge:cobblestones").matches(cobble));
        assertTrue(FilterRule.parse("@minecraft").matches(cobble));
        assertFalse(FilterRule.parse("#forge:ores").matches(cobble));
        assertFalse(FilterRule.parse("minecraft:stone").matches(cobble));
    }

    // ---- 优先级 ----

    @Test
    void whitelistBeatsBlacklist() {
        var decision = FilterRules.check(
                subject("minecraft:iron_ingot", "minecraft"),
                settings(List.of("minecraft:iron_ingot"), List.of("minecraft:iron_ingot"),
                        List.of(), true));
        assertTrue(decision.show());
        assertTrue(decision.emphasized());
    }

    @Test
    void blacklistDrops() {
        var decision = FilterRules.check(
                subject("minecraft:dirt", "minecraft"),
                settings(List.of("minecraft:dirt"), List.of(), List.of(), false));
        assertFalse(decision.show());
    }

    @Test
    void builtinIgnoreDropsOnlyWhenEnabled() {
        var dirt = subject("minecraft:dirt", "minecraft");
        assertTrue(FilterRules.check(dirt, settings(List.of(), List.of(), List.of(), true)).show() == false);
        assertTrue(FilterRules.check(dirt, settings(List.of(), List.of(), List.of(), false)).show());
        // 内置表不该殃及无辜：钻石不在表里
        var diamond = subject("minecraft:diamond", "minecraft");
        assertTrue(FilterRules.check(diamond, settings(List.of(), List.of(), List.of(), true)).show());
    }

    @Test
    void whitelistBeatsBuiltinIgnore() {
        var decision = FilterRules.check(
                subject("minecraft:dirt", "minecraft"),
                settings(List.of(), List.of("minecraft:dirt"), List.of(), true));
        assertTrue(decision.show());
        assertTrue(decision.emphasized());
    }

    @Test
    void muteListFlagsWithoutDropping() {
        var decision = FilterRules.check(
                subject("minecraft:iron_ingot", "minecraft"),
                settings(List.of(), List.of(), List.of("minecraft:iron_ingot"), true));
        assertTrue(decision.show());
        assertFalse(decision.emphasized());
        assertTrue(decision.muted());
    }

    @Test
    void muteAndWhitelistAreOrthogonal() {
        var decision = FilterRules.check(
                subject("minecraft:beacon", "minecraft"),
                settings(List.of(), List.of("minecraft:beacon"), List.of("minecraft:beacon"), true));
        assertTrue(decision.show());
        assertTrue(decision.emphasized());
        assertTrue(decision.muted());
    }

    @Test
    void tagRuleHitsEverythingWithTag() {
        var decision = FilterRules.check(
                subject("somebotania:mana_diamond", "somebotania", "forge:gems", "c:gems"),
                settings(List.of("#forge:gems"), List.of(), List.of(), false));
        assertFalse(decision.show());
    }

    @Test
    void brokenRulesAreSkippedNotFatal() {
        var decision = FilterRules.check(
                subject("minecraft:dirt", "minecraft"),
                settings(List.of("dirt", "#", "@", "!!", "minecraft:dirt"), List.of(), List.of(), false));
        assertFalse(decision.show());
    }

    @Test
    void unfilteredSubjectPassesPlain() {
        assertEquals(FilterRules.Decision.PLAIN,
                FilterRules.check(subject("minecraft:diamond", "minecraft"),
                        FilterSettings.defaults()));
    }
}
