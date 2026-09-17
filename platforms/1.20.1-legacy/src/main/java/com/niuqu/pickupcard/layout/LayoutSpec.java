package com.niuqu.pickupcard.layout;

public record LayoutSpec(
  ScreenAnchor anchor, GrowthDirection growth, int marginX, int marginY, int separation, int slideDistance, int entryWidth, int entryHeight
) {
}
