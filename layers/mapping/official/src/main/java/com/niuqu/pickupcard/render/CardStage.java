package com.niuqu.pickupcard.render;

import com.niuqu.pickupcard.PickupCard;
import com.niuqu.pickupcard.layout.CardMove;
import com.niuqu.pickupcard.layout.HudSafeZone;
import com.niuqu.pickupcard.layout.LayoutSettings;
import com.niuqu.pickupcard.layout.StackLayout;
import com.niuqu.pickupcard.notice.PickupCardSettings;
import com.niuqu.pickupcard.pickup.Inbox;
import com.niuqu.pickupcard.render.nvg.NvgCardPainter;
import com.niuqu.pickupcard.style.CardTimeline;
import com.niuqu.pickupcard.style.StyleModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Score;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 渲染层的调度台：把账本事件变成屏幕上的卡，每帧把它们交给 {@link NvgCardPainter}。
 * <p>
 * 【它不做什么】这里<b>没有一笔绘制</b>，也<b>没有一条几何公式</b>：
 * <ul>
 *   <li>怎么画 → {@link NvgCardPainter}（全项目唯一的画法）</li>
 *   <li>卡多大 → {@link CardMetrics}</li>
 *   <li>卡在哪 → {@code shared} 里的 {@link StackLayout}</li>
 *   <li>主题从哪来 → {@link StyleSource}</li>
 *   <li>动画进度 → {@link CardCanvas} / {@link CardTimeline}</li>
 * </ul>
 * 它只剩两件必须在一处才能保持正确的事：<b>消费事件</b>与<b>驱动每帧</b>。
 * 上一版把上面这五件事连同绘制全塞进一个 436 行的类里，改任意一处都要先读懂全部。
 * <p>
 * 【为什么事件消费与每帧推进必须同处】账本"已经删掉了这张卡"与渲染层"该播退场了"
 * 是同一件事的两面，分到两个类里就会出现"A 删了 B 还不知道"的时序空窗。
 */
public final class CardStage {

    public static final CardStage INSTANCE = new CardStage();

    /** 距屏幕左边的留白（右缘对齐时也就是右边距）。 */
    public static final int MARGIN_X = 16;
    /**
     * 距屏幕<b>下边</b>的留白 —— <b>不再是常量</b>，由 {@link HudSafeZone#bottomInset()} 算出来。
     * <p>
     * 【这里踩过三次】前两次都在改一个魔数（16 → 52），每次都说"这次好了"，结果还是重叠：
     * 第一次漏了血量/护甲那一带，第二次漏了<b>手持物品名</b>那一行（{@code Gui#renderSelectedItemName}
     * 里 {@code y = screenHeight - 59}、<b>居中</b>、宽度随物品名变化）—— 它会横伸进右列，
     * 而 52 让最下面那张卡正好落在 H-52..H-72，整行相交。
     * <p>
     * 现在这个数从原版 HUD 的矩形推出来（数字与出处都在 {@link HudSafeZone}）：
     * <b>让到"手持物品名"那一行之上</b>，就等于让开了它下面所有行。
     * 代价：比原来多让 10px。240 高的画布上 5 张卡仍然放得下（178 里放 116）；
     * guiScale 5 的 144 高画布上放得下 3 张 —— 那一档"最多几张"是玩家可调的（配置界面里有）。
     */
    /** 插入序 = 从老到新，正好是排布要的顺序。 */
    private final Map<String, CardView> live = new LinkedHashMap<>();

    /** 换位置时的过渡（旧的被新卡顶上去）。纯逻辑在 shared 里，有已知答案钉着。 */
    private final CardMove move = new CardMove();
    private final List<Inbox.Event> pending = new ArrayList<>();
    private final StyleSource styles = new StyleSource();

    /**
     * 画法。**全项目只有这一个实现**（NanoVG 矢量）—— 2026-09-17 把 SDF 图层、原版整卡
     * 渲染、DOM 草稿当贴图那三条一起删了，理由（以及"内容为什么还在原版"）见
     * {@link NvgCardPainter} 的类注释。
     */
    private final NvgCardPainter painter = new NvgCardPainter();

    /** 布局设置来自 TOML；默认值让渲染层在没有 Forge 的情况下也能跑。 */
    private Supplier<LayoutSettings> layoutSource = LayoutSettings::defaults;

    /** 上一帧的排布结果与耗时，只给 harness 读。 */
    private List<CardSlot> lastSlots = List.of();
    private long layoutMicros;
    /** 上一帧主题里的入场时长与最新那张卡的展开进度，给 harness 读 —— 动画出问题时靠它定位。 */
    private long lastEnterMs;
    private float lastFirstRise = 1f;

    private CardStage() {
    }

    /**
     * 平台侧把主题与"玩家改过的外观项"接进来。配置界面改的就是这两样 ——
     * 界面不直接碰渲染，只改配置，渲染每秒重读一次。
     */
    public void setStyleSources(java.util.function.Supplier<com.niuqu.pickupcard.style.Theme> theme,
                                java.util.function.Supplier<com.niuqu.pickupcard.style.StyleOverrides> overrides) {
        styles.setThemeSource(theme);
        styles.setOverrideSource(overrides);
    }

    /** 配置界面预览用：当前生效的样式（主题 + 玩家改动过的项）。 */
    public StyleModel previewStyle() {
        return styles.current(System.currentTimeMillis());
    }

    /** 配置改完之后叫一下：下一次绘制立刻用新值，不用等那一秒的重读间隔。 */
    public void refreshStyle() {
        styles.invalidate();
    }

    /** 平台侧把布局配置接进来。 */
    public void setLayoutSource(Supplier<LayoutSettings> source) {
        this.layoutSource = source == null ? LayoutSettings::defaults : source;
    }

    /** 换世界/退出：屏上的卡、没消费的事件、缓存的主题一起清。与 {@link Inbox#reset()} 成对调用。 */
    public void clear() {
        live.clear();
        pending.clear();
        styles.invalidate();
        lastSlots = List.of();
        layoutMicros = 0L;
    }

    // ------------------------------------------------------------------
    // Forge 总线
    // ------------------------------------------------------------------

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        // 只在这里取事件：拾取发生在包处理时，而卡的生命周期按 tick 走。
        pending.addAll(Inbox.INSTANCE.tick());
    }

    @SubscribeEvent
    public void onHudRender(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        renderInto(event.getGuiGraphics(), mc);
    }

    /**
     * 消费事件 → 排布 → 交给 painter。**HUD 与 dev harness 共用这一条路径。**
     * <p>
     * 【为什么抽出来而不是让 harness 自己画一遍】harness 的全部价值在于它看到的东西
     * 与玩家看到的是同一份。如果 harness 自己走一条渲染路径，它就只能证明"那条路径"对，
     * 而上一版正是死在"harness 里没有的东西上了真机才现形"。
     */
    public void renderInto(GuiGraphics gui, Minecraft mc) {
        long now = System.currentTimeMillis();
        pump(now);
        PickupCardSettings settings = Inbox.INSTANCE.settingsSnapshot();

        // 【总开关】关掉就整条路都不走：屏上的卡立刻清、账本里的也一起忘掉。
        // 只"不再新弹"是不够的 —— 重新打开时那一堆旧卡会一起涌出来，像卡了半分钟。
        if (!settings.enabled()) {
            if (!live.isEmpty() || !pending.isEmpty()) {
                live.clear();
                pending.clear();
                Inbox.INSTANCE.reset();
                lastSlots = List.of();
            }
            return;
        }

        if (live.isEmpty()) {
            lastSlots = List.of();
            return;
        }

        StyleModel style = styles.current(now).sanitized();
        LayoutSettings layout = layoutSource.get().sanitized();
        // 本帧的缩放：手动档直接用玩家给的；自动档看"账本里这几张塞不塞得进 HUD 带之上"。
        // 用 live.size() 而不是"最终画出来的张数"是刻意的 —— 张数要先知道缩放才能定，
        // 反过来又要先知道张数，会绕成环；用账本张数最多让卡比必要的稍小一点。
        float scale = layout.scale(gui.guiHeight() - HudSafeZone.bottomInset(),
                style.boxHeight(), live.size(), layout.separation());
        CardCanvas canvas = new CardCanvas(now,
                new CardTimeline(style.enterMs(), style.bumpMs(), style.enterEnabled(), style.bumpEnabled()),
                style, settings, layout,
                gui.guiWidth(), gui.guiHeight(), scale);

        // 退场播完的摘掉，剩下的才参与排布
        live.values().removeIf(view -> {
            boolean done = view.exiting()
                    && CardTimeline.exit(now, view.exitStartAt(), settings.exitMs()) >= 1f;
            if (done) {
                Inbox.INSTANCE.forgetLeft(view.key());
            }
            return done;
        });

        // 淡回播完的复位 —— 不做这一步的话 exitStartAt 还挂着，它下一次退场会从半路开始
        for (CardView view : live.values()) {
            if (view.reviving() && canvas.reviveOf(view) >= 1f) {
                view.endRevive();
            }
        }
        if (live.isEmpty()) {
            lastSlots = List.of();
            return;
        }

        lastEnterMs = style.enterMs();
        lastFirstRise = live.isEmpty() ? 1f : canvas.contentOf(live.values().iterator().next());

        long t0 = System.nanoTime();
        List<CardSlot> slots = layout(canvas, mc);
        layoutMicros = (System.nanoTime() - t0) / 1_000L;
        lastSlots = List.copyOf(slots);
        painter.paint(gui, canvas, slots);
    }

    /** 消费积压的账本事件。 */
    private void pump(long now) {
        if (pending.isEmpty()) return;
        for (Inbox.Event e : pending) {
            absorb(e, now);
        }
        pending.clear();
    }

    /**
     * 上一帧画了哪些卡、量了多久。**只读遥测，没有写入口**——它存在是为了让 harness 能把
     * "看不见的状态"（每张卡的实际位置与尺寸）变成可读的，而不是为了让别处改渲染。
     */
    public record Stats(int live, int painted, long layoutMicros, long enterMs, float firstRise) {
    }

    public Stats stats() {
        return new Stats(live.size(), lastSlots.size(), layoutMicros, lastEnterMs, lastFirstRise);
    }

    /** 上一帧参与绘制的卡。辅助线要按这个画，才保证画的是"真的画了的那批"。 */
    public List<CardSlot> lastSlots() {
        return lastSlots;
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    /** 账本事件 → 屏幕上的卡。 */
    private void absorb(Inbox.Event event, long now) {
        if (event instanceof Inbox.Event.Added added) {
            // 【为什么要记这一笔】同一个 key 又"新增"了一张，说明屏幕上那张（可能正在淡出）
            // 会被**整张换掉**：新 CardView 的 exitStartAt = NO_EXIT，不透明度瞬间回到 1。
            // 用户报的「淡出最后一帧完全不透明，然后消失」只可能是这一类事件造成的，
            // 所以把它写成日志，让下一次复现自己带上证据。
            if (live.put(added.notice().key(), new CardView(added.notice())) != null) {
                PickupCard.LOGGER.info("[重挂] key={}：同名卡被整张替换（可能在淡出中）",
                        added.notice().key());
            }
        } else if (event instanceof Inbox.Event.Merged merged) {
            CardView view = live.get(merged.notice().key());
            if (view == null) {
                // 时序兜底：账本说有、渲染却没见过，按新卡补挂
                live.put(merged.notice().key(), new CardView(merged.notice()));
            } else {
                if (view.exiting()) {
                    // 合并撤销退场，但**不是瞬间回到全不透明**：CardView#beginRevive 记下起点，
                    // 之后 160ms 补回去（用户 2026-09-17 选的这一档）。同样进日志。
                    PickupCard.LOGGER.info("[救回] key={}：淡出改播淡回（{}ms 补回全不透明）",
                            merged.notice().key(), CardTimeline.REVIVE_MS);
                }
                view.absorbMerge(merged.notice(), now);
            }
        } else if (event instanceof Inbox.Event.Evicted evicted) {
            beginExit(evicted.notice().key(), now);
        } else if (event instanceof Inbox.Event.Expired expired) {
            beginExit(expired.notice().key(), now);
        }
    }

    private void beginExit(String key, long now) {
        CardView view = live.get(key);
        if (view != null) {
            view.beginExit(now);
        }
    }

    /** 量尺寸 → 排布 → 把两边按序拼起来。 */
    private List<CardSlot> layout(CardCanvas canvas, Minecraft mc) {
        // 【index 0 = 最新】草稿里新槽位是 unshift 到队首（slots[0] 即最新），StackLayout 按
        // 同一个约定收：最新那张贴着底线、旧的往上顶。live 是插入序（老 -> 新），所以反过来。
        List<CardView> alive = new ArrayList<>(live.values());
        Collections.reverse(alive);

        // 【取舍：屏幕放不下就先丢最老的】画布矮的时候（guiScale 4 的 320×180，让开 HUD 带
        // 之后只剩 105px）一摞 5 张是画到屏幕外面的 —— StackLayout 只做减法，从不检查 y 变负。
        // 丢的是最老的那几张，它们在丢的这一刻本来就在堆顶之上、画布之外，玩家看不见；
        // 而且是从 live 里**摘掉**而不是"这一帧不画"：留下来的话，等新卡走掉时它们会突然冒出来。
        float cardHeight = CardMetrics.height(canvas, mc.font);
        // 间距跟着缩放走：卡缩到 60% 而缝还是 4px 的话，一摞卡会显得"缝比卡还宽"
        float separation = canvas.layout().separation() * canvas.scale();
        int fits = StackLayout.fittingCount(canvas.guiHeight(), HudSafeZone.bottomInset(),
                cardHeight, separation);
        if (fits >= 1 && fits < alive.size()) {
            for (CardView dropped : new ArrayList<>(alive.subList(fits, alive.size()))) {
                live.remove(dropped.key());
                Inbox.INSTANCE.forgetLeft(dropped.key());   // 它不会再画了，账本那边也别留着
            }
            alive = new ArrayList<>(alive.subList(0, fits));
        }

        List<StackLayout.Size> sizes = new ArrayList<>(alive.size());
        float widest = 0f;
        for (CardView view : alive) {
            float width = CardMetrics.width(canvas, mc.font, view.notice().payload(), view.notice().count());
            widest = Math.max(widest, width);
            sizes.add(new StackLayout.Size(width, cardHeight));
        }

        // HUD 安全区：底部留白算出来；右侧再按"侧栏 / 状态效果图标"临时让开多少决定。
        float stackHeight = StackLayout.totalHeight(sizes, separation);
        float reserve = rightReserve(mc, canvas, widest, stackHeight);

        List<CardSlot> slots = new ArrayList<>(alive.size());
        long now = canvas.now();
        for (StackLayout.Slot slot : StackLayout.stack(
                sizes, canvas.guiWidth(), canvas.guiHeight(), canvas.layout(),
                MARGIN_X, HudSafeZone.bottomInset(), separation)) {
            CardView view = alive.get(slot.index());
            // 右侧有东西就把这一张整体左移 —— 位移只发生在需要它的那些帧
            float x = slot.x() - HudSafeZone.shiftLeft(slot.x(), slot.width(),
                    canvas.guiWidth(), reserve);
            slots.add(new CardSlot(view, x, move.y(view.notice().key(), slot.y(), now),
                    slot.width(), slot.height()));
        }
        move.retain(live.keySet());
        return slots;
    }

    /**
     * 右侧要让开多少：<b>计分板侧栏</b>与<b>状态效果图标</b> —— 两者都只在卡堆<b>真的碰到</b>
     * 它们时才让（判据在 {@link HudSafeZone#reserve}，带单测）。
     * <p>
     * 【为什么每一帧现问】它们是"有时才在、而且在屏幕中部"的东西：让多少由它们自己决定，
     * 就不是又一个魔数。数字与出处见 {@link HudSafeZone}（图标一行 26 高、一列 25 宽；侧栏宽度现量）。
     * <p>
     * 【为什么侧栏也要看纵向】从前这条是无条件的：画布 1080 高时卡堆在右下角（y≈890..1005）、
     * 侧栏在屏幕中部（y≈472..607），够不着却被整列推开 —— 低缩放档下就这样被推到快捷栏左边，
     * 正是用户报的「位置会变到物品栏左侧」。
     *
     * @param widestCard  本帧最宽的那张卡（用来还原卡堆矩形）
     * @param stackHeight 整摞卡的高度（含间隙）
     */
    private static float rightReserve(Minecraft mc, CardCanvas canvas, float widestCard,
                                      float stackHeight) {
        if (mc.level == null || stackHeight <= 0f || widestCard <= 0f) {
            return 0f;
        }
        // 卡堆这一帧（未左移时）实际占的矩形。左缘必须用 StackLayout 的同一条公式，
        // 否则会出现"判据说没碰上、画出来却压在一起"。
        float left = Math.max(0f, Math.min(canvas.layout().leftLimit(canvas.guiWidth()),
                canvas.guiWidth() - MARGIN_X - widestCard));
        HudSafeZone.Rect cards = new HudSafeZone.Rect(left,
                canvas.guiHeight() - HudSafeZone.bottomInset() - stackHeight, widestCard, stackHeight);

        // 1 = 侧栏槽位（原版 Gui#render 里就是这么取的：getDisplayObjective(1)）
        Objective sidebar = mc.level.getScoreboard().getDisplayObjective(1);
        HudSafeZone.Rect sidebarRect = null;
        float sidebarWidth = 0f;
        if (sidebar != null) {
            sidebarWidth = scoreboardWidth(mc.font, sidebar);
            sidebarRect = HudSafeZone.sidebar(canvas.guiWidth(), canvas.guiHeight(),
                    sidebarWidth, scoreboardLines(sidebar));
        }

        int beneficial = 0;
        int harmful = 0;
        if (mc.player != null) {
            for (var effect : mc.player.getActiveEffects()) {
                if (effect.getEffect().isBeneficial()) {
                    beneficial++;
                } else {
                    harmful++;
                }
            }
        }
        int columns = Math.max(beneficial, harmful);
        HudSafeZone.Rect effectRect = null;
        float effectWidth = 0f;
        if (columns > 0) {
            // 有害效果会再占一排（图标从 y=27 起）—— 所以图标带的下沿要看有没有那一排
            float effectsBottom = harmful > 0 ? 51f : 25f;
            effectWidth = HudSafeZone.EFFECT_COL_W * columns;
            effectRect = new HudSafeZone.Rect(canvas.guiWidth() - effectWidth, 1f,
                    effectWidth, effectsBottom - 1f);
        }

        return HudSafeZone.reserve(cards, sidebarRect, sidebarWidth, effectRect, effectWidth);
    }

    /** 侧栏会画几行（原版最多 15 行 —— 它决定那一竖条的纵向范围）。 */
    private static int scoreboardLines(Objective objective) {
        int lines = 0;
        for (Score ignored : objective.getScoreboard().getPlayerScores(objective)) {
            if (++lines >= 15) {
                break;
            }
        }
        return Math.max(1, lines);
    }

    /** 侧栏一行的实测宽度（原版是按"标题 / 条目+分数"的最宽那行算的，这里取个上界就够）。 */
    private static float scoreboardWidth(Font font, Objective objective) {
        int widest = font.width(objective.getDisplayName());
        int seen = 0;
        for (Score score : objective.getScoreboard().getPlayerScores(objective)) {
            if (++seen > 15) {
                break;      // 原版也只显示前 15 行
            }
            String owner = score.getOwner();
            if (owner == null || owner.startsWith("#")) {
                continue;
            }
            widest = Math.max(widest, font.width(owner) + 8 + font.width(Integer.toString(score.getScore())));
        }
        return widest;
    }

}
