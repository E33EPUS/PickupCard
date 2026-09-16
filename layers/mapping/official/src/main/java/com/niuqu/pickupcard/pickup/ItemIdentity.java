package com.niuqu.pickupcard.pickup;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * 从 ItemStack 抽出两把键：一把认人，一把认脸。
 * <p>
 * 【为什么要两把】{@code key} 答"是不是同一个东西"，{@code lookKey} 答"该不该长得一样"。
 * 合并要求两者都相同 —— 只比物品会让"普通附魔书"和"经验修补附魔书"并进同一张卡，
 * 而它们的稀有度档位不同，卡面就会跟内容对不上。
 * <p>
 * 【key 为什么不含数量】数量是被合并改写的那个值，把它编进键里，第二次拾取的键就变了，
 * 永远合并不起来 —— 这正是"为什么我捡了一组钻石却弹了两张卡"的经典死法。
 */
public final class ItemIdentity {

    private ItemIdentity() {
    }

    /**
     * 身份键：物品 id + 完整 NBT。改名、附魔、自定义数据都会体现在 NBT 里，
     * 所以"我的钻石剑"和"你的钻石剑"是两件不同的东西 —— 这是想要的语义。
     */
    public static String keyOf(ItemStack stack) {
        if (stack.isEmpty()) return "minecraft:air";
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        // ItemStack#getTag 在 1.20.1 与 1.21.1 上都在（1.21.1 起是组件，但方法仍在）
        return stack.getTag() == null ? id : id + '#' + stack.getTag();
    }

    /**
     * 外观键：稀有度 + 是否附魔 + 是否有损耗。这三样决定卡面档位与特效，
     * 任意一项不同就不该共用一张卡。
     */
    public static String lookOf(ItemStack stack) {
        Rarity rarity = stack.getRarity();
        return rarity.name() + '|' + stack.isEnchanted() + '|' + stack.isDamaged();
    }
}
