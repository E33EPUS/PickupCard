package com.niuqu.pickupcard.layout;

public final class NoticeLayout {
  private NoticeLayout() {
  }

  public static double outwardPixels(int stackIndex, int entryHeight, int separation) {
    return (double)stackIndex * (entryHeight + separation);
  }

  public static NoticeLayout.Placement place(LayoutSpec spec, int screenWidth, int screenHeight, double outward, double slide) {
    int growthSign = spec.anchor().isBottom() ? -1 : 1;
    int slideSign = spec.anchor().isRight() ? 1 : -1;
    int slidePx = (int)Math.round(slide * spec.slideDistance()) * slideSign;
    int offsetPx = (int)Math.round(outward) * growthSign;
    int baseX = spec.anchor().anchorX(screenWidth, spec.entryWidth(), spec.marginX());
    int baseY = spec.anchor().anchorY(screenHeight, spec.entryHeight(), spec.marginY());
    return new NoticeLayout.Placement(baseX + slidePx, baseY + offsetPx);
  }

  public record Placement(int x, int y) {
  }
}
