package com.niuqu.pickupcard.dev;

import com.niuqu.pickupcard.pickup.CardContent;
import com.niuqu.pickupcard.pickup.Inbox;
import com.niuqu.pickupcard.render.CardStage;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

import java.util.ArrayList;
import java.util.List;

/**
 * harness 的固定样例卡集。
 * <p>
 * 【为什么必须固定】"这次改完比上次好看吗"只有在<b>每次都看同一组卡</b>的前提下才是个
 * 可回答的问题。上一版美术定不了稿，很大一部分原因是每次看到的卡都不一样——名字长度、
 * 数量位数、稀有度随手就变，讨论的是不同的东西。
 * <p>
 * 【为什么覆盖这几类】每一张都在钉一个边界：
 * 四档稀有度（强调色）、经验卡（独立色系、没有 ItemStack）、超长名字（会撞卡宽上限）、
 * 超大数量（会走数量缩写）、单字名字（会撞名字区下限）、中文名（字宽与英文不同）。
 * 改画法之后这六类都不出问题，才算没把已有的东西弄坏。
 */
public final class CardFixtures {

    /**
     * 一张样例卡。
     *
     * @param label  显示在 harness 上的名字
     * @param stack  物品栈；{@link ItemStack#EMPTY} 表示经验卡
     * @param amount 拾取数量
     */
    public record Fixture(String label, ItemStack stack, int amount) {

        public boolean xp() {
            return stack.isEmpty();
        }
    }

    private CardFixtures() {
    }

    /** 全部样例。每次调用现算——注册表在 dev 屏打开时才保证已经就绪。 */
    public static List<Fixture> all() {
        List<Fixture> list = new ArrayList<>();

        // 四档稀有度：不写死物品名，而是从注册表里按 rarity 找，跨版本不会因为
        // "某个物品被改了稀有度"而让这一组悄悄少一张
        for (Rarity rarity : Rarity.values()) {
            list.add(new Fixture(rarityName(rarity), new ItemStack(itemOf(rarity)), 1));
        }

        list.add(new Fixture("xp", ItemStack.EMPTY, 137));
        list.add(new Fixture("long-name",
                named(Items.DIAMOND_SWORD, "被铁砧改了名字的附魔钻石剑（超长名字边界测试）"), 1));
        list.add(new Fixture("big-count", new ItemStack(Items.COBBLESTONE), 99_999));
        list.add(new Fixture("short-name", named(Items.STONE, "石"), 1));
        list.add(new Fixture("cjk", named(Items.NETHERITE_INGOT, "下界合金锭"), 64));

        return List.copyOf(list);
    }

    /** 把一张样例送进账本——走的是和真实拾取完全相同的那条路。 */
    public static void inject(Fixture fixture) {
        if (fixture.xp()) {
            Inbox.INSTANCE.offer(new CardContent.Experience(), fixture.amount());
        } else {
            Inbox.INSTANCE.offer(new CardContent.Item(fixture.stack().copy()), fixture.amount());
        }
    }

    public static void injectAll() {
        for (Fixture fixture : all()) {
            inject(fixture);
        }
    }

    /** 清空在屏的卡与账本。换一组样例前先清，免得看不出来谁是谁。 */
    public static void clear() {
        Inbox.INSTANCE.reset();
        CardStage.INSTANCE.clear();
    }

    private static String rarityName(Rarity rarity) {
        return rarity.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static Item itemOf(Rarity rarity) {
        return BuiltInRegistries.ITEM.stream()
                .filter(item -> item.getRarity(new ItemStack(item)) == rarity)
                .findFirst()
                .orElse(Items.STONE);
    }

    private static ItemStack named(Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.setHoverName(Component.literal(name));
        return stack;
    }
}
