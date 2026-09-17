package com.niuqu.pickupcard.queue;

import com.niuqu.pickupcard.config.ClientConfig;
import com.niuqu.pickupcard.pickup.Pickup;
import java.util.List;

public final class NoticeMerger {
  private NoticeMerger() {
  }

  public static boolean tryMerge(List<Notice> active, Pickup pickup, long now) {
    if (!ClientConfig.mergeEnabled()) {
      return false;
    } else {
      long window = ClientConfig.mergeWindowMs();

      for (Notice notice : active) {
        if (now - notice.bornAt() <= window && notice.sameKindAs(pickup.stack())) {
          notice.merge(pickup.amount(), now);
          return true;
        }
      }

      return false;
    }
  }
}
