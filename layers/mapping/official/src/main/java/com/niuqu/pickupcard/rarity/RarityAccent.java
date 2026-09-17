package com.niuqu.pickupcard.rarity;

import net.minecraft.world.item.ItemStack;

/**
 * 稀有度 → 强调色（ARGB）。vanilla 四档取色，色值与 docs/art/mockup.html 的 CSS 变量一致。
 * <p>
 * RarityCore 联动（七档 + 玩家自定义取色）将来在这里接入：检测到 raritycore 时
 * 本类的解析结果被 {@code RarityCoreBridge} 覆盖 —— 外部 API 的接触面只有这一个方法，
 * 联动出问题或要下架时，删掉那个 bridge 就完全解除了。
 */
public final class RarityAccent {

    private RarityAccent() {
    }

    public static int of(ItemStack stack) {
        return switch (stack.getRarity()) {
            case UNCOMMON -> 0xFFFFD83D;
            case RARE -> 0xFF55EBFF;
            case EPIC -> 0xFFD78BFF;
            default -> 0xFF9AA4AD;
        };
    }

    /** 经验卡的强调色：绿色系，刻意不走稀有度档位。 */
    public static final int XP = 0xFF7DFF8A;

    /**
     * 溢出卡的强调色：中性灰蓝。
     * <p>
     * 【为什么不跟着成员走】它的成员混着各种稀有度，跟着谁都会让"这张卡是什么档"说不清；
     * 它本来就不是一件东西，而是一个"还有更多"的信号。
     */
    public static final int OVERFLOW = 0xFFA8B2C0;
}
