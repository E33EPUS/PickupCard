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
    int trackX = this.getX() + 4;
    int trackWidth = this.width - 8;
    int trackY = this.getY() + this.height / 2;
    int filled = (int)Math.round(trackWidth * this.option.fraction());
    graphics.fill(trackX, trackY - 1, trackX + trackWidth, trackY + 1, 1711276032);
    graphics.fill(trackX, trackY - 1, trackX + Math.max(1, filled), trackY + 1, -10389);
    int knobX = trackX + filled;
    if (SdfRectRenderer.available()) {
      SdfRectRenderer.fill(graphics, knobX - 2, this.getY() + 3, knobX + 3, this.getY() + this.height - 3, 2.5F, this.isHoveredOrFocused() ? -1 : -2567448);
    } else {
      graphics.fill(knobX - 2, this.getY() + 3, knobX + 3, this.getY() + this.height - 3, -1);
    }

    String value = this.option.displayValue();
    Font font = Minecraft.getInstance().font;
    graphics.drawString(
      font, value, this.getX() + (this.width - font.width(value)) / 2, this.getY() + (this.height - 8) / 2 + 1, -659457, true
    );
  }

  public void onClick(double mouseX, double mouseY) {
    this.applyMouse(mouseX);
  }

  protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
    this.applyMouse(mouseX);
  }

  private void applyMouse(double mouseX) {
    double span = Math.max(1, this.width - 8);
    this.option.setFraction((mouseX - (this.getX() + 4)) / span);
  }
}
