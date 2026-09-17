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
    Font font = Minecraft.getInstance().font;
    String value = this.option.displayValue();
    int centerY = this.getY() + (this.height - 8) / 2 + 1;
    int textX = this.getX() + (this.width - font.width(value)) / 2;
    graphics.drawString(font, value, textX, centerY, -659457, true);
    int arrowColor = this.isHoveredOrFocused() ? -10389 : -6516811;
    fillArrow(graphics, this.getX() + 4, this.getY() + this.height / 2, false, arrowColor);
    fillArrow(graphics, this.getX() + this.width - 7, this.getY() + this.height / 2, true, arrowColor);
  }

  private static void fillArrow(GuiGraphics graphics, int x, int y, boolean right, int color) {
    for (int row = -2; row <= 2; row++) {
      int length = 3 - Math.abs(row);
      int startX = right ? x : x + (2 - length);
      graphics.fill(startX, y + row, startX + length, y + row + 1, color);
    }
  }

  public void onClick(double mouseX, double mouseY) {
    this.option.cycle();
  }

  protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
  }
}
