package com.niuqu.pickupcard.configui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;

public final class TextWidget extends ConfigWidget {
  private final EditBox box;

  public TextWidget(ConfigOption option, Font font, int x, int y, int width, int height) {
    super(option, x, y, width, height);
    this.box = new EditBox(font, x + 2, y + 2, width - 4, height - 4, option.label());
    this.box.setValue(option.asText());
    this.box.setResponder(option::setText);
    this.box.setMaxLength(512);
  }

  @Override
  protected void renderControl(GuiGraphics graphics) {
    this.box.setX(this.getX() + 2);
    this.box.setY(this.getY() + 2);
    this.box.setWidth(this.width - 4);
    this.box.render(graphics, 0, 0, 0.0F);
  }

  public void onClick(double mouseX, double mouseY) {
    this.box.setFocused(true);
    this.box.mouseClicked(mouseX, mouseY, 0);
  }

  public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
    return this.box.keyPressed(keyCode, scanCode, modifiers);
  }

  public boolean charTyped(char codePoint, int modifiers) {
    return this.box.charTyped(codePoint, modifiers);
  }

  public void setFocused(boolean focused) {
    super.setFocused(focused);
    this.box.setFocused(focused);
    if (!focused) {
      this.option.setText(this.box.getValue());
    }
  }

  public void refresh() {
    if (!this.box.isFocused()) {
      this.box.setValue(this.option.asText());
    }
  }
}
