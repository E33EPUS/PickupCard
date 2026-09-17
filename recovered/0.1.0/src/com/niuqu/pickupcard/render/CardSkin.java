package com.niuqu.pickupcard.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public enum CardSkin {
  COMMON("common", -3108, -5696),
  UNCOMMON("uncommon", -4388, -13942),
  RARE("rare", -1378561, -3151617),
  EPIC("epic", -3841, -5720);

  private static final int TEXEL_SCALE = 4;
  private static final int CARD_W = 140;
  private static final int CARD_H = 26;
  private static final int PAD = 10;
  private static final int CAP = 14;
  private static final int TEX_W = 640;
  private static final int TEX_H = 184;
  private final ResourceLocation texture;
  private final int nameArgb;
  private final int sheenArgb;

  private CardSkin(String id, int nameArgb, int sheenArgb) {
    this.texture = ResourceLocation.fromNamespaceAndPath("pickupcard", "textures/gui/card_" + id + ".png");
    this.nameArgb = nameArgb;
    this.sheenArgb = sheenArgb;
  }

  public static CardSkin of(Rarity rarity) {
    return switch (rarity) {
      case UNCOMMON -> UNCOMMON;
      case RARE -> RARE;
      case EPIC -> EPIC;
      default -> COMMON;
    };
  }

  public int nameArgb() {
    return this.nameArgb;
  }

  public int sheenArgb() {
    return this.sheenArgb;
  }

  void draw(GuiGraphics graphics, int x, int y, int width, int height, double alpha) {
    float a = (float)Math.max(0.0, Math.min(1.0, alpha));
    if (!(a <= 0.0F)) {
      Minecraft.m_91087_().m_91097_().m_118506_(this.texture).m_117960_(true, false);
      double grow = (height + 20.0) / 46.0;
      int edge = (int)Math.round(14.0 * grow);
      int head = (int)Math.round(10.0 * grow);
      int dstY = y - head;
      int dstH = height + head * 2;
      int capW = head + edge;
      int midW = width - edge * 2;
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, a);

      try {
        graphics.m_280411_(this.texture, x - head, dstY, capW, dstH, 0.0F, 0.0F, capW * 4, 184, 640, 184);
        if (midW > 0) {
          graphics.m_280411_(this.texture, x + edge, dstY, midW, dstH, capW * 4, 0.0F, 448, 184, 640, 184);
        }

        graphics.m_280411_(this.texture, x + width - edge, dstY, capW, dstH, 640 - capW * 4, 0.0F, capW * 4, 184, 640, 184);
      } finally {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      }
    }
  }
}
