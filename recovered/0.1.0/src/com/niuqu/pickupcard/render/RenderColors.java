package com.niuqu.pickupcard.render;

import net.minecraft.world.item.Rarity;

public final class RenderColors {
  private RenderColors() {
  }

  public static int withTextAlpha(int argb, double alpha) {
    int base = argb >>> 24 & 0xFF;
    int a = (int)Math.round(base * clamp01(alpha));
    if (base > 0 && a > 0 && a < 4) {
      a = 4;
    }

    return a << 24 | argb & 16777215;
  }

  public static int withAlpha(int argb, double alpha) {
    int a = (int)Math.round((argb >>> 24 & 0xFF) * clamp01(alpha));
    return a << 24 | argb & 16777215;
  }

  public static int rarityColor(Rarity rarity) {
    return switch (rarity) {
      case UNCOMMON -> -171;
      case RARE -> -11141121;
      case EPIC -> -43521;
      default -> -1;
    };
  }

  private static double clamp01(double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }
}
