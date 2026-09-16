package com.niuqu.pickupcard;

import com.niuqu.pickupcard.config.PickupCardConfig;
import com.niuqu.pickupcard.ui.PickupBoard;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pickup Card 的 Forge 入口。
 * <p>
 * 【这个 mod 是纯客户端的】它不注册任何方块/物品/网络包，只在客户端嗅探原版的拾取包，
 * 然后把结果交给 ApricityUI 画成一张卡。所以：服务端不用装，任何服务器都能用。
 * <p>
 * 【为什么加载期不做任何事】界面是懒创建的：第一次真的捡到东西时才建那张覆盖层。
 * 玩家如果整局没捡东西，AUI 那边一个页面都不会被解析 —— 启动开销为零。
 */
@Mod(PickupCard.MOD_ID)
public final class PickupCard {

    public static final String MOD_ID = "pickupcard";
    public static final Logger LOGGER = LoggerFactory.getLogger("PickupCard");

    public PickupCard(FMLJavaModLoadingContext context) {
        // 纯客户端 mod：专服上什么都不做，避免有人在服务端装了个客户端 mod 时抛一堆
        if (FMLEnvironment.dist != Dist.CLIENT) {
            LOGGER.info("Pickup Card 是客户端 mod，服务端不做任何事");
            return;
        }

        PickupCardConfig.register(context);

        // 配置是懒采样的：每次真的要用时才读，玩家改完配置不用重启
        PickupBoard.INSTANCE.setSettingsSource(PickupCardConfig::snapshot);

        MinecraftForge.EVENT_BUS.register(ClientLifecycle.class);
    }

    /**
     * 客户端生命周期：每 tick 推进一次队列（该退场的退场、退场播完的摘节点），
     * 断线时把账本与 DOM 清干净。
     * <p>
     * 【为什么用 tick 而不是自己起定时器】AUI 的节点操作必须在客户端线程，而且断线/换世界
     * 时游戏会停 tick —— 挂在 tick 上，这些状态自然就跟着停了，不需要额外的同步。
     */
    @Mod.EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    static final class ClientLifecycle {

        @SubscribeEvent
        static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            PickupBoard.INSTANCE.tick();
        }

        @SubscribeEvent
        static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            // 离开世界：队列、NEW 账本、DOM 一起清。NEW 不落盘是刻意的，这里就是"忘记"的时机。
            PickupBoard.INSTANCE.reset();
        }
    }
}
