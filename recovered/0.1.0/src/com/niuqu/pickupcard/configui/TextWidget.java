package com.niuqu.pickupcard.configui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;

public final class TextWidget extends ConfigWidget {
  private final EditBox box;

  public TextWidget(ConfigOption option, Font font, int x, int y, int width, int height) {
    super(option, x, y, width, height);
    this.box = new EditBox(font, x + 2, y + 2, width - 4, height - 4, option.label());
    this.box.m_94144_(option.asText());
    this.box.m_94151_(option::setText);
    this.box.m_94199_(512);
  }

  @Override
  protected void renderControl(GuiGraphics graphics) {
    this.box.m_252865_(this.m_252754_() + 2);
    this.box.m_253211_(this.m_252907_() + 2);
    this.box.m_93674_(this.f_93618_ - 4);
    this.box.m_88315_(graphics, 0, 0, 0.0F);
  }

  public void m_5716_(double mouseX, double mouseY) {
    this.box.m_93692_(true);
    this.box.m_6375_(mouseX, mouseY, 0);
  }

  public boolean m_7933_(int keyCode, int scanCode, int modifiers) {
    return this.box.m_7933_(keyCode, scanCode, modifiers);
  }

  public boolean m_5534_(char codePoint, int modifiers) {
    return this.box.m_5534_(codePoint, modifiers);
  }

  public void m_93692_(boolean focused) {
    super.m_93692_(focused);
    this.box.m_93692_(focused);
    if (!focused) {
      this.option.setText(this.box.m_94155_());
    }
  }

  public void refresh() {
    if (!this.box.m_93696_()) {
      this.box.m_94144_(this.option.asText());
    }
  }
}
