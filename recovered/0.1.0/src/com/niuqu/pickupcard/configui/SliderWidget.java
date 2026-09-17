package com.niuqu.pickupcard.configui;

import com.niuqu.pickupcard.render.SdfRectRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class SliderWidget extends ConfigWidget {
  private static final int PAD = 4;

  public SliderWidget(ConfigOption option, int x, int y, int width, int height) {
    super(option, x, y, width, height);
  }

  @Override
  protected void renderControl(GuiGraphics graphics) {
    int trackX = this.m_252754_() + 4;
    int trackWidth = this.f_93618_ - 8;
    int trackY = this.m_252907_() + this.f_93619_ / 2;
    int filled = (int)Math.round(trackWidth * this.option.fraction());
    graphics.m_280509_(trackX, trackY - 1, trackX + trackWidth, trackY + 1, 1711276032);
    graphics.m_280509_(trackX, trackY - 1, trackX + Math.max(1, filled), trackY + 1, -10389);
    int knobX = trackX + filled;
    if (SdfRectRenderer.available()) {
      SdfRectRenderer.fill(graphics, knobX - 2, this.m_252907_() + 3, knobX + 3, this.m_252907_() + this.f_93619_ - 3, 2.5F, this.m_198029_() ? -1 : -2567448);
    } else {
      graphics.m_280509_(knobX - 2, this.m_252907_() + 3, knobX + 3, this.m_252907_() + this.f_93619_ - 3, -1);
    }

    String value = this.option.displayValue();
    Font font = Minecraft.m_91087_().f_91062_;
    graphics.m_280056_(
      font, value, this.m_252754_() + (this.f_93618_ - font.m_92895_(value)) / 2, this.m_252907_() + (this.f_93619_ - 8) / 2 + 1, -659457, true
    );
  }

  public void m_5716_(double mouseX, double mouseY) {
    this.applyMouse(mouseX);
  }

  protected void m_7212_(double mouseX, double mouseY, double dragX, double dragY) {
    this.applyMouse(mouseX);
  }

  private void applyMouse(double mouseX) {
    double span = Math.max(1, this.f_93618_ - 8);
    this.option.setFraction((mouseX - (this.m_252754_() + 4)) / span);
  }
}
