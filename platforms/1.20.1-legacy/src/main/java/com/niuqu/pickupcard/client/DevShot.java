package com.niuqu.pickupcard.client;

import com.niuqu.pickupcard.PickupCard;
import com.niuqu.pickupcard.config.ClientConfig;
import com.niuqu.pickupcard.configui.ConfigScreen;
import com.niuqu.pickupcard.pickup.Pickup;
import com.niuqu.pickupcard.queue.NoticeQueue;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.loading.FMLLoader;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = "pickupcard", bus = Bus.FORGE, value = Dist.CLIENT)
public final class DevShot {
  private static final boolean ENABLED = !FMLLoader.isProduction() && Boolean.getBoolean("pickupcard.devshot");
  private static final boolean CONFIG_SHOTS = ENABLED && Boolean.getBoolean("pickupcard.configshot");
  private static final int CONFIG_TABS = 4;
  private static final boolean FADE_SHOTS = ENABLED && Boolean.getBoolean("pickupcard.fadeshot");
  private static final long[] FADE_OFFSETS = new long[]{-420L, -360L, -300L, -240L, -180L, -120L, -60L, -20L, 40L, 120L};
  private static long fadeStart;
  private static int fadeIndex;
  private static boolean fadeSeeded;
  private static final long[] SHOT_AT_MS = new long[]{0L, 90L, 260L, 600L, 880L, 1400L, 3460L, 3900L};
  private static final String[] SHOT_NAMES = new String[]{
    "01-enter-start", "02-enter-half", "03-enter-done", "04-hold-stack", "05-merge-pulse", "06-after-merge", "07-exit-half", "08-gone"
  };
  private static final long MERGE_AT_MS = 700L;
  private static boolean firstTime = true;
  private static long configAt;
  private static int configTab;
  private static boolean seeded;
  private static boolean merged;
  private static long startAt;
  private static int shotIndex;
  private static boolean finished;

  private DevShot() {
  }

  @SubscribeEvent
  public static void onTick(ClientTickEvent event) {
    if (ENABLED && !finished && event.phase == Phase.END) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.level != null && minecraft.player != null) {
        long now = Util.getMillis();
        if (CONFIG_SHOTS) {
          runConfigShots(minecraft, now);
        } else if (minecraft.screen == null) {
          if (!seeded) {
            startAt = now + 1000L;
            seeded = true;
            pinWindow(minecraft);
          } else if (FADE_SHOTS) {
            runFadeShots(minecraft, now);
          } else {
            long age = now - startAt;
            if (age >= 0L) {
              if (age >= 0L && shotIndex == 0) {
                seed();
              }

              if (!merged && age >= 700L) {
                enqueue(new ItemStack(Items.DIAMOND, 32), now);
                merged = true;
              }

              if (shotIndex < SHOT_AT_MS.length && age >= SHOT_AT_MS[shotIndex]) {
                pinWindow(minecraft);
                grab(minecraft, SHOT_NAMES[shotIndex]);
                shotIndex++;
              }

              if (shotIndex >= SHOT_AT_MS.length) {
                PickupCard.LOGGER.info("[pickupcard] dev \u622a\u56fe\u6d41\u7a0b\u7ed3\u675f\uff0c\u5171 {} \u5f20", SHOT_AT_MS.length);
                finished = true;
                minecraft.stop();
              }
            }
          }
        }
      }
    }
  }

  private static void pinWindow(Minecraft minecraft) {
    long handle = minecraft.getWindow().getWindow();
    GLFW.glfwSetWindowMonitor(handle, 0L, 120, 80, 854, 480, -1);
    PickupCard.LOGGER.info("[pickupcard] \u7a97\u53e3\u5df2\u9489\u4e3a 854x480\uff08\u5168\u5c4f\u5df2\u5173\uff09");
  }

  private static void runFadeShots(Minecraft minecraft, long now) {
    if (!fadeSeeded) {
      enqueue(new ItemStack(Items.DIAMOND, 64), now);
      fadeStart = now;
      fadeSeeded = true;
    } else {
      long age = now - fadeStart;
      long total = ClientConfig.animationSpec().totalMs();
      if (fadeIndex < FADE_OFFSETS.length && age >= total + FADE_OFFSETS[fadeIndex]) {
        grab(minecraft, "fade-" + fadeIndex + "-age" + age);
        fadeIndex++;
      } else {
        if (fadeIndex >= FADE_OFFSETS.length && age > total + 500L) {
          PickupCard.LOGGER.info("[pickupcard] \u6de1\u51fa\u53d6\u8bc1\u7ed3\u675f\uff0c\u5171 {} \u5f20", FADE_OFFSETS.length);
          finished = true;
          minecraft.stop();
        }
      }
    }
  }

  private static void runConfigShots(Minecraft minecraft, long now) {
    if (now - configAt >= 600L) {
      configAt = now;
      if (configTab >= 4) {
        PickupCard.LOGGER.info("[pickupcard] \u914d\u7f6e\u754c\u9762\u622a\u56fe\u6d41\u7a0b\u7ed3\u675f\uff0c\u5171 {} \u5f20", 4);
        finished = true;
        minecraft.stop();
      } else if (minecraft.screen instanceof ConfigScreen screen) {
        grab(minecraft, "config-" + configTab);
        screen.onClose();
        configTab++;
      } else {
        ConfigScreen screen = new ConfigScreen(null);
        screen.showSection(configTab);
        minecraft.setScreen(screen);
      }
    }
  }

  private static void seed() {
    enqueue(damagedPickaxe(), Util.getMillis());
    enqueueLater(new ItemStack(Items.ELYTRA));
    enqueueLater(new ItemStack(Items.NETHER_STAR));
    enqueueLater(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
    enqueueLater(enchantedSword());
  }

  private static ItemStack damagedPickaxe() {
    ItemStack stack = new ItemStack(Items.IRON_PICKAXE);
    stack.setDamageValue(130);
    return stack;
  }

  private static ItemStack enchantedSword() {
    ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
    stack.enchant(Enchantments.SHARPNESS, 3);
    return stack;
  }

  private static void enqueueLater(ItemStack stack) {
    new Thread(() -> {
      try {
        Thread.sleep(60L);
      } catch (InterruptedException var2) {
        Thread.currentThread().interrupt();
        return;
      }

      Minecraft.getInstance().execute(() -> enqueue(stack, Util.getMillis()));
    }, "pickupcard-devshot").start();
  }

  private static void enqueue(ItemStack stack, long now) {
    NoticeQueue.INSTANCE.enqueue(new Pickup(stack, stack.getCount(), firstTime), now);
    firstTime = false;
  }

  private static void grab(Minecraft minecraft, String name) {
    Screenshot.grab(minecraft.gameDirectory, "pickup-" + name + ".png", minecraft.getMainRenderTarget(), component -> {});
    PickupCard.LOGGER.info("[pickupcard] shot {}", name);
  }
}
