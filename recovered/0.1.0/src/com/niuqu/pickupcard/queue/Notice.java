package com.niuqu.pickupcard.queue;

import com.niuqu.pickupcard.anim.Easing;
import com.niuqu.pickupcard.anim.Timeline;
import com.niuqu.pickupcard.pickup.Pickup;
import net.minecraft.Util;
import net.minecraft.world.item.ItemStack;

public final class Notice {
  private final ItemStack stack;
  private int count;
  private long bornAt;
  private final boolean firstTime;
  private long pulseAt = -9223372036854775808L;
  private float targetOffset;
  private float currentOffset = 0.0F / 0.0F;
  private int rollFrom;
  private int rollTo;
  private long rollStart = -9223372036854775808L;

  Notice(Pickup pickup, long now) {
    this.stack = pickup.stack();
    this.count = pickup.amount();
    this.bornAt = now;
    this.firstTime = pickup.firstTime();
  }

  public boolean firstTime() {
    return this.firstTime;
  }

  public static Notice preview(ItemStack stack, int count, boolean firstTime) {
    return new Notice(new Pickup(stack, count, firstTime), Util.m_137550_());
  }

  public ItemStack stack() {
    return this.stack;
  }

  public int count() {
    return this.count;
  }

  public long bornAt() {
    return this.bornAt;
  }

  public float targetOffset() {
    return this.targetOffset;
  }

  public float currentOffset() {
    return this.currentOffset;
  }

  public boolean hasCurrentOffset() {
    return !Float.isNaN(this.currentOffset);
  }

  public void setCurrentOffset(float value) {
    this.currentOffset = value;
  }

  public void setTargetOffset(float value) {
    this.targetOffset = value;
  }

  void merge(int extra, long now) {
    this.rollFrom = this.count;
    this.rollTo = this.count + extra;
    this.rollStart = now;
    this.count = this.rollTo;
    this.bornAt = now;
    this.pulseAt = now;
  }

  boolean sameKindAs(ItemStack other) {
    return ItemStack.m_150942_(this.stack, other);
  }

  public long pulseAt() {
    return this.pulseAt;
  }

  public boolean isRolling() {
    return this.rollStart != -9223372036854775808L;
  }

  public int rollFrom() {
    return this.rollFrom;
  }

  public int rollTo() {
    return this.rollTo;
  }

  public long rollStart() {
    return this.rollStart;
  }

  public int displayCount(long now, int rollMs, Easing easing) {
    if (this.rollStart == -9223372036854775808L) {
      return this.count;
    } else {
      int value = Timeline.rollValue(this.rollFrom, this.rollTo, now - this.rollStart, rollMs, easing);
      if (now - this.rollStart >= rollMs) {
        this.endRoll();
      }

      return value;
    }
  }

  void endRoll() {
    this.rollStart = -9223372036854775808L;
  }
}
