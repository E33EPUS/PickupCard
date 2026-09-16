package com.niuqu.pickupcard.hud;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * 稀有度 → 强调色（ARGB）。vanilla 四档兜底取色，色值与 mockup 的 CSS 变量一致。
 * <p>
 * RarityCore 联动（七档 + 玩家自定义取色）将在下一步接入：检测到 raritycore 时
 * 本类的解析结果被 {@code RarityCoreBridge} 覆盖——接入点只有这一个方法。
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
}
