package com.niuqu.pickupcard.notice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 屏幕上那几张卡的账本：谁在、谁被合并、谁被挤掉。纯逻辑，不碰渲染、不碰时钟。
 * <p>
 * 【为什么自己做而不用 AUI 的 ToastManager】AUI 自带的 Toast 是"一条消息一次课"，
 * 而拾取要的是【合并】—— 两秒内连捡 64 个钻石应该变成一张卡的数字在滚，不是弹 64 张。
 * 合并窗口、数量累加、上位淘汰这套规则是拾取特有的，得自己拿着。
 *
 * @param <T> 平台载荷类型（1.20.1 上是 ItemStack 副本）
 */
public final class NoticeQueue<T> {

    /** 一条卡的生命周期里发生了什么，交给调用方去同步 DOM。 */
    public enum Change {
        /** 新开一张卡：需要创建 DOM 节点并播入场。 */
        ADDED,
        /** 并进了已有卡：只需要把数量与代数写回 DOM。 */
        MERGED,
        /** 已有卡被挤掉（超出同时在屏数量）：需要播退场后移除。 */
        EVICTED
    }

    /**
     * 一次 {@link #absorb} 的结果。
     * <p>
     * 【为什么带着 evicted】队列内部会静默地把超限的卡挤掉，可 DOM 那侧得让被挤掉的
     * 那张播退场再移除。如果这里只回"新卡是谁"，被挤掉的那张就会永远留在屏幕上 ——
     * 账本和界面各说各话。淘汰结果必须跟着返回值一起出去。
     */
    public record Outcome<T>(Change change, Notice<T> notice, List<Notice<T>> evicted) {
        public Outcome {
            evicted = List.copyOf(evicted);
        }
    }

    private final Map<String, Notice<T>> alive = new LinkedHashMap<>();

    /**
     * 收下一次拾取。
     *
     * @param mergeEnabled 关掉合并时，每次拾取都是新卡
     * @param maxOnScreen  同时在屏上限；超出的按"最久没被碰过"淘汰
     * @return 发生的改动；被淘汰的那张卡会作为结果返回（调用方据此让 DOM 播退场）
     */
    public Outcome<T> absorb(String key, String lookKey, T payload, int amount,
                             boolean firstTime, long now,
                             boolean mergeEnabled, long windowMs, int maxOnScreen) {
        List<Notice<T>> evicted = new ArrayList<>();
        Notice<T> existing = alive.get(key);
        if (existing != null) {
            boolean sameLook = existing.lookKey().equals(lookKey);
            if (MergeWindow.shouldMerge(true, sameLook, now - existing.touchedAt(),
                    mergeEnabled ? windowMs : 0L)) {
                Notice<T> merged = existing.mergeInto(amount, now);
                alive.put(key, merged);
                return new Outcome<>(Change.MERGED, merged, evicted);
            }
            // 不能并：把旧卡顶掉，让位给新卡。视觉上就是"同一件东西又来了"，
            // 用新的出生时间重播入场比原地改数字更像是"又捡到了"。
            alive.remove(key);
            evicted.add(existing);
        }
        Notice<T> fresh = new Notice<>(key, lookKey, payload, amount, firstTime, now, now, 0);
        alive.put(key, fresh);
        evicted.addAll(evictOverflow(maxOnScreen));
        return new Outcome<>(Change.ADDED, fresh, evicted);
    }

    /**
     * 淘汰超出上限的卡。淘汰顺序 = 最久没被碰过的先走（LinkedHashMap 的插入序不够，
     * 因为合并会刷新一张老卡，它应该重新排到活着的末尾）。
     *
     * @return 被淘汰的卡，按淘汰先后排列；调用方按顺序让它们退场
     */
    private List<Notice<T>> evictOverflow(int maxOnScreen) {
        List<Notice<T>> evicted = new ArrayList<>();
        if (maxOnScreen <= 0) return evicted;
        while (alive.size() > maxOnScreen) {
            String victim = null;
            long oldest = Long.MAX_VALUE;
            for (Map.Entry<String, Notice<T>> entry : alive.entrySet()) {
                if (entry.getValue().touchedAt() < oldest) {
                    oldest = entry.getValue().touchedAt();
                    victim = entry.getKey();
                }
            }
            if (victim == null) break;
            Notice<T> removed = alive.remove(victim);
            if (removed != null) evicted.add(removed);
        }
        return evicted;
    }

    /** 到点该退场的卡（停留超时），从账本里摘掉并返回。 */
    public List<Notice<T>> sweep(long now, long holdMs) {
        List<Notice<T>> gone = new ArrayList<>();
        alive.entrySet().removeIf(entry -> {
            if (entry.getValue().expiredAt(now, holdMs)) {
                gone.add(entry.getValue());
                return true;
            }
            return false;
        });
        return gone;
    }

    public Optional<Notice<T>> find(String key) {
        return Optional.ofNullable(alive.get(key));
    }

    /** 当前在屏的卡，按"最久没被碰过"排前 —— DOM 顺序反过来就是"最新的在最上面"。 */
    public List<Notice<T>> snapshot() {
        List<Notice<T>> all = new ArrayList<>(alive.values());
        all.sort((a, b) -> Long.compare(a.touchedAt(), b.touchedAt()));
        return Collections.unmodifiableList(all);
    }

    public int size() {
        return alive.size();
    }

    public void clear() {
        alive.clear();
    }
}
