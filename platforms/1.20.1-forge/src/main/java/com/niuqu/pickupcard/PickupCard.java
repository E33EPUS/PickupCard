package com.niuqu.pickupcard;

import com.niuqu.pickupcard.config.PickupCardConfig;
import com.niuqu.pickupcard.filter.FilterRules;
import com.niuqu.pickupcard.pickup.CardContent;
import com.niuqu.pickupcard.dev.DevHarness;
import com.niuqu.pickupcard.render.CardStage;
import com.niuqu.pickupcard.pickup.Inbox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

    /** 已经报过"被丢弃"的物品 id，避免连捡一路圆石把日志刷爆。 */
    private static final Set<String> REPORTED_DROPS = ConcurrentHashMap.newKeySet();

    /**
     * 一条拾取被过滤器丢掉了 —— 日志里说清楚是谁干的、怎么放行。
     * <p>
     * 【为什么这条日志必须有】丢弃是<b>故意</b>的（内置忽略表就是用来挡刷屏物品的），
     * 但"故意"不等于"可以无声"。玩家看到的是"我捡了东西但什么都没弹"，
     * 而这句话和"mod 坏了"长得一模一样 —— 实测真有人捡了一路圆石来问这个。
     */
    private static void reportDroppedPickup(CardContent.Item item) {
        String id = BuiltInRegistries.ITEM.getKey(item.stack().getItem()).toString();
        if (!REPORTED_DROPS.add(id)) {
            return;
        }
        LOGGER.info("拾取 {} 没有弹卡：被过滤器丢弃了。", id);
        LOGGER.info("  内置忽略表当前包含 {}（可在 config/pickupcard-client.toml 的 [filter] 里"
                + "把 useDefaultIgnoreList 设为 false 关掉）。", FilterRules.builtinIgnore());
        LOGGER.info("  黑名单命中就删掉对应规则；想让某件物品无论如何都弹卡，加进 whitelist（白名单优先级最高）。");
        LOGGER.info("  这条每个物品只报一次。");
    }
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
        // 被过滤器丢掉的拾取在玩家那边就是"什么都没发生"。接上日志，每个物品只报一次。
        Inbox.INSTANCE.setDropReporter(PickupCard::reportDroppedPickup);
        CardStage.INSTANCE.setLayoutSource(PickupCardConfig::layoutSnapshot);

        MinecraftForge.EVENT_BUS.register(ClientLifecycle.class);
        MinecraftForge.EVENT_BUS.register(CardStage.INSTANCE);

        // 调试屏只活在开发环境：正式 jar 里这些类存在，但注册路径根本不会走到
        if (!FMLEnvironment.production) {
            DevHarness.register(context.getModEventBus());
            // 打这一行是为了"它到底注册上没有"在日志里可查——静默注册失败过一次的话，
            // 症状只是"按 F9 没反应"，没有任何线索
            LOGGER.info("调试屏已启用：F9 或 /pickupcarddev");
        }
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
