package com.niuqu.pickupcard.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import com.niuqu.pickupcard.notice.Notice;
import com.niuqu.pickupcard.notice.PickupCardSettings;
import com.niuqu.pickupcard.pickup.CardContent;
import com.niuqu.pickupcard.pickup.Inbox;
import com.niuqu.pickupcard.style.CardTimeline;
import com.niuqu.pickupcard.style.Easing;
import com.niuqu.pickupcard.style.StyleModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自绘渲染层 v2（用户草图定稿版）：胶囊形卡身 + 稀有度色双圈描边；数量在左；
 * 物品图标住在上下出头的圆形徽章里；名字装在内嵌胶囊（稀有度低透明描边）；
 * 右端一枚稀有度端点；卡左侧挂同色月牙装饰。卡长随名字长度。
 * 参数全部在 styles/default.json（geometry 段），视觉真源 docs/art/mockup.html。
 * <p>
 * 【绘制分两遍】第一遍全部卡的外壳矢量（含月牙/徽章/端点）进 {@code RenderType.gui()}
 * 批量通道，第二遍 GuiGraphics 画图标与文字——同一条批量管线内按插入序排列，
 * 外壳恒在内容之下，且不再有任何手搓 GL 状态。
 * <p>
 * 【几何约定】卡身高度 {@code H = paddingV*2 + 字号行高}；徽章直径 {@code D = H*badgeScale}
 * 出头，所以行距按 {@code D + gap} 算。内容层一律用卡内局部坐标（原点=卡左上角）。
 */
public final class HudRenderer {

    public static final HudRenderer INSTANCE = new HudRenderer();

    /** 右下角锚点的外边距。 */
    private static final int MARGIN_X = 16;
    private static final int MARGIN_Y = 16;
    /** 图标徽章与数量/名字胶囊之间的间隙（px，随 H 缩放前的基准）。 */
    private static final float GAP_INNER = 5f;
    /** 微光呼吸周期（毫秒）。 */
    private static final long GLOW_PULSE_MS = 1_800L;
    /** 主题文件重读间隔——改 JSON 一秒内生效。 */
    private static final long STYLE_RECHECK_MS = 1_000L;

    private static final ResourceLocation STYLE_PATH =
            new ResourceLocation("pickupcard", "styles/default.json");

    /** 屏上一张卡的渲染态。notice 是账本的权威数据，时刻是动画的锚点。 */
    private static final class RenderCard {
        Notice<Inbox.Card> notice;
        long lastBump = -1L;
        long exitStartAt = -1L;

        RenderCard(Notice<Inbox.Card> notice) {
            this.notice = notice;
        }
    }

    /** 一张卡的横向几何（卡内局部坐标），两遍绘制共用同一份，保证不错位。 */
    private record Geom(float w, float countX, float badgeCx, float pillX, float pillW, float dotCx) {
    }

    /** 插入序 = 从老到新。布局时新的在底部（贴近快捷栏），越老越往上。 */
    private final Map<String, RenderCard> cards = new LinkedHashMap<>();
    private final List<Inbox.Event> pending = new ArrayList<>();

    @Nullable
    private StyleModel style;
    private long nextStyleCheck;

    private HudRenderer() {
    }

    /** 换世界/退出：屏上节点与未消费事件一起清。与 Inbox.reset() 成对调用。 */
    public void clear() {
        cards.clear();
        pending.clear();
    }

    // ------------------------------------------------------------------
    // 事件入口（Forge 总线）
    // ------------------------------------------------------------------

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        pending.addAll(Inbox.INSTANCE.tick());
    }

    @SubscribeEvent
    public void onHudRender(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        if (!pending.isEmpty()) {
            long now = System.currentTimeMillis();
            for (Inbox.Event e : pending) {
                consume(e, now);
            }
            pending.clear();
        }
        if (!cards.isEmpty()) {
            draw(event.getGuiGraphics(), mc);
        }
    }

    // ------------------------------------------------------------------
    // 账本事件 → 渲染态
    // ------------------------------------------------------------------

    private void consume(Inbox.Event event, long now) {
        if (event instanceof Inbox.Event.Added added) {
            cards.put(added.notice().key(), new RenderCard(added.notice()));
        } else if (event instanceof Inbox.Event.Merged merged) {
            RenderCard card = cards.get(merged.notice().key());
            if (card == null) {
                // 时序兜底：账本说有，渲染却没有，当新卡补挂
                cards.put(merged.notice().key(), new RenderCard(merged.notice()));
                return;
            }
            card.notice = merged.notice();
            card.exitStartAt = -1L; // 合并把正在退场的卡拉回来
            card.lastBump = now;
        } else if (event instanceof Inbox.Event.Evicted evicted) {
            beginExit(evicted.notice().key(), now);
        } else if (event instanceof Inbox.Event.Expired expired) {
            beginExit(expired.notice().key(), now);
        }
    }

    private void beginExit(String key, long now) {
        RenderCard card = cards.get(key);
        if (card == null || card.exitStartAt > 0L) return;
        card.exitStartAt = now;
    }

    // ------------------------------------------------------------------
    // 绘制
    // ------------------------------------------------------------------

    private void draw(GuiGraphics gui, Minecraft mc) {
        long now = System.currentTimeMillis();
        StyleModel style = style(mc, now);
        PickupCardSettings cfg = Inbox.INSTANCE.settingsSnapshot();
        CardTimeline timeline = new CardTimeline(style.enterMs(), style.bumpMs(),
                style.enterEnabled(), style.bumpEnabled());
        long exitMs = cfg.exitMs();
        Font font = mc.font;
        float k = style.iconScale();
        // 卡身高度与文字/徽章挂钩：图标住在徽章里，卡身可以比徽章瘦
        float H = style.paddingV() * 2f + font.lineHeight;
        float badgeD = H * style.badgeScale();
        float pitch = badgeD + style.gap();

        // 退场播完的摘掉；剩下的按从老到新排进布局
        List<RenderCard> alive = new ArrayList<>(cards.size());
        for (Iterator<RenderCard> it = cards.values().iterator(); it.hasNext(); ) {
            RenderCard card = it.next();
            if (card.exitStartAt > 0L && CardTimeline.exit(now, card.exitStartAt, exitMs) >= 1f) {
                it.remove();
                continue;
            }
            alive.add(card);
        }
        int n = alive.size();
        if (n == 0) return;

        float right = gui.guiWidth() - MARGIN_X;
        float stackTop = gui.guiHeight() - MARGIN_Y - n * pitch;

        float[] xs = new float[n];
        float[] cys = new float[n];
        Geom[] geoms = new Geom[n];
        for (int i = 0; i < n; i++) {
            geoms[i] = geom(font, alive.get(i).notice, style, k, H);
            xs[i] = right - geoms[i].w();
            cys[i] = stackTop + i * pitch + pitch / 2f;
        }

        // ---- 第一遍：外壳矢量（月牙/投影/卡面/双圈/名字胶囊/端点/徽章/经验珠） ----
        // SDF core shader 逐形状 draw：一形状一 quad，抗锯齿在片段端。坐标一律
        // 屏幕绝对值（v2 的幽灵分身就是绝对/局部混用，这里从接口上杜绝）。
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();   // SDF quad 的绕序无所谓，关剔除免疫半边消失
        RenderSystem.disableDepthTest();
        for (int i = 0; i < n; i++) {
            RenderCard card = alive.get(i);
            gui.pose().pushPose();
            applyCardTransform(gui, card, style, timeline, exitMs, now, xs[i], cys[i], geoms[i], H);
            drawCardChrome(gui.pose().last().pose(), card, style, timeline, exitMs,
                    now, xs[i], cys[i], geoms[i], H, k);
            gui.pose().popPose();
        }
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();

        // ---- 第二遍：GuiGraphics 批量通道（物品图标 + 文字） ----
        for (int i = 0; i < n; i++) {
            RenderCard card = alive.get(i);
            gui.pose().pushPose();
            applyCardTransform(gui, card, style, timeline, exitMs, now, xs[i], cys[i], geoms[i], H);
            // 内容一律用卡内局部坐标：原点=卡身左上角（不含徽章出头部分）
            gui.pose().translate(xs[i], cys[i] - H / 2f, 0);
            drawCardContent(gui, mc, card, style, timeline, exitMs, now, geoms[i], H, k);
            gui.pose().popPose();
        }
    }

    /** 一张卡的横向几何。两遍绘制共用，改布局只改这里。 */
    private Geom geom(Font font, Notice<Inbox.Card> notice, StyleModel style, float k, float h) {
        float L = h * 0.55f;                       // 左内边距（给胶囊圆头留位）
        String name = displayName(notice);
        String count = Inbox.INSTANCE.settingsSnapshot().countFormat().gain(notice.count());
        float countW = font.width(count);
        float badgeD = h * style.badgeScale();
        float pillPad = 6f;
        float nameW = font.width(name) + (notice.firstTime() ? font.width("NEW") + 10f : 0f);
        float pillW = nameW + pillPad * 2f;
        float dotD = h * style.endDotScale();
        float x = L;
        float countX = x;
        x += countW + GAP_INNER;
        float badgeCx = x + badgeD / 2f;
        x += badgeD + GAP_INNER;
        float pillX = x;
        x += pillW + GAP_INNER;
        float dotCx = x + dotD / 2f;
        x += dotD + L;
        return new Geom(x, countX, badgeCx, pillX, pillW, dotCx);
    }

    /**
     * 入场/退场的公共变换：绕卡身中心缩放 + 纵向位移。两遍绘制逐参数一致，
     * 网格与图标文字才不会错位。
     */
    private static void applyCardTransform(GuiGraphics gui, RenderCard card, StyleModel style,
                                           CardTimeline timeline, long exitMs, long now,
                                           float x, float cy, Geom g, float h) {
        float enterT = timeline.enter(now, card.notice.bornAt());
        float exitT = card.exitStartAt > 0L ? CardTimeline.exit(now, card.exitStartAt, exitMs) : 0f;
        float dy = (1f - Easing.easeOutBack(enterT)) * 18f + Easing.easeInQuad(exitT) * 12f;
        float scale = (0.92f + 0.08f * Easing.easeOutBack(enterT)) * (1f - 0.06f * exitT);
        float cx = x + g.w() / 2f;
        gui.pose().translate(cx, cy, 0);
        gui.pose().scale(scale, scale, 1f);
        gui.pose().translate(-cx, -cy, 0);
        gui.pose().translate(0, dy, 0);
    }

    /** 卡的"外壳"：月牙 + 投影 + 玻璃卡面 + 稀有度双圈 + 名字胶囊 + 端点 + 图标徽章。 */
    private void drawCardChrome(Matrix4f m, RenderCard card, StyleModel style,
                                CardTimeline timeline, long exitMs, long now,
                                float x, float cy, Geom g, float h, float k) {
        float enterT = timeline.enter(now, card.notice.bornAt());
        float exitT = card.exitStartAt > 0L ? CardTimeline.exit(now, card.exitStartAt, exitMs) : 0f;
        float alpha = Easing.easeOutCubic(enterT) * (1f - exitT);
        if (alpha <= 0.01f) return;

        int accent = accentOf(card.notice);
        float y = cy - h / 2f;
        float r = style.cornerRadius(); // fill/stroke 内部会夹到 h/2 => 胶囊形
        float badgeR = h * style.badgeScale() / 2f;
        float pillH = h * style.namePillScale();

        // 月牙：挂在卡左外侧，稀有度色实心填充
        if (style.crescentScale() > 0f) {
            float cr = h * style.crescentScale();
            ShapeBatch.crescent(m, x - h * 0.06f - cr * 0.55f, cy, cr,
                    0.10f * cr, 0.95f * cr, ShapeBatch.fade(accent, alpha));
        }

        if (style.shadowAlpha() > 0) {
            int shadow = ShapeBatch.fade(style.shadowAlpha() << 24, alpha);
            ShapeBatch.pillFill(m, x, y + style.shadowOffsetY(), g.w(), h, r, shadow, shadow);
        }

        if (style.glowAlpha() > 0) {
            float pulse = 1f;
            if (style.glowPulseEnabled() && isEpicItem(card)) {
                pulse = 0.7f + 0.3f * (float) Math.sin(
                        Math.PI * 2 * (now % GLOW_PULSE_MS) / (float) GLOW_PULSE_MS);
            }
            float glow = style.glowAlpha() / 255f * alpha * pulse;
            if (card.notice.payload().emphasized()) glow = Math.min(1f, glow * 1.6f);
            ShapeBatch.pillRing(m, x - 2f, y - 2f, g.w() + 4f, h + 4f, r, 2f,
                    ShapeBatch.fade(accent, glow));
        }

        // 玻璃卡面 + 稀有度双圈描边（外圈实、内圈淡）
        ShapeBatch.pillFill(m, x, y, g.w(), h, r,
                ShapeBatch.fade(style.fillTop(), alpha),
                ShapeBatch.fade(style.fillBottom(), alpha));
        ShapeBatch.pillRing(m, x, y, g.w(), h, r, 1.5f, ShapeBatch.fade(accent, 0.95f * alpha));
        float inset = style.borderInset();
        ShapeBatch.pillRing(m, x + inset, y + inset, g.w() - 2f * inset, h - 2f * inset,
                r - inset, 1f, ShapeBatch.fade(accent, 0.55f * alpha));

        // 名字内嵌胶囊：极淡同色填充 + 稀有度低透明描边（绝对坐标 = x + 局部）
        ShapeBatch.pillFill(m, x + g.pillX(), cy - pillH / 2f, g.pillW(), pillH, pillH / 2f,
                ShapeBatch.fade(accent, 0.10f * alpha), ShapeBatch.fade(accent, 0.10f * alpha));
        ShapeBatch.pillRing(m, x + g.pillX(), cy - pillH / 2f, g.pillW(), pillH, pillH / 2f, 1f,
                ShapeBatch.fade(accent, 0.60f * alpha));

        // 右端端点：淡色外环 + 实心内点
        ShapeBatch.circleFill(m, x + g.dotCx(), cy, h * style.endDotScale() / 2f,
                ShapeBatch.fade(accent, 0.35f * alpha));
        ShapeBatch.circleFill(m, x + g.dotCx(), cy, h * style.endDotScale() * 0.24f,
                ShapeBatch.fade(accent, alpha));

        // 图标徽章：出头圆，稀有度细环 + 深色内圆（环=大圆盖小圆留出的边）
        ShapeBatch.circleFill(m, x + g.badgeCx(), cy, badgeR, ShapeBatch.fade(accent, 0.45f * alpha));
        ShapeBatch.circleFill(m, x + g.badgeCx(), cy, badgeR - 1.2f,
                ShapeBatch.fade(style.fillBottom(), alpha));

        // 经验珠画在这里（徽章内）：绿色主体 + 左上高光点
        if (card.notice.payload().content() instanceof CardContent.Experience) {
            float or = badgeR * 0.56f;
            ShapeBatch.circleFill(m, x + g.badgeCx(), cy, or, ShapeBatch.fade(RarityAccent.XP, alpha));
            ShapeBatch.circleFill(m, x + g.badgeCx() - or * 0.3f, cy - or * 0.35f, or * 0.32f,
                    ShapeBatch.fade(0xFFFFFFFF, alpha * 0.85f));
        }
    }

    /** 卡的"内容"：数量（左）、物品图标（徽章内）、名字 + NEW（胶囊内）。局部坐标。 */
    private void drawCardContent(GuiGraphics gui, Minecraft mc, RenderCard card, StyleModel style,
                                 CardTimeline timeline, long exitMs, long now,
                                 Geom g, float h, float k) {
        float enterT = timeline.enter(now, card.notice.bornAt());
        float exitT = card.exitStartAt > 0L ? CardTimeline.exit(now, card.exitStartAt, exitMs) : 0f;
        float alpha = Easing.easeOutCubic(enterT) * (1f - exitT);
        if (alpha <= 0.01f) return;

        Notice<Inbox.Card> notice = card.notice;
        int accent = accentOf(notice);
        Font font = mc.font;
        float cy = h / 2f;
        float badgeD = h * style.badgeScale();

        // 数量在左（合并时围绕自己的中心鼓一下）
        String countText = Inbox.INSTANCE.settingsSnapshot().countFormat().gain(notice.count());
        int countW = font.width(countText);
        float bumpT = timeline.bump(now, card.lastBump);
        gui.pose().pushPose();
        if (bumpT < 1f) {
            float s = Easing.pulse(bumpT, 1.35f);
            gui.pose().translate(g.countX() + countW / 2f, cy, 0);
            gui.pose().scale(s, s, 1f);
            gui.pose().translate(-(g.countX() + countW / 2f), -cy, 0);
        }
        gui.drawString(font, countText, g.countX(), cy - 4f, withAlpha(accent, alpha), false);
        gui.pose().popPose();

        // 物品图标：居中在徽章圆心里
        if (notice.payload().content() instanceof CardContent.Item item) {
            gui.pose().pushPose();
            gui.pose().translate(g.badgeCx() - 8f * k, cy - 8f * k, 0);
            gui.pose().scale(k, k, 1f);
            // 原版渲染白拿：附魔光效在模型里，耐久条由 renderItemDecorations 给
            gui.renderItem(item.stack(), 0, 0);
            gui.renderItemDecorations(font, item.stack(), 0, 0, "");
            gui.pose().popPose();
        }

        // 名字 + NEW 角标（都在内嵌胶囊里）
        float pillPad = 6f;
        float tx = g.pillX() + pillPad;
        String name = displayName(notice);
        gui.drawString(font, name, tx, cy - 4f, withAlpha(style.nameColor(), alpha), false);
        if (notice.firstTime()) {
            int badgeBg = withAlpha(style.newBadgeBg(), alpha);
            int badgeTx = withAlpha(style.newBadgeText(), alpha);
            int bw = font.width("NEW") + 6;
            float bx = tx + font.width(name) + 4f;
            gui.fill((int) bx, (int) (cy - 9f), (int) bx + bw, (int) (cy + 0.5f), badgeBg);
            gui.drawString(font, "NEW", bx + 3f, cy - 8f, badgeTx, false);
        }
    }

    private int accentOf(Notice<Inbox.Card> notice) {
        if (notice.payload().content() instanceof CardContent.Item item) {
            return RarityAccent.of(item.stack());
        }
        return RarityAccent.XP;
    }

    private static boolean isEpicItem(RenderCard card) {
        return card.notice.payload().content() instanceof CardContent.Item item
                && item.stack().getRarity() == Rarity.EPIC;
    }

    private static String displayName(Notice<Inbox.Card> notice) {
        if (notice.payload().content() instanceof CardContent.Item item) {
            return item.stack().getHoverName().getString();
        }
        return Component.translatable("pickupcard.xp").getString();
    }

    private static int withAlpha(int argb, float alpha) {
        int a = Math.min(255, Math.round(((argb >>> 24) & 0xFF) * Easing.clamp01(alpha)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /**
     * 主题懒加载 + 周期性重读（改 JSON 一秒内生效）。缺资源或解析坏都回退整套默认值——
     * 主题坏了不该拖垮拾取提示。
     */
    private StyleModel style(Minecraft mc, long now) {
        if (style != null && now < nextStyleCheck) return style;
        nextStyleCheck = now + STYLE_RECHECK_MS;
        try {
            var resource = mc.getResourceManager().getResource(STYLE_PATH);
            if (resource.isPresent()) {
                try (var in = resource.get().open()) {
                    style = StyleModel.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
            } else {
                style = StyleModel.defaults();
            }
        } catch (Exception e) {
            style = StyleModel.defaults();
        }
        return style;
    }
}
