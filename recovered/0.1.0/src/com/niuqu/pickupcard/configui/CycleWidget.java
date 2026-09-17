package com.niuqu.pickupcard.configui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class CycleWidget extends ConfigWidget {
  public CycleWidget(ConfigOption option, int x, int y, int width, int height) {
    super(option, x, y, width, height);
  }

  @Override
  protected void renderControl(GuiGraphics graphics) {
    Font font = Minecraft.m_91087_().f_91062_;
    String value = this.option.displayValue();
    int centerY = this.m_252907_() + (this.f_93619_ - 8) / 2 + 1;
    int textX = this.m_252754_() + (this.f_93618_ - font.m_92895_(value)) / 2;
    graphics.m_280056_(font, value, textX, centerY, -659457, true);
    int arrowColor = this.m_198029_() ? -10389 : -6516811;
    fillArrow(graphics, this.m_252754_() + 4, this.m_252907_() + this.f_93619_ / 2, false, arrowColor);
    fillArrow(graphics, this.m_252754_() + this.f_93618_ - 7, this.m_252907_() + this.f_93619_ / 2, true, arrowColor);
  }

  private static void fillArrow(GuiGraphics graphics, int x, int y, boolean right, int color) {
    for (int row = -2; row <= 2; row++) {
      int length = 3 - Math.abs(row);
      int startX = right ? x : x + (2 - length);
      graphics.m_280509_(startX, y + row, startX + length, y + row + 1, color);
    }
  }

  public void m_5716_(double mouseX, double mouseY) {
    this.option.cycle();
  }

  protected void m_7212_(double mouseX, double mouseY, double dragX, double dragY) {
  }
}
