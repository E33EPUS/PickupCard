package com.niuqu.pickupcard.render;

import com.niuqu.pickupcard.layout.LayoutSettings;
import com.niuqu.pickupcard.layout.StackLayout;
import com.niuqu.pickupcard.notice.PickupCardSettings;
import com.niuqu.pickupcard.pickup.Inbox;
import com.niuqu.pickupcard.render.painter.TrioCardPainter;
import com.niuqu.pickupcard.style.CardTimeline;
import com.niuqu.pickupcard.style.StyleModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 渲染层的调度台：把账本事件变成屏幕上的卡，每帧把它们交给 {@link CardPainter}。
 * <p>
 * 【它不做什么】这里<b>没有一笔绘制</b>，也<b>没有一条几何公式</b>：
 * <ul>
 *   <li>怎么画 → {@link CardPainter}（可替换的插槽）</li>
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

    /** 距屏幕右/下边的留白。将来接配置（layout.anchor / margin）时从这里长出去。 */
    private static final int MARGIN_X = 16;
    private static final int MARGIN_Y = 16;
    /** 卡与卡之间的间隙。跟卡内间隙（style.gap）不是一回事，刻意分开。 */
    private static final float STACK_GAP = 4f;

    /** 插入序 = 从老到新，正好是排布要的顺序。 */
    private final Map<String, CardView> live = new LinkedHashMap<>();
    private final List<Inbox.Event> pending = new ArrayList<>();
    private final StyleSource styles = new StyleSource();

    /** 画法。换 UI 方案就是换这一个字段。 */
    private CardPainter painter = new TrioCardPainter();

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

    /** 平台侧把布局配置接进来。 */
    public void setLayoutSource(Supplier<LayoutSettings> source) {
        this.layoutSource = source == null ? LayoutSettings::defaults : source;
    }

    /** 换画法。UI 定案后由入口调用；不改的话就是骨架期的基线画法。 */
    public void setPainter(CardPainter painter) {
        if (painter != null) {
            this.painter = painter;
        }
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
        if (live.isEmpty()) {
            lastSlots = List.of();
            return;
        }

        StyleModel style = styles.current(now).sanitized();
        PickupCardSettings settings = Inbox.INSTANCE.settingsSnapshot();
        CardCanvas canvas = new CardCanvas(now,
                new CardTimeline(style.enterMs(), style.bumpMs(), style.enterEnabled(), style.bumpEnabled()),
                style, settings, layoutSource.get().sanitized(),
                gui.guiWidth(), gui.guiHeight());

        // 退场播完的摘掉，剩下的才参与排布
        live.values().removeIf(view -> view.exiting()
                && CardTimeline.exit(now, view.exitStartAt(), settings.exitMs()) >= 1f);
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
            live.put(added.notice().key(), new CardView(added.notice()));
        } else if (event instanceof Inbox.Event.Merged merged) {
            CardView view = live.get(merged.notice().key());
            if (view == null) {
                // 时序兜底：账本说有、渲染却没见过，按新卡补挂
                live.put(merged.notice().key(), new CardView(merged.notice()));
            } else {
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
        List<CardView> alive = new ArrayList<>(live.values());
        List<StackLayout.Size> sizes = new ArrayList<>(alive.size());
        for (CardView view : alive) {
            sizes.add(new StackLayout.Size(
                    CardMetrics.width(canvas, mc.font, view.notice().payload(), view.notice().count()),
                    CardMetrics.height(canvas, mc.font)));
        }

        List<CardSlot> slots = new ArrayList<>(alive.size());
        for (StackLayout.Slot slot : StackLayout.stack(
                sizes, canvas.guiWidth(), canvas.guiHeight(), canvas.layout(),
                MARGIN_X, MARGIN_Y, STACK_GAP)) {
            slots.add(new CardSlot(alive.get(slot.index()), slot.x(), slot.y(), slot.width(), slot.height()));
        }
        return slots;
    }
}
