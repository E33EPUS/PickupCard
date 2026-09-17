package com.niuqu.pickupcard.pickup;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class SeenItems {
  private static final Set<String> SEEN = new HashSet<>();

  private SeenItems() {
  }

  public static boolean markSeen(ItemStack stack) {
    return SEEN.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
  }

  public static boolean hasSeen(ItemStack stack) {
    return SEEN.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
  }

  public static int size() {
    return SEEN.size();
  }

  public static void clear() {
    SEEN.clear();
  }
}
