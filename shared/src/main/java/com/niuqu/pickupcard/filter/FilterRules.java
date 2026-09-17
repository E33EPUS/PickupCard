package com.niuqu.pickupcard.filter;

import java.util.List;

/**
 * 过滤判定的全部规则，纯函数：给一个 {@link FilterSubject} 和三表配置，回答这条拾取
 * 的命运。没有 MC、没有配置文件、没有时钟，所以"白名单压过黑名单"这类优先级问题
 * 都被测试钉死，不靠手感。
 *
 * <p>优先级（从上往下短路）：
 * <ol>
 *   <li>白名单 → 永远弹卡 + 强调</li>
 *   <li>黑名单 → 丢弃</li>
 *   <li>内置忽略表（可整体关闭）→ 丢弃</li>
 *   <li>静音名单 → 照常弹卡，但静音</li>
 *   <li>都不中 → 普通弹卡</li>
 * </ol>
 * 白名单与静音名单是正交的：同时在两表里的物品会"强调地静音弹卡"——玩家如果显式
 * 静音了某件白名单物品，那是更晚、更明确的意图，应该赢。
 */
public final class FilterRules {

    /**
     * 内置忽略表：怎么刷屏都不该弹卡的泥土级物品。刻意保持短——宁可让玩家偶尔看到
     * 一张圆石卡，也不要替他静音掉本想看见的东西；嫌多他自己加黑名单就是。
     */
    /**
     * 内置忽略表的只读视图。
     * <p>
     * 【为什么要暴露它】"这条拾取为什么没弹卡"必须能在日志里回答清楚，而回答里要列出
     * 具体是哪些物品 —— 在日志字符串里再抄一份清单就成了第二真源，早晚和这里不一致。
     */
    public static List<String> builtinIgnore() {
        return BUILTIN_IGNORE;
    }

    private static final List<String> BUILTIN_IGNORE = List.of(
            "minecraft:dirt",
            "minecraft:cobblestone",
            "minecraft:cobbled_deepslate",
            "minecraft:gravel",
            "minecraft:sand",
            "minecraft:red_sand",
            "minecraft:netherrack",
            "minecraft:wheat_seeds");

    /**
     * 一条拾取的判定结果。
     *
     * @param show       弹不弹卡
     * @param emphasized 是否强调（白名单命中：描边/发光增强）
     * @param muted      是否静音（静音名单命中：无稀有提示音，原版拾取音也压掉）
     */
    public record Decision(boolean show, boolean emphasized, boolean muted) {
        public static final Decision PLAIN = new Decision(true, false, false);
        public static final Decision DROP = new Decision(false, false, false);
    }

    private FilterRules() {
    }

    public static Decision check(FilterSubject subject, FilterSettings settings) {
        if (anyMatch(subject, settings.whitelist())) {
            return new Decision(true, true, anyMatch(subject, settings.muteList()));
        }
        if (anyMatch(subject, settings.blacklist())) return Decision.DROP;
        if (settings.useBuiltinIgnore() && anyMatch(subject, BUILTIN_IGNORE)) return Decision.DROP;
        if (anyMatch(subject, settings.muteList())) return new Decision(true, false, true);
        return Decision.PLAIN;
    }

    /** 坏规则静默跳过（见 FilterRule.parse），一条拼错的规则不能废掉整张表。 */
    private static boolean anyMatch(FilterSubject subject, List<String> rawRules) {
        for (String raw : rawRules) {
            FilterRule rule = FilterRule.parse(raw);
            if (rule != null && rule.matches(subject)) return true;
        }
        return false;
    }
}
