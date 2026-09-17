package com.niuqu.pickupcard.layout;

public enum ScreenAnchor {
  TOP_LEFT,
  TOP_RIGHT,
  BOTTOM_LEFT,
  BOTTOM_RIGHT;

  private ScreenAnchor() {
  }

  public boolean isRight() {
    return this == TOP_RIGHT || this == BOTTOM_RIGHT;
  }

  public boolean isBottom() {
    return this == BOTTOM_LEFT || this == BOTTOM_RIGHT;
  }

  public int anchorX(int screenWidth, int entryWidth, int marginX) {
    return this.isRight() ? screenWidth - marginX - entryWidth : marginX;
  }

  public int anchorY(int screenHeight, int entryHeight, int marginY) {
    return this.isBottom() ? screenHeight - marginY - entryHeight : marginY;
  }
}
