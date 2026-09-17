package com.niuqu.pickupcard.configui;

import com.niuqu.pickupcard.anim.AnimationSpec;
import com.niuqu.pickupcard.anim.Motion;
import com.niuqu.pickupcard.anim.Timeline;
import com.niuqu.pickupcard.config.ClientConfig;
import com.niuqu.pickupcard.queue.Notice;
import com.niuqu.pickupcard.render.NoticeMetrics;
import com.niuqu.pickupcard.render.NoticeRenderSettings;
import com.niuqu.pickupcard.render.NoticeRenderer;
import com.niuqu.pickupcard.style.NoticeStyle;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

public final class CardPreview {
  private static final long GAP_MS = 650L;
  private final Notice notice;
  private long cycleStart = Util.getMillis();

  public CardPreview(ItemStack stack) {
    this.notice = Notice.preview(stack, 64, true);
  }

  public void replay() {
    this.cycleStart = Util.getMillis();
  }

  public void render(GuiGraphics graphics, int x, int y, int width, int height) {
    Minecraft minecraft = Minecraft.getInstance();
    Font font = minecraft.font;
    NoticeStyle style = ClientConfig.style();
    NoticeRenderSettings settings = ClientConfig.renderSettings();
    AnimationSpec spec = ClientConfig.animationSpec();
    NoticeMetrics metrics = NoticeMetrics.measure(style, font, this.notice, settings);
    long now = Util.getMillis();
    long age = now - this.cycleStart;
    if (age > spec.totalMs() + 650L) {
      this.cycleStart = now;
      age = 0L;
    }

    Motion motion = Timeline.sample(age, spec);
    if (motion.visible()) {
      int cardX = x + (width - metrics.width()) / 2;
      int cardY = y + (height - metrics.height()) / 2;
      NoticeRenderer.render(graphics, font, this.notice, metrics, cardX, cardY, motion, style, settings, now);
    }
  }
}
