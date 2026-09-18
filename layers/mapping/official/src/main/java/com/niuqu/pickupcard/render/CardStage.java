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
import net.minecraft.world.entity.HumanoidArm;
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
        // 【为什么先取再 pump】pump 会把账本事件变成屏幕上的卡，而"这次合并该救回还是该重播入场"
        // 要用到 exitMs 与 reviveMs —— 事件处理拿不到它们，判据就只能靠猜。
        PickupCardSettings settings = Inbox.INSTANCE.settingsSnapshot();
        StyleModel style = styles.current(now).sanitized();
        pump(now, settings, style);

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

        LayoutSettings layout = layoutSource.get().sanitized();
        CardCanvas canvas = place(gui, mc, now, style, settings, layout);

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
    private void pump(long now, PickupCardSettings settings, StyleModel style) {
        if (pending.isEmpty()) return;
        for (Inbox.Event e : pending) {
            absorb(e, now, settings, style);
        }
        pending.clear();
    }

    /**
     * 淡到多不透明以下就不再"救回"，改当新卡重播入场。
     * <p>【判据从哪来】用户 2026-09-18 报「卡片淡出的最后一帧，文字和图标还是会突然闪一下」。
     * 逐帧量下来它的成因是：救回的不透明度从"已经淡到哪儿"补回来，而在淡出末尾那个位置是
     * <b>alpha ≈ 0.01</b> —— 一张已经看不见的卡在 300ms 内冲回全不透明，屏幕上就是凭空冒出来
     * 一块，怎么调时长都还是闪。0.15 是"还看得出是同一张卡"的下限：低于它就等于换了一件东西，
     * 那本来就该是入场。
     */
    private static final float REVIVE_MIN_ALPHA = 0.15f;

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
    private void absorb(Inbox.Event event, long now, PickupCardSettings settings, StyleModel style) {
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
            } else if (view.exiting()
                    && CardTimeline.exitAlpha(now, view.exitStartAt(), settings.exitMs())
                            < REVIVE_MIN_ALPHA) {
                // 已经淡到几乎看不见：救回来就是"从无到有"，补得再慢也读成闪。
                // 换一张新卡重播入场 —— 玩家按下拾取键的那一刻，本来就该看见"东西进来了"。
                live.put(view.key(), new CardView(merged.notice()));
                PickupCard.LOGGER.info("[救回→新卡] key={}：淡到 alpha<{} 才被再次拾起，改播入场",
                        merged.notice().key(), REVIVE_MIN_ALPHA);
            } else {
                if (view.exiting()) {
                    // 合并撤销退场，但**不是瞬间回到全不透明**：CardView#beginRevive 记下起点，
                    // 之后 reviveMs（主题 --pc-revive-ms）补回去。同样进日志。
                    PickupCard.LOGGER.info("[救回] key={}：淡出改播淡回（{}ms 补回全不透明）",
                            merged.notice().key(), style.reviveMs());
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

    /**
     * 这一帧的落点：<b>缩放 + 右侧条带</b>。两遍算，因为两者互相依赖。
     * <ol>
     *   <li>先按<b>高度</b>定缩放（老规矩：摞太高就整体收小）；</li>
     *   <li>用这个缩放的画布量出每张卡的<b>未缩放宽度</b>，再看右侧条带装不装得下 ——
     *       装不下就按宽度再收一次（{@link LayoutSettings#widthScale}）；</li>
     *   <li>收到 {@link LayoutSettings#MIN_AUTO_PERCENT} 还是装不下，就<b>回退</b>：
     *       整列回到"HUD 带上方、用满屏宽"，也就是 2026-09-18 之前的行为。</li>
     * </ol>
     * <p>【为什么要回退，而不是硬塞】条带宽度是"快捷栏右缘 → 屏幕右边距"，常见画布上正好
     * 卡在"装得下一张卡"的边缘；小画布 / 左撇子玩家 / 右侧有侧栏时会掉到 60px 以下。
     * 那时把卡缩成一条缝（8px 的字变 4px）比换个位置更糟 —— 用户要的是"在物品栏右边"，
     * 不是"在物品栏右边但看不清"。
     * <p>【条带模式的两个后果】卡的<b>宽度上限</b>也跟着条带收（{@code CardMetrics#maxWidth}），
     * 于是长名字退化成已有的"截断 + 省略号"，而不是把卡缩小；而 {@code reserve}
     * （侧栏 / 状态效果图标）不再靠"整列左移"躲开 —— 左移会破坏条带的硬下限，
     * 改成从条带宽度里先扣掉。
     */
    private CardCanvas place(GuiGraphics gui, Minecraft mc, long now, StyleModel style,
                             PickupCardSettings settings, LayoutSettings layout) {
        boolean leftHanded = isLeftHanded(mc);
        lastGuiWidth = gui.guiWidth();

        // 【先量"不可再压的宽度"，再决定落在哪一档】这个数不含缩放、也不含名字
        // （名字是唯一能截掉的部分），所以它可以在定缩放之前就算出来 —— 于是
        // "条带装不装得下"这件事不必先假设一个缩放，也就不会绕成环。
        CardCanvas probe0 = newCanvas(now, style, settings, layout, gui, 1f, 0f);
        float fixedNatural = fixedNatural(
                newCanvas(now, style, settings, layout, gui, 1f, stripWidthGuess(gui, mc, leftHanded)), mc);
        float probeFixed = fixedNatural(probe0, mc);

        // 侧栏 / 状态效果图标要先扣，否则条带右缘还是错的
        HudSafeZone.Strip open = HudSafeZone.strip(gui.guiWidth(), gui.guiHeight(),
                MARGIN_X, 0f, leftHanded);
        // 【stackHeight 不能传 0】reserve 判的是"卡堆矩形与侧栏/图标<b>相交</b>"，
        // 高度为 0 的矩形永远不相交 —— 传 0 等于把这条规则静默关掉（侧栏会被压住而没人知道）。
        float cardH = style.boxHeight();
        float stackH = live.size() * cardH + Math.max(0, live.size() - 1) * layout.separation();
        // 条带里卡片可以停在 [左缘, 右缘−卡宽] 之间，所以送进去的是**整个条带**那一条：
        // 判断"会不会压到侧栏"看的是最靠右的那种摆法，用整条最保守也最诚实。
        HudSafeZone.Rect reach = new HudSafeZone.Rect(open.left(),
                gui.guiHeight() - open.bottomInset() - stackH, open.width(), stackH);
        float reserve = rightReserve(mc, probe0, reach);
        HudSafeZone.Strip strip = HudSafeZone.strip(gui.guiWidth(), gui.guiHeight(),
                MARGIN_X, reserve, leftHanded);

        // 【第二遍：让"卡宽上限"真的收到条带上】第一遍的探针没带条带，量到的是屏宽比例那一档
        // （427×0.45 = 192）—— 在那之下名字不会被截断，于是"卡比条带宽"会被误判成"该缩卡"，
        // 实测就是"按宽度收到 87%"，而按设计这里应该是**截名字、字号不动**
        // （数量与图标才是卡上要一眼读到的东西，缩字体比截名字更伤）。
        CardCanvas bounded = newCanvas(now, style, settings, layout, gui, 1f, strip.width());
        float widestNatural = widestNatural(bounded, mc);
        fixedNatural = fixedNatural(bounded, mc);

        String note;
        boolean on = true;
        if (layout.scalePercent() != LayoutSettings.AUTO_SCALE) {
            // 手动档：玩家说了算。装不下也不回退 —— 那是他自己设的值。
            on = fixedNatural * (layout.scalePercent() / 100f) <= strip.width();
            note = fmt("手动 %d%%：条带 %.0fpx，卡最宽 %.0fpx%s",
                    layout.scalePercent(), strip.width(), widestNatural,
                    on ? "" : "（装不下，按玩家设定照画）");
        } else if (fixedNatural > strip.width()) {
            // 【判据为什么是"固定部分"而不是一个缩放下限】名字是唯一能被截掉的部分，
            // 所以 fixed 就是"再怎么截也这么大"。条带比它还窄 = 连竖条+图标+数量都摆不下，
            // 只剩"把字缩小"这一条路 —— 而缩字比换个位置糟得多，于是回退。
            // 用 MIN_AUTO_PERCENT 当判据是错的：320×180 画布上条带 52px、fixed 62px，
            // 按 62/52 = 84% 缩放"能过"那个下限，结果是字号缩到 84% 而名字只剩一个省略号。
            on = false;
            note = fmt("条带 %.0fpx 连固定部分（%.0fpx）都放不下，回退到 HUD 带上方",
                    strip.width(), fixedNatural);
        } else {
            note = fmt("条带 x[%.0f,%.0f] 宽 %.0fpx（空条带 %.0fpx，让开右侧 %.0fpx），底部留白 %d",
                    strip.left(), strip.right(), strip.width(), open.width(), reserve,
                    HudSafeZone.STRIP_BOTTOM);
        }

        // 【底部留白跟着落点走】条带模式下卡片横向上已经避开快捷栏，不需要再让开底部那一整条
        // （75px）；回退档才要 —— 那时卡片横跨在 HUD 带上方，不让就会压上去。
        stripModeOn = on;
        lastBottomMargin = on ? HudSafeZone.STRIP_BOTTOM : HudSafeZone.bottomInset();
        lastStripWidth = on ? strip.width() : 0f;
        float scale = layout.scale(gui.guiHeight() - lastBottomMargin,
                style.boxHeight(), live.size(), layout.separation());

        if (!note.equals(stripNote)) {
            stripNote = note;
            PickupCard.LOGGER.info("[落点] {}；缩放 {}%；卡最宽 {}/{}px；{}", note,
                    Math.round(scale * 100f), Math.round(widestNatural), Math.round(fixedNatural),
                    leftHanded ? "左撇子：副手在右" : "右手：副手在左");
        }
        return newCanvas(now, style, settings, layout, gui, scale, lastStripWidth);
    }

    /** 定缩放之前先估一个条带宽（用空条带），只为了让"截名字"在第一遍探针里也生效。 */
    private float stripWidthGuess(GuiGraphics gui, Minecraft mc, boolean leftHanded) {
        return HudSafeZone.strip(gui.guiWidth(), gui.guiHeight(), MARGIN_X, 0f, leftHanded).width();
    }

    private static String fmt(String pattern, Object... args) {
        return String.format(java.util.Locale.ROOT, pattern, args);
    }

    /** 这一批卡里最宽的（未缩放单位）。 */
    private float widestNatural(CardCanvas canvas, Minecraft mc) {
        float widest = 0f;
        for (CardView view : live.values()) {
            widest = Math.max(widest, CardMetrics.naturalWidth(canvas, mc.font, view));
        }
        return widest;
    }

    /** 这一批卡里"不可再压"的宽度上限（未缩放单位）—— 名字截光也这么大。 */
    private float fixedNatural(CardCanvas canvas, Minecraft mc) {
        float fixed = 0f;
        for (CardView view : live.values()) {
            fixed = Math.max(fixed, CardMetrics.fixedWidth(canvas, mc.font, view));
        }
        return fixed;
    }

    private CardCanvas newCanvas(long now, StyleModel style, PickupCardSettings settings,
                                 LayoutSettings layout, GuiGraphics gui, float scale,
                                 float stripWidth) {
        return new CardCanvas(now,
                new CardTimeline(style.enterMs(), style.bumpMs(), style.enterEnabled(),
                        style.bumpEnabled()),
                style, settings, layout,
                gui.guiWidth(), gui.guiHeight(), scale, stripWidth);
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
        int fits = StackLayout.fittingCount(canvas.guiHeight(), lastBottomMargin,
                cardHeight, separation);
        if (fits >= 1 && fits < alive.size()) {
            for (CardView dropped : new ArrayList<>(alive.subList(fits, alive.size()))) {
                live.remove(dropped.key());
                Inbox.INSTANCE.forgetLeft(dropped.key());   // 它不会再画了，账本那边也别留着
                // 【为什么留一行】"少了我的那张卡"是最难猜的一类反馈：它可能是被同屏上限挤掉的、
                // 可能是被这里摘掉的（缩放之后仍然放不下）。日志里认领一下，别让人对着截图猜。
                PickupCard.LOGGER.info("[放不下] key={}：这一档画布连缩小的卡都塞不下 {} 张，摘掉最老的",
                        dropped.key(), fits);
            }
            alive = new ArrayList<>(alive.subList(0, fits));
        }

        List<StackLayout.Size> sizes = new ArrayList<>(alive.size());
        float widest = 0f;
        for (CardView view : alive) {
            float width = CardMetrics.width(canvas, mc.font, view);
            widest = Math.max(widest, width);
            sizes.add(new StackLayout.Size(width, cardHeight));
        }

        // HUD 安全区：底部留白算出来；右侧再按"侧栏 / 状态效果图标"临时让开多少决定。
        // 【回退档才用这里的 reserve】条带模式在 place() 里就已经把让位量从条带宽度里扣掉了，
        // 这里再左移一次会破坏条带的硬下限。
        float stackHeight = StackLayout.totalHeight(sizes, separation);
        float left = Math.max(0f, Math.min(canvas.layout().leftLimit(canvas.guiWidth()),
                canvas.guiWidth() - MARGIN_X - widest));
        float reserve = rightReserve(mc, canvas, new HudSafeZone.Rect(left,
                canvas.guiHeight() - lastBottomMargin - stackHeight, widest, stackHeight));

        List<CardSlot> slots = new ArrayList<>(alive.size());
        long now = canvas.now();
        for (StackLayout.Slot slot : StackLayout.stack(
                sizes, canvas.guiWidth(), canvas.guiHeight(), canvas.layout(),
                MARGIN_X, lastBottomMargin, separation, stripLeft(mc))) {
            CardView view = alive.get(slot.index());
            // 【条带模式下不再整列左移】条带的右缘在 place() 里已经扣过 reserve 了，
            // 这里再移一次会把卡片推到条带左缘之外 —— 那正是"不许再左"要禁止的。
            float x = stripModeOn ? slot.x()
                    : slot.x() - HudSafeZone.shiftLeft(slot.x(), slot.width(),
                            canvas.guiWidth(), reserve);
            slots.add(new CardSlot(view, x, move.y(view.notice().key(), slot.y(), now),
                    slot.width(), slot.height()));
        }
        move.retain(live.keySet());
        return slots;
    }

    /** 右侧条带的左缘（卡片左缘的硬下限）；回退档下是 0（不限制）。 */
    private float stripLeft(Minecraft mc) {
        return stripModeOn ? HudSafeZone.stripLeft(lastGuiWidth, isLeftHanded(mc)) : 0f;
    }

    /** 副手槽跟主手相反 —— 左撇子玩家的条带因此窄 29px，见 {@link HudSafeZone#hotbarRightHalf}。 */
    private static boolean isLeftHanded(Minecraft mc) {
        return mc.player != null && mc.player.getMainArm() == HumanoidArm.LEFT;
    }

    /**
     * 这一帧落在条带里吗（false = 回退档：用满屏宽、放在 HUD 带上方）。
     * <p>它同时是"卡片左缘硬下限生不生效"与"宽度上限收不收"的开关，所以必须与
     * {@code place()} 里那次判断是<b>同一个值</b> —— 两处各判一次就会出现
     * "宽度按条带收了、位置却按回退档摆"这种半吊子状态。
     */
    private boolean stripModeOn;
    /** 条带模式生效时本帧的条带宽（屏幕像素）；回退档是 0。 */
    private float lastStripWidth;
    /** 本帧生效的底部留白：条带档 {@link HudSafeZone#STRIP_BOTTOM}，回退档 {@link HudSafeZone#bottomInset()}。 */
    private int lastBottomMargin = HudSafeZone.bottomInset();
    /** 上一次落点变化时打过的日志（变了才打，不刷屏）。 */
    private String stripNote = "";
    /** 本帧画布宽（条带左缘按它算）。 */
    private int lastGuiWidth;

    /** 本帧的落点说明（只读诊断；harness 那行"安全区"日志末尾会带上它）。 */
    public String placementNote() {
        return stripNote;
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
     * @param cards 卡堆这一帧可能占到的矩形（调用方按本帧真实的落点算，别在这里再算一遍 ——
     *              从前这里复制了一份 StackLayout 的左缘公式，条带模式一上就与真实落点不符了）
     */
    private static float rightReserve(Minecraft mc, CardCanvas canvas, HudSafeZone.Rect cards) {
        if (mc.level == null || cards.h() <= 0f || cards.w() <= 0f) {
            return 0f;
        }

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
