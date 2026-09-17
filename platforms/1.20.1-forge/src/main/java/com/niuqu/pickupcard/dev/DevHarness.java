package com.niuqu.pickupcard.dev;

import com.niuqu.pickupcard.PickupCard;
import com.niuqu.pickupcard.render.CardStage;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.commands.Commands;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * 调试屏的入口：一个按键（F9）、一条客户端命令（{@code /pickupcarddev}），
 * 以及一条无人值守的自动驱动（{@code -Dpickupcard.harness.auto=shot}）。
 * <p>
 * 【为什么只在开发环境注册】{@link #register} 只被开发环境分支调用（见 mod 入口）。
 * 这里的内部类<b>刻意不用</b> {@code @Mod.EventBusSubscriber} —— 那个注解会被
 * 自动扫描注册，做不到"只在 dev 生效"；手工 register 才真的把玩家端挡在外面。
 * <p>
 * 【为什么要有自动驱动】"编译过、单测绿"对渲染层是零证据——这个项目已经用三个真机
 * bug 换过这条教训。而"要有人坐在机器前按键才能验"同样是个瓶颈：它让验证没法进 CI，
 * 也没法在改动前后各跑一遍做对比。自动驱动把验证变成一条命令。
 */
public final class DevHarness {

    public static final KeyMapping OPEN = new KeyMapping(
            "key.pickupcard.harness", GLFW.GLFW_KEY_F9, "key.categories.misc");

    private DevHarness() {
    }

    /** 只在 {@code !FMLEnvironment.production} 时调用。 */
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
            AutoDrive.tick(mc);
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

    /**
     * 无人值守驱动：等客户端加载完 → 推全部样例 → 拍图 → 退出。
     * <p>
     * 由系统属性 {@code pickupcard.harness.auto} 开启，构建侧用
     * {@code ./gradlew runClient -PharnessAuto=shot} 转发。默认 {@code off}，
     * 所以平时它一行都不跑。
     */
    static final class AutoDrive {

        private static final String MODE = System.getProperty("pickupcard.harness.auto", "off");
        /** 等客户端把加载屏走完再动手；早于这个时刻开屏会拍到半张加载界面。 */
        private static final int WARMUP_TICKS = 80;
        /** 开屏后等入场动画播完再拍。 */
        private static final int SHOT_AFTER_OPEN = 40;
        /** 截图是异步落盘的，给它足够时间再退出。 */
        private static final int QUIT_AFTER_SHOT = 40;

        private static final List<List<CardFixtures.Fixture>> PAGES = CardFixtures.pages();
        /** 卡样例页之后的最后一页：形状层 spike（不画卡，只画矢量图元）。 */
        private static final int SPIKE_PAGE = PAGES.size();
        private static final int TOTAL_PAGES = PAGES.size() + 1;

        private static DevCardScreen screen;

        private static int ticks;
        private static int page = -1;
        private static int sinceInject = -1;

        private AutoDrive() {
        }

        static boolean enabled() {
            return !"off".equalsIgnoreCase(MODE);
        }

        static void tick(Minecraft mc) {
            if (!enabled()) return;
            ticks++;

            if (page < 0) {
                if (ticks < WARMUP_TICKS) return;
                if (mc.getOverlay() != null) return;
                if (mc.screen instanceof DevCardScreen) return;

                screen = new DevCardScreen();
                mc.setScreen(screen);
                PickupCard.LOGGER.info("[harness-auto] 已打开调试屏，共 {} 页，guiScale={}",
                        TOTAL_PAGES, mc.getWindow().getGuiScale());
                advance(mc);
                return;
            }

            sinceInject++;
            if (sinceInject == SHOT_AFTER_OPEN) {
                capture(mc);
            } else if (sinceInject >= SHOT_AFTER_OPEN + QUIT_AFTER_SHOT) {
                advance(mc);
            }
        }

        /** 切到下一页：清屏 → 注入 → 从头计时。页用完了就收工。 */
        private static void advance(Minecraft mc) {
            page++;
            if (page >= TOTAL_PAGES) {
                PickupCard.LOGGER.info("[harness-auto] 收工，退出客户端");
                mc.stop();
                return;
            }
            CardFixtures.clear();
            if (page == SPIKE_PAGE) {
                screen.setSpike(true);
                PickupCard.LOGGER.info("[harness-auto] 第 {}/{} 页: 形状层 spike", page + 1, TOTAL_PAGES);
            } else {
                screen.setSpike(false);
                for (CardFixtures.Fixture fixture : PAGES.get(page)) {
                    CardFixtures.inject(fixture);
                }
                PickupCard.LOGGER.info("[harness-auto] 第 {}/{} 页: {}", page + 1, TOTAL_PAGES,
                        PAGES.get(page).stream().map(CardFixtures.Fixture::label)
                                .collect(java.util.stream.Collectors.joining(", ")));
            }
            sinceInject = 0;
        }

        private static void capture(Minecraft mc) {
            CardStage.Stats stats = CardStage.INSTANCE.stats();
            // 这张读数就是"客观门"里的第一道：卡有没有真的进到绘制阶段、排布花了多久。
            // 它进日志而不是只进截图，是因为数字比像素可靠。
            String keys = CardStage.INSTANCE.lastSlots().stream()
                    .map(slot -> slot.view().key())
                    .collect(java.util.stream.Collectors.joining(", "));
            PickupCard.LOGGER.info("[harness-auto] 读数 cards={} painted={} layout={}us fps={} guiScale={}",
                    stats.live(), stats.painted(), stats.layoutMicros(), mc.getFps(),
                    mc.getWindow().getGuiScale());
            // 活下来的是哪几张：淘汰顺序不能靠推断，得看数据
            PickupCard.LOGGER.info("[harness-auto] 在屏: {}", keys);
            String name = "pickupcard-harness-p" + (page + 1);
            Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(),
                    message -> PickupCard.LOGGER.info("[harness-auto] 截图: {} -> {}", name, message.getString()));
        }
    }
}
