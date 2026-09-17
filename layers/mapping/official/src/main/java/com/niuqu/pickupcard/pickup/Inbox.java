package com.niuqu.pickupcard.pickup;

import com.niuqu.pickupcard.filter.FilterRules;
import com.niuqu.pickupcard.filter.FilterSettings;
import com.niuqu.pickupcard.filter.FilterSubject;
import com.niuqu.pickupcard.notice.Notice;
import com.niuqu.pickupcard.notice.NoticeQueue;
import com.niuqu.pickupcard.notice.PickupCardSettings;
import com.niuqu.pickupcard.notice.SeenItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 拾取的中枢账本：过滤 → 记账（合并/淘汰/NEW）→ 把"发生了什么"排成事件等渲染层来取。
 * <p>
 * 【为什么账本不直接画】账本与渲染的生命周期不同：拾取事件在包处理时到达，渲染每帧
 * 才发生一次。中间放一条事件队列，两侧就各自独立——换渲染方案（自绘/九宫格/别的框架）
 * 不用动这里，反过来改合并规则也不用碰渲染。
 * <p>
 * 【线程】{@link #offer} 的上游是原版包处理（已在主线程），{@link #tick} 的上游是客户端
 * tick，两者同线程，事件队列因此不需要任何同步。
 */
public final class Inbox {

    /** 经验卡的身份键：世界里只有一种经验，永远合并进同一张卡。 */
    public static final String XP_KEY = "experience";

    public static final Inbox INSTANCE = new Inbox();

    /** 一张卡的完整载荷：内容本体 + 过滤给的强调标记。 */
    public record Card(CardContent content, boolean emphasized) {
    }

    /** 一次拾取在账本上引起的变化。渲染层据此决定挂卡、改数字还是播退场。 */
    public sealed interface Event {
        record Added(Notice<Card> notice) implements Event {
        }

        record Merged(Notice<Card> notice) implements Event {
        }

        /** 被新卡顶掉或超出在屏上限：账本已没有它，渲染播完退场就摘。 */
        record Evicted(Notice<Card> notice) implements Event {
        }

        /** 停留超时自然退场。 */
        record Expired(Notice<Card> notice) implements Event {
        }
    }

    private final NoticeQueue<Card> queue = new NoticeQueue<>();
    private final SeenItems seen = new SeenItems();
    private final List<Event> pending = new ArrayList<>();

    private Supplier<PickupCardSettings> settingsSource = PickupCardSettings::defaults;
    private Supplier<FilterSettings> filterSource = FilterSettings::defaults;

    private Inbox() {
    }

    /**
     * 被过滤器丢弃时的回调。默认什么都不做。
     * <p>
     * 【为什么必须有一个】被过滤掉的拾取在玩家那边表现为"什么都没发生"，
     * 和"mod 坏了"完全分不出来 —— 实测就有人捡了一路圆石来问"怎么一张卡都不弹"。
     * 丢弃是<b>故意</b>的行为，但不能是<b>无声</b>的。平台层接上日志。
     */
    private volatile Consumer<CardContent.Item> dropReporter = item -> {
    };

    /** 平台侧接日志。传 null 就恢复成什么都不做。 */
    public void setDropReporter(Consumer<CardContent.Item> reporter) {
        this.dropReporter = reporter == null ? item -> {
        } : reporter;
    }

    /** 平台侧把配置接进来。账本只认函数，不认 Forge——测试可以给固定值。 */
    public void setSources(Supplier<PickupCardSettings> settings, Supplier<FilterSettings> filter) {
        this.settingsSource = settings == null ? PickupCardSettings::defaults : settings;
        this.filterSource = filter == null ? FilterSettings::defaults : filter;
    }

    private PickupCardSettings settings() {
        return settingsSource.get().sanitized();
    }

    /** 渲染层要读的几项设置（退场时长、数量格式）。渲染只读，不归它改。 */
    public PickupCardSettings settingsSnapshot() {
        return settings();
    }

    /**
     * 收下一次拾取（主线程）。
     *
     * @return true = 这条拾取的提示音应当被压制（静音名单命中；经验卡恒为 false）
     */
    public boolean offer(CardContent content, int amount) {
        int count = Math.max(1, amount);
        long now = System.currentTimeMillis();

        String key;
        String look;
        boolean emphasized;
        if (content instanceof CardContent.Item item) {
            FilterRules.Decision decision = FilterRules.check(
                    subjectOf(item.stack()), filterSource.get());
            if (!decision.show()) {
                // 丢弃前留个声：玩家的观感是"没反应"，而日志里必须能看出是它干的
                dropReporter.accept(item);
                return decision.muted();
            }
            key = ItemIdentity.keyOf(item.stack());
            look = ItemIdentity.lookOf(item.stack());
            emphasized = decision.emphasized();
        } else if (content instanceof CardContent.Experience) {
            // 经验不过滤：它是正反馈本身，也没有"捡错一堆"的刷屏问题
            key = XP_KEY;
            look = XP_KEY;
            emphasized = false;
        } else {
            return false;
        }

        boolean firstTime = seen.markAndCheckFirst(key);
        Card card = new Card(content, emphasized);
        NoticeQueue.Outcome<Card> outcome = queue.absorb(key, look, card, count, firstTime, now,
                settings().mergeEnabled(), settings().maxOnScreen());

        for (Notice<Card> evicted : outcome.evicted()) {
            pending.add(new Event.Evicted(evicted));
        }
        switch (outcome.change()) {
            case ADDED -> pending.add(new Event.Added(outcome.notice()));
            case MERGED -> pending.add(new Event.Merged(outcome.notice()));
            case EVICTED -> {
                // absorb 的顶替淘汰走上面那个列表；这个分支只在协议变动时才会出现
                pending.add(new Event.Evicted(outcome.notice()));
            }
        }
        return false;
    }

    /**
     * 渲染层把一张卡的退场播完了：账本这边也把「离开中」的记忘掉（此后再捡到同一个物品
     * 就是新的一张卡，而不是"救回一张已经不在屏幕上的卡"）。
     */
    public void forgetLeft(String key) {
        queue.forgetLeft(key);
    }

    /**
     * 客户端 tick：取走积累的事件，把到点的卡变成 {@link Event.Expired}。
     * 渲染层对 Expired 的卡播退场动画；账本这边它们已经不存在了。
     */
    public List<Event> tick() {
        long now = System.currentTimeMillis();
        List<Event> events = new ArrayList<>(pending);
        pending.clear();

        for (Notice<Card> expired : queue.sweep(now, settings().holdMs())) {
            events.add(new Event.Expired(expired));
        }
        return events;
    }

    /** 换世界/退出：队列、NEW 账本、未取走的事件一起回到"什么都没发生"。 */
    public void reset() {
        queue.clear();
        seen.clear();
        pending.clear();
    }

    private static FilterSubject subjectOf(ItemStack stack) {
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        Set<String> tags = new HashSet<>();
        // 1.20.1 上 Holder#tags() 返回 Stream：每次拾取现查一次，拾取是稀疏事件，不值得缓存
        stack.getItem().builtInRegistryHolder().tags()
                .forEach(tag -> tags.add(tag.location().toString()));
        return new FilterSubject(id.toString(), id.getNamespace(), tags);
    }
}
