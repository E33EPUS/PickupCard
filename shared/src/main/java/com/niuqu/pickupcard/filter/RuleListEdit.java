package com.niuqu.pickupcard.filter;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 三张名单的编辑动作，纯函数：加一条 / 删一条。
 *
 * <p>【为什么校验要在这里而不是在界面】界面那边只知道"玩家敲了什么"，不知道
 * {@code cobblestone} 比 {@code minecraft:cobblestone} 少了什么。把判定收在这里，
 * 界面就只负责把 {@link Reject} 翻成一句人话，而"什么算合法规则"只由
 * {@link FilterRule#parse} 一处说了算 —— 两边各写一套必然漂移。
 *
 * <p>【为什么被拒不是异常】规则来自玩家手打，打错是常态（见 {@code FilterRule} 的类注释）。
 * 被拒时 {@link Result#rules()} 原样返回，调用方永远拿到一份可用的列表。
 */
public final class RuleListEdit {

    /**
     * 一张名单最多几条。
     * <p>没有上限的话，玩家粘一份大表进来，界面每帧要画的行数、以及每次拾取要跑的解析量
     * 都会跟着涨 —— 而这两件事都发生在渲染/拾取的热路径上。
     */
    public static final int MAX_RULES = 64;

    /** 一条规则最长多少字符。物品 id / tag / modid 都远用不到这么长。 */
    public static final int MAX_RULE_LENGTH = 64;

    /** 为什么没加进去。{@link #NONE} = 加成功了。 */
    public enum Reject {
        NONE,
        BLANK,
        TOO_LONG,
        BAD_SYNTAX,
        DUPLICATE,
        FULL
    }

    /**
     * 一次编辑的结果。
     *
     * @param rules  编辑之后的名单（被拒时就是原来那份）
     * @param reject 拒绝原因
     */
    public record Result(List<String> rules, Reject reject) {
        public Result {
            rules = List.copyOf(rules);
        }

        public boolean ok() {
            return reject == Reject.NONE;
        }
    }

    private RuleListEdit() {
    }

    /** 加一条。前后空白会被吃掉；重复（忽略大小写）算重复。 */
    public static Result add(List<String> rules, String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) return new Result(rules, Reject.BLANK);
        if (s.length() > MAX_RULE_LENGTH) return new Result(rules, Reject.TOO_LONG);
        if (FilterRule.parse(s) == null) return new Result(rules, Reject.BAD_SYNTAX);
        for (String existing : rules) {
            if (existing.equalsIgnoreCase(s)) return new Result(rules, Reject.DUPLICATE);
        }
        if (rules.size() >= MAX_RULES) return new Result(rules, Reject.FULL);
        List<String> out = new ArrayList<>(rules);
        out.add(s);
        return new Result(out, Reject.NONE);
    }

    /** 删第 index 条（越界原样返回）。 */
    public static List<String> remove(List<String> rules, int index) {
        if (index < 0 || index >= rules.size()) return List.copyOf(rules);
        List<String> out = new ArrayList<>(rules);
        out.remove(index);
        return List.copyOf(out);
    }

    /**
     * 拒绝原因翻成一句给玩家看的话（空串 = 没被拒）。
     * <p>shared 碰不到客户端 I18n，走 {@link Component}（与 CardMetrics 的溢出文案同款）；
     * 无语言管理器的测试环境里 getString() 原样返回 key。
     */
    public static String message(Reject reject) {
        return switch (reject) {
            case NONE -> "";
            case BLANK -> Component.translatable("pickupcard.filter.reject.blank").getString();
            case TOO_LONG -> Component.translatable("pickupcard.filter.reject.tooLong", MAX_RULE_LENGTH).getString();
            case BAD_SYNTAX -> Component.translatable("pickupcard.filter.reject.badSyntax").getString();
            case DUPLICATE -> Component.translatable("pickupcard.filter.reject.duplicate").getString();
            case FULL -> Component.translatable("pickupcard.filter.reject.full", MAX_RULES).getString();
        };
    }
}
