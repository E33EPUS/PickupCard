package com.niuqu.pickupcard.queue;

import com.niuqu.pickupcard.anim.AnimationSpec;
import com.niuqu.pickupcard.anim.Timeline;
import com.niuqu.pickupcard.config.ClientConfig;
import com.niuqu.pickupcard.pickup.Pickup;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class NoticeQueue {
  public static final NoticeQueue INSTANCE = new NoticeQueue();
  private final List<Notice> active = new ArrayList<>();
  private final Deque<Pickup> pending = new ArrayDeque<>();

  private NoticeQueue() {
  }

  public void enqueue(Pickup pickup, long now) {
    if (!NoticeMerger.tryMerge(this.active, pickup, now)) {
      if (this.active.size() < ClientConfig.maxOnScreen()) {
        this.active.add(new Notice(pickup, now));
      } else if (this.pending.size() < ClientConfig.queueSize()) {
        this.pending.addLast(pickup);
      }
    }
  }

  public void tick(long now) {
    AnimationSpec spec = ClientConfig.animationSpec();
    this.active.removeIf(notice -> Timeline.phaseOf(now - notice.bornAt(), spec) == NoticePhase.DEAD);

    while (!this.pending.isEmpty() && this.active.size() < ClientConfig.maxOnScreen()) {
      Pickup next = this.pending.pollFirst();
      if (next != null) {
        this.active.add(new Notice(next, now));
      }
    }
  }

  public List<Notice> active() {
    return this.active;
  }

  public void clear() {
    this.active.clear();
    this.pending.clear();
  }
}
