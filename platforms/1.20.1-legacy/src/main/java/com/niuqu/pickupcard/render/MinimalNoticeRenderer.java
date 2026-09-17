package com.niuqu.pickupcard.render;

import com.niuqu.pickupcard.queue.Notice;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class MinimalNoticeRenderer {
  private MinimalNoticeRenderer() {
  }

  static void render(GuiGraphics graphics, Font font, Notice notice, NoticeMetrics metrics, double alpha, NoticeRenderSettings settings, long now) {
    int iconY = (metrics.height() - metrics.iconSize()) / 2;
    NoticeRenderer.drawIcon(graphics, notice.stack(), metrics.padX(), iconY, alpha);
    String count = NoticeRenderer.countText(notice, now, settings);
    int textX = metrics.padX() + metrics.iconSize() + metrics.iconGap();
    NoticeRenderer.resetShaderColor();
    int textY = (metrics.height() - 9) / 2 + 1;
    graphics.drawString(font, count, textX, textY, RenderColors.withTextAlpha(settings.accentColorArgb(), alpha), true);
  }
}
