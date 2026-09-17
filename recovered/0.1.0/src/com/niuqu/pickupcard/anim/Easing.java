package com.niuqu.pickupcard.anim;

public enum Easing {
  LINEAR,
  SMOOTH,
  SNAPPY,
  GLIDE,
  OVERSHOOT,
  BOUNCE;

  private Easing() {
  }

  public double apply(double t) {
    return switch (this) {
      case LINEAR -> t;
      case SMOOTH -> t * t * (3.0 - 2.0 * t);
      case SNAPPY -> 1.0 - Math.pow(1.0 - t, 3.0);
      case GLIDE -> t < 0.5 ? 4.0 * t * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 3.0) / 2.0;
      case OVERSHOOT -> {
        double c1 = 1.70158;
        double c3 = c1 + 1.0;
        yield 1.0 + c3 * Math.pow(t - 1.0, 3.0) + c1 * Math.pow(t - 1.0, 2.0);
      }
      case BOUNCE -> {
        if (t <= 0.0) {
          yield 0.0;
        } else if (t >= 1.0) {
          yield 1.0;
        } else {
          double c4 = 2.0943951023931953;
          yield Math.pow(2.0, -10.0 * t) * Math.sin((t * 10.0 - 0.75) * c4) + 1.0;
        }
      }
    };
  }
}
