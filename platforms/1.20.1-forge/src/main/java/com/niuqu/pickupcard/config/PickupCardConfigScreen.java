package com.niuqu.pickupcard.config;

import com.niuqu.pickupcard.layout.LayoutSettings;
import com.niuqu.pickupcard.layout.StackLayout;
import com.niuqu.pickupcard.notice.PickupCardSettings;
import com.niuqu.pickupcard.render.CardStage;
import com.niuqu.pickupcard.render.nvg.NvgCardPainter;
import com.niuqu.pickupcard.render.nvg.ui.NvgButton;
import com.niuqu.pickupcard.render.nvg.ui.NvgPalette;
import com.niuqu.pickupcard.render.nvg.ui.NvgSlider;
import com.niuqu.pickupcard.render.nvg.ui.NvgTextField;
import com.niuqu.pickupcard.render.nvg.ui.NvgToggle;
import com.niuqu.pickupcard.render.nvg.ui.NvgUi;
import com.niuqu.pickupcard.render.nvg.ui.NvgWidget;
import com.niuqu.pickupcard.style.StyleModel;
import com.niuqu.pickupcard.style.StyleOverrides;
import com.niuqu.pickupcard.text.CountFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 游戏内配置界面：<b>左侧一列分类，右侧实时预览 + 选项行</b>（同一行里左边标签、右边控件）。
 *
 * <p>【两件事决定了它的形状】
 * <ol>
 *   <li><b>形状照旧版</b>（= 从发行 jar 恢复出来的 v0.1.0 / pickupnotice 那一系）：4 段
 *       「通用 / 动画 / 位置与堆叠 / 外观」、每行"左标签右控件"、每项一句话悬停。</li>
 *   <li><b>控件自己画</b>（{@code render/nvg/ui}）：按钮/开关/滑条/输入框全是 NanoVG 画的，
 *       不再用原版控件。旧版就是自绘的（{@code ConfigWidget} 那一套）；换到 NanoVG 之后
 *       圆钮、细轨道这些才画得出来，界面也不再被原版九宫格贴图锁死。</li>
 * </ol>
 *
 * <p>【"跟随主题"为什么从界面上消失了】界面显示的是<b>生效值</b>（主题 + 你改过的），
 * 拨哪一项就写死哪一项（想回主题：TOML 里改回 -1 或空串）。几何参数（竖条宽 / 内缩 /
 * 内边距 / 框间距）不进界面 —— 它们是设计参数，归主题 JSON，摆在玩家面前只会把卡调歪。
 *
 * <p>【界面自己不存值】控件的读写直接打到 Forge 配置上；否则"界面显示 5、实际画 9"迟早出现。
 */
public final class PickupCardConfigScreen extends Screen {

    // ---- 左侧分类 ----
    private static final int PAD = 6;
    private static final int SIDE_W = 86;
    private static final int SIDE_TOP = 30;
    private static final int SIDE_H = 18;
    private static final int SIDE_STEP = 20;

    /** 分类名抄旧版：通用 / 动画 / 位置与堆叠 / 外观。 */
    private enum Section {
        GENERAL("通用"),
        ANIM("动画"),
        LAYOUT("位置与堆叠"),
        LOOK("外观");

        final String label;

        Section(String label) {
            this.label = label;
        }
    }

    private final Screen parent;
    private Section section = Section.GENERAL;
    /** 一行 = 一个标签 + 一个自绘控件 + 一句悬停提示。 */
    private final List<Row> rows = new ArrayList<>();
    private final List<NvgWidget> sideButtons = new ArrayList<>();
    private NvgPalette palette = NvgPalette.dark();
    /** 控件里点出来的"切换分类/重建"请求：不在事件遍历中途重建列表。 */
    private boolean pendingRebuild;

    private record Row(String label, NvgWidget widget, String hint) {
    }

    public PickupCardConfigScreen(Screen parent) {
        super(Component.literal("拾起卡片 · 设置"));
        this.parent = parent;
    }

    // ------------------------------------------------------------------
    // 搭界面
    // ------------------------------------------------------------------

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        rows.clear();
        sideButtons.clear();
        palette = NvgPalette.of(CardStage.INSTANCE.previewStyle());
        PickupCardConfig.Values v = PickupCardConfig.VALUES;

        int y = SIDE_TOP;
        for (Section s : Section.values()) {
            Section target = s;
            NvgButton b = new NvgButton(s.label,
                    () -> (s == section ? "▸ " : "  ") + s.label,
                    () -> {
                        section = target;
                        pendingRebuild = true;
                    });
            b.at(PAD, y, SIDE_W, SIDE_H);
            sideButtons.add(b);
            y += SIDE_STEP;
        }

        switch (section) {
            case GENERAL -> buildGeneral(v);
            case ANIM -> buildAnim(v);
            case LAYOUT -> buildLayout(v);
            case LOOK -> buildLook(v);
        }
        layoutRows();
    }

    /** 「通用」：最上面那几个开关。总开关与"显示物品名"是旧版最好懂的两项。 */
    private void buildGeneral(PickupCardConfig.Values v) {
        PickupCardSettings eff = PickupCardConfig.snapshot();
        cell("总开关", bool(v.enabled, eff.enabled()), "关掉之后捡东西不再弹卡");
        cell("显示物品名", bool(v.showItemName, eff.showItemName()),
                "关掉只剩竖条 + 图标 + 数量，卡片会明显变窄");
        cell("显示物品ID", bool(v.showItemId, eff.showItemId()),
                "显示 minecraft:stone 这种 ID 而不是它的名字");
        cell("名字最大宽度", number(v.nameMaxWidth, eff.nameMaxWidth(), 0, 400, 1, "px"),
                "像素。0 = 按屏宽自动；超出就截断加省略号");
        cell("数量写法", cycle(v.countFormat, CountFormat.values(), PickupCardConfigScreen::countName),
                "数量怎么显示：+64 / ×64 / 64 / +1.2K");
        cell("卡片间距", decimal(v.separation, PickupCardConfig.layoutSnapshot().separation(), 0, 16),
                "两张卡之间的空隙；跟卡内「框间距」不是一回事");
    }

    /** 「动画」：入场 / 数字跳动 / 停留 / 消失 —— 全是"看得见"的时长。 */
    private void buildAnim(PickupCardConfig.Values v) {
        StyleModel style = CardStage.INSTANCE.previewStyle();
        PickupCardSettings eff = PickupCardConfig.snapshot();
        cell("入场时长", styleTime(v.stEnterMs, style.enterMs(), 0, 2_000, 40),
                "卡片从竖条后面滑出来的总时间（默认 480ms）");
        cell("入场动画", styleSwitch(v.stEnterEnabled, style.enterEnabled()),
                "关掉就是直接出现（动画敏感的人可以关）");
        cell("停留时长", time(v.holdMs, eff.holdMs(), 500, 10_000, 250), "一张卡在屏幕上待多久");
        cell("消失时长", time(v.exitMs, eff.exitMs(), 0, 2_000, 20), "消失时淡出多久；0 = 直接消失");
        cell("数字跳动", styleSwitch(v.stBumpEnabled, style.bumpEnabled()),
                "连续捡同一种东西时，数字弹一下");
        cell("跳动时长", styleTime(v.stBumpMs, style.bumpMs(), 0, 1_000, 20), "数字弹一下持续多久");
    }

    /** 「位置与堆叠」：停在哪、怎么展开、卡与卡的距离、同屏几张。 */
    private void buildLayout(PickupCardConfig.Values v) {
        LayoutSettings eff = PickupCardConfig.layoutSnapshot();
        PickupCardSettings settings = PickupCardConfig.snapshot();
        cell("贴边", cycle(v.stickTo, LayoutSettings.Side.values(), PickupCardConfigScreen::sideName),
                "左 = 竖条成一条竖线；右 = 卡的右缘齐、竖条参差");
        cell("竖条位置", anchor(v.leftEdge, eff.leftEdge()), "竖条左缘停在哪；「自动」= 跟着画布宽度算");
        cell("展开方式", cycle(v.appearMode, LayoutSettings.Appear.values(),
                PickupCardConfigScreen::appearName),
                "火车 = 整块滑出来；拉幕 = 可见范围一点点变宽（先露图标）");
        cell("卡片缩放", percent(v.scalePercent, eff.scalePercent()),
                "100% 原样。「自动」= 一摞卡塞不进 HUD 带之上就按比例缩，最多缩到 60%");
        cell("同屏上限", number(v.maxOnScreen, settings.maxOnScreen(), 1, 16, 1, " 张"),
                "同时在屏最多几张。GUI 缩放越大、画布越小，放得下的越少");
        cell("排队上限", new NvgButton("排队上限", () -> "暂未实现", null),
                "现在超出同屏上限的那张会被挤掉，不做排队");
    }

    /**
     * 「外观」：只放"看得懂、调了看得出"的。
     * <p>竖条宽度 / 内缩 / 左右内边距 / 框间距是设计参数，归主题 JSON —— 不进界面。
     */
    private void buildLook(PickupCardConfig.Values v) {
        StyleModel style = CardStage.INSTANCE.previewStyle();
        cell("卡片厚薄", styleNumber(v.stPaddingV, style.paddingV(), 0, 8, 1, ""),
                "上下各留多少；调大卡片变厚，图标大小不变");
        cell("图标大小", styleNumber(v.stIconSize, style.iconSize(), 8, 64, 1, "px"),
                "原版物品图标是 16 —— 取 16 或它的整数倍最清晰");
        cell("圆角", styleNumber(v.stCornerRadius, style.cornerRadius(), 0, 16, 1, "px"),
                "三个框的圆角半径；调到很大就变成胶囊");
        cell("框粗细", styleNumber(v.stBorderWidth, style.borderWidth(), 0, 4, 1, "px"),
                "框描边的粗细；0 = 不描边");
        cell("底色（上）", color(v.stFillTop, style.fillTop()), "卡面底色上端；留空 = 用主题里的");
        cell("底色（下）", color(v.stFillBottom, style.fillBottom()), "下端。和上面写成一样就是纯色");
        cell("框色", color(v.stBorder, style.border()), "框描边的颜色");
        cell("物品名颜色", color(v.stNameColor, style.nameColor()), "名字的颜色");
    }

    private void cell(String label, NvgWidget widget, String hint) {
        rows.add(new Row(label, widget, hint));
    }

    /** 逐行摆：一行两个格子，行高按画布自适应（guiScale 5 时画布只有 144 高）。 */
    private void layoutRows() {
        int maxRows = Math.max(1, (rows.size() + 1) / 2);
        int step = rowStep(maxRows);
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).widget().at(controlX(i % 2), rowsTop() + (i / 2) * step, controlW(), rowH());
        }
    }

    // ------------------------------------------------------------------
    // 画
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        if (pendingRebuild) {
            pendingRebuild = false;
            rebuild();
        }
        for (NvgWidget w : widgets()) {
            w.mouseMoved(mouseX, mouseY);
        }

        gui.fill(0, 0, this.width, this.height, palette.backdrop);
        try (NvgUi ui = NvgUi.begin(gui, palette, mouseX, mouseY, System.currentTimeMillis())) {
            if (ui != null) {
                drawChrome(ui);
                drawLabels(ui);
                for (NvgWidget w : widgets()) {
                    w.draw(ui);
                }
            }
        }
        // 预览放在最后：它自己开一帧（里面还有原版图标与文字），压在自绘界面之上。
        // 两者在屏幕上不重叠，顺序只影响"哪一批先上 GPU"。
        drawPreview(gui);
        drawHint(gui, mouseX, mouseY);
    }

    /** 底 + 标题 + 侧栏 + 预览面板 —— 全是 NanoVG 画的圆角块。 */
    private void drawChrome(NvgUi ui) {
        NvgPalette p = ui.palette;
        ui.text(this.title.getString(), contentLeft(), 8f, 0xFFFFFFFF);
        ui.text("改动即时生效，拨过的项会记进 config/pickupcard-client.toml",
                contentLeft(), 18f, p.textDim);
        ui.fillGradient(PAD - 2, SIDE_TOP - 6, SIDE_W + 4, this.height - SIDE_TOP - 14,
                p.panel, 0x80202836);
        ui.text("预览", contentLeft(), 30f, p.textDim);
        ui.fillRoundRect(contentLeft() - 2, 40f, contentRight() - contentLeft() + 4,
                previewHeight() - 4f, p.radius, 0x40202A38);
    }

    private void drawLabels(NvgUi ui) {
        for (Row row : rows) {
            NvgWidget w = row.widget();
            String text = ui.font().plainSubstrByWidth(row.label(), labelW());
            ui.text(text, w.x() - labelW() - 4f, w.y() + (w.height() - 8) / 2f + 1f, ui.palette.textDim);
        }
    }

    /** 底部那行说明：悬停谁就说谁，这是这个界面唯一能自我解释的地方。 */
    private void drawHint(GuiGraphics gui, int mouseX, int mouseY) {
        String hint = null;
        for (Row row : rows) {
            if (row.widget().hit(mouseX, mouseY)) {
                hint = row.hint();
                break;
            }
        }
        if (hint == null) {
            hint = switch (section) {
                case GENERAL -> "这些改的是「弹不弹、显示什么」";
                case ANIM -> "这些改的是卡片怎么出现、数字怎么跳";
                case LAYOUT -> "这些改的是卡片停在哪、同时显示几张（预览就是一摞卡）";
                case LOOK -> "这些改的是卡片长什么样（预览就是当前设置画出来的）";
            };
        }
        gui.drawString(this.font, hint, PAD, this.height - 12, palette.textDim);
    }

    /** 单张样例卡（经验卡，那一档会亮微光）。 */
    private void drawPreview(GuiGraphics gui) {
        StyleModel style = CardStage.INSTANCE.previewStyle();
        boolean showName = PickupCardConfig.snapshot().showItemName();
        float x = contentLeft();
        float y = 40f;
        if (section == Section.LAYOUT) {
            renderStackPreview(gui, x, y, contentRight() - contentLeft(), previewHeight() - 8f, showName);
        } else {
            float w = showName ? Math.max(60f, Math.min(150f, this.width - x - PAD - 4)) : 58f;
            NvgCardPainter.paintPreview(gui, style, x, y, w,
                    new ItemStack(Items.NETHER_STAR), "经验", "+137", 0xFF7DFF8A, true, showName,
                    previewScale());
        }
    }

    /**
     * 预览该用多大的缩放。
     * <p>
     * 【为什么自动档在预览里恒为 100%】自动倍率取决于"游戏里那摞卡有几张、屏幕多高"，
     * 而预览面板是另一块画布 —— 用它算出来的倍率画在面板里只会骗人。手动档是玩家的明确
     * 选择，预览照它画；自动档的真实倍率看游戏画面（行里的说明也写了这句）。
     */
    private static float previewScale() {
        int pct = PickupCardConfig.layoutSnapshot().scalePercent();
        return pct > LayoutSettings.AUTO_SCALE ? pct / 100f : 1f;
    }

    /**
     * 一摞卡：<b>真的排布函数 + 真的间距</b>画三张不同宽度的卡。
     * <p>【为什么必须调真函数】"贴边 / 间距 / 同屏"这些键的效果只在多张卡之间看得出来：
     * 左对齐时三条竖条成一条竖线、右对齐时右缘齐而竖条参差。自己画一遍就是第二份排版实现。
     */
    private void renderStackPreview(GuiGraphics gui, float x, float y, float w, float h, boolean showName) {
        StyleModel style = CardStage.INSTANCE.previewStyle();
        LayoutSettings layout = PickupCardConfig.layoutSnapshot();
        String[][] samples = {{"经验", "+137"}, {"信标", "+1"}, {"石头", "+64"}};
        int[] accents = {0xFF7DFF8A, 0xFF55EBFF, 0xFF9AA4AD};
        float[] widths = showName ? new float[] {132f, 106f, 84f} : new float[] {58f, 52f, 50f};
        ItemStack[] icons = {
                new ItemStack(Items.NETHER_STAR), new ItemStack(Items.BEACON), new ItemStack(Items.STONE)};

        // 【尺寸全部乘上预览缩放】位置由 StackLayout 按传进去的尺寸算：卡缩了、位置不缩，
        // 三张就会各自跑到与原布局无关的地方去。
        float s = previewScale();
        List<StackLayout.Size> sizes = new ArrayList<>();
        for (int i = widths.length - 1; i >= 0; i--) {      // 最新的排第一（StackLayout 的约定）
            sizes.add(new StackLayout.Size(widths[i] * s, style.boxHeight() * s));
        }
        // 【为什么这里的底部留白是个小数字】这一块是"模拟屏"，不是真屏幕 —— 原版 HUD
        // 不在这个 90px 高的面板里，套 HudSafeZone 会把卡顶到面板外面去。
        for (StackLayout.Slot slot : StackLayout.stack(sizes, Math.max(1, (int) w), h, layout,
                6, 4, layout.separation() * s)) {
            int i = widths.length - 1 - slot.index();
            NvgCardPainter.paintPreview(gui, style, x + slot.x(), y + slot.y(), slot.width(),
                    icons[i], samples[i][0], samples[i][1], accents[i], i == 0, showName, s);
        }
    }

    // ------------------------------------------------------------------
    // 事件：全部转给自绘控件
    // ------------------------------------------------------------------

    private List<NvgWidget> widgets() {
        List<NvgWidget> all = new ArrayList<>(sideButtons);
        for (Row row : rows) {
            all.add(row.widget());
        }
        return all;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (NvgWidget w : widgets()) {
            if (w.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        for (NvgWidget w : widgets()) {
            w.blur();       // 点在空白处：所有控件交还焦点（文本框的光标就该停）
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (NvgWidget w : widgets()) {
            w.mouseReleased(mouseX, mouseY);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        for (NvgWidget w : widgets()) {
            w.mouseDragged(mouseX, mouseY);
        }
        return true;
    }

    // 【为什么没有 mouseMoved】1.20.1 的 GuiEventListener 没这个方法（它是后加的），
    // 而"鼠标在哪"每帧都要知道 —— 悬停状态因此统一在 render() 开头按当前鼠标位置刷，
    // 那条路永远会跑到，不依赖某个版本的事件签名。

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return true;        // 这一页不需要滚动：选项都摆得下（行高按画布自适应）
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (NvgWidget w : widgets()) {
            if (w.keyPressed(keyCode, modifiers)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        for (NvgWidget w : widgets()) {
            if (w.charTyped(codePoint)) {
                return true;
            }
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    // ------------------------------------------------------------------
    // 给 dev harness 的只读入口（不是给渲染层用的）
    // ------------------------------------------------------------------

    /** 按选项名找控件（含侧栏分类按钮）。找不到返回 null —— 调用方要报，不能静默点空。 */
    public NvgWidget widgetFor(String label) {
        for (NvgWidget b : sideButtons) {
            if (b.label().equals(label)) {
                return b;
            }
        }
        for (Row row : rows) {
            if (row.label().equals(label)) {
                return row.widget();
            }
        }
        return null;
    }

    /**
     * 走真实事件路径点一下某个选项（按下 → 松开），返回是否点到。
     * <p>【为什么给 harness 这个口子】控件是自绘的，没有原版 {@code children()} 可以找 ——
     * "按钮 → 写配置 → 渲染重读"这条链要能自动化验，就必须从界面自己这里拿到控件。
     */
    public boolean clickOption(String label) {
        NvgWidget w = widgetFor(label);
        if (w == null) {
            return false;
        }
        double cx = w.x() + w.width() / 2.0;
        double cy = w.y() + w.height() / 2.0;
        mouseClicked(cx, cy, 0);
        mouseReleased(cx, cy, 0);
        return true;
    }

    /** 走真实事件路径拖一下滑条（按下 → 拖到 ratio 处 → 松开）—— 验的是拖拽，不是点击。 */
    public boolean dragOption(String label, double ratio) {
        NvgWidget w = widgetFor(label);
        if (w == null) {
            return false;
        }
        double y = w.y() + w.height() / 2.0;
        double startX = w.x() + 2.0;
        double endX = w.x() + 2.0 + Math.max(0.0, Math.min(1.0, ratio)) * (w.width() - 4.0);
        mouseClicked(startX, y, 0);
        mouseDragged(endX, y, 0, endX - startX, 0);
        mouseReleased(endX, y, 0);
        return true;
    }

    /** 当前这一页有哪些选项（按玩家看到的顺序）。日志里留一份。 */
    public List<String> optionLabels() {
        return rows.stream().map(Row::label).toList();
    }

    /** 选项名 + 位置 + 显示值。布局是算出来的，"框压到边上了"必须能不靠眼睛查出来。 */
    public List<String> optionDump() {
        return rows.stream().map(r -> {
            NvgWidget w = r.widget();
            return r.label() + "=" + Math.round(w.x()) + "," + Math.round(w.y())
                    + " " + Math.round(w.width()) + "x" + Math.round(w.height())
                    + " 值[" + w.value() + "]";
        }).toList();
    }

    // ------------------------------------------------------------------
    // 几何：按画布算，不写死
    // ------------------------------------------------------------------

    private int contentLeft() {
        return PAD + SIDE_W + 8;
    }

    private int contentRight() {
        return Math.max(contentLeft() + 120, this.width - PAD);
    }

    private int colW() {
        return Math.max(70, (contentRight() - contentLeft() - 8) / 2);
    }

    private int controlW() {
        return Math.max(52, Math.min(96, colW() * 3 / 5));
    }

    private int labelW() {
        return Math.max(24, colW() - controlW() - 6);
    }

    private int controlX(int column) {
        return contentLeft() + column * (colW() + 8) + labelW() + 4;
    }

    /** 预览面板高度：布局页要摞三张卡，别人一张卡就够；画布矮时再压一压。 */
    private int previewHeight() {
        int want = section == Section.LAYOUT ? 96 : 44;
        return Math.max(30, Math.min(want, this.height / 3));
    }

    private int rowsTop() {
        return 36 + previewHeight() + 6;
    }

    private int rowH() {
        return 18;
    }

    private int rowStep(int maxRows) {
        int room = this.height - rowsTop() - 24;
        return Math.max(13, Math.min(22, room / Math.max(1, maxRows)));
    }

    // ------------------------------------------------------------------
    // 控件工厂：配置读写都从这里进出
    // ------------------------------------------------------------------

    private NvgToggle bool(ForgeConfigSpec.BooleanValue config, boolean shown) {
        return new NvgToggle("", config::get, on -> {
            config.set(on);
            changed();
        });
    }

    /** 外观开关：配置里是 -1/0/1，界面只显示"开/关"，点了就写死 1/0。 */
    private NvgToggle styleSwitch(ForgeConfigSpec.IntValue config, boolean shown) {
        return new NvgToggle("", () -> config.get() < 0 ? shown : config.get() == 1, on -> {
            config.set(on ? 1 : 0);
            changed();
        });
    }

    private NvgSlider number(ForgeConfigSpec.IntValue config, int shown, int min, int max, int step,
                             String suffix) {
        return new NvgSlider("", min, max, step,
                () -> (double) currentInt(config, shown), v -> {
                    config.set((int) Math.round(v));
                    changed();
                },
                v -> Integer.toString((int) Math.round(v)) + suffix);
    }

    private NvgSlider styleNumber(ForgeConfigSpec.IntValue config, int shown, int min, int max,
                                  int step, String suffix) {
        return number(config, shown, min, max, step, suffix);
    }

    private NvgSlider decimal(ForgeConfigSpec.DoubleValue config, double shown, double min, double max) {
        return new NvgSlider("", min, max, 1,
                // 【这里踩过：值供给器不能读快照】从前写的是 `() -> shown` —— 那是打开页面时
                // 拍下的一次性快照，拖拽写进 TOML 的值永远不回灌到界面，圆钮就一动不动，
                // 玩家看到的是"这条滑条拖不动"。跟 number/time 一样读活配置。
                () -> currentDouble(config, shown),
                v -> {
                    config.set((double) Math.round(v));
                    changed();
                },
                v -> Math.round(v) + "px");
    }

    private NvgSlider time(ForgeConfigSpec.LongValue config, long shown, long min, long max, long step) {
        return new NvgSlider("", min, max, step,
                () -> (double) currentLong(config, shown), v -> {
                    config.set(Math.round(v));
                    changed();
                },
                v -> Math.round(v) + "ms");
    }

    private NvgSlider styleTime(ForgeConfigSpec.LongValue config, long shown, long min, long max, long step) {
        return time(config, shown, min, max, step);
    }

    /**
     * 卡片缩放：0 是个真值（「自动」），格式与锚点那一档同形。
     * <p>
     * 【为什么要把 1..49 夹到 50】滑条上 0 是"自动"，1..49 是空档 —— 不夹的话界面会显示
     * "10%" 而生效的是 50%（{@code LayoutSettings#sanitized} 会夹），那就是"设了等于没设"。
     */
    private NvgSlider percent(ForgeConfigSpec.IntValue config, int shown) {
        return new NvgSlider("", LayoutSettings.AUTO_SCALE, LayoutSettings.MAX_SCALE_PERCENT, 5,
                () -> (double) config.get(),
                v -> {
                    int pct = (int) Math.round(v);
                    config.set(pct <= LayoutSettings.AUTO_SCALE ? LayoutSettings.AUTO_SCALE
                            : Math.max(LayoutSettings.MIN_SCALE_PERCENT, pct));
                    changed();
                },
                v -> v <= LayoutSettings.AUTO_SCALE ? "自动" : Math.round(v) + "%");
    }

    /** 竖条位置：-1 是个真值（「自动」），所以范围与格式都跟普通滑条不同。 */
    private NvgSlider anchor(ForgeConfigSpec.IntValue config, int shown) {
        return new NvgSlider("", LayoutSettings.AUTO_LEFT_EDGE, 600, 1,
                () -> config.get() < LayoutSettings.AUTO_LEFT_EDGE
                        ? (double) LayoutSettings.AUTO_LEFT_EDGE : (double) config.get(),
                v -> {
                    config.set((int) Math.round(v));
                    changed();
                },
                v -> v < 0 ? "自动" : Integer.toString((int) Math.round(v)));
    }

    private <E extends Enum<E>> NvgButton cycle(ForgeConfigSpec.EnumValue<E> config, E[] values,
                                                Function<E, String> name) {
        return new NvgButton("", () -> name.apply(config.get()), () -> {
            E current = config.get();
            int i = 0;
            for (int k = 0; k < values.length; k++) {
                if (values[k] == current) {
                    i = k;
                }
            }
            config.set(values[(i + 1) % values.length]);
            changed();
        });
    }

    private NvgTextField color(ForgeConfigSpec.ConfigValue<String> config, int effectiveArgb) {
        return new NvgTextField("", () -> {
            String raw = config.get();
            return raw == null || raw.isBlank() ? argbText(effectiveArgb) : raw;
        }, text -> {
            String trimmed = text.trim();
            if (trimmed.isEmpty()) {
                config.set("");
                changed();
            } else if (StyleOverrides.parseArgb(trimmed).isPresent()) {
                config.set(trimmed.startsWith("#") ? trimmed : "#" + trimmed);
                changed();
            }
        });
    }

    /** 显示用的当前值：配置里"没改过"（-1）时给生效值，界面因此永远不显示第三态。 */
    private static int currentInt(ForgeConfigSpec.IntValue config, int effective) {
        return config.get() < 0 ? effective : config.get();
    }

    private static long currentLong(ForgeConfigSpec.LongValue config, long effective) {
        return config.get() < 0 ? effective : config.get();
    }

    /** 小数配置（目前只有卡片间距）的活值：跟 int/long 同一条规则，-1 才算没改过。 */
    private static double currentDouble(ForgeConfigSpec.DoubleValue config, double effective) {
        return config.get() < 0 ? effective : config.get();
    }

    // ------------------------------------------------------------------
    // 小工具
    // ------------------------------------------------------------------

    private static String argbText(int argb) {
        return String.format("#%08X", argb);
    }

    private static String sideName(LayoutSettings.Side side) {
        return side == LayoutSettings.Side.RIGHT ? "贴右（右缘齐）" : "贴左（竖条成线）";
    }

    private static String appearName(LayoutSettings.Appear appear) {
        return appear == LayoutSettings.Appear.CLIP ? "拉幕（先露图标）" : "火车（数字端先出）";
    }

    private static String countName(CountFormat format) {
        return switch (format) {
            case X_PREFIX -> "×64";
            case PLAIN -> "64";
            case ABBREVIATED -> "+1.2K";
            default -> "+64";
        };
    }

    /** 每次改动都让渲染立刻重读，不等那一秒的重读间隔。 */
    private static void changed() {
        CardStage.INSTANCE.refreshStyle();
    }
}
