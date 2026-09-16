package com.niuqu.pickupcard.filter;

import java.util.Set;

/**
 * 一次拾取在过滤器眼里的样子：注册表 id、所属 mod、命中的 tag 集合。
 * <p>
 * 【为什么不是 ItemStack】tag 的查询方式与注册表结构是映射层的事，纯逻辑层不碰；
 * 这里只要三个字符串就能把"黑/白/静音"的全部判定跑完，单测因此不需要启动游戏。
 *
 * @param id     物品注册表 id，形如 {@code minecraft:cobblestone}
 * @param modId  所属 mod 的命名空间，形如 {@code minecraft}
 * @param tags   命中的全部 tag，形如 {@code forge:ores}（不带 # 前缀）
 */
public record FilterSubject(String id, String modId, Set<String> tags) {

    public FilterSubject {
        tags = Set.copyOf(tags);
    }
}
