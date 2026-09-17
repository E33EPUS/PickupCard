package com.niuqu.pickupcard.anim;

import com.niuqu.pickupcard.queue.NoticePhase;

public final class Timeline {
  private Timeline() {
  }

  public static NoticePhase phaseOf(long ageMs, AnimationSpec spec) {
    if (ageMs < spec.enterMs()) {
      return NoticePhase.ENTER;
    } else if (ageMs < (long)spec.enterMs() + spec.holdMs()) {
      return NoticePhase.HOLD;
    } else {
      return ageMs < spec.totalMs() ? NoticePhase.EXIT : NoticePhase.DEAD;
    }
  }

  public static Motion sample(long ageMs, AnimationSpec spec) {
    NoticePhase phase = phaseOf(Math.max(0L, ageMs), spec);

    return switch (phase) {
      case ENTER -> {
        double progress = ratio(ageMs, spec.enterMs());
        double motion = spec.style().enterCurve().apply(progress);
        double settleProgress = clamp01((progress - 0.45) / 0.55);
        double settle = spec.style().settle() * Math.sin(3.141592653589793 * settleProgress);
        yield new Motion(Easing.SMOOTH.apply(progress), 1.0 - motion, 1.0 + settle, spec.enterTilt() * (1.0 - motion), phase);
      }
      case HOLD -> new Motion(1.0, 0.0, 1.0, 0.0, phase);
      case EXIT -> {
        long exitAge = ageMs - spec.enterMs() - spec.holdMs();
        double progress = ratio(exitAge, spec.exitMs());
        double motion = spec.style().exitCurve().apply(progress);
        yield new Motion(1.0 - Easing.SMOOTH.apply(progress), motion * 0.6, 1.0 - spec.style().shrink() * motion, -spec.enterTilt() * 0.4 * motion, phase);
      }
      case DEAD -> new Motion(0.0, 1.0, 1.0, 0.0, phase);
    };
  }

  public static double pulseScale(long sincePulseMs, AnimationSpec spec) {
    if (spec.pulseEnabled() && sincePulseMs >= 0L && sincePulseMs < spec.pulseMs()) {
      double p = ratio(sincePulseMs, spec.pulseMs());
      return 1.0 + Math.sin(p * 3.141592653589793) * spec.pulseStrength() * spec.style().pulseCurve().apply(p);
    } else {
      return 1.0;
    }
  }

  public static int rollValue(int from, int to, long sinceRollMs, int rollMs, Easing easing) {
    if (rollMs <= 0 || sinceRollMs >= rollMs) {
      return to;
    } else {
      return sinceRollMs <= 0L ? from : (int)Math.round(from + (to - from) * easing.apply(ratio(sinceRollMs, rollMs)));
    }
  }

  private static double clamp01(double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }

  private static double ratio(long value, long span) {
    if (span <= 0L) {
      return 1.0;
    } else {
      double p = (double)value / span;
      return Math.max(0.0, Math.min(1.0, p));
    }
  }
}
