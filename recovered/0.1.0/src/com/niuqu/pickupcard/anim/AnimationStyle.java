package com.niuqu.pickupcard.anim;

public enum AnimationStyle {
  CLEAN(Easing.SNAPPY, Easing.SNAPPY, Easing.SMOOTH, 0.0, 0.0, 0.7, 0.9),
  SOFT(Easing.GLIDE, Easing.GLIDE, Easing.SMOOTH, 0.03, 0.03, 0.85, 1.0),
  SPRINGY(Easing.OVERSHOOT, Easing.GLIDE, Easing.OVERSHOOT, 0.055, 0.06, 1.0, 1.0),
  BOUNCY(Easing.OVERSHOOT, Easing.OVERSHOOT, Easing.BOUNCE, 0.09, 0.09, 1.35, 1.1);

  private final Easing enterCurve;
  private final Easing exitCurve;
  private final Easing pulseCurve;
  private final double settle;
  private final double shrink;
  private final double pulseScale;
  private final double tiltScale;

  private AnimationStyle(Easing enterCurve, Easing exitCurve, Easing pulseCurve, double settle, double shrink, double pulseScale, double tiltScale) {
    this.enterCurve = enterCurve;
    this.exitCurve = exitCurve;
    this.pulseCurve = pulseCurve;
    this.settle = settle;
    this.shrink = shrink;
    this.pulseScale = pulseScale;
    this.tiltScale = tiltScale;
  }

  public Easing enterCurve() {
    return this.enterCurve;
  }

  public Easing exitCurve() {
    return this.exitCurve;
  }

  public Easing pulseCurve() {
    return this.pulseCurve;
  }

  public double settle() {
    return this.settle;
  }

  public double shrink() {
    return this.shrink;
  }

  public double pulseScale() {
    return this.pulseScale;
  }

  public double tiltScale() {
    return this.tiltScale;
  }
}
