package com.niuqu.pickupcard;

import com.niuqu.pickupcard.render.PickupOverlay;
import com.niuqu.pickupcard.render.SdfRectRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

@EventBusSubscriber(modid = "pickupcard", bus = Bus.MOD, value = Dist.CLIENT)
public final class PickupCardClient {
  private PickupCardClient() {
  }

  @SubscribeEvent
  public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
    event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "pickup_notices", PickupOverlay.INSTANCE);
  }

  @SubscribeEvent
  public static void onRegisterShaders(RegisterShadersEvent event) {
    SdfRectRenderer.register(event);
  }
}
