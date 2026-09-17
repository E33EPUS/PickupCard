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

  protected void m_87963_(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    boolean active = this.m_198029_();
    int background = active ? -1607196592 : 1881544760;
    int border = active ? -10389 : 956290923;
    if (SdfRectRenderer.available()) {
      SdfRectRenderer.fillWithBorder(
        graphics,
        this.m_252754_(),
        this.m_252907_(),
        this.m_252754_() + this.f_93618_,
        this.m_252907_() + this.f_93619_,
        Math.min(4.0F, this.f_93619_ / 2.0F),
        background,
        1.0F,
        border
      );
    } else {
      graphics.m_280509_(this.m_252754_(), this.m_252907_(), this.m_252754_() + this.f_93618_, this.m_252907_() + this.f_93619_, background);
    }

    this.renderControl(graphics);
  }

  protected abstract void renderControl(GuiGraphics var1);

  protected void m_168797_(NarrationElementOutput output) {
    this.m_168802_(output);
  }
}
