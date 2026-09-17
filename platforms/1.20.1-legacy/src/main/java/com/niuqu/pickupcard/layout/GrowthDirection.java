package com.niuqu.pickupcard.layout;

public enum GrowthDirection {
  NATURAL,
  REVERSED;

  private GrowthDirection() {
  }

  public int stackIndex(int listIndex, int size) {
    return this == NATURAL ? size - 1 - listIndex : listIndex;
  }
}
