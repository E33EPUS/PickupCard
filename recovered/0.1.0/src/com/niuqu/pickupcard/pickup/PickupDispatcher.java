package com.niuqu.pickupcard.pickup;

import com.niuqu.pickupcard.config.ClientConfig;
import com.niuqu.pickupcard.queue.NoticeQueue;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public final class PickupDispatcher {
  private PickupDispatcher() {
  }

  public static void handle(@Nullable ClientLevel level, ClientboundTakeItemEntityPacket packet) {
    if (ClientConfig.enabled() && level != null) {
      LocalPlayer player = Minecraft.m_91087_().f_91074_;
      if (player != null && !player.m_5833_() && player.m_6084_()) {
        if (packet.m_133527_() == player.m_19879_()) {
          ItemStack stack = PickupStackResolver.resolve(level, packet);
          if (!stack.m_41619_()) {
            Pickup pickup = new Pickup(stack, packet.m_133528_(), SeenItems.markSeen(stack));
            if (pickup.isValid()) {
              if (ClientConfig.passesFilter(pickup)) {
                NoticeQueue.INSTANCE.enqueue(pickup, Util.m_137550_());
              }
            }
          }
        }
      }
    }
  }
}
