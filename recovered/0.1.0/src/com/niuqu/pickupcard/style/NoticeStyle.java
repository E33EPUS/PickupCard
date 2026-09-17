package com.niuqu.pickupcard.style;

public enum NoticeStyle {
  CARD,
  MINIMAL;

  private NoticeStyle() {
  }

  public boolean hasPanel() {
    return this == CARD;
  }

  public boolean showsName() {
    return this == CARD;
  }
}
