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
}
