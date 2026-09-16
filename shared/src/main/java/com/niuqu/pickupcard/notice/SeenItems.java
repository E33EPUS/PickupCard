package com.niuqu.pickupcard.notice;

import java.util.HashSet;
import java.util.Set;

/**
 * "这件东西我头一次见"的账本。
 * <p>
 * 【刻意不落盘】NEW 角标的语义是"这一局你第一次遇到它"。写进磁盘就变成"这个存档
 * 历史上第一次"，第二天开游戏满屏都没有 NEW、或者换个世界全是 NEW，两种都不对。
 * 内存里记，退出即忘 —— 这是设计决定，不是没做完。
 * <p>
 * 认人的粒度是物品身份键（含 NBT），所以"改名过的钻石剑"和"普通钻石剑"各算一次首次。
 */
public final class SeenItems {

    private final Set<String> seen = new HashSet<>();

    /**
     * 标记并回答：这个东西是不是第一次见。
     *
     * @return true = 本次是首次（调用方据此打 NEW 角标），并且已经记进账本
     */
    public boolean markAndCheckFirst(String key) {
        return seen.add(key);
    }

    /** 只查不问，不改账本。 */
    public boolean hasSeen(String key) {
        return seen.contains(key);
    }

    public int size() {
        return seen.size();
    }

    /** 换世界/退出时清空。 */
    public void clear() {
        seen.clear();
    }
}
