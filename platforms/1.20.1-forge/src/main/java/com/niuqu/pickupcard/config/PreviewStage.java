package com.niuqu.pickupcard.config;

import com.niuqu.pickupcard.PickupCard;
import com.niuqu.pickupcard.layout.CardMove;
import com.niuqu.pickupcard.layout.LayoutSettings;
import com.niuqu.pickupcard.notice.Notice;
import com.niuqu.pickupcard.notice.PickupCardSettings;
import com.niuqu.pickupcard.pickup.CardContent;
import com.niuqu.pickupcard.pickup.Inbox;
import com.niuqu.pickupcard.render.CardCanvas;
import com.niuqu.pickupcard.render.CardMetrics;
import com.niuqu.pickupcard.render.CardSlot;
import com.niuqu.pickupcard.render.CardView;
import com.niuqu.pickupcard.render.nvg.NvgCardPainter;
import com.niuqu.pickupcard.render.nvg.ui.ConfigLayout;
import com.niuqu.pickupcard.style.CardTimeline;
import com.niuqu.pickupcard.style.StyleModel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * 预览舞台：配置界面预览面板里的卡，与拖拽编辑场共用的样例来源。
 *
 * <p>【为什么独立成类（2026-09-19 架构审计）】预览管线从前长在 1700 行的界面类里，
 * 和真卡管线（{@code CardStage}）平行，最要命的是<b>卡片身份当场现编</b>：同一样例的
 * 所有卡共用一个 key，而"换位平滑"（{@code CardMove}）按 key 记账 —— 舞台上同时有两张
 * 同款卡时，它们每帧互相抢同一个过渡目标，稳态是<b>所有同 key 卡永远钉在同一个 y 上</b>，
 * 这就是用户报的"预览卡片重叠"。现在每张卡出生时领唯一的号（{@code preview#序号}），
 * 平滑账本一本账管一张卡，重叠在结构上不可能再发生。
 *
 * <p>【节拍为什么改成"三并一新"】从前自动节拍发现队首同款就只并数字 —— 舞台永远只有
 * 一张卡，"三张真卡的自动舞台"名不副实，退场只能靠手动点爆（用户报的"还没退出动画"）。
 * 现在每四拍并一次（演示合并脉冲 + 数字滚动），其余开新卡：容量到了自然有老卡退场，
 * 动画页要调的入场→停留→消失一整条链自己会演完。
 */
public final class PreviewStage {

    /** 舞台节拍：每隔这么长时间来一拍（略放慢，看得清退场）。 */
    private static final long BEAT_MS = 1_700L;
    /** 开场后第一拍等多久。 */
    private static final long FIRST_BEAT_MS = 500L;
    /** 舞台同屏上限（不含正在退场的那张）。 */
    private static final int CAPACITY = 3;

    // ------------------------------------------------------------------
    // 样例：四张故意长得不一样的卡（预览与编辑场共用的一份内容）
    // ------------------------------------------------------------------

    /**
     * 【为什么不是一个固定的"经验卡"】排版问题只在特定内容下才露出来 —— 名字长到要截断、
     * 稀有度换颜色、微光、只有数字。给一个样例等于只验一种。
     * <p>【四格为什么正好是四个颜色】用户 2026-09-18 问过"预览里四个等级颜色都一样"——
     * 当年是样例物品选错了（钻石剑其实是 COMMON）。现在每格写死标称档位，
     * {@link #verify()} 启动时对一次，不符报 ERROR。
     */
    public enum Sample {
        COMMON("普通", Items.STONE, Rarity.COMMON, 64, false, null,
                "最常见的那一档 —— 灰。原版绝大多数物品都在这档（钻石剑、钻石镐也是）"),
        RARE("稀有", Items.GOLDEN_APPLE, Rarity.RARE, 1, false, null,
                "稀有档 —— 青。样例必须是真·稀有的物品：拿钻石剑当稀有样例只会得到一片灰"),
        XP("经验", Items.NETHER_STAR, null, 137, true, null,
                "经验卡：绿是它专用的一档，刻意不参与稀有度分级"),
        LONG_NAME("长名", Items.ENCHANTED_GOLDEN_APPLE, Rarity.EPIC, 1, false,
                "附魔金苹果（珍藏 · 来自末地城）", "史诗档 —— 紫；顺带验名字太长会被截断");

        final String label;
        /** 这一格的数量。<b>不是显示字符串</b> —— 写法由玩家选的 {@code CountFormat} 决定。 */
        final int amount;
        /** 值得给一层稀有度微光的卡（经验卡）。 */
        final boolean glow;
        final String hint;
        /** 这一格<b>标称</b>的稀有度；{@code null} = 不参与稀有度演示（经验卡）。 */
        private final Rarity tier;
        private final ItemStack icon;

        Sample(String label, Item item, Rarity tier, int amount, boolean glow,
               String customName, String hint) {
            this.label = label;
            this.tier = tier;
            this.amount = amount;
            this.glow = glow;
            this.hint = hint;
            this.icon = new ItemStack(item);
            if (customName != null) {
                // 玩家自己改过名的物品就长这样：名字长、还带符号
                this.icon.setHoverName(Component.literal(customName));
            }
        }

        /**
         * 启动时对一次：标称档位必须就是<b>原版读出来的</b>那一档。只在第一次用到时跑。
         */
        static void verify() {
            if (verifyDone) {
                return;
            }
            verifyDone = true;
            for (Sample s : values()) {
                if (s.tier != null && s.icon.getRarity() != s.tier) {
                    PickupCard.LOGGER.error("[样例] {} 标称 {}，实际读到的是 {}（{}）—— 预览的颜色会不对",
                            s.label, s.tier, s.icon.getRarity(),
                            BuiltInRegistries.ITEM.getKey(s.icon.getItem()));
                }
            }
        }

        private static boolean verifyDone;

        public String label() {
            return label;
        }

        public String hint() {
            return hint;
        }

        public int amount() {
            return amount;
        }

        /** 样例物品（{@code XP} 那格的真卡内容不是物品）。 */
        ItemStack icon() {
            return icon;
        }
    }

    /** 预览缩放：手动档照玩家的选择画；自动档在预览里恒为 100%（倍率取决于真实卡堆，预览面板算它只会骗人）。 */
    public static float scale() {
        int pct = PickupCardConfig.layoutSnapshot().scalePercent();
        return pct > LayoutSettings.AUTO_SCALE ? pct / 100f : 1f;
    }

    /** 一张"早已出生"的卡：入场已播完、无退场计划 —— 静止页与编辑场都用它。 */
    public static CardView settledView(Sample s) {
        return new CardView(notice(nextKey(), s, s.amount, System.currentTimeMillis() - 10_000L));
    }

    private static Notice<Inbox.Card> notice(String key, Sample s, int amount, long bornAt) {
        Inbox.Card payload = new Inbox.Card(
                s == Sample.XP ? new CardContent.Experience() : new CardContent.Item(s.icon()),
                s.glow);
        return new Notice<>(key, "preview", payload, amount, false, bornAt, bornAt, 0);
    }

    // ------------------------------------------------------------------
    // 舞台本体
    // ------------------------------------------------------------------

    /** 舞台上的一位：卡 + 它是哪个样例（合并演示要按样例认亲，不能按 key —— key 每张唯一）。 */
    private record Entry(CardView view, Sample sample) {
    }

    private final java.util.ArrayDeque<Entry> stage = new java.util.ArrayDeque<>();
    private final CardMove move = new CardMove();
    private final NvgCardPainter painter = new NvgCardPainter();
    /** 非动画页的那张静止完整卡（{@code playOnce} 换新重生）。 */
    private CardView staticCard;
    private boolean stageMode;
    /** 下一拍的时刻（{@code <0} = 还没开过场）。 */
    private long nextSpawnAt = -1L;
    private long beats;
    /** 身份发放计数：预览卡的 key 全项目唯一，平滑账本一本账只管一张卡。 */
    private static long seq;

    private static String nextKey() {
        return "preview#" + (++seq);
    }

    /**
     * 按页分工（2026-09-19 定案）：动画页跑三张真卡的自动舞台；其他页一张静止完整卡。
     * 离开动画页收舞台；回来时重新开场（节拍从头起）。
     */
    public void setStageMode(boolean stagePage, Sample sample) {
        if (stagePage) {
            if (!stageMode) {
                nextSpawnAt = -1L;
            }
        } else {
            stage.clear();
            move.retain(java.util.Set.of());
            staticCard = new CardView(notice(nextKey(), sample, sample.amount,
                    System.currentTimeMillis() - 10_000L));
        }
        stageMode = stagePage;
    }

    /** 「来一张」/ 点预览：动画页放一张走完整时间线；其他页把静止卡换成当前样例。 */
    public void playOnce(long now, Sample sample) {
        if (stageMode) {
            // 手动钮必出新卡：合并演示交给自动节拍
            spawn(now, sample, false);
            nextSpawnAt = now + BEAT_MS;
        } else {
            staticCard = new CardView(notice(nextKey(), sample, sample.amount,
                    System.currentTimeMillis() - 10_000L));
        }
    }

    /** 舞台的节拍：到点来一拍，退场播完的清掉。只在舞台模式下动。 */
    public void drive(long now, Sample sample) {
        if (!stageMode) {
            return;
        }
        if (nextSpawnAt < 0L) {
            nextSpawnAt = now + FIRST_BEAT_MS;
        }
        if (now >= nextSpawnAt) {
            beats++;
            // 每四拍并一次（演示脉冲 + 数字滚动），其余开新卡 —— 见类注释
            spawn(now, sample, beats % 4 == 0);
            nextSpawnAt = now + BEAT_MS;
        }
        long exitMs = PickupCardConfig.snapshot().exitMs();
        stage.removeIf(entry -> entry.view().exiting()
                && CardTimeline.exit(now, entry.view().exitStartAt(), exitMs) >= 1f);
    }

    /**
     * 往舞台放一拍。
     *
     * @param mergeDemo true = 队首是同款且没在退场时并进去（数字滚动演示）；
     *                  false = 必出新卡（手动「来一张」用 —— 从前点它"没反应"就是被并吞了）
     */
    private void spawn(long now, Sample sample, boolean mergeDemo) {
        Entry front = stage.peekFirst();
        if (mergeDemo && front != null && !front.view().exiting() && front.sample() == sample) {
            // 合并必须沿用 front 的 key：key 一换，平滑账本就认不出这张卡，位置会跳
            front.view().absorbMerge(notice(front.view().key(), sample,
                    front.view().notice().count() + 1, now), now);
            return;
        }
        stage.addFirst(new Entry(new CardView(notice(nextKey(), sample, sample.amount, now)), sample));
        // 超员的退场：最老那张开始消失，播完由 drive 清掉。beginExit 幂等，重复调用无害。
        while (stage.size() > CAPACITY) {
            Entry oldest = stage.peekLast();
            if (!oldest.view().exiting()) {
                oldest.view().beginExit(now);
            }
            if (stage.size() > CAPACITY + 1) {
                stage.removeLast();     // 消失时长被设得很长时别让尸体堆着
            } else {
                break;
            }
        }
    }

    // ------------------------------------------------------------------
    // 画
    // ------------------------------------------------------------------

    /**
     * 画这一帧的预览（动画页 = 舞台整摞；其他页 = 居中的静止卡）。
     *
     * @param area   预览面板里放卡的那块（{@code ConfigLayout#previewCard}）
     * @param canvas 逻辑画布宽/高（卡宽上限按它算 —— 与真卡同一把尺）
     */
    public void render(net.minecraft.client.gui.GuiGraphics gui, ConfigLayout.Rect area,
                       float canvasWidth, float canvasHeight, StyleModel style, long now) {
        if (stageMode) {
            renderStage(gui, area, canvasWidth, canvasHeight, style, now);
        } else if (staticCard != null) {
            renderStatic(gui, area, canvasWidth, canvasHeight, style, now);
        }
    }

    /**
     * 静止样例卡：<b>在面板里居中</b>。从前贴面板底角，面板又高又空 —— 卡像放丢了；
     * 居中之后它是一张"标本"，不是一摞卡的替身。
     */
    private void renderStatic(net.minecraft.client.gui.GuiGraphics gui, ConfigLayout.Rect area,
                              float canvasWidth, float canvasHeight, StyleModel style, long now) {
        float scale = scale();
        PickupCardSettings settings = panelSettings(style, scale, area.w(), canvasWidth, canvasHeight, now);
        float h = style.boxHeight() * scale;
        float w = Math.min(cardWidth(staticCard, style, scale, settings, canvasWidth, canvasHeight, now),
                area.w() - 6f);
        float x = area.x() + (area.w() - w) / 2f;
        float y = area.y() + (area.h() - h) / 2f;
        painter.paint(gui, previewCanvas(style, scale, settings, canvasWidth, canvasHeight, now),
                List.of(new CardSlot(staticCard, x, y, w, h)));
    }

    /**
     * 舞台落位：<b>贴面板底</b>（与真卡的底锚同构 —— 预览说"新卡出现在固定的一点"，它自己
     * 就得先做到），水平按锚线分数落位。堆里多张时向上生长；换位走 340ms 平滑，跟真卡一路。
     */
    private void renderStage(net.minecraft.client.gui.GuiGraphics gui, ConfigLayout.Rect area,
                             float canvasWidth, float canvasHeight, StyleModel style, long now) {
        LayoutSettings layout = PickupCardConfig.layoutSnapshot();
        float scale = scale();
        float gap = layout.separation() * scale;
        // 【名字截断跟着面板走，不跟整屏走】卡壳被面板夹窄后，名字的截断预算若还按
        // "屏宽 × 45%" 算，文字就会溢出卡壳、戳出面板
        PickupCardSettings settings = panelSettings(style, scale, area.w(), canvasWidth, canvasHeight, now);
        float fx = layout.anchorLeft(canvasWidth) / Math.max(1f, canvasWidth);
        float y = area.bottom();
        List<CardSlot> slots = new ArrayList<>(stage.size());
        List<String> keys = new ArrayList<>(stage.size());
        for (Entry entry : stage) {
            float h = style.boxHeight() * scale;
            float w = Math.min(cardWidth(entry.view(), style, scale, settings, canvasWidth, canvasHeight, now),
                    area.w() - 6f);
            float x;
            if (layout.align() == LayoutSettings.Side.RIGHT) {
                x = Math.max(1f, Math.min(fx * area.w() - w, area.w() - 7f - w));
            } else {
                x = Math.max(1f, Math.min(fx * area.w(), area.w() - 7f - w));
            }
            keys.add(entry.view().key());
            slots.add(new CardSlot(entry.view(), area.x() + x, move.y(entry.view().key(), y - h, now), w, h));
            y -= h + gap;
        }
        move.retain(new HashSet<>(keys));
        if (!slots.isEmpty()) {
            painter.paint(gui, previewCanvas(style, scale, settings, canvasWidth, canvasHeight, now), slots);
        }
    }

    // ------------------------------------------------------------------
    // 量卡：与真卡同一套公式，只把"名字预算"换成面板给的
    // ------------------------------------------------------------------

    /** 预览面板的名字截断预算：卡壳被面板夹窄后，字与壳必须说同一种话（同一把尺）。 */
    private PickupCardSettings panelSettings(StyleModel style, float scale, float areaW,
                                             float canvasWidth, float canvasHeight, long now) {
        float room = Math.max(24f, (areaW - 6f) / scale);
        CardCanvas probe = previewCanvas(style, scale, PickupCardConfig.snapshot(),
                canvasWidth, canvasHeight, now);
        int nameRoom = (int) Math.max(12f,
                room - CardMetrics.namelessWidth(probe, Minecraft.getInstance().font,
                        stage.isEmpty() ? 0 : stage.peekFirst().view().notice().count()));
        return withNameLimit(PickupCardConfig.snapshot(), nameRoom);
    }

    /** 一份快照的副本：名字宽度上限额外夹进面板给的预算（0 = 自动 → 直接用预算）。 */
    private static PickupCardSettings withNameLimit(PickupCardSettings s, int room) {
        int limit = s.nameMaxWidth() > 0 ? Math.min(s.nameMaxWidth(), room) : room;
        return new PickupCardSettings(s.holdMs(), s.exitMs(), s.mergeMode(), s.maxOnScreen(),
                s.queueSize(), s.countFormat(), s.enabled(), s.showItemName(), s.showItemId(),
                limit);
    }

    /** 一张样例在当前缩放下的实际宽度 —— 走真卡的 {@code CardMetrics}，不另写一份公式。 */
    private float cardWidth(CardView view, StyleModel style, float scale, PickupCardSettings settings,
                            float canvasWidth, float canvasHeight, long now) {
        return CardMetrics.naturalWidth(previewCanvas(style, scale, settings, canvasWidth, canvasHeight, now),
                Minecraft.getInstance().font, view) * scale;
    }

    /** 这一帧要用的画布 —— 缩放与主题都按界面上当前生效的值给，预览才不会说谎。 */
    private CardCanvas previewCanvas(StyleModel style, float scale, PickupCardSettings settings,
                                     float canvasWidth, float canvasHeight, long now) {
        return new CardCanvas(now,
                new CardTimeline(style.enterMs(), style.bumpMs(), style.enterEnabled(),
                        style.bumpEnabled()),
                style, settings, PickupCardConfig.layoutSnapshot(),
                (int) canvasWidth, (int) canvasHeight, scale);
    }

    // ------------------------------------------------------------------

    /** 给 harness 的读数：几张卡、各自画在哪个 y、key 是谁 —— 重叠与否只有数字能定案。 */
    public String stateDump() {
        if (!stageMode) {
            return "静止卡";
        }
        StringBuilder ys = new StringBuilder();
        StringBuilder keys = new StringBuilder();
        for (Entry entry : stage) {
            if (ys.length() > 0) {
                ys.append(", ");
                keys.append(", ");
            }
            keys.append(entry.view().key());
            Float drawn = move.drawnY(entry.view().key());
            ys.append(drawn == null ? "?" : String.format(Locale.ROOT, "%.1f", drawn));
        }
        return "舞台" + stage.size() + "张 y=[" + ys + "] 键=[" + keys + "]";
    }
}
