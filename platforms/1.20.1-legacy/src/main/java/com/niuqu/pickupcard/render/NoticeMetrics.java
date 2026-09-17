package com.niuqu.pickupcard.render;

import com.niuqu.pickupcard.queue.Notice;
import com.niuqu.pickupcard.style.NoticeStyle;
import net.minecraft.client.gui.Font;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record NoticeMetrics(
  int width, int height, int padX, int iconSize, int iconGap, int cornerRadius, int socketSize, int badgePad, boolean durability, boolean newBadge
) {
  public static final String NEW_LABEL = "NEW";

  public static NoticeMetrics measure(NoticeStyle style, Font font, Notice notice, NoticeRenderSettings settings) {
    double scale = settings.scale();
    boolean card = style.hasPanel();
    String countText = settings.countFormat().format(notice.count());
    int iconSize = (int)Math.round(16.0 * scale);
    int height = (int)Math.round((card ? 26 : 18) * scale);
    int padX = (int)Math.round((card ? 4 : 1) * scale);
    int iconGap = (int)Math.round((card ? 6 : 3) * scale);
    int badgePad = card ? (int)Math.round(3.0 * scale) : 0;
    boolean newBadge = card && settings.showNewBadge() && notice.firstTime();
    boolean durability = card && settings.showDurability() && notice.stack().isDamaged();
    int width = padX * 2 + iconSize + iconGap + font.width(countText) + badgePad * 2;
    if (card && settings.showName()) {
      width += font.width(NoticeText.displayName(notice, settings, font)) + iconGap;
    }

    if (newBadge) {
      width += font.width("NEW") + badgePad * 2 + iconGap;
    }

    int fallbackRadius = (int)Math.round((card ? 4 : 3) * scale);
    int radius = settings.cornerRadius() >= 0 ? (int)Math.round(settings.cornerRadius() * scale) : fallbackRadius;
    radius = Math.min(radius, height / 2);
    return new NoticeMetrics(width, height, padX, iconSize, iconGap, radius, iconSize, badgePad, durability, newBadge);
  }

  public int badgeHeight() {
    return this.height - this.padX * 2;
  }
}
