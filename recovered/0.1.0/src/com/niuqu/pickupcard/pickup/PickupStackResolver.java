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
    if (level != null && level.m_6815_(packet.m_133524_()) instanceof ItemEntity itemEntity) {
      ItemStack stack = itemEntity.m_32055_();
      if (!stack.m_41619_()) {
        return stack.m_41777_();
      }
    }

    Item item = Item.m_41445_(packet.m_133524_());
    return item == null ? ItemStack.f_41583_ : new ItemStack(item, packet.m_133528_());
  }
}
