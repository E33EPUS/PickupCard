package com.niuqu.pickupcard.config;

import com.niuqu.pickupcard.layout.LayoutSettings;
import com.niuqu.pickupcard.layout.StackLayout;
import com.niuqu.pickupcard.notice.PickupCardSettings;
import com.niuqu.pickupcard.render.CardStage;
import com.niuqu.pickupcard.render.nvg.NvgCardPainter;
import com.niuqu.pickupcard.style.StyleModel;
import com.niuqu.pickupcard.style.StyleOverrides;
import com.niuqu.pickupcard.text.CountFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 游戏内配置界面：<b>左侧一列分类，右侧是实时预览 + 选项行</b>（同一行里左边标签、右边控件）。
 *
 * <p>【这个形状从哪来的】2026-09-17 用户的原话："以前那张界面是有明确分类的，左边一列标签，
 * 右边放实时预览和配置项 —— 那个效果很好；现在这个我不知道都是些什么东西。抄 pickupnotice
 * 的配置界面。" 那份旧界面（= 从发行 jar 恢复出来的 v0.1.0）的分段是
 * <b>通用 / 动画 / 位置与堆叠 / 外观</b>，选项行是"左边一句人话、右边一个控件"，
 * 每个选项悬停给一句解释 —— 这里照抄的就是这三件事。
 *
 * <p>【"跟随主题"为什么从界面上消失了】旧界面里每个键就是一个<b>具体值</b>，没有"跟不跟
 * 主题"这一说；而上一版把设计参数（竖条宽 / 内缩 / 内边距 / 框间距）全摊到界面上，还让每项
 * 都有第三种状态"跟随主题"—— 用户的原话是"我是真不理解…'跟随主题'配置项"。现在的规矩：
 * <ul>
 *   <li>界面显示的是<b>生效值</b>（主题 + 你改过的），不显示"没改过"这个状态；</li>
 *   <li>你拨了哪一项，那一项就写进 TOML 变成固定值（想回到主题：TOML 里改回 -1 或空串）；</li>
 *   <li>几何参数（竖条宽 / 竖条内缩 / 左右内边距 / 框间距）<b>不进界面</b> —— 它们是设计参数，
 *       归主题 JSON（资源包可改），摆在玩家面前只会把卡调歪。</li>
 * </ul>
 *
 * <p>【它改的是配置，不是渲染】拨动 → 写配置 → 通知渲染立刻重读。界面自己不存一份，
 * 否则"界面显示 5、实际画 9"迟早出现。
 *
 * <p>【控件都在 (0,0) 出生】位置统一由 {@link #layoutRows()} 按"第几行第几列"摆 ——
 * 一个控件自己知道该在哪，就会和另一处的坐标打架（上一版就是这样漂的）。
 */
public final class PickupCardConfigScreen extends Screen {

    // ---- 左侧分类 ----
    private static final int PAD = 6;
    private static final int SIDE_W = 86;
    private static final int SIDE_TOP = 30;
    private static final int SIDE_H = 20;
    private static final int SIDE_STEP = 22;

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
    /** 悬停提示：控件 → 一句人话。标签只能写短词，短词一定有歧义。 */
    private final Map<AbstractWidget, String> hints = new LinkedHashMap<>();
    /** 一行 = 一个标签 + 一个控件（控件摆在右半格）。 */
    private final List<Row> rows = new ArrayList<>();

    private record Row(String label, AbstractWidget widget) {
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
        hints.clear();
        rows.clear();
        PickupCardConfig.Values v = PickupCardConfig.VALUES;

        int y = SIDE_TOP;
        for (Section s : Section.values()) {
            Section target = s;
            addRenderableWidget(Button.builder(
                            Component.literal((s == section ? "▸ " : "  ") + s.label),
                            b -> {
                                section = target;
                                rebuildWidgets();
                            })
                    .bounds(PAD, y, SIDE_W, SIDE_H).build());
            y += SIDE_STEP;
        }
        addRenderableWidget(Button.builder(Component.literal("完成"), b -> onClose())
                .bounds(PAD, this.height - 24, SIDE_W, 18).build());

        switch (section) {
            case GENERAL -> buildGeneral(v);
            case ANIM -> buildAnim(v);
            case LAYOUT -> buildLayout(v);
            case LOOK -> buildLook(v);
        }
        layoutRows();
    }

    /**
     * 「通用」：最上面那几个开关。<b>总开关与"显示物品名"是旧版最好懂的两项</b>，
     * 上一版把这两项弄丢了，界面上只剩一堆几何滑条。
     */
    private void buildGeneral(PickupCardConfig.Values v) {
        PickupCardSettings eff = PickupCardConfig.snapshot();
        cell("总开关", boolButton(v.enabled, eff.enabled()), "关掉之后捡东西不再弹卡");
        cell("显示物品名", boolButton(v.showItemName, eff.showItemName()),
                "关掉只剩竖条 + 图标 + 数量，卡片会明显变窄");
        cell("显示物品ID", boolButton(v.showItemId, eff.showItemId()),
                "显示 minecraft:stone 这种 ID 而不是它的名字");
        cell("名字最大宽度", intSlider(v.nameMaxWidth, eff.nameMaxWidth(), 0, 400),
                "像素。0 = 按屏宽自动；超出就截断加省略号");
        cell("数量写法", enumButton(v.countFormat, CountFormat.values(),
                PickupCardConfigScreen::countName), "数量怎么显示：+64 / ×64 / 64 / +1.2K");
        cell("卡片间距", pxSlider(v.separation, PickupCardConfig.layoutSnapshot().separation(), 0, 16),
                "两张卡之间的空隙；跟卡内「框间距」不是一回事");
    }

    /** 「动画」：入场 / 数字跳动 / 停留 / 消失 —— 全是"看得见"的时长。 */
    private void buildAnim(PickupCardConfig.Values v) {
        StyleModel style = CardStage.INSTANCE.previewStyle();
        PickupCardSettings eff = PickupCardConfig.snapshot();
        cell("入场时长", longSlider(v.stEnterMs, style.enterMs(), 0, 2_000, 40),
                "卡片从竖条后面滑出来的总时间（默认 480ms）");
        cell("入场动画", styleToggle(v.stEnterEnabled, style.enterEnabled()),
                "关掉就是直接出现（动画敏感的人可以关）");
        cell("停留时长", longSlider(v.holdMs, eff.holdMs(), 500, 10_000, 250),
                "一张卡在屏幕上待多久");
        cell("消失时长", longSlider(v.exitMs, eff.exitMs(), 0, 2_000, 20),
                "消失时淡出多久；0 = 直接消失");
        cell("数字跳动", styleToggle(v.stBumpEnabled, style.bumpEnabled()),
                "连续捡同一种东西时，数字弹一下");
        cell("跳动时长", longSlider(v.stBumpMs, style.bumpMs(), 0, 1_000, 20),
                "数字弹一下持续多久");
    }

    /** 「位置与堆叠」：停在哪、怎么展开、卡与卡的距离、同屏几张。 */
    private void buildLayout(PickupCardConfig.Values v) {
        LayoutSettings eff = PickupCardConfig.layoutSnapshot();
        PickupCardSettings settings = PickupCardConfig.snapshot();
        cell("贴边", enumButton(v.stickTo, LayoutSettings.Side.values(),
                PickupCardConfigScreen::sideName),
                "左 = 竖条成一条竖线；右 = 卡的右缘齐、竖条参差");
        cell("竖条位置", anchorSlider(v.leftEdge, eff.leftEdge()),
                "竖条左缘停在哪；「自动」= 跟着画布宽度算");
        cell("展开方式", enumButton(v.appearMode, LayoutSettings.Appear.values(),
                PickupCardConfigScreen::appearName),
                "火车 = 整块滑出来；拉幕 = 可见范围一点点变宽（先露图标）");
        cell("同屏上限", intSlider(v.maxOnScreen, settings.maxOnScreen(), 1, 16),
                "同时在屏最多几张。GUI 缩放越大、画布越小，放得下的越少");
        cell("排队上限", readOnly("暂未实现"), "现在超出同屏上限的那张会被挤掉，不做排队");
    }

    /**
     * 「外观」：只放"看得懂、调了看得出"的。
     * <p>
     * 竖条宽度 / 竖条内缩 / 左右内边距 / 框间距这些是设计参数，归主题 JSON ——
     * 摆在玩家面前只会把卡调歪（用户的原话："不知道都是些什么东西"）。
     */
    private void buildLook(PickupCardConfig.Values v) {
        StyleModel style = CardStage.INSTANCE.previewStyle();
        cell("卡片厚薄", intSlider(v.stPaddingV, style.paddingV(), 0, 8),
                "上下各留多少；调大卡片变厚，图标大小不变");
        cell("图标大小", intSlider(v.stIconSize, style.iconSize(), 8, 64),
                "原版物品图标是 16 —— 取 16 或它的整数倍最清晰");
        cell("圆角", intSlider(v.stCornerRadius, style.cornerRadius(), 0, 16),
                "三个框的圆角半径；调到很大就变成胶囊");
        cell("框粗细", intSlider(v.stBorderWidth, style.borderWidth(), 0, 4),
                "框描边的粗细；0 = 不描边");
        cell("底色（上）", colorBox(v.stFillTop, style.fillTop()),
                "卡面底色上端，格式 #AARRGGBB；留空 = 用主题里的");
        cell("底色（下）", colorBox(v.stFillBottom, style.fillBottom()),
                "下端。和上面写成一样就是纯色");
        cell("框色", colorBox(v.stBorder, style.border()), "框描边的颜色");
        cell("物品名颜色", colorBox(v.stNameColor, style.nameColor()), "名字的颜色");
    }

    /** 把一个控件登记成一行：标签由 {@link #drawLabels} 画在它左边，提示挂在悬停上。 */
    private void cell(String label, AbstractWidget widget, String hint) {
        rows.add(new Row(label, widget));
        hints.put(widget, hint);
        addRenderableWidget(widget);
    }

    /** 逐行摆：一行两个格子，行高按画布自适应（guiScale 5 时画布只有 144 高）。 */
    private void layoutRows() {
        int maxRows = Math.max(1, (rows.size() + 1) / 2);
        int step = rowStep(maxRows);
        for (int i = 0; i < rows.size(); i++) {
            AbstractWidget w = rows.get(i).widget();
            w.setX(controlX(i % 2));
            w.setY(rowsTop() + (i / 2) * step);
        }
    }

    // ------------------------------------------------------------------
    // 画
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        renderBackground(gui);
        drawChrome(gui);
        drawLabels(gui);
        super.render(gui, mouseX, mouseY, partialTick);
        gui.drawString(this.font, hintText(mouseX, mouseY), PAD, this.height - 12, 0xFF9AA4AD);
    }

    /** 底、标题、预览面板。标题与提示都放在内容区左上（旧版就是这个位置）。 */
    private void drawChrome(GuiGraphics gui) {
        gui.fill(0, 0, this.width, this.height, 0xE0101218);
        gui.drawString(this.font, this.title, contentLeft(), 8, 0xFFFFFFFF);
        gui.drawString(this.font, "改动即时生效，拨过的项会记进 config/pickupcard-client.toml",
                contentLeft(), 18, 0xFF7D8695);

        int py = 30;
        int ph = previewHeight();
        gui.fill(contentLeft() - 2, py - 2, contentRight() + 2, py + ph + 2, 0x40202A38);
        gui.drawString(this.font, "预览", contentLeft() + 2, py, 0xFF9AA4AD);
        if (section == Section.LAYOUT) {
            renderStackPreview(gui, contentLeft() + 2, py + 10,
                    contentRight() - contentLeft() - 6, ph - 12);
        } else {
            renderCardPreview(gui, contentLeft() + 2, py + 10);
        }
    }

    private void drawLabels(GuiGraphics gui) {
        for (Row row : rows) {
            AbstractWidget w = row.widget();
            int labelLeft = w.getX() - labelW() - 4;
            String text = this.font.plainSubstrByWidth(row.label(), labelW());
            gui.drawString(this.font, text, labelLeft, w.getY() + (w.getHeight() - 8) / 2 + 1,
                    0xFFBFC6D4, true);
        }
    }

    /** 悬停谁就说谁 —— 这是这个界面唯一能自我解释的地方。 */
    private Component hintText(int mouseX, int mouseY) {
        for (Map.Entry<AbstractWidget, String> e : hints.entrySet()) {
            if (e.getKey().isMouseOver(mouseX, mouseY)) {
                return Component.literal(e.getValue());
            }
        }
        return Component.literal(switch (section) {
            case GENERAL -> "这些改的是「弹不弹、显示什么」";
            case ANIM -> "这些改的是卡片怎么出现、数字怎么跳";
            case LAYOUT -> "这些改的是卡片停在哪、同时显示几张（预览就是一摞卡）";
            case LOOK -> "这些改的是卡片长什么样（预览就是当前设置画出来的）";
        });
    }

    /** 单张样例卡：经验卡（那一档会亮微光），拨外观/动画时看它。 */
    private void renderCardPreview(GuiGraphics gui, int x, int y) {
        StyleModel style = CardStage.INSTANCE.previewStyle();
        boolean showName = PickupCardConfig.snapshot().showItemName();
        float w = showName ? Math.max(60f, Math.min(150f, this.width - x - PAD - 4)) : 58f;
        NvgCardPainter.paintPreview(gui, style, x, y, w,
                new ItemStack(Items.NETHER_STAR), "经验", "+137", 0xFF7DFF8A, true, showName);
    }

    /**
     * 一摞卡：<b>真的排布函数 + 真的间距</b>画三张不同宽度的卡。
     * <p>
     * 【为什么这里必须调真函数】"贴边 / 间距 / 同屏"这些键的效果只在多张卡之间看得出来：
     * 左对齐时三条竖条成一条竖线、右对齐时右缘齐而竖条参差。自己画一遍就等于第二份排版实现。
     */
    private void renderStackPreview(GuiGraphics gui, int x, int y, int w, int h) {
        StyleModel style = CardStage.INSTANCE.previewStyle();
        LayoutSettings layout = PickupCardConfig.layoutSnapshot();
        boolean showName = PickupCardConfig.snapshot().showItemName();
        String[][] samples = {{"经验", "+137"}, {"信标", "+1"}, {"石头", "+64"}};
        int[] accents = {0xFF7DFF8A, 0xFF55EBFF, 0xFF9AA4AD};
        float[] widths = showName ? new float[] {132f, 106f, 84f} : new float[] {58f, 52f, 50f};
        ItemStack[] icons = {
                new ItemStack(Items.NETHER_STAR), new ItemStack(Items.BEACON), new ItemStack(Items.STONE)};

        List<StackLayout.Size> sizes = new ArrayList<>();
        for (int i = widths.length - 1; i >= 0; i--) {      // 最新的排第一（StackLayout 的约定）
            sizes.add(new StackLayout.Size(widths[i], style.boxHeight()));
        }
        for (StackLayout.Slot slot : StackLayout.stack(sizes, Math.max(1, w), h, layout,
                6, 4, layout.separation())) {
            int i = widths.length - 1 - slot.index();
            NvgCardPainter.paintPreview(gui, style, x + slot.x(), y + slot.y(), slot.width(),
                    icons[i], samples[i][0], samples[i][1], accents[i], i == 0, showName);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    // ------------------------------------------------------------------
    // 给 dev harness 的只读入口（不是给渲染层用的）
    // ------------------------------------------------------------------

    /**
     * 按选项名找控件。
     * <p>
     * 【为什么需要它】标签现在画在控件<b>左边</b>（旧版的形状），不在控件的消息里 ——
     * 所以"按标签点一下"这件事只能问界面自己。harness 靠它发真实鼠标事件，
     * 把"按钮 → 写配置 → 渲染重读"整条链一起验掉；找不到就报，不会静默点空。
     */
    public AbstractWidget widgetFor(String label) {
        for (Row row : rows) {
            if (row.label().equals(label)) {
                return row.widget();
            }
        }
        return null;
    }

    /** 当前这一页有哪些选项（按玩家看到的顺序）。日志里留一份，截图看不出"第 2 页有没有那几项"。 */
    public List<String> optionLabels() {
        return rows.stream().map(Row::label).toList();
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

    /** 预览面板高度：布局页要摞三张卡，别的地方一张卡就够；画布矮时再压一压。 */
    private int previewHeight() {
        int want = section == Section.LAYOUT ? 96 : 34;
        return Math.max(26, Math.min(want, this.height - rowsTopFor(want) - 40));
    }

    private int rowsTopFor(int previewH) {
        return 30 + previewH + 10;
    }

    private int rowsTop() {
        return 30 + previewHeight() + 10;
    }

    private int rowH() {
        return 18;
    }

    private int rowStep(int maxRows) {
        int room = this.height - rowsTop() - 26;
        return Math.max(14, Math.min(22, room / Math.max(1, maxRows)));
    }

    // ------------------------------------------------------------------
    // 控件：**消息里只有值，标签由 drawLabels 画在左边**
    // ------------------------------------------------------------------

    /**
     * 整数滑条。{@code shown} 是"该显示成多少"：外观项传<b>生效值</b>（主题 + 玩家改动），
     * 别的项传配置值 —— 界面因此永远不显示"跟随主题"这个第三态。
     */
    private AbstractSliderButton intSlider(ForgeConfigSpec.IntValue config, int shown, int min, int max) {
        int lo = Math.max(0, min);
        int span = max - lo + 1;
        AbstractSliderButton w = new AbstractSliderButton(0, 0, controlW(), rowH(), Component.empty(),
                Math.max(0.0, Math.min(1.0, (shown - lo + 1) / (double) span))) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal(Integer.toString(toInt(this.value, min, max))));
            }

            @Override
            protected void applyValue() {
                config.set(toInt(this.value, min, max));
                changed();
            }
        };
        w.setMessage(Component.literal(Integer.toString(shown)));
        return w;
    }

    /** 时长滑条（毫秒，带步进吸附）。 */
    private AbstractSliderButton longSlider(ForgeConfigSpec.LongValue config, long shown,
                                            long min, long max, long step) {
        AbstractSliderButton w = new AbstractSliderButton(0, 0, controlW(), rowH(), Component.empty(),
                Math.max(0.0, Math.min(1.0, (shown - min) / (double) (max - min)))) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal(snap(this.value, min, max, step) + "ms"));
            }

            @Override
            protected void applyValue() {
                config.set(snap(this.value, min, max, step));
                changed();
            }
        };
        w.setMessage(Component.literal(shown + "ms"));
        return w;
    }

    /** 像素滑条（写进 Double 的配置键，比如卡片间距）。 */
    private AbstractSliderButton pxSlider(ForgeConfigSpec.DoubleValue config, float shown,
                                          double min, double max) {
        AbstractSliderButton w = new AbstractSliderButton(0, 0, controlW(), rowH(), Component.empty(),
                Math.max(0.0, Math.min(1.0, (shown - min) / (max - min)))) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal(Math.round(min + this.value * (max - min)) + "px"));
            }

            @Override
            protected void applyValue() {
                config.set((double) Math.round(min + this.value * (max - min)));
                changed();
            }
        };
        w.setMessage(Component.literal(Math.round(shown) + "px"));
        return w;
    }

    /** 开关（两态）：显示生效状态，点一下写具体值。 */
    private Button boolButton(ForgeConfigSpec.BooleanValue config, boolean shown) {
        return Button.builder(Component.literal(shown ? "开" : "关"), b -> {
                    boolean next = !config.get();
                    config.set(next);
                    changed();
                    b.setMessage(Component.literal(next ? "开" : "关"));
                })
                .bounds(0, 0, controlW(), rowH()).build();
    }

    /** 外观开关：配置里是 -1/0/1，界面只显示"开/关"，点了就写死 1/0。 */
    private Button styleToggle(ForgeConfigSpec.IntValue config, boolean shown) {
        return Button.builder(Component.literal(shown ? "开" : "关"), b -> {
                    config.set(config.get() == 1 ? 0 : 1);
                    changed();
                    b.setMessage(Component.literal(config.get() == 1 ? "开" : "关"));
                })
                .bounds(0, 0, controlW(), rowH()).build();
    }

    private <E extends Enum<E>> Button enumButton(ForgeConfigSpec.EnumValue<E> config, E[] values,
                                                 Function<E, String> name) {
        return Button.builder(Component.literal(name.apply(config.get())), b -> {
                    E current = config.get();
                    int i = 0;
                    for (int k = 0; k < values.length; k++) {
                        if (values[k] == current) {
                            i = k;
                        }
                    }
                    E next = values[(i + 1) % values.length];
                    config.set(next);
                    changed();
                    b.setMessage(Component.literal(name.apply(next)));
                })
                .bounds(0, 0, controlW(), rowH()).build();
    }

    /** 竖条位置：最左边是「自动」（-1，跟着画布算），其余是绝对像素。 */
    private AbstractSliderButton anchorSlider(ForgeConfigSpec.IntValue config, int shown) {
        final int max = 600;
        AbstractSliderButton w = new AbstractSliderButton(0, 0, controlW(), rowH(), Component.empty(),
                Math.max(0.0, Math.min(1.0, (shown + 1) / (double) (max + 1)))) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal(anchorName((int) Math.round(this.value * (max + 1)) - 1)));
            }

            @Override
            protected void applyValue() {
                config.set((int) Math.round(this.value * (max + 1)) - 1);
                changed();
            }
        };
        // 【构造完必须自己补一次标签】AbstractSliderButton 构造期间不跑 updateMessage，
        // 而 1.20.1 里它没有公开的 getValue() —— 所以初值只能从调用方传进来。
        w.setMessage(Component.literal(anchorName(shown)));
        return w;
    }

    /**
     * 颜色输入框：显示生效色的 {@code #AARRGGBB}，打进合法值立刻生效。
     * <p>
     * 【写坏了怎么办】不写 —— 值留在上一次合法的状态。半应用一个打错的颜色，
     * 比"这次输入没生效"更让人困惑。
     */
    private EditBox colorBox(ForgeConfigSpec.ConfigValue<String> config, int effectiveArgb) {
        EditBox box = new EditBox(this.font, 0, 0, controlW(), rowH(), Component.empty());
        box.setMaxLength(9);
        box.setValue(argbText(effectiveArgb));
        box.setResponder(text -> {
            String trimmed = text.trim();
            if (trimmed.isEmpty()) {
                config.set("");
                changed();
            } else if (StyleOverrides.parseArgb(trimmed).isPresent()) {
                config.set(trimmed.startsWith("#") ? trimmed : "#" + trimmed);
                changed();
            }
        });
        return box;
    }

    /** 只读控件：用来把"这件事我们还没做"明说，而不是假装它不存在。 */
    private Button readOnly(String text) {
        return Button.builder(Component.literal(text), b -> {
                })
                .bounds(0, 0, controlW(), rowH()).build();
    }

    // ------------------------------------------------------------------
    // 小工具
    // ------------------------------------------------------------------

    /** 位置 → 整数：min..max 线性铺开（没有"哨兵"格，界面上不存在第三态）。 */
    private static int toInt(double pos, int min, int max) {
        int lo = Math.max(0, min);
        int value = (int) Math.round(lo + pos * (max - lo));
        return Math.max(lo, Math.min(max, value));
    }

    /** 位置 → 值：线性铺开并按 step 吸附。 */
    private static long snap(double pos, long min, long max, long step) {
        long raw = min + Math.round(pos * (max - min));
        long snapped = Math.round(raw / (double) step) * step;
        return Math.max(min, Math.min(max, snapped));
    }

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

    private static String anchorName(int value) {
        return value < 0 ? "自动" : Integer.toString(value);
    }

    /** 每次改动都让渲染立刻重读，不等那一秒的重读间隔。 */
    private static void changed() {
        CardStage.INSTANCE.refreshStyle();
    }
}
