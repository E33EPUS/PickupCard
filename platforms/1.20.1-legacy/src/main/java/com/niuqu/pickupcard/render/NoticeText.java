package com.niuqu.pickupcard.render;

import com.niuqu.pickupcard.queue.Notice;
import net.minecraft.client.gui.Font;
import net.minecraft.core.registries.BuiltInRegistries;

public final class NoticeText {
  private static final String ELLIPSIS = "\u2026";

  private NoticeText() {
  }

  public static String displayName(Notice notice, NoticeRenderSettings settings, Font font) {
    String raw = settings.showItemId() ? BuiltInRegistries.ITEM.getKey(notice.stack().getItem()).toString() : notice.stack().getHoverName().getString();
    int maxWidth = settings.nameMaxWidth();
    if (maxWidth > 0 && font.width(raw) > maxWidth) {
      int room = Math.max(8, maxWidth - font.width("\u2026"));
      return font.plainSubstrByWidth(raw, room) + "\u2026";
    } else {
      return raw;
    }
  }
}
