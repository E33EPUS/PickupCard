package com.niuqu.pickupcard.queue;

public final class StackSmoother {
  private static final double RATE = 16.0;
  private static final double SNAP_EPSILON = 0.05;

  private StackSmoother() {
  }

  public static double approach(double current, double target, double dtSeconds) {
    if (dtSeconds <= 0.0) {
      return current;
    } else {
      double next = current + (target - current) * (1.0 - Math.exp(-dtSeconds * 16.0));
      return Math.abs(target - next) < 0.05 ? target : next;
    }
  }
}
