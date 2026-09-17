package com.niuqu.pickupcard.anim;

import com.niuqu.pickupcard.queue.NoticePhase;

public record Motion(double alpha, double slide, double scale, double rotation, NoticePhase phase) {
  private static final double VISIBLE_ALPHA = 0.02;

  public boolean visible() {
    return this.phase != NoticePhase.DEAD && this.alpha > 0.02;
  }
}
