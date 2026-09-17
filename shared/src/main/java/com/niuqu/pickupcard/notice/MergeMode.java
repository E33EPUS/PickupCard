package com.niuqu.pickupcard.notice;

/**
 * 「什么算同一个物品」—— 合并的粒度。四档，默认 {@link #STRICT}（同名同 NBT）。
 * <p>
 * 【为什么是枚举，而不是"合并窗口（毫秒）"】窗口从前管的是"隔多久还算同一张卡"，而它答错了
 * 问题：超窗口那一刻旧卡往往<b>还在屏幕上</b>（正淡出），于是同一物品要被画成两张卡，而渲染层
 * 按账本的键建档，只能把淡出那张整张换掉（用户 2026-09-17 报的那个 bug）。真正的问题是
 * <b>哪些拾取算同一件东西</b> —— 那是个谓词，不是时间。形状取自同赛道的 loot-journal
 * （它的 MergeMode 就是这个四档谓词）。
 * <p>
 * 【经验卡不受影响】一簇经验球本来就是一回事，四档都合并（见 {@code Inbox#offer}）。
 */
public enum MergeMode {

    /** 同名同 NBT（默认）：改名、附魔、自定义数据都会分成两张卡。 */
    STRICT,

    /** 同名就并、忽略 NBT：附魔书、药水这类"同一物品不同数据"会并进一张卡。 */
    TYPE,

    /** 同名就并，但<b>改过名字</b>的不并 —— 那件是你特意留心的，值得自己一张卡。 */
    TYPE_NAMED,

    /** 从不合并：每次拾取都单开一张卡（一次捡 20 样东西就会看到 20 张）。 */
    NONE;

    public static MergeMode defaults() {
        return STRICT;
    }

    /** 这一档要不要把 NBT 编进身份键里（只有 STRICT 要）。 */
    public boolean usesNbt() {
        return this == STRICT;
    }

    /** 这一档要不要比外观档位（只有 STRICT 要；理由见 {@code ItemIdentity#lookOf}）。 */
    public boolean usesLook() {
        return this == STRICT;
    }

    /**
     * 这一次拾取要不要单独一个身份。
     *
     * @param customNamed 这件物品改过名字吗（{@link #TYPE_NAMED} 下改过名的要自己一张卡）
     */
    public boolean uniquePerPickup(boolean customNamed) {
        return this == NONE || (this == TYPE_NAMED && customNamed);
    }
}
