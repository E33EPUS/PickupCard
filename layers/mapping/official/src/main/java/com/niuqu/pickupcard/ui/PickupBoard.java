package com.niuqu.pickupcard.ui;

import com.niuqu.pickupcard.notice.Notice;
import com.niuqu.pickupcard.notice.NoticeQueue;
import com.niuqu.pickupcard.notice.PickupCardSettings;
import com.niuqu.pickupcard.notice.SeenItems;
import com.niuqu.pickupcard.pickup.ItemIdentity;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.element.Item;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 把"队列里有哪几张卡"同步到 ApricityUI 的那张 HTML 覆盖层上。
 * <p>
 * 【分工】这个类只做搬运：决定该有哪些卡（{@link NoticeQueue}）、把每张卡写成 DOM 节点。
 * 卡片长什么样、怎么动，全在 {@code overlays/pickupcard/card.html} 与它的 CSS 里 ——
 * 改外观不用重编译 Java。这正是不用自写渲染层的意义：外观的迭代循环从"改 shader 重编译"
 * 变成"改 CSS 按 END 热重载"。
 * <p>
 * 【为什么每张卡都有个 DOM 引用表】队列用 key 记账，DOM 用节点记账，两边必须有稳定映射，
 * 否则合并时要"找到那张卡"就得每次遍历查询。表在 Document 重建或换世界时整体清掉。
 * <p>
 * 【一个 AUI 的坑，写在这里免得后人踩】{@code Element#append} / {@code insertBefore} 内部会
 * 调 {@code Element.init} 把通用 Element 换成注册类（SPAN→Span、ITEM→Item…），而换出来的是
 * <b>另一个实例</b>。所以凡是"插进 DOM 之后还要继续操作"的节点，必须先自己
 * {@code Element.init(...)} 拿到最终实例，不能拿着 createElement 的返回值当它已经在树里了。
 * <p>
 * 【线程】全部方法只能在客户端线程调用。{@link #offer} 的上游是原版包处理（已在主线程），
 * {@link #tick} 的上游是客户端 tick。
 */
public final class PickupBoard {

    public static final PickupBoard INSTANCE = new PickupBoard();

    /** 覆盖层页面路径（逻辑路径，相对 AUI 的资源根）。 */
    private static final String OVERLAY_PATH = "overlays/pickupcard/card.html";
    /** 卡片容器的 id，写在 HTML 里。 */
    private static final String LIST_ID = "pickupcard-list";

    private static final String CLASS_CARD = "pc-card";
    private static final String CLASS_LEAVING = "pc-leaving";
    private static final String CLASS_FRESH = "pc-fresh";
    private static final String CLASS_RARITY_PREFIX = "pc-rarity-";
    /** 史诗档：要额外挂一条底部符文虚线节点（见 card.css 的 .pc-runeline）。 */
    private static final String EPIC_CLASS = CLASS_RARITY_PREFIX + "epic";

    private final NoticeQueue<ItemStack> queue = new NoticeQueue<>();
    private final SeenItems seen = new SeenItems();
    private final Map<String, CardNodes> cards = new HashMap<>();
    /** 退场中的卡：节点还在，等动画播完再摘。value = 该摘掉的时刻。 */
    private final Map<String, Long> pendingRemoval = new HashMap<>();

    private SettingsSource settingsSource = PickupCardSettings::defaults;
    private Document document;

    private PickupBoard() {
    }

    /** 配置来源。平台侧实现它去读自己的 config，测试可以给个固定值。 */
    @FunctionalInterface
    public interface SettingsSource {
        PickupCardSettings get();
    }

    /** 让平台侧把配置接进来。队列与界面只认这个函数，不认 Forge。 */
    public void setSettingsSource(SettingsSource source) {
        this.settingsSource = source == null ? PickupCardSettings::defaults : source;
    }

    private PickupCardSettings settings() {
        return settingsSource.get().sanitized();
    }

    // ------------------------------------------------------------------
    // 入口
    // ------------------------------------------------------------------

    /** 收到一次拾取。上游已保证在主线程，且 stack 是我们持有的副本。 */
    public void offer(ItemStack stack, int amount) {
        if (stack.isEmpty()) return;
        PickupCardSettings cfg = settings();

        String key = ItemIdentity.keyOf(stack);
        String look = ItemIdentity.lookOf(stack);
        boolean firstTime = seen.markAndCheckFirst(key);
        long now = System.currentTimeMillis();

        NoticeQueue.Outcome<ItemStack> outcome =
                queue.absorb(key, look, stack, amount, firstTime, now,
                        cfg.mergeEnabled(), cfg.mergeWindowMs(), cfg.maxOnScreen());

        Document doc = ensureDocument();
        if (doc == null) return;

        // 先处理被挤掉的：它们占着位置，先让它们开始退场。漏掉这一步，被淘汰的卡会永远
        // 留在屏幕上（账本已经没有它了，界面还在画它）。
        for (Notice<ItemStack> evicted : outcome.evicted()) {
            beginLeave(evicted.key(), now, cfg);
        }

        switch (outcome.change()) {
            case ADDED -> mountCard(doc, outcome.notice(), cfg);
            case MERGED -> {
                updateExisting(doc, outcome.notice(), cfg);
                rescueFromLeaving(outcome.notice().key());
            }
            // absorb 的淘汰走上面那个列表，不会直接从 change 里回 EVICTED；留着以防协议变动
            case EVICTED -> beginLeave(outcome.notice().key(), now, cfg);
        }
    }

    /** 客户端 tick：到点的卡开始退场，退场播完的摘掉节点。 */
    public void tick() {
        long now = System.currentTimeMillis();
        PickupCardSettings cfg = settings();

        for (Notice<ItemStack> expired : queue.sweep(now, cfg.holdMs())) {
            beginLeave(expired.key(), now, cfg);
        }

        Iterator<Map.Entry<String, Long>> it = pendingRemoval.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> entry = it.next();
            if (now >= entry.getValue()) {
                unmountCard(entry.getKey());
                it.remove();
            }
        }
    }

    /** 换世界、退出时清干净：队列、账本、DOM 都要回到"什么都没发生"。 */
    public void reset() {
        queue.clear();
        seen.clear();
        pendingRemoval.clear();
        cards.clear();
        Document doc = document;
        document = null;
        if (doc != null && doc.isActive()) {
            Element list = doc.getElementById(LIST_ID);
            if (list != null) {
                for (Element child : List.copyOf(list.children)) {
                    child.remove();
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // DOM 同步
    // ------------------------------------------------------------------

    private Document ensureDocument() {
        Document doc = document;
        if (doc != null && doc.isActive()) return doc;

        doc = ApricityUI.createDocument(OVERLAY_PATH);
        if (doc == null) {
            // 资源缺失（页面没打进 jar）会走到这里。不抛异常：拾取提示坏掉不该拖垮游戏。
            return null;
        }
        // 原版 Screen 打开时普通 Overlay 会隐藏；拾取提示希望一直在。
        doc.setReloadPersistent(true);
        document = doc;
        // refresh / END 重载会重建整棵树，旧节点引用全部失效 —— 我们的表必须跟着清
        cards.clear();
        pendingRemoval.clear();
        return doc;
    }

    /** 新建一张卡的节点。入场动画由 CSS 在节点插入时自动播，Java 这边不驱动任何动画。 */
    private void mountCard(Document doc, Notice<ItemStack> notice, PickupCardSettings cfg) {
        Element list = doc.getElementById(LIST_ID);
        if (list == null) return;

        String rarity = rarityClass(notice.payload());

        Element card = create(doc, "div", cardClassName(rarity, notice.firstTime()));
        card.setAttribute("data-rarity", rarity);

        // 图标槽：<item> 是 AUI 的扩展元素，走 MC 的物品模型渲染（含附魔光效与耐久条）
        Element slot = create(doc, "div", "pc-slot");
        Element icon = create(doc, "item", "pc-icon");
        if (icon instanceof Item itemElement) {
            itemElement.setIngredientStack(notice.payload());
        }
        slot.append(icon);

        // 文本区：名字 + 可选的 NEW 角标
        Element info = create(doc, "div", "pc-info");
        Element name = create(doc, "span", "pc-name");
        name.setTextContent(displayNameOf(notice.payload()));
        info.append(name);
        if (notice.firstTime()) {
            Element badge = create(doc, "span", "pc-new");
            badge.setTextContent("NEW");
            info.append(badge);
        }

        Element count = create(doc, "span", "pc-count");
        count.setTextContent(cfg.countFormat().gain(notice.count()));

        card.append(slot);
        card.append(info);
        card.append(count);

        // 史诗档独有的底部符文虚线。装饰由 CSS 描述，这里只负责"给史诗档挂上那个节点" ——
        // 其余档位没有这个节点，所以 CSS 里那条规则不会命中任何东西。
        if (EPIC_CLASS.equals(rarity)) {
            card.append(create(doc, "div", "pc-runeline"));
        }

        // 新的插在最上面：最新的拾取离视线最近
        list.insertBefore(card, list.getFirstElementChild());
        cards.put(notice.key(), new CardNodes(card, name, count, rarity, notice.firstTime()));
    }

    /**
     * 合并：把数量换成新数字，并让它自己弹一下。
     * <p>
     * 【为什么换节点而不是改文本】CSS 动画在节点插入时播放。换一个新节点 = 白拿一次
     * "数字跳动"的反馈，不用 Java 侧维护任何状态。上一版靠比较时间戳判断要不要弹一下，
     * 时钟精度不够时会漏掉 —— 换成结构性的写法就没有这个问题。
     */
    private void updateExisting(Document doc, Notice<ItemStack> notice, PickupCardSettings cfg) {
        CardNodes nodes = cards.get(notice.key());
        if (nodes == null) return;

        Element fresh = create(doc, "span", "pc-count pc-bump");
        fresh.setTextContent(cfg.countFormat().gain(notice.count()));

        Element old = nodes.count();
        if (old.parentNode != null) {
            old.parentNode.insertBefore(fresh, old);
        }
        old.remove();
        cards.put(notice.key(), nodes.withCount(fresh));
    }

    /** 让一张卡开始退场：加类触发 CSS 的退场动画，并登记"什么时候可以摘"。 */
    private void beginLeave(String key, long now, PickupCardSettings cfg) {
        CardNodes nodes = cards.get(key);
        if (nodes == null) return;
        if (pendingRemoval.containsKey(key)) return;

        Element card = nodes.card();
        card.setClassName(cardClassName(nodes.rarityClass(), nodes.newBadge()) + " " + CLASS_LEAVING);
        pendingRemoval.put(key, now + cfg.exitMs());
    }

    /** 合并把一张正在退场的卡拉回来：撤掉退场类。 */
    private void rescueFromLeaving(String key) {
        if (pendingRemoval.remove(key) == null) return;
        CardNodes nodes = cards.get(key);
        if (nodes == null) return;
        nodes.card().setClassName(cardClassName(nodes.rarityClass(), nodes.newBadge()));
    }

    private void unmountCard(String key) {
        CardNodes nodes = cards.remove(key);
        if (nodes == null) return;
        nodes.card().remove();
    }

    // ------------------------------------------------------------------
    // 小工具
    // ------------------------------------------------------------------

    /**
     * 建一个元素并拿到【升级后的】实例。
     * <p>
     * 必须先 init 再往树上挂：挂的动作内部会做同样的替换，届时我们手里的引用就指向
     * 一棵不在树上的旧对象了（改它没有任何效果，而且不报错）。
     */
    private static Element create(Document doc, String tagName, String className) {
        Element element = Element.init(doc.createElement(tagName));
        element.setClassName(className);
        return element;
    }

    /** 卡片类名 = 基础类 + 外观档位 +（可选）首次标记。三个维度正交，CSS 里各管各的。 */
    private static String cardClassName(String rarityClass, boolean fresh) {
        StringBuilder sb = new StringBuilder(CLASS_CARD);
        sb.append(' ').append(rarityClass);
        if (fresh) sb.append(' ').append(CLASS_FRESH);
        return sb.toString();
    }

    private static String rarityClass(ItemStack stack) {
        return switch (stack.getRarity()) {
            case UNCOMMON -> CLASS_RARITY_PREFIX + "uncommon";
            case RARE -> CLASS_RARITY_PREFIX + "rare";
            case EPIC -> CLASS_RARITY_PREFIX + "epic";
            default -> CLASS_RARITY_PREFIX + "common";
        };
    }

    /** 物品显示名：走原版的 hover 名，所以改名过的、带附魔的都是玩家看到的那串字。 */
    private static String displayNameOf(ItemStack stack) {
        return stack.getHoverName().getString();
    }

    /** 一张卡对应的几个 DOM 节点。 */
    private record CardNodes(Element card, Element name, Element count,
                             String rarityClass, boolean newBadge) {

        CardNodes withCount(Element next) {
            return new CardNodes(card, name, next, rarityClass, newBadge);
        }
    }
}
