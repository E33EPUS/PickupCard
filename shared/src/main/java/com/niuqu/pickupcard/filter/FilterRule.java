package com.niuqu.pickupcard.filter;

import org.jetbrains.annotations.Nullable;

/**
 * 一条过滤规则，三种写法：
 * <ul>
 *   <li>{@code minecraft:cobblestone} —— 按物品 id</li>
 *   <li>{@code #forge:ores} —— 按 tag（命中该 tag 的所有物品）</li>
 *   <li>{@code @somebotania} —— 按整个 mod</li>
 * </ul>
 * 【解析失败返回 null 而不是抛异常】规则来自玩家手写的配置与命令，拼错是常态不是
 * 事故；静默跳过一条坏规则，比让整个 mod 起不来或者每次拾取都抛日志要合适。
 */
public final class FilterRule {

    private enum Kind { ITEM, TAG, MOD }

    private final String raw;
    private final Kind kind;
    private final String value;

    private FilterRule(String raw, Kind kind, String value) {
        this.raw = raw;
        this.kind = kind;
        this.value = value;
    }

    /** 原始写法原样返回，命令的 list 输出要靠它保持玩家输入的样子。 */
    public String raw() {
        return raw;
    }

    @Nullable
    public static FilterRule parse(@Nullable String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) return null;
        if (s.startsWith("#")) {
            String tag = s.substring(1);
            return tag.isEmpty() ? null : new FilterRule(s, Kind.TAG, tag);
        }
        if (s.startsWith("@")) {
            String mod = s.substring(1);
            return mod.isEmpty() ? null : new FilterRule(s, Kind.MOD, mod);
        }
        // 物品 id 必须带命名空间：写 "cobblestone" 的人多半想要 minecraft:cobblestone，
        // 让他明确写全，比悄悄补前缀然后对不上号强。
        return s.indexOf(':') > 0 ? new FilterRule(s, Kind.ITEM, s) : null;
    }

    public boolean matches(FilterSubject subject) {
        return switch (kind) {
            case ITEM -> subject.id().equals(value);
            case TAG -> subject.tags().contains(value);
            case MOD -> subject.modId().equals(value);
        };
    }
}
