package com.niuqu.pickupcard.configui;

import com.niuqu.pickupcard.config.ClientConfig;
import com.niuqu.pickupcard.render.SdfRectRenderer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ConfigScreen extends Screen {
  private static final String[] SECTIONS = new String[]{"general", "animation", "layout", "appearance"};
  private static final int HEADER_H = 46;
  private static final int FOOTER_H = 26;
  private static final int SIDEBAR_W = 100;
  private static final int ROW_H = 20;
  private static final int ROW_GAP = 3;
  private static final int SIDE_ITEM_H = 20;
  private static final int SIDE_ITEM_GAP = 2;
  private static final int PREVIEW_W = 170;
  private static final int PREVIEW_H = 40;
  private final Screen parent;
  private final CardPreview preview = new CardPreview(new ItemStack(Items.f_42415_));
  private final List<ConfigWidget> widgets = new ArrayList<>();
  private final List<ConfigOption> options = new ArrayList<>();
  private int section;
  private double scroll;
  private double maxScroll;
  private ConfigWidget hovered;

  public ConfigScreen(Screen parent) {
    super(Component.m_237115_("pickupcard.config.title"));
    this.parent = parent;
  }

  public void showSection(int index) {
    this.section = Mth.m_14045_(index, 0, SECTIONS.length - 1);
    if (this.f_96541_ != null) {
      this.rebuild();
    }
  }

  protected void m_7856_() {
    this.rebuild();
  }

  private int contentLeft() {
    return 112;
  }

  private int contentRight() {
    return this.f_96543_ - 10;
  }

  private int listTop() {
    return 48;
  }

  private int listBottom() {
    return this.f_96544_ - 26;
  }

  private int sideItemY(int index) {
    return 52 + index * 22;
  }

  private void rebuild() {
    this.widgets.clear();
    this.options.clear();
    this.scroll = 0.0;

    for (ConfigOption option : ClientConfig.OPTIONS) {
      if (option.section().equals(SECTIONS[this.section])) {
        this.options.add(option);
      }
    }

    int controlWidth = Mth.m_14045_((this.contentRight() - this.contentLeft()) / 2, 96, 132);
    int controlX = this.contentRight() - controlWidth;

    for (ConfigOption optionx : this.options) {
      this.widgets.add((ConfigWidget)(switch (optionx.kind()) {
        case BOOLEAN -> new ToggleWidget(optionx, controlX, 0, controlWidth, 18);
        case INT, DOUBLE -> new SliderWidget(optionx, controlX, 0, controlWidth, 18);
        case ENUM -> new CycleWidget(optionx, controlX, 0, controlWidth, 18);
        case STRING, LIST -> new TextWidget(optionx, this.f_96547_, controlX, 0, controlWidth, 18);
      }));
    }

    this.maxScroll = Math.max(0, this.options.size() * 23 - (this.listBottom() - this.listTop()));
    this.layoutRows();
    this.preview.replay();
  }

  private void layoutRows() {
    for (int i = 0; i < this.widgets.size(); i++) {
      this.widgets.get(i).m_253211_(this.listTop() + i * 23 - (int)this.scroll);
    }
  }

  public void m_88315_(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    graphics.m_280509_(0, 0, this.f_96543_, this.f_96544_, -267778542);
    this.drawHeader(graphics);
    this.drawSidebar(graphics, mouseX, mouseY);
    this.drawContent(graphics, mouseX, mouseY, partialTick);
    this.drawFooter(graphics, mouseX, mouseY);
    this.drawTooltip(graphics, mouseX, mouseY);
  }

  private void drawHeader(GuiGraphics graphics) {
    graphics.m_280614_(this.f_96547_, this.f_96539_, 10, 8, -659457, true);
    graphics.m_280614_(this.f_96547_, Component.m_237115_("pickupcard.config.hint"), 10, 20, -6516811, false);
    int x = this.f_96543_ - 170 - 10;
    panel(graphics, x, 4, x + 170, 44, 4.0F, -535162842, -10389);
    graphics.m_280614_(this.f_96547_, Component.m_237115_("pickupcard.config.preview"), x + 6, 8, -10389, false);
    this.preview.render(graphics, x + 2, 16, 166, 22);
  }

  private void drawSidebar(GuiGraphics graphics, int mouseX, int mouseY) {
    panel(graphics, 6, 46, 106, this.f_96544_ - 26 - 2, 4.0F, -534637008, 956290923);

    for (int i = 0; i < SECTIONS.length; i++) {
      int y = this.sideItemY(i);
      boolean active = i == this.section;
      boolean hot = mouseX >= 8 && mouseX < 106 && mouseY >= y && mouseY < y + 20;
      if (active || hot) {
        panel(graphics, 9, y, 106, y + 20, 3.0F, active ? -1607196592 : 1344673848, 956290923);
      }

      if (active) {
        graphics.m_280509_(9, y + 3, 11, y + 20 - 3, -10389);
      }

      graphics.m_280614_(this.f_96547_, Component.m_237115_("pickupcard.config.section." + SECTIONS[i]), 17, y + 6 + 1, active ? -659457 : -6516811, true);
    }
  }

  private void drawContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    int left = this.contentLeft();
    int right = this.contentRight();
    int top = this.listTop();
    int bottom = this.listBottom();
    graphics.m_280588_(left, top, right, bottom);
    this.hovered = null;

    try {
      for (int i = 0; i < this.widgets.size(); i++) {
        ConfigWidget widget = this.widgets.get(i);
        if (widget.m_252907_() + widget.m_93694_() >= top && widget.m_252907_() <= bottom) {
          widget.m_88315_(graphics, mouseX, mouseY, partialTick);
          String label = this.f_96547_.m_92834_(this.options.get(i).label().getString(), Math.max(24, widget.m_252754_() - left - 14));
          graphics.m_280056_(this.f_96547_, label, left + 4, widget.m_252907_() + (widget.m_93694_() - 8) / 2 + 1, -659457, true);
          if (widget.m_5953_(mouseX, mouseY)) {
            this.hovered = widget;
          }
        }
      }
    } finally {
      graphics.m_280618_();
    }

    if (this.maxScroll > 0.0) {
      int trackHeight = bottom - top;
      int barHeight = Math.max(16, (int)(trackHeight * (trackHeight / (trackHeight + this.maxScroll))));
      int barY = top + (int)((trackHeight - barHeight) * (this.scroll / this.maxScroll));
      graphics.m_280509_(right + 2, top, right + 4, bottom, 1711276032);
      graphics.m_280509_(right + 2, barY, right + 4, barY + barHeight, -10389);
    }
  }

  private void drawFooter(GuiGraphics graphics, int mouseX, int mouseY) {
    Component label = Component.m_237115_("gui.done");
    int w = 96;
    int x = this.f_96543_ - w - 10;
    int y = this.f_96544_ - 20;
    boolean hot = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 14;
    panel(graphics, x, y, x + w, y + 14, 4.0F, hot ? 1728042859 : 1881544760, hot ? -10389 : 956290923);
    graphics.m_280614_(this.f_96547_, label, x + (w - this.f_96547_.m_92852_(label)) / 2, y + 3, -659457, true);
  }

  private void drawTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
    if (this.hovered != null) {
      List<FormattedCharSequence> lines = this.hovered.option().tooltip(190);
      if (!lines.isEmpty()) {
        int lineHeight = 10;
        int boxWidth = 0;

        for (FormattedCharSequence line : lines) {
          boxWidth = Math.max(boxWidth, this.f_96547_.m_92724_(line));
        }

        boxWidth += 14;
        int boxHeight = lines.size() * lineHeight + 8;
        int x = Mth.m_14045_(mouseX + 10, 4, Math.max(4, this.f_96543_ - boxWidth - 4));
        int y = Mth.m_14045_(mouseY + 10, 4, Math.max(4, this.f_96544_ - boxHeight - 4));
        panel(graphics, x, y, x + boxWidth, y + boxHeight, 4.0F, -266858460, -10389);

        for (int i = 0; i < lines.size(); i++) {
          graphics.m_280649_(this.f_96547_, lines.get(i), x + 7, y + 4 + i * lineHeight, -659457, false);
        }
      }
    }
  }

  private static void panel(GuiGraphics graphics, int x1, int y1, int x2, int y2, float radius, int background, int border) {
    if (SdfRectRenderer.available()) {
      SdfRectRenderer.fillWithBorder(graphics, x1, y1, x2, y2, radius, background, 1.0F, border);
    } else {
      graphics.m_280509_(x1, y1, x2, y2, background);
    }
  }

  public boolean m_6375_(double mouseX, double mouseY, int button) {
    if (button != 0) {
      return super.m_6375_(mouseX, mouseY, button);
    } else {
      for (int i = 0; i < SECTIONS.length; i++) {
        int y = this.sideItemY(i);
        if (mouseX >= 8.0 && mouseX < 108.0 && mouseY >= y && mouseY < y + 20) {
          this.section = i;
          this.rebuild();
          return true;
        }
      }

      int w = 96;
      int bx = this.f_96543_ - w - 10;
      int by = this.f_96544_ - 20;
      if (mouseX >= bx && mouseX < bx + w && mouseY >= by && mouseY < by + 14) {
        this.m_7379_();
        return true;
      } else {
        ConfigWidget target = null;

        for (ConfigWidget widget : this.widgets) {
          if (widget.m_5953_(mouseX, mouseY) && mouseY >= this.listTop() && mouseY < this.listBottom()) {
            target = widget;
            break;
          }
        }

        for (ConfigWidget widgetx : this.widgets) {
          widgetx.m_93692_(widgetx == target);
        }

        if (target != null) {
          target.m_6375_(mouseX, mouseY, button);
          this.preview.replay();
          return true;
        } else {
          return super.m_6375_(mouseX, mouseY, button);
        }
      }
    }
  }

  public boolean m_7979_(double mouseX, double mouseY, int button, double dragX, double dragY) {
    for (ConfigWidget widget : this.widgets) {
      if (widget.m_93696_()) {
        widget.m_7979_(mouseX, mouseY, button, dragX, dragY);
        this.preview.replay();
        return true;
      }
    }

    return super.m_7979_(mouseX, mouseY, button, dragX, dragY);
  }

  public boolean m_6348_(double mouseX, double mouseY, int button) {
    for (ConfigWidget widget : this.widgets) {
      widget.m_6348_(mouseX, mouseY, button);
    }

    return super.m_6348_(mouseX, mouseY, button);
  }

  public boolean m_6050_(double mouseX, double mouseY, double delta) {
    if (this.maxScroll > 0.0 && mouseX >= this.contentLeft() - 4) {
      this.scroll = Mth.m_14008_(this.scroll - delta * 16.0, 0.0, this.maxScroll);
      this.layoutRows();
      return true;
    } else {
      return super.m_6050_(mouseX, mouseY, delta);
    }
  }

  public boolean m_7933_(int keyCode, int scanCode, int modifiers) {
    for (ConfigWidget widget : this.widgets) {
      if (widget.m_93696_() && widget.m_7933_(keyCode, scanCode, modifiers)) {
        this.preview.replay();
        return true;
      }
    }

    return super.m_7933_(keyCode, scanCode, modifiers);
  }

  public boolean m_5534_(char codePoint, int modifiers) {
    for (ConfigWidget widget : this.widgets) {
      if (widget.m_93696_() && widget.m_5534_(codePoint, modifiers)) {
        return true;
      }
    }

    return super.m_5534_(codePoint, modifiers);
  }

  public void m_7379_() {
    ClientConfig.SPEC.save();
    if (this.f_96541_ != null) {
      this.f_96541_.m_91152_(this.parent);
    }
  }

  public boolean m_7043_() {
    return false;
  }
}
