package com.niuqu.pickupcard.configui;

import com.niuqu.pickupcard.render.SdfRectRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;

public abstract class ConfigWidget extends AbstractWidget {
  protected final ConfigOption option;

  protected ConfigWidget(ConfigOption option, int x, int y, int width, int height) {
    super(x, y, width, height, option.label());
    this.option = option;
  }

  public ConfigOption option() {
    return this.option;
  }

  protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    boolean active = this.isHoveredOrFocused();
    int background = active ? -1607196592 : 1881544760;
    int border = active ? -10389 : 956290923;
    if (SdfRectRenderer.available()) {
      SdfRectRenderer.fillWithBorder(
        graphics,
        this.getX(),
        this.getY(),
        this.getX() + this.width,
        this.getY() + this.height,
        Math.min(4.0F, this.height / 2.0F),
        background,
        1.0F,
        border
      );
    } else {
      graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, background);
    }

    this.renderControl(graphics);
  }

  protected abstract void renderControl(GuiGraphics var1);

  protected void updateWidgetNarration(NarrationElementOutput output) {
    this.defaultButtonNarrationText(output);
  }
}
