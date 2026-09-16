package com.niuqu.pickupcard;

import com.niuqu.pickupcard.config.PickupCardConfig;
import com.niuqu.pickupcard.render.CardStage;
import com.niuqu.pickupcard.pickup.Inbox;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
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
 * 把结果记进账本（{@link Inbox}），渲染层每 tick 来取事件画卡。服务端不用装，任何
 * 服务器都能用。
 * <p>
 * 【为什么加载期不做渲染的事】账本与渲染都是懒活的：第一次真的捡到东西才有卡可画。
 * 玩家如果整局没捡东西，渲染路径一行都不会跑。
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
        Inbox.INSTANCE.setSources(PickupCardConfig::snapshot, PickupCardConfig::filterSnapshot);

        MinecraftForge.EVENT_BUS.register(ClientLifecycle.class);
        MinecraftForge.EVENT_BUS.register(CardStage.INSTANCE);
    }

    /**
     * 客户端生命周期：断线/换世界时把账本清干净。每 tick 的推进与 HUD 渲染都归
     * {@link CardStage} 管（事件消费与绘制必须在同一处才不会错位）。
     */
    @Mod.EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    static final class ClientLifecycle {

        @SubscribeEvent
        static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            // 离开世界：队列、NEW 账本、未取走的事件一起清。NEW 不落盘是刻意的，这里就是"忘记"的时机。
            Inbox.INSTANCE.reset();
            CardStage.INSTANCE.clear();
        }
    }
}
