package com.niuqu.pickupcard.dev;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

/**
 * 调试屏的入口：一个按键（F9）加一条客户端命令（{@code /pickupcarddev}）。
 * <p>
 * 【为什么只在开发环境注册】{@link #register} 只被开发环境分支调用（见 mod 入口）。
 * 这里的两个内部类<b>刻意不用</b> {@code @Mod.EventBusSubscriber} —— 那个注解会被
 * 自动扫描注册，做不到"只在 dev 生效"；手工 register 才真的把玩家端挡在外面。
 * <p>
 * 【为什么两个入口都要】命令好在能写进脚本、能在别人机器上复现；按键好在快。
 * 迭代速度是这个工具的全部意义，所以快的那条不能省。
 */
public final class DevHarness {

    public static final KeyMapping OPEN = new KeyMapping(
            "key.pickupcard.harness", GLFW.GLFW_KEY_F9, "key.categories.misc");

    private DevHarness() {
    }

    /** 只在 {@code FMLEnvironment.isDevelopment()} 为真时调用。 */
    public static void register(IEventBus modBus) {
        modBus.register(ModEvents.class);
        MinecraftForge.EVENT_BUS.register(ForgeEvents.class);
    }

    static final class ModEvents {

        @SubscribeEvent
        static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN);
        }
    }

    static final class ForgeEvents {

        @SubscribeEvent
        static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            while (OPEN.consumeClick()) {
                if (mc.screen == null) {
                    mc.setScreen(new DevCardScreen());
                }
            }
        }

        @SubscribeEvent
        static void onClientCommands(RegisterClientCommandsEvent event) {
            event.getDispatcher().register(Commands.literal("pickupcarddev").executes(ctx -> {
                Minecraft.getInstance().setScreen(new DevCardScreen());
                return 1;
            }));
        }
    }
}
