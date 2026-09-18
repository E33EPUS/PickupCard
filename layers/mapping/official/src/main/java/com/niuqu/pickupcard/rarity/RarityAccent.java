package com.niuqu.pickupcard.rarity;

import com.niuqu.pickupcard.style.StyleModel;
import net.minecraft.world.item.ItemStack;

/**
 * 稀有度 → 强调色。<b>具体取哪一套色由主题给</b>（{@link StyleModel.Accents}）。
 *
 * <p>【为什么这里不再写死色值】它原来把 {@code #9AA4AD / #FFD83D / #55EBFF / #D78BFF /
 * #7DFF8A} 硬编码了一遍，而主题 JSON 里 {@code accent.*} 从第一天就写着同样六个数 ——
 * 一份真源两份数据：玩家（或资源包）改主题时，Java 里这份不会跟着动，卡片的强调色就
 * "改了没反应"。现在这里只负责<b>把 vanilla 的四档映射到主题给的四个位置</b>，
 * 是"哪一档"的知识，不是"什么颜色"的知识。
 *
 * <p>【RarityCore 联动接在哪】它的七档 + 玩家自定义取色将来覆盖的就是一个
 * {@code Accents} 实例 —— 交接面从六个常量变成一个值对象，bridge 出问题或要下架时，
 * 回到 {@code style.accents()} 就完全解除了。{@code tokens.css} 里那句注释
 * "装了 RarityCore 时由它接管，这里的值作为无联动时的兜底"说的正是这件事。
 */
public final class RarityAccent {

    private RarityAccent() {
    }

    /** 物品的强调色：按 vanilla 稀有度挑主题里的那一档。 */
    public static int of(ItemStack stack, StyleModel.Accents a) {
        return switch (stack.getRarity()) {
            case UNCOMMON -> a.uncommon();
            case RARE -> a.rare();
            case EPIC -> a.epic();
            default -> a.common();
        };
    }

    /** 经验卡的强调色：走主题里单独的一档，<b>刻意不参与稀有度分级</b>。 */
    public static int xp(StyleModel.Accents a) {
        return a.xp();
    }

    /**
     * 溢出卡（"还有 N 项"）的强调色。
     * <p>【为什么不跟着成员走】它的成员混着各种稀有度，跟着谁都会让"这张卡是什么档"说不清；
     * 它本来就不是一件东西，而是一个"还有更多"的信号。
     */
    public static int overflow(StyleModel.Accents a) {
        return a.overflow();
    }
}
