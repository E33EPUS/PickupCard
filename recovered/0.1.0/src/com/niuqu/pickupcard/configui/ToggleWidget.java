package com.niuqu.pickupcard.configui;

import com.niuqu.pickupcard.render.SdfRectRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class ToggleWidget extends ConfigWidget {
  public ToggleWidget(ConfigOption option, int x, int y, int width, int height) {
    super(option, x, y, width, height);
  }

  @Override
  protected void renderControl(GuiGraphics graphics) {
    boolean on = this.option.asBool();
    int knob = this.f_93619_ - 8;
    int trackWidth = knob * 2;
    int trackX = this.m_252754_() + 4;
    int trackY = this.m_252907_() + (this.f_93619_ - knob) / 2;
    int trackColor = on ? 1442830187 : 1711276032;
    if (SdfRectRenderer.available()) {
      SdfRectRenderer.fill(graphics, trackX, trackY, trackX + trackWidth, trackY + knob, knob / 2.0F, trackColor);
    } else {
      graphics.m_280509_(trackX, trackY, trackX + trackWidth, trackY + knob, trackColor);
    }

    int knobX = on ? trackX + trackWidth - knob : trackX;
    int knobColor = on ? -10389 : -9805184;
    if (SdfRectRenderer.available()) {
      SdfRectRenderer.fill(graphics, knobX, trackY, knobX + knob, trackY + knob, knob / 2.0F, knobColor);
    } else {
      graphics.m_280509_(knobX, trackY, knobX + knob, trackY + knob, knobColor);
    }

    String state = on ? "\u5f00" : "\u5173";
    int textX = trackX + trackWidth + 9;
    graphics.m_280056_(Minecraft.m_91087_().f_91062_, state, textX, this.m_252907_() + (this.f_93619_ - 8) / 2 + 1, on ? -659457 : -6516811, true);
  }

  public void m_5716_(double mouseX, double mouseY) {
    this.option.setBool(!this.option.asBool());
  }

  protected void m_7212_(double mouseX, double mouseY, double dragX, double dragY) {
    this.option.setBool(mouseX > this.m_252754_() + this.f_93618_ / 2.0);
  }
}
