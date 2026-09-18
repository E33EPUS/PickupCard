package com.niuqu.pickupcard.config;

import com.niuqu.pickupcard.PickupCard;
import com.niuqu.pickupcard.layout.HudSafeZone;
import com.niuqu.pickupcard.layout.LayoutSettings;
import com.niuqu.pickupcard.notice.Notice;
import com.niuqu.pickupcard.pickup.Inbox;
import com.niuqu.pickupcard.render.CardCanvas;
import com.niuqu.pickupcard.render.CardMetrics;
import com.niuqu.pickupcard.render.CardSlot;
import com.niuqu.pickupcard.render.CardStage;
import com.niuqu.pickupcard.render.CardView;
import com.niuqu.pickupcard.render.nvg.NvgCardPainter;
import com.niuqu.pickupcard.render.nvg.ui.NvgButton;
import com.niuqu.pickupcard.render.nvg.ui.NvgPalette;
import com.niuqu.pickupcard.render.nvg.ui.NvgUi;
import com.niuqu.pickupcard.render.nvg.ui.NvgWidget;
import com.niuqu.pickupcard.style.CardTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>整屏拖拽编辑场</b>：卡片停在哪，不靠滑条靠手 —— 按住那摞样例卡拖到想要的位置，
 * 「完成」存盘，Esc 取消。位置存成<b>画布分数</b>（0~1），换 GUI 缩放档不错位。
 *
 * <p>【为什么要整屏，而不是在预览列里拖】预览列只有屏幕的十几分之一大，拖三五个像素
 * 就是真屏上的一大段，拖不准；而且"位置好不好"是遮挡关系的问题 —— 真实 HUD、准星、
 * 侧栏都在 1:1 画布上，缩小的模拟屏看不出来。与 keyhud 的拖拽编辑同一套路。
 *
 * <p>【真卡为什么要挂起】屏幕上只能有一摞卡。编辑场自己画一摞样例（当前配置的样子），
 * 真卡若照画就叠在一起分不清谁是谁；{@link CardStage#setSuspended} 只是"这几帧不画"，
 * 背后的账本照旧 —— 取消退出时玩家的提示一条不少。
 *
 * <p>【区域框是"承诺"，卡是"样子"】虚框（四角括号）画的是这一套配置下卡堆的<b>最大占地</b>
 * （最宽卡 × 同屏上限），拨同屏上限/缩放/名字宽度它都跟着变；框里实际画几张样例只是示意
 * （画满 16 张只会变成一堵墙）。锚点被拖到连一张卡都放不下的位置时自动抬到 HUD 带上方
 * —— 与游戏里的夹取（{@link LayoutSettings#anchorTop}）同一个公式，编辑场里看见的就是
 * 游戏里会发生的。
 */
public final class AnchorEditScreen extends Screen {

    private final Screen parent;
    private final PickupCardConfig.Values v = PickupCardConfig.VALUES;

    /** 进编辑场那一刻的值（Esc 取消的"原样"就是它 —— 其实什么都没写，回到父界面即取消）。 */
    private final float origX;
    private final float origY;
    /** 正在编辑的锚点（画布分数；-1 = 自动）。拖动会把它变成明确的分数。 */
    private float anchorX;
    private float anchorY;

    private boolean dragging;
    private double grabDx;
    private double grabDy;

    private long now;
    private NvgButton saveButton;
    private NvgButton resetButton;
    private List<NvgWidget> buttons = List.of();
    private NvgPalette palette;
    private final NvgCardPainter painter = new NvgCardPainter();

    public AnchorEditScreen(Screen parent) {
        super(Component.literal("卡片位置 · 拖拽调整"));
        this.parent = parent;
        LayoutSettings current = PickupCardConfig.layoutSnapshot();
        this.origX = current.anchorX();
        this.origY = current.anchorY();
        this.anchorX = origX;
        this.anchorY = origY;
    }

    @Override
    protected void init() {
        // 真卡挂起：屏幕上只能有一摞卡（见类注释）
        PickupCard.LOGGER.info("[编辑场] init");
        CardStage.INSTANCE.setSuspended(true);
        palette = NvgPalette.of(CardStage.INSTANCE.previewStyle());
        int bw = 120;
        int bh = 18;
        int gap = 8;
        int totalW = bw * 2 + gap;
        int by = this.height - bh - 8;
        saveButton = new NvgButton("完成", () -> "完成", this::saveAndClose);
        saveButton.at(this.width / 2f - totalW / 2f, by, bw, bh);
        resetButton = new NvgButton("回到默认", () -> isAuto() ? "已回到默认" : "回到默认", this::resetToAuto);
        resetButton.at(this.width / 2f + totalW / 2f - bw, by, bw, bh);
        buttons = List.of(saveButton, resetButton);
    }

    @Override
    public void removed() {
        // 挂起必须解除，否则编辑场一关真卡永远不画（这条日志是"关掉有据"，不是诊断）
        PickupCard.LOGGER.info("[编辑场] 关闭，恢复真卡渲染");
        CardStage.INSTANCE.setSuspended(false);
        super.removed();
    }

    // ------------------------------------------------------------------
    // 几何：编辑中的锚点（与游戏里同一套公式）
    // ------------------------------------------------------------------

    /** 编辑中的布局快照：展开方式/间距/缩放照当前配置，锚点用编辑中的值。 */
    private LayoutSettings editing() {
        return new LayoutSettings(PickupCardConfig.layoutSnapshot().appearMode(),
                v.separation.get().floatValue(), v.scalePercent.get(), anchorX, anchorY).sanitized();
    }

    private boolean isAuto() {
        return anchorX < 0f && anchorY < 0f;
    }

    private float cardScale() {
        return PickupCardConfigScreen.previewScale();
    }

    /** 锚点像素 x（编辑中的值；自动档照公式解析，玩家看见的就是游戏里会有的）。 */
    private float anchorLeftPx() {
        return editing().anchorLeft(this.width);
    }

    /** 锚点像素 y：带"至少放得下一张卡"的夹取（与游戏里同一公式）。 */
    private float anchorTopPx(float cardHeight) {
        return editing().anchorTop(this.height, cardHeight, HudSafeZone.bottomInset());
    }

    // ------------------------------------------------------------------
    // 画
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        now = System.currentTimeMillis();
        // 世界之上盖一层薄暮色：括号和字读得出，遮挡关系还看得清
        gui.fill(0, 0, this.width, this.height, 0x59000000);

        StyleGeometry g = geometry();
        try (NvgUi ui = NvgUi.begin(gui, palette, mouseX, mouseY, now)) {
            if (ui != null) {
                ui.text(this.title.getString(), 8f, 6f, 0xFFFFFFFF);
                ui.text(isAuto() ? "当前：自动（准星右下）—— 按住卡片堆拖动即变为自定义"
                                : String.format(java.util.Locale.ROOT, "当前：自定义 (x=%.2f, y=%.2f 屏)",
                                anchorX, anchorY),
                        8f, 17f, palette.textDim);
                drawRegionBrackets(ui, g);
                for (NvgWidget w : buttons) {
                    w.mouseMoved(mouseX, mouseY);
                    w.draw(ui);
                }
                ui.textRight("Esc 取消 · Enter 完成", this.width - 8f, this.height - 12f, palette.textDim);
            }
        }
        paintSampleStack(gui, g);
    }

    /** 这一帧的编辑场几何：锚点、区域框（最宽卡 × 同屏上限）、样例卡的位置。 */
    private record StyleGeometry(float anchorLeft, float anchorTop, float cardH, float regionW,
                                 float regionH, float scale) {
    }

    private StyleGeometry geometry() {
        var style = CardStage.INSTANCE.previewStyle();
        float scale = cardScale();
        float cardH = style.boxHeight() * scale;
        float left = anchorLeftPx();
        float top = anchorTopPx(cardH);
        float gap = v.separation.get().floatValue() * scale;
        float widest = 0f;
        for (PickupCardConfigScreen.Sample s : PickupCardConfigScreen.Sample.values()) {
            widest = Math.max(widest, CardMetrics.naturalWidth(canvas(style, scale),
                    Minecraft.getInstance().font, editorView(s)));
        }
        int rows = v.maxOnScreen.get();
        float regionW = Math.max(24f, widest);
        float regionH = Math.max(cardH, rows * cardH + (rows - 1) * gap);
        return new StyleGeometry(left, top, cardH, regionW, regionH, scale);
    }

    /** 区域框 = 四角括号（虚线在 NanoVG 里要自己拼，括号更快也更轻）。 */
    private void drawRegionBrackets(NvgUi ui, StyleGeometry g) {
        float len = 10f;
        float t = 2f;
        int color = ui.palette.accent;
        float x = g.anchorLeft(), y = g.anchorTop();
        float x2 = x + g.regionW(), y2 = y + g.regionH();
        // 四个角，每个角两条短线
        ui.fillRoundRect(x, y, len, t, 1f, color);
        ui.fillRoundRect(x, y, t, len, 1f, color);
        ui.fillRoundRect(x2 - len, y, len, t, 1f, color);
        ui.fillRoundRect(x2 - t, y, t, len, 1f, color);
        ui.fillRoundRect(x, y2 - t, len, t, 1f, color);
        ui.fillRoundRect(x, y2 - len, t, len, 1f, color);
        ui.fillRoundRect(x2 - len, y2 - t, len, t, 1f, color);
        ui.fillRoundRect(x2 - t, y2 - len, t, len, 1f, color);
    }

    /**
     * 编辑场里的样例堆：与配置界面预览同一套样例、同一个画笔、同一个缩放 ——
     * 编辑场里看到的多宽多高，游戏里就是多宽多高。最多画五张示意（画满同屏上限只会
     * 变成一堵墙，区域框已经把"最大占地"说清楚了）。
     */
    private void paintSampleStack(GuiGraphics gui, StyleGeometry g) {
        PickupCardConfigScreen.Sample[] samples = PickupCardConfigScreen.Sample.values();
        int count = Math.min(5, v.maxOnScreen.get());
        float gap = v.separation.get().floatValue() * g.scale();
        var style = CardStage.INSTANCE.previewStyle();
        var font = Minecraft.getInstance().font;
        List<CardSlot> slots = new ArrayList<>();
        float y = g.anchorTop();
        for (int i = 0; i < count; i++) {
            CardView view = editorView(samples[i % samples.length]);
            float w = Math.min(CardMetrics.naturalWidth(canvas(style, g.scale()), font, view) * g.scale(),
                    g.regionW());
            if (y + g.cardH > this.height) {
                break;      // 超出屏幕的部分不画 —— 框已经说明那里还有位子
            }
            slots.add(new CardSlot(view, g.anchorLeft(), y, w, g.cardH));
            y += g.cardH + gap;
        }
        if (!slots.isEmpty()) {
            painter.paint(gui, canvas(style, g.scale()), slots);
        }
    }

    /** 编辑场画布：与配置界面预览同一条构造路（settings/layout 取当前生效值）。 */
    private CardCanvas canvas(com.niuqu.pickupcard.style.StyleModel style, float scale) {
        return new CardCanvas(now,
                new CardTimeline(style.enterMs(), style.bumpMs(), style.enterEnabled(),
                        style.bumpEnabled()),
                style, PickupCardConfig.snapshot(), PickupCardConfig.layoutSnapshot(),
                this.width, this.height, scale);
    }

    /**
     * 编辑场自己的样例卡：born 在很久以前 → 入场已完成、无退场计划。
     * 拖动的时候卡必须稳定 —— 重播动画会让"拖到哪"变得看不清。
     */
    private static CardView editorView(PickupCardConfigScreen.Sample s) {
        Notice<Inbox.Card> notice = PickupCardConfigScreen.previewNotice(s, s.amount, -10_000L);
        return new CardView(notice);
    }

    // ------------------------------------------------------------------
    // 输入：拖（点哪都行，偏移保住"不跳"）、完成/取消
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (NvgWidget w : buttons) {
            if (w.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        // 点空白处即开始拖：偏移记的是"手与卡堆的相对位置"，卡不会跳到指针底下
        dragging = true;
        grabDx = mouseX - anchorLeftPx();
        grabDy = mouseY - anchorTopPx(CardStage.INSTANCE.previewStyle().boxHeight() * cardScale());
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        for (NvgWidget w : buttons) {
            w.mouseDragged(mouseX, mouseY);
        }
        if (dragging) {
            setAnchorPx(mouseX - grabDx, mouseY - grabDy);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (NvgWidget w : buttons) {
            w.mouseReleased(mouseX, mouseY);
        }
        dragging = false;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            cancel();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            saveAndClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        cancel();       // Esc 走这里：取消，不写配置
    }

    /** 把锚点设到屏幕坐标（逻辑 px），并夹进"卡堆整体还在屏内"的范围。 */
    private void setAnchorPx(double x, double y) {
        float scale = cardScale();
        float cardH = CardStage.INSTANCE.previewStyle().boxHeight() * scale;
        float maxX = Math.max(0f, this.width - HudSafeZone.PAD - 24f);      // 至少留一条竖条的宽度
        float maxY = Math.max(0f, this.height - HudSafeZone.bottomInset() - cardH);
        float px = (float) Math.max(0, Math.min(x, maxX));
        float py = (float) Math.max(0, Math.min(y, maxY));
        anchorX = px / this.width;
        anchorY = py / this.height;
    }

    /** 「回到默认」：立刻回到自动锚点（还不写盘 —— 完成/取消才定生死）。 */
    private void resetToAuto() {
        anchorX = LayoutSettings.AUTO_ANCHOR;
        anchorY = LayoutSettings.AUTO_ANCHOR;
    }

    /** 「完成」：写配置（分数保留三位小数足够 —— 256 宽的画布上 0.001 就是四分之一像素）。 */
    private void saveAndClose() {
        v.anchorX.set((double) Math.round(anchorX * 1000f) / 1000.0);
        v.anchorY.set((double) Math.round(anchorY * 1000f) / 1000.0);
        PickupCardConfigScreen.changed();
        Minecraft.getInstance().setScreen(parent);
    }

    /** Esc：不写配置，直接回父界面。 */
    private void cancel() {
        Minecraft.getInstance().setScreen(parent);
    }

    // ------------------------------------------------------------------
    // 给 dev harness 的只读/驱动入口
    // ------------------------------------------------------------------

    public String stateDump() {
        return String.format(java.util.Locale.ROOT,
                "编辑场 锚点=(%.3f,%.3f)%s 区域=%.0fx%.0f 真卡挂起=%s",
                anchorX, anchorY, isAuto() ? "（自动）" : "", geometry().regionW(), geometry().regionH(),
                CardStage.INSTANCE.suspended());
    }

    /** 走真实事件路径拖一次：按在锚点上（偏移=0）→ 拖到目标分数 → 松开。 */
    public boolean dragForHarness(double fx, double fy) {
        float cardH = CardStage.INSTANCE.previewStyle().boxHeight() * cardScale();
        mouseClicked(anchorLeftPx(), anchorTopPx(cardH), 0);
        mouseDragged(fx * this.width, fy * this.height, 0, 0, 0);
        mouseReleased(fx * this.width, fy * this.height, 0);
        return true;
    }

    public boolean saveForHarness() {
        saveAndClose();
        return true;
    }

    public boolean cancelForHarness() {
        cancel();
        return true;
    }
}
