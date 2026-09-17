package com.niuqu.pickupcard.pickup;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public final class PickupStackResolver {
  private PickupStackResolver() {
  }

  public static ItemStack resolve(@Nullable ClientLevel level, ClientboundTakeItemEntityPacket packet) {
    if (level != null && level.getEntity(packet.getItemId()) instanceof ItemEntity itemEntity) {
      ItemStack stack = itemEntity.getItem();
      if (!stack.isEmpty()) {
        return stack.copy();
      }
    }

    Item item = Item.byId(packet.getItemId());
    return item == null ? ItemStack.EMPTY : new ItemStack(item, packet.getAmount());
  }
}
