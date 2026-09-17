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
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null && !player.isSpectator() && player.isAlive()) {
        if (packet.getPlayerId() == player.getId()) {
          ItemStack stack = PickupStackResolver.resolve(level, packet);
          if (!stack.isEmpty()) {
            Pickup pickup = new Pickup(stack, packet.getAmount(), SeenItems.markSeen(stack));
            if (pickup.isValid()) {
              if (ClientConfig.passesFilter(pickup)) {
                NoticeQueue.INSTANCE.enqueue(pickup, Util.getMillis());
              }
            }
          }
        }
      }
    }
  }
}
