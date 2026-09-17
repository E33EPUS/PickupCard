package com.niuqu.pickupcard.text;

public enum CountFormat {
  X_PREFIX,
  PLAIN,
  ABBREVIATED;

  private CountFormat() {
  }

  public String format(int count) {
    return switch (this) {
      case X_PREFIX -> "\u00d7" + count;
      case PLAIN -> Integer.toString(count);
      case ABBREVIATED -> abbreviate(count);
    };
  }

  private static String abbreviate(int value) {
    int abs = Math.abs(value);
    if (abs >= 1000000000) {
      return trim(value / 1.0E9) + "B";
    } else if (abs >= 1000000) {
      return trim(value / 1000000.0) + "M";
    } else {
      return abs >= 1000 ? trim(value / 1000.0) + "K" : Integer.toString(value);
    }
  }

  private static String trim(double value) {
    return value >= 100.0 ? String.format("%.0f", value) : String.format("%.1f", value);
  }
}
