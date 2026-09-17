package com.niuqu.pickupcard;

import com.mojang.logging.LogUtils;
import com.niuqu.pickupcard.config.ClientConfig;
import com.niuqu.pickupcard.configui.ConfigScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod("pickupcard")
public final class PickupCard {
  public static final String MOD_ID = "pickupcard";
  public static final Logger LOGGER = LogUtils.getLogger();

  public PickupCard() {
    ModLoadingContext.get().registerConfig(Type.CLIENT, ClientConfig.SPEC);
    if (FMLEnvironment.dist == Dist.CLIENT) {
      DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> PickupCard::registerConfigScreen);
    }
  }

  private static void registerConfigScreen() {
    ModLoadingContext.get().registerExtensionPoint(ConfigScreenFactory.class, () -> new ConfigScreenFactory((minecraft, parent) -> new ConfigScreen(parent)));
  }
}
