package com.niuqu.pickupcard.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.niuqu.pickupcard.anim.Easing;
import com.niuqu.pickupcard.anim.Motion;
import com.niuqu.pickupcard.queue.Notice;
import com.niuqu.pickupcard.style.NoticeStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class NoticeRenderer {
  private NoticeRenderer() {
  }

  public static void render(
    GuiGraphics graphics,
    Font font,
    Notice notice,
    NoticeMetrics metrics,
    int x,
    int y,
    Motion motion,
    NoticeStyle style,
    NoticeRenderSettings settings,
    long now
  ) {
    PoseStack pose = graphics.pose();
    pose.pushPose();
    pose.translate(x + metrics.width(), y + metrics.height() / 2.0, 0.0);
    pose.mulPose(Axis.ZP.rotationDegrees((float)motion.rotation()));
    pose.scale((float)motion.scale(), (float)motion.scale(), 1.0F);
    pose.translate(-metrics.width(), -metrics.height() / 2.0, 0.0);

    try {
      if (style.hasPanel()) {
        CardNoticeRenderer.render(graphics, font, notice, metrics, motion.alpha(), settings, now);
      } else {
        MinimalNoticeRenderer.render(graphics, font, notice, metrics, motion.alpha(), settings, now);
      }
    } finally {
      pose.popPose();
    }
  }

  static void drawIcon(GuiGraphics graphics, ItemStack stack, int x, int y, double alpha) {
    float a = (float)Math.max(0.0, Math.min(1.0, alpha));
    if (a >= 0.999F) {
      graphics.renderItem(stack, x, y);
    } else {
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, a);

      try {
        graphics.renderItem(stack, x, y);
      } finally {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      }
    }
  }

  static void resetShaderColor() {
    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
  }

  static String countText(Notice notice, long now, NoticeRenderSettings settings) {
    return settings.countFormat().format(notice.displayCount(now, settings.rollMs(), Easing.SNAPPY));
  }
}
