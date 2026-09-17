package com.niuqu.pickupcard.pickup;

import net.minecraft.world.item.ItemStack;

/**
 * 一张卡的载荷：要么是物品（带着我们持有的 ItemStack 副本），要么是经验。
 * <p>
 * 【为什么是密封接口而不是两个队列】物品卡和经验卡共用同一套账本规则（合并窗口、
 * 上位淘汰、停留时长），分开管就会出现"经验把物品卡挤掉了"这种没道理的事。
 * 渲染层按实际类型选卡面：经验卡是独立样式，不走稀有度档位。
 * <p>
 * {@code Item} 里的栈必须是副本——物品实体下一 tick 就可能被移除，而卡要活好几秒。
 */
public sealed interface CardContent {

    record Item(ItemStack stack) implements CardContent {
    }

    /** 经验卡不背数值：数量在 Notice.count 里随合并累加，渲染直接读 count。 */
    record Experience() implements CardContent {
    }

    /**
     * 溢出卡：屏上满了、队也排满了，之后被丢下的那些拾取并成的<b>一张</b>卡。
     * <p>
     * 【为什么要有它】丢掉是 0.1.0 的语义，但玩家看不到任何信号 —— 一次捡 20 样东西，
     * 后面几样就这么没了。并成一张卡、写清"还有 N 项"、图标在成员之间轮播，
     * 至少让人知道"还有别的东西"。（行为学自同赛道最成熟那版，实现与常量是我们自己的。）
     *
     * @param stacks 被并进来的物品，按进来的先后；只为轮播留 {@link #MAX_ICONS} 个
     */
    record Overflow(java.util.List<ItemStack> stacks) implements CardContent {

        /** 轮播最多留几个图标：再多也看不出区别，白占内存。 */
        public static final int MAX_ICONS = 8;
    }
}
