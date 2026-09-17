package com.niuqu.pickupcard.pickup;

import net.minecraft.world.item.ItemStack;

public record Pickup(ItemStack stack, int amount, boolean firstTime) {
  public boolean isValid() {
    return !this.stack.isEmpty() && this.amount > 0;
  }
}
