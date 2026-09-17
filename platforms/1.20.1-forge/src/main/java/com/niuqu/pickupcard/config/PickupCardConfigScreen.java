package com.niuqu.pickupcard.config;

import com.niuqu.pickupcard.layout.LayoutSettings;
import com.niuqu.pickupcard.layout.StackLayout;
import com.niuqu.pickupcard.notice.MergeMode;
import com.niuqu.pickupcard.notice.PickupCardSettings;
import com.niuqu.pickupcard.rarity.RarityAccent;
import com.niuqu.pickupcard.render.CardStage;
import com.niuqu.pickupcard.render.nvg.NvgCardPainter;
import com.niuqu.pickupcard.render.nvg.ui.NvgButton;
import com.niuqu.pickupcard.render.nvg.ui.NvgPalette;
import com.niuqu.pickupcard.render.nvg.ui.ConfigLayout;
import com.niuqu.pickupcard.render.nvg.ui.NvgScroll;
import com.niuqu.pickupcard.render.nvg.ui.ScrollMath;
import com.niuqu.pickupcard.render.nvg.ui.NvgSlider;
import com.niuqu.pickupcard.render.nvg.ui.NvgTextField;
import com.niuqu.pickupcard.render.nvg.ui.NvgToggle;
import com.niuqu.pickupcard.render.nvg.ui.NvgUi;
import com.niuqu.pickupcard.render.nvg.ui.NvgWidget;
import com.niuqu.pickupcard.render.nvg.ui.Tween;
import com.niuqu.pickupcard.style.StyleModel;
import com.niuqu.pickupcard.style.StyleOverrides;
import com.niuqu.pickupcard.text.CountFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

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

    // ---- 界面尺度 ----
    private static final int PAD = 6;
    /**
     * 短动画的时长（毫秒）。
     * <p>【为什么都在 100~200ms 这一档】这些动画只在"我按了东西"的<em>那一瞬间</em>回答问题
     * （换页了 / 悬停到这一行了 / 强调条挪过去了）。超过 200ms 它就开始和玩家的下一次操作
     * 抢时间 —— 手感从"跟手"变成"卡"。反过来短于 80ms 就等于没有。
     */
    private static final long PAGE_MS = 180L;
    private static final long HOVER_IN_MS = 110L;
    private static final long HOVER_OUT_MS = 150L;
    private static final long TAB_MS = 160L;
    private static final long PREVIEW_MS = 200L;
    /** 换页时内容向上滑多少像素（只滑一点点：滑动是"从哪儿来"的提示，不是主体）。 */
    private static final float PAGE_SLIDE = 5f;

    /** 给 harness 看的列几何（只读）：三列到底摆在哪、预览收没收起。 */
    public String columnDump() {
        ConfigLayout lo = layout();
        return String.format(java.util.Locale.ROOT,
                "画布 %dx%d 缩放%.0f | tabs=(x%.0f w%.0f h%.0f%s) items=(x%.0f w%.0f h%.0f) preview=%s%s 行数=%d 可见行=%d 偏移=%.0f",
                this.width, this.height, Minecraft.getInstance().getWindow().getGuiScale(),
                lo.tabs().x(), lo.tabs().w(), lo.tabs().h(), lo.tabsOnTop() ? " 顶排" : "",
                lo.items().x(), lo.items().w(), lo.items().h(),
                lo.previewVisible() ? String.format(java.util.Locale.ROOT, "w%.0f", lo.preview().w())
                        : "收起",
                lo.previewVisible() ? (lo.switcherVisible() ? " 切样例行" : " 无切样例行") : "",
                rows.size(),
                Math.max(1, (int) (lo.items().h() / Math.max(1, rowStep(Math.max(1, rows.size()))))),
                itemsScroll == null ? 0f : itemsScroll.offset());
    }

    /** 给 harness 用：让配置项那一列滚一格（走的是和真人滚轮同一条路）。 */
    public boolean scrollForHarness(double delta) {
        return mouseScrolled(this.width / 2.0, this.height / 2.0, delta);
    }

    /** 给 harness 用：当前滚动偏移。 */
    public float scrollOffsetForHarness() {
        return itemsScroll == null ? 0f : itemsScroll.offset();
    }

    /** 给 harness 用：现在是哪一页、预览是哪个样例（换页/换样例的验证要能读出来）。 */
    public String stateDump() {
        ConfigLayout lo = layout();
        ConfigLayout.Rect card = lo.previewCard();
        float hover = 0f;
        for (Row row : rows) {
            hover = Math.max(hover, row.hover.at(now));
        }
        return String.format(java.util.Locale.ROOT,
                "页=%s 样例=%s 预览卡区=(x%.0f y%.0f w%.0f h%.0f) 换页=%.2f 强调条=%.2f 换样例=%.2f 最大行悬停=%.2f",
                section.label, sample.label, card.x(), card.y(), card.w(), card.h(),
                pageAnim.at(now), tabAccentAnim.at(now), previewAnim.at(now), hover);
    }

    /**
     * 给 harness 用：<b>假装鼠标停在某一行上</b>（null = 取消）。
     * <p>
     * 【为什么需要它】渲染用的鼠标位置来自系统光标，自动化跑的时候光标在别处 ——
     * "悬停高亮这一小段动画"因此只能靠眼睛看，而那正是"看着不对劲但说不清"的那一类。
     * 这里让 harness 能把它定住，截图与读数才拿得到。
     */
    public void hoverForHarness(String label) {
        forcedHover = label;
    }

    /** 三列几何（每次现算，纯函数）。 */
    private ConfigLayout layout() {
        return ConfigLayout.compute(this.width, this.height);
    }

    /** 配置项那一列的滚动视口；每帧按当前几何重建（画布会变，视口跟着变）。 */
    private NvgScroll itemsScroll;

    /** 这一帧配置项内容有多高（滚动的依据）。 */
    private float itemsContentHeight() {
        return rows.size() * rowStep(Math.max(1, rows.size()));
    }

    /**
     * 分类。
     * <p>【为什么每页带一句说明】底部那行字是这个界面唯一的自我解释：悬停在标签上时应该说
     * <em>那一页</em>是干嘛的，而不是当前这页的 —— 否则"点了没反应"和"说明没变"长得一样。
     */
    private enum Section {
        GENERAL("通用", "这些改的是「弹不弹、显示什么、什么算同一样东西」"),
        ANIM("动画", "这些改的是卡片怎么出现、数字怎么跳"),
        LAYOUT("位置与堆叠", "这些改的是卡片停在哪、同时显示几张（预览就是一摞卡）"),
        LOOK("外观", "这些改的是卡片长什么样（预览就是当前设置画出来的）");

        final String label;
        final String hint;

        Section(String label, String hint) {
            this.label = label;
            this.hint = hint;
        }
    }

    /**
     * 预览样例：<b>四张故意长得不一样的卡</b>。
     * <p>【为什么不是一个固定的"经验卡"】排版问题只在特定内容下才露出来 —— 名字长到要截断、
     * 稀有度换颜色、微光、只有数字。给一个样例等于只验一种，而"预览看着好好的、
     * 真卡糊成一团"正是这么发生的。
     */
    private enum Sample {
        COMMON("普通", Items.STONE, "+64", false, null, "灰档：最常见的那种"),
        RARE("稀有", Items.DIAMOND_SWORD, "+1", false, null, "青档：稀有度换强调色"),
        XP("经验", Items.NETHER_STAR, "+137", true, null, "微光：经验卡会亮一层"),
        LONG_NAME("长名", Items.DIAMOND_PICKAXE, "+1", false,
                "钻石镐（效率 V · 时运 III）", "截断：名字太长就补省略号");

        final String label;
        final String count;
        /** 值得给一层稀有度微光的卡（经验卡）。 */
        final boolean glow;
        final String hint;
        private final ItemStack icon;

        Sample(String label, Item item, String count, boolean glow, String customName, String hint) {
            this.label = label;
            this.count = count;
            this.glow = glow;
            this.hint = hint;
            this.icon = new ItemStack(item);
            if (customName != null) {
                // 玩家自己改过名的物品就长这样：名字长、还带符号
                this.icon.setHoverName(Component.literal(customName));
            }
        }

        /** 卡上那个名字：开了「显示物品ID」就跟真卡一样显示 ID。 */
        String name(boolean showItemId) {
            return showItemId ? BuiltInRegistries.ITEM.getKey(icon.getItem()).toString()
                    : icon.getHoverName().getString();
        }

        /** 强调色走真卡的同一张色表 —— 预览里的颜色必须就是游戏里那个颜色。 */
        int accent() {
            return this == XP ? RarityAccent.XP : RarityAccent.of(icon);
        }
    }

    private final Screen parent;
    private Section section = Section.GENERAL;
    private Sample sample = Sample.COMMON;
    /** 一行 = 一个标签 + 一个自绘控件 + 一句悬停提示 + 这一行的悬停进度。 */
    private final List<Row> rows = new ArrayList<>();
    private final List<NvgWidget> tabButtons = new ArrayList<>();
    /** 预览底下那排「切样例」按钮（预览收起时它们是零矩形，点不到）。 */
    private final List<NvgWidget> sampleButtons = new ArrayList<>();
    private NvgPalette palette = NvgPalette.dark();
    /** 控件里点出来的"切换分类/重建"请求：不在事件遍历中途重建列表。 */
    private boolean pendingRebuild;

    /** 这一帧的时刻（毫秒）。动画与悬停都按它取值，一帧里只取一次。 */
    private long now;

    /** 给 harness 定住的悬停行（null = 用真实鼠标位置）。生产路径永远是 null。 */
    private String forcedHover;

    /** 换页：内容淡入 + 向上滑一点点。目标恒为 1，换页时被 {@link Tween#snap} 打回 0。 */
    private final Tween pageAnim = Tween.at(1f, 0L);
    /** 标签强调条：值 = 选中那一颗的序号（小数 = 正在滑）。 */
    private final Tween tabAccentAnim = Tween.at(0f, 0L);
    /** 预览换样例：淡入。目标恒为 1，换样例时打回 0。 */
    private final Tween previewAnim = Tween.at(1f, 0L);

    /**
     * 一行：标签 + 控件 + 悬停提示 + 悬停进度。
     * <p>【为什么不是 record】悬停进度是这一行的<b>状态</b>，每行一份；record 装不下。
     */
    private static final class Row {
        private final String label;
        private final NvgWidget widget;
        private final String hint;
        private final Tween hover = Tween.at(0f, 0L);

        Row(String label, NvgWidget widget, String hint) {
            this.label = label;
            this.widget = widget;
            this.hint = hint;
        }

        String label() {
            return label;
        }

        NvgWidget widget() {
            return widget;
        }

        String hint() {
            return hint;
        }
    }

    /**
     * 分段按钮（标签列与样例切换共用）：<b>选中态自己会亮</b>。
     * <p>【为什么不再用 "▸ " 前缀】四个标签都挂前缀时，字宽被吃掉一大截，而且状态是"文字里的
     * 一个符号"这件事本身就不该由文字承担 —— 底色和强调条说这件事更快。
     */
    private static final class Chip extends NvgWidget {

        private final Supplier<String> text;
        private final BooleanSupplier selected;
        private final Runnable action;
        private final boolean leftAligned;
        private String hint;

        Chip(String label, Supplier<String> text, BooleanSupplier selected, Runnable action,
             boolean leftAligned) {
            super(label);
            this.text = text;
            this.selected = selected;
            this.action = action;
            this.leftAligned = leftAligned;
        }

        Chip hint(String hint) {
            this.hint = hint;
            return this;
        }

        @Override
        public String value() {
            return text.get();
        }

        @Override
        protected void onActivate() {
            action.run();
        }

        @Override
        public void draw(NvgUi ui) {
            NvgPalette p = ui.palette;
            boolean on = selected.getAsBoolean();
            ui.well(x, y, w, h, on ? p.wellHover : wellColor(p));
            if (on) {
                // 选中那颗描一圈强调色：底色一档差别在深色主题下太细，看不清"我在哪一页"
                ui.strokeRoundRect(x, y, w, h, p.radius, NvgUi.fade(p.accent, 0.5f));
            }
            float ty = y + (h - ui.font().lineHeight) / 2f;
            int color = on ? p.text : p.textDim;
            if (leftAligned) {
                ui.text(text.get(), x + 4f, ty, color);
            } else {
                ui.textCentered(text.get(), x + w / 2f, ty, color);
            }
        }
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
        // 开屏不播"换页"动画：这一刻屏幕上还没有"上一页"，淡入只会让人以为界面卡了一下。
        // （窗口缩放也会走 init()，那时候同样不该重播。）
        now = System.currentTimeMillis();
        pageAnim.snap(1f);
        tabAccentAnim.snap(section.ordinal());
        previewAnim.snap(1f);
    }

    private void rebuild() {
        rows.clear();
        tabButtons.clear();
        sampleButtons.clear();
        palette = NvgPalette.of(CardStage.INSTANCE.previewStyle());
        PickupCardConfig.Values v = PickupCardConfig.VALUES;

        ConfigLayout lo = layout();
        Section[] sections = Section.values();
        for (int i = 0; i < sections.length; i++) {
            Section s = sections[i];
            Chip chip = new Chip(s.label, () -> s.label, () -> s == section, () -> {
                if (s == section) {
                    return;     // 点当前这页：什么都不做（否则会白播一次换页动画）
                }
                section = s;
                pageAnim.snap(0f);
                pendingRebuild = true;
            }, !lo.tabsOnTop()).hint(s.hint);
            ConfigLayout.Rect cell = lo.tabRect(i, sections.length);
            chip.at(cell.x(), cell.y(), cell.w(), cell.h());
            tabButtons.add(chip);
        }

        Sample[] samples = Sample.values();
        for (int i = 0; i < samples.length; i++) {
            Sample s = samples[i];
            Chip chip = new Chip(s.label, () -> s.label, () -> s == sample, () -> {
                if (s == sample) {
                    return;
                }
                sample = s;
                previewAnim.snap(0f);   // 换样例：淡入，别硬切
            }, false).hint(s.hint);
            // 【为什么位置是每次重建算的】预览会不会出现、切换行画不画，都取决于画布大小；
            // 算一次存起来的话，窗口一缩它们就全错位了。
            ConfigLayout.Rect cell = lo.switchRect(i, samples.length);
            chip.at(cell.x(), cell.y(), cell.w(), cell.h());
            sampleButtons.add(chip);
        }

        itemsScroll = new NvgScroll(lo.items().x(), lo.items().y(), lo.items().w(), lo.items().h());

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
        cell("合并粒度", cycle(v.mergeMode, MergeMode.values(), PickupCardConfigScreen::mergeName),
                "什么算「同一样东西」：同名同附魔才并 / 同名就并 / 改过名的不并 / 从不合并");
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
        cell("排队上限", number(v.queueSize, settings.queueSize(), 0, 32, 1, " 张"),
                "屏上满了就先排队（先来先上屏）；0 = 不排队，屏满之后的拾取直接丢掉");
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

    /** 逐行摆：**一行一项**（标签左、控件右），行高按画布自适应（guiScale 5 时画布只有 144 高）。 */
    private void layoutRows() {
        if (itemsScroll == null) {
            return;
        }
        int step = rowStep(Math.max(1, rows.size()));
        float offset = itemsScroll.offset();
        // 换页时整列往上滑一点点：滑动给的是"内容换了"的方向感（淡入只说明"变了"）
        int slide = Math.round((1f - pageAnim.at(now)) * PAGE_SLIDE);
        for (int i = 0; i < rows.size(); i++) {
            // 扣掉滚动偏移：控件与它画出来的位置必须是同一个坐标系，否则点了会"选错行"
            rows.get(i).widget().at(controlX(), rowsTop() + i * step - Math.round(offset) + slide,
                    controlW(), rowH());
        }
    }

    // ------------------------------------------------------------------
    // 画
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        now = System.currentTimeMillis();
        if (pendingRebuild) {
            pendingRebuild = false;
            rebuild();
        }
        driveAnimations(mouseX, mouseY);
        layoutRows();       // 每帧刷一遍：滚一下、换一页、改窗口尺寸，位置都要跟上
        for (NvgWidget w : widgets()) {
            w.mouseMoved(mouseX, mouseY);
        }

        gui.fill(0, 0, this.width, this.height, palette.backdrop);
        try (NvgUi ui = NvgUi.begin(gui, palette, mouseX, mouseY, now)) {
            if (ui != null) {
                drawChrome(ui);
                drawTabAccent(ui);
                // 配置项那一列：裁剪到视口里 —— 滚出去的行不许糊在标签列或预览列上。
                // 形状（NanoVG）与文字（原版批次）两套裁剪由 pushClip 一次设好。
                if (itemsScroll != null) {
                    itemsScroll.pushClip(ui);
                }
                // 换页淡入：整层不透明度，形状与文字一起淡（分开淡会出现"有一行没淡"）
                ui.alpha(0.15f + 0.85f * pageAnim.at(now));
                for (Row row : rows) {
                    drawRowHighlight(ui, row);
                }
                drawLabels(ui);
                for (Row row : rows) {
                    row.widget().draw(ui);
                }
                ui.alpha(1f);
                if (itemsScroll != null) {
                    drawScrollBar(ui);
                    ui.popClip();
                }
                for (NvgWidget w : sampleButtons) {
                    w.draw(ui);
                }
            }
        }
        // 预览放在最后：它自己开一帧（里面还有原版图标与文字），压在自绘界面之上。
        // 两者在屏幕上不重叠，顺序只影响"哪一批先上 GPU"。
        drawPreview(gui);
        drawHint(gui, mouseX, mouseY);
    }

    /**
     * 把这一帧的动画目标推进一步。
     * <p>【为什么统一在渲染前推】一个动画一件事：换页与换样例的目标恒为 1（被打回 0 就开始播），
     * 悬停与强调条的目标是"此刻的状态"。分散在各自的绘制里推进的话，"这一帧到底更新过谁"
     * 就没人说得清了 —— 那种 bug 表现为"偶尔动画不动"。
     */
    private void driveAnimations(int mouseX, int mouseY) {
        pageAnim.retarget(1f, now, PAGE_MS);
        previewAnim.retarget(1f, now, PREVIEW_MS);
        tabAccentAnim.retarget(section.ordinal(), now, TAB_MS);
        for (Row row : rows) {
            boolean on = forcedHover != null ? row.label().equals(forcedHover)
                    : row.widget().hit(mouseX, mouseY);
            row.hover.retarget(on ? 1f : 0f, now, on ? HOVER_IN_MS : HOVER_OUT_MS);
        }
    }

    /** 底 + 标题 + 标签列 + 预览面板 —— 全是 NanoVG 画的圆角块。 */
    private void drawChrome(NvgUi ui) {
        NvgPalette p = ui.palette;
        ConfigLayout lo = layout();
        PickupCardSettings eff = PickupCardConfig.snapshot();
        ui.text(this.title.getString(), contentLeft(), 6f, 0xFFFFFFFF);
        ui.text("改动即时生效 · 记进 config/pickupcard-client.toml", contentLeft(), 17f, p.textDim);
        // 右上角那行状态：总开关是"整体生效没生效"的唯一真源，藏进页里就得翻页才知道
        ui.textRight((eff.enabled() ? "总开关 开" : "总开关 关") + " · " + scaleText(),
                contentRight(), 6f, eff.enabled() ? p.accent : p.textDim);
        // 标题和内容之间那条线：没有它，标题行和第一行标签会连成一片
        ui.fillRoundRect(ConfigLayout.MARGIN, ConfigLayout.TOP - 5f,
                Math.max(0f, this.width - ConfigLayout.MARGIN * 2f), 1f, 0.5f,
                NvgUi.fade(p.outline, 0.6f));
        // 标签那一列：列排时是一竖条底，顶排时是一横条底
        ui.fillGradient(lo.tabs().x() - 2f, lo.tabs().y() - 2f, lo.tabs().w() + 4f,
                lo.tabs().h() + 4f, p.panel, 0x80202836);
        // 预览列：面板底 + 标题（收掉时这两样都不画）
        if (lo.previewVisible()) {
            ui.text("预览", lo.preview().x(), lo.preview().y(), p.textDim);
            ui.textRight(sample.hint, lo.preview().right(), lo.preview().y(), p.textDim);
            ui.fillRoundRect(lo.preview().x() - 2f, lo.preview().y() + 10f,
                    lo.preview().w() + 4f, Math.max(0f, lo.preview().h() - 12f), p.radius,
                    0x40202A38);
        } else {
            // 【为什么右对齐】底部那行左边是悬停说明（drawHint），左对齐会跟它叠在一起
            // —— 第一版就是这么写的，截图里两段字糊成一团。
            ui.textRight("预览已收起（窗口太窄）", lo.items().right(), this.height - 12f, p.textDim);
        }
    }

    /**
     * 选中那颗标签的强调条：从上一颗<b>滑</b>到这一颗。
     * <p>【为什么要有它】底色那一档差别在深色主题下太细，"我现在在哪一页"只剩文字颜色一条线索；
     * 加一条强调色带之后，换页这件事在眼睛的余光里也成立。
     * <p>【为什么位置是插值算的】每一颗标签就是一个矩形，序号连续变化时矩形也在两两之间插值 ——
     * 于是列排（往下滑）与顶排（往右滑）共用同一段代码。
     */
    private void drawTabAccent(NvgUi ui) {
        ConfigLayout lo = layout();
        int count = Section.values().length;
        float idx = tabAccentAnim.at(now);
        float low = Math.max(0f, Math.min(count - 1f, (float) Math.floor(idx)));
        float high = Math.max(0f, Math.min(count - 1f, (float) Math.ceil(idx)));
        ConfigLayout.Rect a = lo.tabRect((int) low, count);
        ConfigLayout.Rect b = lo.tabRect((int) high, count);
        float t = idx - low;
        float x = a.x() + (b.x() - a.x()) * t;
        float y = a.y() + (b.y() - a.y()) * t;
        if (lo.tabsOnTop()) {
            ui.fillRoundRect(x + 2f, y + a.h() - 1.5f, Math.max(0f, a.w() - 4f), 2f, 1f,
                    ui.palette.accent);
        } else {
            ui.fillRoundRect(x - 2.5f, y + 2f, 2.5f, Math.max(0f, a.h() - 4f), 1.25f,
                    ui.palette.accent);
        }
    }

    /** 悬停那一行的底：一条横贯整行的浅色带，是"这一行可以被拨"的提示。 */
    private void drawRowHighlight(NvgUi ui, Row row) {
        float t = row.hover.at(now);
        if (t <= 0.01f) {
            return;
        }
        NvgWidget w = row.widget();
        float x = labelX() - 4f;
        float right = layout().items().right() - 4f;
        ui.fillRoundRect(x, w.y() + 1f, Math.max(0f, right - x), w.height() - 2f,
                ui.palette.radius, NvgUi.fade(0xFFFFFFFF, 0.05f * t));
    }

    private void drawLabels(NvgUi ui) {
        for (Row row : rows) {
            NvgWidget w = row.widget();
            String text = ui.font().plainSubstrByWidth(row.label(), labelW());
            // 悬停时标签由暗到亮：它、那条高亮带、底部那句说明指的是同一行
            ui.text(text, labelX(), w.y() + (w.height() - 8) / 2f + 1f,
                    NvgUi.mix(ui.palette.textDim, ui.palette.text, row.hover.at(now)));
        }
    }

    /** 底部那行说明：悬停谁就说谁，这是这个界面唯一能自我解释的地方。 */
    private void drawHint(GuiGraphics gui, int mouseX, int mouseY) {
        String hint = null;
        // 【为什么要夹在配置列里】滚出视口的行，它的矩形还在（只是被裁掉了）——
        // 不做这个判断的话，鼠标划过页眉时会说"这张卡的说明"，而那一行根本看不见。
        ConfigLayout.Rect items = layout().items();
        if (mouseY >= items.y() && mouseY < items.bottom()) {
            for (Row row : rows) {
                if (row.widget().hit(mouseX, mouseY)) {
                    hint = row.hint();
                    break;
                }
            }
        }
        if (hint == null) {
            for (NvgWidget chip : chips()) {
                if (chip instanceof Chip c && c.hint != null && chip.hit(mouseX, mouseY)) {
                    hint = c.hint;
                    break;
                }
            }
        }
        if (hint == null) {
            hint = section.hint;        // 哪儿都没停：说当前这一页是干嘛的
        }
        String shown = this.font.plainSubstrByWidth(hint, Math.max(24, this.width - PAD * 2 - 4));
        gui.drawString(this.font, shown, PAD, this.height - 12, palette.textDim);
    }

    /** 需要滚动时才画的那条滚动条（细，不抢视线；位置一眼看出"还能往下"）。 */
    private void drawScrollBar(NvgUi ui) {
        float content = itemsContentHeight();
        if (!itemsScroll.scrollable(content)) {
            return;
        }
        ConfigLayout lo = layout();
        float trackH = lo.items().h() - 8f;
        float barH = Math.max(12f, trackH * (lo.items().h() / content));
        float t = ScrollMath.maxOffset(content, lo.items().h()) <= 0f ? 0f
                : itemsScroll.offset() / ScrollMath.maxOffset(content, lo.items().h());
        float x = lo.items().right() - 3f;
        float y = lo.items().y() + 4f + t * (trackH - barH);
        ui.fillRoundRect(x, y, 3f, barH, 1.5f, ui.palette.textDim);
    }

    // ------------------------------------------------------------------
    // 预览
    // ------------------------------------------------------------------

    /** 单张样例卡（布局页画一摞）。预览收起时什么都不画 —— 挤成一条比没有更难看。 */
    private void drawPreview(GuiGraphics gui) {
        ConfigLayout lo = layout();
        if (!lo.previewVisible()) {
            return;
        }
        ConfigLayout.Rect area = lo.previewCard();
        if (area.w() <= 2f || area.h() <= 2f) {
            return;
        }
        StyleModel style = CardStage.INSTANCE.previewStyle();
        if (section == Section.LAYOUT) {
            renderStackPreview(gui, area, style);
        } else {
            renderSampleCard(gui, area, style);
        }
    }

    /** 一张样例卡：宽度按内容算（跟真卡同一个公式），放不下才截名字。 */
    private void renderSampleCard(GuiGraphics gui, ConfigLayout.Rect area, StyleModel style) {
        PickupCardSettings settings = PickupCardConfig.snapshot();
        boolean showName = settings.showItemName();
        String name = sample.name(settings.showItemId());
        float scale = previewScale();
        float cardW = Math.min(naturalWidth(style, name, sample.count, showName) * scale,
                area.w() - 2f);
        float cardH = style.boxHeight() * scale;
        // 【为什么竖直居中】预览面板的高度随画布变，卡高只随缩放变 —— 贴顶放的话，
        // 高面板里它会孤零零挂在上面，看着像没画完。
        NvgCardPainter.paintPreview(gui, style, area.x() + 1f,
                area.y() + Math.max(0f, (area.h() - cardH) / 2f), cardW, sample.icon, name,
                sample.count, sample.accent(), sample.glow, showName, scale, previewAnim.at(now));
    }

    /**
     * 一摞卡：<b>真的排布函数 + 真的间距</b>画三张不同宽度的卡。
     * <p>【为什么必须调真函数】"贴边 / 间距 / 同屏"这些键的效果只在多张卡之间看得出来：
     * 左对齐时三条竖条成一条竖线、右对齐时右缘齐而竖条参差。自己画一遍就是第二份排版实现。
     * <p>【为什么宽度也按内容算】卡宽本来就是内容定的。手写三个宽度的话，
     * "名字长的那张会不会挤爆"在预览里永远看不见 —— 而那正是最会出问题的一张。
     */
    private void renderStackPreview(GuiGraphics gui, ConfigLayout.Rect area, StyleModel style) {
        PickupCardSettings settings = PickupCardConfig.snapshot();
        boolean showName = settings.showItemName();
        LayoutSettings layout = PickupCardConfig.layoutSnapshot();
        float scale = previewScale();
        // 选中的样例当最新那张（贴底），另外两张按枚举顺序补齐 —— 宽度差别才看得出来
        List<Sample> trio = new ArrayList<>();
        trio.add(sample);
        for (Sample s : Sample.values()) {
            if (trio.size() < 3 && s != sample) {
                trio.add(s);
            }
        }
        // 最新的排第一（{@link StackLayout#stack} 的约定：第 0 张贴着底线）
        List<StackLayout.Size> sizes = new ArrayList<>();
        for (Sample s : trio) {
            float w = Math.min(naturalWidth(style, s.name(settings.showItemId()), s.count, showName) * scale,
                    area.w() - 6f);
            sizes.add(new StackLayout.Size(Math.max(24f, w), style.boxHeight() * scale));
        }
        // 【为什么底部留白是个小数字】这一块是"模拟屏"，不是真屏幕 —— 原版 HUD 不在这个
        // 面板里，套 HudSafeZone 会把卡顶到面板外面去。
        for (StackLayout.Slot slot : StackLayout.stack(sizes, area.w(), area.h(), layout, 6, 4,
                layout.separation() * scale)) {
            Sample s = trio.get(slot.index());
            NvgCardPainter.paintPreview(gui, style, area.x() + slot.x(), area.y() + slot.y(),
                    slot.width(), s.icon, s.name(settings.showItemId()), s.count, s.accent(),
                    s.glow, showName, scale, previewAnim.at(now));
        }
    }

    /**
     * 样例卡在 100% 下的自然宽度：跟真卡同一个公式（竖条 + 间隙 + 图标格 + 间隙 + 信息框）。
     * <p>自己写一个"看起来差不多"的宽度，就等于预览和真卡各有一套尺寸 —— 那正是这个界面
     * 最不该有的东西。
     */
    private float naturalWidth(StyleModel style, String name, String count, boolean showName) {
        float info = style.paddingH() * 2f + this.font.width(count);
        if (showName) {
            info += style.gap() + this.font.width(name);
        }
        return style.barWidth() + style.gap() + style.boxHeight() + style.gap() + info;
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

    /** 标题右上角那行：总开关之外，玩家最常想知道的是"卡现在缩到了多少"。 */
    private static String scaleText() {
        int pct = PickupCardConfig.layoutSnapshot().scalePercent();
        return "卡片缩放 " + (pct > LayoutSettings.AUTO_SCALE ? pct + "%" : "自动");
    }


    // ------------------------------------------------------------------
    // 事件：全部转给自绘控件
    // ------------------------------------------------------------------

    /** 所有自绘控件：事件遍历、悬停刷新、重建时都用这一份。 */
    private List<NvgWidget> widgets() {
        List<NvgWidget> all = chips();
        for (Row row : rows) {
            all.add(row.widget());
        }
        return all;
    }

    /** 分段按钮（标签 + 样例）：底部那句说明与点击都要能找到它们。 */
    private List<NvgWidget> chips() {
        List<NvgWidget> all = new ArrayList<>(tabButtons);
        all.addAll(sampleButtons);
        return all;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ConfigLayout.Rect items = layout().items();
        for (NvgWidget w : widgets()) {
            // 滚出视口的行不该还能被点到（它们的位置在视口外，只有 x 可能重合）
            boolean rowWidget = rows.stream().anyMatch(r -> r.widget() == w);
            if (rowWidget && (mouseY < items.y() || mouseY >= items.bottom())) {
                continue;
            }
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
        if (itemsScroll == null) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        int step = rowStep(Math.max(1, rows.size()));
        // 一格滚三行（按行不按像素：跨缩放档手感一致，见 ScrollMath）
        if (itemsScroll.wheel(delta, itemsContentHeight(), step)) {
            layoutRows();
            return true;
        }
        return true;
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

    /** 按选项名找控件（含标签与样例按钮）。找不到返回 null —— 调用方要报，不能静默点空。 */
    public NvgWidget widgetFor(String label) {
        for (NvgWidget b : chips()) {
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
        // 【为什么零尺寸算点不到】预览收起时切样例按钮是零矩形（没画出来）。照着它算中心
        // 会点到面板角落上、什么都不会发生 —— 而"点了没反应"和"功能坏了"长得一模一样。
        if (w.width() <= 0f || w.height() <= 0f) {
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
        return Math.round(layout().items().x()) + 2;
    }

    private int contentRight() {
        return Math.round(layout().items().right()) - 2;
    }

    /** 控件那一格多宽：配置列宽的一部分，右对齐（一行一项，不再是一行两项）。 */
    private int controlW() {
        int room = Math.round(layout().items().w()) - 12;
        return Math.max(48, Math.min(130, room * 45 / 100));
    }

    /** 标签左缘：配置列左边留 6px。 */
    private int labelX() {
        return Math.round(layout().items().x()) + 6;
    }

    /** 标签能用多宽：从标签左缘到控件左缘。 */
    private int labelW() {
        return Math.max(24, controlX() - labelX() - 6);
    }

    /** 控件左缘：右对齐到配置列右缘留 6px。 */
    private int controlX() {
        return Math.round(layout().items().right()) - 6 - controlW();
    }

    private int rowsTop() {
        // 预览已经搬到右边那一列了，配置项从这一列的顶上开始
        return Math.round(layout().items().y()) + 2;
    }
    private int rowH() {
        return 18;
    }

    private int rowStep(int maxRows) {
        int room = Math.round(layout().items().h()) - 6;
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

    /** 合并粒度四档的界面说法（要能在同一行里读完，所以比 TOML 注释短）。 */
    private static String mergeName(MergeMode mode) {
        return switch (mode) {
            case SAME_ITEM -> "同名就并";
            case SAME_ITEM_KEEP_NAMED -> "同名就并·改名不并";
            case NEVER -> "从不合并";
            default -> "同名同附魔才并";
        };
    }

    /** 每次改动都让渲染立刻重读，不等那一秒的重读间隔。 */
    private static void changed() {
        CardStage.INSTANCE.refreshStyle();
    }
}
