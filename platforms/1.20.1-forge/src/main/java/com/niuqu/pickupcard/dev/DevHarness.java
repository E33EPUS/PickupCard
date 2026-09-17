package com.niuqu.pickupcard.dev;

import com.niuqu.pickupcard.PickupCard;
import com.niuqu.pickupcard.config.PickupCardConfig;
import com.niuqu.pickupcard.config.PickupCardConfigScreen;
import com.niuqu.pickupcard.layout.LayoutSettings;
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
        /**
         * 展开到一半时先拍一张 —— 只看"已经就位"的稳态，等于没验动画。
         * <p>
         * 【为什么是 5 tick】入场 2026-09-17 从 800ms 压到 480ms（内容 96→403ms），
         * 原来那 8 tick（400ms）拍到的**已经是终态**了 —— 一张"验动画"的图拍到终态，
         * 就等于没验。5 tick ≈ 250ms 落在内容窗口的中段。
         */
        private static final int MID_SHOT = 5;
        /** 刚起步的一帧（2 tick ≈ 100ms）：内容刚起跑，专门验"隧道口"有没有真裁在竖条右侧。 */
        private static final int EARLY_SHOT = 2;
        /** 开屏后等入场动画播完再拍。 */
        private static final int SHOT_AFTER_OPEN = 40;
        /** 截图是异步落盘的，给它足够时间再退出。 */
        private static final int QUIT_AFTER_SHOT = 40;
        /**
         * 退场淡出：稳态那张拍完之后再推一张把最老的挤掉，隔 3 tick（约 150ms）拍中段。
         * <p>
         * 【为什么非要多拍这一张】只看稳态的截图，等于没验过任何动画 —— 退场这条以前只有
         * 12px 位移、没有淡出，而"到底淡没淡"在静止的图上根本看不出来。拍早了那张还没开始
         * 淡，拍晚了它已经下线，所以这两个数是朝着 320ms 的中段取的。
         */
        private static final int EXIT_PUSH_AFTER = SHOT_AFTER_OPEN + 2;
        private static final int EXIT_SHOT_AFTER = EXIT_PUSH_AFTER + 3;

        private static final List<List<CardFixtures.Fixture>> PAGES = CardFixtures.pages();
        /** 卡样例页之后的矢量 spike 页（不画卡，只画图元与外壳探针）。 */
        private static final int SPIKE_PAGE = PAGES.size();
        /**
         * 最后一页：测量页。纯黑底、无辅助线、无读数 —— 给像素对照一张干净的输入。
         * <p>
         * 【为什么它是一页而不是"截图时顺手关掉"】对照的输入必须是可重复的：
         * 同一组卡、同一个底、同一个状态。做成页面，它就有名字、有日志、有产物。
         */
        private static final int MEASURE_PAGE = PAGES.size() + 1;
        private static final int TOTAL_PAGES = PAGES.size() + 2;

        private static DevCardScreen screen;

        private static int ticks;
        private static int page = -1;
        private static int sinceInject = -1;

        private AutoDrive() {
        }

        static boolean enabled() {
            return !"off".equalsIgnoreCase(MODE);
        }

        /** {@code -PharnessAuto=hud}：不打开调试屏，走玩家真正走的那条路。 */
        private static final boolean HUD_ONLY = "hud".equalsIgnoreCase(MODE);
        private static int hudTicks;
        private static boolean hudInjected;

        /** {@code -PharnessAuto=config}：打开配置界面 → 截图 → 退出。界面能不能画出来要能被验。 */
        private static final boolean CONFIG_ONLY = "config".equalsIgnoreCase(MODE);
        private static int configTicks;

        static void tick(Minecraft mc) {
            if (!enabled()) return;
            if (HUD_ONLY) {
                tickHud(mc);
                return;
            }
            if (CONFIG_ONLY) {
                tickConfig(mc);
                return;
            }
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
            if (sinceInject == EARLY_SHOT) {
                // 【为什么还要更早的一帧】入场刚起步那几 tick 才是"隧道口"唯一说了算的时刻：
                // 内容还大幅偏左，有没有裁剪、裁剪线在不在竖条右侧，只有这时候看得出来。
                // 中段那帧（MID_SHOT）是按"入场播到一半"挑的，而那正是曲线把内容推得
                // 差不多到位的时候 —— 拍出来两张几乎一样，等于没验。
                if (page != MEASURE_PAGE) capture(mc, "early");
            } else if (sinceInject == MID_SHOT) {
                // 测量页不拍中途：展开到一半的卡几何是变的，量出来的数没有意义
                if (page != MEASURE_PAGE) capture(mc, "mid");
            } else if (sinceInject == SHOT_AFTER_OPEN) {
                capture(mc, null);
            } else if (sinceInject >= SHOT_AFTER_OPEN + QUIT_AFTER_SHOT) {
                advance(mc);
            }
        }

        static void tickConfig(Minecraft mc) {
            configTicks++;
            if (configTicks == WARMUP_TICKS) {
                mc.setScreen(new PickupCardConfigScreen(null));
                PickupCard.LOGGER.info("[harness-auto] 配置界面已打开");
                return;
            }
            if (configTicks == WARMUP_TICKS + 20) {
                capture(mc, null);
                return;
            }
            // 【为什么不直接改配置值】那样只能证明"配置→渲染"通，证明不了"按钮→配置"通。
            // 这里发的是**真实鼠标事件**（mouseClicked → 按钮 → 写配置 → 渲染重读），
            // 把整条链一起验掉 —— handoff 待办里那条"配置界面没点过"就是它。
            if (configTicks == WARMUP_TICKS + 26) {
                PickupCard.LOGGER.info("[harness-auto] 第 1 页控件: {}", configLabels(mc));
                clickByLabel(mc, "布局");                // 切到「布局」页
                PickupCard.LOGGER.info("[harness-auto] 点『布局』页控件: {}", configLabels(mc));
                return;
            }
            if (configTicks == WARMUP_TICKS + 40) {
                capture(mc, "p2");
                return;
            }
            if (configTicks == WARMUP_TICKS + 46) {
                LayoutSettings.Side before = PickupCardConfig.layoutSnapshot().stickTo();
                clickByLabel(mc, "贴边");
                LayoutSettings.Side after = PickupCardConfig.layoutSnapshot().stickTo();
                PickupCard.LOGGER.info("[harness-auto] 点『贴边』：{} → {}（变了才算这条链通）",
                        before, after);
                return;
            }
            if (configTicks >= WARMUP_TICKS + 56) {
                PickupCard.LOGGER.info("[harness-auto] 配置界面模式收工，退出客户端");
                mc.stop();
            }
        }

        /**
         * 按标签找控件、点它的中心。
         * <p>
         * 【为什么不按"屏幕比例"点】第一版写成 {@code height * 1.0}，正好落在按钮下沿之外 ——
         * 点击静默地什么都没发生，日志里只看到"值没变"，跟"功能坏了"长得一模一样。
         * 按标签找 + 用它自己的 bounds，换分辨率、换列宽都不用改，点不中还会现形（找不到就报）。
         */
        private static void clickByLabel(Minecraft mc, String label) {
            if (!(mc.screen instanceof PickupCardConfigScreen screen)) return;
            for (var child : screen.children()) {
                if (child instanceof net.minecraft.client.gui.components.AbstractWidget w
                        && w.getMessage().getString().contains(label)) {
                    screen.mouseClicked(w.getX() + w.getWidth() / 2.0,
                            w.getY() + w.getHeight() / 2.0, 0);
                    return;
                }
            }
            PickupCard.LOGGER.warn("[harness-auto] 界面上找不到『{}』这个控件", label);
        }

        /** 界面上现在有哪些控件（按标签）。日志里留一份 —— 截图看不出"第 2 页到底有没有那几项"。 */
        private static String configLabels(Minecraft mc) {
            if (!(mc.screen instanceof PickupCardConfigScreen screen)) return "(不是配置界面)";
            return screen.children().stream()
                    .filter(net.minecraft.client.gui.components.AbstractWidget.class::isInstance)
                    .map(net.minecraft.client.gui.components.AbstractWidget.class::cast)
                    .map(w -> w.getMessage().getString())
                    .collect(java.util.stream.Collectors.joining(" | "));
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
                screen.setMeasure(false);
                screen.setSpike(true);
                PickupCard.LOGGER.info("[harness-auto] 第 {}/{} 页: 矢量 spike", page + 1, TOTAL_PAGES);
            } else if (page == MEASURE_PAGE) {
                screen.setSpike(false);
                screen.setMeasure(true);
                for (CardFixtures.Fixture fixture : CardFixtures.measure()) {
                    CardFixtures.inject(fixture);
                }
                PickupCard.LOGGER.info("[harness-auto] 第 {}/{} 页: 测量页（纯黑底/无辅助线/无读数）",
                        page + 1, TOTAL_PAGES);
            } else {
                screen.setMeasure(false);
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

        /**
         * HUD 模式：注入样例，然后**什么都不做**，等 HUD 自己画。
         * <p>
         * 【为什么非要有这么一条】调试屏与 HUD 共用 {@link CardStage#renderInto}，
         * 但**触发路径不同**：一个是 {@code Screen.render}，一个是 Forge 的 GUI 渲染事件。
         * 共用同一个方法不等于共用同一条路径 —— "调试屏里好好的、玩家那边一张卡都没有"
         * 正好就是这个差别，而上面那些自动截图全都走的调试屏那条路，永远测不到。
         */
        private static void tickHud(Minecraft mc) {
            hudTicks++;
            if (!hudInjected) {
                if (hudTicks < WARMUP_TICKS) return;
                if (mc.getOverlay() != null || mc.screen != null || mc.level == null) return;
                CardFixtures.clear();
                for (CardFixtures.Fixture fixture : PAGES.get(0)) {
                    CardFixtures.inject(fixture);
                }
                PickupCard.LOGGER.info("[harness-auto] HUD 模式：注入 {} 张样例，不开调试屏",
                        PAGES.get(0).size());
                hudInjected = true;
                hudTicks = WARMUP_TICKS;
                return;
            }
            int age = hudTicks - WARMUP_TICKS;
            if (age == SHOT_AFTER_OPEN) {
                CardStage.Stats s = CardStage.INSTANCE.stats();
                PickupCard.LOGGER.info("[harness-auto] HUD 读数 cards={} painted={} layout={}us",
                        s.live(), s.painted(), s.layoutMicros());
                // 在屏的是哪几张：「少了我的那张卡」是最常见的问题，不能靠推断
                PickupCard.LOGGER.info("[harness-auto] HUD 在屏: {}", CardStage.INSTANCE.lastSlots()
                        .stream().map(slot -> slot.view().key())
                        .collect(java.util.stream.Collectors.joining(", ")));
                Screenshot.grab(mc.gameDirectory, "pickupcard-hud", mc.getMainRenderTarget(),
                        m -> PickupCard.LOGGER.info("[harness-auto] 截图: pickupcard-hud -> {}",
                                m.getString()));
            } else if (age == EXIT_PUSH_AFTER) {
                // 再推一张：账本上限 5，这一张会挤掉最老的那张 —— 正好把"退场淡出"拍下来。
                // 挑的是别页的样例（钻石），跟这一页那五件都不是同一样东西：同一样东西会被
                // 合并窗口并进已有的卡里，那就根本轮不到淘汰，等着看退场只会拍到一张没动静的图。
                CardFixtures.Fixture extra = CardFixtures.all().get(6);
                CardFixtures.inject(extra);
                PickupCard.LOGGER.info("[harness-auto] 推第 6 张（{}）触发退场", extra.label());
            } else if (age == EXIT_SHOT_AFTER) {
                CardStage.Stats s = CardStage.INSTANCE.stats();
                PickupCard.LOGGER.info("[harness-auto] 退场中读数 cards={} painted={}",
                        s.live(), s.painted());
                PickupCard.LOGGER.info("[harness-auto] HUD 在屏: {}", CardStage.INSTANCE.lastSlots()
                        .stream().map(slot -> slot.view().key())
                        .collect(java.util.stream.Collectors.joining(", ")));
                Screenshot.grab(mc.gameDirectory, "pickupcard-hud-exit", mc.getMainRenderTarget(),
                        m -> PickupCard.LOGGER.info("[harness-auto] 截图: pickupcard-hud-exit -> {}",
                                m.getString()));
            } else if (age >= SHOT_AFTER_OPEN + QUIT_AFTER_SHOT) {
                PickupCard.LOGGER.info("[harness-auto] HUD 模式收工，退出客户端");
                mc.stop();
            }
        }

        /** 产物名跟着页面走：测量页的名字必须一眼看得出是测量页，不是"某张截图"。 */
        private static String shotName(String suffix) {
            // 用 if 而不是 switch：SPIKE_PAGE 是 PAGES.size()，不是编译期常量，
            // 编译不过 —— case 标签要求常量表达式。
            String base;
            if (CONFIG_ONLY) {
                base = "pickupcard-config";
            } else if (page == SPIKE_PAGE) {
                base = "pickupcard-harness-spike";
            } else if (page == MEASURE_PAGE) {
                base = "pickupcard-measure";
            } else {
                base = "pickupcard-harness-p" + (page + 1);
            }
            return base + (suffix == null ? "" : "-" + suffix);
        }

        private static void capture(Minecraft mc, String suffix) {
            CardStage.Stats stats = CardStage.INSTANCE.stats();
            // 这张读数就是"客观门"里的第一道：卡有没有真的进到绘制阶段、排布花了多久。
            // 它进日志而不是只进截图，是因为数字比像素可靠。
            String keys = CardStage.INSTANCE.lastSlots().stream()
                    .map(slot -> slot.view().key())
                    .collect(java.util.stream.Collectors.joining(", "));
            long now = System.currentTimeMillis();
            String ages = CardStage.INSTANCE.lastSlots().stream()
                    .map(slot -> (now - slot.view().notice().bornAt()) + "ms")
                    .collect(java.util.stream.Collectors.joining(", "));
            PickupCard.LOGGER.info("[harness-auto] 读数 cards={} painted={} layout={}us fps={} guiScale={}",
                    stats.live(), stats.painted(), stats.layoutMicros(), mc.getFps(),
                    mc.getWindow().getGuiScale());
            PickupCard.LOGGER.info("[harness-auto] 主题 enterMs={} 最新卡展开进度={} 各卡卡龄=[{}]",
                    stats.enterMs(), String.format(java.util.Locale.ROOT, "%.2f", stats.firstRise()), ages);
            // 活下来的是哪几张：淘汰顺序不能靠推断，得看数据
            PickupCard.LOGGER.info("[harness-auto] 在屏: {}", keys);
            String name = shotName(suffix);
            Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(),
                    message -> PickupCard.LOGGER.info("[harness-auto] 截图: {} -> {}", name, message.getString()));
        }
    }
}
