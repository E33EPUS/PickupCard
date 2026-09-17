package com.niuqu.pickupcard.anim;

public record AnimationSpec(int enterMs, int holdMs, int exitMs, AnimationStyle style, boolean pulseEnabled, int pulseMs, double pulseStrength, double enterTilt) {
  public int totalMs() {
    return this.enterMs + this.holdMs + this.exitMs;
  }

  public static AnimationSpec defaults() {
    return new AnimationSpec(320, 2600, 520, AnimationStyle.SPRINGY, true, 420, 0.18, 9.0);
  }
}
