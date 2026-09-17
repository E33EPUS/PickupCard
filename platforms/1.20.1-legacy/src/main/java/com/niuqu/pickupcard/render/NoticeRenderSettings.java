package com.niuqu.pickupcard.render;

import com.niuqu.pickupcard.text.CountFormat;

public record NoticeRenderSettings(
  double scale,
  int backgroundArgb,
  int borderArgb,
  float borderThickness,
  int cornerRadius,
  int nameColorArgb,
  int accentColorArgb,
  boolean showName,
  CountFormat countFormat,
  int rollMs,
  float frameThickness,
  boolean showSheen,
  boolean showDurability,
  boolean showNewBadge,
  int nameMaxWidth,
  boolean showItemId,
  boolean raritySkins
) {
}
