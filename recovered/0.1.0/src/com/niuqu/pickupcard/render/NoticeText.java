package com.niuqu.pickupcard.render;

import com.niuqu.pickupcard.queue.Notice;
import net.minecraft.client.gui.Font;
import net.minecraft.core.registries.BuiltInRegistries;

public final class NoticeText {
  private static final String ELLIPSIS = "\u2026";

  private NoticeText() {
  }

  public static String displayName(Notice notice, NoticeRenderSettings settings, Font font) {
    String raw = settings.showItemId() ? BuiltInRegistries.f_257033_.m_7981_(notice.stack().m_41720_()).toString() : notice.stack().m_41786_().getString();
    int maxWidth = settings.nameMaxWidth();
    if (maxWidth > 0 && font.m_92895_(raw) > maxWidth) {
      int room = Math.max(8, maxWidth - font.m_92895_("\u2026"));
      return font.m_92834_(raw, room) + "\u2026";
    } else {
      return raw;
    }
  }
}
