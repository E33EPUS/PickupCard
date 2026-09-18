package com.niuqu.pickupcard.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("三张名单的加/删：校验、去重、上限、不可变性")
class RuleListEditTest {

    @Test
    @DisplayName("三种写法都收：物品 id / #tag / @modid")
    void acceptsAllThreeForms() {
        List<String> rules = List.of();
        for (String raw : List.of("minecraft:cobblestone", "#forge:ores", "@botania")) {
            RuleListEdit.Result r = RuleListEdit.add(rules, raw);
            assertTrue(r.ok(), raw + " 应该被接受");
            assertEquals(List.of(raw), r.rules());
        }
    }

    @Test
    @DisplayName("前后空白被吃掉，存的是干净的字符串")
    void trims() {
        RuleListEdit.Result r = RuleListEdit.add(List.of(), "  minecraft:stone \n");
        assertTrue(r.ok());
        assertEquals(List.of("minecraft:stone"), r.rules());
    }

    @Test
    @DisplayName("空字符串不算一条规则")
    void blankRejected() {
        assertEquals(RuleListEdit.Reject.BLANK, RuleListEdit.add(List.of(), "").reject());
        assertEquals(RuleListEdit.Reject.BLANK, RuleListEdit.add(List.of(), "   ").reject());
        assertEquals(RuleListEdit.Reject.BLANK, RuleListEdit.add(List.of(), null).reject());
    }

    @Test
    @DisplayName("缺命名空间的物品 id 被拒 —— 不悄悄补 minecraft: 前缀")
    void bareItemRejected() {
        RuleListEdit.Result r = RuleListEdit.add(List.of(), "cobblestone");
        assertEquals(RuleListEdit.Reject.BAD_SYNTAX, r.reject());
        assertFalse(r.ok());
        assertEquals(List.of(), r.rules(), "被拒时名单原样不动");
    }

    @Test
    @DisplayName("重复（忽略大小写）被拒")
    void duplicateRejected() {
        List<String> rules = List.of("minecraft:Stone");
        assertEquals(RuleListEdit.Reject.DUPLICATE,
                RuleListEdit.add(rules, "minecraft:stone").reject());
    }

    @Test
    @DisplayName("超长被拒")
    void tooLongRejected() {
        String longId = "minecraft:" + "a".repeat(RuleListEdit.MAX_RULE_LENGTH);
        assertEquals(RuleListEdit.Reject.TOO_LONG, RuleListEdit.add(List.of(), longId).reject());
    }

    @Test
    @DisplayName("满了一张名单就不再收")
    void fullRejected() {
        List<String> rules = new ArrayList<>();
        for (int i = 0; i < RuleListEdit.MAX_RULES; i++) {
            rules.add("minecraft:item" + i);
        }
        assertEquals(RuleListEdit.Reject.FULL,
                RuleListEdit.add(rules, "minecraft:onemore").reject());
    }

    @Test
    @DisplayName("编辑不修改传进来的那份列表")
    void doesNotMutateInput() {
        List<String> rules = new ArrayList<>(List.of("minecraft:stone"));
        RuleListEdit.add(rules, "minecraft:dirt");
        assertEquals(1, rules.size(), "原列表不该被就地改掉");
        RuleListEdit.remove(rules, 0);
        assertEquals(1, rules.size());
    }

    @Test
    @DisplayName("删：正常删掉第 index 条；越界原样返回")
    void remove() {
        List<String> rules = List.of("minecraft:a", "minecraft:b", "minecraft:c");
        assertEquals(List.of("minecraft:a", "minecraft:c"), RuleListEdit.remove(rules, 1));
        assertEquals(rules, RuleListEdit.remove(rules, 3));
        assertEquals(rules, RuleListEdit.remove(rules, -1));
        assertEquals(rules, RuleListEdit.remove(rules, 99));
    }

    @Test
    @DisplayName("每个拒绝原因都有一句人话，NONE 是空的")
    void messages() {
        assertEquals("", RuleListEdit.message(RuleListEdit.Reject.NONE));
        for (RuleListEdit.Reject r : RuleListEdit.Reject.values()) {
            if (r != RuleListEdit.Reject.NONE) {
                assertFalse(RuleListEdit.message(r).isBlank(), r + " 应该有说明");
            }
        }
    }
}
