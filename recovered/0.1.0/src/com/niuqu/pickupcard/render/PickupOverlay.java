package com.niuqu.pickupcard.render;

import com.niuqu.pickupcard.PickupCard;
import com.niuqu.pickupcard.anim.AnimationSpec;
import com.niuqu.pickupcard.anim.Motion;
import com.niuqu.pickupcard.anim.Timeline;
import com.niuqu.pickupcard.config.ClientConfig;
import com.niuqu.pickupcard.layout.GrowthDirection;
import com.niuqu.pickupcard.layout.LayoutSpec;
import com.niuqu.pickupcard.layout.NoticeLayout;
import com.niuqu.pickupcard.queue.Notice;
import com.niuqu.pickupcard.queue.NoticeQueue;
import com.niuqu.pickupcard.queue.StackSmoother;
import com.niuqu.pickupcard.style.NoticeStyle;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.fml.loading.FMLLoader;

@OnlyIn(Dist.CLIENT)
public final class PickupOverlay implements IGuiOverlay {
  public static final PickupOverlay INSTANCE = new PickupOverlay();
  private static final boolean TRACE = !FMLLoader.isProduction() && Boolean.getBoolean("pickupcard.trace");
  private long lastFrameAt = -9223372036854775808L;

  private PickupOverlay() {
  }

  public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
    Minecraft minecraft = Minecraft.m_91087_();
    if (ClientConfig.enabled() && minecraft.f_91074_ != null && !minecraft.f_91066_.f_92062_) {
      if (minecraft.f_91080_ == null) {
        long now = Util.m_137550_();
        double dt = this.lastFrameAt == -9223372036854775808L ? 0.016 : Math.min(0.1, (now - this.lastFrameAt) / 1000.0);
        this.lastFrameAt = now;
        NoticeQueue.INSTANCE.tick(now);
        List<Notice> active = NoticeQueue.INSTANCE.active();
        if (!active.isEmpty()) {
          Font font = minecraft.f_91062_;
          AnimationSpec animation = ClientConfig.animationSpec();
          NoticeStyle style = ClientConfig.style();
          NoticeRenderSettings settings = ClientConfig.renderSettings();
          GrowthDirection growth = ClientConfig.growth();
          int size = active.size();

          for (int i = 0; i < size; i++) {
            Notice notice = active.get(i);
            Motion motion = Timeline.sample(now - notice.bornAt(), animation);
            if (motion.visible()) {
              long sincePulse = notice.pulseAt() == -9223372036854775808L ? -1L : now - notice.pulseAt();
              double pulse = Timeline.pulseScale(sincePulse, animation);
              Motion pulsed = pulse == 1.0 ? motion : new Motion(motion.alpha(), motion.slide(), motion.scale() * pulse, motion.rotation(), motion.phase());
              NoticeMetrics metrics = NoticeMetrics.measure(style, font, notice, settings);
              LayoutSpec spec = ClientConfig.layoutSpec(metrics.width(), metrics.height());
              int stackIndex = growth.stackIndex(i, size);
              double target = NoticeLayout.outwardPixels(stackIndex, metrics.height(), spec.separation());
              double current = notice.hasCurrentOffset() ? notice.currentOffset() : target;
              notice.setTargetOffset((float)target);
              notice.setCurrentOffset((float)StackSmoother.approach(current, target, dt));
              NoticeLayout.Placement placement = NoticeLayout.place(spec, screenWidth, screenHeight, notice.currentOffset(), motion.slide());
              if (TRACE) {
                PickupCard.LOGGER
                  .info(
                    "[pickupcard] trace age={} phase={} alpha={} slide={} rot={} pos=({},{}),{}x{} scale={}",
                    new Object[]{
                      now - notice.bornAt(),
                      motion.phase(),
                      String.format("%.4f", motion.alpha()),
                      String.format("%.2f", motion.slide()),
                      String.format("%.2f", motion.rotation()),
                      placement.x(),
                      placement.y(),
                      metrics.width(),
                      metrics.height(),
                      String.format("%.3f", motion.scale())
                    }
                  );
              }

              NoticeRenderer.render(graphics, font, notice, metrics, placement.x(), placement.y(), pulsed, style, settings, now);
            }
          }
        }
      }
    }
  }
}
