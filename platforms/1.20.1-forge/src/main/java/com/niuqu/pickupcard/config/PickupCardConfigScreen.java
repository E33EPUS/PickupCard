package com.niuqu.pickupcard.config;

import com.niuqu.pickupcard.filter.RuleListEdit;
import com.niuqu.pickupcard.layout.CardMove;
import com.niuqu.pickupcard.layout.HudSafeZone;
import com.niuqu.pickupcard.layout.LayoutSettings;
import com.niuqu.pickupcard.PickupCard;
import com.niuqu.pickupcard.notice.MergeMode;
import com.niuqu.pickupcard.notice.PickupCardSettings;
import com.niuqu.pickupcard.notice.Notice;
import com.niuqu.pickupcard.pickup.Inbox;
import com.niuqu.pickupcard.pickup.CardContent;
import com.niuqu.pickupcard.render.CardCanvas;
import com.niuqu.pickupcard.render.CardMetrics;
import com.niuqu.pickupcard.render.CardSlot;
import com.niuqu.pickupcard.render.CardView;
import com.niuqu.pickupcard.style.CardTimeline;
import com.niuqu.pickupcard.render.CardStage;
import com.niuqu.pickupcard.render.nvg.NvgCardPainter;
import com.niuqu.pickupcard.render.nvg.ui.NvgButton;
import com.niuqu.pickupcard.render.nvg.ui.NvgColorChip;
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
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.ForgeConfigSpec;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 游戏内配置界面：<b>左侧一列分类，右侧实时预览 + 选项行</b>（同一行里左边标签、右边控件）。
 *
 * <p>【两件事决定了它的形状】
 * <ol>
 *   <li><b>形状照旧版</b>（= 从发行 jar 恢复出来的 v0.1.0 / pickupnotice 那一系）：4 段
 *       「通用 / 动画 / 位置与堆叠 / 外观」+ 过滤、每行"左标签右控件"。</li>
 *   <li><b>控件自己画</b>（{@code render/nvg/ui}）：按钮/开关/滑条/色块全是 NanoVG 画的，
 *       不再用原版控件。</li>
 * </ol>
 *
 * <p>【2026-09-19 的五条定案（第四批反馈审计 + grill）】
 * <ol>
 *   <li><b>固定行距 + 小节头</b>：行距从前按"页内行数"算（通用 22px、外观 20px），换页时
 *       每行位移不一致 —— "排版杂乱"的数学来源。现在全界面恒 20px；页内加暗色小节头
 *       （「显示什么」「形状」「颜色」…），扫一眼就知道哪几行是一伙的。</li>
 *   <li><b>颜色改色块</b>（{@link NvgColorChip}）：手打 hex 属于程序员的方言，填错还静默；
 *       现在点色块在色板里循环、「跟随主题」是第一档；精确色值仍可写 TOML（行说明里有说）。</li>
 *   <li><b>每页一颗「恢复本页默认」</b>：改动立即生效的代价是"拖错了没有后悔药"——这颗钮
 *       就是后悔药。位置页不重置锚点（那是玩家亲手拖的，归编辑场管）。</li>
 *   <li><b>预览按页分工</b>（回归 2026-09-18 的定案）：非动画页画<b>一张静止完整卡</b>
 *       （点预览/换样例重播一次入场）；动画页保留三张真卡的自动舞台。「在屏上哪儿」这件事
 *       归拖拽编辑场 1:1 管 —— 预览面板是块小画布，把它当屏幕地图就是在撒谎。</li>
 *   <li><b>位置底锚</b>：新卡永远贴 HUD 带上方那条固定底线、旧的向上顶（几何账见
 *       {@link LayoutSettings}）。锚线自动档按对齐档各自解析。</li>
 * </ol>
 *
 * <p>【界面自己不存值】控件的读写直接打到 Forge 配置上；否则"界面显示 5、实际画 9"迟早出现。
 */
public final class PickupCardConfigScreen extends Screen {

    // ---- 界面尺度 ----
    private static final int PAD = 6;
    /**
     * 全界面统一的行距/行高（2026-09-19 定案）。行距从前按页内行数算，换页时行会各自漂 ——
     * 固定之后放不下就滚，节奏不再随内容变。
     */
    private static final int ROW_STEP = 20;
    private static final int ROW_H = 18;
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
    /** 「加一条」被拒时那句话在底部停留多久（够读完，又不会把悬停说明永久顶掉）。 */
    private static final long FILTER_NOTE_MS = 6_000L;
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
                Math.max(1, (int) (lo.items().h() / ROW_STEP)),
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
                "页=%s 样例=%s 预览卡区=(x%.0f y%.0f w%.0f h%.0f) 换页=%.2f 强调条=%.2f 预览=%s 最大行悬停=%.2f 画了=%s",
                section.label, sample.label, card.x(), card.y(), card.w(), card.h(),
                pageAnim.at(now), tabAccentAnim.at(now),
                stagePage() ? "舞台" + stage.size() + "张" : "静止卡",
                hover, paintedDump());
    }

    /**
     * 这一帧画过几个控件 —— <b>"控件在、也能点、就是没画"的自检</b>。
     * <p>【为什么要有这一行】标签列在重构里整列没画过一次：点击照旧有效、单测照旧全绿，
     * 只有截图能看出来。控件自己记了"这帧画过没有"（见 {@code NvgWidget#draw}），
     * 界面每帧数一遍 —— 数出来不是满的，就是漏了绘制调用。
     */
    private String paintedDump() {
        return paintedCount() + "/" + widgetCount();
    }

    /** 这一帧画过几个控件。 */
    public int paintedCount() {
        int painted = 0;
        for (NvgWidget w : widgets()) {
            if (w.paintedIn(now)) {
                painted++;
            }
        }
        return painted;
    }

    /** 这一帧一共有几个控件。 */
    public int widgetCount() {
        return widgets().size();
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
    /** 换页清零、同页重建（删一条规则）保留 —— 列表忽然跳回顶上是纯惊吓。 */
    private float savedScrollOffset;

    /** 这一帧配置项内容有多高（滚动的依据）。 */
    private float itemsContentHeight() {
        return rows.size() * (float) ROW_STEP;
    }

    /**
     * 分类。
     * <p>【为什么每页带一句说明】底部那行字是这个界面唯一的自我解释：悬停在标签上时应该说
     * <em>那一页</em>是干嘛的，而不是当前这页的 —— 否则"点了没反应"和"说明没变"长得一样。
     * <p>【为什么五句不再用同一个句式】它们从前全是「这些改的是…」开头 —— 五条读起来像一条，
     * 扫过去等于没读。现在每条说清"这一页能解决什么问题"，各写各的。
     */
    private enum Section {
        GENERAL("通用", "弹不弹卡、要不要显示名字、什么算同一样东西"),
        ANIM("动画", "卡片出现和消失的快慢，以及数量变化怎么动"),
        LAYOUT("位置与堆叠", "卡片停在哪、同时最多几张（位置整屏拖拽调）"),
        LOOK("外观", "卡片的长相：形状一节、颜色一节"),
        /**
         * 【为什么单独一页】三张名单都不是"一个值"，而是可增删的列表 —— 一行一项那个版式
         * 正好能装（一条规则一行、末尾一行输入框），但行数会随玩家自己加多少条涨，
         * 跟"外观"那种固定八行的页面不是一回事。混在一起会让固定项被列表挤走。
         */
        FILTER("过滤", "哪些东西不弹卡、一定要弹、或者弹了不出声");

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
     * <p>【四格为什么正好是四个颜色】用户 2026-09-18 问「为什么预览里的四个等级，颜色都是
     * 一样的灰色？原版有稀有度这个机制吗？怎么读取的？」—— 机制有（{@code net.minecraft.
     * world.item.Rarity}，{@code ItemStack#getRarity()} 读），但<b>样例物品选错了</b>：
     * 「稀有」那一格用的是<b>钻石剑</b>，而钻石剑在原版是 {@code COMMON}。现在每一格都写死
     * 标称档位，{@link #verify()} 在启动时对一次，标称与实际不符就报 ERROR —— 这种错不会崩、
     * 不会抛异常，只会安安静静画成灰的，只有一条日志能拦住它。
     * <p>【为什么不是 private】锚点编辑场（{@link AnchorEditScreen}）用同一组样例画拖拽堆 ——
     * 样例是"预览与编辑场共用的一份内容"，不是哪一界的私产。
     */
    enum Sample {
        COMMON("普通", Items.STONE, Rarity.COMMON, 64, false, null,
                "最常见的那一档 —— 灰。原版绝大多数物品都在这档（钻石剑、钻石镐也是）"),
        RARE("稀有", Items.GOLDEN_APPLE, Rarity.RARE, 1, false, null,
                "稀有档 —— 青。样例必须是真·稀有的物品：拿钻石剑当稀有样例只会得到一片灰"),
        XP("经验", Items.NETHER_STAR, null, 137, true, null,
                "经验卡：绿是它专用的一档，刻意不参与稀有度分级"),
        LONG_NAME("长名", Items.ENCHANTED_GOLDEN_APPLE, Rarity.EPIC, 1, false,
                "附魔金苹果（珍藏 · 来自末地城）", "史诗档 —— 紫；顺带验名字太长会被截断");

        final String label;
        /** 这一格的数量。<b>不是显示字符串</b> —— 显示成 "+64" 还是 "×64" 由玩家选的
         *  {@code CountFormat} 决定，所以「数量写法」那一格现在在预览里立刻看得见。 */
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
         * 启动时对一次：标称档位必须就是<b>原版读出来的</b>那一档。
         * <p>只在第一次用到样例时跑（{@code verifyDone}），不进每帧路径。
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

        /** 样例物品（{@code XP} 那格的真卡内容不是物品，见 {@code previewNotice}）。 */
        ItemStack icon() {
            return icon;
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
    private NvgPalette palette = NvgPalette.dark(StyleModel.Accents.defaults());
    /** 控件里点出来的"切换分类/重建"请求：不在事件遍历中途重建列表。 */
    private boolean pendingRebuild;
    /** 同页重建（规则增删）保留滚动偏移；换页清零。 */
    private boolean preserveScroll;
    /**
     * 加规则被拒时说的一句话，短时间内在底部那行顶掉悬停说明。
     * <p>【为什么要有它】"打字 → 回车 → 什么都没发生"是这类输入框最常见的失败方式，
     * 而底部那行是这个界面唯一能自我解释的地方（见 {@code drawHint}）。
     */
    private String filterNote = "";
    private long filterNoteAt;

    /** 这一帧的时刻（毫秒）。动画与悬停都按它取值，一帧里只取一次。 */
    private long now;

    /** 给 harness 定住的悬停行（null = 用真实鼠标位置）。生产路径永远是 null。 */
    private String forcedHover;

    /** 换页：内容淡入 + 向上滑一点点。目标恒为 1，换页时被 {@link Tween#snap} 打回 0。 */
    private final Tween pageAnim = Tween.at(1f, 0L);
    /** 标签强调条：值 = 选中那一颗的序号（小数 = 正在滑）。 */
    private final Tween tabAccentAnim = Tween.at(0f, 0L);

    /**
     * 一行：标签 + 控件 + 悬停提示 + 悬停进度。
     * <p>【为什么不是 record】悬停进度是这一行的<b>状态</b>，每行一份；record 装不下。
     * <p>【小节头也是一行】{@code widget == null} 的行是小节头：占同样的行距、画暗色小字、
     * 没有控件也不接悬停 —— 分组信息用"节奏"表达，不引入第二种行高。
     */
    private static final class Row {
        private final String label;
        private final NvgWidget widget;
        private final String hint;
        private final Tween hover = Tween.at(0f, 0L);
        /** 这一帧的行顶 y（小节头画字用；选项行以控件位置为准）。 */
        private float yAt;

        Row(String label, NvgWidget widget, String hint) {
            this.label = label;
            this.widget = widget;
            this.hint = hint;
        }

        boolean isHeader() {
            return widget == null;
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
        protected void paint(NvgUi ui) {
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
        // 预览一打开就核对样例的真实稀有度（只跑一次）—— 标称与实际不符会在日志里报 ERROR
        Sample.verify();
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
    }

    private void rebuild() {
        boolean keep = preserveScroll;
        preserveScroll = false;
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
                pendingRebuild = true;      // 换页：滚动清零（preserveScroll 保持 false）
            }, !lo.tabsOnTop()).hint(s.hint);
            ConfigLayout.Rect cell = lo.tabRect(i, sections.length);
            chip.at(cell.x(), cell.y(), cell.w(), cell.h());
            tabButtons.add(chip);
        }

        Sample[] samples = Sample.values();
        int switchCount = samples.length + 1;      // 样例 + 「来一张」
        for (int i = 0; i < samples.length; i++) {
            Sample s = samples[i];
            Chip chip = new Chip(s.label, () -> s.label, () -> s == sample, () -> {
                if (s == sample) {
                    return;
                }
                sample = s;
                // 选中的样例立刻上台/上纸：改了"下一张是谁"当场看得见
                playOnce();
            }, false).hint(s.hint);
            // 【为什么位置是每次重建算的】预览会不会出现、切换行画不画，都取决于画布大小；
            // 算一次存起来的话，窗口一缩它们就全错位了。
            ConfigLayout.Rect cell = lo.switchRect(i, switchCount);
            chip.at(cell.x(), cell.y(), cell.w(), cell.h());
            sampleButtons.add(chip);
        }
        // 「来一张」：手动入口 —— 想仔细看入场/退场，点它当场放一张，不用等节拍。
        Chip spawn = new Chip("来一张", () -> "来一张", () -> false, this::playOnce, false)
                .hint("立刻放一张当前选中的样例，完整走一遍入场 → 停留 → 消失（动画页）；"
                        + "其他页重播一次入场");
        ConfigLayout.Rect spawnCell = lo.switchRect(switchCount - 1, switchCount);
        spawn.at(spawnCell.x(), spawnCell.y(), spawnCell.w(), spawnCell.h());
        sampleButtons.add(spawn);

        itemsScroll = new NvgScroll(lo.items().x(), lo.items().y(), lo.items().w(), lo.items().h());
        itemsScroll.scrollTo(keep ? savedScrollOffset : 0f);

        // 预览按页分工：离开动画页收舞台；回来时重新开场（节拍从头起）
        if (stagePage()) {
            nextSpawnAt = -1L;
        } else {
            stage.clear();
            staticCard = settledCard();
        }

        switch (section) {
            case GENERAL -> buildGeneral(v);
            case ANIM -> buildAnim(v);
            case LAYOUT -> buildLayout(v);
            case LOOK -> buildLook(v);
            case FILTER -> buildFilter(v);
        }
        layoutRows();
    }

    /** 预览的分工：动画页跑三张真卡的自动舞台；其他页一张静止完整卡。 */
    private boolean stagePage() {
        return section == Section.ANIM;
    }

    /** 一张"早已出生"的卡：入场已播完，停在最终态 —— 静止页的主角。 */
    private CardView settledCard() {
        return new CardView(previewNotice(sample, sample.amount, System.currentTimeMillis() - 10_000L));
    }

    /**
     * 「恢复本页默认」：把<b>这一页</b>看得见的项写回出厂值。
     * <p>【位置页为什么不重置锚点】锚点是玩家在编辑场里亲手拖的刻意选择，混进"一键恢复"
     * 里一个手滑就没了 —— 它的「回到默认」在编辑场自己那颗钮上。
     */
    private void restorePageDefaults() {
        PickupCardConfig.Values v = PickupCardConfig.VALUES;
        switch (section) {
            case GENERAL -> {
                v.enabled.set(true);
                v.showItemName.set(true);
                v.showItemId.set(false);
                v.nameMaxWidth.set(0);
                v.countFormat.set(CountFormat.PLUS);
                v.separation.set((double) LayoutSettings.DEFAULT_SEPARATION);
                v.mergeMode.set(MergeMode.SAME_NBT);
            }
            case ANIM -> {
                v.stEnterMs.set(-1L);
                v.stEnterEnabled.set(-1);
                v.holdMs.set(4_000L);
                v.exitMode.set(LayoutSettings.Exit.FADE);
                v.exitMs.set(480L);
                v.stBumpEnabled.set(-1);
                v.stBumpMs.set(-1L);
                v.stReviveMs.set(-1L);
            }
            case LAYOUT -> {
                v.align.set(LayoutSettings.Side.LEFT);
                v.appearMode.set(LayoutSettings.Appear.SLIDE);
                v.scalePercent.set(LayoutSettings.AUTO_SCALE);
                v.maxOnScreen.set(5);
                v.queueSize.set(9);
            }
            case LOOK -> {
                v.stBarWidth.set(-1);
                v.stPaddingV.set(-1);
                v.stIconSize.set(-1);
                v.stCornerRadius.set(-1);
                v.stBorderWidth.set(-1);
                v.stFillTop.set("");
                v.stFillBottom.set("");
                v.stBorder.set("");
                v.stNameColor.set("");
            }
            case FILTER -> {
                v.blacklist.set(List.of());
                v.whitelist.set(List.of());
                v.muteList.set(List.of());
            }
        }
        changed();
        pendingRebuild = true;      // 行没变但值全变：重建让控件显示活配置
    }

    /** 「通用」：总开关置顶，下面按「显示什么」「行为与合并」分两节。 */
    private void buildGeneral(PickupCardConfig.Values v) {
        PickupCardSettings eff = PickupCardConfig.snapshot();
        restoreRow("重置总开关、显示与合并七项（不含其他页）");
        cell("总开关", bool(v.enabled, eff.enabled()),
                "关掉之后捡东西不再弹卡；重开时屏上不会涌出积压的旧卡");
        header("显示什么");
        cell("显示物品名", bool(v.showItemName, eff.showItemName()),
                "关掉后卡片只剩竖条、图标和数量，会明显变窄");
        cell("显示物品ID", bool(v.showItemId, eff.showItemId()),
                "名字一栏显示 minecraft:stone 这样的注册 ID，而不是它的中文名");
        cell("名字最大宽度", number(v.nameMaxWidth, eff.nameMaxWidth(), 0, 400, 1, "px"),
                "名字超过这个宽度就截断加省略号；0 = 按屏宽自动");
        cell("数量写法", cycle(v.countFormat, CountFormat.values(), PickupCardConfigScreen::countName),
                "数量的书写方式，点一下换下一种：+64 → ×64 → 64 → +1.2K");
        header("行为与合并");
        cell("卡片间距", decimal(v.separation, PickupCardConfig.layoutSnapshot().separation(), 0, 16),
                "两张卡上下之间的空隙。卡内「框与框」的距离是另一回事，归主题 JSON");
        cell("合并粒度", cycle(v.mergeMode, MergeMode.values(), PickupCardConfigScreen::mergeName),
                "「同一种」的判定宽度，点一下换下一种：同名同附魔才并 → 同名就并 → 同名就并但改名不并 → 从不合并");
    }

    /** 「动画」：入场 / 数字跳动 / 停留 / 消失 —— 全是"看得见"的时长。 */
    private void buildAnim(PickupCardConfig.Values v) {
        StyleModel style = CardStage.INSTANCE.previewStyle();
        PickupCardSettings eff = PickupCardConfig.snapshot();
        restoreRow("重置本页全部九项时长与开关（主题归主题、行为归默认）");
        header("入场");
        cell("入场时长", styleTime(v.stEnterMs, style.enterMs(), 0, 2_000, 40),
                "新卡从竖条后面滑出来到就位一共多久；调它预览会当场重演一遍");
        cell("入场动画", styleSwitch(v.stEnterEnabled, style.enterEnabled()),
                "只关新卡滑出这一段：关掉后新卡直接出现，跳动和淡出不受影响");
        header("停留与消失");
        cell("停留时长", time(v.holdMs, eff.holdMs(), 500, 10_000, 250),
                "一张卡从就位到开始淡出，在屏上待多久；连捡同一件会不断刷新这个计时");
        cell("消失方式", cycle(v.exitMode, LayoutSettings.Exit.values(), PickupCardConfigScreen::exitName),
                "卡片怎么消失，点一下换下一种：淡出（原地变透明）→ 火车退回（平移回竖条后）→ 拉幕收拢（可见范围从右往左收）。三种都叠加透明度下降");
        cell("消失时长", time(v.exitMs, eff.exitMs(), 0, 2_000, 20), "上面那个消失动作用多久；0 = 到点立刻消失");
        header("合并与跳动");
        cell("数字跳动", styleSwitch(v.stBumpEnabled, style.bumpEnabled()),
                "连续捡同一种东西时，整张卡向外鼓一下、数字从旧值滚到新值");
        cell("跳动时长", styleTime(v.stBumpMs, style.bumpMs(), 0, 1_000, 20), "上面那下「鼓」持续多久");
        cell("淡回时长", styleTime(v.stReviveMs, style.reviveMs(), 0, 1_000, 20),
                "卡快淡完时又捡到同一种东西，多久补回不透明（已淡到快看不见时改按新卡重播）");
    }

    /** 「位置与堆叠」：锚在哪（拖拽调整）、怎么展开、同屏几张。 */
    private void buildLayout(PickupCardConfig.Values v) {
        PickupCardSettings settings = PickupCardConfig.snapshot();
        restoreRow("重置对齐/展开/缩放/上限五项；锚点不在这里 —— 它归编辑场的「回到默认」");
        header("位置");
        cell("位置", new NvgButton("", this::anchorValueText, this::openAnchorEditor),
                "整摞卡停在哪儿。点开整屏编辑场，按住那摞卡拖到想要的位置；"
                        + "新卡永远贴着锚线、旧的向上顶。位置按屏幕比例记忆，换缩放档不错位。"
                        + "锚点太低放不下时自动抬到 HUD 带上方");
        cell("水平对齐", cycle(v.align, LayoutSettings.Side.values(), PickupCardConfigScreen::sideName),
                "锚线管卡的哪条边，点一下换下一种：竖条左缘锚定（竖条成一条线）→ 右缘对齐（右缘齐、左缘参差）。自动锚线跟着这一档各自解析");
        cell("展开方式", cycle(v.appearMode, LayoutSettings.Appear.values(),
                PickupCardConfigScreen::appearName),
                "新卡入场怎么从竖条右侧出现，点一下换下一种：火车（内容整块平移出来）→ 拉幕（可见范围从左往右展开）");
        cell("卡片缩放", percent(v.scalePercent, PickupCardConfig.layoutSnapshot().scalePercent()),
                "100% 原样。「自动」= 锚线上快放不下整摞时按比例缩小，最小 60%");
        header("数量");
        cell("同屏上限", number(v.maxOnScreen, settings.maxOnScreen(), 1, 16, 1, " 张"),
                "同时在屏最多几张。锚线上实在放不下时，放不下的先回队列等位子，不会硬消失");
        cell("排队上限", number(v.queueSize, settings.queueSize(), 0, 32, 1, " 张"),
                "屏上满了就先排队（先来先上屏）；0 = 不排队，屏满之后的拾取直接丢掉。"
                        + "排不上的会并进「还有 N 项」那张溢出卡");
    }

    /** 「外观」：形状一节、颜色一节。 */
    private void buildLook(PickupCardConfig.Values v) {
        StyleModel style = CardStage.INSTANCE.previewStyle();
        restoreRow("本页九项回到主题；更细的参数（paddingH/gap/竖条内缩）本就在 TOML 里");
        header("形状");
        cell("竖条宽度", styleNumber(v.stBarWidth, style.barWidth(), 1, 8, 1, "px"),
                "最左那根稀有度颜色条的横向粗细；写回 -1 = 跟随主题（默认 2px）");
        cell("图标内边距", styleNumber(v.stPaddingV, style.paddingV(), 0, 8, 1, ""),
                "图标距卡顶、卡底各留多少；调大卡片变高，图标大小不变");
        cell("图标大小", styleNumber(v.stIconSize, style.iconSize(), 8, 64, 1, "px"),
                "原版物品图标是 16 —— 取 16 或它的整数倍最清晰");
        cell("圆角", styleNumber(v.stCornerRadius, style.cornerRadius(), 0, 16, 1, "px"),
                "三个框的圆角半径；调到很大就变成胶囊");
        cell("描边粗细", styleNumber(v.stBorderWidth, style.borderWidth(), 0, 4, 1, "px"),
                "框描边的粗细；0 = 不描边");
        header("颜色");
        cell("底色（上）", color(v.stFillTop, style.fillTop()),
                "卡面渐变的上端。点色块换一个（第一下回到主题色）；要精确色值写 TOML 的 fillTop，如 #7DE38B");
        cell("底色（下）", color(v.stFillBottom, style.fillBottom()),
                "渐变的下端。和上面写成一样就是纯色；精确色值写 TOML 的 fillBottom");
        cell("描边颜色", color(v.stBorder, style.border()),
                "框描边的颜色；精确色值写 TOML 的 border");
        cell("物品名颜色", color(v.stNameColor, style.nameColor()),
                "名字的颜色；精确色值写 TOML 的 nameColor");
    }

    /** 「恢复本页默认」那一行：每页第一行，行动作、不是配置项。 */
    private void restoreRow(String what) {
        cell("恢复本页默认", new NvgButton("", () -> "恢复", this::restorePageDefaults), what);
    }

    /**
     * 「过滤」：三张名单。
     * <p>【为什么是三张】优先级见 {@code FilterRules}：白名单压过黑名单，静音名单与两者正交
     * （同时在白、静音两表里 = 强调地静音弹卡）。界面不替玩家判断"这条该写进哪张"，
     * 只把三张分别摆出来 —— 判定规则是一处，摆法是一处，改一处不会动另一处。
     * <p>【为什么一条规则自己一行】「一行一项」正好装得下：标签那边是规则原文，控件那边是
     * 「删除」—— 于是滚动、悬停高亮带、底部那句说明三样都不用为列表另写一套。
     */
    private void buildFilter(PickupCardConfig.Values v) {
        restoreRow("清空三张名单 —— 误点会把你自己加的规则全删掉");
        filterList("黑名单", v.blacklist,
                "命中就不弹卡。挖一片沙滩不想刷屏时写这里",
                "加进黑名单：物品 minecraft:cobblestone / tag #forge:ores / 整个 mod @modid，回车加");
        filterList("白名单", v.whitelist,
                "命中就一定弹卡并且强调；它压过黑名单",
                "加进白名单：写法同上；白名单 + 静音名单 = 强调地静音弹卡");
        filterList("静音名单", v.muteList,
                "照常弹卡，但稀有提示音和原版拾取音都被压掉",
                "加进静音名单：写法同上");
    }

    private void filterList(String title, ForgeConfigSpec.ConfigValue<List<? extends String>> config,
                            String what, String inputHint) {
        List<String> rules = rules(config);
        // 表头这一行：标签是名单名，右边那颗只读钮报"现在几条"——只读控件的底更暗、不画描边，
        // 一眼能看出它点不动（见 NvgButton 的 action == null）
        cell(title, new NvgButton("", () -> rules.size() + " 条", null), what);
        for (int i = 0; i < rules.size(); i++) {
            String rule = rules.get(i);
            int index = i;
            cell(rule, new NvgButton("", () -> "删除", () -> writeRules(config,
                    RuleListEdit.remove(rules(config), index))),
                    // 【为什么把规则原文放在最前】标签那一格只有几十像素宽，长规则在屏上就是
                    // "minecraft:cobb" —— 底部这行是唯一能看全的地方，而且它自己也只显示到画布边上，
                    // 所以原文必须排在前头，被截掉的是后半句解释
                    rule + " —— 点「删除」把它从「" + title + "」里去掉");
        }
        cell("加一条", NvgTextField
                .rule("", () -> "", RuleListEdit.MAX_RULE_LENGTH, text -> addRule(config, title, text))
                .placeholder("写一条再回车"), inputHint);
    }

    private static List<String> rules(ForgeConfigSpec.ConfigValue<List<? extends String>> config) {
        List<? extends String> raw = config.get();
        return raw == null ? List.of() : List.copyOf(raw);
    }

    private void writeRules(ForgeConfigSpec.ConfigValue<List<? extends String>> config,
                            List<String> rules) {
        config.set(List.copyOf(rules));
        changed();
        // 【必须重建】名单变了 = 行数变了：不重建的话删掉的那一行会留在屏上继续可点，
        // 而它背后的下标已经指向别人了。同页重建保留滚动位置（preserveScroll）。
        preserveScroll = true;
        pendingRebuild = true;
        layoutRows();
    }

    private void addRule(ForgeConfigSpec.ConfigValue<List<? extends String>> config,
                         String title, String text) {
        RuleListEdit.Result r = RuleListEdit.add(rules(config), text);
        if (r.ok()) {
            writeRules(config, r.rules());
            return;
        }
        // 【为什么要把拒绝原因说出来】这里最容易变成"打了字、按了回车、什么都没发生"——
        // 而"静默失败最毒"是这个项目已经付过一次学费的教训。
        filterNote = title + "：" + RuleListEdit.message(r.reject());
        filterNoteAt = System.currentTimeMillis();
    }

    private void cell(String label, NvgWidget widget, String hint) {
        rows.add(new Row(label, widget, hint));
    }

    /** 小节头：占一行、不接控件，把"这几行是一伙的"画出来。 */
    private void header(String title) {
        rows.add(new Row(title, null, null));
    }

    /** 逐行摆：**一行一项**（标签左、控件右），行距全界面恒 20px，放不下就滚。 */
    private void layoutRows() {
        if (itemsScroll == null) {
            return;
        }
        itemsScroll.reflow(itemsContentHeight());
        float offset = itemsScroll.offset();
        // 换页时整列往上滑一点点：滑动给的是"内容换了"的方向感（淡入只说明"变了"）
        int slide = Math.round((1f - pageAnim.at(now)) * PAGE_SLIDE);
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            // 扣掉滚动偏移：控件与它画出来的位置必须是同一个坐标系，否则点了会"选错行"
            float y = rowsTop() + i * (float) ROW_STEP - Math.round(offset) + slide;
            row.yAt = y;
            if (!row.isHeader()) {
                row.widget().at(controlX(), y, controlW(), ROW_H);
            }
        }
    }

    // ------------------------------------------------------------------
    // 画
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        long frameStart = System.nanoTime();
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
                // 【标签列必须自己画一遍】它不参与配置列的裁剪与换页淡入（换页时它不动），
                // 所以不在下面那个循环里 —— 重构时漏掉这一行，屏幕上就是"标签列空着、只有
                // 一条强调条"，而点击照样有效（控件在、只是没画）。
                for (NvgWidget w : tabButtons) {
                    w.draw(ui);
                }
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
                    if (!row.isHeader()) {
                        row.widget().draw(ui);
                    }
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
        // 【单独记账】"换页/悬停时卡"这类反馈只有把预览的开销从界面开销里拆出来才能定案：
        // 预览每帧重渲物品模型，是这一屏唯一可疑的大头。
        long previewStart = System.nanoTime();
        drawPreview(gui);
        previewNanos = System.nanoTime() - previewStart;
        drawHint(gui, mouseX, mouseY);
        frameCost(System.nanoTime() - frameStart);
    }

    /**
     * 逐帧耗时统计 —— 用户报过「配置界面动画掉帧」，而掉帧只有数字能定死。
     * <p>【为什么不是"看一眼卡不卡"】掉帧是<b>少数几帧特别慢</b>，肉眼看整体帧率往往看不出来
     * （平均值被大量快帧摊平）。所以这里记的是 <b>最大值</b>与"超过 16.7ms 的帧数"，
     * 每 {@value #FRAME_LOG_EVERY} 帧往日志里报一次；不刷屏、也不进每帧的绘制路径。
     */
    private void frameCost(long nanos) {
        long us = nanos / 1_000L;
        previewSumUs += previewNanos / 1_000L;
        frameCount++;
        if (us > frameMaxUs) {
            frameMaxUs = us;
            previewAtMaxUs = previewNanos / 1_000L;   // 最慢那一帧预览占多少 —— "换页卡"的归因就用它
            // 【为什么要记第几帧】"最慢 80ms"这个数只有配上"第 1 帧"才有用 ——
            // 第 1 帧慢是开屏的一次性开销（建 NanoVG 上下文、烘图标），
            // 第 300 帧慢才是动画中途真的卡了。少了这个下标，两种情况的日志长得一模一样。
            frameMaxAt = frameCount;
        }
        frameSumUs += us;
        if (us > 16_700L) {
            frameJanky++;
        }
        if (frameCount >= FRAME_LOG_EVERY) {
            reportFrames();
        }
    }

    /**
     * 报一次统计并把窗口清零。
     * <p>【为什么要有一个"关界面时报一次"】常驻每 {@value #FRAME_LOG_EVERY} 帧报一次是为了长时间
     * 盯；而进出一次配置界面只有十来秒、往往凑不满一个窗口 —— 只在退出时报，才不会"来了一趟
     * 却什么都没量到"。
     */
    private void reportFrames() {
        if (frameCount == 0) {
            return;
        }
        PickupCard.LOGGER.info("[配置界面/帧] {} 帧：平均 {}us（预览均摊 {}us），最慢 {}us（其中预览 {}us，第 {} 帧），超过 16.7ms 的有 {} 帧",
                frameCount, frameSumUs / frameCount, previewSumUs / frameCount,
                frameMaxUs, previewAtMaxUs, frameMaxAt, frameJanky);
        frameCount = 0;
        frameSumUs = 0L;
        frameMaxUs = 0L;
        frameMaxAt = 0;
        frameJanky = 0;
        previewSumUs = 0L;
        previewAtMaxUs = 0L;
    }

    @Override
    public void removed() {
        reportFrames();
        super.removed();
    }

    /** 每多少帧报一次（约 10 秒 @60fps）。 */
    private static final int FRAME_LOG_EVERY = 600;
    private int frameCount;
    private long frameSumUs;
    private long frameMaxUs;
    private int frameMaxAt;
    private int frameJanky;
    /** 本窗口内预览列的耗时累计（除以帧数 = 预览均摊；换页卡不卡用它定案）。 */
    private long previewNanos;
    private long previewSumUs;
    private long previewAtMaxUs;

    /**
     * 把这一帧的动画目标推进一步。
     * <p>【为什么统一在渲染前推】一个动画一件事：换页与强调条的目标恒为/随状态，悬停跟着鼠标。
     * 分散在各自的绘制里推进的话，"这一帧到底更新过谁"就没人说得清了 —— 那种 bug 表现为
     * "偶尔动画不动"。
     */
    private void driveAnimations(int mouseX, int mouseY) {
        driveStage();
        pageAnim.retarget(1f, now, PAGE_MS);
        tabAccentAnim.retarget(section.ordinal(), now, TAB_MS);
        for (Row row : rows) {
            if (row.isHeader()) {
                continue;
            }
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
        // 【标题区三行各是什么】标题居中（原版选项页的规矩：标题是页面的，不是哪一列的）；
        // 副标题一句话居中、占满可用宽；状态行钉在标题行右端 —— 总开关是"整体生效没生效"
        // 的唯一真源，藏进页里就得翻页才知道。
        ui.textCentered(this.title.getString(), this.width / 2f, 6f, 0xFFFFFFFF);
        ui.textCentered("所有改动立即生效，不用保存", this.width / 2f, 17f, p.textDim);
        ui.textRight(status(), this.width - PAD - 4f, 6f,
                eff.enabled() ? p.accent : p.textDim);
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
            ui.fillRoundRect(lo.preview().x() - 2f, lo.preview().y() + 10f,
                    lo.preview().w() + 4f, Math.max(0f, lo.preview().h() - 12f), p.radius,
                    0x40202A38);
        } else {
            // 【为什么右对齐】底部那行左边是悬停说明（drawHint），左对齐会跟它叠在一起
            // —— 第一版就是这么写的，截图里两段字糊成一团。
            ui.textRight(PREVIEW_COLLAPSED, lo.items().right(), this.height - 12f, p.textDim);
        }
    }

    /** 标题行右端那行状态：总开关之外，玩家最常想知道的是"卡现在缩到了多少"。 */
    private String status() {
        PickupCardSettings eff = PickupCardConfig.snapshot();
        return (eff.enabled() ? "总开关 开 · " : "总开关 关 · ") + scaleText();
    }

    /**
     * 底部那行「预览被收掉了」的提示语。
     * <p>【为什么提成常量】{@link #hintRightLimit()} 要按它的宽度给悬停说明让路 ——
     * 两处各写一份字面量的话，改了这边忘了那边，叠字就会悄悄回来。
     */
    private static final String PREVIEW_COLLAPSED = "预览已收起（窗口太窄）";

    /**
     * 底部那行<b>左边</b>（悬停说明）最多能画到哪个 x。
     * <p>【为什么不是整幅画布宽】预览收掉时，同一行的右端还有 {@link #PREVIEW_COLLAPSED}
     * 那段字，而它不知道右边有东西，于是窄画布上两段字直接撞上。
     */
    private float hintRightLimit() {
        float limit = this.width - PAD - 4f;
        ConfigLayout lo = layout();
        if (!lo.previewVisible()) {
            limit = Math.min(limit,
                    lo.items().right() - this.font.width(PREVIEW_COLLAPSED) - PAD);
        }
        return limit;
    }

    /**
     * 选中那颗标签的强调条：从上一颗<b>滑</b>到这一颗。
     * <p>【为什么要有它】底色那一档差别在深色主题下太细，"我现在在哪一页"只剩文字颜色一条线索；
     * 加一条强调色带之后，换页这件事在眼睛的余光里也成立。
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
        if (row.isHeader()) {
            return;
        }
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
            if (row.isHeader()) {
                // 小节头：一根强调色小刺 + 暗色小字 —— 只用字号一种手段的话，它会和
                // "没接控件的标签"混成一团（2026-09-19 真机截图抓过）。
                ui.fillRoundRect(labelX() - 3f, row.yAt + 5f, 2f, 8f, 1f,
                        NvgUi.fade(ui.palette.accent, 0.45f));
                ui.text(row.label(), labelX() + 3f, row.yAt + 1f, ui.palette.textDim);
                continue;
            }
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
        // 【加规则被拒时先说话】它是玩家刚刚做的动作的结果，比"鼠标现在停在哪"更该被看见；
        // 停几秒就还回去，免得把悬停说明永久顶掉
        if (!filterNote.isEmpty() && now - filterNoteAt < FILTER_NOTE_MS) {
            hint = filterNote;
        }
        // 【为什么要夹在配置列里】滚出视口的行，它的矩形还在（只是被裁掉了）——
        // 不做这个判断的话，鼠标划过页眉时会说"这张卡的说明"，而那一行根本看不见。
        ConfigLayout.Rect items = layout().items();
        if (hint == null && mouseY >= items.y() && mouseY < items.bottom()) {
            for (Row row : rows) {
                if (!row.isHeader() && row.widget().hit(mouseX, mouseY)) {
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
        String shown = this.font.plainSubstrByWidth(hint,
                Math.max(24, (int) (hintRightLimit() - PAD)));
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
    // 预览：动画页一张自动舞台，其他页一张静止完整卡
    // ------------------------------------------------------------------

    /**
     * 预览收起时什么都不画 —— 挤成一条比没有更难看。
     * <p>【为什么按页分工（2026-09-19 回归）】你在调"停留时长"时，预览永远在演别人的一生
     * （常驻舞台），节奏不由你控制；而大多数页要看的其实是<b>一张完整的卡长什么样</b> ——
     * 静止卡 + 点一下重播入场，节奏归玩家。动画页保留舞台：那页调的就是"卡与卡之间"的事，
     * 一张卡演不出来。
     */
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
        if (stagePage()) {
            driveStage();
            renderCards(gui, area, style, new ArrayList<>(stage));
        } else if (staticCard != null) {
            renderCards(gui, area, style, List.of(staticCard));
        }
    }

    /** 非动画页放一张卡（点预览/换样例 = 换成新出生的一张，入场重播一遍后停住）。 */
    private void playOnce() {
        if (stagePage()) {
            // 「来一张」必出新卡：合并演示交给自动节拍（连发两发同款时它自己会并）
            spawnStage(false);
            nextSpawnAt = now + STAGE_SPAWN_MS;
        } else {
            staticCard = new CardView(previewNotice(sample, sample.amount, now));
        }
    }

    /** 舞台的节拍：到点来一张（同款自动并进去演示合并），退场播完的清掉。只在动画页跑。 */
    private void driveStage() {
        if (!stagePage()) {
            return;
        }
        if (nextSpawnAt < 0L) {
            nextSpawnAt = now + 500L;
        }
        if (now >= nextSpawnAt) {
            spawnStage(true);
            nextSpawnAt = now + STAGE_SPAWN_MS;
        }
        PickupCardSettings settings = PickupCardConfig.snapshot();
        stage.removeIf(v -> v.exiting()
                && CardTimeline.exit(now, v.exitStartAt(), settings.exitMs()) >= 1f);
    }

    /**
     * 往舞台放一张当前选中的样例。
     *
     * @param mergeDemo true = 队首是同款且没在退场时并进去（演示脉冲 + 数字滚动，自动节拍用）；
     *                  false = 必出新卡（「来一张」手动钮用 —— 从前点它"没反应"就是因为被合并吞了）
     */
    private void spawnStage(boolean mergeDemo) {
        CardView front = stage.peekFirst();
        if (mergeDemo && front != null && !front.exiting()
                && front.notice().key().equals("preview:" + sample.label)) {
            front.absorbMerge(previewNotice(sample, front.notice().count() + 1, now), now);
            return;
        }
        stage.addFirst(new CardView(previewNotice(sample, sample.amount, now)));
        // 超员的退场：最老那张开始消失，播完由 driveStage 清掉。beginExit 幂等，重复调用无害。
        while (stage.size() > STAGE_CAPACITY) {
            stage.peekLast().beginExit(now);
            if (stage.size() > STAGE_CAPACITY + 1) {
                stage.removeLast();     // 消失时长被设得很长时别让尸体堆着
            } else {
                break;
            }
        }
    }

    /**
     * 预览落位：<b>贴面板底</b>（与真卡的底锚同构 —— 预览说"新卡出现在固定的一点"，它自己
     * 就得先做到），水平按锚线分数落位。堆里多张时向上生长；换位走 340ms 平滑，跟真卡一路。
     */
    private void renderCards(GuiGraphics gui, ConfigLayout.Rect area, StyleModel style,
                             List<CardView> views) {
        LayoutSettings layout = PickupCardConfig.layoutSnapshot();
        float scale = previewScale();
        float gap = layout.separation() * scale;
        // 【名字截断跟着面板走，不跟整屏走】卡壳被面板夹窄之后，名字的截断预算若还按
        // "屏宽 × 45%" 算，文字就会溢出卡壳、戳出面板（真机上长名样例第一个露馅）。
        // 预算 = 面板里的卡宽 − 名字以外固定占的宽（与 CardMetrics 同一把尺）。
        float room = Math.max(24f, (area.w() - 6f) / scale);
        CardCanvas probe = previewCanvas(style, scale, PickupCardConfig.snapshot());
        int nameRoom = (int) Math.max(12f,
                room - CardMetrics.namelessWidth(probe, this.font, sample.amount));
        PickupCardSettings settings = withNameLimit(PickupCardConfig.snapshot(), nameRoom);
        float fx = layout.anchorLeft(this.width) / Math.max(1f, this.width);
        float y = area.bottom();
        List<CardSlot> slots = new ArrayList<>(views.size());
        List<String> keys = new ArrayList<>(views.size());
        for (CardView v : views) {
            float h = style.boxHeight() * scale;
            float w = Math.min(cardWidth(v, style, scale, settings), area.w() - 6f);
            float x;
            if (layout.align() == LayoutSettings.Side.RIGHT) {
                x = Math.max(1f, Math.min(fx * area.w() - w, area.w() - 7f - w));
            } else {
                x = Math.max(1f, Math.min(fx * area.w(), area.w() - 7f - w));
            }
            keys.add(v.key());
            slots.add(new CardSlot(v, area.x() + x, previewMove.y(v.key(), y - h, now), w, h));
            y -= h + gap;
        }
        previewMove.retain(new java.util.HashSet<>(keys));
        if (!slots.isEmpty()) {
            previewPainter.paint(gui, previewCanvas(style, scale, settings), slots);
        }
    }

    /** 一份快照的副本：名字宽度上限额外夹进面板给的预算（0 = 自动 → 直接用预算）。 */
    private static PickupCardSettings withNameLimit(PickupCardSettings s, int room) {
        int limit = s.nameMaxWidth() > 0 ? Math.min(s.nameMaxWidth(), room) : room;
        return new PickupCardSettings(s.holdMs(), s.exitMs(), s.mergeMode(), s.maxOnScreen(),
                s.queueSize(), s.countFormat(), s.enabled(), s.showItemName(), s.showItemId(),
                limit);
    }

    /** 一张样例在 100% 下的自然宽度 —— 走真卡的 {@code CardMetrics#naturalWidth}，不另写一份公式。 */
    private float cardWidth(CardView view, StyleModel style, float scale, PickupCardSettings settings) {
        return CardMetrics.naturalWidth(previewCanvas(style, scale, settings), this.font, view) * scale;
    }

    /**
     * 一张样例造出的真卡。数量与名字都由真卡的规则给（{@code CountFormat} / {@code displayName}），
     * 于是「数量写法」「显示物品ID」这些键在预览里立刻看得见。
     */
    /** 样例 → 账本快照。预览与锚点编辑场共用（同包），内容只有这一份。 */
    static Notice<Inbox.Card> previewNotice(Sample s, int amount, long bornAt) {
        Inbox.Card payload = new Inbox.Card(
                s == Sample.XP ? new CardContent.Experience() : new CardContent.Item(s.icon()),
                s.glow);
        return new Notice<>("preview:" + s.label, "preview", payload, amount, false,
                bornAt, bornAt, 0);
    }

    /** 这一帧要用的画布 —— 缩放与主题都按界面上当前生效的值给，预览才不会说谎。 */
    private CardCanvas previewCanvas(StyleModel style, float scale, PickupCardSettings settings) {
        return new CardCanvas(now,
                new CardTimeline(style.enterMs(), style.bumpMs(), style.enterEnabled(),
                        style.bumpEnabled()),
                style, settings, PickupCardConfig.layoutSnapshot(),
                this.width, this.height, scale);
    }

    /** 交给真卡的画笔。整摞一次画完 —— 它自己开一帧 NanoVG，比一张一张开省。 */
    private final NvgCardPainter previewPainter = new NvgCardPainter();

    /** 预览里"换位"的平滑（与真卡 {@code CardStage} 同一个类）：新卡顶旧卡不再瞬移。 */
    private final CardMove previewMove = new CardMove();

    /** 非动画页的那张静止完整卡（{@code playOnce()} 换新重生）。 */
    private CardView staticCard;

    /** 舞台节拍：每隔这么长时间自动来一张（略放慢，看得清退场）。 */
    private static final long STAGE_SPAWN_MS = 1_700L;
    /** 舞台同屏上限（不含正在退场的那张）。 */
    private static final int STAGE_CAPACITY = 3;
    /** 舞台上的卡，队首 = 最新。全是真 CardView：入场/合并/退场走真时间线。 */
    private final java.util.ArrayDeque<CardView> stage = new java.util.ArrayDeque<>();
    /** 下一张自动入场的时刻（{@code <0} = 还没开过场）。 */
    private long nextSpawnAt = -1L;

    /**
     * 预览该用多大的缩放。
     * <p>
     * 【为什么自动档在预览里恒为 100%】自动倍率取决于"游戏里那摞卡有几张、屏幕多高"，
     * 而预览面板是另一块画布 —— 用它算出来的倍率画在面板里只会骗人。手动档是玩家的明确
     * 选择，预览照它画；自动档的真实倍率看游戏画面（行里的说明也写了这句）。
     */
    /** 预览该用多大的缩放（锚点编辑场同用这一条：编辑场里的卡也要按玩家选的缩放画）。 */
    static float previewScale() {
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

    /** 所有自绘控件：事件遍历、悬停刷新、重建时都用这一份（小节头没有控件，不在其中）。 */
    private List<NvgWidget> widgets() {
        List<NvgWidget> all = chips();
        for (Row row : rows) {
            if (!row.isHeader()) {
                all.add(row.widget());
            }
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
        // 【点预览 = 放一张】动画页来一张新的走完整时间线；其他页重播一次入场
        ConfigLayout.Rect preview = layout().previewCard();
        if (layout().previewVisible()
                && mouseX >= preview.x() && mouseX < preview.right()
                && mouseY >= preview.y() && mouseY < preview.bottom()) {
            playOnce();
            return true;
        }
        ConfigLayout.Rect items = layout().items();
        for (NvgWidget w : widgets()) {
            // 滚出视口的行不该还能被点到（它们的位置在视口外，只有 x 可能重合）
            boolean rowWidget = rows.stream().anyMatch(r -> !r.isHeader() && r.widget() == w);
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
        // 一格滚三行（按行不按像素：跨缩放档手感一致，见 ScrollMath）
        itemsScroll.wheel(delta, itemsContentHeight(), ROW_STEP * 3f);
        layoutRows();
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
            if (!row.isHeader() && row.label().equals(label)) {
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
        boolean down = mouseClicked(cx, cy, 0);
        mouseReleased(cx, cy, 0);
        // 【诊断常驻】"点了没反应"只能靠这行定案：坐标对不对、按下命中没、点完屏幕换没换
        PickupCard.LOGGER.info("[clickOption] 『{}』点({},{}) 按下命中={} 控件 {}x{}@({},{}) 屏幕={}",
                label, Math.round(cx), Math.round(cy), down,
                Math.round(w.width()), Math.round(w.height()), Math.round(w.x()), Math.round(w.y()),
                Minecraft.getInstance().screen == null ? "无" : Minecraft.getInstance().screen.getClass().getSimpleName());
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
        boolean down = mouseClicked(startX, y, 0);
        mouseDragged(endX, y, 0, endX - startX, 0);
        mouseReleased(endX, y, 0);
        PickupCard.LOGGER.info("[dragOption] 『{}』按({},{})→拖({},{}) 按下命中={} 控件 {}x{}@({},{})",
                label, Math.round(startX), Math.round(y), Math.round(endX), Math.round(y), down,
                Math.round(w.width()), Math.round(w.height()), Math.round(w.x()), Math.round(w.y()));
        return true;
    }

    /**
     * 走真实事件路径往某个文本框里打字并回车（点一下拿焦点 → 逐字 → 回车提交）。
     */
    public boolean typeOption(String label, String text) {
        if (!clickOption(label)) {
            return false;
        }
        for (char c : text.toCharArray()) {
            charTyped(c, 0);
        }
        keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        return true;
    }

    /** 当前这一页有哪些选项（按玩家看到的顺序；小节头不是选项，不列）。日志里留一份。 */
    public List<String> optionLabels() {
        return rows.stream().filter(r -> !r.isHeader()).map(Row::label).toList();
    }

    /** 选项名 + 位置 + 显示值。布局是算出来的，"框压到边上了"必须能不靠眼睛查出来。 */
    public List<String> optionDump() {
        return rows.stream().filter(r -> !r.isHeader()).map(r -> {
            NvgWidget w = r.widget();
            return r.label() + "=" + Math.round(w.x()) + "," + Math.round(w.y())
                    + " " + Math.round(w.width()) + "x" + Math.round(w.height())
                    + " 值[" + w.value() + "]";
        }).toList();
    }

    // ------------------------------------------------------------------
    // 几何：按画布算，不写死
    // ------------------------------------------------------------------

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

    /** 颜色 = 色块循环（{@link NvgColorChip}）；精确色值的出路在 TOML，行说明里有写。 */
    private NvgColorChip color(ForgeConfigSpec.ConfigValue<String> config, int effectiveArgb) {
        return new NvgColorChip("", () -> effectiveArgb(config, effectiveArgb), config::get, text -> {
            config.set(text);
            changed();
        });
    }

    /** 色块画的生效色：配置里解析得动就用解析值（含手写 hex），否则回主题生效值。 */
    private static int effectiveArgb(ForgeConfigSpec.ConfigValue<String> config, int effective) {
        return StyleOverrides.parseArgb(config.get()).orElseGet(() -> effective);
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

    private static String sideName(LayoutSettings.Side side) {
        return side == LayoutSettings.Side.RIGHT ? "右缘对齐" : "竖条左缘锚定";
    }

    /** 草稿（design/animation.html）自己的叫法：火车＝平移，拉幕＝展开可见范围。 */
    private static String appearName(LayoutSettings.Appear appear) {
        return appear == LayoutSettings.Appear.CLIP ? "拉幕" : "火车";
    }

    /** 与入场对称的那一半：淡出 / 火车退回 / 拉幕收拢。 */
    private static String exitName(LayoutSettings.Exit exit) {
        return switch (exit) {
            case TRAIN -> "火车退回";
            case WIPE -> "拉幕收拢";
            default -> "淡出";
        };
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
            // 按钮里只放短值（控件格最窄 64px，九个字必然溢出按钮）；
            // "同名就并、改名不并"的完整语义在悬停说明里
            case SAME_ITEM_KEEP_NAMED -> "改名不并";
            case NEVER -> "从不合并";
            default -> "同名同附魔才并";
        };
    }

    /** 「位置」那颗钮的值：一句话说清现在是自动还是自定义（自动 = 右下贴 HUD 带）。 */
    private String anchorValueText() {
        LayoutSettings layout = PickupCardConfig.layoutSnapshot();
        if (layout.anchorX() < 0 && layout.anchorY() < 0) {
            return "自动（右下）";
        }
        return "自定义 (" + Math.round(layout.anchorLeft(this.width)) + ", "
                + Math.round(layout.anchorTop(this.height,
                        CardStage.INSTANCE.previewStyle().boxHeight() * previewScale(),
                        HudSafeZone.bottomInset())) + ")";
    }

    /** 打开整屏拖拽编辑场。配置不在这里写 —— 编辑场「完成」时才写。 */
    private void openAnchorEditor() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new AnchorEditScreen(this));
        }
    }

    /** 每次改动都让渲染立刻重读，不等那一秒的重读间隔（锚点编辑场同包共用）。 */
    static void changed() {
        CardStage.INSTANCE.refreshStyle();
    }
}
