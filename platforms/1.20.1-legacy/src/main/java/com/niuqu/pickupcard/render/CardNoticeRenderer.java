package com.niuqu.pickupcard.render;

import com.niuqu.pickupcard.queue.Notice;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class CardNoticeRenderer {
  private static final int NEW_TEXT_ARGB = -15069146;
  private static final long SHEEN_MS = 760L;

  private CardNoticeRenderer() {
  }

  static void render(GuiGraphics graphics, Font font, Notice notice, NoticeMetrics m, double alpha, NoticeRenderSettings settings, long now) {
    ItemStack stack = notice.stack();
    boolean sdf = SdfRectRenderer.available();
    CardSkin skin = settings.raritySkins() ? CardSkin.of(stack.getRarity()) : CardSkin.COMMON;
    int nameArgb = RenderColors.withTextAlpha(settings.raritySkins() ? skin.nameArgb() : settings.nameColorArgb(), alpha);
    String name = NoticeText.displayName(notice, settings, font);
    int accentArgb = RenderColors.withTextAlpha(settings.accentColorArgb(), alpha);
    int borderArgb = RenderColors.withAlpha(settings.borderArgb(), alpha * 0.6);
    String count = "+" + NoticeRenderer.countText(notice, now, settings);
    int iconX = m.padX();
    int iconY = (m.height() - m.iconSize()) / 2;
    int textY = (m.height() - 9) / 2 + 1;
    int newHeight = Math.max(9, m.height() - 10);
    int newY = (m.height() - newHeight) / 2;
    skin.draw(graphics, 0, 0, m.width(), m.height(), alpha);
    if (sdf) {
      SdfRectRenderer.Sheen sweep = sheen(notice, m, settings, now, stack.isEnchanted(), skin);
      if (sweep != null || settings.borderThickness() > 0.0F) {
        SdfRectRenderer.fillCard(graphics, 0, 0, m.width(), m.height(), m.cornerRadius(), 0, settings.borderThickness(), borderArgb, sweep);
      }
    }

    int cursor = iconX + m.iconSize() + m.iconGap();
    int newX = cursor;
    int newWidth = 0;
    if (m.newBadge()) {
      newWidth = font.width("NEW") + m.badgePad() * 2;
      if (sdf) {
        SdfRectRenderer.fill(
          graphics, cursor, newY, cursor + newWidth, newY + newHeight, newHeight / 2.0F, RenderColors.withAlpha(settings.accentColorArgb(), alpha)
        );
      } else {
        graphics.fill(cursor, newY, cursor + newWidth, newY + newHeight, RenderColors.withAlpha(settings.accentColorArgb(), alpha));
      }

      cursor += newWidth + m.iconGap();
    }

    int countX = m.width() - m.padX() - font.width(count);
    if (m.durability()) {
      drawDurability(graphics, stack, m, alpha);
    }

    NoticeRenderer.drawIcon(graphics, stack, iconX, iconY, alpha);
    NoticeRenderer.resetShaderColor();
    if (m.newBadge()) {
      graphics.drawString(
        font, "NEW", newX + (newWidth - font.width("NEW")) / 2, newY + (newHeight - 9) / 2 + 1, RenderColors.withTextAlpha(-15069146, alpha), false
      );
    }

    graphics.drawString(font, name, cursor, textY, nameArgb, true);
    graphics.drawString(font, count, countX, textY, accentArgb, true);
  }

  private static void drawDurability(GuiGraphics graphics, ItemStack stack, NoticeMetrics m, double alpha) {
    int barX = 2;
    int barWidth = m.width() - barX - m.padX();
    int barY = m.height() - 3;
    double left = 1.0 - (double)stack.getDamageValue() / Math.max(1, stack.getMaxDamage());
    double clamped = Math.max(0.0, Math.min(1.0, left));
    int filled = (int)Math.round(barWidth * clamped);
    graphics.fill(barX, barY, barX + barWidth, barY + 2, RenderColors.withAlpha(-15458278, alpha));
    graphics.fill(barX, barY, barX + filled, barY + 2, RenderColors.withAlpha(durabilityColor(clamped), alpha));
  }

  private static int durabilityColor(double left) {
    if (left > 0.5) {
      return -11739060;
    } else {
      return left > 0.2 ? -2043316 : -2077620;
    }
  }

  private static SdfRectRenderer.Sheen sheen(Notice notice, NoticeMetrics m, NoticeRenderSettings settings, long now, boolean enchanted, CardSkin skin) {
    if (!settings.showSheen()) {
      return null;
    } else {
      long age = now - notice.bornAt();
      double span = m.width() + 60.0;
      if (enchanted) {
        double phase = age % 2400L / 2400.0;
        return new SdfRectRenderer.Sheen(-30.0 + phase * span, 16.0, 0.18, -5211137);
      } else if (age >= 0L && age <= 760L) {
        double progress = age / 760.0;
        return new SdfRectRenderer.Sheen(-30.0 + progress * span, 14.0, 0.38 * (1.0 - progress * 0.6), skin.sheenArgb());
      } else {
        return null;
      }
    }
  }
}
